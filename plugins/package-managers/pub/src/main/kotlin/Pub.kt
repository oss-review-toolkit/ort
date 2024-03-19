/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.pub

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.dataformat.yaml.JacksonYAMLParseException

import java.io.File
import java.io.IOException

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.analyzer.AbstractPackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.PackageManagerDependencyResult
import org.ossreviewtoolkit.analyzer.PackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManagerResult
import org.ossreviewtoolkit.analyzer.managers.utils.PackageManagerDependencyHandler
import org.ossreviewtoolkit.analyzer.parseAuthorString
import org.ossreviewtoolkit.downloader.VcsHost
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.EMPTY_JSON_NODE
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
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.PackageManagerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.model.yamlMapper
import org.ossreviewtoolkit.plugins.packagemanagers.pub.utils.PubCacheReader
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.ProcessCapture
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.common.isNotEmpty
import org.ossreviewtoolkit.utils.common.realFile
import org.ossreviewtoolkit.utils.common.safeMkdirs
import org.ossreviewtoolkit.utils.common.splitOnWhitespace
import org.ossreviewtoolkit.utils.common.textValueOrEmpty
import org.ossreviewtoolkit.utils.common.unpack
import org.ossreviewtoolkit.utils.ort.downloadFile
import org.ossreviewtoolkit.utils.ort.normalizeVcsUrl
import org.ossreviewtoolkit.utils.ort.okHttpClient
import org.ossreviewtoolkit.utils.ort.ortToolsDirectory
import org.ossreviewtoolkit.utils.ort.showStackTrace

import org.semver4j.RangesList
import org.semver4j.RangesListFactory

private const val GRADLE_VERSION = "7.3"
private const val PUBSPEC_YAML = "pubspec.yaml"
private const val PUB_LOCK_FILE = "pubspec.lock"

private val flutterCommand = if (Os.isWindows) "flutter.bat" else "flutter"
private val dartCommand = if (Os.isWindows) "dart.bat" else "dart"

private val flutterVersion = Os.env["FLUTTER_VERSION"] ?: "3.19.3-stable"
private val flutterInstallDir = ortToolsDirectory.resolve("flutter-$flutterVersion")

val flutterHome by lazy {
    Os.getPathFromEnvironment(flutterCommand)?.realFile()?.parentFile?.parentFile
        ?: Os.env["FLUTTER_HOME"]?.let { File(it) } ?: flutterInstallDir.resolve("flutter")
}

private val flutterAbsolutePath = flutterHome.resolve("bin")

/**
 * The [Pub](https://pub.dev/) package manager for Dart / Flutter.
 *
 * This implementation is using the Pub version that is distributed with Flutter. If Flutter is not installed on the
 * system it is automatically downloaded and installed in the `~/.ort/tools` directory. The version of Flutter that is
 * automatically installed can be configured by setting the `FLUTTER_VERSION` environment variable.
 *
 * This package manager supports the following [options][PackageManagerConfiguration.options]:
 * - *gradleVersion*: The version of Gradle to use when analyzing Gradle projects. Defaults to [GRADLE_VERSION].
 * - *pubDependenciesOnly*: Only scan Pub dependencies and skip native ones for Android (Gradle) and iOS (CocoaPods).
 */
