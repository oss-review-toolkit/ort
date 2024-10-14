/*
 * Copyright (C) 2024 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.node.pnpm

import java.io.File

import org.ossreviewtoolkit.analyzer.AbstractPackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.PackageManager.Companion.processPackageVcs
import org.ossreviewtoolkit.analyzer.PackageManagerResult
import org.ossreviewtoolkit.downloader.VcsHost
import org.ossreviewtoolkit.model.DependencyGraph
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.utils.DependencyGraphBuilder
import org.ossreviewtoolkit.plugins.packagemanagers.node.parsePackageJson
import org.ossreviewtoolkit.plugins.packagemanagers.node.parseProject
import org.ossreviewtoolkit.plugins.packagemanagers.node.utils.NON_EXISTING_SEMVER
import org.ossreviewtoolkit.plugins.packagemanagers.node.utils.NodePackageManager
import org.ossreviewtoolkit.plugins.packagemanagers.node.utils.NpmDetection
import org.ossreviewtoolkit.plugins.packagemanagers.node.utils.expandNpmShortcutUrl
import org.ossreviewtoolkit.plugins.packagemanagers.node.utils.fixNpmDownloadUrl
import org.ossreviewtoolkit.plugins.packagemanagers.node.utils.mapNpmLicenses
import org.ossreviewtoolkit.plugins.packagemanagers.node.utils.parseNpmAuthor
import org.ossreviewtoolkit.plugins.packagemanagers.node.utils.parseNpmVcsInfo
import org.ossreviewtoolkit.plugins.packagemanagers.node.utils.splitNpmNamespaceAndName
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.stashDirectories

import org.semver4j.RangesList
import org.semver4j.RangesListFactory

/**
 * The [fast, disk space efficient package manager](https://pnpm.io/).
 */
class Pnpm(
    name: String,
    analysisRoot: File,
    analyzerConfig: AnalyzerConfiguration,
    repoConfig: RepositoryConfiguration
) : PackageManager(name, analysisRoot, analyzerConfig, repoConfig), CommandLineTool {
    class Factory : AbstractPackageManagerFactory<Pnpm>("PNPM") {
        override val globsForDefinitionFiles = listOf("package.json", "pnpm-lock.yaml")

        override fun create(
            analysisRoot: File,
            analyzerConfig: AnalyzerConfiguration,
            repoConfig: RepositoryConfiguration
        ) = Pnpm(type, analysisRoot, analyzerConfig, repoConfig)
    }

    private val handler = PnpmDependencyHandler()
    private val graphBuilder by lazy { DependencyGraphBuilder(handler) }

    override fun resolveDependencies(definitionFile: File, labels: Map<String, String>): List<ProjectAnalyzerResult> =
        stashDirectories(definitionFile.resolveSibling("node_modules")).use {
            resolveDependencies(definitionFile)
        }

    private fun resolveDependencies(definitionFile: File): List<ProjectAnalyzerResult> {
        val workingDir = definitionFile.parentFile
        installDependencies(workingDir)

        val workspaceModuleDirs = getWorkspaceModuleDirs(workingDir)
        handler.setWorkspaceModuleDirs(workspaceModuleDirs)

        val moduleInfosForScope = Scope.entries.associateWith { scope -> listModules(workingDir, scope) }

        return workspaceModuleDirs.map { projectDir ->
            val project = parseProject(projectDir.resolve("package.json"), analysisRoot, managerName)

            val scopeNames = Scope.entries.mapTo(mutableSetOf()) { scope ->
                val scopeName = scope.toString()
                val qualifiedScopeName = DependencyGraph.qualifyScope(project, scope.toString())
                val moduleInfo = moduleInfosForScope.getValue(scope).single { it.path == projectDir.absolutePath }

                moduleInfo.getScopeDependencies(scope).forEach { dependency ->
                    graphBuilder.addDependency(qualifiedScopeName, dependency)
                }

                scopeName
            }

            ProjectAnalyzerResult(
                project = project.copy(scopeNames = scopeNames),
                packages = emptySet(),
                issues = emptyList()
            )
        }
    }

    override fun command(workingDir: File?) =
        if (Os.isWindows) {
            "pnpm.cmd"
        } else {
            "/home/frank/.nvm/versions/node/v18.12.1/bin/node /home/frank/.nvm/versions/node/v18.12.1/bin/pnpm"
        }

    private fun getWorkspaceModuleDirs(workingDir: File): Set<File> {
        val json = run(workingDir, "list", "--json", "--only-projects", "--recursive").stdout

        return parsePnpmList(json).mapTo(mutableSetOf()) { File(it.path) }
    }

    private fun listModules(workingDir: File, scope: Scope): List<ModuleInfo> {
        val scopeOption = when (scope) {
            Scope.DEPENDENCIES -> "--prod"
            Scope.DEV_DEPENDENCIES -> "--dev"
        }

        val json = run(workingDir, "list", "--json", "--recursive", "--depth", "10000", scopeOption).stdout

        return parsePnpmList(json)
    }

    override fun createPackageManagerResult(projectResults: Map<File, List<ProjectAnalyzerResult>>) =
        PackageManagerResult(projectResults, graphBuilder.build(), graphBuilder.packages())

    override fun getVersionRequirement(): RangesList = RangesListFactory.create("5.* - 9.*")

    override fun mapDefinitionFiles(definitionFiles: List<File>) =
        NpmDetection(definitionFiles).filterApplicable(NodePackageManager.PNPM)

    private fun installDependencies(workingDir: File) =
        run(
            "install",
            "--ignore-pnpmfile",
            "--ignore-scripts",
            "--frozen-lockfile", // Use the existing lockfile instead of updating an outdated one.
            workingDir = workingDir
        )

    override fun beforeResolution(definitionFiles: List<File>) =
        // We do not actually depend on any features specific to a PNPM version, but we still want to stick to a
        // fixed major version to be sure to get consistent results.
        checkVersion()
}

