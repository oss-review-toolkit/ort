/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 * Copyright (C) 2021 Bosch.IO GmbH
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

import com.fasterxml.jackson.databind.JsonNode

import com.vdurmont.semver4j.Requirement

import java.io.File
import java.util.SortedSet
import java.util.Stack

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
import org.ossreviewtoolkit.model.jsonMapper
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.fieldNamesOrEmpty
import org.ossreviewtoolkit.utils.common.fieldsOrEmpty
import org.ossreviewtoolkit.utils.common.stashDirectories
import org.ossreviewtoolkit.utils.common.textValueOrEmpty

/**
 * The [Bower](https://bower.io/) package manager for JavaScript.
 */
@Suppress("TooManyFunctions")
class Bower(
    name: String,
    analysisRoot: File,
    analyzerConfig: AnalyzerConfiguration,
    repoConfig: RepositoryConfiguration
) : PackageManager(name, analysisRoot, analyzerConfig, repoConfig), CommandLineTool {
    companion object {
        private const val SCOPE_NAME_DEPENDENCIES = "dependencies"
        private const val SCOPE_NAME_DEV_DEPENDENCIES = "devDependencies"

        private fun parsePackageId(node: JsonNode) =
            Identifier(
                type = "Bower",
                namespace = "",
                name = node["pkgMeta"]["name"].textValueOrEmpty(),
                version = node["pkgMeta"]["version"].textValueOrEmpty()
            )

        private fun parseRepositoryType(node: JsonNode) =
            VcsType(node["pkgMeta"]["repository"]?.get("type").textValueOrEmpty())

        private fun parseRepositoryUrl(node: JsonNode) =
            node["pkgMeta"]["repository"]?.get("url")?.textValue()
                ?: node["pkgMeta"]["_source"].textValueOrEmpty()

        private fun parseRevision(node: JsonNode): String =
            node["pkgMeta"]["_resolution"]?.get("commit")?.textValue()
                ?: node["pkgMeta"]["_resolution"]?.get("tag").textValueOrEmpty()

        private fun parseVcsInfo(node: JsonNode) =
            VcsInfo(
                type = parseRepositoryType(node),
                url = parseRepositoryUrl(node),
                revision = parseRevision(node)
            )

        private fun parseDeclaredLicenses(node: JsonNode): SortedSet<String> =
            sortedSetOf<String>().apply {
                val license = node["pkgMeta"]["license"].textValueOrEmpty()
                if (license.isNotEmpty()) add(license)
            }

        /**
         * Parse information about the author. According to https://github.com/bower/spec/blob/master/json.md#authors,
         * there are two formats to specify the authors of a package (similar to NPM). The difference is that the
         * strings or objects are inside an array.
         */
        private fun parseAuthors(node: JsonNode): SortedSet<String> =
            sortedSetOf<String>().apply {
                node["pkgMeta"]["authors"]?.mapNotNull { authorNode ->
                    when {
                        authorNode.isObject -> authorNode["name"]?.textValue()
                        authorNode.isTextual -> parseAuthorString(authorNode.textValue(), '<', '(')
                        else -> null
                    }
                }?.let { addAll(it) }
            }

        private fun parsePackage(node: JsonNode) =
            Package(
                id = parsePackageId(node),
                authors = parseAuthors(node),
                declaredLicenses = parseDeclaredLicenses(node),
                description = node["pkgMeta"]["description"].textValueOrEmpty(),
                homepageUrl = node["pkgMeta"]["homepage"].textValueOrEmpty(),
                binaryArtifact = RemoteArtifact.EMPTY,
                sourceArtifact = RemoteArtifact.EMPTY, // TODO: implement me!
                vcs = parseVcsInfo(node)
            )

        private fun getDependencyNodes(node: JsonNode): Sequence<JsonNode> =
            node["dependencies"].fieldsOrEmpty().asSequence().map { it.value }

        private fun parsePackages(node: JsonNode): Map<String, Package> {
            val result = mutableMapOf<String, Package>()

            val stack = Stack<JsonNode>()
            stack += getDependencyNodes(node)

            while (!stack.empty()) {
                val currentNode = stack.pop()
                val pkg = parsePackage(currentNode)
                result["${pkg.id.name}:${pkg.id.version}"] = pkg

                stack += getDependencyNodes(currentNode)
            }

            return result
        }

        private fun hasCompleteDependencies(node: JsonNode, scopeName: String): Boolean {
            val dependencyKeys = node["dependencies"].fieldNamesOrEmpty().asSequence().toSet()
            val dependencyRefKeys = node["pkgMeta"][scopeName].fieldNamesOrEmpty().asSequence().toSet()

            return dependencyKeys.containsAll(dependencyRefKeys)
        }

        private fun dependencyKeyOf(node: JsonNode): String? {
            // As non-null dependency keys are supposed to define an equivalence relation for parsing 'missing' nodes,
            // only the name and version attributes can be used. Typically those attributes should be not null
            // however in particular for root projects the null case also happens.
            val name = node["pkgMeta"]["name"].textValueOrEmpty()
            val version = node["pkgMeta"]["version"].textValueOrEmpty()
            return "$name:$version".takeUnless { name.isEmpty() || version.isEmpty() }
        }

        private fun getNodesWithCompleteDependencies(node: JsonNode): Map<String, JsonNode> {
            val result = mutableMapOf<String, JsonNode>()

            val stack = Stack<JsonNode>().apply { push(node) }
            while (!stack.empty()) {
                val currentNode = stack.pop()

                dependencyKeyOf(currentNode)?.let { key ->
                    if (hasCompleteDependencies(node, SCOPE_NAME_DEPENDENCIES) &&
                        hasCompleteDependencies(node, SCOPE_NAME_DEV_DEPENDENCIES)
                    ) {
                        result[key] = currentNode
                    }
                }

                stack += getDependencyNodes(currentNode)
            }

            return result
        }

        private fun parseDependencyTree(
            node: JsonNode,
            scopeName: String,
            alternativeNodes: Map<String, JsonNode> = getNodesWithCompleteDependencies(node)
        ): SortedSet<PackageReference> {
            val result = mutableSetOf<PackageReference>()

            if (!hasCompleteDependencies(node, scopeName)) {
                // Bower leaves out a dependency entry for a child if there exists a similar node to its parent node
                // with the exact same name and resolved target. This makes it necessary to retrieve the information
                // about the subtree rooted at the parent from that other node containing the full dependency
                // information.
                // See https://github.com/bower/bower/blob/6bc778d/lib/core/Manager.js#L557 and below.
                val alternativeNode = checkNotNull(alternativeNodes[dependencyKeyOf(node)])
                return parseDependencyTree(alternativeNode, scopeName, alternativeNodes)
            }

            node["pkgMeta"][scopeName].fieldNamesOrEmpty().forEach {
                val childNode = node["dependencies"][it]
                val childScope = SCOPE_NAME_DEPENDENCIES
                val childDependencies = parseDependencyTree(childNode, childScope, alternativeNodes)
                val packageReference = PackageReference(
                    id = parsePackageId(childNode),
                    dependencies = childDependencies
                )
                result += packageReference
            }

            return result.toSortedSet()
        }
    }

    class Factory : AbstractPackageManagerFactory<Bower>("Bower") {
        override val globsForDefinitionFiles = listOf("bower.json")

        override fun create(
            analysisRoot: File,
            analyzerConfig: AnalyzerConfiguration,
            repoConfig: RepositoryConfiguration
        ) = Bower(managerName, analysisRoot, analyzerConfig, repoConfig)
    }

    override fun command(workingDir: File?) = if (Os.isWindows) "bower.cmd" else "bower"

    override fun getVersionRequirement(): Requirement = Requirement.buildIvy("[1.8.8,)")

    override fun beforeResolution(definitionFiles: List<File>) = checkVersion()

    override fun resolveDependencies(definitionFile: File, labels: Map<String, String>): List<ProjectAnalyzerResult> {
        val workingDir = definitionFile.parentFile

        stashDirectories(workingDir.resolve("bower_components")).use {
            installDependencies(workingDir)
            val dependenciesJson = listDependencies(workingDir)
            val rootNode = jsonMapper.readTree(dependenciesJson)
            val packages = parsePackages(rootNode)
            val dependenciesScope = Scope(
                name = SCOPE_NAME_DEPENDENCIES,
                dependencies = parseDependencyTree(rootNode, SCOPE_NAME_DEPENDENCIES)
            )
            val devDependenciesScope = Scope(
                name = SCOPE_NAME_DEV_DEPENDENCIES,
                dependencies = parseDependencyTree(rootNode, SCOPE_NAME_DEV_DEPENDENCIES)
            )

            val projectPackage = parsePackage(rootNode)
            val project = Project(
                id = projectPackage.id,
                definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
                authors = projectPackage.authors,
                declaredLicenses = projectPackage.declaredLicenses,
                vcs = projectPackage.vcs,
                vcsProcessed = processProjectVcs(workingDir, projectPackage.vcs, projectPackage.homepageUrl),
                homepageUrl = projectPackage.homepageUrl,
                scopeDependencies = sortedSetOf(dependenciesScope, devDependenciesScope)
            )

            return listOf(ProjectAnalyzerResult(project, packages.values.toSortedSet()))
        }
    }

    private fun installDependencies(workingDir: File) = run(workingDir, "--allow-root", "install")

    private fun listDependencies(workingDir: File) = run(workingDir, "--allow-root", "list", "--json").stdout
}
