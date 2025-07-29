/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.go

import java.io.File

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.decodeToSequence

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.PackageManagerFactory
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageLinkage
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.orEmpty
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.plugins.packagemanagers.go.utils.Graph
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.div
import org.ossreviewtoolkit.utils.common.splitOnWhitespace
import org.ossreviewtoolkit.utils.common.stashDirectories
import org.ossreviewtoolkit.utils.ort.createOrtTempDir

import org.semver4j.range.RangeList
import org.semver4j.range.RangeListFactory

internal object GoCommand : CommandLineTool {
    private val goPath by lazy { createOrtTempDir() }

    private val goEnvironment by lazy {
        mapOf(
            "GOPATH" to goPath.absolutePath,
            "GOPROXY" to "direct",
            "GOWORK" to "off"
        )
    }

    override fun command(workingDir: File?) = "go"

    override fun getVersionArguments() = "version"

    override fun transformVersion(output: String) = output.removePrefix("go version go").substringBefore(' ')

    override fun getVersionRequirement(): RangeList = RangeListFactory.create(">=1.21.1")

    override fun run(vararg args: CharSequence, workingDir: File?, environment: Map<String, String>) =
        super.run(args = args, workingDir, environment + goEnvironment)
}

/**
 * The [Go Modules](https://go.dev/ref/mod) package manager for Go. Also see the [usage and troubleshooting guide]
 * (https://github.com/golang/go/wiki/Modules).
 *
 * Note: The file `go.sum` is not a lockfile as Go modules already allows for reproducible builds without that file.
 * Thus, no logic for handling the [AnalyzerConfiguration.allowDynamicVersions] is needed.
 */
@OrtPlugin(
    displayName = "GoMod",
    description = "The Go Modules package manager for Go.",
    factory = PackageManagerFactory::class
)
class GoMod(override val descriptor: PluginDescriptor = GoModFactory.descriptor) : PackageManager("GoMod") {
    override val globsForDefinitionFiles = listOf("go.mod")

    override fun mapDefinitionFiles(
        analysisRoot: File,
        definitionFiles: List<File>,
        analyzerConfig: AnalyzerConfiguration
    ): List<File> =
        definitionFiles.filterNot { definitionFile ->
            "vendor" in definitionFile
                .parentFile
                .relativeTo(analysisRoot)
                .invariantSeparatorsPath
                .split('/')
        }

    override fun resolveDependencies(
        analysisRoot: File,
        definitionFile: File,
        excludes: Excludes,
        analyzerConfig: AnalyzerConfiguration,
        labels: Map<String, String>
    ): List<ProjectAnalyzerResult> {
        val projectDir = definitionFile.parentFile

        stashDirectories(projectDir / "vendor").use { _ ->
            val moduleInfoForModuleName = getModuleInfos(projectDir, "all").associateBy { it.path }
            val graph = getModuleGraph(projectDir, moduleInfoForModuleName)
            val packages = graph.nodes.mapNotNullTo(mutableSetOf()) {
                moduleInfoForModuleName.getValue(it.name).toPackage(analysisRoot)
            }

            val projectVcs = processProjectVcs(projectDir)
            val mainScopeModules = getTransitiveMainModuleDependencies(projectDir).let { moduleNames ->
                graph.nodes.filterTo(mutableSetOf()) { it.name in moduleNames }
            }

            val scopes = setOf(
                Scope(
                    name = "main",
                    dependencies = graph.subgraph(mainScopeModules)
                        .toPackageReferences(analysisRoot, moduleInfoForModuleName)
                ),
                Scope(
                    name = "vendor",
                    dependencies = graph.toPackageReferences(analysisRoot, moduleInfoForModuleName)
                )
            )

            return listOf(
                ProjectAnalyzerResult(
                    project = Project(
                        id = moduleInfoForModuleName.values.single { it.main }.toId(analysisRoot),
                        definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
                        authors = emptySet(), // Go mod doesn't support author information.
                        declaredLicenses = emptySet(), // Go mod doesn't support declared licenses.
                        vcs = projectVcs,
                        vcsProcessed = projectVcs,
                        homepageUrl = "",
                        scopeDependencies = scopes
                    ),
                    packages = packages
                )
            )
        }
    }

