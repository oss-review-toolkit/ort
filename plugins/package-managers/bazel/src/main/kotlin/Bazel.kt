/*
 * Copyright (C) 2024 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.bazel

import java.io.File
import java.util.Base64

import kotlinx.coroutines.runBlocking

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.analyzer.AbstractPackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.clients.bazelmoduleregistry.BazelModuleRegistryService
import org.ossreviewtoolkit.clients.bazelmoduleregistry.LocalBazelModuleRegistryService
import org.ossreviewtoolkit.clients.bazelmoduleregistry.ModuleMetadata
import org.ossreviewtoolkit.clients.bazelmoduleregistry.ModuleSourceInfo
import org.ossreviewtoolkit.clients.bazelmoduleregistry.RemoteBazelModuleRegistryService
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.HashAlgorithm
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageLinkage
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.collectDependencies
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.model.orEmpty
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.ProcessCapture
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.common.encodeHex
import org.ossreviewtoolkit.utils.common.withoutPrefix
import org.ossreviewtoolkit.utils.ort.createOrtTempFile

import org.semver4j.RangesList
import org.semver4j.RangesListFactory

private const val BAZEL_FALLBACK_VERSION = "7.0.1"
private const val LOCKFILE_NAME = "MODULE.bazel.lock"

class Bazel(
    name: String,
    analysisRoot: File,
    analyzerConfig: AnalyzerConfiguration,
    repoConfig: RepositoryConfiguration
) : PackageManager(name, analysisRoot, analyzerConfig, repoConfig), CommandLineTool {
    class Factory : AbstractPackageManagerFactory<Bazel>("Bazel") {
        override val globsForDefinitionFiles = listOf("MODULE", "MODULE.bazel")

        override fun create(
            analysisRoot: File,
            analyzerConfig: AnalyzerConfiguration,
            repoConfig: RepositoryConfiguration
        ) = Bazel(type, analysisRoot, analyzerConfig, repoConfig)
    }

    override fun command(workingDir: File?) = "bazel"

    override fun run(vararg args: CharSequence, workingDir: File?, environment: Map<String, String>): ProcessCapture =
        super.run(
            args = args,
            workingDir = workingDir,
            // Disable the optional wrapper script under `tools/bazel`, to ensure the --version option works.
            environment = environment + mapOf(
                "BAZELISK_SKIP_WRAPPER" to "true",
                "USE_BAZEL_FALLBACK_VERSION" to BAZEL_FALLBACK_VERSION
            )
        )

    override fun transformVersion(output: String) = output.removePrefix("bazel ")

    // Bazel 6.0 already supports bzlmod but it is not enabled by default.
    // Supporting it would require adding the flag "--enable_bzlmod=true" at the correct position of all bazel
    // invocations.
    override fun getVersionRequirement(): RangesList = RangesListFactory.create(">=7.0")

    override fun resolveDependencies(definitionFile: File, labels: Map<String, String>): List<ProjectAnalyzerResult> {
        val projectDir = definitionFile.parentFile
        val lockfile = projectDir.resolve(LOCKFILE_NAME)
        val projectVcs = processProjectVcs(projectDir)
        val moduleMetadata = Parser(definitionFile.readText()).parse()
        val depDirectives = moduleMetadata.dependencies.associateBy { "${it.name}@${it.version}" }
        val scopes = getDependencyGraph(projectDir, depDirectives)

        // If no lockfile is present, getDependencyGraph() runs "bazel mod graph", which creates a "MODULE.bazel.lock"
        // file as a side effect. That file contains the URL of the Bazel module registry that was used for dependency
        // resolution.
        val issues = mutableListOf<Issue>()
        val registry = determineRegistry(parseLockfile(lockfile), projectDir)

        val packages = if (registry != null) {
            val localPathOverrides = getLocalPathOverrides(projectDir)

            getPackages(scopes, registry, localPathOverrides, projectVcs)
        } else {
            issues += createAndLogIssue(managerName, "Bazel registry URL cannot be determined from the lockfile.")
            emptySet()
        }

        return listOf(
            ProjectAnalyzerResult(
                project = Project(
                    id = Identifier(
                        type = managerName,
                        namespace = "",
                        name = moduleMetadata.module?.name ?: VersionControlSystem.getPathInfo(definitionFile).path,
                        version = moduleMetadata.module?.version.orEmpty()
                    ),
                    definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
                    declaredLicenses = emptySet(),
                    vcs = projectVcs,
                    vcsProcessed = projectVcs,
                    homepageUrl = "",
                    scopeDependencies = scopes
                ),
                packages = packages,
                issues = issues
            )
        )
    }

    /**
     * Collect the local path overrides from the `MODULE.bazel` file in [workingDir]. The overrides are collected with
     * Buildozer and are returned as a map from package names to local path overrides. An empty map is returned if no
     * override is defined.
     */
    private fun getLocalPathOverrides(workingDir: File): Map<String, String> {
        /**
         * Normally, it should be possible to just run "buildozer 'print module_name path'
         * //MODULE.bazel:%local_path_override" to get the local path overrides. However, "path" is a special attribute
         * of the "print" command and several commands must be run instead.
         * See https://github.com/bazelbuild/buildtools/issues/1286.
         */
        val commandsFile = createOrtTempFile("buildozer").apply {
            writeText(
                "rename path not_path|print module_name not_path|" +
                    "rename not_path path|//MODULE.bazel:%local_path_override"
            )
        }

        val process = ProcessCapture(
            "buildozer",
            "-f", commandsFile.absolutePath,
            workingDir = workingDir
        )

        // Buildozer returns 3 "on success, when no changes were made". This value is returned regardless of whether
        // local path overrides are present or not.
        require(process.exitValue == 3) {
            "Failed to get local path overrides from 'buildozer': ${process.stderr}"
        }

        return process.stdout.lines().filter { it.isNotEmpty() }.associate { line ->
            val (moduleName, path) = line.split(' ', limit = 2)
            logger.info { "Local path override for module '$moduleName' is '$path'." }
            moduleName to path
        }
    }

    /**
     * Determine the Bazel module registry to use based on the Bazel version that generated the given [lockfile]. For
     * Bazel version >= 7.2.0, a [CompositeBazelModuleRegistryService] based on the "registryFileHashes" is returned.
     * For Bazel version < 7.2.0, either a [LocalBazelModuleRegistryService] or a [RemoteBazelModuleRegistryService]
     * based on the "cmdRegistries" is returned. Return null if the registry cannot be determined.
     */
    private fun determineRegistry(lockfile: Lockfile, projectDir: File): BazelModuleRegistryService? {
        // Bazel version < 7.2.0.
        if (lockfile.flags != null) {
            return MultiBazelModuleRegistryService.create(lockfile.registryUrls(), projectDir)
        }

        // Bazel version >= 7.2.0.
        if (lockfile.registryFileHashes != null) {
            return CompositeBazelModuleRegistryService.create(lockfile.registryFileHashes.keys, projectDir)
        }

        return null
    }

    /**
     * Get the packages for the dependencies of the given [scopes] using the given [registry], for the dependencies that
     * do not have a local path override. The dependencies having an override defined in [localPathOverrides] are
     * created without querying [registry] and will use [projectVcs] as VCS information.
     */
    private fun getPackages(
        scopes: Set<Scope>,
        registry: BazelModuleRegistryService,
        localPathOverrides: Map<String, String>,
        projectVcs: VcsInfo
    ): Set<Package> {
        val ids = scopes.collectDependencies()
        val (packageIdsWithPathOverride, otherPackageIds) = ids.partition { it.name in localPathOverrides }

        val result = mutableSetOf<Package>()

        packageIdsWithPathOverride.mapTo(result) {
            Package(
                id = it,
                declaredLicenses = emptySet(),
                description = "",
                homepageUrl = "",
                binaryArtifact = RemoteArtifact.EMPTY,
                sourceArtifact = RemoteArtifact.EMPTY,
                vcs = projectVcs.copy(path = localPathOverrides.getValue(it.name))
            )
        }

        val moduleMetadataForId = otherPackageIds.associateWith { getModuleMetadata(it, registry) }
        val moduleSourceInfoForId = otherPackageIds.associateWith { getModuleSourceInfo(it, registry) }
        return otherPackageIds.mapTo(result) {
            getPackage(it, moduleMetadataForId[it], moduleSourceInfoForId[it])
        }
    }

    private fun getPackage(id: Identifier, metadata: ModuleMetadata?, sourceInfo: ModuleSourceInfo?) =
        Package(
            id = id,
            declaredLicenses = emptySet(),
            description = "",
            homepageUrl = metadata?.homepage?.toString().orEmpty(),
            binaryArtifact = RemoteArtifact.EMPTY,
            sourceArtifact = sourceInfo?.toRemoteArtifact().orEmpty(),
            vcs = metadata?.toVcsInfo().orEmpty()
        )

    private fun getModuleMetadata(id: Identifier, registry: BazelModuleRegistryService): ModuleMetadata? =
        runCatching {
            runBlocking { registry.getModuleMetadata(id.name) }
        }.onFailure {
            logger.warn { "Failed to fetch metadata for Bazel module '${id.name}': ${it.collectMessages()}" }
        }.getOrNull()

    private fun getModuleSourceInfo(id: Identifier, registry: BazelModuleRegistryService): ModuleSourceInfo? =
        runCatching {
            runBlocking { registry.getModuleSourceInfo(id.name, id.version) }
        }.onFailure {
            logger.warn {
                "Failed to fetch source info for Bazel module '${id.name}@${id.version}': ${it.collectMessages()}"
            }
        }.getOrNull()

    private fun getDependencyGraph(projectDir: File, depDirectives: Map<String, BazelDepDirective>): Set<Scope> {
        val process = run("mod", "graph", "--output", "json", "--disk_cache=", workingDir = projectDir)
        val mainModule = process.stdout.parseBazelModule()
        val (mainDeps, devDeps) = mainModule.dependencies.partition { depDirectives[it.key]?.devDependency != true }

        return setOf(
            Scope(
                name = "main",
                dependencies = mainDeps.mapTo(mutableSetOf()) { it.toPackageReference() }
            ),
            Scope(
                name = "dev",
                dependencies = devDeps.mapTo(mutableSetOf()) { it.toPackageReference() }
            )
        )
    }

    private fun BazelModule.toPackageReference(): PackageReference =
        PackageReference(
            id = Identifier(
                type = managerName,
                namespace = "",
                name = name ?: key.substringBefore("@", ""),
                version = version ?: key.substringAfter("@", "")
            ),
            // Static linking is the default for cc_binary targets. According to the documentation, it is off for
            // cc_test, but according to experiments, it is on.
            // See https://bazel.build/reference/be/c-cpp#cc_binary.linkstatic
            linkage = PackageLinkage.STATIC,
            dependencies = dependencies.mapTo(mutableSetOf()) { it.toPackageReference() }
        )
}

private fun String.expandRepositoryUrl(): String = withoutPrefix("github:")?.let { "https://github.com/$it" } ?: this

private fun ModuleMetadata.toVcsInfo() =
    VcsInfo(
        type = VcsType.GIT,
        url = repository?.firstOrNull().orEmpty().expandRepositoryUrl(),
        revision = "",
        path = ""
    )

private fun ModuleSourceInfo.toRemoteArtifact(): RemoteArtifact {
    val (algo, b64digest) = integrity.split("-", limit = 2)
    val digest = Base64.getDecoder().decode(b64digest).encodeHex()

    val hash = Hash(
        value = digest,
        algorithm = HashAlgorithm.fromString(algo)
    )

    return RemoteArtifact(url = url.toString(), hash = hash)
}
