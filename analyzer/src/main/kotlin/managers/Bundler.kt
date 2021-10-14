/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 * Copyright (C) 2021 Bosch.IO GmbH
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
import com.fasterxml.jackson.module.kotlin.readValue

import com.vdurmont.semver4j.Requirement

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.util.SortedSet

import org.ossreviewtoolkit.analyzer.AbstractPackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.downloader.VcsHost
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.OrtIssue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.model.yamlMapper
import org.ossreviewtoolkit.utils.CommandLineTool
import org.ossreviewtoolkit.utils.HttpDownloadError
import org.ossreviewtoolkit.utils.OkHttpClientHelper
import org.ossreviewtoolkit.utils.collectMessagesAsString
import org.ossreviewtoolkit.utils.createOrtTempFile
import org.ossreviewtoolkit.utils.log
import org.ossreviewtoolkit.utils.showStackTrace
import org.ossreviewtoolkit.utils.textValueOrEmpty

private const val ROOT_DEPENDENCIES_SCRIPT = "/scripts/bundler_root_dependencies.rb"
private const val RESOLVE_DEPENDENCIES_SCRIPT = "/scripts/bundler_resolve_dependencies.rb"

/**
 * The [Bundler](https://bundler.io/) package manager for Ruby. Also see
 * [Clarifying the Roles of the .gemspec and Gemfile][1].
 *
 * [1]: http://yehudakatz.com/2010/12/16/clarifying-the-roles-of-the-gemspec-and-gemfile/
 */
