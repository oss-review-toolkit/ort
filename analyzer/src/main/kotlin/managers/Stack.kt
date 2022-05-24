/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

package org.ossreviewtoolkit.analyzer.managers

import com.paypal.digraph.parser.GraphParser

import com.vdurmont.semver4j.Requirement

import java.io.File
import java.io.IOException
import java.util.SortedSet

import org.ossreviewtoolkit.analyzer.AbstractPackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.parseAuthorString
import org.ossreviewtoolkit.downloader.VersionControlSystem
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
import org.ossreviewtoolkit.model.utils.toPurl
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.ProcessCapture
import org.ossreviewtoolkit.utils.common.safeDeleteRecursively
import org.ossreviewtoolkit.utils.common.unquote
import org.ossreviewtoolkit.utils.ort.OkHttpClientHelper
import org.ossreviewtoolkit.utils.ort.log

/**
 * The [Stack](https://haskellstack.org/) package manager for Haskell.
 */
class Stack(
    name: String,
    analysisRoot: File,
    analyzerConfig: AnalyzerConfiguration,
    repoConfig: RepositoryConfiguration
) : PackageManager(name, analysisRoot, analyzerConfig, repoConfig), CommandLineTool {
    class Factory : AbstractPackageManagerFactory<Stack>("Stack") {
        override val globsForDefinitionFiles = listOf("stack.yaml")

        override fun create(
            analysisRoot: File,
            analyzerConfig: AnalyzerConfiguration,
            repoConfig: RepositoryConfiguration
        ) = Stack(managerName, analysisRoot, analyzerConfig, repoConfig)
    }

    override fun command(workingDir: File?) = "stack"

    override fun transformVersion(output: String) =
        // The version string can be something like:
        // Version 1.7.1 x86_64
        // Version 2.1.1, Git revision f612ea85316bbc327a64e4ad8d9f0b150dc12d4b (7648 commits) x86_64 hpack-0.31.2
        output.removePrefix("Version ").substringBefore(',').substringBefore(' ')

    override fun getVersionRequirement(): Requirement = Requirement.buildIvy("[2.1.1,)")

    override fun beforeResolution(definitionFiles: List<File>) = checkVersion()

    override fun resolveDependencies(definitionFile: File, labels: Map<String, String>): List<ProjectAnalyzerResult> {
        val workingDir = definitionFile.parentFile

        // Parse project information from the *.cabal file.
        val cabalFiles = workingDir.walk().filter {
            it.isFile && it.extension == "cabal"
        }.toList()

        val cabalFile = when (cabalFiles.size) {
            0 -> throw IOException("No *.cabal file found in '$workingDir'.")
            1 -> cabalFiles.first()
            else -> throw IOException("Multiple *.cabal files found in '$cabalFiles'.")
        }

        val projectPackage = parseCabalFile(cabalFile.readText())
        val projectId = projectPackage.id.copy(type = managerName)

        // Parse package information from the stack.yaml file.
        fun runStack(vararg command: String): ProcessCapture {
            // Delete any left-overs from interrupted stack runs.
            workingDir.resolve(".stack-work").safeDeleteRecursively()

            return run(workingDir, *command)
        }

        fun mapParentsToChildren(scope: String): Map<String, List<String>> {
            val dotGraph = runStack("dot", "--global-hints", "--$scope").stdout

            // Strip any leading garbage in case Stack was bootstrapping itself, resulting in unrelated output.
            val dotLines = dotGraph.lineSequence().dropWhile { !it.startsWith("strict digraph deps") }

            val dotParser = GraphParser(dotLines.joinToString("\n").byteInputStream())
            val dependencies = mutableMapOf<String, MutableList<String>>()

            dotParser.edges.values.forEach { edge ->
                val parent = edge.node1.id.unquote()
                val child = edge.node2.id.unquote()
                dependencies.getOrPut(parent) { mutableListOf() } += child
            }

            log.debug { "Parsed ${dependencies.size} dependency relations from graph." }

            return dependencies
        }

        fun mapNamesToVersions(scope: String): Map<String, String> {
            val dependencies = runStack("ls", "dependencies", "--global-hints", "--$scope").stdout
            return dependencies.lines().associate {
                Pair(it.substringBefore(' '), it.substringAfter(' '))
            }.also {
                log.debug { "Parsed ${it.size} dependency versions from list." }
            }
        }

        // A map of package IDs to enriched package information.
        val allPackages = mutableMapOf<Identifier, Package>()

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
            authors = projectPackage.authors,
            declaredLicenses = projectPackage.declaredLicenses,
            vcs = projectPackage.vcs,
            vcsProcessed = processProjectVcs(workingDir, projectPackage.vcs, projectPackage.homepageUrl),
            homepageUrl = projectPackage.homepageUrl,
            scopeDependencies = scopes
        )

        return listOf(ProjectAnalyzerResult(project, allPackages.values.toSortedSet()))
    }

    private fun buildDependencyTree(
        parentName: String, allPackages: MutableMap<Identifier, Package>,
        childMap: Map<String, List<String>>, versionMap: Map<String, String>,
        scopeDependencies: SortedSet<PackageReference>
    ) {
        childMap[parentName]?.let { children ->
            children.forEach { childName ->
                val pkgId = Identifier(
                    type = "Hackage",
                    namespace = "",
                    name = childName,
                    version = versionMap[childName].orEmpty()
                )

                val pkgFallback = Package.EMPTY.copy(id = pkgId, purl = pkgId.toPurl())

                val pkg = allPackages.getOrPut(pkgId) {
                    if (pkgId.type == "Hackage") {
                        // Enrich the package with additional metadata from Hackage.
                        downloadCabalFile(pkgId)?.let {
                            parseCabalFile(it)
                        } ?: pkgFallback
                    } else {
                        pkgFallback
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

    private fun downloadCabalFile(pkgId: Identifier): String? {
        val url = "${getPackageUrl(pkgId.name, pkgId.version)}/src/${pkgId.name}.cabal"

        return OkHttpClientHelper.downloadText(url).onFailure {
            log.warn { "Unable to retrieve Hackage metadata for package '${pkgId.toCoordinates()}'." }
        }.getOrNull()
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
            when (keyValue.size) {
                1 -> {
                    // Handle lines without a colon.
                    val nestedMap = parseKeyValue(i, keyPrefix + keyValue[0].replace(" ", "-") + "-")
                    map += nestedMap
                }
                2 -> {
                    // Handle lines with a colon.
                    val key = (keyPrefix + keyValue[0]).lowercase()

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
                            if (valueLines.isNotEmpty()) valueLines += ""
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
    //       it as JSON to the console.
    private fun parseCabalFile(cabal: String): Package {
        // For an example file see
        // https://hackage.haskell.org/package/transformers-compat-0.5.1.4/src/transformers-compat.cabal
        val map = parseKeyValue(cabal.lines().listIterator())

        val id = Identifier(
            type = "Hackage",
            namespace = map["category"].orEmpty(),
            name = map["name"].orEmpty(),
            version = map["version"].orEmpty()
        )

        val artifact = RemoteArtifact.EMPTY.copy(
            url = "${getPackageUrl(id.name, id.version)}/${id.name}-${id.version}.tar.gz"
        )

        val vcsType = (map["source-repository-this-type"] ?: map["source-repository-head-type"]).orEmpty()
        val vcsUrl = (map["source-repository-this-location"] ?: map["source-repository-head-location"]).orEmpty()
        val vcs = VcsInfo(
            type = VcsType(vcsType),
            revision = map["source-repository-this-tag"].orEmpty(),
            url = vcsUrl
        )

        val homepageUrl = map["homepage"].orEmpty()

        return Package(
            id = id,
            authors = map["author"].orEmpty()
                .split(',')
                .map(String::trim)
                .filter(String::isNotEmpty)
                .mapTo(sortedSetOf(), ::parseAuthorString),
            declaredLicenses = map["license"]?.let { sortedSetOf(it) } ?: sortedSetOf(),
            description = map["description"].orEmpty(),
            homepageUrl = homepageUrl,
            binaryArtifact = RemoteArtifact.EMPTY,
            sourceArtifact = artifact,
            vcs = vcs,
            vcsProcessed = processPackageVcs(vcs, homepageUrl)
        )
    }
}