@Suppress("TooManyFunctions")
class Pub(
    name: String,
    analysisRoot: File,
    analyzerConfig: AnalyzerConfiguration,
    repoConfig: RepositoryConfiguration
) : PackageManager(name, analysisRoot, analyzerConfig, repoConfig), CommandLineTool {
    companion object {
        const val OPTION_PUB_DEPENDENCIES_ONLY = "pubDependenciesOnly"
    }

    class Factory : AbstractPackageManagerFactory<Pub>("Pub") {
        override val globsForDefinitionFiles = listOf(PUBSPEC_YAML)

        override fun create(
            analysisRoot: File,
            analyzerConfig: AnalyzerConfiguration,
            repoConfig: RepositoryConfiguration
        ) = Pub(type, analysisRoot, analyzerConfig, repoConfig)
    }

    private val gradleFactory = PackageManagerFactory.ALL["Gradle"]

    private data class ParsePackagesResult(
        val packages: Map<Identifier, Package>,
        val issues: List<Issue>
    )

    private val reader = PubCacheReader()
    private val gradleDefinitionFilesForPubDefinitionFiles = mutableMapOf<File, Set<File>>()

    private val pubDependenciesOnly = options[OPTION_PUB_DEPENDENCIES_ONLY].toBoolean()

    override fun transformVersion(output: String) = output.removePrefix("Dart SDK version: ").substringBefore(' ')

    override fun getVersionRequirement(): RangesList = RangesListFactory.create("[2.10,)")

    override fun beforeResolution(definitionFiles: List<File>) {
        if (pubDependenciesOnly) {
            logger.info {
                "Only analyzing Pub dependencies, skipping additional package managers for Flutter packages " +
                    "(Gradle for Android, CocoaPods for iOS dependencies)."
            }
        } else if (gradleFactory != null) {
            gradleDefinitionFilesForPubDefinitionFiles.clear()
            gradleDefinitionFilesForPubDefinitionFiles += findGradleDefinitionFiles(definitionFiles)

            logger.info {
                "Found ${gradleDefinitionFilesForPubDefinitionFiles.values.flatten().size} Gradle project(s)."
            }

            gradleDefinitionFilesForPubDefinitionFiles.forEach { (flutterDefinitionFile, gradleDefinitionFiles) ->
                if (gradleDefinitionFiles.isEmpty()) return@forEach

                logger.info { "- ${flutterDefinitionFile.relativeTo(analysisRoot)}" }

                gradleDefinitionFiles.forEach { gradleDefinitionFile ->
                    logger.info { "  - ${gradleDefinitionFile.relativeTo(analysisRoot)}" }
                }
            }
        }

        if (flutterAbsolutePath.resolve(flutterCommand).isFile) {
            logger.info { "Skipping to bootstrap Flutter as it was found in $flutterAbsolutePath." }
            return
        }

        logger.info { "Bootstrapping Flutter as it was not found." }

        val archive = when {
            Os.isWindows -> "windows/flutter_windows_$flutterVersion.zip"
            Os.isLinux -> "linux/flutter_linux_$flutterVersion.tar.xz"
            Os.isMac -> {
                when (val arch = System.getProperty("os.arch")) {
                    "x86_64" -> "macos/flutter_macos_$flutterVersion.zip"
                    "aarch64" -> "macos/flutter_macos_arm64_$flutterVersion.zip"
                    else -> throw IllegalArgumentException("Unsupported macOS architecture '$arch'.")
                }
            }
            else -> throw IllegalArgumentException("Unsupported operating system.")
        }

        val url = "https://storage.googleapis.com/flutter_infra_release/releases/stable/$archive"

        logger.info { "Downloading flutter-$flutterVersion from $url... " }
        flutterInstallDir.safeMkdirs()
        val flutterArchive = okHttpClient.downloadFile(url, flutterInstallDir).onFailure {
            logger.warn { "Unable to download Flutter $flutterVersion from $url." }
        }.getOrThrow()

        logger.info { "Unpacking '$flutterArchive' to '$flutterInstallDir'... " }
        flutterArchive.unpack(flutterInstallDir)

        if (!flutterArchive.delete()) {
            logger.warn { "Unable to delete temporary file '$flutterArchive'." }
        }

        ProcessCapture("$flutterAbsolutePath${File.separator}$flutterCommand", "config", "--no-analytics")
            .requireSuccess()
    }

    private fun findGradleDefinitionFiles(pubDefinitionFiles: Collection<File>): Map<File, Set<File>> {
        val result = mutableMapOf<File, MutableSet<File>>()

        val gradleDefinitionFiles = findManagedFiles(analysisRoot, setOfNotNull(gradleFactory)).values.flatten()

        val pubDefinitionFilesWithFlutterSdkSorted = pubDefinitionFiles.filter {
            containsFlutterSdk(it.parentFile)
        }.sortedByDescending {
            it.parentFile.canonicalPath.length
        }

        pubDefinitionFiles.associateWithTo(result) { mutableSetOf() }

        gradleDefinitionFiles.forEach { gradleDefinitionFile ->
            val pubDefinitionFile = pubDefinitionFilesWithFlutterSdkSorted.firstOrNull {
                gradleDefinitionFile.parentFile.canonicalFile.startsWith(it.parentFile)
            } ?: return@forEach

            result.getValue(pubDefinitionFile) += gradleDefinitionFile
        }

        return result
    }

    override fun findPackageManagerDependencies(
        managedFiles: Map<PackageManager, List<File>>
    ): PackageManagerDependencyResult {
        if (pubDependenciesOnly) {
            return PackageManagerDependencyResult(mustRunBefore = emptySet(), mustRunAfter = emptySet())
        }

        // If there are any Gradle definition files which seem to be associated to a Pub Flutter project, it is likely
        // that Pub needs to run before Gradle, because Pub generates the required local.properties file which contains
        // the path to the Android SDK.
        val gradle = managedFiles.keys.find { it.managerName == gradleFactory?.type }
        val mustRunBefore = if (gradle != null && findGradleDefinitionFiles(managedFiles.getValue(this)).isNotEmpty()) {
            setOf(gradle.managerName)
        } else {
            emptySet()
        }

        return PackageManagerDependencyResult(mustRunBefore = mustRunBefore, mustRunAfter = emptySet())
    }

    override fun resolveDependencies(definitionFile: File, labels: Map<String, String>): List<ProjectAnalyzerResult> {
        val workingDir = definitionFile.parentFile
        val manifest = yamlMapper.readTree(definitionFile)

        val hasDependencies = manifest.fields().asSequence().any { (key, value) ->
            key.startsWith("dependencies") && value.isNotEmpty()
        }

        val packages = mutableMapOf<Identifier, Package>()
        val scopes = mutableSetOf<Scope>()
        val issues = mutableListOf<Issue>()
        val projectAnalyzerResults = mutableListOf<ProjectAnalyzerResult>()

        if (hasDependencies) {
            installDependencies(workingDir)

            logger.info { "Reading $PUB_LOCK_FILE file in $workingDir." }

            val lockfile = yamlMapper.readTree(workingDir.resolve(PUB_LOCK_FILE))

            logger.info { "Successfully read lockfile." }

            val parsePackagesResult = parseInstalledPackages(lockfile, labels, workingDir)

            if (!pubDependenciesOnly) {
                if (gradleFactory != null) {
                    val gradleDefinitionFiles = gradleDefinitionFilesForPubDefinitionFiles
                        .getValue(definitionFile)
                        .toList()

                    if (gradleDefinitionFiles.isNotEmpty()) {
                        val gradleDependencies = gradleDefinitionFiles.mapTo(mutableSetOf()) {
                            PackageManagerDependencyHandler.createPackageManagerDependency(
                                packageManager = gradleFactory.type,
                                definitionFile = VersionControlSystem.getPathInfo(it).path,
                                scope = "releaseCompileClasspath",
                                linkage = PackageLinkage.PROJECT_STATIC
                            )
                        }

                        scopes += Scope("android", gradleDependencies)
                    }
                } else {
                    createAndLogIssue(
                        source = managerName,
                        message = "The Gradle package manager plugin was not found in the runtime classpath of " +
                            "ORT. Gradle project analysis will be disabled.",
                        severity = Severity.WARNING
                    )
                }
            }

            packages += parsePackagesResult.packages
            issues += parsePackagesResult.issues

            logger.info { "Successfully parsed installed packages." }

            scopes += parseScope("dependencies", manifest, lockfile, parsePackagesResult.packages, labels, workingDir)
            scopes += parseScope(
                "dev_dependencies", manifest, lockfile, parsePackagesResult.packages, labels, workingDir
            )
        }

        val project = parseProject(definitionFile, manifest, scopes)

        projectAnalyzerResults += ProjectAnalyzerResult(project, packages.values.toSet(), issues)

        return projectAnalyzerResults
    }

    private fun parseScope(
        scopeName: String,
        manifest: JsonNode,
        lockfile: JsonNode,
        packages: Map<Identifier, Package>,
        labels: Map<String, String>,
        workingDir: File
    ): Scope {
        val packageName = manifest["name"].textValue()

        logger.info { "Parsing scope '$scopeName' for package '$packageName'." }

        val requiredPackages = manifest[scopeName]?.fieldNames()?.asSequence().orEmpty().toList()
        val dependencies = buildDependencyTree(requiredPackages, manifest, lockfile, packages, labels, workingDir)
        return Scope(scopeName, dependencies)
    }

    private fun buildDependencyTree(
        dependencies: List<String>,
        manifest: JsonNode,
        lockfile: JsonNode,
        packages: Map<Identifier, Package>,
        labels: Map<String, String>,
        workingDir: File,
        processedPackages: Set<String> = emptySet()
    ): Set<PackageReference> {
        val packageReferences = mutableSetOf<PackageReference>()
        val nameOfCurrentPackage = manifest["name"].textValue()
        val containsFlutter = "flutter" in dependencies

        logger.debug { "Building dependency tree for package '$nameOfCurrentPackage'." }

        // Lookup the dependencies listed in pubspec.yaml file and build the dependency tree.
        dependencies.forEach { packageName ->
            // To prevent infinite loops, resolve the dependency tree for each package just once for each branch of the
            // dependency tree.
            if (packageName in processedPackages) return@forEach

            val pkgInfoFromLockfile = lockfile["packages"][packageName]
            // If the package is marked as SDK (e.g. flutter, flutter_test, dart) we cannot resolve it correctly as
            // it is not stored in .pub-cache. For now, we just ignore those SDK packages.
            if (pkgInfoFromLockfile == null || pkgInfoFromLockfile["source"].textValueOrEmpty() == "sdk") return@forEach

            val id = Identifier(
                type = managerName,
                namespace = "",
                name = packageName,
                version = pkgInfoFromLockfile["version"].textValueOrEmpty()
            )

            val packageInfo = packages[id] ?: throw IOException("Could not find package info for $packageName")

            try {
                val dependencyYamlFile = readPackageInfoFromCache(pkgInfoFromLockfile, workingDir)
                val requiredPackages =
                    dependencyYamlFile["dependencies"]?.fieldNames()?.asSequence()?.toList().orEmpty()

                val transitiveDependencies = buildDependencyTree(
                    dependencies = requiredPackages,
                    manifest = dependencyYamlFile,
                    lockfile = lockfile,
                    packages = packages,
                    labels = labels,
                    workingDir = workingDir,
                    processedPackages = processedPackages + nameOfCurrentPackage
                )

                // If the project contains Flutter, we need to trigger the analyzer for Gradle and CocoaPods
                // dependencies for each pub dependency manually, as the analyzer will only scan the
                // projectRoot, but not the packages in the ".pub-cache" directory.
                if (containsFlutter && !pubDependenciesOnly) {
                    scanAndroidPackages(pkgInfoFromLockfile, labels, workingDir).forEach { resultAndroid ->
                        packageReferences += packageInfo.toReference(
                            dependencies = resultAndroid.project.scopes
                                .find { it.name == "releaseCompileClasspath" }
                                ?.dependencies
                        )
                    }
                    // TODO: Enable support for iOS / CocoaPods once the package manager is implemented.
                }

                packageReferences += packageInfo.toReference(dependencies = transitiveDependencies)
            } catch (e: IOException) {
                e.showStackTrace()

                packageReferences += packageInfo.toReference(
                    issues = listOf(
                        createAndLogIssue(
                            source = managerName,
                            message = "Could not resolve dependencies of '$packageName': " +
                                e.collectMessages()
                        )
                    )
                )
            }
        }

        return packageReferences
    }

    private val analyzerResultCacheAndroid = mutableMapOf<String, List<ProjectAnalyzerResult>>()

    private fun scanAndroidPackages(
        packageInfo: JsonNode,
        labels: Map<String, String>,
        workingDir: File
    ): List<ProjectAnalyzerResult> {
        val packageName = packageInfo["description"]["name"].textValueOrEmpty()

        // We cannot find packages without a valid name.
        if (packageName.isEmpty()) return emptyList()

        val projectRoot = reader.findProjectRoot(packageInfo, workingDir) ?: return emptyList()
        val androidDir = projectRoot.resolve("android")
        val definitionFile = androidDir.resolve("build.gradle")

        // Check for build.gradle failed, no Gradle scan required.
        if (gradleFactory == null || !definitionFile.isFile) return emptyList()

        return analyzerResultCacheAndroid.getOrPut(packageName) {
            val pubGradleVersion = options[gradleFactory.type] ?: GRADLE_VERSION

            logger.info {
                "Analyzing Android dependencies for package '$packageName' using Gradle version $pubGradleVersion."
            }

            val gradleAnalyzerConfig = analyzerConfig.withPackageManagerOption(
                "Gradle",
                gradleFactory.type,
                pubGradleVersion
            )

            val gradle = gradleFactory.create(androidDir, gradleAnalyzerConfig, repoConfig)
            gradle.resolveDependencies(listOf(definitionFile), labels).run {
                projectResults.getValue(definitionFile).map { result ->
                    val project = result.project.withResolvedScopes(dependencyGraph)
                    result.copy(project = project, packages = sharedPackages)
                }
            }
        }
    }

    private fun scanIosPackages(packageInfo: JsonNode, workingDir: File): ProjectAnalyzerResult? {
        // TODO: Implement similar to `scanAndroidPackages` once CocoaPods is implemented.
        val packageName = packageInfo["description"]["name"].textValueOrEmpty()

        // We cannot find packages without a valid name.
        if (packageName.isEmpty()) return null

        val projectRoot = reader.findProjectRoot(packageInfo, workingDir) ?: return null
        val iosDir = projectRoot.resolve("ios")
        val definitionFile = iosDir.resolve("$packageName.podspec")

        // Check for build.gradle failed, no Gradle scan required.
        if (!definitionFile.isFile) return null

        val issue = createAndLogIssue(
            source = managerName,
            severity = Severity.WARNING,
            message = "Cannot get iOS dependencies for package '$packageName'. Support for CocoaPods is not yet " +
                "implemented."
        )

        return ProjectAnalyzerResult(Project.EMPTY, emptySet(), listOf(issue))
    }

    private fun parseProject(definitionFile: File, pubspec: JsonNode, scopes: Set<Scope>): Project {
        // See https://dart.dev/tools/pub/pubspec for supported fields.
        val rawName = pubspec["name"]?.textValue() ?: getFallbackProjectName(analysisRoot, definitionFile)
        val homepageUrl = pubspec["homepage"].textValueOrEmpty()
        val repositoryUrl = pubspec["repository"].textValueOrEmpty()
        val authors = parseAuthors(pubspec)

        val vcs = VcsHost.parseUrl(repositoryUrl)

        return Project(
            id = Identifier(
                type = managerName,
                namespace = "",
                name = rawName,
                version = pubspec["version"].textValueOrEmpty()
            ),
            definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
            authors = authors,
            // Pub does not declare any licenses in the pubspec files, therefore we keep this empty.
            declaredLicenses = emptySet(),
            vcs = vcs,
            vcsProcessed = processProjectVcs(definitionFile.parentFile, vcs, homepageUrl),
            homepageUrl = homepageUrl,
            scopeDependencies = scopes
        )
    }

    private fun parseInstalledPackages(
        lockfile: JsonNode,
        labels: Map<String, String>,
        workingDir: File
    ): ParsePackagesResult {
        logger.info { "Parsing installed Pub packages..." }

        val packages = mutableMapOf<Identifier, Package>()
        val issues = mutableListOf<Issue>()

        // Flag if the project is a Flutter project.
        var containsFlutter = false

        listOf("packages"/*, "packages-dev"*/).forEach {
            lockfile[it]?.fields()?.forEach { (packageName, pkgInfoFromLockfile) ->
                try {
                    val version = pkgInfoFromLockfile["version"].textValueOrEmpty()
                    var description = ""
                    var rawName = ""
                    var homepageUrl = ""
                    var vcs = VcsInfo.EMPTY
                    var authors = emptySet<String>()

                    val source = pkgInfoFromLockfile["source"].textValueOrEmpty()

                    when {
                        source == "path" -> {
                            rawName = packageName
                            val path = pkgInfoFromLockfile["description"]["path"].textValueOrEmpty()
                            vcs = VersionControlSystem.forDirectory(workingDir.resolve(path))?.getInfo() ?: run {
                                logger.warn {
                                    "Invalid path of package $rawName: " +
                                        "'$path' is outside of the project root '$workingDir'."
                                }
                                VcsInfo.EMPTY
                            }
                        }

                        source == "git" -> {
                            val pkgInfoFromYamlFile = readPackageInfoFromCache(pkgInfoFromLockfile, workingDir)

                            rawName = pkgInfoFromYamlFile["name"]?.textValue() ?: packageName
                            description = pkgInfoFromYamlFile["description"].textValueOrEmpty().trim()
                            homepageUrl = pkgInfoFromYamlFile["homepage"].textValueOrEmpty()
                            authors = parseAuthors(pkgInfoFromYamlFile)

                            vcs = VcsInfo(
                                type = VcsType.GIT,
                                url = normalizeVcsUrl(pkgInfoFromLockfile["description"]["url"].textValueOrEmpty()),
                                revision = pkgInfoFromLockfile["description"]["resolved-ref"].textValueOrEmpty(),
                                path = pkgInfoFromLockfile["description"]["path"].textValueOrEmpty()
                            )
                        }

                        // For now, we ignore SDKs like the Dart SDK and the Flutter SDK in the analyzer.
                        source != "sdk" -> {
                            val pkgInfoFromYamlFile = readPackageInfoFromCache(pkgInfoFromLockfile, workingDir)

                            rawName = pkgInfoFromYamlFile["name"].textValueOrEmpty()
                            description = pkgInfoFromYamlFile["description"].textValueOrEmpty().trim()
                            homepageUrl = pkgInfoFromYamlFile["homepage"].textValueOrEmpty()
                            authors = parseAuthors(pkgInfoFromYamlFile)

                            val repositoryUrl = pkgInfoFromYamlFile["repository"].textValueOrEmpty()

                            // Ignore the revision parsed from the repositoryUrl because the URL often points to the
                            // main or master branch of the repository but never to the correct revision that matches
                            // the version of the package.
                            vcs = VcsHost.parseUrl(repositoryUrl).copy(revision = "")
                        }

                        pkgInfoFromLockfile["description"].textValueOrEmpty() == "flutter" -> {
                            // Set Flutter flag, which triggers another scan for iOS and Android native dependencies.
                            containsFlutter = true
                            // Set hardcoded package details.
                            rawName = "flutter"
                            homepageUrl = "https://github.com/flutter/flutter"
                            description = "Flutter SDK"
                        }

                        pkgInfoFromLockfile["description"].textValueOrEmpty() == "flutter_test" -> {
                            // Set hardcoded package details.
                            rawName = "flutter_test"
                            homepageUrl = "https://github.com/flutter/flutter/tree/master/packages/flutter_test"
                            description = "Flutter Test SDK"
                        }
                    }

                    if (version.isEmpty()) {
                        logger.warn { "No version information found for package $rawName." }
                    }

                    val hostUrl = pkgInfoFromLockfile["description"]["url"].textValueOrEmpty()

                    val sourceArtifact = if (source == "hosted" && hostUrl.isNotEmpty() && version.isNotEmpty()) {
                        val sha256 = pkgInfoFromLockfile["description"]["sha256"].textValueOrEmpty()

                        RemoteArtifact(
                            url = "$hostUrl/packages/$rawName/versions/$version.tar.gz",
                            hash = Hash.create(sha256, HashAlgorithm.SHA256.name)
                        )
                    } else {
                        RemoteArtifact.EMPTY
                    }

                    val id = Identifier(
                        type = managerName,
                        namespace = "",
                        name = rawName,
                        version = version
                    )

                    packages[id] = Package(
                        id,
                        authors = authors,
                        // Pub does not declare any licenses in the pubspec files, therefore we keep this empty.
                        declaredLicenses = emptySet(),
                        description = description,
                        homepageUrl = homepageUrl,
                        // Pub does not create binary artifacts, therefore use any empty artifact.
                        binaryArtifact = RemoteArtifact.EMPTY,
                        sourceArtifact = sourceArtifact,
                        vcs = vcs,
                        vcsProcessed = processPackageVcs(vcs, homepageUrl)
                    )
                } catch (e: JacksonYAMLParseException) {
                    e.showStackTrace()

                    val packageVersion = pkgInfoFromLockfile["version"].textValueOrEmpty()
                    issues += createAndLogIssue(
                        source = managerName,
                        message = "Failed to parse $PUBSPEC_YAML for package $packageName:$packageVersion: " +
                            e.collectMessages()
                    )
                }
            }
        }

        // If the project contains Flutter, we need to trigger the analyzer for Gradle and CocoaPods dependencies for
        // each Pub dependency manually, as the analyzer will only analyze the projectRoot, but not the packages in
        // the ".pub-cache" directory.
        if (containsFlutter && !pubDependenciesOnly) {
            lockfile["packages"]?.forEach { pkgInfoFromLockfile ->
                // As this package contains Flutter, trigger Gradle manually for it.
                scanAndroidPackages(pkgInfoFromLockfile, labels, workingDir).forEach { result ->
                    result.collectPackagesByScope("releaseCompileClasspath").forEach { pkg ->
                        packages[pkg.id] = pkg
                    }

                    issues += result.issues
                }

                // As this package contains Flutter, trigger CocoaPods manually for it.
                scanIosPackages(pkgInfoFromLockfile, workingDir)?.let { result ->
                    result.packages.forEach { pkg ->
                        packages[pkg.id] = pkg
                    }

                    issues += result.issues
                }
            }
        }

        return ParsePackagesResult(packages, issues)
    }

    private fun readPackageInfoFromCache(packageInfo: JsonNode, workingDir: File): JsonNode {
        val definitionFile = reader.findFile(packageInfo, workingDir, PUBSPEC_YAML)
        if (definitionFile == null) {
            createAndLogIssue(
                source = managerName,
                message = "Could not find '$PUBSPEC_YAML' for '${packageInfo["name"].textValueOrEmpty()}'.",
                severity = Severity.WARNING
            )

            return EMPTY_JSON_NODE
        }

        return yamlMapper.readTree(definitionFile)
    }

    override fun getVersion(workingDir: File?): String {
        val result = ProcessCapture(workingDir, command(workingDir), getVersionArguments()).requireSuccess()

        return transformVersion(result.stdout)
    }

    override fun command(workingDir: File?): String =
        if (flutterAbsolutePath.isDirectory) "$flutterAbsolutePath${File.separator}$dartCommand" else dartCommand

    private fun commandPub(): String = "${command()} pub"

    private fun commandFlutter(): String =
        if (flutterAbsolutePath.isDirectory) {
            "$flutterAbsolutePath${File.separator}$flutterCommand pub"
        } else {
            "$flutterCommand pub"
        }

    override fun run(workingDir: File?, vararg args: CharSequence): ProcessCapture {
        var result = ProcessCapture(workingDir, *commandPub().splitOnWhitespace().toTypedArray(), *args)
        if (result.isError) {
            // If Pub fails with the message that Flutter should be used instead, fall back to using Flutter.
            if ("Flutter users should run `flutter" in result.errorMessage) {
                result = ProcessCapture(workingDir, *commandFlutter().splitOnWhitespace().toTypedArray(), *args)
                    .requireSuccess()
            } else {
                throw IOException(result.errorMessage)
            }
        }
        return result
    }

    private fun installDependencies(workingDir: File) {
        requireLockfile(workingDir) { workingDir.resolve(PUB_LOCK_FILE).isFile }

        if (containsFlutterSdk(workingDir)) {
            // For Flutter projects it is not enough to run `dart pub get`. Instead, use `flutter pub get` which
            // installs the required dependencies and also creates the `local.properties` file which is required for
            // the Android analysis.
            ProcessCapture(workingDir, *commandFlutter().splitOnWhitespace().toTypedArray(), "get").requireSuccess()
        } else {
            // The "get" command creates a "pubspec.lock" file (if not yet present) except for projects without any
            // dependencies, see https://dart.dev/tools/pub/cmd/pub-get.
            run(workingDir, "get")
        }
    }

    /**
     * Check the [PUBSPEC_YAML] within [workingDir] if the project contains the Flutter SDK.
     */
    private fun containsFlutterSdk(workingDir: File): Boolean {
        val specFile = yamlMapper.readTree(workingDir.resolve(PUBSPEC_YAML))

        return specFile?.get("dependencies")?.get("flutter")?.get("sdk")?.textValue() == "flutter"
    }

    /**
     * Create the final [PackageManagerResult] by making sure that packages are removed from [projectResults] that
     * are also referenced as project dependencies.
     */
    override fun createPackageManagerResult(
        projectResults: Map<File, List<ProjectAnalyzerResult>>
    ): PackageManagerResult =
        // TODO: Dependencies on projects should use the correct package linkage. To fix this, all project identifiers
        //       should already be determined in beforeResolution() so that the linkage can be correctly set when the
        //       dependency tree is built. Then also project packages could be prevented and the filter below could be
        //       removed.
        PackageManagerResult(projectResults.filterProjectPackages())
}

/**
 * Extract information about package authors from the given [pubspec].
 */
private fun parseAuthors(pubspec: JsonNode): Set<String> =
    (setOfNotNull(pubspec["author"]) + pubspec["authors"]?.toSet().orEmpty()).mapNotNullTo(mutableSetOf()) {
        parseAuthorString(it.textValue())
    }

private fun ProjectAnalyzerResult.collectPackagesByScope(scopeName: String): List<Package> {
    val scope = project.scopes.find { it.name == scopeName } ?: return emptyList()
    return packages.filter { it.id in scope }
}