private enum class Scope(val descriptor: String) {
    DEPENDENCIES("dependencies"),
    DEV_DEPENDENCIES("devDependencies");

    override fun toString(): String = descriptor
}

private fun ModuleInfo.getScopeDependencies(scope: Scope) =
    when (scope) {
        Scope.DEPENDENCIES -> buildList {
            addAll(dependencies.values)
            addAll(optionalDependencies.values)
        }

        Scope.DEV_DEPENDENCIES -> devDependencies.values.toList()
    }

internal fun parsePackage(packageJsonFile: File): Package {
    val packageJson = parsePackageJson(packageJsonFile)

    // The "name" and "version" fields are only required if the package is going to be published, otherwise they are
    // optional, see
    // - https://docs.npmjs.com/cli/v10/configuring-npm/package-json#name
    // - https://docs.npmjs.com/cli/v10/configuring-npm/package-json#version
    // So, projects analyzed by ORT might not have these fields set.
    val rawName = packageJson.name.orEmpty() // TODO: Fall back to a generated name if the name is unset.
    val (namespace, name) = splitNpmNamespaceAndName(rawName)
    val version = packageJson.version ?: NON_EXISTING_SEMVER

    val declaredLicenses = packageJson.licenses.mapNpmLicenses()
    val authors = parseNpmAuthor(packageJson.authors.firstOrNull()) // TODO: parse all authors.

    var description = packageJson.description.orEmpty()
    var homepageUrl = packageJson.homepage.orEmpty()

    // Note that all fields prefixed with "_" are considered private to NPM and should not be relied on.
    var downloadUrl = expandNpmShortcutUrl(packageJson.resolved.orEmpty()).ifEmpty {
        // If the normalized form of the specified dependency contains a URL as the version, expand and use it.
        val fromVersion = packageJson.from.orEmpty().substringAfterLast('@')
        expandNpmShortcutUrl(fromVersion).takeIf { it != fromVersion }.orEmpty()
    }

    var hash = Hash.create(packageJson.integrity.orEmpty())

    var vcsFromPackage = parseNpmVcsInfo(packageJson)

    val id = Identifier("NPM", namespace, name, version)

    downloadUrl = downloadUrl.fixNpmDownloadUrl()

    val vcsFromDownloadUrl = VcsHost.parseUrl(downloadUrl)
    if (vcsFromDownloadUrl.url != downloadUrl) {
        vcsFromPackage = vcsFromPackage.merge(vcsFromDownloadUrl)
    }

    val module = Package(
        id = id,
        authors = authors,
        declaredLicenses = declaredLicenses,
        description = description,
        homepageUrl = homepageUrl,
        binaryArtifact = RemoteArtifact.EMPTY,
        sourceArtifact = RemoteArtifact(
            url = VcsHost.toArchiveDownloadUrl(vcsFromDownloadUrl) ?: downloadUrl,
            hash = hash
        ),
        vcs = vcsFromPackage,
        vcsProcessed = processPackageVcs(vcsFromPackage, homepageUrl)
    )

    require(module.id.name.isNotEmpty()) {
        "Generated package info for '${id.toCoordinates()}' has no name."
    }

    require(module.id.version.isNotEmpty()) {
        "Generated package info for '${id.toCoordinates()}' has no version."
    }

    return module
}
