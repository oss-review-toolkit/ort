/*
 * Copyright (C) 2019 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
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

@file:Suppress("TooManyFunctions")

package org.ossreviewtoolkit.plugins.packagemanagers.cargo

import java.io.File

import net.peanuuutz.tomlkt.decodeFromNativeReader

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.PackageManagerFactory
import org.ossreviewtoolkit.analyzer.parseAuthorString
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
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.orEmpty
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.div
import org.ossreviewtoolkit.utils.common.unquote
import org.ossreviewtoolkit.utils.common.withoutPrefix
import org.ossreviewtoolkit.utils.ort.DeclaredLicenseProcessor
import org.ossreviewtoolkit.utils.spdx.SpdxConstants
import org.ossreviewtoolkit.utils.spdx.SpdxOperator

private const val DEFAULT_KIND_NAME = "normal"
private const val DEV_KIND_NAME = "dev"
private const val BUILD_KIND_NAME = "build"

internal object CargoCommand : CommandLineTool {
    override fun command(workingDir: File?) = "cargo"

    override fun transformVersion(output: String) =
        // The version string can be something like:
        // cargo 1.35.0 (6f3e9c367 2019-04-04)
        output.removePrefix("cargo ").substringBefore(' ')
}

/**
 * The [Cargo](https://doc.rust-lang.org/cargo/) package manager for Rust.
 */
@OrtPlugin(
    displayName = "Cargo",
    description = "The Cargo package manager for Rust.",
    factory = PackageManagerFactory::class
)
class Cargo(override val descriptor: PluginDescriptor = CargoFactory.descriptor) : PackageManager("Cargo") {
    override val globsForDefinitionFiles = listOf("Cargo.toml")

    /**
     * Cargo.lock is located next to Cargo.toml or in one of the parent directories. The latter is the case when the
     * project is part of a workspace. Cargo.lock is then located next to the Cargo.toml file defining the workspace.
     */
    private fun resolveLockfile(analysisRoot: File, metadata: CargoMetadata, allowDynamicVersions: Boolean): File {
        val workspaceRoot = File(metadata.workspaceRoot)
        val lockfile = workspaceRoot / "Cargo.lock"

        requireLockfile(analysisRoot, workspaceRoot, allowDynamicVersions) { lockfile.isFile }

        return lockfile
    }

    private fun readHashes(lockfile: File): Map<String, String> {
        if (!lockfile.isFile) {
            logger.debug { "Cannot determine the hashes of remote artifacts because the Cargo lockfile is missing." }
            return emptyMap()
        }

        val contents = lockfile.reader().use { toml.decodeFromNativeReader<CargoLockfile>(it) }

        if (contents.version == null) {
            val checksumMetadata = contents.metadata.mapNotNull { (k, v) ->
                // Lockfile version 1 uses strings like:
                // "checksum cfg-if 0.1.9 (registry+https://github.com/rust-lang/crates.io-index)"
                k.unquote().withoutPrefix("checksum ")?.let { it to v }
            }.toMap()

            if (checksumMetadata.isNotEmpty()) return checksumMetadata
        }

        return when (contents.version) {
            null, 2, 3, 4 -> {
                contents.packages.mapNotNull { pkg ->
                    pkg.checksum?.let { checksum ->
                        // Use the same key format as for version 1, see above.
                        val key = "${pkg.name} ${pkg.version} (${pkg.source})"
                        key to checksum
                    }
                }.toMap()
            }

            else -> throw IllegalArgumentException("Unsupported lockfile version ${contents.version}.")
        }
    }

    override fun mapDefinitionFiles(
        analysisRoot: File,
        definitionFiles: List<File>,
        analyzerConfig: AnalyzerConfiguration
    ): List<File> {
        fun File.isVirtualWorkspace(): Boolean {
            var foundWorkspace = false
            var foundPackage = false

            forEachLine { line ->
                if (!foundWorkspace && line.startsWith("[workspace]")) foundWorkspace = true
                if (!foundPackage && line.startsWith("[package]")) foundPackage = true
            }

            return foundWorkspace && !foundPackage
        }

        // A virtual workspace does not define any packages and thus can be skipped, see
        // https://doc.rust-lang.org/cargo/reference/workspaces.html#virtual-workspace.
        return definitionFiles.mapNotNull { file -> file.takeUnless { it.isVirtualWorkspace() } }
    }

