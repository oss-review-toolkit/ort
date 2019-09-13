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
import com.here.ort.model.config.AnalyzerConfiguration
import com.here.ort.model.config.RepositoryConfiguration
import com.here.ort.model.jsonMapper
import com.here.ort.utils.CommandLineTool
import com.here.ort.utils.log
import com.here.ort.utils.textValueOrEmpty

import com.moandjiezana.toml.Toml

import com.vdurmont.semver4j.Requirement

import java.io.File

/**
 * The [Cargo](https://doc.rust-lang.org/cargo/) package manager for Rust.
 */
class Cargo(
    name: String,
    analysisRoot: File,
    analyzerConfig: AnalyzerConfiguration,
    repoConfig: RepositoryConfiguration
) : PackageManager(name, analysisRoot, analyzerConfig, repoConfig), CommandLineTool {
    class Factory : AbstractPackageManagerFactory<Cargo>("Cargo") {
        override val globsForDefinitionFiles = listOf("Cargo.toml")

        override fun create(
            analysisRoot: File,
            analyzerConfig: AnalyzerConfiguration,
            repoConfig: RepositoryConfiguration
        ) = Cargo(managerName, analysisRoot, analyzerConfig, repoConfig)
    }

    companion object {
        private const val REQUIRED_CARGO_VERSION = "1.0.0"
        private const val SCOPE_NAME_DEPENDENCIES = "dependencies"
        private const val SCOPE_NAME_DEV_DEPENDENCIES = "dev-dependencies"
        private const val SCOPE_NAME_BUILD_DEPENDENCIES = "build-dependencies"
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
        val hash = Hash.create(hashes[checksum].orEmpty())
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

    /**
     * Cargo.lock is located next to Cargo.toml or in one of the parent directories. The latter is the case when the
     * project is part of a workspace. Cargo.lock is then located next to the Cargo.toml file defining the workspace.
     */
    private fun resolveLockfile(metadata: JsonNode): File {
        val workspaceRoot = metadata["workspace_root"].textValueOrEmpty()
        val workingDir = File(workspaceRoot)
        val lockfile = workingDir.resolve("Cargo.lock")

        requireLockfile(workingDir) { lockfile.isFile }

        return lockfile
    }

    private fun readHashes(lockfile: File): Map<String, String> {
        if (!lockfile.isFile) {
            log.debug { "Cannot determine the hashes of remote artifacts because the Cargo lockfile is missing." }
            return emptyMap()
        }

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

    /**
     * Check if a package is a project. All path dependencies inside of the analyzer root are treated as project
     * dependencies.
     */
    private fun isProjectDependency(id: String) =
        pathDependencyRegex.matchEntire(id)?.groups?.get(1)?.let { match ->
            val packageDir = File(match.value)
            packageDir.startsWith(analysisRoot)
        } ?: false

    private fun buildDependencyTree(
        name: String,
        version: String,
        packages: Map<String, Package>,
        metadata: JsonNode
    ): PackageReference {
        val node = metadata["packages"].single {
            it["name"].textValue() == name && it["version"].textValue() == version
        }

        val dependencies = node["dependencies"].filter {
            // Filter dev and build dependencies, because they are not transitive.
            val kind = it["kind"].textValueOrEmpty()
            kind != "dev" && kind != "build"
        }.mapNotNull {
            // TODO: Handle renamed dependencies here, see:
            //       https://doc.rust-lang.org/cargo/reference/specifying-dependencies.html#renaming-dependencies-in-cargotoml
            val dependencyName = it["name"].textValue()

            getResolvedVersion(name, version, dependencyName, metadata)?.let { dependencyVersion ->
                buildDependencyTree(dependencyName, dependencyVersion, packages, metadata)
            }
        }.toSortedSet()

        val id = extractCargoId(node)
        val pkg = packages.getValue(id)
        val linkage = if (isProjectDependency(id)) PackageLinkage.PROJECT_STATIC else PackageLinkage.STATIC

        return pkg.toReference(linkage, dependencies)
    }

    private fun getResolvedVersion(
        parentName: String,
        parentVersion: String,
        dependencyName: String,
        metadata: JsonNode
    ): String? {
        val node = metadata["resolve"]["nodes"].single {
            it["id"].textValue().startsWith("$parentName $parentVersion")
        }

        // This is null if the dependency is optional and the feature was not enabled. In this case the version was not
        // resolved and the dependency should not appear in the dependency tree.
        // See: https://doc.rust-lang.org/cargo/commands/cargo-metadata.html
        val dependency = node["dependencies"].find {
            it.textValue().startsWith(dependencyName)
        }

        return dependency?.textValue()?.split(' ')?.get(1)
    }

    override fun resolveDependencies(definitionFile: File): ProjectAnalyzerResult? {
        log.info { "Resolving dependencies for: '$definitionFile'" }

        // Get the project name and version. If one of them is missing return null, because this is a workspace
        // definition file that does not contain a project.
        val pkgDefinition = Toml().read(definitionFile)
        val projectName = pkgDefinition.getString("package.name") ?: return null
        val projectVersion = pkgDefinition.getString("package.version") ?: return null

        val workingDir = definitionFile.parentFile
        val metadataJson = runMetadata(workingDir)
        val metadata = jsonMapper.readTree(metadataJson)
        val hashes = readHashes(resolveLockfile(metadata))

        val packages = metadata["packages"].associateBy(
            { extractCargoId(it) },
            { extractPackage(it, hashes) }
        )

        val projectId = metadata["workspace_members"]
            .map { it.textValueOrEmpty() }
            .single { it.startsWith("$projectName $projectVersion") }

        val projectNode = metadata["packages"].single { it["id"].textValueOrEmpty() == projectId }

        fun filterDependencies(condition: (String) -> Boolean) =
            projectNode["dependencies"].filter { node ->
                val kind = node["kind"].textValueOrEmpty()
                condition(kind)
            }.mapNotNull {
                val dependencyName = it["name"].textValue()
                val version = getResolvedVersion(projectName, projectVersion, dependencyName, metadata)
                if (version == null) null else Pair(dependencyName, version)
            }

        val directDependencies = filterDependencies { kind -> kind != "dev" && kind != "build" }
        val directDevDependencies = filterDependencies { kind -> kind == "dev" }
        val directBuildDependencies = filterDependencies { kind -> kind == "build" }

        val dependencies = directDependencies.mapTo(sortedSetOf()) {
            buildDependencyTree(name = it.first, version = it.second, packages = packages, metadata = metadata)
        }
        val devDependencies = directDevDependencies.mapTo(sortedSetOf()) {
            buildDependencyTree(name = it.first, version = it.second, packages = packages, metadata = metadata)
        }
        val buildDependencies = directBuildDependencies.mapTo(sortedSetOf()) {
            buildDependencyTree(name = it.first, version = it.second, packages = packages, metadata = metadata)
        }

        val dependenciesScope = Scope(
            name = SCOPE_NAME_DEPENDENCIES,
            dependencies = dependencies
        )
        val devDependenciesScope = Scope(
            name = SCOPE_NAME_DEV_DEPENDENCIES,
            dependencies = devDependencies
        )
        val buildDependenciesScope = Scope(
            name = SCOPE_NAME_BUILD_DEPENDENCIES,
            dependencies = buildDependencies
        )

        val projectPkg = packages.values.single { pkg ->
            pkg.id.name == projectName && pkg.id.version == projectVersion
        }

        val homepageUrl = pkgDefinition.getString("package.homepage").orEmpty()
        val project = Project(
            id = projectPkg.id,
            definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
            declaredLicenses = projectPkg.declaredLicenses,
            vcs = projectPkg.vcs,
            vcsProcessed = processProjectVcs(workingDir, projectPkg.vcs, homepageUrl),
            homepageUrl = homepageUrl,
            scopes = sortedSetOf(dependenciesScope, devDependenciesScope, buildDependenciesScope)
        )

        val nonProjectPackages = packages
            .filterNot { isProjectDependency(it.key) }
            .map { it.value.toCuratedPackage() }
            .toSortedSet()

        return ProjectAnalyzerResult(project, nonProjectPackages)
    }
}
