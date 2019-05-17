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

import com.here.ort.utils.CommandLineTool
import com.here.ort.utils.ProcessCapture
import com.here.ort.utils.log
import com.here.ort.utils.showStackTrace
import com.here.ort.utils.textValueOrEmpty
import com.here.ort.utils.collectMessagesAsString
import com.here.ort.utils.getUserHomeDirectory
import com.vdurmont.semver4j.Requirement

import java.io.File
import java.io.IOException
import java.util.SortedSet

const val PUB_LOCK_FILE = "pubspec.lock"

/**
 * The [Pub](https://https://pub.dev/) package manager for Dart / Flutter.
 */
class Pub (
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
        ) =
            Pub(managerName, analyzerRoot, analyzerConfig, repoConfig)
    }

    override fun getVersionRequirement(): Requirement = Requirement.buildIvy("[2.2,)")

    override fun beforeResolution(definitionFiles: List<File>) {

    }

    /**
     * A workspace reader that is backed by the local Gradle artifact cache.
     */
    private class PubCacheReader {
        // TODO: Check if we can support a second .pub-cache
        // If there is a .pub-cache directory in Flutter root,
        // flutter packages get will use that one instead of the .pub-cache directory in user home
        // Currently we do not support usage of two different pub-cache directories.
        // Therefore you need to delete the .pub-cache directory in your flutter root folder
        // to force flutter to use the default one
        private val pubCacheRoot = getUserHomeDirectory().resolve(".pub-cache/")

        fun findFile(packageInfo: JsonNode, fileName: String): File? {
            val artifactRootDir = findProjectRoot(packageInfo);

            // try to locate file directly
            val file = File(
                artifactRootDir,
                fileName
            );
            if(file.isFile) return file;

            // search directory for file
            return artifactRootDir.walkTopDown().find {
                it.isFile && it.name == fileName
            }
        }

        fun findProjectRoot(packageInfo: JsonNode): File {
            var path : String;

            val packageVersion = packageInfo["version"].textValueOrEmpty();
            val type = packageInfo["source"].textValueOrEmpty();
            val description = packageInfo["description"];
            val packageName = description["name"].textValueOrEmpty();
            val url = description["url"].textValueOrEmpty();
            val resolvedRef = packageInfo["resolved-ref"].textValueOrEmpty();

            if(type == "hosted" && url.isNotEmpty()){
                // packages with source set to "hosted" and "url" key in description set to "https://pub.dartlang.org",
                // path should be resolved to "hosted/pub.dartlang.org/packageName-packageVersion"
                path = "hosted/" + url.replace("https://","") + "/" + packageName + "-" + packageVersion;
            } else if(type == "git" && resolvedRef.isNotEmpty()){
                // packages with source set to "git" and a "resolved-ref" key in description set to a gitHash,
                // should be resolved to "git/packageName-gitHash"
                path = "git/" + packageName + "-" + resolvedRef;
            } else {
                // not supported type
                path = "/";

                log.error { "Could not find projectRoot of '$packageName'" }
            }

            val artifactRootDir = File(
                pubCacheRoot,
                path
            )

            return artifactRootDir;
        }
    }

    private val reader = PubCacheReader();

    override fun resolveDependencies(definitionFile: File): ProjectAnalyzerResult? {
        val workingDir = definitionFile.parentFile

        val manifest = yamlMapper.readTree(definitionFile)
        val hasDependencies = manifest.fields().asSequence().any { (key, value) ->
            key.startsWith("dependencies") && value.count() > 0
        }

        val (packages, scopes) = if (hasDependencies) {
            installDependencies(workingDir)

            log.info { "Reading $PUB_LOCK_FILE file in $workingDir " }
            val lockFile = yamlMapper.readTree(File(workingDir, PUB_LOCK_FILE))

            log.info { "Reading lockfile success. " }

            var packages = parseInstalledPackages(lockFile)

            log.info { "parseInstalledPackages success. " }

            val scopes = sortedSetOf(
                parseScope("dependencies", manifest, lockFile, packages),
                parseScope("dev_dependencies", manifest, lockFile, packages)
            )

            Pair(packages, scopes)
        } else {
            Pair(emptyMap(), sortedSetOf())
        }

        log.info { "Reading ${definitionFile.name} file in $workingDir " }

        val project = parseProject(definitionFile, scopes)

        return ProjectAnalyzerResult(project, packages.values.map { it.toCuratedPackage() }.toSortedSet())

    }

    private fun parseScope(
        scopeName: String, manifest: JsonNode, lockFile: JsonNode, packages: Map<Identifier, Package>
    ): Scope {
        val packageName = manifest["name"].textValue();
        log.info { "parseScope '$scopeName' for $packageName" }
        val requiredPackages = manifest[scopeName]?.fieldNames()?.asSequence()?.toList() ?: listOf<String>()
        val dependencies = buildDependencyTree(requiredPackages, manifest, lockFile, packages)
        return Scope(scopeName, dependencies)
    }

    private val processedPackages = mutableListOf<String>();

    private fun buildDependencyTree(
        dependencies: List<String>,
        manifest: JsonNode,
        lockFile: JsonNode,
        packages: Map<Identifier, Package>
    ): SortedSet<PackageReference> {
        val packageReferences = mutableSetOf<PackageReference>();
        val nameOfCurrentPackage = manifest["name"].textValue();
        val containsFlutter = dependencies.contains("flutter");

        log.info { "buildDependencyTree for package $nameOfCurrentPackage " }

        // ensure we process every package only once
        processedPackages.add(nameOfCurrentPackage);

        // now lookup dependencies listed in pubspec.yaml file and build the tree
        dependencies.forEach { packageName ->
            // we need to resolve the dependency tree for every package just once.
            // This check ensures we do not run into infinite loops
            // when we add this check, and two packages list the same package as dependency,
            // only the first might be listed.
            if(!processedPackages.contains(packageName)) {
                val pkgInfoFromLockFile = lockFile["packages"].get(packageName);

                // if the package is marked as SDK (e.g. flutter, flutter_test, dart)
                // we cannot resolve it correctly as it is not stored in .pub-cache
                // for now we just ignore those SDK packages
                if(pkgInfoFromLockFile != null && pkgInfoFromLockFile["source"].textValueOrEmpty() != "sdk") {
                    val id = Identifier(
                        type = managerName,
                        namespace = packageName.substringBefore("/"),
                        name = packageName.substringAfter("/"),
                        version = pkgInfoFromLockFile["version"].textValueOrEmpty()
                    );

                    val packageInfo = packages[id]?: throw IOException("Could not find package info for $packageName");

                    try {
                        val dependencyYamlFile = readPackageInfoFromCache(pkgInfoFromLockFile);
                        val requiredPackages = dependencyYamlFile["dependencies"]?.
                                                                    fieldNames()?.
                                                                    asSequence()?.toList() ?:
                                                                        listOf<String>();

                        val transitiveDependencies =
                            buildDependencyTree(requiredPackages, dependencyYamlFile, lockFile, packages);

                        // If the project contains Flutter, we need to trigger a scan for Gradle
                        // and CocoaPod dependencies for each pub dependency manually,
                        // as the analyser will only scan the projectRoot,
                        // but not the packages in .pub-cache folder
                        if(containsFlutter){
                            // as this package contains flutter, trigger Gradle and CocoaPod scanners manual for it
                            val resultAndroid = scanAndroidPackages(pkgInfoFromLockFile);
                            if(resultAndroid != null){
                                packageReferences += packageInfo.toReference(
                                    dependencies = resultAndroid.project.scopes.find {
                                        it.name == "releaseCompileClasspath"
                                    }?.collectDependencies(-1, false)
                                );
                            }
                            // TODO: Implement support for iOS / Cocoapods
                            /*val resultIos = scanIosPackages(pkgInfoFromLockFile);
                            if(resultIos != null){
                                packageReferences += packageInfo.toReference(
                                    dependencies = resultIos.project.scopes.find {
                                        it.name == "release"
                                    }?.collectDependencies(-1, false)
                                );
                            }*/
                        }

                        packageReferences += packageInfo.toReference(dependencies = transitiveDependencies);
                    } catch (e: IOException) {
                        e.showStackTrace()

                        log.error { "Could not resolve dependencies of '$packageName': ${e.collectMessagesAsString()}" }

                        packageInfo.toReference(
                            errors = listOf(
                                OrtIssue(
                                    source = managerName,
                                    message = e.collectMessagesAsString()
                                )
                            )
                        )
                    }
                }
            }
        }

        return packageReferences.toSortedSet()
    }

    private val scanResultCacheAndroid = mutableSetOf<ProjectAnalyzerResult>();

    private fun scanAndroidPackages(packageInfo: JsonNode) : ProjectAnalyzerResult? {
        val packageName = packageInfo["description"]["name"].textValueOrEmpty();

        // we cannot find packages without a valid name. Stop here
        if(packageName.isEmpty()) return null;

        log.info { "scanAndroidPackages for $packageName" }

        val projectDir = File(reader.findProjectRoot(packageInfo), "android");
        val packageFile = File(projectDir, "build.gradle");

        // check for build.gradle failed, no Gradle scan required
        if(!packageFile.isFile) return null;

        var result = scanResultCacheAndroid.find { projectAnalyzerResult ->
            projectAnalyzerResult.project.id.name == packageName
        };
        // in case there is no result in cache, run the scan and cache the result
        if(result == null){
            result = Gradle("Gradle", projectDir, analyzerConfig, repoConfig).
                resolveDependencies(listOf(packageFile))[packageFile];

            scanResultCacheAndroid.add(result!!);
        }
        /*val resultString = yamlMapper.writeValueAsString(result);

        log.info { "Project $packageName depends on Gradle dependencies. Scan result: " }
        log.info { " $resultString " }*/

        return result;
    }

    private val scanResultCacheIos = mutableSetOf<ProjectAnalyzerResult>();

    private fun scanIosPackages(packageInfo: JsonNode) : ProjectAnalyzerResult? {
        val packageName = packageInfo["description"]["name"].textValueOrEmpty();

        // we cannot find packages without a valid name. Stop here
        if(packageName.isEmpty()) return null;

        // TODO: CocoaPods isn´t working yet. To avoid any bugs,
        // we return null here. Remove this return once cocoapods are supported
        log.warn { "CocoaPods aren´t working yet." }
        return null;
/*
        val projectDir = File(reader.findProjectRoot(packageInfo), "ios");
        // TODO: How to find correct name of podspec file?
        val packageFile = File(projectDir, packageName.toLowerCase() + ".podspec")

        // check for *.podspec failed, no CocoaPods scan required
        if(!packageFile.isFile) return null;
        var result = scanResultCacheIos.find { projectAnalyzerResult ->
            projectAnalyzerResult.project.id.name == packageName
        };
        // in case there is no result in cache, run the scan and cache the result
        if(result == null){
            result = CocoaPods("CocoaPods", projectDir, analyzerConfig, repoConfig).
                resolveDependencies(listOf(packageFile))[packageFile];

            scanResultCacheIos.add(result!!);
        }

        val resultString = yamlMapper.writeValueAsString(result);

        log.info { "Project $packageName depends on Cocoapod dependencies. Scan result: " }
        log.info { " $resultString " }

        return result;*/
    }

    private fun parseProject(definitionFile: File, scopes: SortedSet<Scope>): Project {
        val data = yamlMapper.readTree(definitionFile)
        val description = data["description"]
        val homepageUrl = data["homepage"].textValueOrEmpty()
        val vcs = parseVcsInfo(data)
        val rawName = description["name"]?.textValue() ?: definitionFile.parentFile.name

        return Project(
            id = Identifier(
                type = managerName,
                namespace = rawName.substringBefore("/"),
                name = rawName.substringAfter("/"),
                version = data["version"].textValueOrEmpty()
            ),
            definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
            // PUB does not declare any licenses in the pubspec files, therefore we keep this empty
            declaredLicenses = sortedSetOf(),
            vcs = vcs,
            vcsProcessed = processProjectVcs(definitionFile.parentFile, vcs, homepageUrl),
            homepageUrl = homepageUrl,
            scopes = scopes
        )
    }

    private fun parseInstalledPackages(lockFile: JsonNode):  Map<Identifier, Package> {
        log.info { "parseInstalledPackages..." }

        val packages = mutableMapOf<Identifier, Package>()

        try {
            // flag if project is a flutter project
            var containsFlutter = false;

            listOf("packages"/*, "packages-dev"*/).forEach {
                lockFile[it]?.forEach { pkgInfoFromLockFile ->

                    val version = pkgInfoFromLockFile["version"].textValueOrEmpty();

                    var description = "";
                    var rawName = "";
                    var homepageUrl = "";
                    var vcsFromPackage = VcsInfo.EMPTY;

                    // For now, we ignore SDKs like the Dart SDK and the Flutter SDK in the scanner
                    if(pkgInfoFromLockFile["source"].textValue() != "sdk") {
                        val pkgInfoYamlFile = readPackageInfoFromCache(pkgInfoFromLockFile);
                        vcsFromPackage = parseVcsInfo(pkgInfoYamlFile);
                        description = parseDescriptionInfo(pkgInfoYamlFile);
                        rawName = pkgInfoFromLockFile["description"]["name"].textValueOrEmpty();
                        homepageUrl = pkgInfoFromLockFile["description"]["url"].textValueOrEmpty();
                    } else {
                        if(pkgInfoFromLockFile["description"].textValueOrEmpty() == "flutter"){
                            // set flutter flag, which triggers another scan for iOS and Android native dependencies
                            containsFlutter = true;
                            // set hardcoded package details
                            rawName = "flutter";
                            homepageUrl = "https://github.com/flutter/flutter";
                            description = "Flutter SDK"
                        } else if(pkgInfoFromLockFile["description"].textValueOrEmpty() == "flutter_test"){
                            // set hardcoded package details
                            rawName = "flutter_test";
                            homepageUrl = "https://github.com/flutter/flutter/tree/master/packages/flutter_test";
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
                    );
                    packages[id] = Package(
                        id,
                        // PUB does not declare any licenses in the pubspec files, therefore we keep this empty
                        declaredLicenses = sortedSetOf(),
                        description = description,
                        homepageUrl = homepageUrl,
                        // PUB does not create binary artifacts, therefore use any empty artifact
                        binaryArtifact = RemoteArtifact.EMPTY,
                        // PUB does not create source artifacts, therefore use any empty artifact
                        sourceArtifact = RemoteArtifact.EMPTY,
                        vcs = vcsFromPackage,
                        vcsProcessed = processPackageVcs(vcsFromPackage, homepageUrl)
                    )
                }
            }

            // If the project contains Flutter, we need to trigger a scan for Gradle
            // and CocoaPod dependencies for each pub dependency manually,
            // as the analyser will only scan the projectRoot,
            // but not the packages in .pub-cache folder
            if(containsFlutter){
                lockFile["packages"]?.forEach { pkgInfoFromLockFile ->
                    // as this package contains flutter, trigger Gradle scanner manual for it
                    val resultAndroid = scanAndroidPackages(pkgInfoFromLockFile);
                    if (resultAndroid != null) {
                        resultAndroid.collectPackages("releaseCompileClasspath").forEach{ item ->
                            //log.info { "Add Android package '$item.pkg.id.name' to packages" }
                            packages[item.pkg.id] = item.pkg;
                        }
                    }
                    // as this package contains flutter, trigger CocoaPod scanner manual for it
                    val resultIos = scanIosPackages(pkgInfoFromLockFile);
                    if (resultIos != null) {
                        resultIos.packages.forEach{ item ->
                            //log.info { "Add iOS package '$item.pkg.id.name' to packages" }
                            packages[item.pkg.id] = item.pkg;
                        }
                    }
                }
            }

        } catch (e: Exception) {
            e.printStackTrace();
            log.error { "Could not parseInstalledPackages: ${e.collectMessagesAsString()}" }
        }
        return packages
    }

    private fun readPackageInfoFromCache(packageInfo: JsonNode): JsonNode {
        //val packageName = packageInfo["description"]["name"].textValue()
        //log.info { "readPackageInfoFromCache - packageName: $packageName" }
        val definitionFile = reader.findFile(packageInfo, "pubspec.yaml");
        return yamlMapper.readTree(definitionFile);
    }

    private fun parseDescriptionInfo(packageInfo: JsonNode): String {
        return packageInfo["description"].textValueOrEmpty();
    }

    private fun parseVcsInfo(packageInfo: JsonNode): VcsInfo {
        return packageInfo["homepage"]?.let {
            // Currently, we only support Github repositories
            if(it.textValueOrEmpty().contains("github")){
                VcsInfo("git", it.textValueOrEmpty()+".git", "")
            } else {
                VcsInfo.EMPTY
            }
        } ?: VcsInfo.EMPTY
    }

    override fun command(workingDir: File?) = "pub"

    private fun commandFlutter() = "flutter packages"

    /*override fun run(workingDir: File?, vararg args: String) =
        ProcessCapture(workingDir, *command(workingDir).split(" ").toTypedArray(), *args).requireSuccess()*/


    override  fun run(workingDir: File?, vararg args: String): ProcessCapture {
        var result = ProcessCapture(workingDir, *command(workingDir).split(" ").toTypedArray(), *args)
        if (result.isError) {
            if (result.errorMessage.contains("Flutter users should run `flutter packages get` instead of `pub get`.")) {
                result = ProcessCapture(workingDir, *commandFlutter().split(" ").toTypedArray(), *args).requireSuccess()
            } else {
                throw IOException(result.errorMessage)
            }
        }
        return result;
    }

    private fun installDependencies(workingDir: File) {
        require(analyzerConfig.allowDynamicVersions || File(workingDir, PUB_LOCK_FILE).isFile) {
            "No lock file found in $workingDir, dependency versions are unstable."
        }

        // The "get" command creates a "pubspec.lock" file (if not yet present) except for projects without any
        // dependencies, see https://dart.dev/tools/pub/cmd/pub-get
        run(workingDir, "get")
    }
}

