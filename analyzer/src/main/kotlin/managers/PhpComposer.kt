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

import com.here.ort.analyzer.AbstractPackageManagerFactory
import com.here.ort.analyzer.PackageManager
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.model.Error
import com.here.ort.model.HashAlgorithm
import com.here.ort.model.Identifier
import com.here.ort.model.Package
import com.here.ort.model.PackageReference
import com.here.ort.model.Project
import com.here.ort.model.ProjectAnalyzerResult
import com.here.ort.model.RemoteArtifact
import com.here.ort.model.Scope
import com.here.ort.model.VcsInfo
import com.here.ort.model.config.AnalyzerConfiguration
import com.here.ort.model.config.RepositoryConfiguration
import com.here.ort.model.jsonMapper
import com.here.ort.utils.CommandLineTool
import com.here.ort.utils.OS
import com.here.ort.utils.ProcessCapture
import com.here.ort.utils.collectMessagesAsString
import com.here.ort.utils.log
import com.here.ort.utils.showStackTrace
import com.here.ort.utils.stashDirectories
import com.here.ort.utils.textValueOrEmpty

import com.vdurmont.semver4j.Requirement

import java.io.File
import java.io.IOException
import java.util.SortedSet

const val COMPOSER_PHAR_BINARY = "composer.phar"
const val COMPOSER_LOCK_FILE = "composer.lock"

/**
 * The Composer package manager for PHP, see https://getcomposer.org/.
 */
