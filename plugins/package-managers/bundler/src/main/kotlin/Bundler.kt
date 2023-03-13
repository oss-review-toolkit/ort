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

package org.ossreviewtoolkit.plugins.packagemanagers.bundler

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.readValue

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.PrintStream
import java.net.HttpURLConnection

import kotlin.time.measureTime

import org.apache.logging.log4j.kotlin.Logging

import org.jruby.embed.LocalContextScope
import org.jruby.embed.PathType
import org.jruby.embed.ScriptingContainer

import org.ossreviewtoolkit.analyzer.AbstractPackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.downloader.VcsHost
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.PackageManagerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.model.yamlMapper
import org.ossreviewtoolkit.utils.common.AlphaNumericComparator
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.common.textValueOrEmpty
import org.ossreviewtoolkit.utils.ort.HttpDownloadError
import org.ossreviewtoolkit.utils.ort.OkHttpClientHelper
import org.ossreviewtoolkit.utils.ort.showStackTrace

/**
 * The path to the helper script resource that resolves a `Gemfile`'s top-level dependencies with group information.
 */
private const val ROOT_DEPENDENCIES_SCRIPT = "root_dependencies.rb"

/**
 * The path to the helper script resource that resolves a `Gemfile`'s dependencies.
 */
private const val RESOLVE_DEPENDENCIES_SCRIPT = "resolve_dependencies.rb"

/**
 * The name of the Bundler Gem.
 */
private const val BUNDLER_GEM_NAME = "bundler"

/**
 * The name of the file where Bundler stores locked down dependency information.
 */
internal const val BUNDLER_LOCKFILE_NAME = "Gemfile.lock"

private fun runScriptCode(code: String, workingDir: File? = null): String {
    val bytes = ByteArrayOutputStream()

    with(ScriptingContainer(LocalContextScope.THREADSAFE)) {
        output = PrintStream(bytes, /* autoFlush = */ true, "UTF-8")
        if (workingDir != null) currentDirectory = workingDir.path
        runScriptlet(code)
    }

    val stdout = bytes.toString()
    if (stdout.isEmpty()) throw IOException("Failed to run script code '$code'.")

    return stdout
}

private fun runScriptResource(resource: String, workingDir: File? = null): String {
    val bytes = ByteArrayOutputStream()

    with(ScriptingContainer(LocalContextScope.THREADSAFE)) {
        output = PrintStream(bytes, /* autoFlush = */ true, "UTF-8")
        if (workingDir != null) currentDirectory = workingDir.path
        runScriptlet(PathType.CLASSPATH, resource)
    }

    val stdout = bytes.toString()
    if (stdout.isEmpty()) throw IOException("Failed to run script resource '$resource'.")

    return stdout
}

internal fun parseBundlerVersionFromLockfile(lockfile: File): String? {
    val bundledWithLines = lockfile.readLines().dropWhile { it != "BUNDLED WITH" }
    if (bundledWithLines.size < 2) return null
    return bundledWithLines[1].trim()
}

/**
 * The [Bundler][1] package manager for Ruby. Also see [Clarifying the Roles of the .gemspec and Gemfile][2].
 *
 * This package manager supports the following [options][PackageManagerConfiguration.options]:
 * - *bundlerVersion*: The Bundler version to resolve dependencies for. By default, this is the highest version declared
 *   in any present lockfile, or the version that ships with JRuby if no lockfiles are present. In any case, the value
 *   can currently only be set to a higher version than the version that ships with JRuby.
 *
 * [1]: https://bundler.io/
 * [2]: http://yehudakatz.com/2010/12/16/clarifying-the-roles-of-the-gemspec-and-gemfile/
 */
