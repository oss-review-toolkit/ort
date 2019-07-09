/*
 * Copyright (C) 2019 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */

package com.here.ort.analyzer.managers

import ch.frankel.slf4k.*

import com.fasterxml.jackson.databind.JsonNode

import com.here.ort.analyzer.AbstractPackageManagerFactory
import com.here.ort.analyzer.PackageManager
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.model.Identifier
import com.here.ort.model.Package
import com.here.ort.model.PackageLinkage
import com.here.ort.model.PackageReference
import com.here.ort.model.Project
import com.here.ort.model.ProjectAnalyzerResult
import com.here.ort.model.RemoteArtifact
import com.here.ort.model.Hash
import com.here.ort.model.Scope
import com.here.ort.model.VcsInfo
import com.here.ort.model.VcsType
import com.here.ort.model.config.AnalyzerConfiguration
import com.here.ort.model.config.RepositoryConfiguration
import com.here.ort.model.jsonMapper
import com.here.ort.utils.CommandLineTool
import com.here.ort.utils.log
import com.here.ort.utils.textValueOrEmpty

import com.moandjiezana.toml.Toml

import com.vdurmont.semver4j.Requirement

import java.io.File
import java.util.SortedSet

class Cargo(
    name: String,
    analyzerRoot: File,
    analyzerConfig: AnalyzerConfiguration,
    repoConfig: RepositoryConfiguration
) : PackageManager(name, analyzerRoot, analyzerConfig, repoConfig), CommandLineTool {
    class Factory : AbstractPackageManagerFactory<Cargo>("Cargo") {
        override val globsForDefinitionFiles = listOf("Cargo.toml")

        override fun create(
            analyzerRoot: File,
            analyzerConfig: AnalyzerConfiguration,
            repoConfig: RepositoryConfiguration
        ) = Cargo(managerName, analyzerRoot, analyzerConfig, repoConfig)
    }

    companion object {
        private const val REQUIRED_CARGO_VERSION = "1.0.0"
        private const val SCOPE_NAME_DEPENDENCIES = "dependencies"
        private const val SCOPE_NAME_DEV_DEPENDENCIES = "devDependencies"
        private val pathDependencyRegex = Regex("""^.*\(path\+file://(.*)\)$""")
    }

    override fun command(workingDir: File?) = "cargo"

    override fun getVersionRequirement(): Requirement = Requirement.buildStrict(REQUIRED_CARGO_VERSION)

    private fun runMetadata(workingDir: File): String = run(workingDir, "metadata", "--format-version=1").stdout

    private fun extractCargoId(node: JsonNode) = node["id"].textValueOrEmpty()

    private fun extractPackageId(node: JsonNode) =
        Identifier(
            type = "Cargo",
            namespace = "",
            name = node["name"].textValueOrEmpty(),
            version = node["version"].textValueOrEmpty()
        )

    private fun extractRepositoryUrl(node: JsonNode) = node["repository"].textValueOrEmpty()

    private fun extractVcsInfo(node: JsonNode) =
        VersionControlSystem.splitUrl(extractRepositoryUrl(node))

    private fun extractDeclaredLicenses(node: JsonNode) =
        node["license"].textValueOrEmpty().split("/")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSortedSet()

    private fun extractSourceArtifact(
        node: JsonNode,
        hashes: Map<String, String>
    ): RemoteArtifact? {
        if (node["source"].textValueOrEmpty() != "registry+https://github.com/rust-lang/crates.io-index") {
            return null
        }

        val name = node["name"]?.textValue() ?: return null
        val version = node["version"]?.textValue() ?: return null
        val url = "https://crates.io/api/v1/crates/$name/$version/download"
        val checksum = checksumKeyOf(node)
        val hash = Hash.create(hashes[checksum] ?: "")
        return RemoteArtifact(url, hash)
    }

    private fun extractPackage(node: JsonNode, hashes: Map<String, String>) =
        Package(
            id = extractPackageId(node),
            declaredLicenses = extractDeclaredLicenses(node),
            description = node["description"].textValueOrEmpty(),
            binaryArtifact = RemoteArtifact.EMPTY,
            sourceArtifact = extractSourceArtifact(node, hashes) ?: RemoteArtifact.EMPTY,
            homepageUrl = "",
            vcs = extractVcsInfo(node)
        )

    private fun checksumKeyOf(metadata: JsonNode): String {
        val id = extractCargoId(metadata)
        return "\"checksum $id\""
    }

    // Cargo.lock is located next to Cargo.toml or in one of the parent directories. The latter
    // is case when the project is part of a workspace. Cargo.lock is then located next to the
    // Cargo.toml file defining the workspace.
    private fun resolveLockfile(metadata: JsonNode): File {
        val workspaceRoot = metadata["workspace_root"].textValueOrEmpty()
        val lockfile = File(workspaceRoot, "Cargo.lock")
        if (!lockfile.isFile) {
            throw IllegalArgumentException("missing Cargo.lock file")
        }

        return lockfile
    }

    private fun readHashes(lockfile: File): Map<String, String> {
        val contents = Toml().read(lockfile)
        val metadata = contents.getTable("metadata") ?: return emptyMap()
        val metadataMap = metadata.toMap()

        val metadataMapNotNull = mutableMapOf<String, String>()
        metadataMap.forEach { (key, value) ->
            if (key != null && (value as? String) != null) {
                metadataMapNotNull[key] = value
            }
        }

        return metadataMapNotNull
    }

    private fun resolveDependenciesTree(
        pkgName: String,
        pkgVersion: String,
        metadata: JsonNode,
        packages: Map<String, Package>,
        filter: (pkgId: String, depId: String) -> Boolean = { _, _ -> true }
    ): SortedSet<PackageReference> {
        val nodes = metadata["resolve"]["nodes"]
        val pkgId = metadata["workspace_members"]
            .map { it.textValueOrEmpty() }
            .single { it.contains(pkgName) && it.contains(pkgVersion) }

        val root = nodes.map { extractCargoId(it) }.single { it == pkgId }
        return resolveDependenciesOf(root, nodes, packages, filter).dependencies
    }

    private fun resolveDependenciesOf(
        id: String,
        nodes: JsonNode,
        packages: Map<String, Package>,
        filter: (pkgId: String, depId: String) -> Boolean
    ): PackageReference {
        val node = nodes.single { it["id"].textValueOrEmpty() == id }
        val depsReferences = node["dependencies"]
            .map { it.textValueOrEmpty() }
            .filter { filter(id, it) }
            .map { resolveDependenciesOf(it, nodes, packages, filter) }
        val pkg = packages.getValue(id)
        val linkage = if (isProjectDependency(id)) PackageLinkage.PROJECT_STATIC else PackageLinkage.STATIC
        return pkg.toReference(linkage, dependencies = depsReferences.toSortedSet())
    }

    private fun isDevDependencyOf(id: String, depId: String, metadata: JsonNode): Boolean {
        val packages = metadata["packages"]
        val pkg = packages.single { it["id"].textValueOrEmpty() == id }
        val depPkg = packages.single { it["id"].textValueOrEmpty() == depId }
        return pkg["dependencies"].any {
            val name = it["name"].textValueOrEmpty()
            val kind = it["kind"].textValueOrEmpty()
            name == depPkg["name"].textValueOrEmpty() && (kind == "dev" || kind == "build")
        }
    }

    // Check if a package is a project.
    //
    // We treat all path dependencies inside of the analyzer root as project dependencies.
    private fun isProjectDependency(id: String) =
        pathDependencyRegex.matchEntire(id)?.groups?.get(1)?.let { match ->
            val packageDir = File(match.value)
            packageDir.startsWith(analyzerRoot)
        } ?: false

    override fun resolveDependencies(definitionFile: File): ProjectAnalyzerResult? {
        log.info { "Resolving dependencies for: '$definitionFile'" }

        // Get the project name; if none => we have a workspace definition => return null.
        val pkgDefinition = Toml().read(definitionFile)
        val projectName = pkgDefinition.getString("package.name") ?: return null
        val projectVersion = pkgDefinition.getString("package.version") ?: return null

        val workingDir = definitionFile.parentFile
        val metadataJson = runMetadata(workingDir)
        val metadata = jsonMapper.readTree(metadataJson)
        val hashes = readHashes(resolveLockfile(metadata))

        // Collect all packages.
        val packages = metadata["packages"].associateBy(
            { extractCargoId(it) },
            { extractPackage(it, hashes) }
        )

        // Resolve the dependencies tree.
        val dependencies = resolveDependenciesTree(projectName, projectVersion, metadata, packages) { id, devId ->
            !isDevDependencyOf(id, devId, metadata)
        }

        val devDependencies = resolveDependenciesTree(projectName, projectVersion, metadata, packages) { id, devId ->
            isDevDependencyOf(id, devId, metadata)
        }

        val dependenciesScope = Scope(
            name = SCOPE_NAME_DEPENDENCIES,
            dependencies = dependencies
        )
        val devDependenciesScope = Scope(
            name = SCOPE_NAME_DEV_DEPENDENCIES,
            dependencies = devDependencies
        )

        // Resolve project.
        val projectPkg = packages.values.single { pkg ->
            pkg.id.name == projectName && pkg.id.version == projectVersion
        }

        val homepageUrl = pkgDefinition.getString("package.homepage") ?: ""
        val project = Project(
            id = projectPkg.id,
            definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
            declaredLicenses = projectPkg.declaredLicenses,
            vcs = projectPkg.vcs,
            vcsProcessed = processProjectVcs(workingDir, projectPkg.vcs, homepageUrl),
            homepageUrl = homepageUrl,
            scopes = sortedSetOf(dependenciesScope, devDependenciesScope)
        )

        val nonProjectPackages = packages
            .filterNot { isProjectDependency(it.key) }
            .map { it.value.toCuratedPackage() }
            .toSortedSet()

        return ProjectAnalyzerResult(project, nonProjectPackages)
    }
}
