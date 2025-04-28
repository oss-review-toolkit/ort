/*
 * Copyright (C) 2022 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.node.yarn2

import java.io.File

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.analyzer.PackageManagerFactory
import org.ossreviewtoolkit.analyzer.parseAuthorString
import org.ossreviewtoolkit.downloader.VcsHost
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.model.utils.DependencyGraphBuilder
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.OrtPluginOption
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.plugins.packagemanagers.node.NodePackageManager
import org.ossreviewtoolkit.plugins.packagemanagers.node.NodePackageManagerType
import org.ossreviewtoolkit.plugins.packagemanagers.node.PackageJson
import org.ossreviewtoolkit.plugins.packagemanagers.node.Scope
import org.ossreviewtoolkit.plugins.packagemanagers.node.fixDownloadUrl
import org.ossreviewtoolkit.plugins.packagemanagers.node.getDependenciesForScope
import org.ossreviewtoolkit.plugins.packagemanagers.node.getNames
import org.ossreviewtoolkit.plugins.packagemanagers.node.mapLicenses
import org.ossreviewtoolkit.plugins.packagemanagers.node.parsePackageJson
import org.ossreviewtoolkit.plugins.packagemanagers.node.parsePackageJsons
import org.ossreviewtoolkit.plugins.packagemanagers.node.parseVcsInfo
import org.ossreviewtoolkit.plugins.packagemanagers.node.splitNamespaceAndName
import org.ossreviewtoolkit.utils.ort.runBlocking
import org.ossreviewtoolkit.utils.ort.showStackTrace

/**
 * The pattern to extract rawName, type and version from a Yarn 2+ locator e.g. @babel/preset-env@npm:7.11.0.
 */
private val EXTRACT_FROM_LOCATOR_PATTERN = Regex("(.+)@(\\w+):(.+)")

/**
 * The amount of package details to query at once with `yarn npm info`.
 */
private const val YARN_NPM_INFO_CHUNK_SIZE = 1000

data class Yarn2Config(
    /**
     * If true, the `yarn npm info` commands called by this package manager will not verify the server certificate of
     * the HTTPS connection to the NPM registry. This allows replacing the latter by a local one, e.g., for intercepting
     * the requests or replaying them.
     */
    @OrtPluginOption(defaultValue = "false")
    val disableRegistryCertificateVerification: Boolean,

    /**
     * Per default, this class determines via auto-detection whether Yarn has been installed via
     * [Corepack](https://yarnpkg.com/corepack), which impacts the name of the executable to use. With this option,
     * auto-detection can be disabled, and the enabled status of Corepack can be explicitly specified. This is useful to
     * force a specific behavior in some environments.
     */
    val corepackOverride: Boolean?
)

/**
 * The [Yarn 2+ package manager](https://v2.yarnpkg.com/).
 */
