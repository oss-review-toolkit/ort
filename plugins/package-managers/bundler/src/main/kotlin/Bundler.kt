/*
 * Copyright (C) 2017 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.packagemanagers.bundler

import java.io.File

import kotlinx.serialization.decodeFromString

import org.apache.logging.log4j.kotlin.logger

import org.jruby.embed.PathType

import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.PackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManagerResult
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.Includes
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.model.utils.DependencyGraphBuilder
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.OrtPluginOption
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.utils.common.AlphaNumericComparator
import org.ossreviewtoolkit.utils.common.collectMessages

internal const val PROJECT_TYPE = "Bundler"
internal const val PACKAGE_TYPE = "Gem"

/** The name of the helper script resource that resolves a `Gemfile`'s top-level dependencies with group information. */
private const val ROOT_DEPENDENCIES_SCRIPT_RESOURCE_NAME = "root_dependencies.rb"

/** The name of the helper script resource that resolves a `Gemfile`'s dependencies. */
private const val RESOLVE_DEPENDENCIES_SCRIPT_RESOURCE_NAME = "resolve_dependencies.rb"

/** The name of the Bundler Gem. */
private const val BUNDLER_GEM_NAME = "bundler"

/** The name of the file where Bundler stores locked down dependency information. */
internal const val BUNDLER_LOCKFILE_NAME = "Gemfile.lock"

data class BundlerConfig(
    /**
     * The Bundler version to resolve dependencies for. By default, this is the highest version declared in any present
     * lockfile, or the version that ships with JRuby if no lockfiles are present. In any case, the value can currently
     * only be set to a higher version than the version that ships with JRuby.
     */
    val bundlerVersion: String?,

    /**
     * The platform to resolve dependencies for, like "ruby", "java" or "x86_64-linux". This also determines which
     * `platforms` restrictions in a Gemfile match: "java" matches `:jruby`, "ruby" and native Unix platforms match
     * `:mri`, and Windows platforms match `:windows`. Dependencies get re-resolved if the lockfile does not cover the
     * platform.
     */
    @OrtPluginOption(defaultValue = "ruby")
    val platform: String
)

/**
 * The [Bundler](https://bundler.io/) package manager for Ruby. Also see [Clarifying the Roles of the .gemspec and
 * Gemfile](https://yehudakatz.com/2010/12/16/clarifying-the-roles-of-the-gemspec-and-gemfile/).
 */
