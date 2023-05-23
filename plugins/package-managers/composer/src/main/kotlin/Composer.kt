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

package org.ossreviewtoolkit.plugins.packagemanagers.composer

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode

import java.io.File
import java.io.IOException

import org.apache.logging.log4j.kotlin.Logging

import org.ossreviewtoolkit.analyzer.AbstractPackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.model.jsonMapper
import org.ossreviewtoolkit.model.orEmpty
import org.ossreviewtoolkit.model.readTree
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.common.fieldNamesOrEmpty
import org.ossreviewtoolkit.utils.common.splitOnWhitespace
import org.ossreviewtoolkit.utils.common.textValueOrEmpty
import org.ossreviewtoolkit.utils.ort.showStackTrace

import org.semver4j.RangesList
import org.semver4j.RangesListFactory
import org.semver4j.Semver

const val COMPOSER_PHAR_BINARY = "composer.phar"
const val COMPOSER_LOCK_FILE = "composer.lock"

/**
 * The [Composer](https://getcomposer.org/) package manager for PHP.
 */
@Suppress("TooManyFunctions")
class Composer(
    name: String,
    analysisRoot: File,
    analyzerConfig: AnalyzerConfiguration,
    repoConfig: RepositoryConfiguration
) : PackageManager(name, analysisRoot, analyzerConfig, repoConfig), CommandLineTool {
    private companion object : Logging

    class Factory : AbstractPackageManagerFactory<Composer>("Composer") {
        override val globsForDefinitionFiles = listOf("composer.json")

        override fun create(
            analysisRoot: File,
            analyzerConfig: AnalyzerConfiguration,
            repoConfig: RepositoryConfiguration
        ) = Composer(type, analysisRoot, analyzerConfig, repoConfig)
    }

    override fun command(workingDir: File?) =
        if (workingDir?.resolve(COMPOSER_PHAR_BINARY)?.isFile == true) {
            "php $COMPOSER_PHAR_BINARY"
        } else {
            if (Os.isWindows) {
                "composer.bat"
            } else {
                "composer"
            }
        }

    override fun getVersionArguments() = "--no-ansi --version"

    override fun transformVersion(output: String) =
        // The version string can be something like:
        // Composer version 1.5.1 2017-08-09 16:07:22
        // Composer version @package_branch_alias_version@ (1.0.0-beta2) 2016-03-27 16:00:34
        output.splitOnWhitespace().dropLast(2).last().removeSurrounding("(", ")")

    override fun getVersionRequirement(): RangesList = RangesListFactory.create(">=1.5")

    override fun beforeResolution(definitionFiles: List<File>) {
        // If all directories we are analyzing contain a composer.phar, no global installation of Composer is required
        // and hence we skip the version check.
        if (definitionFiles.all { File(it.parentFile, COMPOSER_PHAR_BINARY).isFile }) return

        // We do not actually depend on any features specific to a version of Composer, but we still want to stick to
        // fixed versions to be sure to get consistent results.
        checkVersion()
    }

    override fun resolveDependencies(definitionFile: File, labels: Map<String, String>): List<ProjectAnalyzerResult> {
        val workingDir = definitionFile.parentFile

        val manifest = definitionFile.readTree()
        val hasDependencies = manifest.fields().asSequence().any { (key, value) ->
            key.startsWith("require") && value.count() > 0
        }

        if (!hasDependencies) {
            val project = parseProject(definitionFile, scopes = emptySet())
            val result = ProjectAnalyzerResult(project, packages = emptySet())

            return listOf(result)
        }

        val lockFile = ensureLockFile(workingDir)

        logger.info { "Parsing lock file at '$lockFile'..." }

        val json = jsonMapper.readTree(lockFile)
        val packages = parseInstalledPackages(json)

        // Let's also determine the "virtual" (replaced and provided) packages. These can be declared as
        // required, but are not listed in composer.lock as installed.
        // If we didn't handle them specifically, we would report them as missing when trying to load the
        // dependency information for them. We can't simply put these "virtual" packages in the normal package
        // map as this would cause us to report a package which is not actually installed with the contents of
        // the "replacing" package.
        val virtualPackages = parseVirtualPackageNames(packages, manifest, json)

        val scopes = setOf(
            parseScope("require", manifest, json, packages, virtualPackages),
            parseScope("require-dev", manifest, json, packages, virtualPackages)
        )

        val project = parseProject(definitionFile, scopes)
        val result = ProjectAnalyzerResult(project, packages.values.toSet())

        return listOf(result)
    }

    private fun parseScope(
        scopeName: String, manifest: JsonNode, lockFile: JsonNode, packages: Map<String, Package>,
        virtualPackages: Set<String>
    ): Scope {
        val requiredPackages = manifest[scopeName].fieldNamesOrEmpty().asSequence()
        val dependencies = buildDependencyTree(requiredPackages, lockFile, packages, virtualPackages)
        return Scope(scopeName, dependencies)
    }

    private fun buildDependencyTree(
        dependencies: Sequence<String>,
        lockFile: JsonNode,
        packages: Map<String, Package>,
        virtualPackages: Set<String>,
        dependencyBranch: List<String> = emptyList()
    ): Set<PackageReference> {
        val packageReferences = mutableSetOf<PackageReference>()

        dependencies.filterNot { packageName ->
            packageName.isPlatformDependency() || packageName in virtualPackages // Virtual packages have no metadata.
        }.forEach { packageName ->
            val packageInfo = packages[packageName]
                ?: throw IOException("Could not find package info for $packageName")

            if (packageName in dependencyBranch) {
                logger.debug {
                    "Not adding circular dependency '$packageName' to the tree, it is already on this branch of the " +
                            "dependency tree: ${dependencyBranch.joinToString(" -> ")}."
                }

                return@forEach
            }

            try {
                val runtimeDependencies = getRuntimeDependencies(packageName, lockFile)
                val transitiveDependencies = buildDependencyTree(
                    runtimeDependencies, lockFile, packages, virtualPackages, dependencyBranch + packageName
                )
                packageReferences += packageInfo.toReference(dependencies = transitiveDependencies)
            } catch (e: IOException) {
                e.showStackTrace()

                packageInfo.toReference(
                    issues = listOf(
                        createAndLogIssue(
                            source = managerName,
                            message = "Could not resolve dependencies of '$packageName': ${e.collectMessages()}"
                        )
                    )
                )
            }
        }

        return packageReferences
    }

    private fun parseProject(definitionFile: File, scopes: Set<Scope>): Project {
        logger.info { "Parsing project metadata from '$definitionFile'..." }

        val json = definitionFile.readTree()
        val homepageUrl = json["homepage"].textValueOrEmpty()
        val vcs = parseVcsInfo(json)
        val rawName = json["name"]?.textValue() ?: definitionFile.parentFile.name

        return Project(
            id = Identifier(
                type = managerName,
                namespace = rawName.substringBefore('/'),
                name = rawName.substringAfter('/'),
                version = json["version"].textValueOrEmpty()
            ),
            definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
            authors = parseAuthors(json),
            declaredLicenses = parseDeclaredLicenses(json),
            vcs = vcs,
            vcsProcessed = processProjectVcs(definitionFile.parentFile, vcs, homepageUrl),
            homepageUrl = homepageUrl,
            scopeDependencies = scopes
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
                    logger.warn { "No version information found for package $rawName." }
                }

                packages[rawName] = Package(
                    id = Identifier(
                        type = managerName,
                        namespace = rawName.substringBefore('/'),
                        name = rawName.substringAfter('/'),
                        version = version
                    ),
                    authors = parseAuthors(pkgInfo),
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

    private fun ensureLockFile(workingDir: File): File {
        val lockFile = workingDir.resolve(COMPOSER_LOCK_FILE)

        val hasLockFile = lockFile.isFile
        requireLockfile(workingDir) { hasLockFile }
        if (hasLockFile) return lockFile

        val composerVersion = Semver(getVersion(workingDir))
        val args = listOfNotNull(
            "update",
            "--ignore-platform-reqs",
            "--no-install".takeIf { composerVersion.major >= 2 }
        )

        run(workingDir, *args.toTypedArray())

        return lockFile
    }
}

/**
 * Return whether this String denotes a type of platform dependency, see
 * https://getcomposer.org/doc/articles/composer-platform-dependencies.md#different-types-of-platform-packages.
 */
private fun String.isPlatformDependency(): Boolean =
    this in (COMPOSER_PLATFORM_TYPES + PHP_PLATFORM_TYPES) || startsWith("ext-") || startsWith("lib-")

private val COMPOSER_PLATFORM_TYPES = setOf("composer", "composer-plugin-api", "composer-runtime-api")
private val PHP_PLATFORM_TYPES = setOf("php", "php-64bit", "php-ipv6", "php-zts", "php-debug")

private fun getRuntimeDependencies(packageName: String, lockFile: JsonNode): Sequence<String> {
    listOf("packages", "packages-dev").forEach {
        lockFile[it]?.forEach { packageInfo ->
            if (packageInfo["name"].textValueOrEmpty() == packageName) {
                val requiredPackages = packageInfo["require"]
                if (requiredPackages != null && requiredPackages.isObject) {
                    return (requiredPackages as ObjectNode).fieldNames().asSequence()
                }
            }
        }
    }

    return emptySequence()
}

private fun parseArtifact(packageInfo: JsonNode): RemoteArtifact =
    packageInfo["dist"]?.let {
        val shasum = it["shasum"].textValueOrEmpty()
        RemoteArtifact(it["url"].textValueOrEmpty(), Hash.create(shasum))
    }.orEmpty()

private fun parseAuthors(packageInfo: JsonNode): Set<String> =
    packageInfo["authors"]?.mapNotNullTo(mutableSetOf()) { it["name"]?.textValue() }.orEmpty()

private fun parseDeclaredLicenses(packageInfo: JsonNode): Set<String> =
    packageInfo["license"]?.mapNotNullTo(mutableSetOf()) { it.textValue() }.orEmpty()

private fun parseVcsInfo(packageInfo: JsonNode): VcsInfo =
    packageInfo["source"]?.let {
        VcsInfo(
            type = VcsType.forName(it["type"].textValueOrEmpty()),
            url = it["url"].textValueOrEmpty(),
            revision = it["reference"].textValueOrEmpty()
        )
    }.orEmpty()

/**
 * Get all names of "virtual" (replaced or provided) packages in the package or lock file.
 *
 * While Composer also takes the versions of the virtual packages into account, we simply use priorities here. Since
 * Composer can't handle the same package in multiple version, we can assume that as soon as a package is found in
 * 'composer.lock' we can ignore any virtual package with the same name. Since the code later depends on the virtual
 * packages not accidentally containing a package which is actually installed, we make sure to only return virtual
 * packages for which are not in the installed package map.
 */
private fun parseVirtualPackageNames(
    packages: Map<String, Package>,
    manifest: JsonNode,
    lockFile: JsonNode
): Set<String> {
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

private fun parseVirtualNames(packageInfo: JsonNode): Set<String> =
    listOf("replace", "provide").flatMapTo(mutableSetOf()) {
        packageInfo[it]?.fieldNames()?.asSequence().orEmpty()
    }
