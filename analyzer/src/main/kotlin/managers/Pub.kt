/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 * Copyright (C) 2021 Bosch.IO GmbH
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

package org.ossreviewtoolkit.analyzer.managers

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.dataformat.yaml.JacksonYAMLParseException

import com.vdurmont.semver4j.Requirement

import java.io.File
import java.io.IOException
import java.util.SortedSet

import org.ossreviewtoolkit.analyzer.AbstractPackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.PackageManagerDependencyResult
import org.ossreviewtoolkit.analyzer.PackageManagerResult
import org.ossreviewtoolkit.analyzer.managers.utils.PackageManagerDependencyHandler
import org.ossreviewtoolkit.analyzer.managers.utils.PubCacheReader
import org.ossreviewtoolkit.analyzer.parseAuthorString
import org.ossreviewtoolkit.downloader.VcsHost
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.EMPTY_JSON_NODE
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.OrtIssue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageLinkage
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.model.yamlMapper
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.ProcessCapture
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.common.realFile
import org.ossreviewtoolkit.utils.common.safeMkdirs
import org.ossreviewtoolkit.utils.common.textValueOrEmpty
import org.ossreviewtoolkit.utils.common.unpack
import org.ossreviewtoolkit.utils.ort.OkHttpClientHelper
import org.ossreviewtoolkit.utils.ort.log
import org.ossreviewtoolkit.utils.ort.ortToolsDirectory
import org.ossreviewtoolkit.utils.ort.showStackTrace

private const val GRADLE_VERSION = "7.3"
private const val PUBSPEC_YAML = "pubspec.yaml"
private const val PUB_LOCK_FILE = "pubspec.lock"

private val flutterCommand = if (Os.isWindows) "flutter.bat" else "flutter"
private val dartCommand = if (Os.isWindows) "dart.bat" else "dart"

private val flutterVersion = Os.env["FLUTTER_VERSION"] ?: "3.0.5-stable"
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
 */