    /**
     * Return the module graph output from `go mod graph` with non-vendor dependencies removed.
     */
    private fun getModuleGraph(projectDir: File, moduleInfoForModuleName: Map<String, ModuleInfo>): Graph<GoModule> {
        fun GoModule.hasModuleInfo() = name in moduleInfoForModuleName
        fun moduleInfo(moduleName: String): ModuleInfo = moduleInfoForModuleName.getValue(moduleName)

        fun parseModuleEntry(entry: String): GoModule =
            GoModule(
                name = entry.substringBefore('@'),
                version = entry.substringAfter('@', "")
            )

        val mainModule = moduleInfoForModuleName.values.single { it.main }.run {
            GoModule(path, normalizeModuleVersion(version))
        }

        var graph = Graph<GoModule>().apply { addNode(mainModule) }

        val edges = GoCommand.run("mod", "graph", workingDir = projectDir).requireSuccess()

        edges.stdout.lines().forEach { line ->
            if (line.isBlank()) return@forEach

            val columns = line.splitOnWhitespace()
            require(columns.size == 2) { "Expected exactly one occurrence of ' ' on any non-blank line." }

            val parent = parseModuleEntry(columns[0])
            val child = parseModuleEntry(columns[1])

            if (!parent.hasModuleInfo() || !child.hasModuleInfo()) {
                // As of go version 1.21.1 the module graph contains a version constraint for the go version for
                // the main project. The edge would be filtered out below by getVendorModules(). However, ignore
                // it already here as there is no module info for 'go' and potentially also for 'go's transitive
                // dependencies, so parseModuleEntry() would fail.
                logger.debug {
                    "Skip edge from '${parent.name}' to '${child.name}' due to missing module info."
                }

                return@forEach
            }

            if (moduleInfo(parent.name).main && moduleInfo(child.name).indirect) {
                logger.debug {
                    "Module '${child.name}' is an indirect dependency of '${parent.name}. Skip adding edge."
                }

                return@forEach
            }

            graph.addEdge(parent, child)
        }

        val vendorModules = getVendorModules(graph, projectDir, mainModule.name)
        if (vendorModules.size < graph.size) {
            logger.debug {
                "Removing ${graph.size - vendorModules.size} non-vendor modules from the dependency graph."
            }

            graph = graph.subgraph(vendorModules)
        }

        return graph.breakCycles()
    }

    /**
     * Return a sequence of module information for [packages] in [projectDir].
     */
    private fun getModuleInfos(projectDir: File, vararg packages: String): Sequence<ModuleInfo> {
        projectDir.resolve("go.mod").also { goModFile ->
            require(goModFile.isFile) {
                "The expected '$goModFile' file does not exist."
            }
        }

        val list = GoCommand.run("list", "-m", "-json", "-buildvcs=false", *packages, workingDir = projectDir)
            .requireSuccess()

        return list.stdout.byteInputStream().use { JSON.decodeToSequence<ModuleInfo>(it) }
    }

    /**
     * Return the module names of all transitive main module dependencies. This excludes test-only dependencies.
     */
    private fun getTransitiveMainModuleDependencies(projectDir: File): Set<String> {
        // See https://pkg.go.dev/text/template for the format syntax.
        val list = GoCommand.run("list", "-deps", "-json=Module", "-buildvcs=false", "./...", workingDir = projectDir)
            .requireSuccess()

        val depInfos = list.stdout.byteInputStream().use { JSON.decodeToSequence<DepInfo>(it) }

        return depInfos.mapNotNullTo(mutableSetOf()) { depInfo ->
            depInfo.module?.path
        }
    }