    override fun resolveDependencies(
        analysisRoot: File,
        definitionFile: File,
        excludes: Excludes,
        analyzerConfig: AnalyzerConfiguration,
        labels: Map<String, String>
    ): List<ProjectAnalyzerResult> {
        val workingDir = definitionFile.parentFile
        val metadataProcess = CargoCommand.run("metadata", "--format-version=1", "--manifest-path=$definitionFile")
            .requireSuccess()
        val metadata = json.decodeFromString<CargoMetadata>(metadataProcess.stdout)

        // Virtual workspaces have been filtered out in "mapDefinitionFiles".
        val projectId = checkNotNull(metadata.resolve.root)

        val projectNode = metadata.resolve.nodes.single { it.id == projectId }
        val depNodesByKind = mutableMapOf<String, MutableList<CargoMetadata.Node>>()
        projectNode.deps.forEach { dep ->
            val depNode = metadata.resolve.nodes.single { it.id == dep.pkg }

            dep.depKinds.forEach { depKind ->
                depNodesByKind.getOrPut(depKind.kind ?: DEFAULT_KIND_NAME) { mutableListOf() } += depNode
            }
        }

        val packageById = metadata.packages.associateBy { it.id }

        fun Collection<CargoMetadata.Node>.toPackageReferences(): Set<PackageReference> =
            mapNotNullTo(mutableSetOf()) { node ->
                // TODO: Handle renamed dependencies here, see:
                //       https://doc.rust-lang.org/cargo/reference/specifying-dependencies.html#renaming-dependencies-in-cargotoml
                val dependencyNodes = node.deps.filter { dep ->
                    // Only normal dependencies are transitive.
                    dep.depKinds.any { it.kind == null }
                }.map { dep ->
                    metadata.resolve.nodes.single { it.id == dep.pkg }
                }

                val pkg = packageById.getValue(node.id)
                PackageReference(
                    id = Identifier("Crate", "", pkg.name, pkg.version),
                    linkage = if (pkg.isProject(analysisRoot)) PackageLinkage.PROJECT_STATIC else PackageLinkage.STATIC,
                    dependencies = dependencyNodes.toPackageReferences()
                )
            }

        val scopes = setOfNotNull(
            depNodesByKind[DEFAULT_KIND_NAME]?.let { Scope("dependencies", it.toPackageReferences()) },
            depNodesByKind[DEV_KIND_NAME]?.let { Scope("dev-dependencies", it.toPackageReferences()) },
            depNodesByKind[BUILD_KIND_NAME]?.let { Scope("build-dependencies", it.toPackageReferences()) }
        )

        val hashes = readHashes(resolveLockfile(analysisRoot, metadata, analyzerConfig.allowDynamicVersions))
        val projectPkg = packageById.getValue(projectId).let { cargoPkg ->
            cargoPkg.toPackage(hashes).let { it.copy(id = it.id.copy(type = projectType)) }
        }

        val project = Project(
            id = projectPkg.id,
            definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
            authors = projectPkg.authors,
            declaredLicenses = projectPkg.declaredLicenses,
            declaredLicensesProcessed = projectPkg.declaredLicensesProcessed,
            vcs = projectPkg.vcs,
            vcsProcessed = processProjectVcs(workingDir, projectPkg.vcs, projectPkg.homepageUrl),
            homepageUrl = projectPkg.homepageUrl,
            scopeDependencies = scopes
        )

        val nonProjectPackages = packageById.values.mapNotNullTo(mutableSetOf()) { cargoPkg ->
            cargoPkg.takeUnless { it.isProject(analysisRoot) }?.toPackage(hashes)
        }

        return listOf(ProjectAnalyzerResult(project, nonProjectPackages))
    }
}

/**
 * Return the local path for this Cargo package if applicable, or null if the Cargo package is not local.
 */
