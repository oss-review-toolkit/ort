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

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature

import com.here.ort.analyzer.AbstractPackageManagerFactory
import com.here.ort.analyzer.PackageManager
import com.here.ort.downloader.VersionControlSystem
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
import com.here.ort.utils.fieldNamesOrEmpty
import com.here.ort.utils.log
import com.here.ort.utils.safeDeleteRecursively
import com.here.ort.utils.textValueOrEmpty

import com.vdurmont.semver4j.Requirement

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.SortedSet
import java.util.Stack

/**
 * The Bower package manager for JavaScript, see https://bower.io/.
 */
class Bower(analyzerConfig: AnalyzerConfiguration, repoConfig: RepositoryConfiguration) :
        PackageManager(analyzerConfig, repoConfig), CommandLineTool {

    class Factory : AbstractPackageManagerFactory<Bower>() {
        override val globsForDefinitionFiles = listOf("bower.json")

        override fun create(analyzerConfig: AnalyzerConfiguration, repoConfig: RepositoryConfiguration) =
                Bower(analyzerConfig, repoConfig)
    }

    companion object {
        /* We do not actually depend on any features specific to Bower version 1.8.2, but we still want to
          stick to fixed versions to be sure to get consistent results. */
        private const val REQUIRED_BOWER_VERSION = "1.8.2"
        private const val SCOPE_NAME_DEPENDENCIES = "dependencies"
        private const val SCOPE_NAME_DEV_DEPENDENCIES = "devDependencies"
        private const val PROVIDER_NAME = "Bower"

        private fun extractPackageId(node: JsonNode) = Identifier(
                provider = PROVIDER_NAME,
                namespace = "",
                name = node["pkgMeta"]["name"].textValueOrEmpty(),
                version = node["pkgMeta"]["version"].textValueOrEmpty()
        )

        private fun extractRepositoryType(node: JsonNode): String {
            var result = ""

            val repositoryNode = node["pkgMeta"]["repository"]
            if ( repositoryNode != null ) {
                result = repositoryNode["type"].textValueOrEmpty()
            }

            return result
        }

        private fun extractRepositoryUrl(node: JsonNode): String {
            var result = ""

            val repositoryNode = node["pkgMeta"]["repository"]
            if (repositoryNode != null) {
                result = repositoryNode["url"].textValueOrEmpty()
            }

            if (result.isEmpty()) {
                result = node["pkgMeta"]["_source"].textValueOrEmpty()
            }

            return result
        }

        private fun extractRevision(node: JsonNode): String {
            val resolutionNode = node["pkgMeta"]["_resolution"]
            if (resolutionNode == null) {
                return ""
            }

            var result = resolutionNode["commit"].textValueOrEmpty()

            if (result.isEmpty()) {
                result = resolutionNode["tag"].textValueOrEmpty()
            }

            return result
        }

        private fun extractVcsInfo(node: JsonNode): VcsInfo {
            return VcsInfo(
                    type = extractRepositoryType(node),
                    url = extractRepositoryUrl(node),
                    revision = extractRevision(node)
            )
        }

        private fun extractDeclaredLicenses(node: JsonNode): SortedSet<String> {
            val result = mutableSetOf<String>()

            val license = node["pkgMeta"]["license"].textValueOrEmpty()
            if (!license.isEmpty()) {
                result.add(license)
            }

            return result.toSortedSet()
        }

        private fun extractPackage(node: JsonNode ): Package {
            val vcsInfo = extractVcsInfo(node)
            return Package(
                    id = extractPackageId(node),
                    declaredLicenses = extractDeclaredLicenses(node),
                    description = node["pkgMeta"]["description"].textValueOrEmpty(),
                    homepageUrl = node["pkgMeta"]["homepage"].textValueOrEmpty(),
                    binaryArtifact = RemoteArtifact.EMPTY,
                    sourceArtifact = RemoteArtifact.EMPTY, // TODO: implement me!
                    vcs = vcsInfo,
                    vcsProcessed = vcsInfo.normalize()
            )
        }

        private fun extractPackages(node: JsonNode): Map<String, Package> {
            val result = mutableMapOf<String, Package>()

            val stack = Stack<JsonNode>()
            stack.push(node)

            while (!stack.empty()) {
                val currentNode = stack.pop()
                val pkg = extractPackage(currentNode)
                result.put("${pkg.id.name}:${pkg.id.version}", pkg)

                val children = currentNode["dependencies"].fields().asSequence().map { it.value }
                stack.addAll(children)
            }

            return result
        }

        private fun extractDependencyTree(node: JsonNode, scopeName: String): SortedSet<PackageReference> {
            val result = mutableSetOf<PackageReference>()

            node["pkgMeta"][scopeName].fieldNamesOrEmpty().forEach {
                val childNode = node["dependencies"][it]
                val childScope = SCOPE_NAME_DEPENDENCIES
                val childDependencies = extractDependencyTree(childNode, childScope)
                val packageRefence = PackageReference(
                        id = extractPackageId(childNode),
                        dependencies = childDependencies
                )
                result.add(packageRefence)
            }

            return result.toSortedSet()
        }
    }

    private val stashedDirectories = HashMap<File, File>()

    override fun toString(): String {
        return PROVIDER_NAME
    }

    override fun command(workingDir: File?) = if (OS.isWindows) "bower.cmd" else "bower"

    override fun prepareResolution(definitionFiles: List<File>): List<File> {
        jsonMapper.enable(SerializationFeature.INDENT_OUTPUT)
        checkVersion(
                Requirement.buildNPM(REQUIRED_BOWER_VERSION),
                ignoreActualVersion = analyzerConfig.ignoreToolVersions
        )
        return definitionFiles
    }

    override fun resolveDependencies(definitionFile: File): ProjectAnalyzerResult? {
        log.info( "Resolving dependencies for: $definitionFile" )

        val workingDir = definitionFile.parentFile
        val bowerComponentsDir = File(workingDir, "bower_components")
        var result: ProjectAnalyzerResult?

        try {
            stashDir(bowerComponentsDir)

            installDependencies(workingDir)
            val dependenciesJson = listDependencies(workingDir)
            val rootNode = jsonMapper.readTree(dependenciesJson)
            val packages = extractPackages(rootNode)
            val dependenciesScope = Scope(
                    name = SCOPE_NAME_DEPENDENCIES,
                    dependencies = extractDependencyTree(rootNode, SCOPE_NAME_DEPENDENCIES)
            )
            val devDependenciesScope = Scope(
                    name = SCOPE_NAME_DEV_DEPENDENCIES,
                    dependencies = extractDependencyTree(rootNode, SCOPE_NAME_DEV_DEPENDENCIES)
            )

            val projectPackage = extractPackage(rootNode)
            val project = Project(
                    id = projectPackage.id,
                    definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
                    declaredLicenses = projectPackage.declaredLicenses,
                    vcs = projectPackage.vcs,
                    vcsProcessed = processProjectVcs(workingDir, projectPackage.vcs, projectPackage.homepageUrl),
                    homepageUrl = projectPackage.homepageUrl,
                    scopes = sortedSetOf(dependenciesScope, devDependenciesScope)
            )

            result = ProjectAnalyzerResult(
                    project = project,
                    packages = packages.map { it.value.toCuratedPackage() }.toSortedSet()
            )
        } finally {
            bowerComponentsDir.safeDeleteRecursively()
            unstashDir(bowerComponentsDir)
        }

        return result
    }

    private fun stashDir(originalDir: File) {
        if (!originalDir.isDirectory) return

        val tempDir = createTempDir("analyzer", ".tmp", originalDir)
        log.info("Temporarily moving directory from '$originalDir' to '$tempDir'.")

        Files.move(originalDir.toPath(), tempDir.toPath(), StandardCopyOption.ATOMIC_MOVE)
        stashedDirectories.put(originalDir, tempDir)
    }

    private fun unstashDir(originalDir: File) {
        val tempDir = stashedDirectories[originalDir]
        tempDir ?: return

        log.info("Moving back directory from: '$tempDir' to '$originalDir'.")

        Files.move(tempDir.toPath(), originalDir.toPath(), StandardCopyOption.ATOMIC_MOVE)
        stashedDirectories.remove(originalDir)
    }

    private fun installDependencies(workingDir: File) {
        run(workingDir, "install")
    }

    private fun listDependencies(workingDir: File): String {
        return run(workingDir, "list", "--json").stdout
    }
}