class Bundler(
    name: String,
    analysisRoot: File,
    analyzerConfig: AnalyzerConfiguration,
    repoConfig: RepositoryConfiguration
) : PackageManager(name, analysisRoot, analyzerConfig, repoConfig) {
    companion object : Logging {
        /**
         * The name of the option to specify the Bundler version.
         */
        const val OPTION_BUNDLER_VERSION = "bundlerVersion"
    }

    class Factory : AbstractPackageManagerFactory<Bundler>("Bundler") {
        override val globsForDefinitionFiles = listOf("Gemfile")

        override fun create(
            analysisRoot: File,
            analyzerConfig: AnalyzerConfiguration,
            repoConfig: RepositoryConfiguration
        ) = Bundler(type, analysisRoot, analyzerConfig, repoConfig)
    }

    override fun beforeResolution(definitionFiles: List<File>) {
        // JRuby comes with Bundler as a default Gem, see e.g. [1]. Any manually installed Bundler version will only
        // take precedence if it is newer than JRuby's default version.
        //
        // TODO: Find a way to customize the Bundler version to an older version than JRuby's, see [2].
        //
        // [1]: https://github.com/jruby/jruby/blob/9.3.8.0/lib/pom.rb#L21
        // [2]: https://github.com/jruby/jruby/discussions/7403

        val lockfiles = definitionFiles.map { it.resolveSibling(BUNDLER_LOCKFILE_NAME) }.filter { it.isFile }
        val lockFilesBundlerVersion = lockfiles.mapNotNull {
            parseBundlerVersionFromLockfile(it)
        }.sortedWith(AlphaNumericComparator).lastOrNull()

        val bundlerVersion = options[OPTION_BUNDLER_VERSION] ?: lockFilesBundlerVersion

        if (bundlerVersion != null) {
            val duration = measureTime {
                val output = runScriptCode(
                    """
                    require 'rubygems/commands/install_command'
                    cmd = Gem::Commands::InstallCommand.new
                    cmd.handle_options ["--no-document", "--user-install", "$BUNDLER_GEM_NAME:$bundlerVersion"]
                    cmd.execute
                    """.trimIndent()
                ).trim()

                output.lines().forEach(logger::info)
            }

            logger.info { "Installing the '$BUNDLER_GEM_NAME' Gem in version $bundlerVersion took $duration." }
        }

        runCatching {
            runScriptCode("puts(Gem::Specification.find_by_name('$BUNDLER_GEM_NAME').version)").trim()
        }.onSuccess { installedBundlerVersion ->
            logger.info { "Using the '$BUNDLER_GEM_NAME' Gem in version $installedBundlerVersion." }
        }.onFailure {
            logger.warn { "Unable to determine the '$BUNDLER_GEM_NAME' Gem version." }
        }
    }

    override fun resolveDependencies(definitionFile: File, labels: Map<String, String>): List<ProjectAnalyzerResult> {
        val workingDir = definitionFile.parentFile

        requireLockfile(workingDir) { workingDir.resolve(BUNDLER_LOCKFILE_NAME).isFile }

        val scopes = mutableSetOf<Scope>()
        val issues = mutableListOf<Issue>()

        val gemSpecs = resolveGemsMetadata(workingDir)

        return with(parseProject(definitionFile, gemSpecs)) {
            val projectId = Identifier(managerName, "", name, version)
            val groupedDeps = getDependencyGroups(workingDir)

            groupedDeps.forEach { (groupName, dependencyList) ->
                parseScope(workingDir, projectId, groupName, dependencyList, scopes, gemSpecs, issues)
            }

            val project = Project(
                id = projectId,
                definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
                authors = authors,
                declaredLicenses = declaredLicenses,
                vcs = VcsInfo.EMPTY,
                vcsProcessed = processProjectVcs(workingDir, VcsInfo.EMPTY, homepageUrl),
                homepageUrl = homepageUrl,
                scopeDependencies = scopes.toSortedSet()
            )

            val allProjectDeps = groupedDeps.values.flatten().toSet()
            val hasBundlerDep = BUNDLER_GEM_NAME in allProjectDeps

            val packages = gemSpecs.values.mapNotNullTo(mutableSetOf()) { gemSpec ->
                getPackageFromGemspec(gemSpec).takeUnless { gemSpec.name == BUNDLER_GEM_NAME && !hasBundlerDep }
            }

            listOf(ProjectAnalyzerResult(project, packages, issues))
        }
    }

    private fun parseScope(
        workingDir: File, projectId: Identifier, groupName: String, dependencyList: List<String>,
        scopes: MutableSet<Scope>, gemSpecs: MutableMap<String, GemSpec>, issues: MutableList<Issue>
    ) {
        logger.debug {
            "Parsing scope '$groupName' with top-level dependencies $dependencyList for project " +
                    "'${projectId.toCoordinates()}' in '$workingDir'."
        }

        val scopeDependencies = mutableSetOf<PackageReference>()

        dependencyList.forEach {
            parseDependency(workingDir, projectId, it, gemSpecs, scopeDependencies, issues)
        }

        scopes += Scope(groupName, scopeDependencies.toSortedSet())
    }

    private fun parseDependency(
        workingDir: File, projectId: Identifier, gemName: String, gemSpecs: MutableMap<String, GemSpec>,
        scopeDependencies: MutableSet<PackageReference>, issues: MutableList<Issue>
    ) {
        logger.debug { "Parsing dependency '$gemName'." }

        runCatching {
            val gemSpec = gemSpecs.getValue(gemName)
            val gemId = Identifier("Gem", "", gemSpec.name, gemSpec.version)

            // The project itself can be listed as a dependency if the project is a gem (i.e. there is a .gemspec file
            // for it, and the Gemfile refers to it). In that case, skip querying Rubygems and adding Package and
            // PackageReference objects and continue with the projects dependencies.
            if (gemId == projectId) {
                gemSpec.runtimeDependencies.forEach {
                    parseDependency(workingDir, projectId, it, gemSpecs, scopeDependencies, issues)
                }
            } else {
                queryRubygems(gemId.name, gemId.version)?.apply {
                    gemSpecs[gemName] = merge(gemSpec)
                }

                val transitiveDependencies = mutableSetOf<PackageReference>()

                gemSpec.runtimeDependencies.forEach {
                    parseDependency(workingDir, projectId, it, gemSpecs, transitiveDependencies, issues)
                }

                scopeDependencies += PackageReference(gemId, dependencies = transitiveDependencies.toSortedSet())
            }
        }.onFailure {
            it.showStackTrace()

            issues += createAndLogIssue(
                source = managerName,
                message = "Failed to parse dependency '$gemName' of project '${projectId.toCoordinates()}' in " +
                        "'$workingDir': ${it.collectMessages()}"
            )
        }
    }

    private fun getDependencyGroups(workingDir: File): Map<String, List<String>> =
        yamlMapper.readValue(runScriptResource(ROOT_DEPENDENCIES_SCRIPT, workingDir))

    private fun resolveGemsMetadata(workingDir: File): MutableMap<String, GemSpec> {
        val stdout = runScriptResource(RESOLVE_DEPENDENCIES_SCRIPT, workingDir)

        // The metadata produced by the "bundler_resolve_dependencies.rb" script separates specs for packages with the
        // "\0" character as delimiter.
        val gemSpecs = stdout.split('\u0000').dropWhile { it.startsWith("Fetching gem metadata") }.map {
            GemSpec.createFromMetadata(yamlMapper.readTree(it))
        }.associateByTo(mutableMapOf()) {
            it.name
        }

        return gemSpecs
    }

    private fun parseProject(definitionFile: File, gemSpecs: MutableMap<String, GemSpec>) =
        getGemspecFile(definitionFile.parentFile)?.let { gemspecFile ->
            // Project is a Gem, i.e. a library.
            gemSpecs[gemspecFile.nameWithoutExtension]
        } ?: GemSpec(
            name = getFallbackProjectName(analysisRoot, definitionFile),
            version = "",
            homepageUrl = "",
            authors = emptySet(),
            declaredLicenses = emptySet(),
            description = "",
            runtimeDependencies = emptySet(),
            vcs = VcsInfo.EMPTY,
            artifact = RemoteArtifact.EMPTY
        )

    private fun getPackageFromGemspec(gemSpec: GemSpec): Package {
        val gemId = Identifier("Gem", "", gemSpec.name, gemSpec.version)

        return Package(
            id = gemId,
            authors = gemSpec.authors,
            declaredLicenses = gemSpec.declaredLicenses,
            description = gemSpec.description,
            homepageUrl = gemSpec.homepageUrl,
            binaryArtifact = RemoteArtifact.EMPTY,
            sourceArtifact = gemSpec.artifact,
            vcs = gemSpec.vcs,
            vcsProcessed = processPackageVcs(gemSpec.vcs, gemSpec.homepageUrl)
        )
    }

    private fun getGemspecFile(workingDir: File) =
        workingDir.walk().maxDepth(1).filter { it.isFile && it.extension == "gemspec" }.firstOrNull()

    private fun queryRubygems(name: String, version: String, retryCount: Int = 3): GemSpec? {
        // See http://guides.rubygems.org/rubygems-org-api-v2/.
        val url = "https://rubygems.org/api/v2/rubygems/$name/versions/$version.yaml"

        return OkHttpClientHelper.downloadText(url).mapCatching {
            GemSpec.createFromGem(yamlMapper.readTree(it))
        }.onFailure {
            val error = (it as? HttpDownloadError) ?: run {
                logger.warn { "Unable to retrieve metadata for gem '$name' from RubyGems: ${it.message}" }
                return null
            }

            when (error.code) {
                HttpURLConnection.HTTP_NOT_FOUND -> logger.info { "Gem '$name' was not found on RubyGems." }

                OkHttpClientHelper.HTTP_TOO_MANY_REQUESTS -> {
                    throw IOException(
                        "RubyGems reported too many requests when requesting metadata for gem '$name', see " +
                                "https://guides.rubygems.org/rubygems-org-api/#rate-limits."
                    )
                }

                HttpURLConnection.HTTP_BAD_GATEWAY -> {
                    if (retryCount > 0) {
                        // We see a lot of sporadic "bad gateway" responses that disappear when trying again.
                        Thread.sleep(100)
                        return queryRubygems(name, version, retryCount - 1)
                    }

                    throw IOException(
                        "RubyGems reported too many bad gateway errors when requesting metadata for gem '$name'."
                    )
                }

                else -> {
                    throw IOException(
                        "RubyGems reported unhandled HTTP code ${error.code} when requesting metadata for gem '$name'."
                    )
                }
            }
        }.getOrNull()
    }
}

