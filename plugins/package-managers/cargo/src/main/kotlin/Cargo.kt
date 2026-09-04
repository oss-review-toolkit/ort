/*
 * Copyright (C) 2019 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.packagemanagers.cargo

import java.io.File

import net.peanuuutz.tomlkt.decodeFromNativeReader

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.PackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManagerResult
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.Includes
import org.ossreviewtoolkit.model.utils.DependencyGraphBuilder
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.alsoIfNull
import org.ossreviewtoolkit.utils.common.div
import org.ossreviewtoolkit.utils.common.unquote
import org.ossreviewtoolkit.utils.common.withoutPrefix

private const val PROJECT_TYPE = "Cargo"
internal const val PACKAGE_TYPE = "Crate"

/**
 * A map from the Cargo dependency kinds, see https://doc.rust-lang.org/cargo/commands/cargo-metadata.html, to the names
 * of the corresponding ORT scopes.
 */
private val SCOPE_NAME_FOR_KIND = mapOf(
    null to "dependencies",
    "dev" to "dev-dependencies",
    "build" to "build-dependencies"
)

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
    summary = "The Cargo package manager for Rust.",
    factory = PackageManagerFactory::class
)
class Cargo(override val descriptor: PluginDescriptor = CargoFactory.descriptor) : PackageManager(PROJECT_TYPE) {
    override val globsForDefinitionFiles = listOf("Cargo.toml")

    private val graphBuilder = DependencyGraphBuilder(CargoDependencyHandler())

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
        return definitionFiles.mapNotNull { file ->
            file.takeUnless { it.isVirtualWorkspace() }.alsoIfNull {
                logger.info { "Skipping virtual workspace '$file'." }
            }
        }
    }

    override fun resolveDependencies(
        analysisRoot: File,
        definitionFile: File,
        excludes: Excludes,
        includes: Includes,
        analyzerConfig: AnalyzerConfiguration,
        labels: Map<String, String>
    ): List<ProjectAnalyzerResult> {
        val workingDir = definitionFile.parentFile
        val metadataProcess = CargoCommand.run("metadata", "--format-version=1", "--manifest-path=$definitionFile")
            .requireSuccess()
        val metadata = json.decodeFromString<CargoMetadata>(metadataProcess.stdout)

        // Virtual workspaces have been filtered out in "mapDefinitionFiles".
        val projectId = checkNotNull(metadata.resolve.root)

        val hashes = readHashes(resolveLockfile(analysisRoot, metadata, analyzerConfig.allowDynamicVersions))
        val dependencies = metadata.toDependencies(analysisRoot, hashes)

        val projectPkg = dependencies.getValue(projectId).pkg.let { it.copy(id = it.id.copy(type = projectType)) }

        val projectNode = metadata.resolve.nodes.single { it.id == projectId }
        val dependenciesByScopeName = mutableMapOf<String, MutableSet<CargoDependency>>()
        projectNode.deps.forEach { dep ->
            dep.depKinds.forEach { depKind ->
                SCOPE_NAME_FOR_KIND[depKind.kind]?.also { scopeName ->
                    dependenciesByScopeName.getOrPut(scopeName) { mutableSetOf() } += dependencies.getValue(dep.pkg)
                }
            }
        }

        dependenciesByScopeName.forEach { (scopeName, scopeDependencies) ->
            graphBuilder.addDependencies(projectPkg.id, scopeName, scopeDependencies)
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
            scopeNames = graphBuilder.scopesFor(projectPkg.id)
        )

        return listOf(ProjectAnalyzerResult(project, emptySet()))
    }

    override fun createPackageManagerResult(projectResults: Map<File, List<ProjectAnalyzerResult>>) =
        PackageManagerResult(projectResults, graphBuilder.build(), graphBuilder.packages())
}
