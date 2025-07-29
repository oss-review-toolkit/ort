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

import java.io.File
import java.io.IOException

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.PackageManager.Companion.processPackageVcs
import org.ossreviewtoolkit.analyzer.PackageManagerFactory
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
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.model.orEmpty
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.common.div
import org.ossreviewtoolkit.utils.common.splitOnWhitespace
import org.ossreviewtoolkit.utils.common.stashDirectories
import org.ossreviewtoolkit.utils.ort.showStackTrace

import org.semver4j.range.RangeList
import org.semver4j.range.RangeListFactory

private const val COMPOSER_PHAR_BINARY = "composer.phar"
private const val SCOPE_NAME_REQUIRE = "require"
private const val SCOPE_NAME_REQUIRE_DEV = "require-dev"
private val ALL_SCOPE_NAMES = setOf(SCOPE_NAME_REQUIRE, SCOPE_NAME_REQUIRE_DEV)

internal object ComposerCommand : CommandLineTool {
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

    override fun transformVersion(output: String) =
        // The version string can be something like:
        // Composer version 1.5.1 2017-08-09 16:07:22
        // Composer version @package_branch_alias_version@ (1.0.0-beta2) 2016-03-27 16:00:34
        output.splitOnWhitespace().dropLast(2).last().removeSurrounding("(", ")")

    override fun getVersionRequirement(): RangeList = RangeListFactory.create(">=1.5")
}

/**
 * The [Composer](https://getcomposer.org/) package manager for PHP.
 */
@OrtPlugin(
    displayName = "Composer",
    description = "The Composer package manager for PHP.",
    factory = PackageManagerFactory::class
)
class Composer(override val descriptor: PluginDescriptor = ComposerFactory.descriptor) : PackageManager("Composer") {
    override val globsForDefinitionFiles = listOf("composer.json")

    override fun beforeResolution(
        analysisRoot: File,
        definitionFiles: List<File>,
        analyzerConfig: AnalyzerConfiguration
    ) {
        // If all directories we are analyzing contain a composer.phar, no global installation of Composer is required
        // and hence we skip the version check.
        if (definitionFiles.all { File(it.parentFile, COMPOSER_PHAR_BINARY).isFile }) return

        // We do not actually depend on any features specific to a version of Composer, but we still want to stick to
        // fixed versions to be sure to get consistent results.
        ComposerCommand.checkVersion()
    }

    override fun mapDefinitionFiles(
        analysisRoot: File,
        definitionFiles: List<File>,
        analyzerConfig: AnalyzerConfiguration
    ): List<File> {
        val projectFiles = definitionFiles.toMutableList()

        // Ignore definition files from vendor directories that reside next to other definition files, to avoid the
        // former from being recognized as projects.
        var index = 0
        while (index < projectFiles.size - 1) {
            val projectFile = projectFiles[index++]
            val vendorDir = projectFile.resolveSibling("vendor")
            projectFiles.subList(index, projectFiles.size).removeAll { it.startsWith(vendorDir) }
        }

        return projectFiles
    }

    override fun resolveDependencies(
        analysisRoot: File,
        definitionFile: File,
        excludes: Excludes,
        analyzerConfig: AnalyzerConfiguration,
        labels: Map<String, String>
    ): List<ProjectAnalyzerResult> {
        val workingDir = definitionFile.parentFile

        val projectPackageInfo = parsePackageInfo(definitionFile.readText())
        val hasDependencies = projectPackageInfo.require.isNotEmpty()

        if (!hasDependencies) {
            val project = parseProject(analysisRoot, definitionFile, scopes = emptySet())
            val result = ProjectAnalyzerResult(project, packages = emptySet())

            return listOf(result)
        }

        val lockfile = stashDirectories(workingDir / "vendor").use { _ ->
            val lockfileProvider = LockfileProvider(definitionFile)

            requireLockfile(analysisRoot, workingDir, analyzerConfig.allowDynamicVersions) {
                lockfileProvider.lockfile.isFile
            }

            lockfileProvider.ensureLockfile {
                logger.info { "Parsing lockfile at '$it'..." }
                parseLockfile(it.readText())
            }
        }

        val packages = (lockfile.packages + lockfile.packagesDev).associate {
            checkNotNull(it.name) to it.toPackage()
        }

        // Let's also determine the "virtual" (replaced and provided) packages. These can be declared as
        // required, but are not listed in composer.lock as installed.
        // If we didn't handle them specifically, we would report them as missing when trying to load the
        // dependency information for them. We can't simply put these "virtual" packages in the normal package
        // map as this would cause ORT to report a package which is not actually installed with the contents of
        // the "replacing" package.
        val virtualPackages = parseVirtualPackageNames(packages, projectPackageInfo, lockfile)

        val scopes = ALL_SCOPE_NAMES.mapTo(mutableSetOf()) { scopeName ->
            val requiredPackages = projectPackageInfo.getScopeDependencies(scopeName)
            val dependencies = buildDependencyTree(requiredPackages, lockfile, packages, virtualPackages)
            Scope(scopeName, dependencies)
        }

        val project = parseProject(analysisRoot, definitionFile, scopes)
        val result = ProjectAnalyzerResult(project, packages.values.toSet())

        return listOf(result)
    }

