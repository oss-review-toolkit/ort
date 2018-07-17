/*
 * Copyright (C) 2017-2018 HERE Europe B.V.
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
import com.here.ort.analyzer.AnalyzerConfiguration

import com.here.ort.analyzer.Main
import com.here.ort.analyzer.PackageManager
import com.here.ort.analyzer.PackageManagerFactory
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.model.HashAlgorithm
import com.here.ort.model.Identifier
import com.here.ort.model.Package
import com.here.ort.model.PackageReference
import com.here.ort.model.Project
import com.here.ort.model.ProjectAnalyzerResult
import com.here.ort.model.RemoteArtifact
import com.here.ort.model.Scope
import com.here.ort.model.VcsInfo
import com.here.ort.model.jsonMapper
import com.here.ort.utils.OS
import com.here.ort.utils.ProcessCapture
import com.here.ort.utils.textValueOrEmpty
import com.here.ort.utils.checkCommandVersion
import com.here.ort.utils.collectMessages
import com.here.ort.utils.log
import com.here.ort.utils.safeDeleteRecursively
import com.here.ort.utils.showStackTrace

import com.vdurmont.semver4j.Requirement

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.SortedSet

class PhpComposer(config: AnalyzerConfiguration) : PackageManager(config) {
    companion object : PackageManagerFactory<PhpComposer>(
            "https://getcomposer.org/",
            "PHP",
            listOf("composer.json")
    ) {
        override fun create(config: AnalyzerConfiguration) = PhpComposer(config)

        private const val PHAR_BINARY = "composer.phar"
        private const val LOCK_FILE = "composer.lock"
    }

    override fun command(workingDir: File) =
            if (File(workingDir, PHAR_BINARY).isFile) {
                "php $PHAR_BINARY"
            } else {
                if (OS.isWindows) {
                    "composer.bat"
                } else {
                    "composer"
                }
            }

    override fun toString() = PhpComposer.toString()

    override fun prepareResolution(definitionFiles: List<File>): List<File> {
        // If all of the directories we are analyzing contain a composer.phar, no global installation of Composer is
        // required and hence we skip the version check.
        if (definitionFiles.all { File(it.parentFile, PHAR_BINARY).isFile }) {
            return definitionFiles
        }

        val workingDir = definitionFiles.first().parentFile

        // We do not actually depend on any features specific to a version of Composer, but we still want to stick to
        // fixed versions to be sure to get consistent results. The version string can be something like:
        // Composer version 1.5.1 2017-08-09 16:07:22
        // Composer version @package_branch_alias_version@ (1.0.0-beta2) 2016-03-27 16:00:34
        checkCommandVersion(
                command(workingDir),
                Requirement.buildIvy("[1.5,)"),
                "--no-ansi --version",
                ignoreActualVersion = config.ignoreToolVersions,
                transform = { it.split(" ").dropLast(2).last().removeSurrounding("(", ")") }
        )

        return definitionFiles
    }

    override fun resolveDependencies(definitionFile: File): ProjectAnalyzerResult? {
        val workingDir = definitionFile.parentFile
        val vendorDir = File(workingDir, "vendor")
        var tempVendorDir: File? = null

        try {
            if (vendorDir.isDirectory) {
                val tempDir = createTempDir(Main.TOOL_NAME, ".tmp", workingDir)
                tempVendorDir = File(tempDir, "composer_vendor")
                log.warn { "'$vendorDir' already exists, temporarily moving it to '$tempVendorDir'." }
                Files.move(vendorDir.toPath(), tempVendorDir.toPath(), StandardCopyOption.ATOMIC_MOVE)
            }

            val manifest = jsonMapper.readTree(definitionFile)
            val hasDependencies = manifest.fields().asSequence().any { (key, value) ->
                key.startsWith("require") && value.count() > 0
            }

            val (packages, scopes) = if (hasDependencies) {
                installDependencies(workingDir)

                log.info { "Reading $LOCK_FILE file in ${workingDir.absolutePath}..." }
                val lockFile = jsonMapper.readTree(File(workingDir, LOCK_FILE))
                val packages = parseInstalledPackages(lockFile)
                val scopes = sortedSetOf(
                        parseScope("require", true, manifest, lockFile, packages),
                        parseScope("require-dev", false, manifest, lockFile, packages)
                )

                Pair(packages, scopes)
            } else {
                Pair(emptyMap(), sortedSetOf())
            }

            log.info { "Reading ${definitionFile.name} file in ${workingDir.absolutePath}..." }

            val project = parseProject(definitionFile, scopes)

            return ProjectAnalyzerResult(config, project, packages.values.map { it.toCuratedPackage() }.toSortedSet())
        } finally {
            // Delete vendor folder to not pollute the scan.
            log.info { "Deleting temporary '$vendorDir'..." }
            vendorDir.safeDeleteRecursively()

            // Restore any previously existing "vendor" directory.
            if (tempVendorDir != null) {
                log.info { "Restoring original '$vendorDir' directory from '$tempVendorDir'." }
                Files.move(tempVendorDir.toPath(), vendorDir.toPath(), StandardCopyOption.ATOMIC_MOVE)
                if (!tempVendorDir.parentFile.delete()) {
                    throw IOException("Unable to delete the '${tempVendorDir.parent}' directory.")
                }
            }
        }
    }

    private fun parseScope(scopeName: String, distributed: Boolean, manifest: JsonNode, lockFile: JsonNode,
                           packages: Map<String, Package>): Scope {
        val requiredPackages = manifest[scopeName]?.fieldNames() ?: listOf<String>().iterator()
        val dependencies = buildDependencyTree(requiredPackages, lockFile, packages)
        return Scope(scopeName, distributed, dependencies)
    }

    private fun buildDependencyTree(dependencies: Iterator<String>, lockFile: JsonNode,
                                    packages: Map<String, Package>): SortedSet<PackageReference> {
        val packageReferences = mutableSetOf<PackageReference>()

        dependencies.forEach { packageName ->
            // Composer allows declaring the required PHP version including any extensions, such as "ext-curl". Language
            // implementations are not included in the results from other analyzer modules, so we want to skip them here
            // as well.
            if (packageName != "php" && !packageName.startsWith("ext-")) {
                val packageInfo = packages[packageName]
                        ?: throw IOException("Could not find package info for $packageName")
                try {
                    val transitiveDependencies = getRuntimeDependencies(packageName, lockFile)
                    packageReferences += packageInfo.toReference(
                            buildDependencyTree(transitiveDependencies, lockFile, packages))
                } catch (e: Exception) {
                    e.showStackTrace()
                    PackageReference(packageInfo.id, sortedSetOf<PackageReference>(), e.collectMessages())
                }
            }
        }
        return packageReferences.toSortedSet()
    }

    private fun parseProject(definitionFile: File, scopes: SortedSet<Scope>): Project {
        val json = jsonMapper.readTree(definitionFile)
        val homepageUrl = json["homepage"].textValueOrEmpty()
        val vcs = parseVcsInfo(json)
        val rawName = json["name"]?.textValue() ?: definitionFile.parentFile.name

        return Project(
                id = Identifier(
                        provider = toString(),
                        namespace = rawName.substringBefore("/"),
                        name = rawName.substringAfter("/"),
                        version = json["version"].textValueOrEmpty()
                ),
                definitionFilePath = VersionControlSystem.getPathToRoot(definitionFile) ?: "",
                declaredLicenses = parseDeclaredLicenses(json),
                vcs = vcs,
                vcsProcessed = processProjectVcs(definitionFile.parentFile, vcs, homepageUrl),
                homepageUrl = homepageUrl,
                scopes = scopes
        )
    }

    private fun parseInstalledPackages(json: JsonNode): Map<String, Package> {
        val packages = mutableMapOf<String, Package>()

        listOf("packages", "packages-dev").forEach {
            json[it]?.forEach { pkgInfo ->
                val rawName = pkgInfo["name"].textValue()
                val version = pkgInfo["version"].textValueOrEmpty()
                val homepageUrl = pkgInfo["homepage"].textValueOrEmpty()
                val vcsFromPackage = parseVcsInfo(pkgInfo)

                // Just warn if the version is missing as Composer itself declares it as optional, see
                // https://getcomposer.org/doc/04-schema.md#version.
                if (version.isEmpty()) {
                    log.warn { "No version information found for package $rawName." }
                }

                packages[rawName] = Package(
                        id = Identifier(
                                provider = toString(),
                                namespace = rawName.substringBefore("/"),
                                name = rawName.substringAfter("/"),
                                version = version
                        ),
                        declaredLicenses = parseDeclaredLicenses(pkgInfo),
                        description = pkgInfo["description"].textValueOrEmpty(),
                        homepageUrl = homepageUrl,
                        binaryArtifact = RemoteArtifact.EMPTY,
                        sourceArtifact = parseArtifact(pkgInfo),
                        vcs = vcsFromPackage,
                        vcsProcessed = processPackageVcs(vcsFromPackage, homepageUrl)
                )
            }
        }
        return packages
    }

    private fun parseDeclaredLicenses(packageInfo: JsonNode) =
            packageInfo["license"]?.mapNotNull { it?.textValue() }?.toSortedSet() ?: sortedSetOf<String>()

    private fun parseVcsInfo(packageInfo: JsonNode): VcsInfo {
        return packageInfo["source"]?.let {
            VcsInfo(it["type"].textValueOrEmpty(), it["url"].textValueOrEmpty(), it["reference"].textValueOrEmpty())
        } ?: VcsInfo.EMPTY
    }

    private fun parseArtifact(packageInfo: JsonNode): RemoteArtifact {
        return packageInfo["dist"]?.let {
            val sha = it["shasum"].textValueOrEmpty()
            // "shasum" is SHA-1: https://github.com/composer/composer/blob/ \
            // 285ff274accb24f45ffb070c2b9cfc0722c31af4/src/Composer/Repository/ArtifactRepository.php#L149
            val algo = if (sha.isEmpty()) HashAlgorithm.UNKNOWN else HashAlgorithm.SHA1
            RemoteArtifact(it["url"].textValueOrEmpty(), sha, algo)
        } ?: RemoteArtifact.EMPTY
    }

    private fun getRuntimeDependencies(packageName: String, lockFile: JsonNode): Iterator<String> {
        listOf("packages", "packages-dev").forEach {
            lockFile[it]?.forEach { packageInfo ->
                if (packageInfo["name"].textValueOrEmpty() == packageName) {
                    val requiredPackages = packageInfo["require"]
                    if (requiredPackages != null && requiredPackages.isObject) {
                        return (requiredPackages as ObjectNode).fieldNames()
                    }
                }
            }
        }

        return emptyList<String>().iterator()
    }

    private fun installDependencies(workingDir: File) {
        require(config.allowDynamicVersions || File(workingDir, LOCK_FILE).isFile) {
            "No lock file found in $workingDir, dependency versions are unstable."
        }

        // The "install" command creates a "composer.lock" file (if not yet present) except for projects without any
        // dependencies, see https://getcomposer.org/doc/01-basic-usage.md#installing-without-composer-lock.
        ProcessCapture(workingDir, *command(workingDir).split(" ").toTypedArray(), "install")
                .requireSuccess()
    }
}