private fun CargoMetadata.Package.getLocalPath(): File? =
    id.substringAfter("path+file://", "").ifEmpty { null }
        ?.removeSuffix(")")?.substringBefore("#")?.let { File(it) }

/**
 * Return whether this Cargo package is supposed to be regarded as an ORT project. The [analysisRoot] is used to check
 * whether this Cargo package lives within the analyzer root.
 */
private fun CargoMetadata.Package.isProject(analysisRoot: File): Boolean {
    val isWithinAnalyzerRoot = getLocalPath()?.startsWith(analysisRoot.absoluteFile) == true

    // If a package cannot be retrieved from anywhere but lies within the analyzer root, treat it as a project.
    return source == null && isWithinAnalyzerRoot
}

private fun CargoMetadata.Package.toPackage(hashes: Map<String, String>): Package {
    val declaredLicenses = parseDeclaredLicenses()

    // While the previously used "/" was not explicit about the intended license operator, the community consensus
    // seems to be that an existing "/" should be interpreted as "OR", see e.g. the discussions at
    // https://github.com/rust-lang/cargo/issues/2039
    // https://github.com/rust-lang/cargo/pull/4920
    val declaredLicensesProcessed = DeclaredLicenseProcessor.process(declaredLicenses, operator = SpdxOperator.OR)

    val vcs = repository?.let { VcsHost.parseUrl(it) }.orEmpty()
    val vcsProcessed = getLocalPath()?.let { PackageManager.processProjectVcs(it) } ?: vcs.normalize()

    return Package(
        id = Identifier(
            type = "Crate",
            // Note that Rust / Cargo do not support package namespaces, see:
            // https://samsieber.tech/posts/2020/09/registry-structure-influence/
            namespace = "",
            name = name,
            version = version
        ),
        authors = authors.flatMap { parseAuthorString(it) }.mapNotNullTo(mutableSetOf()) { it.name },
        declaredLicenses = declaredLicenses,
        declaredLicensesProcessed = declaredLicensesProcessed,
        description = description.orEmpty(),
        binaryArtifact = RemoteArtifact.EMPTY,
        sourceArtifact = parseSourceArtifact(hashes).orEmpty(),
        homepageUrl = homepage.orEmpty(),
        vcs = vcs,
        vcsProcessed = vcsProcessed
    )
}

private fun CargoMetadata.Package.parseDeclaredLicenses(): Set<String> {
    val declaredLicenses = license.orEmpty().split('/')
        .map { it.trim() }
        .filterTo(mutableSetOf()) { it.isNotEmpty() }

    // Cargo allows declaring non-SPDX licenses only by referencing a license file. If a license file is specified, add
    // an unknown declared license to indicate that there is a declared license, but we cannot know which it is at this
    // point.
    // See: https://doc.rust-lang.org/cargo/reference/manifest.html#the-license-and-license-file-fields
    if (licenseFile.orEmpty().isNotBlank()) {
        declaredLicenses += SpdxConstants.NOASSERTION
    }

    return declaredLicenses
}

// Match source dependencies that directly reference git repositories. The specified tag or branch
// name is ignored (i.e. not captured) in favor of the actual commit hash that they currently refer
// to.
// See https://doc.rust-lang.org/cargo/reference/specifying-dependencies.html#specifying-dependencies-from-git-repositories
// for the specification for this kind of dependency.
private val GIT_DEPENDENCY_REGEX = Regex("git\\+(https://.*)\\?(?:rev|tag|branch)=.+#([0-9a-fA-F]{7,40})")

private fun CargoMetadata.Package.parseSourceArtifact(hashes: Map<String, String>): RemoteArtifact? {
    val source = source ?: return null

    if (source == "registry+https://github.com/rust-lang/crates.io-index") {
        val url = "https://crates.io/api/v1/crates/$name/$version/download"
        val key = "$name $version ($source)"
        val hash = Hash.create(hashes[key].orEmpty())
        return RemoteArtifact(url, hash)
    }

    val match = GIT_DEPENDENCY_REGEX.matchEntire(source) ?: return null
    val (url, hash) = match.destructured
    return RemoteArtifact(url, Hash.create(hash))
}
