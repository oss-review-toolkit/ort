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
import java.net.URI

import kotlin.io.encoding.Base64

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.PackageManagerDependencyResult
import org.ossreviewtoolkit.analyzer.PackageManagerFactory
import org.ossreviewtoolkit.analyzer.determineEnabledPackageManagers
import org.ossreviewtoolkit.analyzer.withResolvedScopes
import org.ossreviewtoolkit.clients.bazelmoduleregistry.ArchiveOverride
import org.ossreviewtoolkit.clients.bazelmoduleregistry.ArchiveSourceInfo
import org.ossreviewtoolkit.clients.bazelmoduleregistry.BazelModuleRegistryService
import org.ossreviewtoolkit.clients.bazelmoduleregistry.GitRepositorySourceInfo
import org.ossreviewtoolkit.clients.bazelmoduleregistry.LocalBazelModuleRegistryService
import org.ossreviewtoolkit.clients.bazelmoduleregistry.LocalRepositorySourceInfo
import org.ossreviewtoolkit.clients.bazelmoduleregistry.METADATA_JSON
import org.ossreviewtoolkit.clients.bazelmoduleregistry.ModuleMetadata
import org.ossreviewtoolkit.clients.bazelmoduleregistry.ModuleSourceInfo
import org.ossreviewtoolkit.clients.bazelmoduleregistry.RemoteBazelModuleRegistryService
import org.ossreviewtoolkit.clients.bazelmoduleregistry.SOURCE_JSON
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
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.model.orEmpty
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.OrtPluginOption
import org.ossreviewtoolkit.plugins.api.PluginConfig
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.ProcessCapture
import org.ossreviewtoolkit.utils.common.alsoIfNull
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.common.div
import org.ossreviewtoolkit.utils.common.withoutPrefix
import org.ossreviewtoolkit.utils.ort.createOrtTempFile
import org.ossreviewtoolkit.utils.ort.runBlocking

import org.semver4j.range.RangeList
import org.semver4j.range.RangeListFactory

private const val BAZEL_FALLBACK_VERSION = "7.0.1"
private const val BAZEL_RC_FILE = ".bazelrc"
private const val BAZEL_RC_REGISTRY_PATTERN = "common --registry="
private const val LOCKFILE_NAME = "MODULE.bazel.lock"
private const val BUILDOZER_MISSING_VALUE = "(missing)"
private const val CONAN_REQUIRES_SCOPE_NAME = "conan_requires"
private const val CONAN_BUILD_TEST_SCOPE_NAME = "conan_test_requires"

data class BazelConfig(
    /**
     * The default name of the lockfile for the Conan package manager.
     */
    @OrtPluginOption(defaultValue = "conan.lock")
    val conanLockfileName: String,

    /**
     * Use Conan2 when fetching Conan packages, otherwise use Conan 1.
     */
    @OrtPluginOption(defaultValue = "false")
    val useConan2: Boolean,

    /**
     * Only scan Bazel dependencies and skip reporting Conan packages in the dependency tree.
     */
    @OrtPluginOption(defaultValue = "false")
    val bazelDependenciesOnly: Boolean
)

internal object BazelCommand : CommandLineTool {
    override fun command(workingDir: File?) = "bazel"

    override fun run(vararg args: CharSequence, workingDir: File?, environment: Map<String, String>): ProcessCapture =
        super.run(
            args = args,
            workingDir,
            // Disable the optional wrapper script under `tools/bazel` only for the "--version" call.
            environment + mapOf(
                "BAZELISK_SKIP_WRAPPER" to "${args[0] == getVersionArguments()}",
                "USE_BAZEL_FALLBACK_VERSION" to BAZEL_FALLBACK_VERSION
            )
        )

    override fun transformVersion(output: String) = output.removePrefix("bazel ")

    // Bazel 6.0 already supports bzlmod but it is not enabled by default.
    // Supporting it would require adding the flag "--enable_bzlmod=true" at the correct position of all bazel
    // invocations.
    override fun getVersionRequirement(): RangeList = RangeListFactory.create(">=7.0")
}

