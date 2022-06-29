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

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.MappingIterator
import com.fasterxml.jackson.databind.node.NullNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.readValues

import com.vdurmont.semver4j.Requirement

import java.io.File
import java.util.SortedSet

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking

import org.ossreviewtoolkit.analyzer.AbstractPackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.PackageManagerResult
import org.ossreviewtoolkit.analyzer.managers.utils.mapDefinitionFilesForYarn2
import org.ossreviewtoolkit.downloader.VcsHost
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.DependencyGraph
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.OrtIssue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageLinkage
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.PackageManagerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.model.jsonMapper
import org.ossreviewtoolkit.model.utils.DependencyGraphBuilder
import org.ossreviewtoolkit.model.utils.DependencyHandler
import org.ossreviewtoolkit.model.yamlMapper
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.ProcessCapture
import org.ossreviewtoolkit.utils.common.textValueOrEmpty
import org.ossreviewtoolkit.utils.ort.log
import org.ossreviewtoolkit.utils.ort.showStackTrace

internal const val OPTION_DISABLE_REGISTRY_CERTIFICATE_VERIFICATION = "disableRegistryCertificateVerification"

// The various Yarn dependency types supported by this package manager.
private enum class YarnDependencyType(val type: String) {
    DEPENDENCIES("dependencies"),
    DEV_DEPENDENCIES("devDependencies")
}

/**
 * The [Yarn 2+](https://next.yarnpkg.com/) package manager for JavaScript.
 *
 * This package manager supports the following [options][PackageManagerConfiguration.options]:
 * - *disableRegistryCertificateVerification*: If true, the 'yarn npm info' commands called by this package manager
 * won't verify the server certificate of the HTTPS connection to the NPM registry. This allows to replace the latter by
 * a local one, e.g. for intercepting the requests or replaying them.
 */
