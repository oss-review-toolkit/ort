/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

package com.here.ort.analyzer.managers

import ch.frankel.slf4k.*

import com.fasterxml.jackson.databind.JsonNode

import com.here.ort.analyzer.AbstractPackageManagerFactory
import com.here.ort.analyzer.PackageManager
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.model.Identifier
import com.here.ort.model.Package
import com.here.ort.model.PackageReference
import com.here.ort.model.Project
import com.here.ort.model.ProjectAnalyzerResult
import com.here.ort.model.RemoteArtifact
import com.here.ort.model.Scope
import com.here.ort.model.yamlMapper
import com.here.ort.model.VcsInfo
import com.here.ort.model.config.AnalyzerConfiguration
import com.here.ort.model.config.RepositoryConfiguration
import com.here.ort.model.OrtIssue
import com.here.ort.model.Severity
import com.here.ort.model.VcsType
import com.here.ort.utils.CommandLineTool
import com.here.ort.utils.Os
import com.here.ort.utils.ProcessCapture
import com.here.ort.utils.log
import com.here.ort.utils.showStackTrace
import com.here.ort.utils.textValueOrEmpty
import com.here.ort.utils.collectMessagesAsString
import com.here.ort.utils.getPathFromEnvironment
import com.here.ort.utils.getUserHomeDirectory

import com.vdurmont.semver4j.Requirement

import java.io.File
import java.io.IOException
import java.util.SortedSet

const val PUB_LOCK_FILE = "pubspec.lock"

/**
 * The [Pub](https://https://pub.dev/) package manager for Dart / Flutter.
 */
