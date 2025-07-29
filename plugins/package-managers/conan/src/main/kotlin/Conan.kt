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

package org.ossreviewtoolkit.plugins.packagemanagers.conan

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.YamlScalar
import com.charleskorn.kaml.yamlList
import com.charleskorn.kaml.yamlMap
import com.charleskorn.kaml.yamlScalar

import java.io.File

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.PackageManagerFactory
import org.ossreviewtoolkit.analyzer.parseAuthorString
import org.ossreviewtoolkit.downloader.VcsHost
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.HashAlgorithm
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.OrtPluginOption
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.alsoIfNull
import org.ossreviewtoolkit.utils.common.div
import org.ossreviewtoolkit.utils.common.masked
import org.ossreviewtoolkit.utils.common.safeDeleteRecursively
import org.ossreviewtoolkit.utils.common.stashDirectories
import org.ossreviewtoolkit.utils.common.toUri
import org.ossreviewtoolkit.utils.ort.ORT_CONFIG_FILENAME
import org.ossreviewtoolkit.utils.ort.createOrtTempDir
import org.ossreviewtoolkit.utils.ort.requestPasswordAuthentication

import org.semver4j.range.RangeList
import org.semver4j.range.RangeListFactory

internal class ConanCommand(private val useConan2: Boolean = false) : CommandLineTool {
    override fun command(workingDir: File?) = if (useConan2) "conan2" else "conan"

    override fun transformVersion(output: String) =
        // Conan could report version strings like:
        // Conan version 1.18.0
        output.removePrefix("Conan version ")

    override fun getVersionRequirement(): RangeList = RangeListFactory.create(">=1.44.0 <3.0")

    override fun run(vararg args: CharSequence, workingDir: File?, environment: Map<String, String>) =
        super.run(args = args, workingDir, environment + ("CONAN_NON_INTERACTIVE" to "1"))
}

data class ConanConfig(
    /**
     * The name of the lockfile, which is used for analysis if allowDynamicVersions is set to false. The lockfile should
     * be located in the analysis root. Currently only one lockfile is supported per Conan project.
     */
    val lockfileName: String?,

    /**
     * If true, the Conan package manager will call a command called "conan2" instead of "conan". This is required to
     * be able to support both Conan major versions in a given environment e.g., the ORT Docker image or a local
     * development environment.
     */
    @OrtPluginOption(defaultValue = "false")
    val useConan2: Boolean
)

/**
 * The [Conan](https://conan.io/) package manager for C / C++.
 *
 * TODO: Add support for `python_requires`.
 */