class Yarn2(
    name: String,
    analysisRoot: File,
    analyzerConfig: AnalyzerConfiguration,
    repoConfig: RepositoryConfiguration
) : PackageManager(name, analysisRoot, analyzerConfig, repoConfig), CommandLineTool {
    class Factory : AbstractPackageManagerFactory<Yarn2>("Yarn2") {
        override val globsForDefinitionFiles = listOf("package.json")

        override fun create(
            analysisRoot: File,
            analyzerConfig: AnalyzerConfiguration,
            repoConfig: RepositoryConfiguration
        ) = Yarn2(managerName, analysisRoot, analyzerConfig, repoConfig)
    }

    /**
     * The Yarn 2+ executable is not installed globally: The program shipped by the project in `.yarn/releases` is used
     * instead. The value of the 'yarnPath' property in the resource file `.yarnrc.yml` defines the path to the
     * executable for the current project e.g. `yarnPath: .yarn/releases/yarn-3.2.1.cjs`.
     * This map holds the mapping between the directory and their Yarn 2+ executables.
     */
    private val yarn2ExecutablesByPath: MutableMap<File, String> = mutableMapOf()

    private val disableRegistryCertificateVerification = analyzerConfig.getPackageManagerConfiguration(managerName)
        ?.options
        ?.get(OPTION_DISABLE_REGISTRY_CERTIFICATE_VERIFICATION)
        .toBoolean()

    companion object {
        /**
         * The name of Yarn 2+ resource file.
         */
        const val YARN2_RESOURCE_FILE = ".yarnrc.yml"

        /**
         * The property in `.yarnrc.yml`containing the path to the Yarn2+ executable.
         */
        const val YARN_PATH_PROPERTY_NAME = "yarnPath"

        /**
         * The pattern to extract rawName, type and version from a Yarn 2+ locator e.g. @babel/preset-env@npm:7.11.0.
         */
        private val EXTRACT_FROM_LOCATOR_PATTERN = Regex("(.+)@(\\w+):(.+)")

        /**
         * The amount of package details to query at once with `yarn npm info`.
         */
        private const val BULK_DETAILS_SIZE = 1000
    }
    // A builder to build the dependency graph of the project.
    private val graphBuilder = DependencyGraphBuilder(Yarn2DependencyHandler())

    // All the packages parsed by this package manager, mapped by their ids.
    private val allPackages = mutableMapOf<Identifier, Package>()

    // All the projects parsed by this package manager, mapped by their ids.
    private val allProjects = mutableMapOf<Identifier, Project>()

    // The issues that have been found when resolving the dependencies.
    private val issues = mutableListOf<OrtIssue>()

    override fun command(workingDir: File?): String {
        if (workingDir == null) return ""

        return yarn2ExecutablesByPath.getOrPut(workingDir) {
            val yarnConfig = yamlMapper.readTree(workingDir.resolve(YARN2_RESOURCE_FILE))
            val yarnCommand = requireNotNull(yarnConfig[YARN_PATH_PROPERTY_NAME]) {
                "No Yarn 2+ executable could be found in 'yarnrc.yml'."
            }

            val yarnExecutable = workingDir.resolve(yarnCommand.textValue())

            // TODO: Yarn2 executable is a `cjs` file. Check if under Windows it needs to be run with `node`.

            // TODO: This is a security risk to blindly run code coming from a repository other than ORT's. ORT
            //       should download the Yarn2 binary from the official repository and run it.
            require(yarnExecutable.isFile) {
                "The Yarn 2+ program '${yarnExecutable.name}' does not exist."
            }

            if (!yarnExecutable.canExecute()) {
                log.warn {
                    "The Yarn 2+ program '${yarnExecutable.name}' should be executable. Changing its rights."
                }

                require(yarnExecutable.setExecutable(true)) {
                    "Cannot set the Yarn 2+ program to be executable."
                }
            }

            if (Os.isWindows) "node ${yarnExecutable.absolutePath}" else yarnExecutable.absolutePath
        }
    }

    override fun getVersion(workingDir: File?): String =
        // `getVersion` with a `null` parameter is called once by the Analyzer to get the version of the global tool.
        // For Yarn2+, the version is specific to each definition file being scanned therefore a global version doesn't
        // apply.
        // TODO: An alternative would be to collate the versions of all tools in `yarn2CommandsByPath`.
        if (workingDir == null) "" else super.getVersion(workingDir)

    override fun getVersionRequirement(): Requirement = Requirement.buildNPM(">=2.0.0")

    override fun mapDefinitionFiles(definitionFiles: List<File>) = mapDefinitionFilesForYarn2(definitionFiles).toList()

    override fun beforeResolution(definitionFiles: List<File>) =
        // We depend on a version >= 2, so we check the version for safety.
        definitionFiles.forEach { checkVersion(it.parentFile) }

    override fun resolveDependencies(definitionFile: File, labels: Map<String, String>): List<ProjectAnalyzerResult> {
        val workingDir = definitionFile.parentFile

        // Running `yarn install` before `yarn info` allows to get the real local package version. Otherwise, it will be
        // a generic one such as '0.0.0-use.local'.

        run("install", workingDir = workingDir)

        // Query the list of all packages with their dependencies. The output is in NDJSON format.
        val process = run(
            "info",
            "-A",
            "-R",
            "--manifest",
            "--json",
            workingDir = workingDir,
            environment = mapOf("YARN_NODE_LINKER" to "pnp")
        )

        // First pass: Parse the headers and query each package details.
        val packagesHeaders: Map<String, PackageHeader>
        val packagedDetails = mutableMapOf<String, AdditionalData>()
        jsonMapper.createParser(process.stdout).use {
            val iterator = jsonMapper.readValues<ObjectNode>(it)

            packagesHeaders = parsePackageHeaders(iterator)
            packagedDetails += queryPackageDetails(workingDir, packagesHeaders)
        }

        // Second pass: Parse the packages.
        jsonMapper.createParser(process.stdout).use { parser ->
            val iterator = jsonMapper.readValues<ObjectNode>(parser)

            log.info { "Parsing packages..." }

            val allProjects = parseAllPackages(iterator, definitionFile, packagesHeaders, packagedDetails)
            val scopeNames = YarnDependencyType.values().map { it.type }.toSortedSet()
            return allProjects.values.map { project ->
                ProjectAnalyzerResult(project.copy(scopeNames = scopeNames), sortedSetOf(), issues)
            }.toList()
        }
    }

    /**
     * Parse several packages and construct their headers i.e. their representations as a triple : rawName/type/locator.
     * [iterator] should come from a NDJSON file. Return the headers mapped by package id.
     */
    private fun parsePackageHeaders(iterator: MappingIterator<ObjectNode>): Map<String, PackageHeader> {
        log.info { "Parsing packages headers..." }

        return iterator.asSequence().mapNotNull { json ->
            val value = json["value"].textValue()
            val nameMatcher = EXTRACT_FROM_LOCATOR_PATTERN.matchEntire(value)
            if (nameMatcher == null) {
                issues += createAndLogIssue(
                    managerName,
                    "Name of package $value cannot be parsed.",
                    Severity.ERROR
                )
                null
            } else {
                val rawName = nameMatcher.groupValues[1]
                val type = nameMatcher.groupValues[2] // either "npm" or "workspace"
                val version = nameMatcher.groupValues[3]
                value to PackageHeader(rawName, type, version)
            }
        }.toMap()
    }

    /**
     * Query the details of several packages. [iterator] should come from a NDJSON file. [packagesHeaders] should be
     * the package representations as a triple : rawName/type/locator, mapped by package id.
     * The packages are separated in chunks and queried with `npm file` in [workingDir]. Unfortunately, under the hood,
     * NPM does a request per package. However, if a solution to batch these requests arise, the code is ready for it.
     * From the response to `npm file`, package details are extracted and returned.
     */
    private fun queryPackageDetails(
        workingDir: File,
        packagesHeaders: Map<String, PackageHeader>
    ): Map<String, AdditionalData> {
        log.info { "Fetching packages details..." }

        val chunks = packagesHeaders.filterValues { it.type != "workspace" }.values.map {
            "${it.rawName}@${cleanYarn2VersionString(it.version)}"
        }.chunked(BULK_DETAILS_SIZE)

        return runBlocking(Dispatchers.IO) {
            chunks.mapIndexed { index, chunk ->
                async {
                    Yarn2.log.info { "Fetching packages details chunk #$index." }

                    val process = run(
                        "npm",
                        "info",
                        "--fields",
                        "description,repository,dist,homepage,author,gitHead,version",
                        "--json",
                        *chunk.toTypedArray(),
                        workingDir = workingDir,
                        environment = mapOf("NODE_TLS_REJECT_UNAUTHORIZED" to "0")
                            .takeIf { disableRegistryCertificateVerification }
                            .orEmpty()
                    )
                    jsonMapper.createParser(process.stdout).use {
                        val detailsIterator =
                            jsonMapper.readValues<ObjectNode>(it)
                        detailsIterator.asSequence().map { json ->
                            processAdditionalPackageInfo(json)
                        }.toList()
                    }.also {
                        Yarn2.log.info { "Chunk #$index packages details have been fetched." }
                    }
                }
            }.awaitAll().flatten().associateBy { "${it.name}@${it.version}" }
        }
    }

    /**
     * Parse all packages defined in [iterator], which should come from a NDJSON file. [packagesHeaders] should be
     * the package representations as a triple : rawName/type/locator, mapped by package id.
     * [packagesDetails] should be the package details extracted from `yarn npm view`, mapped by id.
     * Each package defined in this file is parsed and it's dependencies are computed. Finally, each dependency tree is
     * appended to the dependency graph.
     */
    private fun parseAllPackages(
        iterator: MappingIterator<ObjectNode>,
        definitionFile: File,
        packagesHeaders: Map<String, PackageHeader>,
        packagesDetails: Map<String, AdditionalData>
    ): Map<Identifier, Project> {
        val allDependencies = mutableMapOf<YarnDependencyType, MutableMap<Identifier, List<Identifier>>>()
        // Create packages for all modules found in the workspace and add them to the graph builder. They are reused
        // when they are referenced by scope dependencies.
        iterator.forEach { node ->
            val dependencyMapping = parsePackage(node, definitionFile, packagesHeaders, packagesDetails)
            dependencyMapping.forEach {
                val mapping = allDependencies.getOrPut(it.key) { mutableMapOf() }
                mapping += it.value
            }
        }
        graphBuilder.addPackages(allPackages.values)

        allDependencies.forEach { (dependencyType, allScopedDependencies) ->
            allProjects.values.forEach { project ->
                val qualifiedScopeName = DependencyGraph.qualifyScope(project.id, dependencyType.type)
                val dependencies = allScopedDependencies[project.id]
                val dependenciesInfo = dependencies?.mapNotNull { dependency ->
                    if ("Yarn2" in dependency.type) {
                        val projectAsDependency = allProjects.entries.find { entry ->
                            entry.key.type == "Yarn2" && entry.key.name == dependency.name &&
                                    entry.key.namespace == dependency.namespace
                        }

                        if (projectAsDependency == null) {
                            log.warn { "Could not find project for dependency '$dependency.'" }
                            null
                        } else {
                            val projectAsDependencyPkg = projectAsDependency.value.toPackage()
                            YarnModuleInfo(
                                projectAsDependency.key,
                                null,
                                projectAsDependencyPkg.collectDependencies(allScopedDependencies)
                            )
                        }
                    } else {
                        val packageDependency = allPackages[dependency]
                        if (packageDependency == null) {
                            log.warn { "Could not find package for dependency $dependency." }
                            null
                        } else {
                            // As small hack here : Because the detection of dependencies per scope is limited (due to
                            // the fact it relies on package.json parsing and only the project ones are available), the
                            // dependencies of a package are always searched in the 'Dependencies' scope, instead of
                            // the scope of this package.
                            val dependenciesInDependenciesScope = allDependencies[YarnDependencyType.DEPENDENCIES]!!
                            YarnModuleInfo(
                                packageDependency.id,
                                packageDependency,
                                packageDependency.collectDependencies(dependenciesInDependenciesScope)
                            )
                        }
                    }
                }?.toSet().orEmpty()

                dependenciesInfo.forEach {
                    graphBuilder.addDependency(qualifiedScopeName, it)
                }
            }
        }
        return allProjects
    }

    /**
     * Construct a Package or a Project by parsing its [json] representation generated by `yarn info`, run for the given
     * [definitionFile].
     * Additional data necessary for constructing the instances is read from [packagesHeaders] which should be the
     * package representations as a triple : rawName/type/locator, mapped by package id. Other additional data is read
     * from [packagesDetails] which should be the package details extracted from `yarn npm view`, mapped by id.
     * The objects constructed by this function are put either in [allPackages] or in [allProjects].
     * The list of dependencies of the constructed object is returned.
     */
    private fun parsePackage(
        json: ObjectNode,
        definitionFile: File,
        packagesHeaders: Map<String, PackageHeader>,
        packagesDetails: Map<String, AdditionalData>
    ): Map<YarnDependencyType, Pair<Identifier, List<Identifier>>> {
        val value = json["value"].textValue()
        val header = packagesHeaders[value]
        if (header == null) {
            issues += createAndLogIssue(
                managerName,
                "No package header found for '$value'.",
                Severity.ERROR
            )
            return emptyMap()
        }

        val (namespace, name) = Npm.splitNamespaceAndName(header.rawName)
        val childrenNode = json["children"]
        val version = childrenNode["Version"].textValue()

        val manifest = childrenNode["Manifest"] as ObjectNode
        // Yarn's manifests contain licenses in an element with an uppercase 'L'. To leverage existing license parsing
        // code, an extra property with lowercase 'L' is added.
        manifest["License"].takeUnless {
            it is NullNode
        }?.also {
            manifest.set<ObjectNode>("license", it)
        }

        val declaredLicenses = Npm.parseLicenses(manifest)
        var homepageUrl = manifest["Homepage"].textValueOrEmpty()

        val id = if (header.type == "workspace") {
            val projectFile = definitionFile.parentFile.resolve(header.version).resolve(definitionFile.name)
            val workingDir = definitionFile.parentFile

            val additionalData = getProjectAdditionalData(workingDir, name, version)
            val id = Identifier("Yarn2", namespace, name, version)
            allProjects += id to Project(
                id = id.copy(type = managerName),
                definitionFilePath = VersionControlSystem.getPathInfo(projectFile).path,
                declaredLicenses = declaredLicenses,
                vcs = additionalData.vcsFromPackage,
                vcsProcessed = processProjectVcs(definitionFile.parentFile, additionalData.vcsFromPackage, homepageUrl),
                homepageUrl = homepageUrl
            )
            id
        } else {
            val versionFromLocator = cleanYarn2VersionString(header.version)
            val details = packagesDetails["${header.rawName}@$versionFromLocator"]

            if (details == null) {
                issues += createAndLogIssue(
                    managerName,
                    "No package details found for '${header.rawName}' at version '$versionFromLocator'.",
                    Severity.ERROR
                )
                return emptyMap()
            }

            if (homepageUrl.isEmpty()) {
                homepageUrl = details.homepage
            }

            val hash = details.hash
            val authors = details.author
            var vcsFromPackage = details.vcsFromPackage

            if (details.vcsFromDownloadUrl.url != details.downloadUrl) {
                vcsFromPackage = vcsFromPackage.merge(details.vcsFromDownloadUrl)
            }

            val id = Identifier("NPM", namespace, name, details.version)
            val pkg = Package(
                id = id,
                authors = authors,
                declaredLicenses = declaredLicenses,
                description = details.description,
                homepageUrl = homepageUrl,
                binaryArtifact = RemoteArtifact.EMPTY,
                sourceArtifact = RemoteArtifact(
                    url = VcsHost.toArchiveDownloadUrl(details.vcsFromDownloadUrl) ?: details.downloadUrl,
                    hash = hash
                ),
                vcs = vcsFromPackage,
                vcsProcessed = processPackageVcs(vcsFromPackage, homepageUrl),
            )

            require(pkg.id.name.isNotEmpty()) {
                "Generated package info for ${id.toCoordinates()} has no name."
            }

            require(pkg.id.version.isNotEmpty()) {
                "Generated package info for ${id.toCoordinates()} has no version."
            }
            allPackages += id to pkg
            id
        }

        val rawDependencies = childrenNode.withArray<JsonNode>("Dependencies")
        val dependencies = processDependencies(rawDependencies)

        val dependencyToType = listDependenciesByType(definitionFile)

        // Sort all dependencies per scope. Notice that the logic is somehow lenient : If a dependency is not found
        // in this scope, it falls back in the 'dependencies' scope. This is due to the fact that the detection of
        // dependencies per scope is limited, because it relies on package.json parsing and only the project ones
        // are available.

        return YarnDependencyType.values().associateWith { dependencyType ->
            id to dependencies.filter {
                dependencyToType[it.name] == dependencyType
                        || (it.name !in dependencyToType && dependencyType == YarnDependencyType.DEPENDENCIES)
            }
        }
    }

    /**
     * Recursively collect all the dependencies of a given package. Dependency mapping is taken from [allDependencies],
     * which should map a package to its dependencies. [ancestorPackageIds] is used to prevent cycles in the dependency
     * graph.
     */
    private fun Package.collectDependencies(
        allDependencies: Map<Identifier, List<Identifier>>,
        ancestorPackageIds: Set<Identifier> = emptySet(),
    ): Set<YarnModuleInfo> {
        val dependenciesIds = allDependencies[id]
        return dependenciesIds?.mapNotNull { dependencyId ->
            if (dependencyId in ancestorPackageIds) {
                log.debug("Not adding the dependency '$dependencyId' of package '$id' to prevent a cycle.")
                return@mapNotNull null
            }

            val dependencyPkg = allPackages[dependencyId]
            if (dependencyPkg == null) {
                log.warn { "Could not find package for sub dependency '$dependencyId' of package '$id'." }
                null
            } else {
                val subDependencies = dependencyPkg.collectDependencies(
                    allDependencies,
                    ancestorPackageIds + dependencyId
                )
                YarnModuleInfo(dependencyId, dependencyPkg, subDependencies)
            }
        }?.toSet().orEmpty()
    }

    /**
     * Process the [json] result of `yarn npm info` for a given package and return a populated [AdditionalData].
     */
    private fun processAdditionalPackageInfo(json: ObjectNode): AdditionalData {
        val name = json["name"].textValue()
        val version = json["version"].textValue()
        val description = json["description"].textValueOrEmpty()
        val vcsFromPackage = Npm.parseVcsInfo(json)
        val homepage = json["homepage"].textValueOrEmpty()
        val author = Npm.parseAuthors(json)

        val dist = json["dist"]
        var downloadUrl = dist["tarball"].textValueOrEmpty()

        downloadUrl = Npm.fixDownloadUrl(downloadUrl)

        val hash = Hash.create(dist["shasum"].textValueOrEmpty())

        val vcsFromDownloadUrl = VcsHost.parseUrl(downloadUrl)

        return AdditionalData(
            name,
            version,
            description,
            vcsFromPackage,
            vcsFromDownloadUrl,
            homepage,
            downloadUrl,
            hash,
            author
        )
    }

    /**
     * Process [dependencies], the `Dependencies` sub-element of a single node returned by `yarn info`.
     * The dependencies are returned as a list.
     */
    private fun processDependencies(dependencies: JsonNode): List<Identifier> =
        dependencies.mapNotNull { dependency ->
            val locator = dependency["locator"].textValue()
            val locatorMatcher = EXTRACT_FROM_LOCATOR_PATTERN.matchEntire(locator)
            if (locatorMatcher == null) {
                issues += createAndLogIssue(
                    managerName,
                    "Locator '$locator' cannot be parsed.",
                    Severity.ERROR
                )
                return@mapNotNull null
            }

            val locatorRawName = locatorMatcher.groupValues[1]
            val locatorType = locatorMatcher.groupValues[2]
            val locatorVersion = locatorMatcher.groupValues[3]

            val (locatorNamespace, locatorName) = Npm.splitNamespaceAndName(locatorRawName)
            val version = cleanYarn2VersionString(locatorVersion)

            val identifierType = if ("workspace" in locatorType) "Yarn2" else "NPM"
            when {
                // Prevent @virtual dependencies because they are internal to Yarn.
                // See https://yarnpkg.com/features/protocols
                "virtual" in locatorType -> null

                else -> {
                    runCatching {
                        Identifier(identifierType, locatorNamespace, locatorName, version)
                    }.onFailure {
                        it.showStackTrace()
                        issues += createAndLogIssue(
                            managerName,
                            "Cannot build identifier for dependency '$locator.'",
                            Severity.ERROR
                        )
                    }.getOrNull()
                }
            }
        }.toList()

    /**
     * Parse the [definitionFile] (package.json) to find the scope of a dependency. Unfortunately, `yarn info -A -R`
     * does not deliver this information.
     * Return the dependencies present in the file mapped to their scope.
     * See also https://classic.yarnpkg.com/en/docs/dependency-types (documentation for Yarn 1).
     */
    private fun listDependenciesByType(definitionFile: File): Map<String, YarnDependencyType> {
        val json = jsonMapper.readTree(definitionFile)
        val result = mutableMapOf<String, YarnDependencyType>()
        YarnDependencyType.values().forEach { dependencyType ->
            json[dependencyType.type]?.fieldNames()?.asSequence()?.forEach {
                result += it to dependencyType
            }
        }
        return result
    }

    /**
     * Clean the [rawVersion] string contained in a Yarn 2+ locator to have it compatible with NPM/Semver.
     */
    private fun cleanYarn2VersionString(rawVersion: String): String {
        // 'Patch' locators are complex expressions such as
        // resolve@npm%3A2.0.0-next.3#~builtin<compat/resolve>%3A%3Aversion=2.0.0-next.3&hash=07638b
        // Therefore, the version has to be extracted (here '2.0.0-next.3).
        var result = rawVersion.substringAfter("version=")
            .substringBefore("&")

        // Remove the archive URLs that can be present due to private registries.
        // See https://github.com/yarnpkg/berry/issues/2192.
        result = result.substringBefore("::__archiveUrl")

        // Rewrite some dependencies to make them compatible with Identifier.
        // E.g. typescript@patch:typescript@npm%3A4.0.2#~builtin<compat/typescript>::version=4.0.2&hash=ddd1e8
        return result.replace(":", "%3A")
    }

    /**
     * With Yarn 2+, it is currently not possible with a native command to get the repository information for a project
     * package. Therefore, this function runs a low-level NPM command to fetch this information.
     * Note that the project must have been installed first with `yarn install`.
     */
    private fun getProjectAdditionalData(
        workingDir: File,
        packageName: String,
        packageVersion: String
    ): AdditionalData {
        // Notice that a ProcessCapture is directly called to avoid the `requiredSuccess` : NPM sets exit code to 1 if
        // some peer dependencies cannot be resolved (see https://github.com/npm/npm/issues/17624).
        val process = ProcessCapture(
            if (Os.isWindows) "npm.cmd" else "npm",
            "list",
            "-l",
            "--json",
            "$packageName@$packageVersion",
            workingDir = workingDir
        )
        val json = jsonMapper.readTree(process.stdout)

        val vcsFromPackage = Npm.parseVcsInfo(json)
        val name = json["name"].textValueOrEmpty()
        val version = json["version"].textValueOrEmpty()
        val description = json["description"].textValueOrEmpty()
        return AdditionalData(name, version, description, vcsFromPackage, VcsInfo.EMPTY)
    }

    override fun createPackageManagerResult(projectResults: Map<File, List<ProjectAnalyzerResult>>) =
        PackageManagerResult(projectResults, graphBuilder.build(), graphBuilder.packages())

    /**
     * A data class storing information about a specific Yarn 2+ module and its dependencies.
     */
    private data class YarnModuleInfo(
        /** The identifier for the represented module. */
        val id: Identifier,

        /** Package that represent the current dependency or `null` if the dependency is a project dependency. */
        val pkg: Package?,

        /** A set with information about the modules this module depends on. */
        val dependencies: Set<YarnModuleInfo>
    )

    /**
     * A specialized [DependencyHandler] implementation for Yarn 2+.
     */
    private class Yarn2DependencyHandler : DependencyHandler<YarnModuleInfo> {
        override fun identifierFor(dependency: YarnModuleInfo): Identifier = dependency.id

        override fun dependenciesFor(dependency: YarnModuleInfo): Collection<YarnModuleInfo> = dependency.dependencies

        override fun linkageFor(dependency: YarnModuleInfo): PackageLinkage =
            if (dependency.pkg == null) PackageLinkage.PROJECT_DYNAMIC else PackageLinkage.DYNAMIC

        override fun createPackage(dependency: YarnModuleInfo, issues: MutableList<OrtIssue>): Package? = dependency.pkg
    }

    /**
     * The header of a NPM package, coming from a Yarn 2+ locator raw version string.
     */
    private data class PackageHeader(
        val rawName: String,
        val type: String,
        val version: String
    )

    /**
     * Class containing additional data returned by `yarn npm info`.
     */
    private data class AdditionalData(
        val name: String,
        val version: String,
        val description: String,
        val vcsFromPackage: VcsInfo,
        val vcsFromDownloadUrl: VcsInfo,
        val homepage: String = "",
        val downloadUrl: String = "",
        val hash: Hash = Hash.NONE,
        val author: SortedSet<String> = emptySet<String>().toSortedSet()
    )
}
