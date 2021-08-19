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

@file:Suppress("TooManyFunctions")

package org.ossreviewtoolkit.analyzer.managers

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode

import com.vdurmont.semver4j.Requirement

import java.io.File
import java.net.URLEncoder
import java.util.SortedSet

import org.ossreviewtoolkit.analyzer.AbstractPackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.PackageManagerResult
import org.ossreviewtoolkit.analyzer.managers.utils.expandNpmShortcutUrl
import org.ossreviewtoolkit.analyzer.managers.utils.hasNpmLockFile
import org.ossreviewtoolkit.analyzer.managers.utils.mapDefinitionFilesForNpm
import org.ossreviewtoolkit.analyzer.managers.utils.readProxySettingsFromNpmRc
import org.ossreviewtoolkit.analyzer.managers.utils.readRegistryFromNpmRc
import org.ossreviewtoolkit.analyzer.parseAuthorString
import org.ossreviewtoolkit.downloader.VcsHost
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.DependencyGraph
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.model.jsonMapper
import org.ossreviewtoolkit.model.orEmpty
import org.ossreviewtoolkit.model.readJsonFile
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.model.utils.DependencyGraphBuilder
import org.ossreviewtoolkit.spdx.SpdxConstants
import org.ossreviewtoolkit.utils.CommandLineTool
import org.ossreviewtoolkit.utils.OkHttpClientHelper
import org.ossreviewtoolkit.utils.Os
import org.ossreviewtoolkit.utils.fieldNamesOrEmpty
import org.ossreviewtoolkit.utils.installAuthenticatorAndProxySelector
import org.ossreviewtoolkit.utils.isSymbolicLink
import org.ossreviewtoolkit.utils.log
import org.ossreviewtoolkit.utils.realFile
import org.ossreviewtoolkit.utils.stashDirectories
import org.ossreviewtoolkit.utils.textValueOrEmpty

const val PUBLIC_NPM_REGISTRY = "https://registry.npmjs.org"

/**
 * The [Node package manager](https://www.npmjs.com/) for JavaScript.
 */
