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
import com.fasterxml.jackson.databind.node.ObjectNode

import com.here.ort.analyzer.AbstractPackageManagerFactory
import com.here.ort.analyzer.PackageManager
import com.here.ort.analyzer.identifier
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.model.HashAlgorithm
import com.here.ort.model.Identifier
import com.here.ort.model.OrtIssue
import com.here.ort.model.Package
import com.here.ort.model.PackageReference
import com.here.ort.model.Project
import com.here.ort.model.ProjectAnalyzerResult
import com.here.ort.model.RemoteArtifact
import com.here.ort.model.Scope
import com.here.ort.model.VcsInfo
import com.here.ort.model.config.AnalyzerConfiguration
import com.here.ort.model.config.RepositoryConfiguration
import com.here.ort.model.yamlMapper
import com.here.ort.utils.*

import com.vdurmont.semver4j.Requirement
import org.eclipse.aether.repository.WorkspaceReader
import org.eclipse.aether.repository.WorkspaceRepository

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

    override fun command(workingDir: File?) = "pub"

    override fun run(workingDir: File?, vararg args: String) =
        ProcessCapture(workingDir, *command(workingDir).split(" ").toTypedArray(), *args).requireSuccess()

    override fun getVersionRequirement(): Requirement = Requirement.buildIvy("[2.2,)")

    override fun beforeResolution(definitionFiles: List<File>) {

    }

    /**
     * A workspace reader that is backed by the local Gradle artifact cache.
     */
    private class PubCacheReader {
        private val workspaceRepository = WorkspaceRepository("pubCache")
        private val pubCacheRoot = getUserHomeDirectory().resolve(".pub-cache/")

        fun findFile(packageInfo: JsonNode, fileName: String): File? {
            var path : String;

            val packageVersion = packageInfo["version"].textValueOrEmpty();
            val type = packageInfo["source"].textValueOrEmpty();
            val description = packageInfo["description"];
            val packageName = description["name"].textValueOrEmpty();
            val url = description["url"].textValueOrEmpty();
            val resolvedRef = packageInfo["resolved-ref"].textValueOrEmpty();

            if(type == "hosted" && url.isNotEmpty()){
                // packages with source set to "hosted" and "url" key in description set to "https://pub.dartlang.org", path should be resolved to "hosted/pub.dartlang.org/packageName-packageVersion"
                path = "hosted/" + url.replace("https://","") + "/" + packageName + "-" + packageVersion;
            } else if(type == "git" && resolvedRef.isNotEmpty()){
                // packages with source set to "git" and a "resolved-ref" key in description set to a gitHash, should be resolved to "git/packageName-gitHash"
                path = "git/" + packageName + "-" + resolvedRef;
            } else {
                // not supported type
                path = "";
            }

            val artifactRootDir = File(
                pubCacheRoot,
                path
            )

            return artifactRootDir.walkTopDown().find {
                it.isFile && it.name == fileName
            }
        }
    }

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
                parseScope("dependencies", manifest, lockFile, packages) //,
                //parseScope("dev_dependencies", manifest, lockFile, packages)
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
        scopeName: String, manifest: JsonNode, lockFile: JsonNode, packages: Map<String, Package>
    ): Scope {
        val packageName = manifest["name"].textValue();
        log.info { "parseScope for $packageName" }
        val requiredPackages = manifest[scopeName]?.fieldNames() ?: listOf<String>().iterator()
        val dependencies = buildDependencyTree(requiredPackages, manifest, lockFile, packages)
        return Scope(scopeName, dependencies)
    }

    private fun buildDependencyTree(
        dependencies: Iterator<String>,
        manifest: JsonNode,
        lockFile: JsonNode,
        packages: Map<String, Package>
    ): SortedSet<PackageReference> {
        val packageReferences = mutableSetOf<PackageReference>();

        dependencies.forEach { packageName ->
            val pkgInfoFromLockFile = lockFile["packages"].get(packageName);
            val packageInfo = packages[packageName]
                ?: throw IOException("Could not find package info for $packageName")

            try {
                val dependencyYamlFile = readPackageInfoFromCache(pkgInfoFromLockFile);
                val requiredPackages = manifest["dependencies"]?.fieldNames() ?: listOf<String>().iterator();
                val transitiveDependencies = buildDependencyTree(requiredPackages, dependencyYamlFile, lockFile, packages);
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

        return packageReferences.toSortedSet()
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
            declaredLicenses = parseDeclaredLicenses(data),
            vcs = vcs,
            vcsProcessed = processProjectVcs(definitionFile.parentFile, vcs, homepageUrl),
            homepageUrl = homepageUrl,
            scopes = scopes
        )
    }

    private fun parseInstalledPackages(json: JsonNode):  Map<String, Package> {
        log.info { "parseInstalledPackages..." }

        val packages = mutableMapOf<String, Package>()

        try {

            listOf("packages"/*, "packages-dev"*/).forEach {
                json[it]?.forEach { pkgInfoLockFile ->
                    val rawName = pkgInfoLockFile["description"]["name"].textValue()
                    val version = pkgInfoLockFile["version"].textValueOrEmpty()
                    val homepageUrl = pkgInfoLockFile["description"]["url"].textValueOrEmpty()
                    val pkgInfoYamlFile = readPackageInfoFromCache(pkgInfoLockFile);
                    val vcsFromPackage = parseVcsInfo(pkgInfoYamlFile)

                    // Just warn if the version is missing as Composer itself declares it as optional, see
                    // https://getcomposer.org/doc/04-schema.md#version.
                    if (version.isEmpty()) {
                        log.warn { "No version information found for package $rawName." }
                    }

                    packages[rawName] = Package(
                        id = Identifier(
                            type = managerName,
                            namespace = rawName.substringBefore("/"),
                            name = rawName.substringAfter("/"),
                            version = version
                        ),
                        declaredLicenses = parseDeclaredLicenses(pkgInfoYamlFile),
                        description = parseDescriptionInfo(pkgInfoYamlFile),
                        homepageUrl = homepageUrl,
                        binaryArtifact = RemoteArtifact.EMPTY,
                        sourceArtifact = parseArtifact(pkgInfoYamlFile),
                        vcs = vcsFromPackage,
                        vcsProcessed = processPackageVcs(vcsFromPackage, homepageUrl)
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace();
        }
        return packages
    }

    private fun readPackageInfoFromCache(packageInfo: JsonNode): JsonNode {
        val packageName = packageInfo["description"]["name"].textValue()
        log.info { "readPackageInfoFromCache - packageName: $packageName" }

        val reader = PubCacheReader();
        val definitionFile = reader.findFile(packageInfo, "pubspec.yaml");

        return yamlMapper.readTree(definitionFile);
    }

    private fun readPackageLockFileFromCache(packageInfo: JsonNode): JsonNode {
        val packageName = packageInfo["description"]["name"].textValue()
        log.info { "readPackageLockFileFromCache - packageName: $packageName" }

        val reader = PubCacheReader();
        val definitionFile = reader.findFile(packageInfo, "pubspec.lock");

        return yamlMapper.readTree(definitionFile);
    }

    private fun parseDeclaredLicenses(packageInfo: JsonNode) : SortedSet<String> {
        // TODO: Check if we can support this for PUB packages by reading it from LICENSE file
        //val reader = PubCacheReader();
        //reader.findFile(packageInfo, "LICENSE");
        val packageName = packageInfo["name"].textValueOrEmpty();
        log.info { "parseDeclaredLicenses - packageName: $packageName" }

        return sortedSetOf();
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

    private fun parseArtifact(packageInfo: JsonNode): RemoteArtifact {
        // TODO: Check if we can support this for PUB packages
        val packageName = packageInfo["name"].textValueOrEmpty();
        log.info { "parseArtifact - packageName: $packageName" }

        return RemoteArtifact.EMPTY
    }

    private fun getRuntimeDependencies(packageName: String, lockFile: JsonNode): Iterator<String> {
        log.info {"getRuntimeDependencies - packageName: $packageName" }

        // TODO: CHeck if this makes sense, as pubspec.yaml only lists dependencies, and a lockFile is only generated for project root
        val requiredPackages = lockFile["packages"]
        if (requiredPackages != null && requiredPackages.isObject) {
            return (requiredPackages as ObjectNode).fieldNames()
        }

        return emptyList<String>().iterator()
    }

    private fun installDependencies(workingDir: File) {
        // TODO: Should we require a lock file?
        /*require(analyzerConfig.allowDynamicVersions || File(workingDir, PUB_LOCK_FILE).isFile) {
            "No lock file found in $workingDir, dependency versions are unstable."
        }*/

        // The "get" command creates a "pubspec.lock" file (if not yet present) except for projects without any
        // dependencies, see https://dart.dev/tools/pub/cmd/pub-get
        run(workingDir, "get")
    }
}

