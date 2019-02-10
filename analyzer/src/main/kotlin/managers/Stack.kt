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

import com.here.ort.analyzer.AbstractPackageManagerFactory
import com.here.ort.analyzer.HTTP_CACHE_PATH
import com.here.ort.analyzer.PackageManager
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.model.Hash
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
import com.here.ort.utils.CommandLineTool
import com.here.ort.utils.OkHttpClientHelper
import com.here.ort.utils.ProcessCapture
import com.here.ort.utils.log
import com.here.ort.utils.safeDeleteRecursively

import com.paypal.digraph.parser.GraphParser

import java.io.File
import java.io.FileFilter
import java.io.IOException
import java.net.HttpURLConnection
import java.nio.file.FileSystems
import java.util.SortedSet

import okhttp3.Request

/**
 * The Stack package manager for Haskell, see https://haskellstack.org/.
 */
class Stack(name: String, analyzerConfig: AnalyzerConfiguration, repoConfig: RepositoryConfiguration) :
        PackageManager(name, analyzerConfig, repoConfig), CommandLineTool {
    class Factory : AbstractPackageManagerFactory<Stack>("Stack") {
        override val globsForDefinitionFiles = listOf("stack.yaml")

        override fun create(analyzerConfig: AnalyzerConfiguration, repoConfig: RepositoryConfiguration) =
                Stack(managerName, analyzerConfig, repoConfig)
    }

    override fun command(workingDir: File?) = "stack"

    override fun resolveDependencies(definitionFile: File): ProjectAnalyzerResult? {
        val workingDir = definitionFile.parentFile

        // Parse project information from the *.cabal file.
        val cabalMatcher = FileSystems.getDefault().getPathMatcher("glob:**/*.cabal")

        val cabalFiles = workingDir.listFiles(FileFilter {
            cabalMatcher.matches(it.toPath())
        })

        val cabalFile = when (cabalFiles.count()) {
            0 -> throw IOException("No *.cabal file found in '$workingDir'.")
            1 -> cabalFiles.first()
            else -> throw IOException("Multiple *.cabal files found in '$cabalFiles'.")
        }

        val projectPackage = parseCabalFile(cabalFile.readText())
        val projectId = projectPackage.id.copy(type = managerName)

        // Parse package information from the stack.yaml file.
        fun runStack(vararg command: String): ProcessCapture {
            // Delete any left-overs from interrupted stack runs.
            File(workingDir, ".stack-work").safeDeleteRecursively()

            return run(workingDir, *command)
        }

        fun mapParentsToChildren(scope: String): Map<String, List<String>> {
            val dotGraph = runStack("dot", "--$scope").stdout

            // Strip any leading garbage in case Stack was bootstrapping itself, resulting in unrelated output.
            val dotLines = dotGraph.lineSequence().dropWhile { !it.startsWith("strict digraph deps") }

            val dotParser = GraphParser(dotLines.joinToString("\n").byteInputStream())
            val dependencies = mutableMapOf<String, MutableList<String>>()

            dotParser.edges.values.forEach { edge ->
                val parent = edge.node1.id.removeSurrounding("\"")
                val child = edge.node2.id.removeSurrounding("\"")
                dependencies.getOrPut(parent) { mutableListOf() } += child
            }

            log.debug { "Parsed ${dependencies.count()} dependency relations from graph." }

            return dependencies
        }

        fun mapNamesToVersions(scope: String): Map<String, String> {
            val dependencies = runStack("ls", "dependencies", "--$scope").stdout
            return dependencies.lines().associate {
                Pair(it.substringBefore(" "), it.substringAfter(" "))
            }.also {
                log.debug { "Parsed ${it.count()} dependency versions from list." }
            }
        }

        val allPackages = mutableMapOf<Package, Package>()

        val externalChildren = mapParentsToChildren("external")
        val externalVersions = mapNamesToVersions("external")
        val externalDependencies = sortedSetOf<PackageReference>()
        buildDependencyTree(projectId.name, allPackages, externalChildren, externalVersions, externalDependencies)

        val testChildren = mapParentsToChildren("test")
        val testVersions = mapNamesToVersions("test")
        val testDependencies = sortedSetOf<PackageReference>()
        buildDependencyTree(projectId.name, allPackages, testChildren, testVersions, testDependencies)

        val benchChildren = mapParentsToChildren("bench")
        val benchVersions = mapNamesToVersions("bench")
        val benchDependencies = sortedSetOf<PackageReference>()
        buildDependencyTree(projectId.name, allPackages, benchChildren, benchVersions, benchDependencies)

        val scopes = sortedSetOf(
                Scope("external", externalDependencies),
                Scope("test", testDependencies),
                Scope("bench", benchDependencies)
        )

        val project = Project(
                id = projectId,
                definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
                declaredLicenses = projectPackage.declaredLicenses,
                vcs = projectPackage.vcs,
                vcsProcessed = processProjectVcs(workingDir, projectPackage.vcs, projectPackage.homepageUrl),
                homepageUrl = projectPackage.homepageUrl,
                scopes = scopes
        )

        return ProjectAnalyzerResult(project, allPackages.values.map { it.toCuratedPackage() }.toSortedSet())
    }

    private fun buildDependencyTree(parentName: String, allPackages: MutableMap<Package, Package>,
                                    childMap: Map<String, List<String>>, versionMap: Map<String, String>,
                                    scopeDependencies: SortedSet<PackageReference>) {
        childMap[parentName]?.let { children ->
            children.forEach { childName ->
                val pkgTemplate = Package(
                        id = Identifier(
                                // The runtime system ships with the Glasgow Haskell Compiler (GHC) and is not hosted
                                // on Hackage.
                                type = if (childName == "rts") "GHC" else "Hackage",
                                namespace = "",
                                name = childName,
                                version = versionMap[childName] ?: ""
                        ),
                        declaredLicenses = sortedSetOf(),
                        description = "",
                        homepageUrl = "",
                        binaryArtifact = RemoteArtifact.EMPTY,
                        sourceArtifact = RemoteArtifact.EMPTY,
                        vcs = VcsInfo.EMPTY
                )

                val pkg = allPackages.getOrPut(pkgTemplate) {
                    if (pkgTemplate.id.type == "Hackage") {
                        // Enrich the package with additional meta-data from Hackage.
                        downloadCabalFile(pkgTemplate)?.let {
                            parseCabalFile(it)
                        } ?: pkgTemplate
                    } else {
                        pkgTemplate
                    }
                }

                val packageRef = pkg.toReference()
                scopeDependencies += packageRef

                buildDependencyTree(childName, allPackages, childMap, versionMap, packageRef.dependencies)
            }
        } ?: log.debug { "No dependencies found for '$parentName'." }
    }

    private fun getPackageUrl(name: String, version: String) =
            "https://hackage.haskell.org/package/$name-$version"

    private fun downloadCabalFile(pkg: Package): String? {
        val pkgRequest = Request.Builder()
                .get()
                .url("${getPackageUrl(pkg.id.name, pkg.id.version)}/src/${pkg.id.name}.cabal")
                .build()

        return OkHttpClientHelper.execute(HTTP_CACHE_PATH, pkgRequest).use { response ->
            val body = response.body()?.string()?.trim()

            if (response.code() != HttpURLConnection.HTTP_OK || body.isNullOrEmpty()) {
                log.warn { "Unable to retrieve Hackage meta-data for package '${pkg.id.toCoordinates()}'." }
                if (body != null) {
                    log.warn { "The response was '$body' (code ${response.code()})." }
                }

                null
            } else {
                body
            }
        }
    }

    private fun parseKeyValue(i: ListIterator<String>, keyPrefix: String = ""): Map<String, String> {
        fun getIndentation(line: String) =
                line.takeWhile { it.isWhitespace() }.length

        var indentation: Int? = null
        val map = mutableMapOf<String, String>()

        while (i.hasNext()) {
            val line = i.next()

            // Skip blank lines and comments.
            if (line.isBlank() || line.trimStart().startsWith("--")) continue

            if (indentation == null) {
                indentation = getIndentation(line)
            } else if (indentation != getIndentation(line)) {
                // Stop if the indentation level changes.
                i.previous()
                break
            }

            val keyValue = line.split(':', limit = 2).map { it.trim() }
            when (keyValue.count()) {
                1 -> {
                    // Handle lines without a colon.
                    val nestedMap = parseKeyValue(i, keyPrefix + keyValue[0].replace(" ", "-") + "-")
                    map += nestedMap
                }
                2 -> {
                    // Handle lines with a colon.
                    val key = (keyPrefix + keyValue[0]).toLowerCase()

                    val valueLines = mutableListOf<String>()

                    var isBlock = false
                    if (keyValue[1].isNotEmpty()) {
                        if (keyValue[1] == "{") {
                            // Support multi-line values that use curly braces instead of indentation.
                            isBlock = true
                        } else {
                            valueLines += keyValue[1]
                        }
                    }

                    // Parse a multi-line value.
                    while (i.hasNext()) {
                        var indentedLine = i.next()

                        if (isBlock) {
                            if (indentedLine == "}") {
                                // Stop if a block closes.
                                break
                            }
                        } else {
                            if (indentedLine.isNotBlank() && getIndentation(indentedLine) <= indentation) {
                                // Stop if the indentation level does not increase.
                                i.previous()
                                break
                            }
                        }

                        indentedLine = indentedLine.trim()

                        // Within a multi-line value, lines with only a dot mark empty lines.
                        if (indentedLine == ".") {
                            if (valueLines.isNotEmpty()) {
                                valueLines += ""
                            }
                        } else {
                            valueLines += indentedLine
                        }
                    }

                    val trimmedValueLines = valueLines.dropWhile { it.isBlank() }.dropLastWhile { it.isBlank() }
                    map[key] = trimmedValueLines.joinToString("\n")
                }
            }
        }

        return map
    }

    // TODO: Consider replacing this with a Haskell helper script that calls "readGenericPackageDescription" and dumps
    // it as JSON to the console.
    private fun parseCabalFile(cabal: String): Package {
        // For an example file see
        // https://hackage.haskell.org/package/transformers-compat-0.5.1.4/src/transformers-compat.cabal
        val map = parseKeyValue(cabal.lines().listIterator())

        val id = Identifier(
                type = "Hackage",
                namespace = map["category"] ?: "",
                name = map["name"] ?: "",
                version = map["version"] ?: ""
        )

        val artifact = RemoteArtifact(
                url = "${getPackageUrl(id.name, id.version)}/${id.name}-${id.version}.tar.gz",
                hash = Hash.UNKNOWN.value,
                hashAlgorithm = Hash.UNKNOWN.algorithm
        )

        val vcs = VcsInfo(
                type = map["source-repository-this-type"] ?: map["source-repository-head-type"] ?: "",
                revision = map["source-repository-this-tag"] ?: "",
                url = map["source-repository-this-location"] ?: map["source-repository-head-location"] ?: ""
        )

        val homepageUrl = map["homepage"] ?: ""

        return Package(
                id = id,
                declaredLicenses = map["license"]?.let { sortedSetOf(it) } ?: sortedSetOf(),
                description = map["description"] ?: "",
                homepageUrl = homepageUrl,
                binaryArtifact = RemoteArtifact.EMPTY,
                sourceArtifact = artifact,
                vcs = vcs,
                vcsProcessed = processPackageVcs(vcs, homepageUrl)
        )
    }
}
