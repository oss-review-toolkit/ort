/*
 * Copyright (C) 2025 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.packagemanagers.gleam

import java.io.File

import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.PackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManagerResult
import org.ossreviewtoolkit.downloader.VcsHost
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.utils.DependencyGraphBuilder
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.utils.common.div

private const val GLEAM_TOML = "gleam.toml"
private const val MANIFEST_TOML = "manifest.toml"
private const val SCOPE_DEPENDENCIES = "dependencies"
private const val SCOPE_DEV_DEPENDENCIES = "dev-dependencies"

/**
 * The [Gleam](https://gleam.run/) package manager for Gleam.
 *
 * This package manager parses `gleam.toml` (project manifest) and `manifest.toml` (lockfile) files
 * to extract dependency information. Gleam packages are hosted on [Hex](https://hex.pm/).
 */
@OrtPlugin(
    displayName = "Gleam",
    description = "The package manager for Gleam.",
    factory = PackageManagerFactory::class
)
class Gleam internal constructor(
    override val descriptor: PluginDescriptor = GleamFactory.descriptor,
    private val hexApiClientFactory: () -> HexApiClient
) : PackageManager("Gleam") {
    constructor(descriptor: PluginDescriptor = GleamFactory.descriptor) : this(descriptor, ::HexApiClient)

    override val globsForDefinitionFiles = listOf(GLEAM_TOML)

    private val dependencyHandler = GleamDependencyHandler()
    private val graphBuilder = DependencyGraphBuilder(dependencyHandler)
    private lateinit var projectDirs: Set<File>

    override fun beforeResolution(
        analysisRoot: File,
        definitionFiles: List<File>,
        analyzerConfig: AnalyzerConfiguration
    ) {
        super.beforeResolution(analysisRoot, definitionFiles, analyzerConfig)

        projectDirs = definitionFiles.mapTo(mutableSetOf()) { it.parentFile.canonicalFile }
    }

    override fun mapDefinitionFiles(
        analysisRoot: File,
        definitionFiles: List<File>,
        analyzerConfig: AnalyzerConfiguration
    ): List<File> {
        val projectFiles = definitionFiles.toMutableList()

        // Ignore definition files from build directories that reside next to other definition files,
        // to avoid dependency gleam.toml files from being recognized as projects.
        var index = 0
        while (index < projectFiles.size - 1) {
            val projectFile = projectFiles[index++]
            val buildDir = projectFile.resolveSibling("build")
            projectFiles.subList(index, projectFiles.size).removeAll { it.startsWith(buildDir) }
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
        val manifestFile = workingDir / MANIFEST_TOML

        val gleamToml = parseGleamToml(definitionFile)
        val hasDependencies = gleamToml.dependencies.isNotEmpty() || gleamToml.devDependencies.isNotEmpty()

        requireLockfile(analysisRoot, workingDir, analyzerConfig.allowDynamicVersions) {
            manifestFile.isFile || !hasDependencies
        }

        val hexClient = hexApiClientFactory()
        val issues = mutableListOf<Issue>()

        val project = createProject(definitionFile, gleamToml)

        val manifest = if (manifestFile.isFile) {
            parseManifest(manifestFile)
        } else if (!hasDependencies) {
            GleamManifest.EMPTY
        } else {
            issues += Issue(
                source = projectType,
                message = "Dependencies were resolved dynamically as no lockfile was present. " +
                    "Only the latest matching versions of direct dependencies were resolved without " +
                    "transitive dependency resolution. The results are not reproducible. " +
                    "Consider running 'gleam deps download' to generate a manifest.toml lockfile.",
                severity = Severity.WARNING
            )
            GleamManifest.EMPTY
        }

        val context = GleamProjectContext(
            hexClient = hexClient,
            project = project,
            analysisRoot = analysisRoot,
            workingDir = workingDir,
            manifest = manifest,
            projectDirs = projectDirs
        )

        val deps = gleamToml.dependencies.map { (name, element) ->
            DependencyPackageInfo(name, GleamToml.Dependency.fromToml(element))
        }

        val devDeps = gleamToml.devDependencies.map { (name, element) ->
            DependencyPackageInfo(name, GleamToml.Dependency.fromToml(element))
        }

        dependencyHandler.setContext(context)

        if (deps.isNotEmpty()) graphBuilder.addDependencies(project.id, SCOPE_DEPENDENCIES, deps)

        if (devDeps.isNotEmpty()) {
            graphBuilder.addDependencies(project.id, SCOPE_DEV_DEPENDENCIES, devDeps)
        }

        val projectWithScopes = project.copy(scopeNames = graphBuilder.scopesFor(project.id))

        return listOf(ProjectAnalyzerResult(projectWithScopes, emptySet(), issues))
    }

    override fun createPackageManagerResult(projectResults: Map<File, List<ProjectAnalyzerResult>>) =
        PackageManagerResult(projectResults, graphBuilder.build(), graphBuilder.packages())

    private fun createProject(definitionFile: File, gleamToml: GleamToml): Project {
        val workingDir = definitionFile.parentFile

        val projectId = Identifier(
            type = projectType,
            namespace = "",
            name = gleamToml.name,
            version = gleamToml.version
        )

        val vcsUrl = gleamToml.repository?.toUrl().orEmpty()
        val vcs = VcsHost.parseUrl(vcsUrl)
        val vcsProcessed = processProjectVcs(workingDir, vcs, vcsUrl)

        return Project(
            id = projectId,
            definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
            authors = emptySet(),
            declaredLicenses = gleamToml.licences.toSet(),
            vcs = vcs,
            vcsProcessed = vcsProcessed,
            homepageUrl = gleamToml.findHomepageUrl().ifEmpty { vcsUrl },
            scopeNames = emptySet()
        )
    }
}