@OrtPlugin(
    displayName = "Conan",
    description = "The Conan package manager for C / C++.",
    factory = PackageManagerFactory::class
)
@Suppress("TooManyFunctions")
class Conan(
    override val descriptor: PluginDescriptor = ConanFactory.descriptor,
    private val config: ConanConfig
) : PackageManager("Conan") {
    companion object {
        internal val DUMMY_COMPILER_SETTINGS = arrayOf(
            "-s", "compiler=gcc",
            "-s", "compiler.libcxx=libstdc++",
            "-s", "compiler.version=11.1"
        )

        internal const val SCOPE_NAME_DEPENDENCIES = "requires"
        internal const val SCOPE_NAME_DEV_DEPENDENCIES = "build_requires"
        internal const val SCOPE_NAME_TEST_DEPENDENCIES = "test_requires"
    }

    internal val command by lazy { ConanCommand(config.useConan2) }

    override val globsForDefinitionFiles = listOf("conanfile*.txt", "conanfile*.py")

    private val handler by lazy {
        if (command.getVersion().startsWith("1.")) {
            ConanV1Handler(this)
        } else {
            ConanV2Handler(this)
        }
    }

    // This is where Conan caches downloaded packages [1]. Note that the package cache is not concurrent, and its
    // layout does not support packages from different remotes that are named (and versioned) the same.
    //
    // TODO: Consider using the experimental (and by default disabled) download cache [2] to lift these limitations.
    //
    // [1]: https://docs.conan.io/en/latest/reference/config_files/conan.conf.html#storage
    // [2]: https://docs.conan.io/en/latest/configuration/download_cache.html#download-cache
    internal val conanStoragePath by lazy { handler.getConanStoragePath() }

    private val pkgInspectResults = mutableMapOf<String, JsonObject>()

    private fun hasLockfile(file: String) = File(file).isFile

    /**
     * If a Bazel project uses some Conan packages, the corresponding Conan files should not be picked up by the Conan
     * package manager. Therefore, the Conan file is checked to NOT contain the BazelDeps and BazelToolchain generators.
     */
    override fun mapDefinitionFiles(
        analysisRoot: File,
        definitionFiles: List<File>,
        analyzerConfig: AnalyzerConfiguration
    ): List<File> =
        definitionFiles.mapNotNull { file ->
            file.takeUnless {
                val content = it.readText()
                "BazelDeps" in content || "BazelToolchain" in content
            }.alsoIfNull {
                logger.info { "Ignoring definition file '$file' as it is used from Bazel." }
            }
        }

    override fun beforeResolution(
        analysisRoot: File,
        definitionFiles: List<File>,
        analyzerConfig: AnalyzerConfiguration
    ) = command.checkVersion()

    /**
     * Primary method for resolving dependencies from [definitionFile].
     */
    override fun resolveDependencies(
        analysisRoot: File,
        definitionFile: File,
        excludes: Excludes,
        analyzerConfig: AnalyzerConfiguration,
        labels: Map<String, String>
    ): List<ProjectAnalyzerResult> =
        try {
            resolvedDependenciesInternal(analysisRoot, definitionFile, analyzerConfig.allowDynamicVersions)
        } finally {
            // Clear the inspection result cache, because we call "conan config install" for each definition file which
            // could overwrite the remotes and result in different metadata for packages with the same name and version.
            pkgInspectResults.clear()
        }

    private fun resolvedDependenciesInternal(
        analysisRoot: File,
        definitionFile: File,
        allowDynamicVersions: Boolean
    ): List<ProjectAnalyzerResult> {
        val workingDir = definitionFile.parentFile

        // TODO: Support customizing the "conan_config" directory name, and also support getting the config from a URL.
        //       These options should be retrieved from package manager specific analyzer configuration in ".ort.yml".
        val conanConfig = sequenceOf(workingDir, analysisRoot).map { it / "conan_config" }
            .find { it.isDirectory }

        val directoryToStash = conanConfig?.let { handler.getConanHome() } ?: conanStoragePath

        stashDirectories(directoryToStash).use {
            configureRemoteAuthentication(conanConfig)

            // TODO: Support lockfiles which are located in a different directory than the definition file.
            requireLockfile(analysisRoot, workingDir, allowDynamicVersions) {
                config.lockfileName?.let { hasLockfile(workingDir.resolve(it).path) } == true
            }

            val handlerResults = handler.process(definitionFile, config.lockfileName)

            val result = with(handlerResults) {
                val scopes = setOfNotNull(dependenciesScope, devDependenciesScope, testDependenciesScope)
                ProjectAnalyzerResult(
                    project = Project(
                        id = projectPackage.id,
                        definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
                        authors = projectPackage.authors,
                        declaredLicenses = projectPackage.declaredLicenses,
                        vcs = projectPackage.vcs,
                        vcsProcessed = processProjectVcs(
                            workingDir,
                            projectPackage.vcs,
                            projectPackage.homepageUrl
                        ),
                        homepageUrl = projectPackage.homepageUrl,
                        scopeDependencies = scopes
                    ),
                    packages = packages.values.toSet()
                )
            }

            return listOf(result)
        }
    }

    internal fun verifyLockfileBelongsToProject(workingDir: File, lockfileName: String?) {
        require(workingDir.resolve(lockfileName.orEmpty()).canonicalFile.startsWith(workingDir.canonicalFile)) {
            "The provided lockfile path points to the directory outside of the analyzed project: '$lockfileName' and " +
                "potentially does not belong to the project. Please move the lockfile to the '$workingDir' and " +
                "set the path in '$ORT_CONFIG_FILENAME' accordingly."
        }
    }

    private fun configureRemoteAuthentication(conanConfig: File?) {
        // Install configuration from a local directory if available.
        conanConfig?.let {
            command.run("config", "install", it.absolutePath).requireSuccess()
        }

        val remotes = handler.listRemotes()
        configureUserAuthentication(remotes)
    }

    private fun configureUserAuthentication(remotes: List<Pair<String, String>>) =
        remotes.forEach { (remoteName, remoteUrl) ->
            remoteUrl.toUri().onSuccess { uri ->
                logger.info { "Found remote '$remoteName' pointing to URL $remoteUrl." }

                // Request authentication for the extracted remote URL.
                val auth = requestPasswordAuthentication(uri)

                if (auth != null) {
                    // Configure Conan's authentication based on ORT's authentication for the remote.
                    runCatching {
                        command.run(
                            "user",
                            "-r", remoteName,
                            "-p", String(auth.password).masked(),
                            auth.userName.masked()
                        ).requireSuccess()
                    }.onFailure {
                        logger.error { "Failed to configure user authentication for remote '$remoteName'." }
                    }
                }
            }.onFailure {
                logger.warn { "The remote '$remoteName' points to invalid URL $remoteUrl." }
            }
        }

    /**
     * Return the value `conan inspect` reports for the given [field], or null if the field does not exist.
     */
    internal fun inspectField(pkgName: String, workingDir: File, field: String): String? {
        val results = pkgInspectResults.getOrPut(pkgName) {
            // Note: While Conan 2 supports inspect output to stdout, Conan 1 does not and a temporary file is required,
            // see https://github.com/conan-io/conan/issues/6972.
            val jsonFile = createOrtTempDir() / "inspect.json"

            handler.runInspectCommand(workingDir, pkgName, jsonFile)

            Json.parseToJsonElement(jsonFile.readText()).jsonObject.also {
                jsonFile.parentFile.safeDeleteRecursively()
            }
        }

        // Note that while the console output of "conan inspect" uses "None" for absent values, the JSON output actually
        // uses null values.
        return results[field]?.jsonPrimitive?.contentOrNull
    }

    /**
     * Return the [VcsInfo] contained in [pkgInfo].
     */
    internal fun parseVcsInfo(pkgInfo: PackageInfo): VcsInfo {
        val revision = pkgInfo.revision.orEmpty()
        val url = pkgInfo.url.orEmpty()
        val vcsInfo = VcsHost.parseUrl(url)
        return if (revision == "0") vcsInfo else vcsInfo.copy(revision = revision)
    }

    /**
     * Return the source artifact contained in [conanData], or [RemoteArtifact.EMPTY] if no source artifact is
     * available.
     */
    internal fun parseSourceArtifact(conanData: ConanData): RemoteArtifact {
        val url = conanData.url ?: return RemoteArtifact.EMPTY
        val hashValue = conanData.sha256.orEmpty()
        val hash = Hash.NONE.takeIf { hashValue.isEmpty() } ?: Hash(hashValue, HashAlgorithm.SHA256)

        return RemoteArtifact(url, hash)
    }

    /**
     * Parse information about the package author from the given [package info][pkgInfo]. If present, return a set
     * containing the author name; otherwise, return an empty set.
     */
    internal fun parseAuthors(pkgInfo: PackageInfo): Set<String> =
        parseAuthorString(pkgInfo.author).mapNotNullTo(mutableSetOf()) { it.name }

    internal fun readConanData(id: Identifier, conanStorageDir: File, recipeFolder: String? = null): ConanData {
        val conanDataFile = handler.getConanDataFile(id.name, id.version, conanStorageDir, recipeFolder)
            ?: return ConanData.EMPTY

        if (!conanDataFile.isFile) {
            logger.warn {
                "'${id.toCoordinates()}' does not provide a conandata.yml file. Some metadata might be missing."
            }

            return ConanData.EMPTY
        }

        val root = Yaml.default.parseToYamlNode(conanDataFile.readText()).yamlMap

        val patchesForVersion = root.get<YamlMap>("patches")?.get<YamlList>(id.version)
        val hasPatches = !patchesForVersion?.items.isNullOrEmpty()

        val sourceForVersion = root.get<YamlMap>("sources")?.get<YamlMap>(id.version)
        val sha256 = sourceForVersion?.get<YamlScalar>("sha256")?.content

        val url = sourceForVersion?.get<YamlNode>("url")?.let {
            when {
                it is YamlList -> it.yamlList.items.firstOrNull()?.yamlScalar?.content
                else -> it.yamlScalar.content
            }
        }

        return ConanData(url, sha256, hasPatches)
    }
}

internal data class ConanData(
    val url: String?,
    val sha256: String?,
    val hasPatches: Boolean
) {
    companion object {
        val EMPTY = ConanData(url = null, sha256 = null, hasPatches = false)
    }
}
