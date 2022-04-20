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
import org.ossreviewtoolkit.utils.common.collectMessagesAsString
import org.ossreviewtoolkit.utils.common.realFile
import org.ossreviewtoolkit.utils.common.safeMkdirs
import org.ossreviewtoolkit.utils.common.textValueOrEmpty
import org.ossreviewtoolkit.utils.common.unpack
import org.ossreviewtoolkit.utils.core.OkHttpClientHelper
import org.ossreviewtoolkit.utils.core.log
import org.ossreviewtoolkit.utils.core.ortToolsDirectory
import org.ossreviewtoolkit.utils.core.showStackTrace

private const val GRADLE_VERSION = "7.3"
private const val PUBSPEC_YAML = "pubspec.yaml"
private const val PUB_LOCK_FILE = "pubspec.lock"

private val flutterCommand = if (Os.isWindows) "flutter.bat" else "flutter"
private val dartCommand = if (Os.isWindows) "dart.bat" else "dart"

private val flutterVersion = Os.env["FLUTTER_VERSION"] ?: "2.10.5-stable"
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

    private val processedPackages = mutableListOf<String>()
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

            val parsePackagesResult = parseInstalledPackages(lockFile, labels)

            val gradleDefinitionFiles = gradleDefinitionFilesForPubDefinitionFiles.getValue(definitionFile).toList()

            if (gradleDefinitionFiles.isNotEmpty()) {
                log.info { "Found ${gradleDefinitionFiles.size} Gradle definition file(s) at:" }

                gradleDefinitionFiles.forEach { gradleDefinitionFile ->
                    val relativePath =
                        gradleDefinitionFile.toRelativeString(workingDir).takeIf { it.isNotEmpty() } ?: "."

                    log.info { "\t$relativePath" }
                }

                log.info { "Running Gradle analysis for Flutter project." }

                val gradleAnalyzerResult = Gradle.Factory().create(analysisRoot, analyzerConfig, repoConfig)
                    .resolveDependencies(gradleDefinitionFiles, labels)

                val androidScope = Scope("android", sortedSetOf())
                gradleAnalyzerResult.projectResults.values.flatten().forEach { projectAnalyzerResult ->
                    val project = projectAnalyzerResult.project.withResolvedScopes(gradleAnalyzerResult.dependencyGraph)

                    androidScope.dependencies += PackageReference(
                        id = project.id,
                        linkage = PackageLinkage.PROJECT_STATIC,
                        dependencies = project.scopes.find { it.name == "releaseCompileClasspath" }?.dependencies
                            ?: sortedSetOf()
                    )

                    projectAnalyzerResults += ProjectAnalyzerResult(
                        project,
                        projectAnalyzerResult.packages,
                        projectAnalyzerResult.issues
                    )
                }

                scopes += androidScope
                packages += gradleAnalyzerResult.sharedPackages.associateBy { it.id }
            }

            packages += parsePackagesResult.packages
            issues += parsePackagesResult.issues

            log.info { "Successfully parsed installed packages." }

            scopes += parseScope("dependencies", manifest, lockFile, parsePackagesResult.packages, labels)
            scopes += parseScope("dev_dependencies", manifest, lockFile, parsePackagesResult.packages, labels)
        }

        log.info { "Reading ${definitionFile.name} file in $workingDir." }

        val project = parseProject(definitionFile, manifest, scopes)

        projectAnalyzerResults += ProjectAnalyzerResult(project, packages.values.toSortedSet(), issues)

        return projectAnalyzerResults
    }

    private fun parseScope(
        scopeName: String,
        manifest: JsonNode,
        lockFile: JsonNode,
        packages: Map<Identifier, Package>,
        labels: Map<String, String>
    ): Scope {
        val packageName = manifest["name"].textValue()

        log.info { "Parsing scope '$scopeName' for package '$packageName'." }

        val requiredPackages = manifest[scopeName]?.fieldNames()?.asSequence()?.toList() ?: listOf<String>()
        val dependencies = buildDependencyTree(requiredPackages, manifest, lockFile, packages, labels)
        return Scope(scopeName, dependencies)
    }

    private fun buildDependencyTree(
        dependencies: List<String>,
        manifest: JsonNode,
        lockFile: JsonNode,
        packages: Map<Identifier, Package>,
        labels: Map<String, String>
    ): SortedSet<PackageReference> {
        val packageReferences = mutableSetOf<PackageReference>()
        val nameOfCurrentPackage = manifest["name"].textValue()
        val containsFlutter = "flutter" in dependencies

        log.info { "buildDependencyTree for package $nameOfCurrentPackage " }

        // Ensure we process every package only once.
        processedPackages += nameOfCurrentPackage

        // Lookup the dependencies listed in pubspec.yaml file and build the dependency tree.
        dependencies.forEach { packageName ->
            // We need to resolve the dependency tree for every package just once. This check ensures we do not run into
            // infinite loops. When we add this check, and two packages list the same package as dependency, only the
            // first might be listed.
            if (packageName in processedPackages) return@forEach

            val pkgInfoFromLockFile = lockFile["packages"][packageName]
            // If the package is marked as SDK (e.g. flutter, flutter_test, dart) we cannot resolve it correctly as
            // it is not stored in .pub-cache. For now we just ignore those SDK packages.
            if (pkgInfoFromLockFile == null || pkgInfoFromLockFile["source"].textValueOrEmpty() == "sdk") return@forEach

            val id = Identifier(
                type = managerName,
                namespace = packageName.substringBefore('/'),
                name = packageName.substringAfter('/'),
                version = pkgInfoFromLockFile["version"].textValueOrEmpty()
            )

            val packageInfo = packages[id] ?: throw IOException("Could not find package info for $packageName")

            try {
                val dependencyYamlFile = readPackageInfoFromCache(pkgInfoFromLockFile)
                val requiredPackages =
                    dependencyYamlFile["dependencies"]?.fieldNames()?.asSequence()?.toList().orEmpty()

                val transitiveDependencies =
                    buildDependencyTree(requiredPackages, dependencyYamlFile, lockFile, packages, labels)

                // If the project contains Flutter, we need to trigger the analyzer for Gradle and CocoaPod
                // dependencies for each pub dependency manually, as the analyzer will only scan the
                // projectRoot, but not the packages in the ".pub-cache" directory.
                if (containsFlutter) {
                    scanAndroidPackages(pkgInfoFromLockFile, labels).forEach { resultAndroid ->
                        packageReferences += packageInfo.toReference(
                            dependencies = resultAndroid.project.scopes
                                .find { it.name == "releaseCompileClasspath" }
                                ?.dependencies
                        )
                    }
                    // TODO: Enable support for iOS / Cocoapods once the package manager is implemented.
                }

                packageReferences += packageInfo.toReference(dependencies = transitiveDependencies)
            } catch (e: IOException) {
                e.showStackTrace()

                packageReferences += packageInfo.toReference(
                    issues = listOf(
                        createAndLogIssue(
                            source = managerName,
                            message = "Could not resolve dependencies of '$packageName': " +
                                    e.collectMessagesAsString()
                        )
                    )
                )
            }
        }

        return packageReferences.toSortedSet()
    }

    private val analyzerResultCacheAndroid = mutableMapOf<String, List<ProjectAnalyzerResult>>()

    private fun scanAndroidPackages(packageInfo: JsonNode, labels: Map<String, String>): List<ProjectAnalyzerResult> {
        val packageName = packageInfo["description"]["name"].textValueOrEmpty()

        // We cannot find packages without a valid name.
        if (packageName.isEmpty()) return emptyList()

        val projectRoot = reader.findProjectRoot(packageInfo) ?: return emptyList()
        val androidDir = projectRoot.resolve("android")
        val packageFile = androidDir.resolve("build.gradle")

        // Check for build.gradle failed, no Gradle scan required.
        if (!packageFile.isFile) return emptyList()

        log.info { "Analyzing Android dependencies for package '$packageName' using Gradle version $GRADLE_VERSION." }

        return analyzerResultCacheAndroid.getOrPut(packageName) {
            Gradle("Gradle", androidDir, analyzerConfig, repoConfig, GRADLE_VERSION)
                .resolveDependencies(listOf(packageFile), labels).run {
                    projectResults.getValue(packageFile).map { result ->
                        val project = result.project.withResolvedScopes(dependencyGraph)
                        result.copy(project = project, packages = sharedPackages.toSortedSet())
                    }
                }
        }
    }

    private fun scanIosPackages(packageInfo: JsonNode): ProjectAnalyzerResult? {
        // TODO: Implement similar to `scanAndroidPackages` once Cocoapods is implemented.
        val packageName = packageInfo["description"]["name"].textValueOrEmpty()

        // We cannot find packages without a valid name.
        if (packageName.isEmpty()) return null

        val projectRoot = reader.findProjectRoot(packageInfo) ?: return null
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
        val rawName = pubspec["description"]["name"]?.textValue() ?: definitionFile.parentFile.name
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

    private fun parseInstalledPackages(lockFile: JsonNode, labels: Map<String, String>): ParsePackagesResult {
        log.info { "Parsing installed Pub packages..." }

        val packages = mutableMapOf<Identifier, Package>()
        val issues = mutableListOf<OrtIssue>()

        // Flag if the project is a flutter project.
        var containsFlutter = false

        listOf("packages"/*, "packages-dev"*/).forEach {
            lockFile[it]?.forEach { pkgInfoFromLockFile ->
                try {
                    val version = pkgInfoFromLockFile["version"].textValueOrEmpty()
                    var description = ""
                    var rawName = ""
                    var homepageUrl = ""
                    var vcs = VcsInfo.EMPTY
                    var authors: SortedSet<String> = sortedSetOf<String>()

                    // For now, we ignore SDKs like the Dart SDK and the Flutter SDK in the analyzer.
                    when {
                        pkgInfoFromLockFile["source"].textValueOrEmpty() != "sdk" -> {
                            val pkgInfoFromYamlFile = readPackageInfoFromCache(pkgInfoFromLockFile)

                            rawName = pkgInfoFromYamlFile["name"].textValueOrEmpty()
                            description = pkgInfoFromYamlFile["description"].textValueOrEmpty()
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

                    val packageName = pkgInfoFromLockFile["name"].textValueOrEmpty()
                    val packageVersion = pkgInfoFromLockFile["version"].textValueOrEmpty()
                    issues += createAndLogIssue(
                        source = managerName,
                        message = "Failed to parse $PUBSPEC_YAML for package $packageName:$packageVersion: " +
                                e.collectMessagesAsString()
                    )
                }
            }
        }

        // If the project contains Flutter, we need to trigger the analyzer for Gradle and CocoaPod dependencies for
        // each Pub dependency manually, as the analyzer will only analyze the projectRoot, but not the packages in
        // the ".pub-cache" directory.
        if (containsFlutter) {
            lockFile["packages"]?.forEach { pkgInfoFromLockFile ->
                // As this package contains flutter, trigger Gradle manually for it.
                scanAndroidPackages(pkgInfoFromLockFile, labels).forEach { result ->
                    result.collectPackagesByScope("releaseCompileClasspath").forEach { pkg ->
                        packages[pkg.id] = pkg
                    }

                    issues += result.issues
                }

                // As this package contains flutter, trigger CocoaPods manually for it.
                scanIosPackages(pkgInfoFromLockFile)?.let { result ->
                    result.packages.forEach { pkg ->
                        packages[pkg.id] = pkg
                    }

                    issues += result.issues
                }
            }
        }

        return ParsePackagesResult(packages, issues)
    }

    private fun readPackageInfoFromCache(packageInfo: JsonNode): JsonNode {
        val definitionFile = reader.findFile(packageInfo, PUBSPEC_YAML)
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