internal data class GemSpec(
    val name: String,
    val version: String,
    val homepageUrl: String,
    val authors: Set<String>,
    val declaredLicenses: Set<String>,
    val description: String,
    val runtimeDependencies: Set<String>,
    val vcs: VcsInfo,
    val artifact: RemoteArtifact
) {
    companion object {
        fun createFromMetadata(node: JsonNode): GemSpec {
            val runtimeDependencies = node["dependencies"]?.asIterable()?.mapNotNull { dependency ->
                dependency["name"]?.textValue()?.takeIf { dependency["type"]?.textValue() == ":runtime" }
            }?.toSet()

            val homepage = node["homepage"].textValueOrEmpty()
            return GemSpec(
                node["name"].textValue(),
                node["version"]["version"].textValue(),
                homepage,
                node["authors"]?.toList().mapToSetOfNotEmptyStrings(),
                node["licenses"]?.toList().mapToSetOfNotEmptyStrings(),
                node["description"].textValueOrEmpty(),
                runtimeDependencies.orEmpty(),
                VcsHost.parseUrl(homepage),
                RemoteArtifact.EMPTY
            )
        }

        fun createFromGem(node: JsonNode): GemSpec {
            val runtimeDependencies = node["dependencies"]?.get("runtime")?.mapNotNull { dependency ->
                dependency["name"]?.textValue()
            }?.toSet()

            val vcs = when {
                node.hasNonNull("source_code_uri") -> VcsHost.parseUrl(node["source_code_uri"].textValue())
                node.hasNonNull("homepage_uri") -> VcsHost.parseUrl(node["homepage_uri"].textValue())
                else -> VcsInfo.EMPTY
            }

            val artifact = if (node.hasNonNull("gem_uri") && node.hasNonNull("sha")) {
                val sha = node["sha"].textValue()
                RemoteArtifact(node["gem_uri"].textValue(), Hash.create(sha))
            } else {
                RemoteArtifact.EMPTY
            }

            return GemSpec(
                node["name"].textValue(),
                node["version"].textValue(),
                node["homepage_uri"].textValueOrEmpty(),
                node["authors"].textValueOrEmpty().split(',').mapToSetOfNotEmptyStrings(),
                node["licenses"]?.toList().mapToSetOfNotEmptyStrings(),
                node["description"].textValueOrEmpty(),
                runtimeDependencies.orEmpty(),
                vcs,
                artifact
            )
        }

        private inline fun <reified T> Collection<T>?.mapToSetOfNotEmptyStrings(): Set<String> =
            this?.mapNotNullTo(mutableSetOf()) { entry ->
                val text = when (T::class) {
                    JsonNode::class -> (entry as JsonNode).textValue()
                    else -> entry.toString()
                }

                text?.trim()?.takeIf { it.isNotEmpty() }
            } ?: emptySet()
    }

    fun merge(other: GemSpec): GemSpec {
        require(name == other.name && version == other.version) {
            "Cannot merge specs for different gems."
        }

        return GemSpec(
            name,
            version,
            homepageUrl.takeUnless { it.isEmpty() } ?: other.homepageUrl,
            authors.takeUnless { it.isEmpty() } ?: other.authors,
            declaredLicenses.takeUnless { it.isEmpty() } ?: other.declaredLicenses,
            description.takeUnless { it.isEmpty() } ?: other.description,
            runtimeDependencies.takeUnless { it.isEmpty() } ?: other.runtimeDependencies,
            vcs.takeUnless { it == VcsInfo.EMPTY } ?: other.vcs,
            artifact.takeUnless { it == RemoteArtifact.EMPTY } ?: other.artifact
        )
    }
}
