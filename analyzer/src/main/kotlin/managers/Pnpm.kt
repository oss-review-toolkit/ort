/*
 * Copyright (C) 2022 Bosch.IO GmbH
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

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.readValue

import java.io.File

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking

import org.ossreviewtoolkit.analyzer.AbstractPackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManagerResult
import org.ossreviewtoolkit.analyzer.managers.utils.hasPnpmLockFile
import org.ossreviewtoolkit.analyzer.managers.utils.mapDefinitionFilesForPnpm
import org.ossreviewtoolkit.downloader.VcsHost
import org.ossreviewtoolkit.model.DependencyGraph
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.OrtIssue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageLinkage
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.jsonMapper
import org.ossreviewtoolkit.model.utils.DependencyGraphBuilder
import org.ossreviewtoolkit.model.utils.DependencyHandler
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.ProcessCapture
import org.ossreviewtoolkit.utils.common.textValueOrEmpty

class Pnpm(
    name: String,
    analysisRoot: File,
    analyzerConfig: AnalyzerConfiguration,
    repoConfig: RepositoryConfiguration
) : Npm(name, analysisRoot, analyzerConfig, repoConfig), CommandLineTool {
    class Factory : AbstractPackageManagerFactory<Pnpm>("PNPM") {
        override val globsForDefinitionFiles = listOf("package.json", "pnpm-lock.yaml")

        override fun create(
            analysisRoot: File,
            analyzerConfig: AnalyzerConfiguration,
            repoConfig: RepositoryConfiguration
        ) = Pnpm(managerName, analysisRoot, analyzerConfig, repoConfig)
    }

    // TODO: Workaround for artifactory might be required, here as well

    private val graphBuilder = DependencyGraphBuilder(PnpmDependencyHandler())

    override fun hasLockFile(projectDir: File) = hasPnpmLockFile(projectDir)

    override fun command(workingDir: File?) = if (Os.isWindows) "pnpm.cmd" else "pnpm"

    override fun mapDefinitionFiles(definitionFiles: List<File>) = mapDefinitionFilesForPnpm(definitionFiles).toList()

    override fun run(workingDir: File?, vararg args: String) =
        ProcessCapture(workingDir, *command(workingDir).split(' ').toTypedArray(), *args).requireSuccess()

    override fun createPackageManagerResult(projectResults: Map<File, List<ProjectAnalyzerResult>>) =
        PackageManagerResult(projectResults, graphBuilder.build(), graphBuilder.packages())

    override fun resolveDependencies(definitionFile: File, labels: Map<String, String>): List<ProjectAnalyzerResult> {
        val workingDir = definitionFile.parentFile

        run(workingDir, "install", "--ignore-pnpmfile", "--ignore-scripts", "--frozen-lockfile")

        // Get the dependency tree as shown by `pnpm list`.
        val listProcess = run(workingDir, "list", "--recursive", "--depth=${Int.MAX_VALUE}", "--json")
        val result = listProcess.stdout
        val rawModuleInfos = jsonMapper.readValue<List<RawModuleInfo>>(result).fixProjectDependencies()

        // Get a flat list of all dependencies for `pnpm view`.
        val flatPackageDependencyInfos = mutableListOf<RawDependencyInfo>()
        rawModuleInfos.forEach { collectFlatDependencyInfo(it.dependencies.values, flatPackageDependencyInfos) }
        rawModuleInfos.forEach {
            collectFlatDependencyInfo(it.devDependencies.values, flatPackageDependencyInfos)
        }
        rawModuleInfos.forEach { collectFlatDependencyInfo(it.optionalDependencies.values, flatPackageDependencyInfos) }

        // Create the [Package]s by using `pnpm view`.
        val packages = runBlocking(Dispatchers.IO) {
            flatPackageDependencyInfos.map { dependencyInfo ->
                async {
                    // TODO: Requesting the package information from the remote registry is very slow. To improve
                    //       performance this information could also be parsed from the package's package.json file,
                    //       while requesting the remote information only as a fallback.
                    val remotePackageDetails =
                        getRemotePackageDetails(workingDir, "${dependencyInfo.from}@${dependencyInfo.version}")

                    parseViewJson(remotePackageDetails)
                }
            }.awaitAll().associateBy { it.id }
        }

        graphBuilder.addPackages(packages.values)

        // Build the PnpmModuleInfo from the information in rawModuleInfos and packages.
        val pnpmModules = mutableMapOf<Identifier, MutableMap<String, MutableSet<PnpmModuleInfo>>>()
        rawModuleInfos.forEach { moduleInfo ->
            val id = getProjectIdentifier(moduleInfo.name, moduleInfo.version)

            val modulesById = pnpmModules.getOrPut(id) { mutableMapOf() }
            val dependencyModules = modulesById.getOrPut("dependencies") { mutableSetOf() }
            val devDependencyModules = modulesById.getOrPut("devDependencies") { mutableSetOf() }
            val optionalDependencyModules = modulesById.getOrPut("optionalDependencies") { mutableSetOf() }

            collectDependencyModuleInfos(moduleInfo.dependencies.values, packages, dependencyModules)
            collectDependencyModuleInfos(moduleInfo.devDependencies.values, packages, devDependencyModules)
            collectDependencyModuleInfos(moduleInfo.optionalDependencies.values, packages, optionalDependencyModules)
        }

        val flatPnpmModules = mutableMapOf<Identifier, PnpmModuleInfo>()
        flattenPnpmModules(pnpmModules.values.flatMap { it.values }.flatten(), flatPnpmModules)

        // Add all direct dependencies to the dependency graph
        rawModuleInfos.forEach { moduleInfo ->
            val projectId = getProjectIdentifier(moduleInfo.name, moduleInfo.version)

            moduleInfo.dependencies.values.forEach { dependencyInfo ->
                val qualifiedScope = DependencyGraph.qualifyScope(projectId, "dependencies")
                addToDependencyGraph(dependencyInfo, qualifiedScope, flatPnpmModules)
            }

            moduleInfo.devDependencies.values.forEach { dependencyInfo ->
                val qualifiedScope = DependencyGraph.qualifyScope(projectId, "devDependencies")
                addToDependencyGraph(dependencyInfo, qualifiedScope, flatPnpmModules)
            }

            moduleInfo.optionalDependencies.values.forEach { dependencyInfo ->
                val qualifiedScope = DependencyGraph.qualifyScope(projectId, "optionalDependencies")
                addToDependencyGraph(dependencyInfo, qualifiedScope, flatPnpmModules)
            }
        }

        // Parse projects
        val projects = rawModuleInfos.map { moduleInfo ->
            val parsedProject = parseProject(File("${moduleInfo.path}/package.json"))

            parsedProject.copy(id = parsedProject.id.copy(type = "PNPM"))
        }.associateBy { it.id }

        // Construct the [ProjectAnalyzerResult] for every project.
        val analyzerResults = rawModuleInfos.map { module ->
            val projectId = getProjectIdentifier(module.name, module.version)
            val project = projects[projectId] ?: Project.EMPTY

            val projectDependencies = mutableListOf<Package>()
            collectPackagesForProject(module.dependencies.values, packages, projectDependencies)

            val nonEmptyScopeNames = pnpmModules[projectId]?.filter { it.value.isNotEmpty() }?.keys ?: emptySet()

            ProjectAnalyzerResult(
                project.copy(scopeNames = nonEmptyScopeNames.toSortedSet()),
                projectDependencies.toSortedSet()
            )
        }

        return analyzerResults
    }

    /**
     * Flatten the [pnpmModuleInfos] and store them in [flatPnpmModules], to be able to address them directly.
     */
    private fun flattenPnpmModules(
        pnpmModuleInfos: Collection<PnpmModuleInfo>,
        flatPnpmModules: MutableMap<Identifier, PnpmModuleInfo>
    ) {
        pnpmModuleInfos.forEach {
            flatPnpmModules[it.id] = it
            flattenPnpmModules(it.dependencies, flatPnpmModules)
        }
    }

    /**
     * Add the dependencies to the [graphBuilder]. Used for direct dependencies only.
     */
    private fun addToDependencyGraph(
        dependencyInfo: RawDependencyInfo,
        qualifiedScope: String,
        flatPnpmModules: Map<Identifier, PnpmModuleInfo>
    ) {
        val dependencyId = if (dependencyInfo.isProjectDependency) {
            getProjectIdentifier(dependencyInfo.from, dependencyInfo.version)
        } else {
            getPackageIdentifier(dependencyInfo.from, dependencyInfo.version)
        }

        flatPnpmModules[dependencyId]?.let {
            graphBuilder.addDependency(qualifiedScope, it)
        }
    }

    override fun getRemotePackageDetails(workingDir: File, packageName: String): JsonNode {
        val process = run(workingDir, "view", "--json", packageName)

        return jsonMapper.readTree(process.stdout)
    }

    private fun collectPackagesForProject(
        dependencyInfos: Collection<RawDependencyInfo>,
        packages: Map<Identifier, Package>,
        result: MutableList<Package>
    ) {
        dependencyInfos.forEach { dependencyInfo ->
            packages[getPackageIdentifier(dependencyInfo.from, dependencyInfo.version)]?.let { result += it }

            collectPackagesForProject(dependencyInfo.dependencies.values, packages, result)
        }
    }

    /**
     * Flatten the [rawDependencyInfos] and store them in [result], to be able to address them directly.
     */
    private fun collectFlatDependencyInfo(
        rawDependencyInfos: Collection<RawDependencyInfo>,
        result: MutableList<RawDependencyInfo>
    ) {
        rawDependencyInfos.forEach { dependencyInfo ->
            if (!dependencyInfo.isProjectDependency) {
                result += dependencyInfo.copy(dependencies = mutableMapOf())
            }

            dependencyInfo.dependencies.values.forEach { inner ->
                collectFlatDependencyInfo(listOf(inner), result)
            }
        }
    }

    /**
     * Build the [PnpmModuleInfo]s required for the dependency graph with the dependency tree information from
     * [dependencyInfos] and the package information from [packages]. Optionally, these module informations are stored
     * in [modules] to be able to retain scope information.
     */
    private fun collectDependencyModuleInfos(
        dependencyInfos: Collection<RawDependencyInfo>,
        packages: Map<Identifier, Package>,
        modules: MutableSet<PnpmModuleInfo> = mutableSetOf()
    ): Set<PnpmModuleInfo> = dependencyInfos.map { dependencyInfo ->
        val identifier = if (dependencyInfo.isProjectDependency) {
            getProjectIdentifier(dependencyInfo.from, dependencyInfo.version)
        } else {
            getPackageIdentifier(dependencyInfo.from, dependencyInfo.version)
        }
        val modulePackage = packages[identifier]

        PnpmModuleInfo(
            id = identifier,
            pkg = modulePackage,
            dependencies = collectDependencyModuleInfos(dependencyInfo.dependencies.values, packages)
        ).also { modules += it }
    }.toSet()

    /**
     * Construct the [Package] from the package [details] as given in `pnpm view name@version --json` command.
     */
    private fun parseViewJson(details: JsonNode): Package {
        val rawName = details["name"].textValueOrEmpty()
        val authors = parseAuthors(details)
        val declaredLicenses = parseLicenses(details)
        val version = details["version"].textValueOrEmpty()
        val description = details["description"].textValueOrEmpty()
        val homepageUrl = details["homepage"].textValueOrEmpty()
        val vcsFromPackage = parseVcsInfo(details)
        var downloadUrl = ""
        var hash: Hash = Hash.NONE
        details["dist"]?.let { dist ->
            downloadUrl = dist["tarball"].textValueOrEmpty()
            hash = Hash.create(dist["shasum"].textValueOrEmpty())
        }
        val vcsFromDownloadUrl = VcsHost.parseUrl(downloadUrl)

        return Package(
            id = getPackageIdentifier(rawName, version),
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
    }

    private fun getIdentifier(type: String, rawName: String, version: String): Identifier {
        val (namespace, name) = splitNamespaceAndName(rawName)

        return Identifier(type, namespace, name, version)
    }

    private fun getProjectIdentifier(rawName: String, version: String) = getIdentifier(managerName, rawName, version)

    private fun getPackageIdentifier(rawName: String, version: String) = getIdentifier("NPM", rawName, version)

    /**
     * The data structure as given from `pnpm list`.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class RawModuleInfo(
        val name: String,
        val version: String,
        val path: String,
        val dependencies: Map<String, RawDependencyInfo> = mapOf(),
        @JsonAlias("devDependencies")
        val devDependencies: Map<String, RawDependencyInfo> = mapOf(),
        @JsonAlias("optionalDependencies")
        val optionalDependencies: Map<String, RawDependencyInfo> = mapOf()
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class RawDependencyInfo(
        val from: String,
        var version: String,
        val resolved: String?,
        var dependencies: Map<String, RawDependencyInfo> = mapOf(),
        @JsonIgnore
        val isProjectDependency: Boolean = resolved == null
    )

    /**
     * Adjust the data structure given from `pnpm list` by project dependency references with their actual version and
     * dependencies.
     */
    private fun List<RawModuleInfo>.fixProjectDependencies(): List<RawModuleInfo> {
        // Find all project dependencies.
        val projectDependencies = flatMap { rawModuleInfo ->
            rawModuleInfo.dependencies.values.filter { it.isProjectDependency } +
                    rawModuleInfo.devDependencies.values.filter { it.isProjectDependency } +
                    rawModuleInfo.optionalDependencies.values.filter { it.isProjectDependency }
        }

        // Fix the versions and dependencies of the project dependencies.
        forEach { rawModuleInfo ->
            projectDependencies.forEach { projectDependency ->
                find { it.name == projectDependency.from }?.let { packageDependency ->
                    rawModuleInfo.dependencies[projectDependency.from]?.version = packageDependency.version
                    rawModuleInfo.dependencies[projectDependency.from]?.dependencies = packageDependency.dependencies
                    rawModuleInfo.devDependencies[projectDependency.from]?.version = packageDependency.version
                    rawModuleInfo.devDependencies[projectDependency.from]?.dependencies =
                        packageDependency.devDependencies
                    rawModuleInfo.optionalDependencies[projectDependency.from]?.version = packageDependency.version
                    rawModuleInfo.optionalDependencies[projectDependency.from]?.dependencies =
                        packageDependency.optionalDependencies
                }
            }
        }

        return this
    }

    /**
     * A data class storing information about a specific PNPM module and its dependencies.
     */
    private data class PnpmModuleInfo(
        /** The identifier for the represented module. */
        val id: Identifier,

        /** Package that represent the current dependency or `null` if the dependency is a project dependency. */
        val pkg: Package?,

        /** A set with information about the modules this module depends on. */
        val dependencies: Set<PnpmModuleInfo>
    )

    /**
     * A specialized [DependencyHandler] implementation for PNPM.
     */
    private class PnpmDependencyHandler : DependencyHandler<PnpmModuleInfo> {
        override fun identifierFor(dependency: PnpmModuleInfo) = dependency.id

        override fun dependenciesFor(dependency: PnpmModuleInfo) = dependency.dependencies

        override fun linkageFor(dependency: PnpmModuleInfo) =
            if (dependency.pkg == null) PackageLinkage.PROJECT_DYNAMIC else PackageLinkage.DYNAMIC

        override fun createPackage(dependency: PnpmModuleInfo, issues: MutableList<OrtIssue>) = dependency.pkg
    }
}
