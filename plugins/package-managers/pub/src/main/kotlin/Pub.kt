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

import java.io.File
import java.io.IOException

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.PackageManagerDependency
import org.ossreviewtoolkit.analyzer.PackageManagerDependencyResult
import org.ossreviewtoolkit.analyzer.PackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManagerResult
import org.ossreviewtoolkit.analyzer.determineEnabledPackageManagers
import org.ossreviewtoolkit.analyzer.parseAuthorString
import org.ossreviewtoolkit.analyzer.toPackageReference
import org.ossreviewtoolkit.analyzer.withResolvedScopes
import org.ossreviewtoolkit.downloader.VcsHost
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
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.OrtPluginOption
import org.ossreviewtoolkit.plugins.api.PluginConfig
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.plugins.packagemanagers.pub.model.Lockfile
import org.ossreviewtoolkit.plugins.packagemanagers.pub.model.PackageInfo
import org.ossreviewtoolkit.plugins.packagemanagers.pub.model.Pubspec
import org.ossreviewtoolkit.plugins.packagemanagers.pub.model.Pubspec.Dependency
import org.ossreviewtoolkit.plugins.packagemanagers.pub.model.Pubspec.SdkDependency
import org.ossreviewtoolkit.plugins.packagemanagers.pub.model.parseLockfile
import org.ossreviewtoolkit.plugins.packagemanagers.pub.model.parsePubspec
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.ProcessCapture
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.common.div
import org.ossreviewtoolkit.utils.common.realFile
import org.ossreviewtoolkit.utils.common.safeMkdirs
import org.ossreviewtoolkit.utils.common.splitOnWhitespace
import org.ossreviewtoolkit.utils.common.unpack
import org.ossreviewtoolkit.utils.ort.downloadFile
import org.ossreviewtoolkit.utils.ort.normalizeVcsUrl
import org.ossreviewtoolkit.utils.ort.okHttpClient
import org.ossreviewtoolkit.utils.ort.ortToolsDirectory
import org.ossreviewtoolkit.utils.ort.showStackTrace

import org.semver4j.range.RangeList
import org.semver4j.range.RangeListFactory

private const val PUBSPEC_YAML = "pubspec.yaml"
private const val PUB_LOCK_FILE = "pubspec.lock"
private const val SCOPE_NAME_DEPENDENCIES = "dependencies"
private const val SCOPE_NAME_DEV_DEPENDENCIES = "dev_dependencies"
private val ALL_PUB_SCOPE_NAMES = setOf(SCOPE_NAME_DEPENDENCIES, SCOPE_NAME_DEV_DEPENDENCIES)

private val flutterCommand = if (Os.isWindows) "flutter.bat" else "flutter"
private val dartCommand = if (Os.isWindows) "dart.bat" else "dart"

internal class PubCommand(private val flutterAbsolutePath: File) : CommandLineTool {
    @Suppress("unused") // The no-arg constructor is required by the requirements command.
    constructor() : this(File(""))

    override fun getVersion(workingDir: File?): String {
        val result = ProcessCapture(workingDir, command(workingDir), getVersionArguments()).requireSuccess()

        return transformVersion(result.stdout)
    }

    override fun getVersionRequirement(): RangeList = RangeListFactory.create("[2.10,)")

    override fun transformVersion(output: String) = output.removePrefix("Dart SDK version: ").substringBefore(' ')

    override fun command(workingDir: File?): String =
        if (flutterAbsolutePath.isDirectory) "$flutterAbsolutePath${File.separator}$dartCommand" else dartCommand

    private fun commandPub(): String = "${command()} pub"

    override fun run(workingDir: File?, vararg args: CharSequence): ProcessCapture {
        var result = ProcessCapture(workingDir, *commandPub().splitOnWhitespace().toTypedArray(), *args)
        if (result.isError) {
            // If Pub fails with the message that Flutter should be used instead, fall back to using Flutter.
            if ("Flutter users should run `flutter" in result.errorMessage) {
                result = ProcessCapture(
                    workingDir,
                    *commandFlutter(flutterAbsolutePath).splitOnWhitespace().toTypedArray(),
                    *args
                ).requireSuccess()
            } else {
                throw IOException(result.errorMessage)
            }
        }

        return result
    }
}