open class Npm(
    name: String,
    analysisRoot: File,
    analyzerConfig: AnalyzerConfiguration,
    repoConfig: RepositoryConfiguration
) : PackageManager(name, analysisRoot, analyzerConfig, repoConfig), CommandLineTool {
    class Factory : AbstractPackageManagerFactory<Npm>("NPM") {
        override val globsForDefinitionFiles = listOf("package.json")

        override fun create(
            analysisRoot: File,
            analyzerConfig: AnalyzerConfiguration,
            repoConfig: RepositoryConfiguration
        ) = Npm(managerName, analysisRoot, analyzerConfig, repoConfig)
    }

    companion object {
        /** Name of the scope with the regular dependencies. */
        private const val DEPENDENCIES_SCOPE = "dependencies"

        /** Name of the scope with optional dependencies. */
        private const val OPTIONAL_DEPENDENCIES_SCOPE = "optionalDependencies"

        /** Name of the scope with development dependencies. */
        private const val DEV_DEPENDENCIES_SCOPE = "devDependencies"

        /**
         * Parse information about licenses from the [package.json][json] file of a module.
         */
        internal fun parseLicenses(json: JsonNode): SortedSet<String> {
            val declaredLicenses = mutableListOf<String>()

            // See https://docs.npmjs.com/files/package.json#license. Some old packages use a "license" (singular) node
            // which ...
            json["license"]?.let { licenseNode ->
                // ... can either be a direct text value, an array of text values (which is not officially supported),
                // or an object containing nested "type" (and "url") text nodes.
                when {
                    licenseNode.isTextual -> declaredLicenses += licenseNode.textValue()
                    licenseNode.isArray -> licenseNode.mapNotNullTo(declaredLicenses) { it.textValue() }
                    licenseNode.isObject -> declaredLicenses += licenseNode["type"].textValue()
                    else -> throw IllegalArgumentException("Unsupported node type in '$licenseNode'.")
                }
            }

            // New packages use a "licenses" (plural) node containing an array of objects with nested "type" (and "url")
            // text nodes.
            json["licenses"]?.mapNotNullTo(declaredLicenses) { licenseNode ->
                licenseNode["type"]?.textValue()
            }

            return declaredLicenses.mapNotNullTo(sortedSetOf()) { declaredLicense ->
                when {
                    // NPM does not mean https://unlicense.org/ here, but the wish to not "grant others the right to use
                    // a private or unpublished package under any terms", which corresponds to SPDX's "NONE".
                    declaredLicense == "UNLICENSED" -> SpdxConstants.NONE

                    // NPM allows to declare non-SPDX licenses only by referencing a license file. Avoid reporting an
                    // [OrtIssue] by mapping this to a valid license identifier.
                    declaredLicense.startsWith("SEE LICENSE IN ") -> "LicenseRef-ort-unknown-license-reference"

                    else -> declaredLicense.takeUnless { it.isBlank() }
                }
            }
        }

        /**
         * Parse information about the author from the [package.json][json] file of a module. According to
         * https://docs.npmjs.com/files/package.json#people-fields-author-contributors, there are two formats to
         * specify the author of a package: An object with multiple properties or a single string.
         */
        internal fun parseAuthors(json: JsonNode): SortedSet<String> =
            sortedSetOf<String>().apply {
                json["author"]?.let { authorNode ->
                    when {
                        authorNode.isObject -> authorNode["name"]?.textValue()
                        authorNode.isTextual -> parseAuthorString(authorNode.textValue(), '<', '(')
                        else -> null
                    }
                }?.let { add(it) }
            }

        /**
         * Parse information about the VCS from the [package.json][node] file of a module.
         */
        internal fun parseVcsInfo(node: JsonNode): VcsInfo {
            // See https://github.com/npm/read-package-json/issues/7 for some background info.
            val head = node["gitHead"].textValueOrEmpty()

            return node["repository"]?.let { repo ->
                val type = repo["type"].textValueOrEmpty()
                val url = repo.textValue() ?: repo["url"].textValueOrEmpty()
                val path = repo["directory"].textValueOrEmpty()

                VcsInfo(
                    type = VcsType(type),
                    url = expandNpmShortcutUrl(url),
                    revision = head,
                    path = path
                )
            } ?: VcsInfo(
                type = VcsType.UNKNOWN,
                url = "",
                revision = head
            )
        }

        /**
         * Construct a [Package] by parsing its _package.json_ file and - if applicable - querying additional
         * content from the [npmRegistry]. Result is a [Pair] with the raw identifier and the new package.
         */
        @Suppress("HttpUrlsUsage")
        internal fun parsePackage(packageFile: File, npmRegistry: String): Pair<String, Package> {
            val packageDir = packageFile.parentFile

            log.debug { "Found a 'package.json' file in '$packageDir'." }

            val json = packageFile.readValue<ObjectNode>()
            val rawName = json["name"].textValue()
            val (namespace, name) = splitNamespaceAndName(rawName)
            val version = json["version"].textValue()

            val declaredLicenses = parseLicenses(json)
            val authors = parseAuthors(json)

            var description = json["description"].textValueOrEmpty()
            var homepageUrl = json["homepage"].textValueOrEmpty()
            var downloadUrl = expandNpmShortcutUrl(json["_resolved"].textValueOrEmpty()).ifEmpty {
                // If the normalized form of the specified dependency contains a URL as the version, expand and use it.
                val fromVersion = json["_from"].textValueOrEmpty().substringAfterLast('@')
                expandNpmShortcutUrl(fromVersion).takeIf { it != fromVersion }.orEmpty()
            }

            var vcsFromPackage = parseVcsInfo(json)

            val identifier = "$rawName@$version"

            var hash = Hash.create(json["_integrity"].textValueOrEmpty())

            // Download package info from registry.npmjs.org.
            // TODO: check if unpkg.com can be used as a fallback in case npmjs.org is down.
            val encodedName = if (rawName.startsWith("@")) {
                "@${URLEncoder.encode(rawName.substringAfter('@'), "UTF-8")}"
            } else {
                rawName
            }

            if (packageDir.isSymbolicLink()) {
                val realPackageDir = packageDir.realFile()

                log.debug { "The package directory '$packageDir' links to '$realPackageDir'." }

                // Yarn workspaces refer to project dependencies from the same workspace via symbolic links. Use that
                // as the trigger to get VcsInfo locally instead of querying the NPM registry.
                log.debug { "Resolving the package info for '$identifier' locally from '$realPackageDir'." }

                val vcsFromDirectory = VersionControlSystem.forDirectory(realPackageDir)?.getInfo().orEmpty()
                vcsFromPackage = vcsFromPackage.merge(vcsFromDirectory)
            } else {
                log.debug { "Resolving the package info for '$identifier' via NPM registry." }

                OkHttpClientHelper.downloadText("$npmRegistry/$encodedName").onSuccess {
                    val packageInfo = jsonMapper.readTree(it)

                    packageInfo["versions"]?.get(version)?.let { versionInfo ->
                        description = versionInfo["description"].textValueOrEmpty()
                        homepageUrl = versionInfo["homepage"].textValueOrEmpty()

                        versionInfo["dist"]?.let { dist ->
                            downloadUrl = dist["tarball"].textValueOrEmpty()
                                // Work around the issue described at
                                // https://npm.community/t/some-packages-have-dist-tarball-as-http-and-not-https/285/19.
                                .replace("http://registry.npmjs.org/", "https://registry.npmjs.org/")

                            hash = Hash.create(dist["shasum"].textValueOrEmpty())
                        }

                        vcsFromPackage = parseVcsInfo(versionInfo)
                    }
                }.onFailure {
                    log.info {
                        "Could not retrieve package information for '$encodedName' from NPM registry $npmRegistry: " +
                                it.message
                    }
                }
            }

            val vcsFromDownloadUrl = VcsHost.toVcsInfo(downloadUrl)
            if (vcsFromDownloadUrl.url != downloadUrl) {
                vcsFromPackage = vcsFromPackage.merge(vcsFromDownloadUrl)
            }

            val module = Package(
                id = Identifier(
                    type = "NPM",
                    namespace = namespace,
                    name = name,
                    version = version
                ),
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
                "Generated package info for $identifier has no name."
            }

            require(module.id.version.isNotEmpty()) {
                "Generated package info for $identifier has no version."
            }

            return Pair(identifier, module)
        }

        /**
         * Split the given [rawName] of a module to a pair with namespace and name.
         */
        internal fun splitNamespaceAndName(rawName: String): Pair<String, String> {
            val name = rawName.substringAfterLast("/")
            val namespace = rawName.removeSuffix(name).removeSuffix("/")
            return Pair(namespace, name)
        }
    }

    private val ortProxySelector = installAuthenticatorAndProxySelector()

    private val npmRegistry: String

    init {
        val npmRcFile = Os.userHomeDirectory.resolve(".npmrc")
        npmRegistry = if (npmRcFile.isFile) {
            val npmRcContent = npmRcFile.readText()
            ortProxySelector.addProxies(managerName, readProxySettingsFromNpmRc(npmRcContent))
            readRegistryFromNpmRc(npmRcContent) ?: PUBLIC_NPM_REGISTRY
        } else {
            PUBLIC_NPM_REGISTRY
        }
    }

    private val graphBuilder: DependencyGraphBuilder<NpmModuleInfo> =
        DependencyGraphBuilder(NpmDependencyHandler(npmRegistry))

    /**
     * Array of parameters passed to the install command when installing dependencies.
     */
    protected open val installParameters = arrayOf("--ignore-scripts")

    protected open fun hasLockFile(projectDir: File) = hasNpmLockFile(projectDir)

    override fun command(workingDir: File?) = if (Os.isWindows) "npm.cmd" else "npm"

    override fun getVersionRequirement(): Requirement = Requirement.buildNPM("5.7.* - 7.20.*")

    override fun mapDefinitionFiles(definitionFiles: List<File>) = mapDefinitionFilesForNpm(definitionFiles).toList()

    override fun beforeResolution(definitionFiles: List<File>) {
        // We do not actually depend on any features specific to an NPM version, but we still want to stick to a
        // fixed minor version to be sure to get consistent results.
        checkVersion(analyzerConfig.ignoreToolVersions)
    }

    override fun afterResolution(definitionFiles: List<File>) {
        ortProxySelector.removeProxyOrigin(managerName)
    }

    override fun createPackageManagerResult(projectResults: Map<File, List<ProjectAnalyzerResult>>) =
        PackageManagerResult(projectResults, graphBuilder.build(), graphBuilder.packages())

    override fun resolveDependencies(definitionFile: File): List<ProjectAnalyzerResult> {
        val workingDir = definitionFile.parentFile

        stashDirectories(workingDir.resolve("node_modules")).use {
            // Actually installing the dependencies is the easiest way to get the meta-data of all transitive
            // dependencies (i.e. their respective "package.json" files). As NPM uses a global cache, the same
            // dependency is only ever downloaded once.
            installDependencies(workingDir)

            // Create packages for all modules found in the workspace and add them to the graph builder. They are
            // reused when they are referenced by scope dependencies.
            val packages = parseInstalledModules(workingDir)
            graphBuilder.addPackages(packages.values)

            val project = parseProject(definitionFile)

            val scopeNames = listOfNotNull(
                // Optional dependencies are just like regular dependencies except that NPM ignores failures when
                // installing them (see https://docs.npmjs.com/files/package.json#optionaldependencies), i.e. they are
                // not a separate scope in our semantics.
                buildDependencyGraphForScopes(
                    project,
                    workingDir,
                    setOf(DEPENDENCIES_SCOPE, OPTIONAL_DEPENDENCIES_SCOPE),
                    DEPENDENCIES_SCOPE
                ),

                buildDependencyGraphForScopes(
                    project,
                    workingDir,
                    setOf(DEV_DEPENDENCIES_SCOPE),
                    DEV_DEPENDENCIES_SCOPE
                )
            )

            // TODO: add support for peerDependencies and bundledDependencies.

            return listOf(
                ProjectAnalyzerResult(project.copy(scopeNames = scopeNames.toSortedSet()), sortedSetOf())
            )
        }
    }

    private fun parseInstalledModules(rootDirectory: File): Map<String, Package> {
        val packages = mutableMapOf<String, Package>()
        val nodeModulesDir = rootDirectory.resolve("node_modules")

        log.info { "Searching for 'package.json' files in '$nodeModulesDir'..." }

        nodeModulesDir.walk().filter {
            it.name == "package.json" && isValidNodeModulesDirectory(nodeModulesDir, nodeModulesDirForPackageJson(it))
        }.forEach { file ->
            val (id, pkg) = parsePackage(file, npmRegistry)
            packages[id] = pkg
        }

        return packages
    }

    private fun isValidNodeModulesDirectory(rootModulesDir: File, modulesDir: File?): Boolean {
        if (modulesDir == null) {
            return false
        }

        var currentDir: File = modulesDir
        while (currentDir != rootModulesDir) {
            if (currentDir.name != "node_modules") {
                return false
            }

            currentDir = currentDir.parentFile.parentFile
            if (currentDir.name.startsWith("@")) {
                currentDir = currentDir.parentFile
            }
        }

        return true
    }

    private fun nodeModulesDirForPackageJson(packageJson: File): File? {
        var modulesDir = packageJson.parentFile.parentFile
        if (modulesDir.name.startsWith("@")) {
            modulesDir = modulesDir.parentFile
        }

        return modulesDir.takeIf { it.name == "node_modules" }
    }

    /**
     * Retrieve all the dependencies of [project] from the given [scopes] and add them to the dependency graph under
     * the given [targetScope]. Return the target scope name if dependencies are found; *null* otherwise.
     */
    private fun buildDependencyGraphForScopes(
        project: Project,
        workingDir: File,
        scopes: Set<String>,
        targetScope: String
    ): String? {
        val qualifiedScopeName = DependencyGraph.qualifyScope(project, targetScope)
        val moduleDependencies = getModuleDependencies(workingDir, scopes)

        moduleDependencies.forEach { graphBuilder.addDependency(qualifiedScopeName, it) }

        return targetScope.takeUnless { moduleDependencies.isEmpty() }
    }

    private fun getPackageReferenceForMissingModule(moduleName: String, rootModuleDir: File): PackageReference {
        val issue = createAndLogIssue(
            source = managerName,
            message = "Package '$moduleName' was not installed, because the package file could not be found " +
                    "anywhere in '$rootModuleDir'. This might be fine if the module was not installed because it is " +
                    "specific to a different platform.",
            severity = Severity.WARNING
        )

        val (namespace, name) = splitNamespaceAndName(moduleName)

        return PackageReference(
            id = Identifier(managerName, namespace, name, ""),
            issues = listOf(issue)
        )
    }

    private fun findWorkspaceSubmodules(moduleDir: File): List<File> {
        val nodeModulesDir = moduleDir.resolve("node_modules")
        if (!nodeModulesDir.isDirectory) return emptyList()

        val searchDirs = nodeModulesDir.walk().maxDepth(1).filter {
            it.isDirectory && it.name.startsWith("@")
        }.toList() + nodeModulesDir

        return searchDirs.flatMap { dir ->
            dir.walk().maxDepth(1).filter { it.isDirectory && it.isSymbolicLink() }.toList()
        }
    }

    private fun getModuleDependencies(moduleDir: File, scopes: Set<String>): Set<NpmModuleInfo> {
        val workspaceModuleDirs = findWorkspaceSubmodules(moduleDir)

        return mutableSetOf<NpmModuleInfo>().apply {
            addAll(getModuleInfo(moduleDir, scopes)!!.dependencies)

            workspaceModuleDirs.forEach { workspaceModuleDir ->
                addAll(getModuleInfo(workspaceModuleDir, scopes, listOf(moduleDir))!!.dependencies)
            }
        }
    }

    private fun getModuleInfo(
        moduleDir: File,
        scopes: Set<String>,
        ancestorModuleDirs: List<File> = emptyList(),
        ancestorModuleIds: List<Identifier> = emptyList(),
        packageType: String = managerName
    ): NpmModuleInfo? {
        val moduleInfo = parsePackageJson(moduleDir, scopes)
        val dependencies = mutableSetOf<NpmModuleInfo>()
        val moduleId = splitNamespaceAndName(moduleInfo.name).let { (namespace, name) ->
            Identifier(packageType, namespace, name, moduleInfo.version)
        }

        val cycleStartIndex = ancestorModuleIds.indexOf(moduleId)
        if (cycleStartIndex >= 0) {
            val cycle = (ancestorModuleIds.subList(cycleStartIndex, ancestorModuleIds.size) + moduleId)
                .joinToString(" -> ")
            log.debug { "Not adding dependency '$moduleId' to avoid cycle: $cycle." }
            return null
        }

        log.debug { "Building dependency tree for '${moduleInfo.name}' from directory '$moduleDir'." }

        val pathToRoot = listOf(moduleDir) + ancestorModuleDirs
        moduleInfo.dependencyNames.forEach { dependencyName ->
            val dependencyModuleDirPath = findDependencyModuleDir(dependencyName, pathToRoot)

            if (dependencyModuleDirPath.isNotEmpty()) {
                val dependencyModuleDir = dependencyModuleDirPath.first()
                log.debug { "Found module dir for '$dependencyName' at '$dependencyModuleDir'." }

                getModuleInfo(
                    moduleDir = dependencyModuleDir,
                    scopes = setOf("dependencies", "optionalDependencies"),
                    ancestorModuleDirs = dependencyModuleDirPath.subList(1, dependencyModuleDirPath.size),
                    ancestorModuleIds = ancestorModuleIds + moduleId,
                    packageType = "NPM"
                )?.let { dependencies += it }

                return@forEach
            }

            log.debug { "Could not find module dir for '$dependencyName' within: '${pathToRoot.joinToString()}'." }
            getPackageReferenceForMissingModule(dependencyName, pathToRoot.first())
        }

        return NpmModuleInfo(moduleId, moduleInfo.packageJson, dependencies)
    }

    /**
     * An internally used data class with information about a module retrieved from the module's package.json. This
     * information is further processed and eventually converted to an [NpmModuleInfo] object containing everything
     * required by the Npm package manager.
     */
    private data class RawModuleInfo(
        val name: String,
        val version: String,
        val dependencyNames: Set<String>,
        val packageJson: File
    )

    private fun parsePackageJson(moduleDir: File, scopes: Set<String>): RawModuleInfo {
        val packageJsonFile = moduleDir.resolve("package.json")
        val json = readJsonFile(packageJsonFile)

        val name = json["name"].textValueOrEmpty()
        if (name.isBlank()) {
            log.warn {
                "The '$packageJsonFile' does not set a name, which is only allowed for unpublished packages."
            }
        }

        val version = json["version"].textValueOrEmpty()
        if (version.isBlank()) {
            log.warn {
                "The '$packageJsonFile' does not set a version, which is only allowed for unpublished packages."
            }
        }

        val dependencyNames = scopes.flatMapTo(mutableSetOf()) { scope ->
            // Yarn ignores "//" keys in the dependencies to allow comments, therefore ignore them here as well.
            json[scope].fieldNamesOrEmpty().asSequence().filterNot { it == "//" }
        }

        return RawModuleInfo(
            name = name,
            version = version,
            dependencyNames = dependencyNames,
            packageJson = packageJsonFile
        )
    }

    private fun findDependencyModuleDir(dependencyName: String, searchModuleDirs: List<File>): List<File> {
        searchModuleDirs.forEachIndexed { index, moduleDir ->
            // Note: resolve() also works for scoped dependencies, e.g. dependencyName = "@x/y"
            val dependencyModuleDir = moduleDir.resolve("node_modules/$dependencyName")
            if (dependencyModuleDir.isDirectory) {
                return listOf(dependencyModuleDir) + searchModuleDirs.subList(index, searchModuleDirs.size)
            }
        }
        return emptyList()
    }

    private fun parseProject(packageJson: File): Project {
        log.debug { "Parsing project info from '$packageJson'." }

        val json = jsonMapper.readTree(packageJson)

        val rawName = json["name"].textValueOrEmpty()
        val (namespace, name) = splitNamespaceAndName(rawName)
        if (name.isBlank()) {
            log.warn { "'$packageJson' does not define a name." }
        }

        val version = json["version"].textValueOrEmpty()
        if (version.isBlank()) {
            log.warn { "'$packageJson' does not define a version." }
        }

        val declaredLicenses = parseLicenses(json)
        val authors = parseAuthors(json)
        val homepageUrl = json["homepage"].textValueOrEmpty()
        val projectDir = packageJson.parentFile
        val vcsFromPackage = parseVcsInfo(json)

        return Project(
            id = Identifier(
                type = managerName,
                namespace = namespace,
                name = name,
                version = version
            ),
            definitionFilePath = VersionControlSystem.getPathInfo(packageJson).path,
            authors = authors,
            declaredLicenses = declaredLicenses,
            vcs = vcsFromPackage,
            vcsProcessed = processProjectVcs(projectDir, vcsFromPackage, homepageUrl),
            homepageUrl = homepageUrl
        )
    }

    /**
     * Install dependencies using the given package manager command.
     */
    private fun installDependencies(workingDir: File) {
        requireLockfile(workingDir) { hasLockFile(workingDir) }

        // Install all NPM dependencies to enable NPM to list dependencies.
        if (hasLockFile(workingDir) && this::class.java == Npm::class.java) {
            run(workingDir, "ci")
        } else {
            run(workingDir, "install", *installParameters)
        }

        // TODO: Capture warnings from npm output, e.g. "Unsupported platform" which happens for fsevents on all
        //       platforms except for Mac.
    }
}
