/*
 * Copyright (C) 2026 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.packagemanagers.platformio

import java.io.File

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.PackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManagerResult
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.Includes
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.model.utils.DependencyGraphBuilder
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.div
import org.ossreviewtoolkit.utils.common.stashDirectories

import org.semver4j.range.RangeList
import org.semver4j.range.RangeListFactory

private const val PROJECT_TYPE = "PlatformIO"
internal const val PACKAGE_TYPE = "PlatformIO"

private const val DEFINITION_FILE = "platformio.ini"
private const val PROJECT_LIBRARY_DIR = "lib"
private const val LIBRARY_MANIFEST_FILE = "library.json"
private const val PROPERTIES_MANIFEST_FILE = "library.properties"
internal const val PACKAGE_METADATA_FILE = ".piopm"
private const val ENVIRONMENT_SECTION_PREFIX = "env:"

internal object PlatformIoCommand : CommandLineTool {
    override fun command(workingDir: File?) = "pio"

    override fun transformVersion(output: String) =
        // PlatformIO reports version strings like:
        // PlatformIO Core, version 6.1.13
        output.substringAfterLast("version").trim()

    override fun getVersionRequirement(): RangeList = RangeListFactory.create(">=6.0.0")
}

/**
 * The [PlatformIO](https://platformio.org/) package manager for embedded C / C++ projects.
 *
 * This implementation resolves, for each build environment defined in a `platformio.ini` file:
 * - the libraries declared via `lib_deps`, as well as any "private" libraries PlatformIO automatically includes from
 *   the project's own "lib" directory
 * - the platform declared via `platform`, together with its framework, toolchain, and other packages (including any
 *   `platform_packages` overrides)
 *
 * For each environment, `pio pkg install` is used to ensure all `lib_deps` (including transitive libraries) are
 * installed. Libraries are installed into project-local storage, and their resolved versions and metadata are read
 * from the "library.json" and ".piopm" files found there; see [readLibraries] for how libraries from the project's
 * own "lib" directory are incorporated. Platform, framework, and packages are installed into a global package store
 * shared by all PlatformIO projects; see [readPlatform] for how those belonging to a given environment are determined.
 */
@OrtPlugin(
    id = "PlatformIO",
    displayName = "PlatformIO",
    summary = "The PlatformIO package manager for embedded C / C++ projects.",
    factory = PackageManagerFactory::class
)
class PlatformIo(
    override val descriptor: PluginDescriptor = PlatformIoFactory.descriptor
) : PackageManager(PROJECT_TYPE) {
    override val globsForDefinitionFiles = listOf(DEFINITION_FILE)

    private val graphBuilder = DependencyGraphBuilder(PlatformIoDependencyHandler())

    override fun beforeResolution(
        analysisRoot: File,
        definitionFiles: List<File>,
        analyzerConfig: AnalyzerConfiguration
    ) = PlatformIoCommand.checkVersion()

    override fun resolveDependencies(
        analysisRoot: File,
        definitionFile: File,
        excludes: Excludes,
        includes: Includes,
        analyzerConfig: AnalyzerConfiguration,
        labels: Map<String, String>
    ): List<ProjectAnalyzerResult> {
        val workingDir = definitionFile.parentFile
        val projectId = Identifier(
            type = projectType,
            namespace = "",
            name = getFallbackProjectName(analysisRoot, definitionFile),
            version = ""
        )

        val coreDir = getCoreDir(workingDir)
        val issues = mutableListOf<Issue>()
        val onIssue = { message: String -> issues += createAndLogIssue(message, Severity.WARNING) }

        val environments = stashDirectories(workingDir / ".pio" / "libdeps").use {
            val environments = getEnvironments(workingDir)

            environments.forEach { environment ->
                PlatformIoCommand.run(
                    workingDir, "pkg", "install", "-e", environment, "--silent"
                ).requireSuccess()

                val libraryRoots = readLibraries(
                    workingDir / ".pio" / "libdeps" / environment,
                    workingDir / PROJECT_LIBRARY_DIR,
                    onIssue
                )
                val platform = readPlatform(workingDir, environment, coreDir, onIssue)

                graphBuilder.addDependencies(projectId, environment, libraryRoots + listOfNotNull(platform))
            }

            environments
        }

        val project = Project(
            id = projectId,
            definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
            declaredLicenses = emptySet(),
            vcs = VcsInfo.EMPTY,
            vcsProcessed = processProjectVcs(workingDir),
            homepageUrl = "",
            scopeNames = environments.toSet()
        )

        return listOf(ProjectAnalyzerResult(project, emptySet(), issues))
    }

    override fun createPackageManagerResult(projectResults: Map<File, List<ProjectAnalyzerResult>>) =
        PackageManagerResult(projectResults, graphBuilder.build(), graphBuilder.packages())
}