    /**
     * Return the subset of the modules in [graph] required for building and testing the main module. So, test
     * dependencies of dependencies are filtered out. The [GoModule]s in [Graph] must not have the replace directive
     * applied.
     */
    private fun getVendorModules(graph: Graph<GoModule>, projectDir: File, mainModuleName: String): Set<GoModule> {
        val vendorModuleNames = mutableSetOf(mainModuleName)

        graph.nodes.chunked(WHY_CHUNK_SIZE).forEach { ids ->
            val moduleNames = ids.map { it.name }.toTypedArray()
            // Use the ´-m´ switch to use module names because the graph also uses module names, not package names.
            // This fixes the accidental dropping of some modules.
            val why = GoCommand.run("mod", "why", "-m", "-vendor", *moduleNames, workingDir = projectDir)
                .requireSuccess()

            vendorModuleNames += parseWhyOutput(why.stdout)
        }

        return graph.nodes.filterTo(mutableSetOf()) { it.name in vendorModuleNames }
    }

    private fun ModuleInfo.toId(analysisRoot: File): Identifier {
        if (replace != null) return replace.toId(analysisRoot) // Apply replace directive.

        return if (version.isBlank()) {
            // If the version is blank, it is a project in ORT speak.
            checkNotNull(dir) { "For projects, the directory is expected to not be null." }

            val projectDir = File(dir).absoluteFile

            require(projectDir.startsWith(analysisRoot)) {
                "A replace directive references a module in '$projectDir' outside of analysis root which is not " +
                    "supported."
            }

            Identifier(
                type = projectType,
                namespace = "",
                name = getModuleInfos(projectDir).single().path,
                version = processProjectVcs(projectDir).revision
            )
        } else {
            // If the version is not blank, it is a package in ORT speak.
            Identifier(
                type = "Go",
                namespace = "",
                name = path,
                version = normalizeModuleVersion(version)
            )
        }
    }

    private fun ModuleInfo.toPackage(analysisRoot: File): Package? {
        // A ModuleInfo with blank version should be represented by a Project:
        if (version.isBlank()) return null

        // Apply the replace directive:
        if (replace != null) return replace.toPackage(analysisRoot)

        val vcsInfo = toVcsInfo().orEmpty()

        return Package(
            id = toId(analysisRoot),
            authors = emptySet(), // Go mod doesn't support author information.
            declaredLicenses = emptySet(), // Go mod doesn't support declared licenses.
            description = "",
            homepageUrl = "",
            binaryArtifact = RemoteArtifact.EMPTY,
            sourceArtifact = if (vcsInfo == VcsInfo.EMPTY) {
                toSourceArtifact()
            } else {
                RemoteArtifact.EMPTY
            },
            vcs = vcsInfo
        )
    }

    /**
     * Convert this [Graph] to a set of [PackageReference]s that spawn the dependency trees of the direct dependencies
     * of the main module. The graph must not contain any cycles, so [Graph.breakCycles] should be called
     * before.
     */
    private fun Graph<GoModule>.toPackageReferences(
        analysisRoot: File,
        moduleInfoForModuleName: Map<String, ModuleInfo>
    ): Set<PackageReference> {
        fun getPackageReference(module: GoModule): PackageReference {
            val dependencies = getDependencies(module).mapTo(mutableSetOf()) {
                getPackageReference(it)
            }

            return PackageReference(
                id = moduleInfoForModuleName.getValue(module.name).toId(analysisRoot),
                linkage = PackageLinkage.PROJECT_STATIC,
                dependencies = dependencies
            )
        }

        val mainModule = moduleInfoForModuleName.values.single { it.main }.run {
            GoModule(path, version)
        }

        return getDependencies(mainModule).mapTo(mutableSetOf()) { getPackageReference(it) }
    }
}

/** Separator string indicating that data of a new package follows in the output of the go mod why command. */
private const val PACKAGE_SEPARATOR = "# "

/**
 * Constant for the number of modules to pass to the _go mod why_ command in a single step. This value is chosen rather
 * arbitrarily to keep the command's output to a reasonable size.
 */
private const val WHY_CHUNK_SIZE = 32

private val JSON = Json { ignoreUnknownKeys = true }

private const val DEFAULT_GO_PROXY = "https://proxy.golang.org"

