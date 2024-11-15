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

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlScalar
import com.charleskorn.kaml.yamlMap

import java.io.File

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.analyzer.AbstractPackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.PackageManagerResult
import org.ossreviewtoolkit.analyzer.parseAuthorString
import org.ossreviewtoolkit.downloader.VcsHost
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
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
import org.ossreviewtoolkit.model.utils.DependencyGraphBuilder
import org.ossreviewtoolkit.model.utils.DependencyHandler
import org.ossreviewtoolkit.plugins.packagemanagers.node.NodePackageManager
import org.ossreviewtoolkit.plugins.packagemanagers.node.NpmDetection
import org.ossreviewtoolkit.plugins.packagemanagers.node.PackageJson
import org.ossreviewtoolkit.plugins.packagemanagers.node.fixNpmDownloadUrl
import org.ossreviewtoolkit.plugins.packagemanagers.node.mapNpmLicenses
import org.ossreviewtoolkit.plugins.packagemanagers.node.parseNpmVcsInfo
import org.ossreviewtoolkit.plugins.packagemanagers.node.parsePackageJson
import org.ossreviewtoolkit.plugins.packagemanagers.node.parsePackageJsons
import org.ossreviewtoolkit.plugins.packagemanagers.node.splitNpmNamespaceAndName
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.ort.runBlocking
import org.ossreviewtoolkit.utils.ort.showStackTrace

import org.semver4j.RangesList
import org.semver4j.RangesListFactory

/**
 * The name of Yarn 2+ resource file.
 */
private const val YARN2_RESOURCE_FILE = ".yarnrc.yml"

/**
 * The pattern to extract rawName, type and version from a Yarn 2+ locator e.g. @babel/preset-env@npm:7.11.0.
 */
private val EXTRACT_FROM_LOCATOR_PATTERN = Regex("(.+)@(\\w+):(.+)")

/**
 * The amount of package details to query at once with `yarn npm info`.
 */
private const val YARN_NPM_INFO_CHUNK_SIZE = 1000

/**
 * The name of the manifest file used by Yarn 2+.
 */
private const val MANIFEST_FILE = "package.json"

// The various Yarn dependency types supported by this package manager.
private enum class YarnDependencyType(val type: String) {
    DEPENDENCIES("dependencies"),
    DEV_DEPENDENCIES("devDependencies")
}

/**
 * The [Yarn 2+](https://v2.yarnpkg.com/) package manager for JavaScript.
 *
 * This package manager supports the following [options][PackageManagerConfiguration.options]:
 * - *disableRegistryCertificateVerification*: If true, the `yarn npm info` commands called by this package manager will
 *   not verify the server certificate of the HTTPS connection to the NPM registry. This allows to replace the latter by
 *   a local one, e.g. for intercepting the requests or replaying them.
 * - *corepackOverride*: Per default, this class determines via auto-detection whether Yarn has been installed via
 *   [Corepack](https://yarnpkg.com/corepack), which impacts the name of the executable to use. With this option,
 *   auto-detection can be disabled, and the enabled status of Corepack can be explicitly specified. This is useful to
 *   force a specific behavior in some environments.
 */