/**
 * Return the names of the PlatformIO build environments declared in the `platformio.ini` file inside [workingDir].
 */
private fun getEnvironments(workingDir: File): List<String> {
    val output = PlatformIoCommand.run(workingDir, "project", "config", "--json-output").requireSuccess().stdout
    val sections = JSON.parseToJsonElement(output).jsonArray

    return sections.mapNotNull { sectionElement ->
        val sectionName = sectionElement.jsonArray[0].jsonPrimitive.content
        sectionName.takeIf { it.startsWith(ENVIRONMENT_SECTION_PREFIX) }?.removePrefix(ENVIRONMENT_SECTION_PREFIX)
    }
}

/**
 * Read all libraries relevant to a single environment, combining those installed via `lib_deps` in [libdepsDir]
 * with any "private" libraries PlatformIO automatically includes from the project's own [projectLibDir]
 * ("lib/" by default), regardless of whether they are also declared via `lib_deps`.
 *
 * Subdirectories of [projectLibDir] are not required to have a manifest. It is common for first-party code to be
 * merely organized as a "private" library for organization purposes; these are silently ignored.
 */
private fun readLibraries(libdepsDir: File, projectLibDir: File, onIssue: (String) -> Unit): List<PlatformIoPackage> {
    val installedLibraries = libdepsDir.listFiles { file -> file.isDirectory }.orEmpty().mapNotNull { dir ->
        readRawLibrary(dir) ?: run {
            onIssue("Could not find or read a supported manifest for the library installed at '${dir.path}'.")
            null
        }
    }

    val projectLibraries = projectLibDir.listFiles { file -> file.isDirectory }.orEmpty()
        .mapNotNull { readRawLibrary(it) }

    // Give a library in the project's own "lib" directory priority over a same-named library installed via
    // "lib_deps", mirroring PlatformIO's own library search order ("lib/" is searched before ".pio/libdeps/").
    val rawLibraries = (installedLibraries + projectLibraries).associateBy { it.manifest.name }

    val resolved = mutableMapOf<String, PlatformIoPackage>()

    fun resolve(name: String): PlatformIoPackage? {
        resolved[name]?.let { return it }
        val raw = rawLibraries[name] ?: return null

        val library = PlatformIoPackage(raw.manifest, raw.authors, raw.owner, raw.version)
        // Register the library before resolving its dependencies to guard against cycles.
        resolved[name] = library
        library.dependencies = raw.dependencies.mapNotNull { resolve(it.name) }

        return library
    }

    val transitiveNames = rawLibraries.values.flatMapTo(mutableSetOf()) { raw -> raw.dependencies.map { it.name } }
    val rootNames = rawLibraries.keys - transitiveNames

    return rootNames.mapNotNull { resolve(it) }
}

private class RawLibrary(
    val manifest: LibraryManifest,
    val authors: Set<String>,
    val owner: String?,
    val version: String,
    val dependencies: List<LibraryDependency>
)

/**
 * Read a library's manifest from [dir].
 *
 * Prefers a project's PlatformIO "library.json" file over a legacy Arduino "library.properties" file.
 */
private fun readRawLibrary(dir: File): RawLibrary? {
    val parsed = runCatching {
        (dir / LIBRARY_MANIFEST_FILE).takeIf { it.isFile }?.let { parseLibraryManifest(it.readText()) }
            ?: (dir / PROPERTIES_MANIFEST_FILE).takeIf { it.isFile }?.let { parseLibraryProperties(it.readText()) }
    }.getOrNull() ?: return null

    val metadata = runCatching {
        (dir / PACKAGE_METADATA_FILE).takeIf { it.isFile }?.let { parsePackageMetadata(it.readText()) }
    }.getOrNull()

    return RawLibrary(
        manifest = parsed.manifest,
        authors = parsed.authors,
        owner = metadata?.spec?.owner,
        version = metadata?.version ?: parsed.manifest.version.orEmpty(),
        dependencies = parsed.dependencies
    )
}
