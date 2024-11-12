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

import org.ossreviewtoolkit.analyzer.AbstractPackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.parseAuthorString
import org.ossreviewtoolkit.downloader.VcsHost
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.HashAlgorithm
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.PackageManagerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.masked
import org.ossreviewtoolkit.utils.common.safeDeleteRecursively
import org.ossreviewtoolkit.utils.common.stashDirectories
import org.ossreviewtoolkit.utils.common.toUri
import org.ossreviewtoolkit.utils.ort.ORT_CONFIG_FILENAME
import org.ossreviewtoolkit.utils.ort.createOrtTempDir
import org.ossreviewtoolkit.utils.ort.requestPasswordAuthentication

import org.semver4j.RangesList
import org.semver4j.RangesListFactory

/**
 * The [Conan](https://conan.io/) package manager for C / C++.
 *
 * This package manager supports the following [options][PackageManagerConfiguration.options]:
 * - *lockfileName*: The name of the lockfile, which is used for analysis if allowDynamicVersions is set to false.
 *   The lockfile should be located in the analysis root. Currently only one lockfile is supported per Conan project.
 * TODO: Add support for `python_requires`.
 */
@Suppress("TooManyFunctions")
class Conan(
    name: String,
    analysisRoot: File,
    analyzerConfig: AnalyzerConfiguration,
    repoConfig: RepositoryConfiguration
) : PackageManager(name, "Conan", analysisRoot, analyzerConfig, repoConfig), CommandLineTool {
    companion object {
        /**
         * The name of the option to specify the name of the lockfile.
         */
        const val OPTION_LOCKFILE_NAME = "lockfileName"

        private val DUMMY_COMPILER_SETTINGS = arrayOf(
            "-s", "compiler=gcc",
            "-s", "compiler.libcxx=libstdc++",
            "-s", "compiler.version=11.1"
        )

        private const val SCOPE_NAME_DEPENDENCIES = "requires"
        private const val SCOPE_NAME_DEV_DEPENDENCIES = "build_requires"
    }

    class Factory : AbstractPackageManagerFactory<Conan>("Conan") {
        override val globsForDefinitionFiles = listOf("conanfile*.txt", "conanfile*.py")

        override fun create(
            analysisRoot: File,
            analyzerConfig: AnalyzerConfiguration,
            repoConfig: RepositoryConfiguration
        ) = Conan(type, analysisRoot, analyzerConfig, repoConfig)
    }

    private val conanHome = Os.userHomeDirectory.resolve(".conan")

    // This is where Conan caches downloaded packages [1]. Note that the package cache is not concurrent, and its
    // layout does not support packages from different remotes that are named (and versioned) the same.
    //
    // TODO: Consider using the experimental (and by default disabled) download cache [2] to lift these limitations.
    //
    // [1]: https://docs.conan.io/en/latest/reference/config_files/conan.conf.html#storage
    // [2]: https://docs.conan.io/en/latest/configuration/download_cache.html#download-cache
    private val conanStoragePath = conanHome.resolve("data")

    private val pkgInspectResults = mutableMapOf<String, JsonObject>()

    override fun command(workingDir: File?) = "conan"

    private fun hasLockfile(file: String) = File(file).isFile

    override fun transformVersion(output: String) =
        // Conan could report version strings like:
        // Conan version 1.18.0
        output.removePrefix("Conan version ")

    override fun getVersionRequirement(): RangesList = RangesListFactory.create(">=1.44.0 <2.0")

    override fun beforeResolution(definitionFiles: List<File>) = checkVersion()

    /**
     * Primary method for resolving dependencies from [definitionFile].
     */
    override fun resolveDependencies(definitionFile: File, labels: Map<String, String>): List<ProjectAnalyzerResult> =
        try {
            resolvedDependenciesInternal(definitionFile)
        } finally {
            // Clear the inspection result cache, because we call "conan config install" for each definition file which
            // could overwrite the remotes and result in different metadata for packages with the same name and version.
            pkgInspectResults.clear()
        }

    override fun run(vararg args: CharSequence, workingDir: File?, environment: Map<String, String>) =
        super.run(args = args, workingDir = workingDir, environment = environment + ("CONAN_NON_INTERACTIVE" to "1"))

    private fun resolvedDependenciesInternal(definitionFile: File): List<ProjectAnalyzerResult> {
        val workingDir = definitionFile.parentFile

        // TODO: Support customizing the "conan_config" directory name, and also support getting the config from a URL.
        //       These options should be retrieved from package manager specific analyzer configuration in ".ort.yml".
        val conanConfig = sequenceOf(workingDir, analysisRoot).map { it.resolve("conan_config") }
            .find { it.isDirectory }

        val directoryToStash = conanConfig?.let { conanHome } ?: conanStoragePath

        stashDirectories(directoryToStash).use {
            configureRemoteAuthentication(conanConfig)

            // TODO: Support lockfiles which are located in a different directory than the definition file.
            val lockfileName = options[OPTION_LOCKFILE_NAME]
            requireLockfile(workingDir) { lockfileName?.let { hasLockfile(workingDir.resolve(it).path) } == true }

            val jsonFile = createOrtTempDir().resolve("info.json")
            if (lockfileName != null) {
                verifyLockfileBelongsToProject(workingDir, lockfileName)
                run(workingDir, "info", definitionFile.name, "-l", lockfileName, "--json", jsonFile.absolutePath)
            } else {
                run(workingDir, "info", definitionFile.name, "--json", jsonFile.absolutePath, *DUMMY_COMPILER_SETTINGS)
            }

            val pkgInfos = parsePackageInfos(jsonFile)
            jsonFile.parentFile.safeDeleteRecursively()

            val packageList = removeProjectPackage(pkgInfos, definitionFile.name)
            val packages = parsePackages(packageList, workingDir)
            val projectInfo = findProjectPackageInfo(pkgInfos, definitionFile.name)

            val dependenciesScope = Scope(
                name = SCOPE_NAME_DEPENDENCIES,
                dependencies = parseDependencyTree(pkgInfos, projectInfo.requires, workingDir)
            )
            val devDependenciesScope = Scope(
                name = SCOPE_NAME_DEV_DEPENDENCIES,
                dependencies = parseDependencyTree(pkgInfos, projectInfo.buildRequires, workingDir)
            )

            val projectPackage = generateProjectPackage(projectInfo, definitionFile, workingDir)

            return listOf(
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
                        scopeDependencies = setOf(dependenciesScope, devDependenciesScope)
                    ),
                    packages = packages.values.toSet()
                )
            )
        }
    }

    private fun verifyLockfileBelongsToProject(workingDir: File, lockfileName: String?) {
        require(workingDir.resolve(lockfileName.orEmpty()).canonicalFile.startsWith(workingDir.canonicalFile)) {
            "The provided lockfile path points to the directory outside of the analyzed project: '$lockfileName' and " +
                "potentially does not belong to the project. Please move the lockfile to the '$workingDir' and " +
                "set the path in '$ORT_CONFIG_FILENAME' accordingly."
        }
    }

    private fun configureRemoteAuthentication(conanConfig: File?) {
        // Install configuration from a local directory if available.
        conanConfig?.let {
            run("config", "install", it.absolutePath)
        }

        // List configured remotes in "remotes.txt" format.
        val remoteList = runCatching {
            run("remote", "list", "--raw")
        }.getOrElse {
            logger.warn { "Failed to list remotes." }
            return
        }

        val remotes = parseConanRemoteList(remoteList.stdout)
        configureUserAuthentication(remotes)
    }

    private fun parseConanRemoteList(remoteList: String): List<Pair<String, String>> =
        remoteList.lines().mapNotNull { line ->
            // Extract the remote URL.
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty() || trimmedLine.startsWith('#')) return@mapNotNull null

            val wordIterator = trimmedLine.splitToSequence(' ').iterator()

            if (!wordIterator.hasNext()) return@mapNotNull null
            val remoteName = wordIterator.next()

            if (!wordIterator.hasNext()) return@mapNotNull null
            val remoteUrl = wordIterator.next()

            remoteName to remoteUrl
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
                        run("user", "-r", remoteName, "-p", String(auth.password).masked(), auth.userName.masked())
                    }.onFailure {
                        logger.error { "Failed to configure user authentication for remote '$remoteName'." }
                    }
                }
            }.onFailure {
                logger.warn { "The remote '$remoteName' points to invalid URL $remoteUrl." }
            }
        }

    /**
     * Return the dependency tree for the given [direct scope dependencies][requires].
     */
    private fun parseDependencyTree(
        pkgInfos: List<PackageInfo>,
        requires: List<String>,
        workingDir: File
    ): Set<PackageReference> =
        buildSet {
            requires.forEach { childRef ->
                pkgInfos.find { it.reference.orEmpty() == childRef }?.let { pkgInfo ->
                    logger.debug { "Found child '$childRef'." }

                    val id = parsePackageId(pkgInfo, workingDir)
                    val dependencies = parseDependencyTree(pkgInfos, pkgInfo.requires, workingDir) +
                        parseDependencyTree(pkgInfos, pkgInfo.buildRequires, workingDir)

                    this += PackageReference(id, dependencies = dependencies)
                }
            }
        }

    /**
     * Return the map of packages and their identifiers which are contained in [pkgInfos].
     */
    private fun parsePackages(pkgInfos: List<PackageInfo>, workingDir: File): Map<String, Package> =
        pkgInfos.associate { pkgInfo ->
            val pkg = parsePackage(pkgInfo, workingDir)
            "${pkg.id.name}:${pkg.id.version}" to pkg
        }

    /**
     * Return the [Package] parsed from the given [pkgInfo].
     */
    private fun parsePackage(pkgInfo: PackageInfo, workingDir: File): Package {
        val homepageUrl = pkgInfo.homepage.orEmpty()

        val id = parsePackageId(pkgInfo, workingDir)
        val conanData = readConanData(id.name, id.version, conanStoragePath)

        return Package(
            id = id,
            authors = parseAuthors(pkgInfo),
            declaredLicenses = pkgInfo.license.toSet(),
            description = inspectField(pkgInfo.displayName, workingDir, "description").orEmpty(),
            homepageUrl = homepageUrl,
            binaryArtifact = RemoteArtifact.EMPTY, // TODO: implement me!
            sourceArtifact = parseSourceArtifact(conanData),
            vcs = processPackageVcs(VcsInfo.EMPTY, homepageUrl),
            isModified = conanData.hasPatches
        )
    }

    /**
     * Return the value `conan inspect` reports for the given [field], or null if the field does not exist.
     */
    private fun inspectField(pkgName: String, workingDir: File, field: String): String? {
        val results = pkgInspectResults.getOrPut(pkgName) {
            // Note: While Conan 2 supports inspect output to stdout, Conan 1 does not and a temporary file is required,
            // see https://github.com/conan-io/conan/issues/6972.
            val jsonFile = createOrtTempDir().resolve("inspect.json")
            run(workingDir, "inspect", pkgName, "--json", jsonFile.absolutePath)
            Json.parseToJsonElement(jsonFile.readText()).jsonObject.also {
                jsonFile.parentFile.safeDeleteRecursively()
            }
        }

        // Note that while the console output of "conan inspect" uses "None" for absent values, the JSON output actually
        // uses null values.
        return results[field]?.jsonPrimitive?.contentOrNull
    }

    /**
     * Find the [PackageInfo] that represents the project defined in the definition file.
     */
    private fun findProjectPackageInfo(pkgInfos: List<PackageInfo>, definitionFileName: String): PackageInfo =
        pkgInfos.first {
            // Use "in" because conanfile.py's reference string often includes other data.
            definitionFileName in it.reference.orEmpty()
        }

    /**
     * Return the full list of packages, excluding the project level information.
     */
    private fun removeProjectPackage(pkgInfos: List<PackageInfo>, definitionFileName: String): List<PackageInfo> =
        pkgInfos.minusElement(findProjectPackageInfo(pkgInfos, definitionFileName))

    /**
     * Return the [Identifier] for the package contained in [pkgInfo].
     */
    private fun parsePackageId(pkgInfo: PackageInfo, workingDir: File) =
        Identifier(
            type = "Conan",
            namespace = "",
            name = inspectField(pkgInfo.displayName, workingDir, "name").orEmpty(),
            version = inspectField(pkgInfo.displayName, workingDir, "version").orEmpty()
        )

    /**
     * Return the [VcsInfo] contained in [pkgInfo].
     */
    private fun parseVcsInfo(pkgInfo: PackageInfo): VcsInfo {
        val revision = pkgInfo.revision.orEmpty()
        val url = pkgInfo.url.orEmpty()
        val vcsInfo = VcsHost.parseUrl(url)
        return if (revision == "0") vcsInfo else vcsInfo.copy(revision = revision)
    }

    /**
     * Return the source artifact contained in [conanData], or [RemoteArtifact.EMPTY] if no source artifact is
     * available.
     */
    private fun parseSourceArtifact(conanData: ConanData): RemoteArtifact {
        val url = conanData.url ?: return RemoteArtifact.EMPTY
        val hashValue = conanData.sha256.orEmpty()
        val hash = Hash.NONE.takeIf { hashValue.isEmpty() } ?: Hash(hashValue, HashAlgorithm.SHA256)

        return RemoteArtifact(url, hash)
    }

    /**
     * Return a [Package] containing project-level information from [pkgInfo] and [definitionFile] using the
     * `conan inspect` command if possible:
     * - conanfile.txt: `conan inspect conanfile.txt` is not supported.
     * - conanfile.py: `conan inspect conanfile.py` is supported and more useful project metadata is parsed.
     *
     * TODO: The format of `conan info` output for a conanfile.txt file may be such that we can get project metadata
     *       from the `requires` field. Need to investigate whether this is a sure thing before implementing.
     */
    private fun generateProjectPackage(pkgInfo: PackageInfo, definitionFile: File, workingDir: File): Package {
        fun inspectPyFile(field: String) =
            definitionFile.name.takeIf { it == "conanfile.py" }?.let { inspectField(it, workingDir, field) }

        return Package(
            id = Identifier(
                type = managerName,
                namespace = "",
                name = inspectPyFile("name") ?: pkgInfo.reference.orEmpty(),
                version = inspectPyFile("version").orEmpty()
            ),
            authors = parseAuthors(pkgInfo),
            declaredLicenses = pkgInfo.license.toSet(),
            description = inspectPyFile("description").orEmpty(),
            homepageUrl = pkgInfo.homepage.orEmpty(),
            binaryArtifact = RemoteArtifact.EMPTY, // TODO: implement me!
            sourceArtifact = RemoteArtifact.EMPTY, // TODO: implement me!
            vcs = parseVcsInfo(pkgInfo)
        )
    }

    /**
     * Parse information about the package author from the given [package info][pkgInfo]. If present, return a set
     * containing the author name; otherwise, return an empty set.
     */
    private fun parseAuthors(pkgInfo: PackageInfo): Set<String> =
        parseAuthorString(pkgInfo.author).mapNotNullTo(mutableSetOf()) { it.name }
}

private data class ConanData(
    val url: String?,
    val sha256: String?,
    val hasPatches: Boolean
)

private fun readConanData(name: String, version: String, conanStorageDir: File): ConanData {
    val conanDataFile = conanStorageDir.resolve("$name/$version/_/_/export/conandata.yml")
    val root = Yaml.default.parseToYamlNode(conanDataFile.readText()).yamlMap

    val patchesForVersion = root.get<YamlMap>("patches")?.get<YamlList>(version)
    val hasPatches = !patchesForVersion?.items.isNullOrEmpty()

    val sourceForVersion = root.get<YamlMap>("sources")?.get<YamlMap>(version)
    val sha256 = sourceForVersion?.get<YamlScalar>("sha256")?.content

    val url = sourceForVersion?.get<YamlNode>("url")?.let {
        when {
            it is YamlList -> it.yamlList.items.firstOrNull()?.yamlScalar?.content
            else -> it.yamlScalar.content
        }
    }

    return ConanData(url, sha256, hasPatches)
}