@Suppress("TooManyFunctions")
class Pub(
    name: String,
    analysisRoot: File,
    analyzerConfig: AnalyzerConfiguration,
    repoConfig: RepositoryConfiguration
) : PackageManager(name, analysisRoot, analyzerConfig, repoConfig), CommandLineTool {
    class Factory : AbstractPackageManagerFactory<Pub>("Pub") {
        override val globsForDefinitionFiles = listOf(PUBSPEC_YAML)

        override fun create(
            analysisRoot: File,
            analyzerConfig: AnalyzerConfiguration,
            repoConfig: RepositoryConfiguration
        ) = Pub(managerName, analysisRoot, analyzerConfig, repoConfig)
    }

    private data class ParsePackagesResult(
        val packages: Map<Identifier, Package>,
        val issues: List<OrtIssue>
    )

    private val reader = PubCacheReader()
    private val gradleDefinitionFilesForPubDefinitionFiles = mutableMapOf<File, Set<File>>()

    override fun transformVersion(output: String) = output.removePrefix("Dart SDK version: ").substringBefore(' ')

    override fun getVersionRequirement(): Requirement = Requirement.buildIvy("[2.10,)")

    override fun beforeResolution(definitionFiles: List<File>) {
        gradleDefinitionFilesForPubDefinitionFiles.clear()
        gradleDefinitionFilesForPubDefinitionFiles += findGradleDefinitionFiles(definitionFiles)

        log.info { "Found ${gradleDefinitionFilesForPubDefinitionFiles.values.flatten().size} Gradle project(s)." }

        gradleDefinitionFilesForPubDefinitionFiles.forEach { (flutterDefinitionFile, gradleDefinitionFiles) ->
            if (gradleDefinitionFiles.isEmpty()) return@forEach

            log.info { "- ${flutterDefinitionFile.relativeTo(analysisRoot)}" }

            gradleDefinitionFiles.forEach { gradleDefinitionFile ->
                log.info { "  - ${gradleDefinitionFile.relativeTo(analysisRoot)}" }
            }
        }

        if (flutterAbsolutePath.resolve(flutterCommand).isFile) {
            log.info { "Skipping to bootstrap flutter as it was found in $flutterAbsolutePath." }
            return
        }

        log.info { "Bootstrapping flutter as it was not found." }

        val archive = when {
            Os.isWindows -> "windows/flutter_windows_$flutterVersion.zip"
            Os.isLinux -> "linux/flutter_linux_$flutterVersion.tar.xz"
            else -> throw IllegalArgumentException("Unsupported operating system.")
        }

        val url = "https://storage.googleapis.com/flutter_infra_release/releases/stable/$archive"

        log.info { "Downloading flutter-$flutterVersion from $url... " }
        flutterInstallDir.safeMkdirs()
        val flutterArchive = OkHttpClientHelper.downloadFile(url, flutterInstallDir).getOrThrow()

        log.info { "Unpacking '$flutterArchive' to '$flutterInstallDir'... " }
        flutterArchive.unpack(flutterInstallDir)

        if (!flutterArchive.delete()) {
            log.warn { "Unable to delete temporary file '$flutterArchive'." }
        }

        ProcessCapture("$flutterAbsolutePath${File.separator}$flutterCommand", "config", "--no-analytics")
            .requireSuccess()
    }

    private fun findGradleDefinitionFiles(pubDefinitionFiles: Collection<File>): Map<File, Set<File>> {
        val result = mutableMapOf<File, MutableSet<File>>()

        val gradleDefinitionFiles = findManagedFiles(analysisRoot, setOf(Gradle.Factory())).values.flatten()

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
        // If there are any Gradle definition files which seem to be associated to a Pub Flutter project, it is likely
        // that Pub needs to run before Gradle, because Pub generates the required local.properties file which contains
        // the path to the Android SDK.
        val gradle = managedFiles.keys.find { it.managerName == Gradle.Factory().managerName }
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
            key.startsWith("dependencies") && value.count() > 0
        }

        val packages = mutableMapOf<Identifier, Package>()
        val scopes = sortedSetOf<Scope>()
        val issues = mutableListOf<OrtIssue>()
        val projectAnalyzerResults = mutableListOf<ProjectAnalyzerResult>()

        if (hasDependencies) {
            installDependencies(workingDir)

            log.info { "Reading $PUB_LOCK_FILE file in $workingDir." }

            val lockFile = yamlMapper.readTree(workingDir.resolve(PUB_LOCK_FILE))

            log.info { "Successfully read lockfile." }

            val parsePackagesResult = parseInstalledPackages(lockFile, labels, workingDir)

            val gradleDefinitionFiles = gradleDefinitionFilesForPubDefinitionFiles.getValue(definitionFile).toList()

            if (gradleDefinitionFiles.isNotEmpty()) {
                val gradleName = Gradle.Factory().managerName
                val gradleDependencies = gradleDefinitionFiles.map {
                    PackageManagerDependencyHandler.createPackageManagerDependency(
                        packageManager = gradleName,
                        definitionFile = VersionControlSystem.getPathInfo(it).path,
                        scope = "releaseCompileClasspath",
                        linkage = PackageLinkage.PROJECT_STATIC
                    )
                }.toSortedSet()

                scopes += Scope("android", gradleDependencies)
            }

            packages += parsePackagesResult.packages
            issues += parsePackagesResult.issues

            log.info { "Successfully parsed installed packages." }

            scopes += parseScope("dependencies", manifest, lockFile, parsePackagesResult.packages, labels, workingDir)
            scopes += parseScope(
                "dev_dependencies", manifest, lockFile, parsePackagesResult.packages, labels, workingDir
            )
        }

        val project = parseProject(definitionFile, manifest, scopes)

        projectAnalyzerResults += ProjectAnalyzerResult(project, packages.values.toSortedSet(), issues)

        return projectAnalyzerResults
    }

    private fun parseScope(
        scopeName: String,
        manifest: JsonNode,
        lockFile: JsonNode,
        packages: Map<Identifier, Package>,
        labels: Map<String, String>,
        workingDir: File
    ): Scope {
        val packageName = manifest["name"].textValue()

        log.info { "Parsing scope '$scopeName' for package '$packageName'." }

        val requiredPackages = manifest[scopeName]?.fieldNames()?.asSequence()?.toList() ?: listOf<String>()
        val dependencies = buildDependencyTree(requiredPackages, manifest, lockFile, packages, labels, workingDir)
        return Scope(scopeName, dependencies)
    }

    private fun buildDependencyTree(
        dependencies: List<String>,
        manifest: JsonNode,
        lockFile: JsonNode,
        packages: Map<Identifier, Package>,
        labels: Map<String, String>,
        workingDir: File,
        processedPackages: Set<String> = emptySet()
    ): SortedSet<PackageReference> {
        val packageReferences = mutableSetOf<PackageReference>()
        val nameOfCurrentPackage = manifest["name"].textValue()
        val containsFlutter = "flutter" in dependencies

        log.debug { "Building dependency tree for package '$nameOfCurrentPackage'." }

        // Lookup the dependencies listed in pubspec.yaml file and build the dependency tree.
        dependencies.forEach { packageName ->
            // To prevent infinite loops, resolve the dependency tree for each package just once for each branch of the
            // dependency tree.
            if (packageName in processedPackages) return@forEach

            val pkgInfoFromLockFile = lockFile["packages"][packageName]
            // If the package is marked as SDK (e.g. flutter, flutter_test, dart) we cannot resolve it correctly as
            // it is not stored in .pub-cache. For now, we just ignore those SDK packages.
            if (pkgInfoFromLockFile == null || pkgInfoFromLockFile["source"].textValueOrEmpty() == "sdk") return@forEach

            val id = Identifier(
                type = managerName,
                namespace = packageName.substringBefore('/'),
                name = packageName.substringAfter('/'),
                version = pkgInfoFromLockFile["version"].textValueOrEmpty()
            )

            val packageInfo = packages[id] ?: throw IOException("Could not find package info for $packageName")

            try {
                val dependencyYamlFile = readPackageInfoFromCache(pkgInfoFromLockFile, workingDir)
                val requiredPackages =
                    dependencyYamlFile["dependencies"]?.fieldNames()?.asSequence()?.toList().orEmpty()

                val transitiveDependencies = buildDependencyTree(
                    dependencies = requiredPackages,
                    manifest = dependencyYamlFile,
                    lockFile = lockFile,
                    packages = packages,
                    labels = labels,
                    workingDir = workingDir,
                    processedPackages = processedPackages + nameOfCurrentPackage
                )

                // If the project contains Flutter, we need to trigger the analyzer for Gradle and CocoaPods
                // dependencies for each pub dependency manually, as the analyzer will only scan the
                // projectRoot, but not the packages in the ".pub-cache" directory.
                if (containsFlutter) {
                    scanAndroidPackages(pkgInfoFromLockFile, labels, workingDir).forEach { resultAndroid ->
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

        return packageReferences.toSortedSet()
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
        val packageFile = androidDir.resolve("build.gradle")

        // Check for build.gradle failed, no Gradle scan required.
        if (!packageFile.isFile) return emptyList()

        return analyzerResultCacheAndroid.getOrPut(packageName) {
            log.info {
                "Analyzing Android dependencies for package '$packageName' using Gradle version $GRADLE_VERSION."
            }

            Gradle("Gradle", androidDir, analyzerConfig, repoConfig, GRADLE_VERSION)
                .resolveDependencies(listOf(packageFile), labels).run {
                    projectResults.getValue(packageFile).map { result ->
                        val project = result.project.withResolvedScopes(dependencyGraph)
                        result.copy(project = project, packages = sharedPackages.toSortedSet())
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
        val packageFile = iosDir.resolve("$packageName.podspec")

        // Check for build.gradle failed, no Gradle scan required.
        if (!packageFile.isFile) return null

        val issue = createAndLogIssue(
            source = managerName,
            severity = Severity.WARNING,
            message = "Cannot get iOS dependencies for package '$packageName'. Support for CocoaPods is not yet " +
                    "implemented."
        )

        return ProjectAnalyzerResult(Project.EMPTY, sortedSetOf(), listOf(issue))
    }

    private fun parseProject(definitionFile: File, pubspec: JsonNode, scopes: SortedSet<Scope>): Project {
        // See https://dart.dev/tools/pub/pubspec for supported fields.
        val rawName = pubspec["name"]?.textValue() ?: definitionFile.parentFile.name
        val homepageUrl = pubspec["homepage"].textValueOrEmpty()
        val repositoryUrl = pubspec["repository"].textValueOrEmpty()
        val authors = parseAuthors(pubspec)

        val vcs = VcsHost.parseUrl(repositoryUrl)

        return Project(
            id = Identifier(
                type = managerName,
                namespace = rawName.substringBefore('/'),
                name = rawName.substringAfter('/'),
                version = pubspec["version"].textValueOrEmpty()
            ),
            definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
            authors = authors,
            // Pub does not declare any licenses in the pubspec files, therefore we keep this empty.
            declaredLicenses = sortedSetOf(),
            vcs = vcs,
            vcsProcessed = processProjectVcs(definitionFile.parentFile, vcs, homepageUrl),
            homepageUrl = homepageUrl,
            scopeDependencies = scopes
        )
    }

    private fun parseInstalledPackages(
        lockFile: JsonNode,
        labels: Map<String, String>,
        workingDir: File
    ): ParsePackagesResult {
        log.info { "Parsing installed Pub packages..." }

        val packages = mutableMapOf<Identifier, Package>()
        val issues = mutableListOf<OrtIssue>()

        // Flag if the project is a flutter project.
        var containsFlutter = false

        listOf("packages"/*, "packages-dev"*/).forEach {
            lockFile[it]?.fields()?.forEach { (packageName, pkgInfoFromLockFile) ->
                try {
                    val version = pkgInfoFromLockFile["version"].textValueOrEmpty()
                    var description = ""
                    var rawName = ""
                    var homepageUrl = ""
                    var vcs = VcsInfo.EMPTY
                    var authors: SortedSet<String> = sortedSetOf<String>()

                    when {
                        pkgInfoFromLockFile["source"].textValueOrEmpty() == "path" -> {
                            rawName = packageName
                            val path = pkgInfoFromLockFile["description"]["path"].textValueOrEmpty()
                            vcs = VersionControlSystem.forDirectory(workingDir.resolve(path))?.getInfo() ?: run {
                                log.warn {
                                    "Invalid path of package $rawName: " +
                                    "'$path' is outside of the project root '$workingDir'."
                                }
                                VcsInfo.EMPTY
                            }
                        }

                        // For now, we ignore SDKs like the Dart SDK and the Flutter SDK in the analyzer.
                        pkgInfoFromLockFile["source"].textValueOrEmpty() != "sdk" -> {
                            val pkgInfoFromYamlFile = readPackageInfoFromCache(pkgInfoFromLockFile, workingDir)

                            rawName = pkgInfoFromYamlFile["name"].textValueOrEmpty()
                            description = pkgInfoFromYamlFile["description"].textValueOrEmpty().trim()
                            homepageUrl = pkgInfoFromYamlFile["homepage"].textValueOrEmpty()
                            authors = parseAuthors(pkgInfoFromYamlFile)

                            val repositoryUrl = pkgInfoFromYamlFile["repository"].textValueOrEmpty()
                            vcs = VcsHost.parseUrl(repositoryUrl)
                        }

                        pkgInfoFromLockFile["description"].textValueOrEmpty() == "flutter" -> {
                            // Set flutter flag, which triggers another scan for iOS and Android native dependencies.
                            containsFlutter = true
                            // Set hardcoded package details.
                            rawName = "flutter"
                            homepageUrl = "https://github.com/flutter/flutter"
                            description = "Flutter SDK"
                        }

                        pkgInfoFromLockFile["description"].textValueOrEmpty() == "flutter_test" -> {
                            // Set hardcoded package details.
                            rawName = "flutter_test"
                            homepageUrl = "https://github.com/flutter/flutter/tree/master/packages/flutter_test"
                            description = "Flutter Test SDK"
                        }
                    }

                    if (version.isEmpty()) {
                        log.warn { "No version information found for package $rawName." }
                    }

                    val id = Identifier(
                        type = managerName,
                        namespace = rawName.substringBefore('/'),
                        name = rawName.substringAfter('/'),
                        version = version
                    )

                    packages[id] = Package(
                        id,
                        authors = authors,
                        // Pub does not declare any licenses in the pubspec files, therefore we keep this empty.
                        declaredLicenses = sortedSetOf(),
                        description = description,
                        homepageUrl = homepageUrl,
                        // Pub does not create binary artifacts, therefore use any empty artifact.
                        binaryArtifact = RemoteArtifact.EMPTY,
                        // Pub does not create source artifacts, therefore use any empty artifact.
                        sourceArtifact = RemoteArtifact.EMPTY,
                        vcs = vcs,
                        vcsProcessed = processPackageVcs(vcs, homepageUrl)
                    )
                } catch (e: JacksonYAMLParseException) {
                    e.showStackTrace()

                    val packageVersion = pkgInfoFromLockFile["version"].textValueOrEmpty()
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
        if (containsFlutter) {
            lockFile["packages"]?.forEach { pkgInfoFromLockFile ->
                // As this package contains flutter, trigger Gradle manually for it.
                scanAndroidPackages(pkgInfoFromLockFile, labels, workingDir).forEach { result ->
                    result.collectPackagesByScope("releaseCompileClasspath").forEach { pkg ->
                        packages[pkg.id] = pkg
                    }

                    issues += result.issues
                }

                // As this package contains flutter, trigger CocoaPods manually for it.
                scanIosPackages(pkgInfoFromLockFile, workingDir)?.let { result ->
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

        return transformVersion(result.stderr)
    }

    override fun command(workingDir: File?): String =
        if (flutterAbsolutePath.isDirectory) "$flutterAbsolutePath${File.separator}$dartCommand" else dartCommand

    private fun commandPub(): String = "${command()} pub"

    private fun commandFlutter(): String =
        if (flutterAbsolutePath.isDirectory) "$flutterAbsolutePath${File.separator}$flutterCommand pub"
        else "$flutterCommand pub"

    override fun run(workingDir: File?, vararg args: String): ProcessCapture {
        var result = ProcessCapture(workingDir, *commandPub().split(' ').toTypedArray(), *args)
        if (result.isError) {
            // If Pub fails with the message that Flutter should be used instead, fall back to using Flutter.
            if ("Flutter users should run `flutter" in result.errorMessage) {
                result = ProcessCapture(workingDir, *commandFlutter().split(' ').toTypedArray(), *args).requireSuccess()
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
            ProcessCapture(workingDir, *commandFlutter().split(' ').toTypedArray(), "get").requireSuccess()
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
private fun parseAuthors(pubspec: JsonNode): SortedSet<String> =
    (listOfNotNull(pubspec["author"]) + pubspec["authors"]?.toList().orEmpty()).mapNotNullTo(sortedSetOf()) {
        parseAuthorString(it.textValue())
    }

private fun ProjectAnalyzerResult.collectPackagesByScope(scopeName: String): List<Package> {
    val scope = project.scopes.find { it.name == scopeName } ?: return emptyList()
    return packages.filter { it.id in scope }
}