internal object BuildozerCommand : CommandLineTool {
    override fun command(workingDir: File?) = "buildozer"

    override fun transformVersion(output: String) =
        output.lineSequence().first().trim().removePrefix("buildozer version: ")
}

/**
 * This package manager does not only resolve Bazel dependency tree, it has also the ability to resolve Conan
 * dependencies and integrate them in the aforementioned tree. The Conan dependencies are created by the Conan package
 * manager and are filtered according to the list of Conan dependencies reported by Bazel.
 * Consequently, the Conan dependency in Bazel is not reported as a project, even if its definition file lies alongside
 * Bazel's ones. It iś rather reported as a package.
 * Please note that the conan project must use the `BazelDeps` and the `BazelToolchain` generators and must support
 * Conan 2.x.
 */
@OrtPlugin(
    displayName = "Bazel",
    description = "The Bazel package manager.",
    factory = PackageManagerFactory::class
)
class Bazel(
    override val descriptor: PluginDescriptor = BazelFactory.descriptor,
    private val config: BazelConfig
) : PackageManager("Bazel") {
    override val globsForDefinitionFiles = listOf("MODULE", "MODULE.bazel")

    /**
     * To avoid processing the module files in a local registry as definition files, ignore them if they are aside a
     * source.json file and under a directory with a metadata.json file. This simple metric avoids parsing the .bazelrc
     * in the top directory of the project to find out if a MODULE.bazel file is part of a local registry or not.
     */
    override fun mapDefinitionFiles(
        analysisRoot: File,
        definitionFiles: List<File>,
        analyzerConfig: AnalyzerConfiguration
    ): List<File> =
        definitionFiles.mapNotNull { file ->
            file.takeUnless {
                it.resolveSibling(SOURCE_JSON).isFile && it.parentFile.resolveSibling(METADATA_JSON).isFile
            }.alsoIfNull {
                logger.info { "Ignoring definition file '$file' as it is a module of a local registry." }
            }
        }

    override fun findPackageManagerDependencies(
        analysisRoot: File,
        managedFiles: Map<PackageManager, List<File>>,
        analyzerConfig: AnalyzerConfiguration
    ): PackageManagerDependencyResult {
        // If both Bazel and Conan are enabled, they should not run in parallel, because this may cause conflicts with
        // the Conan HOME files (see https://github.com/oss-review-toolkit/ort/pull/10357). Therefore, Bazel is always
        // run after Conan.
        val conanFactory = analyzerConfig.getConanFactory()
        val mustRunAfter = if (conanFactory != null) {
            setOf(conanFactory.descriptor.id)
        } else {
            emptySet()
        }

        return PackageManagerDependencyResult(mustRunBefore = emptySet(), mustRunAfter = mustRunAfter)
    }

    override fun resolveDependencies(
        analysisRoot: File,
        definitionFile: File,
        excludes: Excludes,
        analyzerConfig: AnalyzerConfiguration,
        labels: Map<String, String>
    ): List<ProjectAnalyzerResult> {
        val projectDir = definitionFile.parentFile
        val lockfile = projectDir / LOCKFILE_NAME
        val projectVcs = processProjectVcs(projectDir)
        val moduleMetadata = Parser(definitionFile.readText()).parse()
        val depDirectives = moduleMetadata.dependencies.associateBy { "${it.name}@${it.version}" }
        val archiveOverrides = getArchiveOverrides(projectDir)
        val issues = mutableListOf<Issue>()
        val (mainModule, scopes) = getDependencyGraph(
            projectDir,
            depDirectives,
            archiveOverrides,
            issues
        )

        val conanPackages = mainModule.extensionUsages
            .find { "conan_deps_module_extension.bzl" in it.key }
            .takeUnless { config.bazelDependenciesOnly }
            ?.let { extension ->
                val conanAnalyzerResults = resolveConanDependencies(
                    projectDir,
                    analysisRoot,
                    excludes,
                    analyzerConfig,
                    labels
                )

                processConanDependencies(
                    extension,
                    scopes,
                    conanAnalyzerResults
                )
            }.orEmpty()

        // If no lockfile is present, getDependencyGraph() runs "bazel mod graph", which creates a "MODULE.bazel.lock"
        // file as a side effect. That file contains the URL of the Bazel module registry that was used for dependency
        // resolution.
        val registry = determineRegistry(parseLockfile(lockfile), projectDir)

        val packages = if (registry != null) {
            val localPathOverrides = getLocalPathOverrides(projectDir)

            getPackages(scopes, registry, localPathOverrides, archiveOverrides, projectVcs)
        } else {
            issues += createAndLogIssue("Bazel registry URL cannot be determined from the lockfile.")
            emptySet()
        }

        return listOf(
            ProjectAnalyzerResult(
                project = Project(
                    id = Identifier(
                        type = projectType,
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
                packages = packages + conanPackages,
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

        val process = BuildozerCommand.run(
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
     * Collect the archive overrides from the `MODULE.bazel` file in [workingDir]. The overrides are collected with
     * Buildozer and are returned as a map from package names to archive overrides. An empty map is returned if no
     * override is defined.
     */
    private fun getArchiveOverrides(workingDir: File): Map<String, ArchiveOverride> {
        val process = BuildozerCommand.run(
            "print module_name urls integrity patches",
            "//MODULE.bazel:%archive_override",
            workingDir = workingDir
        ).requireSuccess()

        // If an optional attribute is missing, buildozer outputs first a warning line and then the result with the
        // value "(missing"):
        // $ buildozer 'print integrity patch_cmds ' //MODULE.bazel:%archive_override
        //      rule "//.:bazel-archive-override" has no attribute "patch_cmds"
        //      sha256-3B9PcEylbj1e3Zc/mKRfBIfQ8oxonQpXuiNhEhSLGDM= (missing)
        return process.stdout.lines().filterNot {
            it.isEmpty() || "has no attribute" in it
        }.associate { line ->
            val (moduleName, urlsAsString, integrity, patchesAsString) = line.split(' ', limit = 4)
            logger.info {
                "Archive override URL(s) for module '$moduleName': ${urlsAsString.removeSurrounding("[", "]")}"
            }

            val urls = urlsAsString.removeSurrounding("[", "]").split(',').map { URI(it) }

            val patches = patchesAsString.takeUnless { BUILDOZER_MISSING_VALUE in it }?.let {
                logger.warn {
                    "The module $moduleName has patches defined in the archive override, but ORT does not support " +
                        "patches for source artefacts yet (see issue #8452)."
                }

                patchesAsString.removeSurrounding("[", "]").split(',')
            }

            val integrityValue = integrity.takeUnless { BUILDOZER_MISSING_VALUE in it }

            moduleName to ArchiveOverride(
                moduleName = moduleName,
                integrity = integrityValue,
                patches = patches,
                urls = urls
            )
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
            // Bazel >= 7.2 lockfiles do not contain anymore the local registry URLs in the 'registryFileHashes'
            // property, so the .bazelrc file is parsed instead.
            val localRegistryServices = getRegistryUrlsFromBazelRcFile(projectDir).mapNotNull { url ->
                LocalBazelModuleRegistryService.create(url, projectDir)
            }

            return MultiBazelModuleRegistryService(
                localRegistryServices + CompositeBazelModuleRegistryService.create(
                    lockfile.registryFileHashes.keys,
                    projectDir
                )
            )
        }

        return null
    }

    /**
     * Get the packages for the dependencies of the given [scopes] using the given [registry], for the dependencies that
     * do not have a local path override. The dependencies having a local path override defined in [localPathOverrides]
     * are created without querying [registry] and will use [projectVcs] as VCS information. The dependencies having an
     * archive override defined in [archiveOverrides] are created without querying [registry] and will have their
     * version suppressed.
     */
    private fun getPackages(
        scopes: Set<Scope>,
        registry: BazelModuleRegistryService,
        localPathOverrides: Map<String, String>,
        archiveOverrides: Map<String, ArchiveOverride>,
        projectVcs: VcsInfo
    ): Set<Package> {
        // Some packages can exist both in the Bazel central registry and in Conan, for instance "fmt". Therefore, the
        // scopes are filtered to ignore the Conan scope. Additionally, all the Conan packages are generated by
        // processConanDependencies() and should not be created here.
        val ids = scopes.filterNot { it.name == CONAN_REQUIRES_SCOPE_NAME }
            .collectDependencies().filterNot { it.type == "Conan" && it.version.isEmpty() }

        // Fortunately, two partitions can be done in a row since it can safely be assumed that a package cannot have
        // both a local path override and an archive override.
        val (packageIdsWithPathOverride, idsFiltered) = ids.partition { it.name in localPathOverrides }
        val (packageIdsWithArchiveOverride, otherPackageIds) = idsFiltered.partition { it.name in archiveOverrides }

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

        packageIdsWithArchiveOverride.mapTo(result) {
            val archiveOverride = archiveOverrides.getValue(it.name)

            if (archiveOverride.urls.size > 1) {
                logger.warn {
                    "The module '${it.name}' has multiple archive override URLs defined. Only the first URL of the " +
                        "following URLs will be used: ${archiveOverride.urls.joinToString()}"
                }
            }

            val hash = archiveOverride.integrity?.let { integrity ->
                val (algo, b64digest) = integrity.split('-', limit = 2)
                val digest = Base64.decode(b64digest).toHexString()
                Hash(
                    value = digest,
                    algorithm = HashAlgorithm.fromString(algo)
                )
            }

            Package(
                id = it,
                declaredLicenses = emptySet(),
                description = "",
                homepageUrl = "",
                binaryArtifact = RemoteArtifact.EMPTY,
                sourceArtifact = RemoteArtifact(
                    url = archiveOverride.urls.first().toString(),
                    hash = hash ?: Hash.NONE
                ),
                vcs = VcsInfo.EMPTY,
                isModified = archiveOverride.patches?.isNotEmpty() == true
            )
        }

        val moduleMetadataForId = otherPackageIds.associateWith { getModuleMetadata(it, registry) }
        val moduleSourceInfoForId = otherPackageIds.associateWith { getModuleSourceInfo(it, registry) }
        return otherPackageIds.mapTo(result) {
            getPackage(it, moduleMetadataForId[it], moduleSourceInfoForId[it])
        }
    }

    private fun getPackage(id: Identifier, metadata: ModuleMetadata?, sourceInfo: ModuleSourceInfo?): Package =
        Package(
            id = id,
            declaredLicenses = emptySet(),
            description = "",
            homepageUrl = metadata?.homepage?.toString().orEmpty(),
            binaryArtifact = RemoteArtifact.EMPTY,
            sourceArtifact = sourceInfo?.toRemoteArtifact() ?: RemoteArtifact.EMPTY,
            vcs = sourceInfo?.toVcsInfo() ?: metadata?.toVcsInfo().orEmpty()
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

    private fun getDependencyGraph(
        projectDir: File,
        depDirectives: Map<String, BazelDepDirective>,
        archiveOverrides: Map<String, ArchiveOverride>,
        issues: MutableList<Issue>
    ): Pair<BazelModule, MutableSet<Scope>> {
        val process = BazelCommand.run(
            "mod",
            "graph",
            "--output",
            "json",
            "--disk_cache=",
            "--lockfile_mode=update",
            "--extension_info=all",
            workingDir = projectDir
        ).requireSuccess()

        if (process.stderr.isNotEmpty()) {
            process.stderr.lines()
                .filter { it.startsWith("WARNING") && "Please update the version in your MODULE.bazel" in it }
                .forEach { issues += createAndLogIssue(it) }
        }

        val mainModule = process.stdout.parseBazelModule()
        val (mainDeps, devDeps) = mainModule.dependencies.map { module ->
            val name = module.name ?: module.key.substringBefore("@", "")
            val version = module.version ?: module.key.substringAfter("@", "")

            val overriddenKey = "$name@$version"
            if (overriddenKey != module.key) {
                logger.info { "Using overridden module key '$overriddenKey' instead of '${module.key}'." }
                module.copy(key = "$name@$version")
            } else {
                module
            }
        }.partition {
            val depDirective = depDirectives[it.key]

            if (depDirective == null) {
                logger.warn { "No dependency directive found for '${it.key}'. Assuming it is a main dependency." }
                true
            } else {
                !depDirective.devDependency
            }
        }

        return mainModule to mutableSetOf(
            Scope(
                name = "main",
                dependencies = mainDeps.mapTo(mutableSetOf()) { it.toPackageReference(archiveOverrides) }
            ),
            Scope(
                name = "dev",
                dependencies = devDeps.mapTo(mutableSetOf()) { it.toPackageReference(archiveOverrides) }
            )
        )
    }

    /**
     * Convert a [BazelModule] to a [PackageReference].
     */
    private fun BazelModule.toPackageReference(archiveOverrides: Map<String, ArchiveOverride>): PackageReference =
        PackageReference(
            id = Identifier(
                type = "Bazel",
                namespace = "",
                name = key.substringBefore("@", ""),
                version = key.substringAfter("@", "")
            ),
            // Static linking is the default for cc_binary targets. According to the documentation, it is off for
            // cc_test, but according to experiments, it is on.
            // See https://bazel.build/reference/be/c-cpp#cc_binary.linkstatic
            linkage = PackageLinkage.STATIC,
            dependencies = dependencies.mapTo(mutableSetOf()) { it.toPackageReference(archiveOverrides) }
        )

    /**
     * Convert a module source info to a [VcsInfo] or return 'null' if the VCS is not relevant for this module.
     */
    private fun ModuleSourceInfo.toVcsInfo(): VcsInfo? =
        when (this) {
            is GitRepositorySourceInfo -> VcsInfo(
                type = VcsType.GIT,
                url = remote,
                revision = commit.orEmpty()
            )

            is LocalRepositorySourceInfo -> vcs

            is ArchiveSourceInfo -> null
        }

    /**
     * Process the Conan dependencies [conanAnalyzerResults] returned by `resolveConanDependencies`, based on the
     * [extension] information returned by 'bazel mod graph'. The newly created scopes are added to [scopes] and the
     * Conan packages are returned.
     */
    private fun processConanDependencies(
        extension: BazelExtension,
        scopes: MutableSet<Scope>,
        conanAnalyzerResults: List<ProjectAnalyzerResult>
    ): MutableSet<Package> {
        val conanPackages = mutableSetOf<Package>()
        val conanProjectReferences = mutableSetOf<PackageReference>()
        val conanProjectWithDependencies = mutableSetOf<PackageReference>()
        val conanProjectWithTestDependencies = mutableSetOf<PackageReference>()

        conanPackages += conanAnalyzerResults.map { it.project.toPackage() }

        conanAnalyzerResults.forEach { conanAnalyzerResult ->
            val projectReference = conanAnalyzerResult.project.toPackage().toReference()
            conanProjectReferences += projectReference

            val dependenciesPerScopes = conanAnalyzerResult.project.scopes.associate {
                // Only include the packages reported by Bazel.
                it.name to it.dependencies.filterTo(mutableSetOf()) { dep -> dep.id.name in extension.usedRepos }
            }

            conanProjectWithDependencies += projectReference.copy(
                dependencies = dependenciesPerScopes["requires"].orEmpty()
            )
            conanProjectWithTestDependencies += projectReference.copy(
                dependencies = dependenciesPerScopes["test_requires"].orEmpty()
            )
            conanPackages += conanAnalyzerResult.packages.filter { pkg ->
                (conanProjectWithDependencies + conanProjectWithTestDependencies).flatMap {
                    it.dependencies
                }.any { it.id == pkg.id }
            }
        }

        scopes.mapTo(mutableSetOf()) {
            if (it.name == "main") {
                it.copy(dependencies = it.dependencies + conanProjectReferences)
            } else {
                it
            }
        }.also {
            scopes.clear()
            scopes.addAll(it)
        }

        scopes += Scope(
            name = CONAN_REQUIRES_SCOPE_NAME,
            dependencies = conanProjectWithDependencies
        )
        scopes += Scope(
            name = CONAN_BUILD_TEST_SCOPE_NAME,
            dependencies = conanProjectWithTestDependencies
        )

        return conanPackages
    }

    /**
     * Check if the Bazel project in [projectDir] has some Conan dependencies. If it does, load them from a Conan
     * definition file in [projectDir].
     */
    private fun resolveConanDependencies(
        projectDir: File,
        analysisRoot: File,
        excludes: Excludes,
        analyzerConfig: AnalyzerConfiguration,
        labels: Map<String, String>
    ): List<ProjectAnalyzerResult> {
        val conanFactory = analyzerConfig.getConanFactory()
        if (conanFactory == null) {
            logger.info { "Not fetching Conan dependencies since Conan package manager is not enabled." }
            return emptyList()
        }

        val conanConfig = PluginConfig(
            mapOf(
                "lockfileName" to config.conanLockfileName,
                "useConan2" to config.useConan2.toString()
            )
        )
        val conan = conanFactory.create(conanConfig)

        val conanDefinitionFile = conan.matchersForDefinitionFiles.mapNotNull { glob ->
            val filesMatchingGlob = projectDir.walk().maxDepth(1).filter { glob.matches(it.toPath()) }
            filesMatchingGlob.toList().takeUnless { it.isEmpty() }
        }.flatten().firstOrNull()

        if (conanDefinitionFile == null) {
            return emptyList()
        }

        return conan.resolveDependencies(
            analysisRoot,
            listOf(conanDefinitionFile),
            excludes,
            analyzerConfig,
            labels
        ).run {
            projectResults.getValue(conanDefinitionFile).map { result ->
                val project = result.project.withResolvedScopes(dependencyGraph)
                result.copy(project = project, packages = result.packages)
            }
        }
    }
}

private fun String.expandRepositoryUrl(): String = withoutPrefix("github:")?.let { "https://github.com/$it" } ?: this

private fun ModuleMetadata.toVcsInfo() =
    VcsInfo(
        type = VcsType.GIT,
        url = repository?.firstOrNull().orEmpty().expandRepositoryUrl(),
        revision = ""
    )

private fun ModuleSourceInfo.toRemoteArtifact(): RemoteArtifact? =
    when (this) {
        is ArchiveSourceInfo -> {
            val hash = if (integrity.isNotEmpty()) {
                val (algo, b64digest) = integrity.split('-', limit = 2)
                val digest = Base64.decode(b64digest).toHexString()
                Hash(
                    value = digest,
                    algorithm = HashAlgorithm.fromString(algo)
                )
            } else {
                Hash.NONE
            }

            RemoteArtifact(url = url.toString(), hash = hash)
        }

        is GitRepositorySourceInfo, is LocalRepositorySourceInfo -> {
            // In case of a Git repository or a local path repository, no source artifact is available and the
            // repository information will be used by the `vcs` and 'vcs_processed' properties.
            null
        }
    }

private fun AnalyzerConfiguration.getConanFactory() =
    determineEnabledPackageManagers().find { it.descriptor.id.startsWith("Conan") }

private fun getRegistryUrlsFromBazelRcFile(projectDir: File): Set<String> {
    val bazelRcFile = projectDir.resolve(BAZEL_RC_FILE).takeIf { it.isFile } ?: return emptySet()
    return bazelRcFile.readLines().mapNotNullTo(mutableSetOf()) { it.withoutPrefix(BAZEL_RC_REGISTRY_PATTERN) }
}