@OrtPlugin(
    displayName = "Yarn 2+",
    description = "The Yarn 2+ package manager for Node.js.",
    factory = PackageManagerFactory::class
)
class Yarn2(override val descriptor: PluginDescriptor = Yarn2Factory.descriptor, private val config: Yarn2Config) :
    NodePackageManager(NodePackageManagerType.YARN2) {
    override val globsForDefinitionFiles = listOf(NodePackageManagerType.DEFINITION_FILE)

    private val packageForId = mutableMapOf<Identifier, Package>()
    private val projectForId = mutableMapOf<Identifier, Project>()

    internal val yarn2Command = Yarn2Command(config.corepackOverride)

    private val issues = mutableListOf<Issue>()

    override val graphBuilder = DependencyGraphBuilder(Yarn2DependencyHandler())

    override fun beforeResolution(
        analysisRoot: File,
        definitionFiles: List<File>,
        analyzerConfig: AnalyzerConfiguration
    ) = definitionFiles.forEach { yarn2Command.checkVersion(it.parentFile) }

    override fun resolveDependencies(
        analysisRoot: File,
        definitionFile: File,
        excludes: Excludes,
        analyzerConfig: AnalyzerConfiguration,
        labels: Map<String, String>
    ): List<ProjectAnalyzerResult> {
        val workingDir = definitionFile.parentFile
        installDependencies(workingDir)

        val packageInfos = getPackageInfos(workingDir)
        val packageHeaders = parsePackageHeaders(packageInfos)
        val packageDetails = queryPackageDetails(
            workingDir,
            moduleIds = packageHeaders.values.filterNot { it.isProject }.mapTo(mutableSetOf()) { it.moduleId }
        ).mapValues { (_, packageJson) ->
            processAdditionalPackageInfo(packageJson)
        }

        val allProjects = parseAllPackages(packageInfos, definitionFile, packageHeaders, packageDetails, excludes)
        val scopeNames = Scope.entries.getNames()

        return allProjects.values.map { project ->
            ProjectAnalyzerResult(project.copy(scopeNames = scopeNames), emptySet(), issues)
        }.toList()
    }

    private fun installDependencies(workingDir: File) {
        yarn2Command.run("install", workingDir = workingDir).requireSuccess()
    }

    private fun getPackageInfos(workingDir: File): List<PackageInfo> {
        val process = yarn2Command.run(
            "info",
            "--all",
            "--recursive",
            "--manifest",
            "--json",
            workingDir = workingDir,
            environment = mapOf("YARN_NODE_LINKER" to "pnp")
        ).requireSuccess()

        return parsePackageInfos(process.stdout)
    }

    /**
     * Parse several packages and construct their headers i.e. their representations as a triple : rawName/type/locator.
     * [iterator] should come from a NDJSON file. Return the headers mapped by package id.
     */
    private fun parsePackageHeaders(packageInfos: Collection<PackageInfo>): Map<String, PackageHeader> {
        logger.info { "Parsing packages headers..." }

        return packageInfos.mapNotNull { info ->
            val value = info.value
            val nameMatcher = EXTRACT_FROM_LOCATOR_PATTERN.matchEntire(value)
            if (nameMatcher == null) {
                issues += createAndLogIssue("Name of package $value cannot be parsed.", Severity.ERROR)
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
     * Query the details for the given NPM [moduleIds].
     * The packages are separated in chunks and queried with `npm file` in [workingDir]. Unfortunately, under the hood,
     * NPM does a request per package. However, if a solution to batch these requests arise, the code is ready for it.
     * From the response to `npm file`, package details are extracted and returned.
     */
    private fun queryPackageDetails(workingDir: File, moduleIds: Set<String>): Map<String, PackageJson> {
        logger.info { "Fetching packages details..." }

        val chunks = moduleIds.chunked(YARN_NPM_INFO_CHUNK_SIZE)

        return runBlocking(Dispatchers.IO.limitedParallelism(20)) {
            chunks.mapIndexed { index, chunk ->
                async {
                    logger.info { "Fetching packages details chunk #$index." }

                    val process = yarn2Command.run(
                        "npm",
                        "info",
                        "--json",
                        *chunk.toTypedArray(),
                        workingDir = workingDir,
                        environment = mapOf("NODE_TLS_REJECT_UNAUTHORIZED" to "0")
                            .takeIf { config.disableRegistryCertificateVerification }
                            .orEmpty()
                    ).requireSuccess()

                    parsePackageJsons(process.stdout)
                }
            }.awaitAll().flatten().associateBy { "${it.name}@${it.version}" }
        }
    }

    /**
     * Parse all packages defined in [iterator], which should come from a NDJSON file. [packagesHeaders] should be
     * the package representations as a triple: rawName/type/locator, mapped by package id.
     * [packagesDetails] should be the package details extracted from `yarn npm view`, mapped by id.
     * Each package defined in this file is parsed and it's dependencies are computed. Finally, each dependency tree is
     * appended to the dependency graph.
     */
    private fun parseAllPackages(
        packageInfos: Collection<PackageInfo>,
        definitionFile: File,
        packagesHeaders: Map<String, PackageHeader>,
        packagesDetails: Map<String, AdditionalData>,
        excludes: Excludes
    ): Map<Identifier, Project> {
        val allDependencies = mutableMapOf<Scope, MutableMap<Identifier, List<Identifier>>>()

        packageInfos.forEach { info ->
            val dependencyMapping = parsePackage(info, definitionFile, packagesHeaders, packagesDetails)
            dependencyMapping.forEach {
                val mapping = allDependencies.getOrPut(it.key) { mutableMapOf() }
                mapping += it.value
            }
        }

        allDependencies.filterNot { (scope, _) -> scope.isExcluded(excludes) }
            .forEach { (scope, allScopedDependencies) ->
                projectForId.values.forEach { project ->
                    val dependencies = allScopedDependencies[project.id]
                    val dependenciesInfo = dependencies?.mapNotNullTo(mutableSetOf()) { dependency ->
                        if ("Yarn2" in dependency.type) {
                            val projectAsDependency = projectForId.entries.find { entry ->
                                entry.key.type == "Yarn2" && entry.key.name == dependency.name &&
                                    entry.key.namespace == dependency.namespace
                            }

                            if (projectAsDependency == null) {
                                logger.warn { "Could not find project for dependency '$dependency.'" }
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
                            val packageDependency = packageForId[dependency]
                            if (packageDependency == null) {
                                logger.warn { "Could not find package for dependency $dependency." }
                                null
                            } else {
                                // As small hack here: Because the detection of dependencies per scope is limited (due
                                // to the fact it relies on package.json parsing and only the project ones are
                                // available), the dependencies of a package are always searched in the 'Dependencies'
                                // scope, instead of the scope of this package.
                                @Suppress("UnsafeCallOnNullableType")
                                val dependenciesInDependenciesScope = allDependencies[Scope.DEPENDENCIES]!!

                                YarnModuleInfo(
                                    packageDependency.id,
                                    packageDependency,
                                    packageDependency.collectDependencies(dependenciesInDependenciesScope)
                                )
                            }
                        }
                    }.orEmpty()

                    graphBuilder.addDependencies(project.id, scope.descriptor, dependenciesInfo)
                }
            }

        return projectForId
    }

    /**
     * Construct a Package or a Project by parsing its [packageInfo] representation generated by `yarn info`, run for
     * the given [definitionFile].
     * Additional data necessary for constructing the instances is read from [packagesHeaders] which should be the
     * package representations as a triple : rawName/type/locator, mapped by package id. Other additional data is read
     * from [packagesDetails] which should be the package details extracted from `yarn npm view`, mapped by id.
     * The objects constructed by this function are put either in [packageForId] or in [projectForId].
     * The list of dependencies of the constructed object is returned.
     */
    private fun parsePackage(
        packageInfo: PackageInfo,
        definitionFile: File,
        packagesHeaders: Map<String, PackageHeader>,
        packagesDetails: Map<String, AdditionalData>
    ): Map<Scope, Pair<Identifier, List<Identifier>>> {
        val value = packageInfo.value
        val header = packagesHeaders[value]
        if (header == null) {
            issues += createAndLogIssue("No package header found for '$value'.", Severity.ERROR)
            return emptyMap()
        }

        val (namespace, name) = splitNamespaceAndName(header.rawName)
        val manifest = packageInfo.children.manifest
        val declaredLicenses = manifest.license.orEmpty().let { setOf(it).mapLicenses() }
        var homepageUrl = manifest.homepage.orEmpty()

        val id = if (header.isProject) {
            val version = packageInfo.children.version
            val projectFile = definitionFile.resolveSibling(header.version).resolve(definitionFile.name)
            val packageJson = parsePackageJson(projectFile)
            val additionalData = processAdditionalPackageInfo(packageJson)
            val authors = packageJson.authors
                .flatMap { parseAuthorString(it.name) }
                .mapNotNullTo(mutableSetOf()) { it.name }

            val id = Identifier("Yarn2", namespace, name, version)
            projectForId += id to Project(
                id = id.copy(type = projectType),
                definitionFilePath = VersionControlSystem.getPathInfo(projectFile).path,
                declaredLicenses = declaredLicenses,
                vcs = additionalData.vcsFromPackage,
                vcsProcessed = processProjectVcs(definitionFile.parentFile, additionalData.vcsFromPackage, homepageUrl),
                description = additionalData.description,
                homepageUrl = homepageUrl,
                authors = authors
            )
            id
        } else {
            val version = header.version.cleanVersionString()
            val details = packagesDetails["${header.rawName}@$version"]

            if (details == null) {
                issues += createAndLogIssue(
                    "No package details found for '${header.rawName}' at version '$version'.",
                    Severity.ERROR
                )
                return emptyMap()
            }

            if (homepageUrl.isEmpty()) {
                homepageUrl = details.homepage
            }

            val hash = details.hash
            val authors = details.authors
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
                vcsProcessed = processPackageVcs(vcsFromPackage, homepageUrl)
            )

            require(pkg.id.name.isNotEmpty()) {
                "Generated package info for '${id.toCoordinates()}' has no name."
            }

            require(pkg.id.version.isNotEmpty()) {
                "Generated package info for '${id.toCoordinates()}' has no version."
            }

            packageForId += id to pkg
            id
        }

        val dependencies = processDependencies(packageInfo.children.dependencies)

        val dependencyToScope = listDependenciesByScope(definitionFile)

        // Sort all dependencies per scope. Notice that the logic is somehow lenient: If a dependency is not found
        // in this scope, it falls back in the 'dependencies' scope. This is due to the fact that the detection of
        // dependencies per scope is limited, because it relies on package.json parsing and only the project ones
        // are available.

        return Scope.entries.associateWith { scope ->
            id to dependencies.filter {
                dependencyToScope[it.name] == scope || (it.name !in dependencyToScope && scope == Scope.DEPENDENCIES)
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
        ancestorPackageIds: Set<Identifier> = emptySet()
    ): Set<YarnModuleInfo> {
        val dependenciesIds = allDependencies[id]
        return dependenciesIds?.mapNotNull { dependencyId ->
            if (dependencyId in ancestorPackageIds) {
                logger.debug { "Not adding the dependency '$dependencyId' of package '$id' to prevent a cycle." }
                return@mapNotNull null
            }

            val dependencyPkg = packageForId[dependencyId]
            if (dependencyPkg == null) {
                logger.warn { "Could not find package for sub dependency '$dependencyId' of package '$id'." }
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
     * Process the [packageJson] coming from `yarn npm info` for a given package and return a populated
     * [AdditionalData].
     */
    private fun processAdditionalPackageInfo(packageJson: PackageJson): AdditionalData {
        val name = checkNotNull(packageJson.name)
        val version = checkNotNull(packageJson.version)
        val description = packageJson.description.orEmpty()
        val vcsFromPackage = parseVcsInfo(packageJson)
        val homepage = packageJson.homepage.orEmpty()
        val authors = packageJson.authors
            .flatMap { parseAuthorString(it.name) }
            .mapNotNullTo(mutableSetOf()) { it.name }
        val downloadUrl = packageJson.dist?.tarball.orEmpty().fixDownloadUrl()

        val hash = Hash.create(packageJson.dist?.shasum.orEmpty())

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
            authors
        )
    }

    /**
     * Process [dependencies], the `Dependencies` sub-element of a single node returned by `yarn info`.
     * The dependencies are returned as a list.
     */
    private fun processDependencies(dependencies: Collection<PackageInfo.Dependency>): List<Identifier> =
        dependencies.mapNotNull { dependency ->
            val locator = dependency.locator
            val locatorMatcher = EXTRACT_FROM_LOCATOR_PATTERN.matchEntire(locator)
            if (locatorMatcher == null) {
                issues += createAndLogIssue("Locator '$locator' cannot be parsed.", Severity.ERROR)
                return@mapNotNull null
            }

            val locatorRawName = locatorMatcher.groupValues[1]
            val locatorType = locatorMatcher.groupValues[2]
            val locatorVersion = locatorMatcher.groupValues[3]

            val (locatorNamespace, locatorName) = splitNamespaceAndName(locatorRawName)
            val version = locatorVersion.cleanVersionString()

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
                            "Cannot build identifier for dependency '$locator.'",
                            Severity.ERROR
                        )
                    }.getOrNull()
                }
            }
        }.toList()
}

/**
 * A data class storing information about a specific Yarn 2+ module and its dependencies.
 */
internal data class YarnModuleInfo(
    /** The identifier for the represented module. */
    val id: Identifier,

    /** Package that represent the current dependency or `null` if the dependency is a project dependency. */
    val pkg: Package?,

    /** A set with information about the modules this module depends on. */
    val dependencies: Set<YarnModuleInfo>
)

/**
 * The header of a NPM package, coming from a Yarn 2+ locator raw version string.
 */
private data class PackageHeader(
    val rawName: String,
    val type: String,
    val version: String
)

private val PackageHeader.isProject: Boolean get() = type == "workspace"

private val PackageHeader.moduleId: String get() = "$rawName@${version.cleanVersionString()}"

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
    val authors: Set<String> = emptySet()
)

/**
 * Clean this Yarn2 version string (originating from a Yarn 2+ locator) for compatibility with NPM/Semver.
 */
private fun String.cleanVersionString(): String =
    this
        // 'Patch' locators are complex expressions such as
        // resolve@npm%3A2.0.0-next.3#~builtin<compat/resolve>%3A%3Aversion=2.0.0-next.3&hash=07638b
        // Therefore, the version has to be extracted (here '2.0.0-next.3').
        .substringAfter("version=")
        .substringBefore("&")
        // Remove the archive URLs that can be present due to private registries.
        // See https://github.com/yarnpkg/berry/issues/2192.
        .substringBefore("::__archiveUrl")
        // Rewrite some dependencies to make them compatible with Identifier.
        // E.g. typescript@patch:typescript@npm%3A4.0.2#~builtin<compat/typescript>::version=4.0.2&hash=ddd1e8
        .replace(":", "%3A")

/**
 * Parse the [definitionFile] (package.json) to find the scope of a dependency. Unfortunately, `yarn info -A -R`
 * does not deliver this information.
 * Return the dependencies present in the file mapped to their scope.
 * See also https://classic.yarnpkg.com/en/docs/dependency-types (documentation for Yarn 1).
 */
private fun listDependenciesByScope(definitionFile: File): Map<String, Scope> {
    val packageJson = parsePackageJson(definitionFile)
    val result = mutableMapOf<String, Scope>()

    Scope.entries.forEach { scope ->
        packageJson.getDependenciesForScope(scope).forEach {
            result += it to scope
        }
    }

    return result
}