class Yarn2(
    name: String,
    analysisRoot: File,
    analyzerConfig: AnalyzerConfiguration,
    repoConfig: RepositoryConfiguration
) : PackageManager(name, "Yarn2", analysisRoot, analyzerConfig, repoConfig), CommandLineTool {
    companion object {
        /**
         * The name of the option to disable HTTPS server certificate verification.
         */
        const val OPTION_DISABLE_REGISTRY_CERTIFICATE_VERIFICATION = "disableRegistryCertificateVerification"

        /**
         * The name of the option that allows overriding the automatic detection of Corepack.
         */
        const val OPTION_COREPACK_OVERRIDE = "corepackOverride"
    }

    class Factory : AbstractPackageManagerFactory<Yarn2>("Yarn2") {
        override val globsForDefinitionFiles = listOf(MANIFEST_FILE)

        override fun create(
            analysisRoot: File,
            analyzerConfig: AnalyzerConfiguration,
            repoConfig: RepositoryConfiguration
        ) = Yarn2(type, analysisRoot, analyzerConfig, repoConfig)
    }

    /**
     * The Yarn 2+ executable is not installed globally: The program shipped by the project in `.yarn/releases` is used
     * instead. The value of the 'yarnPath' property in the resource file `.yarnrc.yml` defines the path to the
     * executable for the current project e.g. `yarnPath: .yarn/releases/yarn-3.2.1.cjs`.
     * This map holds the mapping between the directory and their Yarn 2+ executables. It is only used if Yarn has not
     * been installed via Corepack; then it is accessed under a default name.
     */
    private val yarn2ExecutablesByPath: MutableMap<File, File> = mutableMapOf()

    private val disableRegistryCertificateVerification =
        options[OPTION_DISABLE_REGISTRY_CERTIFICATE_VERIFICATION].toBoolean()

    // A builder to build the dependency graph of the project.
    private val graphBuilder = DependencyGraphBuilder(Yarn2DependencyHandler())

    // All the packages parsed by this package manager, mapped by their ids.
    private val allPackages = mutableMapOf<Identifier, Package>()

    // All the projects parsed by this package manager, mapped by their ids.
    private val allProjects = mutableMapOf<Identifier, Project>()

    // The issues that have been found when resolving the dependencies.
    private val issues = mutableListOf<Issue>()

    override fun command(workingDir: File?): String {
        if (workingDir == null) return ""
        if (isCorepackEnabled(workingDir)) return "yarn"

        val executablePath = yarn2ExecutablesByPath.getOrPut(workingDir) { getYarnExecutable(workingDir) }.absolutePath
        return executablePath.takeUnless { Os.isWindows } ?: "node $executablePath"
    }

    override fun getVersion(workingDir: File?): String =
        // `getVersion` with a `null` parameter is called once by the Analyzer to get the version of the global tool.
        // For Yarn2+, the version is specific to each definition file being scanned therefore a global version doesn't
        // apply.
        // TODO: An alternative would be to collate the versions of all tools in `yarn2CommandsByPath`.
        if (workingDir == null) "" else super.getVersion(workingDir)

    override fun getVersionRequirement(): RangesList = RangesListFactory.create(">=2.0.0")

    private fun isCorepackEnabled(workingDir: File): Boolean =
        if (OPTION_COREPACK_OVERRIDE in options) {
            options[OPTION_COREPACK_OVERRIDE].toBoolean()
        } else {
            isCorepackEnabledInManifest(workingDir)
        }

    override fun mapDefinitionFiles(definitionFiles: List<File>) =
        NpmDetection(definitionFiles).filterApplicable(NodePackageManager.YARN2)

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

        logger.info { "Parsing packages..." }

        val packageInfos = parsePackageInfos(process.stdout)
        val packageHeaders = parsePackageHeaders(packageInfos)
        val packageDetails = queryPackageDetails(workingDir, packageHeaders)

        val allProjects = parseAllPackages(packageInfos, definitionFile, packageHeaders, packageDetails)
        val scopeNames = YarnDependencyType.entries.mapTo(mutableSetOf()) { it.type }

        return allProjects.values.map { project ->
            ProjectAnalyzerResult(project.copy(scopeNames = scopeNames), emptySet(), issues)
        }.toList()
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
     * the package representations as a triple: rawName/type/locator, mapped by package id.
     * The packages are separated in chunks and queried with `npm file` in [workingDir]. Unfortunately, under the hood,
     * NPM does a request per package. However, if a solution to batch these requests arise, the code is ready for it.
     * From the response to `npm file`, package details are extracted and returned.
     */
    private fun queryPackageDetails(
        workingDir: File,
        packagesHeaders: Map<String, PackageHeader>
    ): Map<String, AdditionalData> {
        logger.info { "Fetching packages details..." }

        val chunks = packagesHeaders.filterValues { it.type != "workspace" }.values.map {
            "${it.rawName}@${it.version.cleanVersionString()}"
        }.chunked(YARN_NPM_INFO_CHUNK_SIZE)

        return runBlocking(Dispatchers.IO) {
            chunks.mapIndexed { index, chunk ->
                async {
                    logger.info { "Fetching packages details chunk #$index." }

                    val process = run(
                        "npm",
                        "info",
                        "--json",
                        *chunk.toTypedArray(),
                        workingDir = workingDir,
                        environment = mapOf("NODE_TLS_REJECT_UNAUTHORIZED" to "0")
                            .takeIf { disableRegistryCertificateVerification }
                            .orEmpty()
                    )
                    val packageJsons = parsePackageJsons(process.stdout)

                    logger.info { "Chunk #$index packages details have been fetched." }

                    packageJsons.map { packageJson ->
                        processAdditionalPackageInfo(packageJson)
                    }
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
        packagesDetails: Map<String, AdditionalData>
    ): Map<Identifier, Project> {
        val allDependencies = mutableMapOf<YarnDependencyType, MutableMap<Identifier, List<Identifier>>>()
        // Create packages for all modules found in the workspace and add them to the graph builder. They are reused
        // when they are referenced by scope dependencies.
        packageInfos.forEach { info ->
            val dependencyMapping = parsePackage(info, definitionFile, packagesHeaders, packagesDetails)
            dependencyMapping.forEach {
                val mapping = allDependencies.getOrPut(it.key) { mutableMapOf() }
                mapping += it.value
            }
        }

        allDependencies.filterNot { excludes.isScopeExcluded(it.key.type) }
            .forEach { (dependencyType, allScopedDependencies) ->
                allProjects.values.forEach { project ->
                    val dependencies = allScopedDependencies[project.id]
                    val dependenciesInfo = dependencies?.mapNotNullTo(mutableSetOf()) { dependency ->
                        if ("Yarn2" in dependency.type) {
                            val projectAsDependency = allProjects.entries.find { entry ->
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
                            val packageDependency = allPackages[dependency]
                            if (packageDependency == null) {
                                logger.warn { "Could not find package for dependency $dependency." }
                                null
                            } else {
                                // As small hack here: Because the detection of dependencies per scope is limited (due
                                // to the fact it relies on package.json parsing and only the project ones are
                                // available), the dependencies of a package are always searched in the 'Dependencies'
                                // scope, instead of the scope of this package.
                                @Suppress("UnsafeCallOnNullableType")
                                val dependenciesInDependenciesScope = allDependencies[YarnDependencyType.DEPENDENCIES]!!

                                YarnModuleInfo(
                                    packageDependency.id,
                                    packageDependency,
                                    packageDependency.collectDependencies(dependenciesInDependenciesScope)
                                )
                            }
                        }
                    }.orEmpty()

                    graphBuilder.addDependencies(project.id, dependencyType.type, dependenciesInfo)
                }
            }

        return allProjects
    }

    /**
     * Construct a Package or a Project by parsing its [packageInfo] representation generated by `yarn info`, run for
     * the given [definitionFile].
     * Additional data necessary for constructing the instances is read from [packagesHeaders] which should be the
     * package representations as a triple : rawName/type/locator, mapped by package id. Other additional data is read
     * from [packagesDetails] which should be the package details extracted from `yarn npm view`, mapped by id.
     * The objects constructed by this function are put either in [allPackages] or in [allProjects].
     * The list of dependencies of the constructed object is returned.
     */
    private fun parsePackage(
        packageInfo: PackageInfo,
        definitionFile: File,
        packagesHeaders: Map<String, PackageHeader>,
        packagesDetails: Map<String, AdditionalData>
    ): Map<YarnDependencyType, Pair<Identifier, List<Identifier>>> {
        val value = packageInfo.value
        val header = packagesHeaders[value]
        if (header == null) {
            issues += createAndLogIssue(
                managerName,
                "No package header found for '$value'.",
                Severity.ERROR
            )
            return emptyMap()
        }

        val (namespace, name) = splitNpmNamespaceAndName(header.rawName)
        val manifest = packageInfo.children.manifest
        val declaredLicenses = manifest.license.orEmpty().let { setOf(it).mapNpmLicenses() }
        var homepageUrl = manifest.homepage.orEmpty()

        val id = if (header.type == "workspace") {
            val version = packageInfo.children.version
            val projectFile = definitionFile.resolveSibling(header.version).resolve(definitionFile.name)
            val packageJson = parsePackageJson(projectFile)
            val additionalData = processAdditionalPackageInfo(packageJson)
            val authors = packageJson.authors
                .flatMap { parseAuthorString(it.name) }
                .mapNotNullTo(mutableSetOf()) { it.name }

            val id = Identifier("Yarn2", namespace, name, version)
            allProjects += id to Project(
                id = id.copy(type = managerName),
                definitionFilePath = VersionControlSystem.getPathInfo(projectFile).path,
                declaredLicenses = declaredLicenses,
                vcs = additionalData.vcsFromPackage,
                vcsProcessed = processProjectVcs(definitionFile.parentFile, additionalData.vcsFromPackage, homepageUrl),
                homepageUrl = homepageUrl,
                authors = authors
            )
            id
        } else {
            val version = header.version.cleanVersionString()
            val details = packagesDetails["${header.rawName}@$version"]

            if (details == null) {
                issues += createAndLogIssue(
                    managerName,
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

            allPackages += id to pkg
            id
        }

        val dependencies = processDependencies(packageInfo.children.dependencies)

        val dependencyToType = listDependenciesByType(definitionFile)

        // Sort all dependencies per scope. Notice that the logic is somehow lenient: If a dependency is not found
        // in this scope, it falls back in the 'dependencies' scope. This is due to the fact that the detection of
        // dependencies per scope is limited, because it relies on package.json parsing and only the project ones
        // are available.

        return YarnDependencyType.entries.associateWith { dependencyType ->
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
        ancestorPackageIds: Set<Identifier> = emptySet()
    ): Set<YarnModuleInfo> {
        val dependenciesIds = allDependencies[id]
        return dependenciesIds?.mapNotNull { dependencyId ->
            if (dependencyId in ancestorPackageIds) {
                logger.debug { "Not adding the dependency '$dependencyId' of package '$id' to prevent a cycle." }
                return@mapNotNull null
            }

            val dependencyPkg = allPackages[dependencyId]
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
        val vcsFromPackage = parseNpmVcsInfo(packageJson)
        val homepage = packageJson.homepage.orEmpty()
        val authors = packageJson.authors
            .flatMap { parseAuthorString(it.name) }
            .mapNotNullTo(mutableSetOf()) { it.name }
        val downloadUrl = packageJson.dist?.tarball.orEmpty().fixNpmDownloadUrl()

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

            val (locatorNamespace, locatorName) = splitNpmNamespaceAndName(locatorRawName)
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
                            managerName,
                            "Cannot build identifier for dependency '$locator.'",
                            Severity.ERROR
                        )
                    }.getOrNull()
                }
            }
        }.toList()

    override fun createPackageManagerResult(projectResults: Map<File, List<ProjectAnalyzerResult>>) =
        PackageManagerResult(projectResults, graphBuilder.build(), graphBuilder.packages())
}

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

    override fun dependenciesFor(dependency: YarnModuleInfo): List<YarnModuleInfo> = dependency.dependencies.toList()

    override fun linkageFor(dependency: YarnModuleInfo): PackageLinkage =
        if (dependency.pkg == null) PackageLinkage.PROJECT_DYNAMIC else PackageLinkage.DYNAMIC

    override fun createPackage(dependency: YarnModuleInfo, issues: MutableCollection<Issue>): Package? = dependency.pkg
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

private fun PackageJson.getScopeDependencies(type: YarnDependencyType) =
    when (type) {
        YarnDependencyType.DEPENDENCIES -> dependencies
        YarnDependencyType.DEV_DEPENDENCIES -> devDependencies
    }

private fun getYarnExecutable(workingDir: File): File {
    val yarnrcFile = workingDir.resolve(YARN2_RESOURCE_FILE)
    val yarnConfig = Yaml.default.parseToYamlNode(yarnrcFile.readText()).yamlMap
    val yarnPath = yarnConfig.get<YamlScalar>("yarnPath")?.content

    require(!yarnPath.isNullOrEmpty()) { "No Yarn 2+ executable could be found in '$YARN2_RESOURCE_FILE'." }

    val yarnExecutable = workingDir.resolve(yarnPath)

    // TODO: This is a security risk to blindly run code coming from a repository other than ORT's. ORT
    //       should download the Yarn2 binary from the official repository and run it.
    require(yarnExecutable.isFile) {
        "The Yarn 2+ program '${yarnExecutable.name}' does not exist."
    }

    if (!yarnExecutable.canExecute()) {
        Yarn2.logger.warn {
            "The Yarn 2+ program '${yarnExecutable.name}' should be executable. Changing its rights."
        }

        require(yarnExecutable.setExecutable(true)) {
            "Cannot set the Yarn 2+ program to be executable."
        }
    }

    return yarnExecutable
}

/**
 * Check whether Corepack is enabled based on the `package.json` file in [workingDir]. If no such file is found
 * or if it cannot be read, assume that this is not the case.
 */
private fun isCorepackEnabledInManifest(workingDir: File): Boolean =
    runCatching {
        val packageJson = parsePackageJson(workingDir.resolve(MANIFEST_FILE))
        !packageJson.packageManager.isNullOrEmpty()
    }.getOrDefault(false)

/**
 * Parse the [definitionFile] (package.json) to find the scope of a dependency. Unfortunately, `yarn info -A -R`
 * does not deliver this information.
 * Return the dependencies present in the file mapped to their scope.
 * See also https://classic.yarnpkg.com/en/docs/dependency-types (documentation for Yarn 1).
 */
private fun listDependenciesByType(definitionFile: File): Map<String, YarnDependencyType> {
    val packageJson = parsePackageJson(definitionFile)
    val result = mutableMapOf<String, YarnDependencyType>()

    YarnDependencyType.entries.forEach { dependencyType ->
        packageJson.getScopeDependencies(dependencyType).keys.forEach {
            result += it to dependencyType
        }
    }

    return result
}
