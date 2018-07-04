/*
 * Copyright (c) 2017-2018 HERE Europe B.V.
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
import com.here.ort.model.HashAlgorithm
import com.here.ort.model.Identifier
import com.here.ort.model.Package
import com.here.ort.model.PackageReference
import com.here.ort.model.Project
import com.here.ort.model.ProjectAnalyzerResult
import com.here.ort.model.RemoteArtifact
import com.here.ort.model.Scope
import com.here.ort.model.VcsInfo
import com.here.ort.utils.OkHttpClientHelper
import com.here.ort.utils.ProcessCapture
import com.here.ort.utils.log
import com.here.ort.utils.safeDeleteRecursively

import com.paypal.digraph.parser.GraphParser

import okhttp3.Request

import java.io.File
import java.net.HttpURLConnection
import java.util.SortedSet

class Stack : PackageManager() {
    companion object : PackageManagerFactory<Stack>(
            "http://haskellstack.org/",
            "Haskell",
            listOf("stack.yaml")
    ) {
        override fun create() = Stack()
    }

    override fun command(workingDir: File) = "stack"

    override fun toString() = Stack.toString()

    override fun resolveDependencies(definitionFile: File): ProjectAnalyzerResult? {
        val workingDir = definitionFile.parentFile
        val projectName = workingDir.name

        fun runStack(vararg command: String): ProcessCapture {
            // Delete any left-overs from interrupted stack runs.
            File(workingDir, ".stack-work").safeDeleteRecursively()

            return ProcessCapture(workingDir, command(workingDir), *command).requireSuccess()
        }

        fun mapParentsToChildren(scope: String): Map<String, List<String>> {
            val dotGraph = runStack("dot", "--$scope").stdout()
            val dotParser = GraphParser(dotGraph.byteInputStream())

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
            val dependencies = runStack("ls", "dependencies", "--$scope").stdout()
            return dependencies.lines().associate {
                Pair(it.substringBefore(" "), it.substringAfter(" "))
            }.also {
                log.debug { "Parsed ${it.count()} dependency versions from list." }
            }
        }

        val packageTemplates = sortedSetOf<Package>()

        val externalChildren = mapParentsToChildren("external")
        val externalVersions = mapNamesToVersions("external")
        val externalDependencies = sortedSetOf<PackageReference>()
        buildDependencyTree(projectName, externalChildren, externalVersions, packageTemplates, externalDependencies)

        val testChildren = mapParentsToChildren("test")
        val testVersions = mapNamesToVersions("test")
        val testDependencies = sortedSetOf<PackageReference>()
        buildDependencyTree(projectName, testChildren, testVersions, packageTemplates, testDependencies)

        val benchChildren = mapParentsToChildren("bench")
        val benchVersions = mapNamesToVersions("bench")
        val benchDependencies = sortedSetOf<PackageReference>()
        buildDependencyTree(projectName, benchChildren, benchVersions, packageTemplates, benchDependencies)

        val scopes = sortedSetOf(
                Scope("external", true, externalDependencies),
                Scope("test", false, testDependencies),
                Scope("bench", false, benchDependencies)
        )

        // Enrich the package templates with additional meta-data from Hackage.
        val packages = sortedSetOf<Package>()
        packageTemplates.mapTo(packages) { pkg ->
            if (pkg.id.name == "rts") {
                // The runtime system ships with the compiler and is not hosted on Hackage.
                pkg
            } else {
                downloadCabalFile(pkg)?.let {
                    parseCabalFile(it)
                } ?: pkg
            }
        }

        var parentDir = workingDir.parentFile
        var cabalFile = File(workingDir, "$projectName.cabal")

        while (!cabalFile.isFile && parentDir != null) {
            // Guess cabal file names for sub-projects.
            cabalFile = File(workingDir, "${parentDir.name}-$projectName.cabal")
            parentDir = parentDir.parentFile
        }

        val project = if (cabalFile.isFile) {
            val projectPackage = parseCabalFile(cabalFile.readText())

            Project(
                    id = projectPackage.id,
                    definitionFilePath = VersionControlSystem.getPathToRoot(definitionFile) ?: "",
                    declaredLicenses = projectPackage.declaredLicenses,
                    vcs = projectPackage.vcs,
                    vcsProcessed = processProjectVcs(workingDir, projectPackage.vcs, projectPackage.homepageUrl),
                    homepageUrl = projectPackage.homepageUrl,
                    scopes = scopes
            )
        } else {
            Project(
                    id = Identifier(
                            provider = toString(),
                            namespace = "",
                            name = projectName,
                            version = ""
                    ),
                    definitionFilePath = VersionControlSystem.getPathToRoot(definitionFile) ?: "",
                    declaredLicenses = sortedSetOf(),
                    vcs = VcsInfo.EMPTY,
                    vcsProcessed = processProjectVcs(workingDir),
                    homepageUrl = "",
                    scopes = scopes
            )
        }

        // Stack does not support lock files, so hard-code "allowDynamicVersions" to "true".
        return ProjectAnalyzerResult(true, project,
                packages.map { it.toCuratedPackage() }.toSortedSet())
    }

    private fun buildDependencyTree(parentName: String,
                                    childMap: Map<String, List<String>>, versionMap: Map<String, String>,
                                    allPackages: SortedSet<Package>, dependencies: SortedSet<PackageReference>) {
        childMap[parentName]?.let { children ->
            children.forEach { childName ->
                val pkg = Package(
                        id = Identifier(
                                provider = toString(),
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

                allPackages += pkg

                val packageRef = pkg.toReference()
                dependencies += packageRef

                buildDependencyTree(childName, childMap, versionMap, allPackages, packageRef.dependencies)
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

        return OkHttpClientHelper.execute(Main.HTTP_CACHE_PATH, pkgRequest).use { response ->
            val body = response.body()?.string()?.trim()

            if (response.code() != HttpURLConnection.HTTP_OK || body.isNullOrEmpty()) {
                log.warn { "Unable to retrieve Hackage meta-data for package '${pkg.id}'." }
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
                            if (getIndentation(indentedLine) <= indentation) {
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

                    map[key] = valueLines.joinToString("\n")
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
                provider = "Hackage",
                namespace = map["category"] ?: "",
                name = map["name"] ?: "",
                version = map["version"] ?: ""
        )

        val artifact = RemoteArtifact(
                url = "${getPackageUrl(id.name, id.version)}/${id.name}-${id.version}.tar.gz",
                hash = "",
                hashAlgorithm = HashAlgorithm.UNKNOWN
        )

        val vcs = VcsInfo(
                type = map["source-repository-this-type"] ?: map["source-repository-head-type"] ?: "",
                revision = map["source-repository-this-tag"] ?: "",
                url = map["source-repository-this-location"] ?: map["source-repository-head-location"] ?: ""
        )

        return Package(
                id = id,
                declaredLicenses = map["license"]?.let { sortedSetOf(it) } ?: sortedSetOf(),
                description = map["description"] ?: "",
                homepageUrl = map["homepage"] ?: "",
                binaryArtifact = artifact,
                sourceArtifact = RemoteArtifact.EMPTY,
                vcs = vcs,
                vcsProcessed = processPackageVcs(vcs, homepageUrl)
        )
    }
}