class Bundler(
    name: String,
    analysisRoot: File,
    analyzerConfig: AnalyzerConfiguration,
    repoConfig: RepositoryConfiguration
) : PackageManager(name, analysisRoot, analyzerConfig, repoConfig), CommandLineTool {
    class Factory : AbstractPackageManagerFactory<Bundler>("Bundler") {
        override val globsForDefinitionFiles = listOf("Gemfile")

        override fun create(
            analysisRoot: File,
            analyzerConfig: AnalyzerConfiguration,
            repoConfig: RepositoryConfiguration
        ) = Bundler(managerName, analysisRoot, analyzerConfig, repoConfig)
    }

    override fun command(workingDir: File?) = "ruby"

    override fun transformVersion(output: String) = output.removePrefix("ruby ").substringBefore("p")
    override fun getVersionRequirement(): Requirement = Requirement.buildIvy("[2.5.1,)")

    override fun beforeResolution(definitionFiles: List<File>) =
        // We do not actually depend on any features specific to a version of Bundler, but we still want to stick to
        // fixed versions to be sure to get consistent results.
        checkVersion(analyzerConfig.ignoreToolVersions)

    override fun resolveDependencies(definitionFile: File): List<ProjectAnalyzerResult> {
        val workingDir = definitionFile.parentFile
        requireLockfile(workingDir) { workingDir.resolve("Gemfile.lock").isFile }

        val scopes = mutableSetOf<Scope>()
        val issues = mutableListOf<OrtIssue>()

        val gemSpecs = resolveGemsMetadata(workingDir)

        val (projectName, version, homepageUrl, authors, declaredLicenses) = parseProject(workingDir, gemSpecs)
        val projectId = Identifier(managerName, "", projectName, version)
        val groupedDeps = getDependencyGroups(workingDir)

        for ((groupName, dependencyList) in groupedDeps) {
            parseScope(workingDir, projectId, groupName, dependencyList, scopes, gemSpecs, issues)
        }

        val project = Project(
            id = projectId,
            definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
            authors = authors,
            declaredLicenses = declaredLicenses.toSortedSet(),
            vcs = VcsInfo.EMPTY,
            vcsProcessed = processProjectVcs(workingDir, VcsInfo.EMPTY, homepageUrl),
            homepageUrl = homepageUrl,
            scopeDependencies = scopes.toSortedSet()
        )

        val packages = gemSpecs.values.mapTo(sortedSetOf()) { getPackageFromGemspec(it) }
        return listOf(ProjectAnalyzerResult(project, packages, issues))
    }

    private fun parseScope(
        workingDir: File, projectId: Identifier, groupName: String, dependencyList: List<String>,
        scopes: MutableSet<Scope>, gemSpecs: MutableMap<String, GemSpec>, issues: MutableList<OrtIssue>
    ) {
        log.debug { "Parsing scope: $groupName\nscope top level deps list=$dependencyList" }

        val scopeDependencies = mutableSetOf<PackageReference>()

        dependencyList.forEach {
            parseDependency(workingDir, projectId, it, gemSpecs, scopeDependencies, issues)
        }

        scopes += Scope(groupName, scopeDependencies.toSortedSet())
    }

    private fun parseDependency(
        workingDir: File, projectId: Identifier, gemName: String, gemSpecs: MutableMap<String, GemSpec>,
        scopeDependencies: MutableSet<PackageReference>, issues: MutableList<OrtIssue>
    ) {
        log.debug { "Parsing dependency '$gemName'." }

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
                message = "Failed to parse dependency '$gemName': ${it.collectMessagesAsString()}"
            )
        }
    }

    private fun getDependencyGroups(workingDir: File): Map<String, List<String>> {
        val scriptFile = createOrtTempFile(File(ROOT_DEPENDENCIES_SCRIPT).nameWithoutExtension, ".rb")
        scriptFile.writeBytes(javaClass.getResource(ROOT_DEPENDENCIES_SCRIPT).readBytes())

        try {
            val scriptCmd = run(
                scriptFile.path,
                workingDir = workingDir,
            )
            return yamlMapper.readValue(scriptCmd.stdout)
        } finally {
            if (!scriptFile.delete()) {
                log.warn { "Helper script file '$scriptFile' could not be deleted." }
            }
        }
    }

    private fun resolveGemsMetadata(workingDir: File): MutableMap<String, GemSpec> {
        val scriptFile = createOrtTempFile(File(RESOLVE_DEPENDENCIES_SCRIPT).nameWithoutExtension, ".rb")
        scriptFile.writeBytes(javaClass.getResource(RESOLVE_DEPENDENCIES_SCRIPT).readBytes())

        try {
            val scriptCmd = run(
                scriptFile.path,
                workingDir = workingDir,
            )

            // The metadata produced by the "bundler_dependencies_metadata.rb" script separates specs for packages with
            // the "\0" character as delimiter. Always drop the first "Fetching gem metadata from" entry.
            val gemSpecs = scriptCmd.stdout.split('\u0000').drop(1).map {
                GemSpec.createFromMetadata(yamlMapper.readTree(it))
            }.associateByTo(mutableMapOf()) {
                it.name
            }

            // Bundler itself always shows up as a dependency because the helper script requires it, but it should be
            // removed unless it actually is a runtime dependency.
            val isBundlerARuntimeDependency = gemSpecs.values.any { gemspec ->
                gemspec.runtimeDependencies.any { name ->
                    name.startsWith("bundler")
                }
            }
            if (!isBundlerARuntimeDependency) gemSpecs.remove("bundler")

            return gemSpecs
        } finally {
            if (!scriptFile.delete()) {
                log.warn { "Helper script file '$scriptFile' could not be deleted." }
            }
        }
    }

    private fun parseProject(workingDir: File, gemSpecs: MutableMap<String, GemSpec>) =
        getGemspecFile(workingDir)?.let { gemspecFile ->
            // Project is a Gem, i.e. a library.
            gemSpecs[gemspecFile.nameWithoutExtension]
        } ?: GemSpec(
            workingDir.name,
            "",
            "",
            sortedSetOf(),
            sortedSetOf(),
            "",
            emptySet(),
            VcsInfo.EMPTY,
            RemoteArtifact.EMPTY
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
                log.warn { "Unable to retrieve metadata for gem '$name' from RubyGems: ${it.message}" }
                return null
            }

            when (error.code) {
                HttpURLConnection.HTTP_NOT_FOUND -> log.info { "Gem '$name' was not found on RubyGems." }

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

data class GemSpec(
    val name: String,
    val version: String,
    val homepageUrl: String,
    val authors: SortedSet<String>,
    val declaredLicenses: SortedSet<String>,
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
                node["authors"]?.asIterable()?.mapTo(sortedSetOf()) { it.textValue() } ?: sortedSetOf(),
                node["licenses"]?.asIterable()?.mapTo(sortedSetOf()) { it.textValue() } ?: sortedSetOf(),
                node["description"].textValueOrEmpty(),
                runtimeDependencies.orEmpty(),
                VcsHost.toVcsInfo(homepage),
                RemoteArtifact.EMPTY
            )
        }

        fun createFromGem(node: JsonNode): GemSpec {
            val runtimeDependencies = node["dependencies"]?.get("runtime")?.mapNotNull { dependency ->
                dependency["name"]?.textValue()
            }?.toSet()

            val vcs = if (node.hasNonNull("source_code_uri")) {
                VcsHost.toVcsInfo(node["source_code_uri"].textValue())
            } else if (node.hasNonNull("homepage_uri")) {
                VcsHost.toVcsInfo(node["homepage_uri"].textValue())
            } else {
                VcsInfo.EMPTY
            }

            val artifact = if (node.hasNonNull("gem_uri") && node.hasNonNull("sha")) {
                val sha = node["sha"].textValue()
                RemoteArtifact(node["gem_uri"].textValue(), Hash.create(sha))
            } else {
                RemoteArtifact.EMPTY
            }

            val authors = node["authors"]
                .textValueOrEmpty()
                .split(',')
                .mapNotNullTo(sortedSetOf()) { author ->
                    author.trim().takeIf {
                        it.isNotEmpty()
                    }
                }

            return GemSpec(
                node["name"].textValue(),
                node["version"].textValue(),
                node["homepage_uri"].textValueOrEmpty(),
                authors,
                node["licenses"]?.asIterable()?.mapTo(sortedSetOf()) { it.textValue() } ?: sortedSetOf(),
                node["description"].textValueOrEmpty(),
                runtimeDependencies.orEmpty(),
                vcs,
                artifact
            )
        }
    }

    fun merge(other: GemSpec): GemSpec {
        require(name == other.name && version == other.version) {
            "Cannot merge specs for different gems."
        }

        return GemSpec(name, version,
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
