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

import com.here.ort.analyzer.PackageManager
import com.here.ort.analyzer.PackageManagerFactory
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.model.Identifier
import com.here.ort.model.Package
import com.here.ort.model.PackageReference
import com.here.ort.model.Project
import com.here.ort.model.ProjectAnalyzerResult
import com.here.ort.model.RemoteArtifact
import com.here.ort.model.Scope
import com.here.ort.model.VcsInfo
import com.here.ort.utils.ProcessCapture
import com.here.ort.utils.safeDeleteRecursively

import com.paypal.digraph.parser.GraphParser

import java.io.File
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

            return dependencies
        }

        fun mapNamesToVersions(scope: String): Map<String, String> {
            val dependencies = runStack("ls", "dependencies", "--$scope").stdout()
            return dependencies.lines().associate {
                Pair(it.substringBefore(" "), it.substringAfter(" "))
            }
        }

        val allPackages = sortedSetOf<Package>()

        val externalChildren = mapParentsToChildren("external")
        val externalVersions = mapNamesToVersions("external")
        val externalDependencies = sortedSetOf<PackageReference>()
        buildDependencyTree(projectName, externalChildren, externalVersions, allPackages, externalDependencies)

        val testChildren = mapParentsToChildren("test")
        val testVersions = mapNamesToVersions("test")
        val testDependencies = sortedSetOf<PackageReference>()
        buildDependencyTree(projectName, testChildren, testVersions, allPackages, testDependencies)

        val benchChildren = mapParentsToChildren("bench")
        val benchVersions = mapNamesToVersions("bench")
        val benchDependencies = sortedSetOf<PackageReference>()
        buildDependencyTree(projectName, benchChildren, benchVersions, allPackages, benchDependencies)

        val scopes = sortedSetOf(
                Scope("external", true, externalDependencies),
                Scope("test", false, testDependencies),
                Scope("bench", false, benchDependencies)
        )

        val project = Project(
                id = Identifier(
                        provider = toString(),
                        namespace = "",
                        name = projectName,
                        version = ""
                ),
                definitionFilePath = VersionControlSystem.getPathToRoot(definitionFile) ?: "",
                declaredLicenses = sortedSetOf(),
                vcs = VcsInfo.EMPTY,
                vcsProcessed = processProjectVcs(workingDir, homepageUrl = ""),
                homepageUrl = "",
                scopes = scopes
        )

        // Stack does not support lock files, so hard-code "allowDynamicVersions" to "true".
        return ProjectAnalyzerResult(true, project,
                allPackages.map { it.toCuratedPackage() }.toSortedSet())
    }

    private fun buildDependencyTree(parentName: String,
                                    childMap: Map<String, List<String>>, versionMap: Map<String, String>,
                                    allPackages: SortedSet<Package>, dependencies: SortedSet<PackageReference>) {
        childMap[parentName]?.forEach { childName ->
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
    }
}
