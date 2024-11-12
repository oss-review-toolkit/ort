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

import com.charleskorn.kaml.Yaml

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.PrintStream
import java.net.HttpURLConnection

import kotlin.time.measureTime

import kotlinx.serialization.serializer

import org.apache.logging.log4j.kotlin.logger

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
import org.ossreviewtoolkit.model.orEmpty
import org.ossreviewtoolkit.utils.common.AlphaNumericComparator
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.ort.HttpDownloadError
import org.ossreviewtoolkit.utils.ort.OkHttpClientHelper
import org.ossreviewtoolkit.utils.ort.downloadText
import org.ossreviewtoolkit.utils.ort.okHttpClient
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

// TODO: Remove this again once available upstream.
private inline fun <reified T> Yaml.decodeFromString(string: String): T = decodeFromString(serializer<T>(), string)

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
) : PackageManager(name, "Bundler", analysisRoot, analyzerConfig, repoConfig) {
    companion object {
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
        val lockfilesBundlerVersion = lockfiles.mapNotNull {
            parseBundlerVersionFromLockfile(it)
        }.sortedWith(AlphaNumericComparator).lastOrNull()

        val bundlerVersion = options[OPTION_BUNDLER_VERSION] ?: lockfilesBundlerVersion

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

        val gemsInfo = resolveGemsInfo(workingDir)

        return with(parseProject(definitionFile, gemsInfo)) {
            val projectId = Identifier(managerName, "", name, version)
            val groupedDeps = getDependencyGroups(workingDir)

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
                getPackageFromGemInfo(gemInfo).takeUnless { gemInfo.name == BUNDLER_GEM_NAME && !hasBundlerDep }
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
            val gemId = Identifier("Gem", "", gemInfo.name, gemInfo.version)

            // The project itself can be listed as a dependency if the project is a gem (i.e. there is a .gemspec file
            // for it, and the Gemfile refers to it). In that case, skip querying RubyGems and adding Package and
            // PackageReference objects and continue with the projects dependencies.
            if (gemId == projectId) {
                gemInfo.runtimeDependencies.forEach {
                    parseDependency(workingDir, projectId, it, gemsInfo, scopeDependencies, issues)
                }
            } else {
                queryRubyGems(gemId.name, gemId.version)?.apply {
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
                source = managerName,
                message = "Failed to parse dependency '$gemName' of project '${projectId.toCoordinates()}' in " +
                    "'$workingDir': ${it.collectMessages()}"
            )
        }
    }

    private fun getDependencyGroups(workingDir: File): Map<String, List<String>> =
        YAML.decodeFromString(runScriptResource(ROOT_DEPENDENCIES_SCRIPT, workingDir))

    private fun resolveGemsInfo(workingDir: File): MutableMap<String, GemInfo> {
        val stdout = runScriptResource(RESOLVE_DEPENDENCIES_SCRIPT, workingDir)

        // The metadata produced by the "resolve_dependencies.rb" script separates specs for packages with the "\0"
        // character as delimiter.
        val gemsInfo = stdout.split('\u0000').map {
            val spec = YAML.decodeFromString<GemSpec>(it)
            GemInfo.createFromMetadata(spec)
        }.associateByTo(mutableMapOf()) {
            it.name
        }

        return gemsInfo
    }

    private fun parseProject(definitionFile: File, gemsInfo: MutableMap<String, GemInfo>) =
        getGemspecFile(definitionFile.parentFile)?.let { gemspecFile ->
            // Project is a Gem, i.e. a library.
            gemsInfo[gemspecFile.nameWithoutExtension]
        } ?: GemInfo(
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

    private fun getPackageFromGemInfo(gemInfo: GemInfo): Package {
        val gemId = Identifier("Gem", "", gemInfo.name, gemInfo.version)

        return Package(
            id = gemId,
            authors = gemInfo.authors,
            declaredLicenses = gemInfo.declaredLicenses,
            description = gemInfo.description,
            homepageUrl = gemInfo.homepageUrl,
            binaryArtifact = RemoteArtifact.EMPTY,
            sourceArtifact = gemInfo.artifact,
            vcs = gemInfo.vcs,
            vcsProcessed = processPackageVcs(gemInfo.vcs, gemInfo.homepageUrl)
        )
    }

    private fun getGemspecFile(workingDir: File) =
        workingDir.walk().maxDepth(1).filter { it.isFile && it.extension == "gemspec" }.firstOrNull()

    private fun queryRubyGems(name: String, version: String, retryCount: Int = 3): GemInfo? {
        // NOTE: Explicitly use platform=ruby here to enforce the same behavior here that is also used in the Bundler
        //       resolving logic.
        // See plugins/package-managers/bundler/src/main/resources/resolve_dependencies.rb
        // See <http://guides.rubygems.org/rubygems-org-api-v2/>.
        val url = "https://rubygems.org/api/v2/rubygems/$name/versions/$version.yaml?platform=ruby"

        return okHttpClient.downloadText(url).mapCatching {
            val details = YAML.decodeFromString<VersionDetails>(it)
            GemInfo.createFromGem(details)
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
                        return queryRubyGems(name, version, retryCount - 1)
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

internal data class GemInfo(
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
        fun createFromMetadata(spec: GemSpec): GemInfo {
            val runtimeDependencies = spec.dependencies.mapNotNullTo(mutableSetOf()) { (name, type) ->
                name.takeIf { type == VersionDetails.Scope.RUNTIME.toString() }
            }

            val homepage = spec.homepage.orEmpty()
            val info = spec.description ?: spec.summary

            return GemInfo(
                spec.name,
                spec.version.version,
                homepage,
                spec.authors.mapToSetOfNotEmptyStrings(),
                spec.licenses.mapToSetOfNotEmptyStrings(),
                info.orEmpty(),
                runtimeDependencies,
                VcsHost.parseUrl(homepage),
                RemoteArtifact.EMPTY
            )
        }

        fun createFromGem(details: VersionDetails): GemInfo {
            val runtimeDependencies = details.dependencies[VersionDetails.Scope.RUNTIME]
                ?.mapTo(mutableSetOf()) { it.name }
                .orEmpty()

            val vcs = listOfNotNull(details.sourceCodeUri, details.homepageUri)
                .mapToSetOfNotEmptyStrings()
                .firstOrNull()
                ?.let { VcsHost.parseUrl(it) }
                .orEmpty()

            val artifact = if (details.gemUri != null && details.sha != null) {
                RemoteArtifact(details.gemUri, Hash.create(details.sha))
            } else {
                RemoteArtifact.EMPTY
            }

            return GemInfo(
                details.name,
                details.version,
                details.homepageUri.orEmpty(),
                details.authors?.split(',').mapToSetOfNotEmptyStrings(),
                details.licenses.mapToSetOfNotEmptyStrings(),
                details.info.orEmpty(),
                runtimeDependencies,
                vcs,
                artifact
            )
        }

        private fun Collection<String>?.mapToSetOfNotEmptyStrings(): Set<String> =
            this?.mapNotNullTo(mutableSetOf()) { string -> string.trim().ifEmpty { null } }.orEmpty()
    }

    fun merge(other: GemInfo): GemInfo {
        require(name == other.name && version == other.version) {
            "Cannot merge specs for different gems."
        }

        return GemInfo(
            name,
            version,
            homepageUrl.ifEmpty { other.homepageUrl },
            authors.ifEmpty { other.authors },
            declaredLicenses.ifEmpty { other.declaredLicenses },
            description.ifEmpty { other.description },
            runtimeDependencies.ifEmpty { other.runtimeDependencies },
            vcs.takeUnless { it == VcsInfo.EMPTY } ?: other.vcs,
            artifact.takeUnless { it == RemoteArtifact.EMPTY } ?: other.artifact
        )
    }
}