data class PubConfig(
    /**
     * The version to use when bootstrapping Flutter. If Flutter is already on the path, this option is ignored.
     */
    @OrtPluginOption(defaultValue = "3.19.3-stable")
    val flutterVersion: String,

    /**
     * The version of Gradle to use when analyzing Gradle projects.
     */
    @OrtPluginOption(defaultValue = "7.3")
    val gradleVersion: String,

    /**
     * Only scan Pub dependencies and skip native ones for Android (Gradle) and iOS (CocoaPods).
     */
    @OrtPluginOption(defaultValue = "false")
    val pubDependenciesOnly: Boolean
)

/**
 * The [Pub](https://pub.dev/) package manager for Dart / Flutter.
 *
 * This implementation is using the Pub version distributed with Flutter. If Flutter is not installed on the system, it
 * is automatically downloaded and installed in the `~/.ort/tools` directory. The version of Flutter that is
 * automatically installed can be configured either by the `flutterVersion` package manager option (see below) or by
 * setting the `FLUTTER_VERSION` environment variable. Setting the environment variable takes precedence over the
 * configuration option.
 */
@OrtPlugin(
    displayName = "Pub",
    description = "The Pub package manager for Dart / Flutter.",
    factory = PackageManagerFactory::class
)
@Suppress("TooManyFunctions")
class Pub(override val descriptor: PluginDescriptor = PubFactory.descriptor, private val config: PubConfig) :
    PackageManager("Pub") {
    override val globsForDefinitionFiles = listOf(PUBSPEC_YAML)

    private val flutterVersion = Os.env["FLUTTER_VERSION"] ?: config.flutterVersion
    private val flutterInstallDir = ortToolsDirectory / "flutter-$flutterVersion"

    private val flutterHome by lazy {
        Os.getPathFromEnvironment(flutterCommand)?.realFile?.parentFile?.parentFile
            ?: Os.env["FLUTTER_HOME"]?.let { File(it) } ?: (flutterInstallDir / "flutter")
    }

    private val flutterAbsolutePath = flutterHome / "bin"

    private data class ParsePackagesResult(
        val packages: Map<Identifier, Package>,
        val issues: List<Issue>
    )

    private val reader = PubCacheReader(flutterHome)
    private val gradleDefinitionFilesForPubDefinitionFiles = mutableMapOf<File, Set<File>>()

    override fun beforeResolution(
        analysisRoot: File,
        definitionFiles: List<File>,
        analyzerConfig: AnalyzerConfiguration
    ) {
        if (config.pubDependenciesOnly) {
            logger.info {
                "Only analyzing Pub dependencies, skipping additional package managers for Flutter packages " +
                    "(Gradle for Android, CocoaPods for iOS dependencies)."
            }
        } else if (analyzerConfig.getGradleFactory() != null) {
            gradleDefinitionFilesForPubDefinitionFiles.clear()
            gradleDefinitionFilesForPubDefinitionFiles +=
                findGradleDefinitionFiles(analysisRoot, definitionFiles, analyzerConfig)

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

        val archive = when (Os.Name.current) {
            Os.Name.WINDOWS -> "windows/flutter_windows_$flutterVersion.zip"
            Os.Name.LINUX -> "linux/flutter_linux_$flutterVersion.tar.xz"
            Os.Name.MAC -> when (Os.Arch.current) {
                Os.Arch.X86_64 -> "macos/flutter_macos_$flutterVersion.zip"
                Os.Arch.AARCH64 -> "macos/flutter_macos_arm64_$flutterVersion.zip"
                else -> throw IllegalArgumentException("Unsupported macOS architecture '${Os.Arch.current}'.")
            }
            else -> throw IllegalArgumentException("Unsupported operating system '${Os.Name.current}'.")
        }

        val url = "https://storage.googleapis.com/flutter_infra_release/releases/stable/$archive"

        logger.info { "Downloading flutter-$flutterVersion from $url..." }
        flutterInstallDir.safeMkdirs()
        val flutterArchive = okHttpClient.downloadFile(url, flutterInstallDir).onFailure {
            logger.warn { "Unable to download Flutter $flutterVersion from $url." }
        }.getOrThrow()

        logger.info { "Unpacking '$flutterArchive' to '$flutterInstallDir'..." }
        flutterArchive.unpack(flutterInstallDir)

        if (!flutterArchive.delete()) {
            logger.warn { "Unable to delete temporary file '$flutterArchive'." }
        }

        ProcessCapture("$flutterAbsolutePath${File.separator}$flutterCommand", "config", "--no-analytics")
            .requireSuccess()
    }

    private fun findGradleDefinitionFiles(
        analysisRoot: File,
        pubDefinitionFiles: Collection<File>,
        analyzerConfig: AnalyzerConfiguration
    ): Map<File, Set<File>> {
        val result = mutableMapOf<File, MutableSet<File>>()

        val gradleFactory = analyzerConfig.getGradleFactory()
        val gradleOptions = gradleFactory?.let {
            analyzerConfig.getPackageManagerConfiguration(it.descriptor.id)?.options
        }.orEmpty()

        val gradleDefinitionFiles = findManagedFiles(
            analysisRoot,
            setOfNotNull(analyzerConfig.getGradleFactory()?.create(PluginConfig(gradleOptions)))
        ).values.flatten()

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
        analysisRoot: File,
        managedFiles: Map<PackageManager, List<File>>,
        analyzerConfig: AnalyzerConfiguration
    ): PackageManagerDependencyResult {
        if (config.pubDependenciesOnly) {
            return PackageManagerDependencyResult(mustRunBefore = emptySet(), mustRunAfter = emptySet())
        }

        val gradleFactory = analyzerConfig.getGradleFactory()

        // If there are any Gradle definition files which seem to be associated to a Pub Flutter project, it is likely
        // that Pub needs to run before Gradle, because Pub generates the required local.properties file which contains
        // the path to the Android SDK.
        val gradle = managedFiles.keys.find { it.descriptor.id == gradleFactory?.descriptor?.id }
        val mustRunBefore =
            if (gradle != null &&
                findGradleDefinitionFiles(analysisRoot, managedFiles.getValue(this), analyzerConfig).isNotEmpty()
            ) {
                setOf(gradle.descriptor.id)
            } else {
                emptySet()
            }

        return PackageManagerDependencyResult(mustRunBefore = mustRunBefore, mustRunAfter = emptySet())
    }

    override fun resolveDependencies(
        analysisRoot: File,
        definitionFile: File,
        excludes: Excludes,
        analyzerConfig: AnalyzerConfiguration,
        labels: Map<String, String>
    ): List<ProjectAnalyzerResult> {
        val workingDir = definitionFile.parentFile
        val pubspec = parsePubspec(definitionFile)
        val gradleFactory = analyzerConfig.getGradleFactory()

        val hasDependencies = pubspec.getScopeDependencies(SCOPE_NAME_DEPENDENCIES).isNotEmpty()

        val packages = mutableMapOf<Identifier, Package>()
        val scopes = mutableSetOf<Scope>()
        val issues = mutableListOf<Issue>()
        val projectAnalyzerResults = mutableListOf<ProjectAnalyzerResult>()

        if (hasDependencies) {
            installDependencies(analysisRoot, workingDir, analyzerConfig.allowDynamicVersions)

            logger.info { "Reading $PUB_LOCK_FILE file in $workingDir." }

            val lockfile = parseLockfile(workingDir / PUB_LOCK_FILE)

            logger.info { "Successfully read lockfile." }

            val parsePackagesResult = parseInstalledPackages(lockfile, labels, workingDir, analyzerConfig)

            if (!config.pubDependenciesOnly) {
                if (gradleFactory != null) {
                    val gradleDefinitionFiles = gradleDefinitionFilesForPubDefinitionFiles
                        .getValue(definitionFile)
                        .toList()

                    if (gradleDefinitionFiles.isNotEmpty()) {
                        val gradleDependencies = gradleDefinitionFiles.mapTo(mutableSetOf()) {
                            PackageManagerDependency(
                                packageManager = gradleFactory.descriptor.id,
                                definitionFile = VersionControlSystem.getPathInfo(it).path,
                                scope = "releaseCompileClasspath",
                                linkage = PackageLinkage.PROJECT_STATIC
                            ).toPackageReference()
                        }

                        scopes += Scope("android", gradleDependencies)
                    }
                } else {
                    createAndLogIssue(
                        "The Gradle package manager plugin was not found in the runtime classpath of ORT. Gradle " +
                            "project analysis will be disabled.",
                        Severity.WARNING
                    )
                }
            }

            packages += parsePackagesResult.packages
            issues += parsePackagesResult.issues

            logger.info { "Successfully parsed installed packages." }

            ALL_PUB_SCOPE_NAMES.mapTo(scopes) { scopeName ->
                parseScope(
                    scopeName,
                    pubspec,
                    lockfile,
                    parsePackagesResult.packages,
                    labels,
                    workingDir,
                    analyzerConfig
                )
            }
        }

        val project = parseProject(definitionFile, pubspec, scopes)

        projectAnalyzerResults += ProjectAnalyzerResult(project, packages.values.toSet(), issues)

        return projectAnalyzerResults
    }

    private fun parseScope(
        scopeName: String,
        pubspec: Pubspec,
        lockfile: Lockfile,
        packages: Map<Identifier, Package>,
        labels: Map<String, String>,
        workingDir: File,
        analyzerConfig: AnalyzerConfiguration
    ): Scope {
        val packageName = pubspec.name

        logger.info { "Parsing scope '$scopeName' for package '$packageName'." }

        val requiredPackages = pubspec.getScopeDependencies(scopeName).keys
        val dependencies =
            buildDependencyTree(requiredPackages, pubspec, lockfile, packages, labels, workingDir, analyzerConfig)
        return Scope(scopeName, dependencies)
    }

    @Suppress("LongParameterList")
    private fun buildDependencyTree(
        dependencies: Collection<String>,
        pubspec: Pubspec?,
        lockfile: Lockfile,
        packages: Map<Identifier, Package>,
        labels: Map<String, String>,
        workingDir: File,
        analyzerConfig: AnalyzerConfiguration,
        processedPackages: Set<String> = emptySet()
    ): Set<PackageReference> {
        val packageReferences = mutableSetOf<PackageReference>()
        val nameOfCurrentPackage = pubspec?.name.orEmpty()
        val containsFlutter = "flutter" in dependencies

        logger.debug { "Building dependency tree for package '$nameOfCurrentPackage'." }

        // Lookup the dependencies listed in pubspec.yaml file and build the dependency tree.
        dependencies.forEach { packageName ->
            // To prevent infinite loops, resolve the dependency tree for each package just once for each branch of the
            // dependency tree.
            if (packageName in processedPackages) return@forEach

            val pkgInfoFromLockfile = lockfile.packages[packageName]
            // If the package is marked as SDK (e.g. flutter, flutter_test, dart) we cannot resolve it correctly as
            // it is not stored in .pub-cache. For now, we just ignore those SDK packages.
            if (pkgInfoFromLockfile == null || pkgInfoFromLockfile.source == "sdk") return@forEach

            val id = Identifier(
                type = if (pkgInfoFromLockfile.isProject) projectType else "Pub",
                namespace = "",
                name = packageName,
                version = pkgInfoFromLockfile.version.orEmpty()
            )

            val packageInfo = requireNotNull(packages[id]) { "Could not find any info for package '$packageName'." }

            try {
                val dependencyPubspec = readPackageInfoFromCache(pkgInfoFromLockfile, workingDir)
                val requiredPackages = dependencyPubspec?.dependencies.orEmpty().keys

                val transitiveDependencies = buildDependencyTree(
                    dependencies = requiredPackages,
                    pubspec = dependencyPubspec,
                    lockfile = lockfile,
                    packages = packages,
                    labels = labels,
                    workingDir = workingDir,
                    analyzerConfig = analyzerConfig,
                    processedPackages = processedPackages + nameOfCurrentPackage
                )

                // If the project contains Flutter, we need to trigger the analyzer for Gradle and CocoaPods
                // dependencies for each pub dependency manually, as the analyzer will only scan the
                // projectRoot, but not the packages in the ".pub-cache" directory.
                if (containsFlutter && !config.pubDependenciesOnly) {
                    analyzeAndroidPackages(
                        pkgInfoFromLockfile,
                        labels,
                        workingDir,
                        analyzerConfig
                    ).forEach { resultAndroid ->
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
                        createAndLogIssue("Could not resolve dependencies of '$packageName': ${e.collectMessages()}")
                    )
                )
            }
        }

        return packageReferences
    }

    private val analyzerResultCacheAndroid = mutableMapOf<String, List<ProjectAnalyzerResult>>()

    private fun analyzeAndroidPackages(
        packageInfo: PackageInfo,
        labels: Map<String, String>,
        workingDir: File,
        analyzerConfig: AnalyzerConfiguration
    ): List<ProjectAnalyzerResult> {
        val packageName = packageInfo.description.name.orEmpty()

        // We cannot find packages without a valid name.
        if (packageName.isEmpty()) return emptyList()

        val projectRoot = reader.findProjectRoot(packageInfo, workingDir) ?: return emptyList()
        val androidDir = projectRoot / "android"
        val definitionFile = androidDir / "build.gradle"

        // Check for build.gradle failed, no Gradle scan required.
        val gradleFactory = analyzerConfig.getGradleFactory()
        if (gradleFactory == null || !definitionFile.isFile) return emptyList()

        return analyzerResultCacheAndroid.getOrPut(packageName) {
            logger.info {
                "Analyzing Android dependencies for package '$packageName' using Gradle version " +
                    "${config.gradleVersion}."
            }

            val gradleAnalyzerConfig = PluginConfig(mapOf("gradleVersion" to config.gradleVersion))

            val gradle = gradleFactory.create(gradleAnalyzerConfig)
            gradle.resolveDependencies(androidDir, listOf(definitionFile), Excludes.EMPTY, analyzerConfig, labels).run {
                projectResults.getValue(definitionFile).map { result ->
                    val project = result.project.withResolvedScopes(dependencyGraph)
                    result.copy(project = project, packages = sharedPackages)
                }
            }
        }
    }

    private fun analyzeIosPackages(packageInfo: PackageInfo, workingDir: File): ProjectAnalyzerResult? {
        // TODO: Implement similar to `scanAndroidPackages` once CocoaPods is implemented.
        val packageName = packageInfo.description.name.orEmpty()

        // We cannot find packages without a valid name.
        if (packageName.isEmpty()) return null

        val projectRoot = reader.findProjectRoot(packageInfo, workingDir) ?: return null
        val iosDir = projectRoot / "ios"
        val definitionFile = iosDir / "$packageName.podspec"

        // Check for build.gradle failed, no Gradle scan required.
        if (!definitionFile.isFile) return null

        val issue = createAndLogIssue(
            "Cannot get iOS dependencies for package '$packageName'. Support for CocoaPods is not yet implemented.",
            Severity.WARNING
        )

        return ProjectAnalyzerResult(Project.EMPTY, emptySet(), listOf(issue))
    }

    private fun parseProject(definitionFile: File, pubspec: Pubspec, scopes: Set<Scope>): Project {
        // See https://dart.dev/tools/pub/pubspec for supported fields.
        val homepageUrl = pubspec.homepage.orEmpty()
        val vcs = VcsHost.parseUrl(pubspec.repository.orEmpty())

        return Project(
            id = Identifier(
                type = projectType,
                namespace = "",
                name = pubspec.name,
                version = pubspec.version.orEmpty()
            ),
            definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
            authors = parseAuthors(pubspec),
            // Pub does not declare any licenses in the pubspec files, therefore we keep this empty.
            declaredLicenses = emptySet(),
            vcs = vcs,
            vcsProcessed = processProjectVcs(definitionFile.parentFile, vcs, homepageUrl),
            homepageUrl = homepageUrl,
            scopeDependencies = scopes
        )
    }

    private fun parseInstalledPackages(
        lockfile: Lockfile,
        labels: Map<String, String>,
        workingDir: File,
        analyzerConfig: AnalyzerConfiguration
    ): ParsePackagesResult {
        logger.info { "Parsing installed Pub packages..." }

        val packages = mutableMapOf<Identifier, Package>()
        val issues = mutableListOf<Issue>()

        // Flag if the project is a Flutter project.
        var containsFlutter = false

        lockfile.packages.forEach { (packageName, packageInfo) ->
            runCatching {
                val version = packageInfo.version.orEmpty()
                var description = ""
                var rawName = ""
                var homepageUrl = ""
                var vcs = VcsInfo.EMPTY
                var authors = emptySet<String>()

                val source = packageInfo.source.orEmpty()

                when {
                    source == "path" -> {
                        rawName = packageName
                        val path = packageInfo.description.path.orEmpty()
                        vcs = VersionControlSystem.forDirectory(workingDir / path)?.getInfo() ?: run {
                            logger.warn {
                                "Invalid path of package $rawName: " +
                                    "'$path' is outside of the project root '$workingDir'."
                            }

                            VcsInfo.EMPTY
                        }
                    }

                    source == "git" -> {
                        val pkgInfoFromYamlFile = readPackageInfoFromCache(packageInfo, workingDir)

                        rawName = pkgInfoFromYamlFile?.name ?: packageName
                        description = pkgInfoFromYamlFile?.description.orEmpty().trim()
                        homepageUrl = pkgInfoFromYamlFile?.homepage.orEmpty()
                        authors = pkgInfoFromYamlFile?.let { parseAuthors(it) }.orEmpty()

                        vcs = VcsInfo(
                            type = VcsType.GIT,
                            url = normalizeVcsUrl(packageInfo.description.url.orEmpty()),
                            revision = packageInfo.description.resolvedRef.orEmpty(),
                            path = packageInfo.description.path.orEmpty()
                        )
                    }

                    // For now, we ignore SDKs like the Dart SDK and the Flutter SDK in the analyzer.
                    source != "sdk" -> {
                        val pkgInfoFromYamlFile = readPackageInfoFromCache(packageInfo, workingDir)

                        rawName = pkgInfoFromYamlFile?.name.orEmpty()
                        description = pkgInfoFromYamlFile?.description.orEmpty().trim()
                        homepageUrl = pkgInfoFromYamlFile?.homepage.orEmpty()
                        authors = pkgInfoFromYamlFile?.let { parseAuthors(it) }.orEmpty()

                        val repositoryUrl = pkgInfoFromYamlFile?.repository.orEmpty()

                        // Ignore the revision parsed from the repositoryUrl because the URL often points to the
                        // main or master branch of the repository but never to the correct revision that matches
                        // the version of the package.
                        vcs = VcsHost.parseUrl(repositoryUrl).copy(revision = "")
                    }

                    packageInfo.description.path.orEmpty() == "flutter" -> {
                        // Set Flutter flag, which triggers another scan for iOS and Android native dependencies.
                        containsFlutter = true
                        // Set hardcoded package details.
                        rawName = "flutter"
                        homepageUrl = "https://github.com/flutter/flutter"
                        description = "Flutter SDK"
                    }

                    packageInfo.description.path.orEmpty() == "flutter_test" -> {
                        // Set hardcoded package details.
                        rawName = "flutter_test"
                        homepageUrl = "https://github.com/flutter/flutter/tree/master/packages/flutter_test"
                        description = "Flutter Test SDK"
                    }
                }

                if (version.isEmpty()) {
                    logger.warn { "No version information found for package $rawName." }
                }

                val hostUrl = packageInfo.description.url.orEmpty()

                val sourceArtifact = if (source == "hosted" && hostUrl.isNotEmpty() && version.isNotEmpty()) {
                    val sha256 = packageInfo.description.sha256.orEmpty()

                    RemoteArtifact(
                        url = "$hostUrl/packages/$rawName/versions/$version.tar.gz",
                        hash = Hash(sha256, HashAlgorithm.SHA256)
                    )
                } else {
                    RemoteArtifact.EMPTY
                }

                val id = Identifier(
                    type = if (packageInfo.isProject) projectType else "Pub",
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
            }.onFailure {
                it.showStackTrace()

                val packageVersion = packageInfo.version
                issues += createAndLogIssue(
                    "Failed to parse $PUBSPEC_YAML for package $packageName:$packageVersion: ${it.collectMessages()}"
                )
            }
        }

        // If the project contains Flutter, we need to trigger the analyzer for Gradle and CocoaPods dependencies for
        // each Pub dependency manually, as the analyzer will only analyze the projectRoot, but not the packages in
        // the ".pub-cache" directory.
        if (containsFlutter && !config.pubDependenciesOnly) {
            lockfile.packages.values.forEach { packageInfo ->
                // As this package contains Flutter, trigger Gradle manually for it.
                analyzeAndroidPackages(packageInfo, labels, workingDir, analyzerConfig).forEach { result ->
                    result.collectPackagesByScope("releaseCompileClasspath").forEach { pkg ->
                        packages[pkg.id] = pkg
                    }

                    issues += result.issues
                }

                // As this package contains Flutter, trigger CocoaPods manually for it.
                analyzeIosPackages(packageInfo, workingDir)?.let { result ->
                    result.packages.forEach { pkg ->
                        packages[pkg.id] = pkg
                    }

                    issues += result.issues
                }
            }
        }

        return ParsePackagesResult(packages, issues)
    }

    private fun readPackageInfoFromCache(packageInfo: PackageInfo, workingDir: File): Pubspec? {
        val definitionFile = reader.findFile(packageInfo, workingDir, PUBSPEC_YAML)
        if (definitionFile == null) {
            createAndLogIssue(
                "Could not find '$PUBSPEC_YAML' for '${packageInfo.description.name.orEmpty()}'.",
                Severity.WARNING
            )

            return null
        }

        return parsePubspec(definitionFile)
    }

    private fun installDependencies(analysisRoot: File, workingDir: File, allowDynamicVersions: Boolean) {
        requireLockfile(analysisRoot, workingDir, allowDynamicVersions) { workingDir.resolve(PUB_LOCK_FILE).isFile }

        if (containsFlutterSdk(workingDir)) {
            // For Flutter projects it is not enough to run `dart pub get`. Instead, use `flutter pub get` which
            // installs the required dependencies and also creates the `local.properties` file which is required for
            // the Android analysis.
            ProcessCapture(workingDir, *commandFlutter(flutterAbsolutePath).splitOnWhitespace().toTypedArray(), "get")
                .requireSuccess()
        } else {
            // The "get" command creates a "pubspec.lock" file (if not yet present) except for projects without any
            // dependencies, see https://dart.dev/tools/pub/cmd/pub-get.
            PubCommand(flutterAbsolutePath).run(workingDir, "get").requireSuccess()
        }
    }

    /**
     * Check the [PUBSPEC_YAML] within [workingDir] if the project contains the Flutter SDK.
     */
    private fun containsFlutterSdk(workingDir: File): Boolean {
        val dependencies = parsePubspec(workingDir / PUBSPEC_YAML).dependencies ?: return false
        return dependencies.values.filterIsInstance<SdkDependency>().any { it.sdk == "flutter" }
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

private fun commandFlutter(flutterAbsolutePath: File): String =
    if (flutterAbsolutePath.isDirectory) {
        "$flutterAbsolutePath${File.separator}$flutterCommand pub"
    } else {
        "$flutterCommand pub"
    }

private val PackageInfo.isProject: Boolean
    get() = source == "path" && description.url == null

/**
 * Extract information about package authors from the given [pubspec].
 */
private fun parseAuthors(pubspec: Pubspec): Set<String> =
    (pubspec.authors + pubspec.author).flatMap { parseAuthorString(it) }.mapNotNullTo(mutableSetOf()) { it.name }

private fun ProjectAnalyzerResult.collectPackagesByScope(scopeName: String): List<Package> {
    val scope = project.scopes.find { it.name == scopeName } ?: return emptyList()
    return packages.filter { it.id in scope }
}

private fun Pubspec.getScopeDependencies(scopeName: String): Map<String, Dependency> =
    when (scopeName) {
        SCOPE_NAME_DEPENDENCIES -> dependencies.orEmpty()
        SCOPE_NAME_DEV_DEPENDENCIES -> devDependencies.orEmpty()
        else -> error("Invalid scope name: '$scopeName'.")
    }

private fun AnalyzerConfiguration.getGradleFactory() =
    determineEnabledPackageManagers()
        .filter { it.descriptor.id.startsWith("Gradle") }
        .let { managers ->
            require(managers.size < 2) {
                "All of the $managers managers are able to manage 'Gradle' projects. Please enable only one of them."
            }

            managers.firstOrNull()
        }