class Pub(
    name: String,
    analyzerRoot: File,
    analyzerConfig: AnalyzerConfiguration,
    repoConfig: RepositoryConfiguration
) : PackageManager(name, analyzerRoot, analyzerConfig, repoConfig), CommandLineTool {
    class Factory : AbstractPackageManagerFactory<Pub>("Pub") {
        override val globsForDefinitionFiles = listOf("pubspec.yaml")

        override fun create(
            analyzerRoot: File,
            analyzerConfig: AnalyzerConfiguration,
            repoConfig: RepositoryConfiguration
        ) = Pub(managerName, analyzerRoot, analyzerConfig, repoConfig)
    }

    /**
     * A reader for the Pub cache directory. It looks for files in the ".pub-cache" directory in the user's home
     * directory. If Flutter is installed it additionally looks for files in the ".pub-cache" directory of Flutter's
     * installation directory.
     */
    private class PubCacheReader {
        private val pubCacheRoot by lazy {
            // TODO: Add support for the PUB_CACHE environment variable.
            if (Os.isWindows) {
                val appData = System.getenv("APPDATA")
                File(appData, "Pub/Cache")
            } else {
                getUserHomeDirectory().resolve(".pub-cache/")
            }
        }

        private val flutterPubCacheRoot by lazy {
            findFlutterHome()?.resolve(".pub-cache")?.takeIf { it.isDirectory }
        }

        private fun findFlutterHome(): File? {
            val flutterCommand = if (Os.isWindows) "flutter.bat" else "flutter"
            return getPathFromEnvironment(flutterCommand)?.parentFile?.parentFile
        }

        fun findFile(packageInfo: JsonNode, fileName: String): File? {
            val artifactRootDir = findProjectRoot(packageInfo) ?: return null

            // Try to locate the file directly.
            val file = File(artifactRootDir, fileName)
            if (file.isFile) return file

            // Search the directory tree for the file.
            return artifactRootDir
                .walkTopDown()
                .find { it.isFile && it.name == fileName }
        }

        fun findProjectRoot(packageInfo: JsonNode): File? {
            val packageVersion = packageInfo["version"].textValueOrEmpty()
            val type = packageInfo["source"].textValueOrEmpty()
            val description = packageInfo["description"]
            val packageName = description["name"].textValueOrEmpty()
            val url = description["url"].textValueOrEmpty()
            val resolvedRef = packageInfo["resolved-ref"].textValueOrEmpty()

            val path = if (type == "hosted" && url.isNotEmpty()) {
                // Packages with source set to "hosted" and "url" key in description set to "https://pub.dartlang.org".
                // The path should be resolved to "hosted/pub.dartlang.org/packageName-packageVersion".
                "hosted/${url.replace("https://", "")}/$packageName-$packageVersion"
            } else if (type == "git" && resolvedRef.isNotEmpty()) {
                // Packages with source set to "git" and a "resolved-ref" key in description set to a gitHash.
                // The path should be resolved to "git/packageName-gitHash".
                "git/$packageName-$resolvedRef"
            } else {
                log.error { "Could not find projectRoot of '$packageName'." }

                // Unsupported type.
                null
            }

            if (path != null) {
                File(pubCacheRoot, path).let {
                    if (it.isDirectory) {
                        return it
                    }
                }

                if (flutterPubCacheRoot != null) {
                    File(flutterPubCacheRoot, path).let {
                        if (it.isDirectory) {
                            return it
                        }
                    }
                }
            }

            return null
        }
    }

    private data class ParsePackagesResult(
        val packages: Map<Identifier, Package>,
        val issues: List<OrtIssue>
    )

    private val processedPackages = mutableListOf<String>()
    private val reader = PubCacheReader()

    override fun getVersionRequirement(): Requirement = Requirement.buildIvy("[2.2,)")

    override fun resolveDependencies(definitionFile: File): ProjectAnalyzerResult? {
        val workingDir = definitionFile.parentFile
        val manifest = yamlMapper.readTree(definitionFile)

        val hasDependencies = manifest.fields().asSequence().any { (key, value) ->
            key.startsWith("dependencies") && value.count() > 0
        }

        val packages = mutableMapOf<Identifier, Package>()
        val scopes = sortedSetOf<Scope>()
        val issues = mutableListOf<OrtIssue>()

        if (hasDependencies) {
            installDependencies(workingDir)

            log.info { "Reading $PUB_LOCK_FILE file in $workingDir." }

            val lockFile = yamlMapper.readTree(File(workingDir, PUB_LOCK_FILE))

            log.info { "Successfully read lockfile." }

            val parsePackagesResult = parseInstalledPackages(lockFile)
            packages += parsePackagesResult.packages
            issues += parsePackagesResult.issues

            log.info { "Successfully parsed installed packages." }

            scopes += parseScope("dependencies", manifest, lockFile, parsePackagesResult.packages)
            scopes += parseScope("dev_dependencies", manifest, lockFile, parsePackagesResult.packages)
        }

        log.info { "Reading ${definitionFile.name} file in $workingDir." }

        val project = parseProject(definitionFile, scopes)

        return ProjectAnalyzerResult(project, packages.values.map { it.toCuratedPackage() }.toSortedSet(), issues)
    }

    private fun parseScope(
        scopeName: String,
        manifest: JsonNode,
        lockFile: JsonNode,
        packages: Map<Identifier, Package>
    ): Scope {
        val packageName = manifest["name"].textValue()

        log.info { "Parsing scope '$scopeName' for package '$packageName'." }

        val requiredPackages = manifest[scopeName]?.fieldNames()?.asSequence()?.toList() ?: listOf<String>()
        val dependencies = buildDependencyTree(requiredPackages, manifest, lockFile, packages)
        return Scope(scopeName, dependencies)
    }

    private fun buildDependencyTree(
        dependencies: List<String>,
        manifest: JsonNode,
        lockFile: JsonNode,
        packages: Map<Identifier, Package>
    ): SortedSet<PackageReference> {
        val packageReferences = mutableSetOf<PackageReference>()
        val nameOfCurrentPackage = manifest["name"].textValue()
        val containsFlutter = dependencies.contains("flutter")

        log.info { "buildDependencyTree for package $nameOfCurrentPackage " }

        // Ensure we process every package only once.
        processedPackages.add(nameOfCurrentPackage)

        // Lookup the dependencies listed in pubspec.yaml file and build the dependency tree.
        dependencies.forEach { packageName ->
            // We need to resolve the dependency tree for every package just once. This check ensures we do not run into
            // infinite loops. When we add this check, and two packages list the same package as dependency, only the
            // first might be listed.
            if (packageName !in processedPackages) {
                val pkgInfoFromLockFile = lockFile["packages"][packageName]

                // If the package is marked as SDK (e.g. flutter, flutter_test, dart) we cannot resolve it correctly as
                // it is not stored in .pub-cache. For now we just ignore those SDK packages.
                if (pkgInfoFromLockFile != null && pkgInfoFromLockFile["source"].textValueOrEmpty() != "sdk") {
                    val id = Identifier(
                        type = managerName,
                        namespace = packageName.substringBefore("/"),
                        name = packageName.substringAfter("/"),
                        version = pkgInfoFromLockFile["version"].textValueOrEmpty()
                    )

                    val packageInfo = packages[id] ?: throw IOException("Could not find package info for $packageName")

                    try {
                        val dependencyYamlFile = readPackageInfoFromCache(pkgInfoFromLockFile)
                        val requiredPackages =
                            dependencyYamlFile["dependencies"]?.fieldNames()?.asSequence()?.toList().orEmpty()

                        val transitiveDependencies =
                            buildDependencyTree(requiredPackages, dependencyYamlFile, lockFile, packages)

                        // If the project contains Flutter, we need to trigger the analyzer for Gradle and CocoaPod
                        // dependencies for each pub dependency manually, as the analyzer will only scan the
                        // projectRoot, but not the packages in the .pub-cache folder.
                        if (containsFlutter) {
                            val resultAndroid = scanAndroidPackages(pkgInfoFromLockFile)
                            if (resultAndroid != null) {
                                packageReferences += packageInfo.toReference(
                                    dependencies = resultAndroid.project.scopes
                                        .find { it.name == "releaseCompileClasspath" }
                                        ?.collectDependencies(-1, false)
                                )
                            }
                            // TODO: Enable support for iOS / Cocoapods once the package manager is implemented.
                            /*
                            val resultIos = scanIosPackages(pkgInfoFromLockFile)
                            if (resultIos != null) {
                                packageReferences += packageInfo.toReference(
                                    dependencies = resultIos.project.scopes
                                        .find { it.name == "release" }
                                        ?.collectDependencies(-1, false)
                                )
                            }
                            */
                        }

                        packageReferences += packageInfo.toReference(dependencies = transitiveDependencies)
                    } catch (e: IOException) {
                        e.showStackTrace()

                        log.error { "Could not resolve dependencies of '$packageName': ${e.collectMessagesAsString()}" }

                        packageInfo.toReference(
                            errors = listOf(OrtIssue(source = managerName, message = e.collectMessagesAsString()))
                        )
                    }
                }
            }
        }

        return packageReferences.toSortedSet()
    }

    private val analyzerResultCacheAndroid = mutableMapOf<String, ProjectAnalyzerResult>()

    private fun scanAndroidPackages(packageInfo: JsonNode): ProjectAnalyzerResult? {
        val packageName = packageInfo["description"]["name"].textValueOrEmpty()

        // We cannot find packages without a valid name.
        if (packageName.isEmpty()) return null

        val projectRoot = reader.findProjectRoot(packageInfo) ?: return null
        val androidDir = File(projectRoot, "android")
        val packageFile = File(androidDir, "build.gradle")

        // Check for build.gradle failed, no Gradle scan required.
        if (!packageFile.isFile) return null

        log.info { "Analyzing Android dependencies for package '$packageName'." }

        return if (analyzerResultCacheAndroid.containsKey(packageName)) {
            analyzerResultCacheAndroid[packageName]
        } else {
            Gradle("Gradle", androidDir, analyzerConfig, repoConfig)
                .resolveDependencies(listOf(packageFile))[packageFile]
                ?.also {
                    analyzerResultCacheAndroid[packageName] = it
                }
        }
    }

    private fun scanIosPackages(packageInfo: JsonNode): ProjectAnalyzerResult? {
        // TODO: Implement similar to `scanAndroidPackages` once Cocoapods is implemented.
        val packageName = packageInfo["description"]["name"].textValueOrEmpty()

        // We cannot find packages without a valid name.
        if (packageName.isEmpty()) return null

        val projectRoot = reader.findProjectRoot(packageInfo) ?: return null
        val iosDir = File(projectRoot, "ios")
        val packageFile = File(iosDir, "$packageName.podspec")

        // Check for build.gradle failed, no Gradle scan required.
        if (!packageFile.isFile) return null

        val message = "Cannot get iOS dependencies for package '$packageName'. " +
                "Support for CocoaPods is not yet implemented."
        log.warn { message }
        val issue = OrtIssue(source = managerName, severity = Severity.WARNING, message = message)
        return ProjectAnalyzerResult(Project.EMPTY, sortedSetOf(), listOf(issue))
    }

    private fun parseProject(definitionFile: File, scopes: SortedSet<Scope>): Project {
        val data = yamlMapper.readTree(definitionFile)
        val homepageUrl = data["homepage"].textValueOrEmpty()
        val vcs = parseVcsInfo(data)
        val rawName = data["description"]["name"]?.textValue() ?: definitionFile.parentFile.name

        return Project(
            id = Identifier(
                type = managerName,
                namespace = rawName.substringBefore("/"),
                name = rawName.substringAfter("/"),
                version = data["version"].textValueOrEmpty()
            ),
            definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
            // Pub does not declare any licenses in the pubspec files, therefore we keep this empty.
            declaredLicenses = sortedSetOf(),
            vcs = vcs,
            vcsProcessed = processProjectVcs(definitionFile.parentFile, vcs, homepageUrl),
            homepageUrl = homepageUrl,
            scopes = scopes
        )
    }

    private fun parseInstalledPackages(lockFile: JsonNode): ParsePackagesResult {
        log.info { "Parsing installed Pub packages..." }

        val packages = mutableMapOf<Identifier, Package>()
        val issues = mutableListOf<OrtIssue>()

        // Flag if the project is a flutter project.
        var containsFlutter = false

        listOf("packages"/*, "packages-dev"*/).forEach {
            lockFile[it]?.forEach { pkgInfoFromLockFile ->
                val version = pkgInfoFromLockFile["version"].textValueOrEmpty()
                var description = ""
                var rawName = ""
                var homepageUrl = ""
                var vcsFromPackage = VcsInfo.EMPTY

                // For now, we ignore SDKs like the Dart SDK and the Flutter SDK in the analyzer.
                when {
                    pkgInfoFromLockFile["source"].textValueOrEmpty() != "sdk" -> {
                        val pkgInfoYamlFile = readPackageInfoFromCache(pkgInfoFromLockFile)
                        vcsFromPackage = parseVcsInfo(pkgInfoYamlFile)
                        description = parseDescriptionInfo(pkgInfoYamlFile)
                        rawName = pkgInfoFromLockFile["description"]["name"].textValueOrEmpty()
                        homepageUrl = pkgInfoFromLockFile["description"]["url"].textValueOrEmpty()
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
                    namespace = rawName.substringBefore("/"),
                    name = rawName.substringAfter("/"),
                    version = version
                )

                packages[id] = Package(
                    id,
                    // Pub does not declare any licenses in the pubspec files, therefore we keep this empty.
                    declaredLicenses = sortedSetOf(),
                    description = description,
                    homepageUrl = homepageUrl,
                    // Pub does not create binary artifacts, therefore use any empty artifact.
                    binaryArtifact = RemoteArtifact.EMPTY,
                    // Pub does not create source artifacts, therefore use any empty artifact.
                    sourceArtifact = RemoteArtifact.EMPTY,
                    vcs = vcsFromPackage,
                    vcsProcessed = processPackageVcs(vcsFromPackage, homepageUrl)
                )
            }
        }

        // If the project contains Flutter, we need to trigger the analyzer for Gradle and CocoaPod dependencies for
        // each Pub dependency manually, as the analyzer will only analyze the projectRoot, but not the packages in
        // the .pub-cache folder.
        if (containsFlutter) {
            lockFile["packages"]?.forEach { pkgInfoFromLockFile ->
                // As this package contains flutter, trigger Gradle manually for it.
                scanAndroidPackages(pkgInfoFromLockFile)?.let { result ->
                    result.collectPackagesByScope("releaseCompileClasspath").forEach { item ->
                        packages[item.pkg.id] = item.pkg
                    }

                    issues += result.errors
                }


                // As this package contains flutter, trigger CocoaPods manually for it.
                scanIosPackages(pkgInfoFromLockFile)?.let { result ->
                    result.packages.forEach { item ->
                        packages[item.pkg.id] = item.pkg
                    }

                    issues += result.errors
                }
            }
        }

        return ParsePackagesResult(packages, issues)
    }

    private fun readPackageInfoFromCache(packageInfo: JsonNode): JsonNode {
        val definitionFile = reader.findFile(packageInfo, "pubspec.yaml")
        return yamlMapper.readTree(definitionFile)
    }

    private fun parseDescriptionInfo(packageInfo: JsonNode): String {
        return packageInfo["description"].textValueOrEmpty()
    }

    private fun parseVcsInfo(packageInfo: JsonNode): VcsInfo {
        return packageInfo["homepage"]?.let {
            // Currently, we only support Github repositories.
            if (it.textValueOrEmpty().contains("github")) {
                VcsInfo(VcsType.GIT, it.textValueOrEmpty() + ".git", "")
            } else {
                VcsInfo.EMPTY
            }
        } ?: VcsInfo.EMPTY
    }

    override fun command(workingDir: File?) = if (Os.isWindows) "pub.bat" else "pub"

    private fun commandFlutter() = if (Os.isWindows) "flutter.bat packages" else "flutter packages"

    override fun run(workingDir: File?, vararg args: String): ProcessCapture {
        var result = ProcessCapture(workingDir, *command(workingDir).split(" ").toTypedArray(), *args)
        if (result.isError) {
            // If Pub fails with the message that Flutter should be used instead, fall back to using Flutter.
            if (result.errorMessage.contains("Flutter users should run `flutter")) {
                result = ProcessCapture(workingDir, *commandFlutter().split(" ").toTypedArray(), *args).requireSuccess()
            } else {
                throw IOException(result.errorMessage)
            }
        }
        return result
    }

    private fun installDependencies(workingDir: File) {
        require(analyzerConfig.allowDynamicVersions || File(workingDir, PUB_LOCK_FILE).isFile) {
            "No lock file found in $workingDir, dependency versions are unstable."
        }

        // The "get" command creates a "pubspec.lock" file (if not yet present) except for projects without any
        // dependencies, see https://dart.dev/tools/pub/cmd/pub-get.
        run(workingDir, "get")
    }
}
