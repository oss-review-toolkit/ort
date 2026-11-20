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
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.Includes
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.OrtPluginOption
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.utils.common.AlphaNumericComparator
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.ort.showStackTrace

private const val PROJECT_TYPE = "Bundler"
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
     * `platforms` restrictions in a Gemfile match, e.g. "java" matches `:jruby` and any other platform matches `:mri`.
     * If the platform is not declared in a present lockfile, dependencies get re-resolved for it.
     */
    @OrtPluginOption(defaultValue = "ruby")
    val platform: String
)

/**
 * The [Bundler](https://bundler.io/) package manager for Ruby. Also see [Clarifying the Roles of the .gemspec and
 * Gemfile](http://yehudakatz.com/2010/12/16/clarifying-the-roles-of-the-gemspec-and-gemfile/).
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

        val scopes = mutableSetOf<Scope>()
        val issues = mutableListOf<Issue>()

        val gemsInfo = resolveGemsInfo(definitionFile)

        return with(parseProject(analysisRoot, definitionFile, gemsInfo)) {
            val projectId = Identifier(projectType, "", name, version)
            val groupedDeps = getDependencyGroups(definitionFile)

            groupedDeps.forEach { (groupName, dependencyList) ->
                parseScope(workingDir, projectId, groupName, dependencyList, scopes, gemsInfo, issues)
            }

            val project = Project(
                id = projectId,
                definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
                authors = authors,
                declaredLicenses = declaredLicenses,
                vcs = VcsInfo.EMPTY,
                vcsProcessed = processProjectVcs(workingDir, VcsInfo.EMPTY, homepageUrl),
                homepageUrl = homepageUrl,
                scopeDependencies = scopes
            )

            val allProjectDeps = groupedDeps.values.flatten().toSet()
            val hasBundlerDep = BUNDLER_GEM_NAME in allProjectDeps

            val packages = gemsInfo.values.mapNotNullTo(mutableSetOf()) { gemInfo ->
                gemInfo.toPackage().takeUnless { gemInfo.name == BUNDLER_GEM_NAME && !hasBundlerDep }
            }

            listOf(ProjectAnalyzerResult(project, packages, issues))
        }
    }

    private fun parseScope(
        workingDir: File,
        projectId: Identifier,
        groupName: String,
        dependencyList: List<String>,
        scopes: MutableSet<Scope>,
        gemsInfo: MutableMap<String, GemInfo>,
        issues: MutableList<Issue>
    ) {
        logger.debug {
            "Parsing scope '$groupName' with top-level dependencies $dependencyList for project " +
                "'${projectId.toCoordinates()}' in '$workingDir'."
        }

        val scopeDependencies = mutableSetOf<PackageReference>()

        dependencyList.forEach {
            parseDependency(workingDir, projectId, it, gemsInfo, scopeDependencies, issues)
        }

        scopes += Scope(groupName, scopeDependencies)
    }

    private fun parseDependency(
        workingDir: File,
        projectId: Identifier,
        gemName: String,
        gemsInfo: MutableMap<String, GemInfo>,
        scopeDependencies: MutableSet<PackageReference>,
        issues: MutableList<Issue>
    ) {
        logger.debug { "Parsing dependency '$gemName'." }

        runCatching {
            val gemInfo = gemsInfo.getValue(gemName)
            val gemId = Identifier(PACKAGE_TYPE, "", gemInfo.name, gemInfo.version)

            // The project itself can be listed as a dependency if the project is a gem (i.e. there is a .gemspec file
            // for it, and the Gemfile refers to it). In that case, skip querying RubyGems and adding Package and
            // PackageReference objects and continue with the projects dependencies.
            if (gemId == projectId) {
                gemInfo.runtimeDependencies.forEach {
                    parseDependency(workingDir, projectId, it, gemsInfo, scopeDependencies, issues)
                }
            } else {
                queryRubyGems(gemId.name, gemId.version, gemInfo.platform)?.apply {
                    gemsInfo[gemName] = merge(gemInfo)
                }

                val transitiveDependencies = mutableSetOf<PackageReference>()

                gemInfo.runtimeDependencies.forEach {
                    parseDependency(workingDir, projectId, it, gemsInfo, transitiveDependencies, issues)
                }

                scopeDependencies += PackageReference(gemId, dependencies = transitiveDependencies)
            }
        }.onFailure {
            it.showStackTrace()

            issues += createAndLogIssue(
                "Failed to parse dependency '$gemName' of project '${projectId.toCoordinates()}' in '$workingDir': " +
                    it.collectMessages()
            )
        }
    }

    private fun getScriptEnvironment(gemfile: File): Map<String, String> =
        buildMap {
            putAll(SCRIPT_ENVIRONMENT)
            put("BUNDLE_GEMFILE", gemfile.absolutePath)
            put("ORT_BUNDLER_PLATFORM", config.platform)
        }

    @Suppress("UNCHECKED_CAST")
    private fun getDependencyGroups(gemfile: File): Map<String, List<String>> =
        runScript(
            ROOT_DEPENDENCIES_SCRIPT_RESOURCE_NAME,
            PathType.CLASSPATH,
            environment = getScriptEnvironment(gemfile)
        ) as Map<String, List<String>>

    private fun resolveGemsInfo(gemfile: File): MutableMap<String, GemInfo> {
        val specs = runScript(
            RESOLVE_DEPENDENCIES_SCRIPT_RESOURCE_NAME,
            PathType.CLASSPATH,
            environment = getScriptEnvironment(gemfile)
        ).toString()

        // Guard against the case when a custom platform excludes all dependencies.
        if (specs.isBlank()) return mutableMapOf()

        // The metadata produced by the "resolve_dependencies.rb" script separates specs for packages with the "\0"
        // character as delimiter.
        val gemsInfo = specs.split('\u0000').map {
            val spec = YAML.decodeFromString<GemSpec>(it)
            GemInfo.createFromMetadata(spec)
        }.associateByTo(mutableMapOf()) {
            it.name
        }

        return gemsInfo
    }
}
