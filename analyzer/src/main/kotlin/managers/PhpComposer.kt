/*
 * Copyright (c) 2017 HERE Europe B.V.
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

import com.here.ort.analyzer.Main
import com.here.ort.analyzer.PackageManager
import com.here.ort.analyzer.PackageManagerFactory
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.model.AnalyzerResult
import com.here.ort.model.Package
import com.here.ort.model.PackageReference
import com.here.ort.model.Project
import com.here.ort.model.RemoteArtifact
import com.here.ort.model.Scope
import com.here.ort.model.VcsInfo
import com.here.ort.utils.OS
import com.here.ort.utils.ProcessCapture
import com.here.ort.utils.log

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

const val COMPOSER_GLOBAL_SCRIPT_FILENAME_WINDOWS = "composer.bat"
const val COMPOSER_BINARY = "composer.phar"
const val COMPOSER_GLOBAL_SCRIPT_FILENAME = "composer"

const val SCOPE_REQUIRED_SECTION_HEADER = "requires"
const val SCOPE_REQUIRED_DEV_SECTION_HEADER = "requires (dev)"

class PhpComposer : PackageManager() {
    companion object : PackageManagerFactory<PhpComposer>(
            "https://getcomposer.org/",
            "PHP",
            listOf("composer.json")
    ) {
        override fun create() = PhpComposer()
    }

    override fun command(workingDir: File) =
            if (File(workingDir, COMPOSER_BINARY).isFile) {
                "php $COMPOSER_BINARY"
            } else {
                if (OS.isWindows) {
                    COMPOSER_GLOBAL_SCRIPT_FILENAME_WINDOWS
                } else {
                    COMPOSER_GLOBAL_SCRIPT_FILENAME
                }
            }

    override fun resolveDependencies(projectDir: File, workingDir: File, definitionFile: File): AnalyzerResult? {
        if (definitionFile.readText().isBlank()) {
            log.warn { "Sipped parsing dependencies for '${definitionFile.path}' - file is empty" }
            return null
        }

        val vendorDir = File(workingDir, "vendor")
        var tempVendorDir: File? = null

        try {
            if (vendorDir.isDirectory) {
                val tempDir = createTempDir("analyzer", ".tmp", workingDir)
                tempVendorDir = File(tempDir, "composer_vendor")
                log.warn { "'$vendorDir' already exists, temporarily moving it to '$tempVendorDir'." }
                Files.move(vendorDir.toPath(), tempVendorDir.toPath(), StandardCopyOption.ATOMIC_MOVE)
            }

            val scopes = mutableSetOf<Scope>()
            val packages = mutableSetOf<Package>()
            val errors = mutableListOf<String>()
            val vcsDir = VersionControlSystem.forDirectory(projectDir)
            val projectDetails = showPackage(workingDir).stdout()
            val (packageManager, namespace, projectName, version, declaredLicenses, _, projectHomepageUrl, _, _, _, _) =
                    parsePackageDetails(projectDetails, workingDir)

            // Currently single 'composer install' is performed on top level of project, which enables composer to
            // produce results for top level dependencies and their dependencies. If we need deeper dependency
            // analysis, recursive installing and parsing of dependencies should be implemented (probably with
            // controlled recursion depth)
            installDependencies(workingDir)

            try {
                parseScope(projectDetails, SCOPE_REQUIRED_SECTION_HEADER, scopes, packages, errors, workingDir)
                parseScope(projectDetails, SCOPE_REQUIRED_DEV_SECTION_HEADER, scopes, packages, errors, workingDir)
            } catch (e: Exception) {
                if (Main.stacktrace) {
                    e.printStackTrace()
                }

                log.error { "Could not analyze '${definitionFile.absolutePath}': ${e.message}" }
                return null
            }

            val project = Project(
                    packageManager = packageManager,
                    namespace = namespace,
                    name = projectName,
                    version = version,
                    declaredLicenses = declaredLicenses,
                    aliases = emptyList(),
                    vcs = vcsDir?.getInfo(projectDir) ?: VcsInfo.EMPTY,
                    homepageUrl = projectHomepageUrl,
                    scopes = scopes.toSortedSet())
            return AnalyzerResult(true, project, packages.toSortedSet(), errors)
        } finally {
            // Delete vendor folder to not pollute the scan.
            if (!vendorDir.deleteRecursively()) {
                throw IOException("Unable to delete the '$vendorDir' directory.")
            }

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

    private fun parseScope(projectDetails: String, scopeName: String, scopes: MutableSet<Scope>,
                           packages: MutableSet<Package>, errors: MutableList<String>, workingDir: File) {
        log.info { "Parsing dependencies for $scopeName" }
        try {
            val dependantPackageRefs = parseDependencies(projectDetails, scopeName) {
                val (scopeDependencyPkgName, scopeDependencyVersionConstraint) = it.split(Regex("\\s"))
                val dependencies2ndLevel = mutableSetOf<PackageReference>()
                val parsedPackage = parseDependencyPackage(workingDir, scopeDependencyPkgName, dependencies2ndLevel,
                        scopeName, errors)
                if (parsedPackage != null) {
                    packages.add(parsedPackage)
                }

                PackageReference(namespace = parsedPackage?.namespace ?: "",
                        name = parsedPackage?.name ?: scopeDependencyPkgName,
                        version = parsedPackage?.version ?: scopeDependencyVersionConstraint,
                        dependencies = dependencies2ndLevel.toSortedSet())
            }

            scopes.add(Scope(name = scopeName,
                    delivered = true,
                    dependencies = dependantPackageRefs.toSortedSet()))
        } catch (e: Exception) {
            if (Main.stacktrace) {
                e.printStackTrace()
            }

            val errorMsg = "Failed to parse scope $scopeName: ${e.message}"
            log.error { errorMsg }
            errors.add(errorMsg)
        }
    }

    private fun parseDependencyPackage(workingDir: File, packageName: String,
                                       dependencies: MutableSet<PackageReference>,
                                       scopeName: String, errors: MutableList<String>): Package? {
        log.info { "Parsing package $packageName" }
        if (packageName == "php" || packageName.startsWith("ext-")) {
            // Skip PHP itself along with PHP Extensions
            return null
        }

        return try {
            val pkgDetailLines = showPackage(workingDir, packageName).stdout().trim()
            val dependenciesLines = parseDependencies(pkgDetailLines, scopeName) { it }
            dependencies.addAll(dependenciesLines.map {
                val (scopeDependencyPkgName, scopeDependencyVersionConstraint) = it.split(Regex("\\s"))
                PackageReference(namespace = scopeDependencyPkgName.substringBefore("/"),
                        name = scopeDependencyPkgName,
                        version = scopeDependencyVersionConstraint,
                        dependencies = sortedSetOf())
            })
            parsePackageDetails(pkgDetailLines, workingDir)
        } catch (e: Exception) {
            if (Main.stacktrace) {
                e.printStackTrace()
            }

            val errorMsg = "Failed to parse package $packageName: ${e.message}"
            log.error { errorMsg }
            errors.add(errorMsg)
            null
        }
    }

    private fun <T : Any> parseDependencies(pkgDetailLines: String, scopeName: String, transform: (String) -> T):
            Iterable<T> {
        var endScopeSection = false
        return pkgDetailLines.substringAfter(scopeName, "").trim().lines().mapNotNull {
            endScopeSection = it.isBlank() || endScopeSection
            if (endScopeSection) {
                null
            } else {
                transform(it)
            }
        }
    }

    private fun parsePackageDetails(pkgShowCommandOutput: String, workingDir: File): Package {
        val pkgDetailsLines = pkgShowCommandOutput.lineSequence()
        val map = pkgDetailsLines.groupBy({ it.substringBefore(":").trim() }, { it.substringAfter(":").trim() })
        val pkgName = map["name"]?.first() ?: ""
        val version = map["version"]?.first() ?: ""
        val licenses = map["license"]?.toSortedSet() ?: sortedSetOf()
        val desc = map["descrip."]?.first() ?: ""
        val source = map["source"]?.first() ?: ""
        val dist = map["dist"]?.first() ?: ""

        val namespace = pkgName.substringBefore("/")
        val homepage = showHomepage(workingDir, pkgName).stdout().trim()
        val vcs = parseVcs(source)
        val sourceArtifact = parseSourceArtifact(dist)

        return Package(
                packageManager = javaClass.simpleName,
                namespace = namespace,
                name = pkgName,
                version = version,
                declaredLicenses = licenses,
                description = desc,
                homepageUrl = homepage,
                binaryArtifact = RemoteArtifact.EMPTY,
                sourceArtifact = sourceArtifact,
                vcs = vcs
        )
    }

    private fun installDependencies(workingDir: File): ProcessCapture =
            ProcessCapture(workingDir, *command(workingDir).split(" ").toTypedArray(), "install").requireSuccess()

    /**
     * Show package details for given [pkgName] ("--self" used by default for project details on top of [workingDir])
     */
    private fun showPackage(workingDir: File, pkgName: String = "--self"): ProcessCapture =
            ProcessCapture(workingDir, *command(workingDir).split(" ").toTypedArray(), "show", pkgName).requireSuccess()

    private fun showHomepage(workingDir: File, pkgName: String): ProcessCapture {
        val homeResult = ProcessCapture(workingDir, *command(workingDir).split(" ")
                .toTypedArray(), "home", "-s", "-H", pkgName)
        return if (homeResult.exitValue() != 0) {
            log.warn { "Could not get homepage url for '$pkgName': ${homeResult.stderr()} trying get repository url" }
            ProcessCapture(workingDir, *command(workingDir).split(" ")
                    .toTypedArray(), "home", "-s", pkgName).requireSuccess()
        } else {
            homeResult
        }
    }

    private fun parseVcs(sourceLine: String): VcsInfo {
        val matchResult = Regex("\\[(?<vcs>git|svn|fossil|hg)\\]\\s+(?<url>[\\w.:\\/-]+)\\s+(?<revision>\\w*)")
                .matchEntire(sourceLine)
        val vcs = matchResult?.groups?.get("vcs")?.value
        val url = matchResult?.groups?.get("url")?.value
        val rev = matchResult?.groups?.get("revision")?.value
        return if (vcs != null && url != null && rev != null) {
            VcsInfo(vcs, url, rev, "")
        } else {
            VcsInfo.EMPTY
        }
    }

    private fun parseSourceArtifact(dist: String): RemoteArtifact {
        val matchResult = Regex("\\[(?<type>zip|tar)\\]\\s+(?<url>[\\w.:\\/-]+)\\s+(?<checksum>\\w*)")
                .matchEntire(dist)
        val url = matchResult?.groups?.get("url")?.value
        val hash = matchResult?.groups?.get("checksum")?.value
        return if (url != null) {
            RemoteArtifact(url, hash ?: "", if (hash != null && hash.isNotBlank()) "sha1" else "")
        } else {
            RemoteArtifact.EMPTY
        }
    }
}