@OrtPlugin(
    displayName = "Bundler",
    summary = "The Bundler package manager for Ruby.",
    factory = PackageManagerFactory::class
)
class Bundler(
    override val descriptor: PluginDescriptor = BundlerFactory.descriptor,
    private val config: BundlerConfig
) : PackageManager(PROJECT_TYPE) {
    private val graphBuilder = DependencyGraphBuilder(BundlerDependencyHandler)

    override val globsForDefinitionFiles = listOf("Gemfile")

    override fun beforeResolution(
        analysisRoot: File,
        definitionFiles: List<File>,
        analyzerConfig: AnalyzerConfiguration
    ) {
        // JRuby comes with Bundler as a default Gem, see e.g. [1]. Any manually installed Bundler version will only
        // take precedence if it is newer than JRuby's default version.
        //
        // TODO: Find a way to customize the Bundler version to an older version than JRuby's, see [2].
        //
        // [1]: https://github.com/jruby/jruby/blob/9.4.15.0/lib/pom.rb#L30
        // [2]: https://github.com/jruby/jruby/discussions/7403

        val lockfiles = definitionFiles.map { it.resolveSibling(BUNDLER_LOCKFILE_NAME) }.filter { it.isFile }
        val lockfilesBundlerVersion = lockfiles.mapNotNull {
            parseBundlerVersionFromLockfile(it)
        }.sortedWith(AlphaNumericComparator).lastOrNull()
        val bundlerVersion = config.bundlerVersion ?: lockfilesBundlerVersion

        if (bundlerVersion != null) {
            logger.info { "Installing custom '$BUNDLER_GEM_NAME' version $bundlerVersion..." }

            runCatching {
                val code = """
                    require 'rubygems/commands/install_command'
                    cmd = Gem::Commands::InstallCommand.new
                    cmd.handle_options ["--no-document", "--user-install", "$BUNDLER_GEM_NAME:$bundlerVersion"]
                    cmd.execute
                """.trimIndent()
                val result = runScript(code, environment = SCRIPT_ENVIRONMENT) as Long
                check(result == 0L) { "Error code $result." }
            }.onSuccess {
                logger.info { "Installing '$BUNDLER_GEM_NAME' version $bundlerVersion succeeded." }
            }.onFailure {
                logger.info { "Installing '$BUNDLER_GEM_NAME' version $bundlerVersion faied. ${it.collectMessages()}" }
            }
        }

        runCatching {
            val code = "Gem::Specification.find_by_name('$BUNDLER_GEM_NAME').version.to_s"
            runScript(code, environment = SCRIPT_ENVIRONMENT) as String
        }.onSuccess { installedBundlerVersion ->
            logger.info { "Using '$BUNDLER_GEM_NAME' in version $installedBundlerVersion." }
        }.onFailure {
            logger.warn { "Unable to determine the '$BUNDLER_GEM_NAME' version: ${it.collectMessages()}" }
        }
    }

    override fun resolveDependencies(
        analysisRoot: File,
        definitionFile: File,
        excludes: Excludes,
        includes: Includes,
        analyzerConfig: AnalyzerConfiguration,
        labels: Map<String, String>
    ): List<ProjectAnalyzerResult> {
        val workingDir = definitionFile.parentFile

        requireLockfileForStableVersions(analysisRoot, definitionFile, analyzerConfig.allowDynamicVersions) {
            workingDir.resolve(BUNDLER_LOCKFILE_NAME).isFile
        }

        val gemsInfo = resolveGemsInfo(definitionFile)
        val context = BundlerProjectContext(gemsInfo)

        val issues = mutableListOf<Issue>()

        return with(parseProject(analysisRoot, definitionFile, gemsInfo)) {
            val projectId = Identifier(projectType, "", name, version)

            getDependencyGroups(definitionFile).forEach { (groupName, gemNames) ->
                val dependencies = gemNames.mapNotNull { gemName ->
                    gemsInfo[gemName]?.let { BundlerDependency(it, context) } ?: run {
                        issues += createAndLogIssue(
                            "The dependency '$gemName' of project '${projectId.toCoordinates()}' in '$workingDir' " +
                                "was not resolved."
                        )

                        null
                    }
                }

                graphBuilder.addDependencies(projectId, groupName, dependencies)
            }

            val project = Project(
                id = projectId,
                definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
                authors = authors,
                declaredLicenses = declaredLicenses,
                vcs = VcsInfo.EMPTY,
                vcsProcessed = processProjectVcs(workingDir, VcsInfo.EMPTY, homepageUrl),
                homepageUrl = homepageUrl,
                scopeNames = graphBuilder.scopesFor(projectId)
            )

            listOf(ProjectAnalyzerResult(project, packages = emptySet(), issues))
        }
    }

    override fun createPackageManagerResult(projectResults: Map<File, List<ProjectAnalyzerResult>>) =
        PackageManagerResult(projectResults, graphBuilder.build(), graphBuilder.packages())

    private fun getScriptEnvironment(gemfile: File): Map<String, String> =
        buildMap {
            putAll(SCRIPT_ENVIRONMENT)
            put("BUNDLE_GEMFILE", gemfile.absolutePath)
            put("ORT_BUNDLER_PLATFORM", config.platform)
        }

    private fun getDependencyGroups(gemfile: File): Map<String, List<String>> {
        val groups = runScript(
            ROOT_DEPENDENCIES_SCRIPT_RESOURCE_NAME,
            PathType.CLASSPATH,
            environment = getScriptEnvironment(gemfile)
        ).toString()

        return YAML.decodeFromString(groups)
    }

    private fun resolveGemsInfo(gemfile: File): Map<String, GemInfo> {
        val specs = runScript(
            RESOLVE_DEPENDENCIES_SCRIPT_RESOURCE_NAME,
            PathType.CLASSPATH,
            environment = getScriptEnvironment(gemfile)
        ).toString()

        // Guard against the case when a custom platform excludes all dependencies.
        if (specs.isBlank()) return emptyMap()

        // The metadata produced by the "resolve_dependencies.rb" script separates specs for packages with the "\0"
        // character as delimiter.
        return specs.split('\u0000').map {
            val spec = YAML.decodeFromString<GemSpec>(it)
            GemInfo.createFromMetadata(spec)
        }.associateBy {
            it.name
        }
    }
}
