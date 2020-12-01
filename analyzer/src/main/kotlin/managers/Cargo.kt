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

package org.ossreviewtoolkit.analyzer.managers

import com.fasterxml.jackson.databind.JsonNode

import com.moandjiezana.toml.Toml

import java.io.File
import java.util.SortedSet

import org.ossreviewtoolkit.analyzer.AbstractPackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.downloader.VcsHost
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageLinkage
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.jsonMapper
import org.ossreviewtoolkit.utils.CommandLineTool
import org.ossreviewtoolkit.utils.log
import org.ossreviewtoolkit.utils.textValueOrEmpty

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
        private val PATH_DEPENDENCY_REGEX = Regex("""^.*\(path\+file://(.*)\)$""")
    }

    override fun command(workingDir: File?) = "cargo"

    override fun transformVersion(output: String) = output.removePrefix("cargo ")

    private fun runMetadata(workingDir: File): String = run(workingDir, "metadata", "--format-version=1").stdout

    private fun extractCargoId(node: JsonNode) = node["id"].textValueOrEmpty()

    private fun extractPackageId(node: JsonNode) =
        Identifier(
            type = "Crate",
            namespace = "",
            name = node["name"].textValueOrEmpty(),
            version = node["version"].textValueOrEmpty()
        )

    private fun extractRepositoryUrl(node: JsonNode) = node["repository"].textValueOrEmpty()

    private fun extractVcsInfo(node: JsonNode) =
        VcsHost.toVcsInfo(extractRepositoryUrl(node))

    private fun extractDeclaredLicenses(node: JsonNode): SortedSet<String> {
        val licenses = node["license"].textValueOrEmpty().split('/')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        return if (licenses.isEmpty()) sortedSetOf() else sortedSetOf(licenses.joinToString(" OR "))
    }

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
        PATH_DEPENDENCY_REGEX.matchEntire(id)?.groups?.get(1)?.let { match ->
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
        // resolved and the dependency should not appear in the dependency tree. An example for a dependency string is
        // "bitflags 1.0.4 (registry+https://github.com/rust-lang/crates.io-index)", for more details see
        // https://doc.rust-lang.org/cargo/commands/cargo-metadata.html.
        node["dependencies"].forEach {
            val substrings = it.textValue().split(' ')
            require(substrings.size > 1) { "Unexpected format while parsing dependency JSON node." }

            if (substrings[0] == dependencyName) return substrings[1]
        }

        return null
    }

    override fun resolveDependencies(definitionFile: File): List<ProjectAnalyzerResult> {
        // Get the project name and version. If one of them is missing return null, because this is a workspace
        // definition file that does not contain a project.
        val pkgDefinition = Toml().read(definitionFile)
        val projectName = pkgDefinition.getString("package.name") ?: return emptyList()
        val projectVersion = pkgDefinition.getString("package.version") ?: return emptyList()

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
        val groupedDependencies = projectNode["dependencies"].groupBy { it["kind"].textValueOrEmpty() }

        fun getTransitiveDependencies(directDependencies: List<JsonNode>?, scope: String): Scope? {
            if (directDependencies == null) return null

            val transitiveDependencies = directDependencies
                .mapNotNull { dependency ->
                    val dependencyName = dependency["name"].textValue()
                    val version = getResolvedVersion(projectName, projectVersion, dependencyName, metadata)
                    version?.let { Pair(dependencyName, it) }
                }
                .map {
                    buildDependencyTree(name = it.first, version = it.second, packages = packages, metadata = metadata)
                }
                .toSortedSet()

            return Scope(scope, transitiveDependencies)
        }

        val scopes = listOfNotNull(
            getTransitiveDependencies(groupedDependencies[""], "dependencies"),
            getTransitiveDependencies(groupedDependencies["dev"], "dev-dependencies"),
            getTransitiveDependencies(groupedDependencies["build"], "build-dependencies")
        )

        val projectPkg = packages.values.single { pkg ->
            pkg.id.name == projectName && pkg.id.version == projectVersion
        }.let { it.copy(id = it.id.copy(type = managerName)) }

        val homepageUrl = pkgDefinition.getString("package.homepage").orEmpty()
        val project = Project(
            id = projectPkg.id,
            definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
            declaredLicenses = projectPkg.declaredLicenses,
            vcs = projectPkg.vcs,
            vcsProcessed = processProjectVcs(workingDir, projectPkg.vcs, homepageUrl),
            homepageUrl = homepageUrl,
            scopes = scopes.toSortedSet()
        )

        val nonProjectPackages = packages
            .filterNot { isProjectDependency(it.key) }
            .mapTo(sortedSetOf()) { it.value }

        return listOf(ProjectAnalyzerResult(project, nonProjectPackages))
    }
}