@Serializable
private data class ModuleInfo(
    @SerialName("Path")
    val path: String,

    @SerialName("Version")
    val version: String = "",

    @SerialName("Replace")
    val replace: ModuleInfo? = null,

    @SerialName("Indirect")
    val indirect: Boolean = false,

    @SerialName("Main")
    val main: Boolean = false,

    @SerialName("GoMod")
    val goMod: String? = null,

    @SerialName("Dir")
    val dir: String? = null
)

@Serializable
private data class DepInfo(
    @SerialName("Module")
    val module: ModuleInfo? = null
)

private data class GoModule(
    val name: String,
    val version: String
) {
    override fun toString(): String =
        if (version.isBlank()) {
            name
        } else {
            "$name@$version"
        }
}

/**
 * The format of `.info` files the Go command line tools cache under '$GOPATH/pkg/mod'.
 */
@Serializable
private data class ModuleInfoFile(
    @SerialName("Origin")
    val origin: Origin
) {
    @Serializable
    data class Origin(
        @SerialName("VCS")
        val vcs: String? = null,
        @SerialName("URL")
        val url: String? = null,
        @SerialName("Ref")
        val ref: String? = null,
        @SerialName("Hash")
        val hash: String? = null,
        @SerialName("Subdir")
        val subdir: String? = null
    )
}

private fun ModuleInfo.toSourceArtifact(): RemoteArtifact {
    // The below construction of the remote artifact URL simply assumes the module to be available at Go's default
    // proxy, which might not always hold.
    return RemoteArtifact(url = "$DEFAULT_GO_PROXY/$path/@v/$version.zip", hash = Hash.NONE)
}

private fun ModuleInfo.toVcsInfo(): VcsInfo? {
    val escapedVersion = escapeModuleVersion(version)
    val infoFile = goMod?.let { File(it).resolveSibling("$escapedVersion.info") } ?: return null
    val info = infoFile.inputStream().use { JSON.decodeFromStream<ModuleInfoFile>(it) }
    val type = info.origin.vcs?.let { VcsType.forName(it) }.takeIf { it == VcsType.GIT } ?: return null

    return VcsInfo(
        type = type,
        url = checkNotNull(info.origin.url),
        revision = checkNotNull(info.origin.hash),
        path = info.origin.subdir.orEmpty()
    )
}

/**
 * Parse the given [output] generated by the _go mod why_ command and the names of modules that are reported to be used
 * by the main module. See https://golang.org/ref/mod#go-mod-why.
 */
internal fun parseWhyOutput(output: String): Set<String> {
    val usedModules = mutableSetOf<String>()
    var currentModule: String? = null

    output.lineSequence().forEach { line ->
        if (line.startsWith(PACKAGE_SEPARATOR)) {
            currentModule = line.substring(PACKAGE_SEPARATOR.length)
        } else if (!line.startsWith('(') && line.isNotBlank()) {
            currentModule?.let { usedModules += it }
        }
    }

    return usedModules
}

/**
 * Module paths appear as substrings of file system paths in the module cache on the file system.
 * Go does not rely on the file system to be case-sensitive. For this reason, Go has decided to
 * replace every uppercase letter in file system paths with an exclamation mark followed by the
 * letter's lowercase equivalent.
 *
 * Details behind the reasoning and implementation in Go can be found in the Go source code at
 * [module.go](https://github.com/golang/go/blob/5b6d3dea8744311825fd544a73edb8d26d9c7e98/src/cmd/vendor/golang.org/x/mod/module/module.go#L33-L42C64)
 */
internal fun escapeModuleVersion(version: String): String {
    require("!" !in version) { "Module versions must not contain exclamation marks: $version" }
    return version.replace(upperCaseCharRegex) { "!${it.value.lowercase()}" }
}

private val upperCaseCharRegex = Regex("[A-Z]")

/**
 * Return the given [moduleVersion] normalized to a Semver compliant version. The `v` prefix gets stripped and
 * also any suffix starting with '+', because build metadata is not involved in version comparison according to
 * https://go.dev/ref/mod#incompatible-versions.
 */
private fun normalizeModuleVersion(moduleVersion: String): String = moduleVersion.removePrefix("v").substringBefore("+")