class PhpComposer(analyzerConfig: AnalyzerConfiguration, repoConfig: RepositoryConfiguration) :
        PackageManager(analyzerConfig, repoConfig), CommandLineTool {
    class Factory : AbstractPackageManagerFactory<PhpComposer>() {
        override val globsForDefinitionFiles = listOf("composer.json")

        override fun create(analyzerConfig: AnalyzerConfiguration, repoConfig: RepositoryConfiguration) =
                PhpComposer(analyzerConfig, repoConfig)
    }

    override fun command(workingDir: File?) =
            if (workingDir?.resolve(COMPOSER_PHAR_BINARY)?.isFile == true) {
                "php $COMPOSER_PHAR_BINARY"
            } else {
                if (OS.isWindows) {
                    "composer.bat"
                } else {
                    "composer"
                }
            }

    override fun run(workingDir: File?, vararg args: String) =
            ProcessCapture(workingDir, *command(workingDir).split(" ").toTypedArray(), *args).requireSuccess()

    override fun getVersionRequirement(): Requirement = Requirement.buildIvy("[1.5,)")

    override fun prepareResolution(definitionFiles: List<File>) {
        // If all of the directories we are analyzing contain a composer.phar, no global installation of Composer is
        // required and hence we skip the version check.
        if (definitionFiles.all { File(it.parentFile, COMPOSER_PHAR_BINARY).isFile }) {
            return
        }

        // We do not actually depend on any features specific to a version of Composer, but we still want to stick to
        // fixed versions to be sure to get consistent results. The version string can be something like:
        // Composer version 1.5.1 2017-08-09 16:07:22
        // Composer version @package_branch_alias_version@ (1.0.0-beta2) 2016-03-27 16:00:34
        checkVersion(
                "--no-ansi --version",
                ignoreActualVersion = analyzerConfig.ignoreToolVersions,
                transform = { it.split(" ").dropLast(2).last().removeSurrounding("(", ")") }
        )
    }

    override fun resolveDependencies(definitionFile: File): ProjectAnalyzerResult? {
        val workingDir = definitionFile.parentFile

        stashDirectories(File(workingDir, "vendor")).use {
            val manifest = jsonMapper.readTree(definitionFile)
            val hasDependencies = manifest.fields().asSequence().any { (key, value) ->
                key.startsWith("require") && value.count() > 0
            }

            val (packages, scopes) = if (hasDependencies) {
                installDependencies(workingDir)

                log.info { "Reading $COMPOSER_LOCK_FILE file in ${workingDir.absolutePath}..." }
                val lockFile = jsonMapper.readTree(File(workingDir, COMPOSER_LOCK_FILE))
                val packages = parseInstalledPackages(lockFile)

                // Let's also determine the "virtual" (replaced and provided) packages. These can be declared as 
                // required, but are not listed in composer.lock as installed.  
                // If we didn't handle them specifically, we would report them as missing when trying to load the 
                // dependency information for them. We can't simply put these "virtual" packages in the normal package 
                // map as this would cause us to report a package which is not actually installed with the contents of 
                // the "replacing" package.
                val virtualPackages = parseVirtualPackageNames(packages, manifest, lockFile)

                val scopes = sortedSetOf(
                        parseScope("require", manifest, lockFile, packages, virtualPackages),
                        parseScope("require-dev", manifest, lockFile, packages, virtualPackages)
                )

                Pair(packages, scopes)
            } else {
                Pair(emptyMap(), sortedSetOf())
            }

            log.info { "Reading ${definitionFile.name} file in ${workingDir.absolutePath}..." }

            val project = parseProject(definitionFile, scopes)

            return ProjectAnalyzerResult(project, packages.values.map { it.toCuratedPackage() }.toSortedSet())
        }
    }

    private fun parseScope(scopeName: String, manifest: JsonNode, lockFile: JsonNode, packages: Map<String, Package>,
                           virtualPackages: Set<String>): Scope {
        val requiredPackages = manifest[scopeName]?.fieldNames() ?: listOf<String>().iterator()
        val dependencies = buildDependencyTree(requiredPackages, lockFile, packages, virtualPackages)
        return Scope(scopeName, dependencies)
    }

    private fun buildDependencyTree(dependencies: Iterator<String>, lockFile: JsonNode, packages: Map<String, Package>,
                                    virtualPackages: Set<String>): SortedSet<PackageReference> {
        val packageReferences = mutableSetOf<PackageReference>()

        dependencies.forEach { packageName ->
            // Composer allows declaring the required PHP version including any extensions, such as "ext-curl". Language
            // implementations are not included in the results from other analyzer modules, so we want to skip them here
            // as well. The special package "composer-plugin-api" is also excluded since it's only used to specify the
            // supported composer plugin versions.
            // Virtual packages are also ignored, since otherwise we would mark them as missing.
            if (packageName != "php" && !packageName.startsWith("ext-") && packageName != "composer-plugin-api"
                    && packageName !in virtualPackages) {
                val packageInfo = packages[packageName]
                        ?: throw IOException("Could not find package info for $packageName")
                try {
                    val transitiveDependencies = getRuntimeDependencies(packageName, lockFile)
                    packageReferences += packageInfo.toReference(
                            buildDependencyTree(transitiveDependencies, lockFile, packages, virtualPackages))
                } catch (e: Exception) {
                    e.showStackTrace()

                    log.error { "Could not resolve dependencies of '$packageName': ${e.collectMessagesAsString()}" }

                    packageInfo.toReference(errors = listOf(Error(source = toString(),
                            message = e.collectMessagesAsString())))
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
                definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
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

    /**
     * Get all names of "virtual" (replaced or provided) packages in the package or lock file.
     *
     * While Composer also takes the versions of the virtual packages into account, we simply use priorities here. Since
     * Composer can't handle the same package in multiple version, we can assume that as soon as a package is found in
     * composer.lock we can ignore any virtual package with the same name. Since the code later depends on the virtual
     * packages not accidentally containing a package which is actually installed, we make sure to only return virtual
     * packages for which are not in the installed package map.
     */
    private fun parseVirtualPackageNames(packages: Map<String, Package>, manifest: JsonNode, lockFile: JsonNode)
            : Set<String> {
        val replacedNames = mutableSetOf<String>()

        // The contents of the manifest file, which can also define replacements, is not included in the lock file, so 
        // we parse the manifest file as well. 
        replacedNames += parseVirtualNames(manifest)

        listOf("packages", "packages-dev").forEach { type ->
            lockFile[type]?.flatMap { pkgInfo ->
                parseVirtualNames(pkgInfo)
            }?.let {
                replacedNames += it
            }
        }
        return replacedNames - packages.keys
    }

    private fun parseVirtualNames(packageInfo: JsonNode) =
            listOf("replace", "provide").flatMap {
                packageInfo[it]?.fieldNames()?.asSequence()?.toSet() ?: emptySet()
            }.toSet()

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
        require(analyzerConfig.allowDynamicVersions || File(workingDir, COMPOSER_LOCK_FILE).isFile) {
            "No lock file found in $workingDir, dependency versions are unstable."
        }

        // The "install" command creates a "composer.lock" file (if not yet present) except for projects without any
        // dependencies, see https://getcomposer.org/doc/01-basic-usage.md#installing-without-composer-lock.
        run(workingDir, "install")
    }
}