    private fun buildDependencyTree(
        dependencies: Set<String>,
        lockfile: Lockfile,
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
                val runtimeDependencies = getRuntimeDependencies(packageName, lockfile)
                val transitiveDependencies = buildDependencyTree(
                    runtimeDependencies, lockfile, packages, virtualPackages, dependencyBranch + packageName
                )
                packageReferences += packageInfo.toReference(dependencies = transitiveDependencies)
            } catch (e: IOException) {
                e.showStackTrace()

                packageInfo.toReference(
                    issues = listOf(
                        createAndLogIssue("Could not resolve dependencies of '$packageName': ${e.collectMessages()}")
                    )
                )
            }
        }

        return packageReferences
    }

    private fun parseProject(analysisRoot: File, definitionFile: File, scopes: Set<Scope>): Project {
        logger.info { "Parsing project metadata from '$definitionFile'..." }

        val pkgInfo = parsePackageInfo(definitionFile.readText())
        val homepageUrl = pkgInfo.homepage.orEmpty()
        val vcs = parseVcsInfo(pkgInfo)
        val rawName = pkgInfo.name
        val namespace = rawName?.substringBefore("/", missingDelimiterValue = "").orEmpty()
        val name = rawName?.substringAfter("/") ?: getFallbackProjectName(analysisRoot, definitionFile)

        return Project(
            id = Identifier(
                type = projectType,
                namespace = namespace,
                name = name,
                version = pkgInfo.version.orEmpty()
            ),
            definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
            authors = parseAuthors(pkgInfo),
            declaredLicenses = parseDeclaredLicenses(pkgInfo),
            vcs = vcs,
            vcsProcessed = processProjectVcs(definitionFile.parentFile, vcs, homepageUrl),
            homepageUrl = homepageUrl,
            scopeDependencies = scopes
        )
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

private fun getRuntimeDependencies(packageName: String, lockfile: Lockfile): Set<String> {
    (lockfile.packages + lockfile.packagesDev).forEach { packageInfo ->
        if (packageInfo.name == packageName) {
            return packageInfo.require.keys
        }
    }

    return emptySet()
}

private fun parseAuthors(packageInfo: PackageInfo): Set<String> =
    packageInfo.authors.mapNotNullTo(mutableSetOf()) { it.name }

private fun parseDeclaredLicenses(packageInfo: PackageInfo): Set<String> = packageInfo.license.toSet()

private fun parseVcsInfo(packageInfo: PackageInfo): VcsInfo =
    packageInfo.source?.let {
        VcsInfo(
            type = VcsType.forName(it.type.orEmpty()),
            url = it.url.orEmpty(),
            revision = it.reference.orEmpty()
        )
    }.orEmpty()

/**
 * Get all names of "virtual" (replaced or provided) packages in the package or lockfile.
 *
 * While Composer also takes the versions of the virtual packages into account, we simply use priorities here. Since
 * Composer can't handle the same package in multiple version, we can assume that as soon as a package is found in
 * 'composer.lock' we can ignore any virtual package with the same name. Since the code later depends on the virtual
 * packages not accidentally containing a package which is actually installed, we make sure to only return virtual
 * packages for which are not in the installed package map.
 */
private fun parseVirtualPackageNames(
    packages: Map<String, Package>,
    projectPackageInfo: PackageInfo,
    lockfile: Lockfile
): Set<String> =
    buildSet {
        // The contents of the manifest file, which can also define replacements, is not included in the lockfile, so
        // we parse the manifest file as well.
        (lockfile.packages + lockfile.packagesDev + projectPackageInfo).flatMapTo(this) {
            it.replace.keys + it.provide.keys
        }

        removeAll(packages.keys)
    }

private fun PackageInfo.toPackage(): Package {
    val rawName = checkNotNull(name)
    val version = version.orEmpty()
    val homepageUrl = homepage.orEmpty()
    val vcsFromPackage = parseVcsInfo(this)

    return Package(
        id = Identifier(
            type = "Composer",
            namespace = rawName.substringBefore('/'),
            name = rawName.substringAfter('/'),
            version = version
        ),
        authors = parseAuthors(this),
        declaredLicenses = parseDeclaredLicenses(this),
        description = description.orEmpty(),
        homepageUrl = homepageUrl,
        binaryArtifact = RemoteArtifact.EMPTY,
        sourceArtifact = dist?.let {
            RemoteArtifact(
                url = it.url.orEmpty(),
                hash = Hash.create(it.shasum.orEmpty())
            )
        }.orEmpty(),
        vcs = vcsFromPackage,
        vcsProcessed = processPackageVcs(vcsFromPackage, homepageUrl)
    )
}

private fun PackageInfo.getScopeDependencies(scopeName: String): Set<String> =
    when (scopeName) {
        SCOPE_NAME_REQUIRE -> require.keys
        SCOPE_NAME_REQUIRE_DEV -> requireDev.keys
        else -> error("Invalid scope name: '$scopeName'.")
    }
