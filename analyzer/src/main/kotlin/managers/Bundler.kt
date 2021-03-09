/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

import com.fasterxml.jackson.module.kotlin.readValue

import com.vdurmont.semver4j.Requirement

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.util.SortedSet

import okhttp3.Request

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
import org.ossreviewtoolkit.model.jsonMapper
import org.ossreviewtoolkit.model.yamlMapper
import org.ossreviewtoolkit.utils.CommandLineTool
import org.ossreviewtoolkit.utils.OkHttpClientHelper
import org.ossreviewtoolkit.utils.Os
import org.ossreviewtoolkit.utils.collectMessagesAsString
import org.ossreviewtoolkit.utils.log
import org.ossreviewtoolkit.utils.showStackTrace
import org.ossreviewtoolkit.utils.stashDirectories
import org.ossreviewtoolkit.utils.textValueOrEmpty

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

    override fun command(workingDir: File?) = if (Os.isWindows) "bundle.bat" else "bundle"

    override fun transformVersion(output: String) = output.removePrefix("Bundler version ")

    override fun getVersionRequirement(): Requirement = Requirement.buildIvy("[1.16,)")

    override fun beforeResolution(definitionFiles: List<File>) =
        // We do not actually depend on any features specific to a version of Bundler, but we still want to stick to
        // fixed versions to be sure to get consistent results.
        checkVersion(analyzerConfig.ignoreToolVersions)

    override fun resolveDependencies(definitionFile: File): List<ProjectAnalyzerResult> {
        val workingDir = definitionFile.parentFile

        stashDirectories(workingDir.resolve("vendor")).use {
            val scopes = mutableSetOf<Scope>()
            val packages = sortedSetOf<Package>()
            val issues = mutableListOf<OrtIssue>()

            installDependencies(workingDir)

            val (projectName, version, homepageUrl, authors, declaredLicenses) = parseProject(workingDir)
            val projectId = Identifier(managerName, "", projectName, version)
            val groupedDeps = getDependencyGroups(workingDir)

            for ((groupName, dependencyList) in groupedDeps) {
                parseScope(workingDir, projectId, groupName, dependencyList, scopes, packages, issues)
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

            return listOf(ProjectAnalyzerResult(project, packages, issues))
        }
    }

    private fun parseScope(
        workingDir: File, projectId: Identifier, groupName: String, dependencyList: List<String>,
        scopes: MutableSet<Scope>, packages: MutableSet<Package>, issues: MutableList<OrtIssue>
    ) {
        log.debug { "Parsing scope: $groupName\nscope top level deps list=$dependencyList" }

        val scopeDependencies = mutableSetOf<PackageReference>()

        dependencyList.forEach {
            parseDependency(workingDir, projectId, it, packages, scopeDependencies, issues)
        }

        scopes += Scope(groupName, scopeDependencies.toSortedSet())
    }

    private fun parseDependency(
        workingDir: File, projectId: Identifier, gemName: String, packages: MutableSet<Package>,
        scopeDependencies: MutableSet<PackageReference>, issues: MutableList<OrtIssue>
    ) {
        log.debug { "Parsing dependency '$gemName'." }

        try {
            var gemSpec = getGemspec(gemName, workingDir)
            val gemId = Identifier("Gem", "", gemSpec.name, gemSpec.version)

            // The project itself can be listed as a dependency if the project is a gem (i.e. there is a .gemspec file
            // for it, and the Gemfile refers to it). In that case, skip querying Rubygems and adding Package and
            // PackageReference objects and continue with the projects dependencies.
            if (gemId == projectId) {
                gemSpec.runtimeDependencies.forEach {
                    parseDependency(workingDir, projectId, it, packages, scopeDependencies, issues)
                }
            } else {
                queryRubygems(gemId.name, gemId.version)?.apply {
                    gemSpec = merge(gemSpec)
                }

                packages += Package(
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

                val transitiveDependencies = mutableSetOf<PackageReference>()

                gemSpec.runtimeDependencies.forEach {
                    parseDependency(workingDir, projectId, it, packages, transitiveDependencies, issues)
                }

                scopeDependencies += PackageReference(gemId, dependencies = transitiveDependencies.toSortedSet())
            }
        } catch (e: IOException) {
            e.showStackTrace()

            issues += createAndLogIssue(
                source = managerName,
                message = "Failed to parse spec for gem '$gemName': ${e.collectMessagesAsString()}"
            )
        }
    }

    private fun getDependencyGroups(workingDir: File): Map<String, List<String>> {
        val scriptFile = File.createTempFile("bundler_dependencies", ".rb")
        scriptFile.writeBytes(javaClass.getResource("/scripts/bundler_dependencies.rb").readBytes())

        try {
            val scriptCmd = run(
                "exec", "ruby", scriptFile.path,
                workingDir = workingDir,
                environment = mapOf("BUNDLE_PATH" to "vendor/bundle")
            )
            return jsonMapper.readValue(scriptCmd.stdout)
        } finally {
            if (!scriptFile.delete()) {
                log.warn { "Helper script file '$scriptFile' could not be deleted." }
            }
        }
    }

    private fun parseProject(workingDir: File) =
        getGemspecFile(workingDir)?.let { gemspecFile ->
            // Project is a Gem, i.e. a library.
            getGemspec(gemspecFile.nameWithoutExtension, workingDir)
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

    private fun getGemspec(gemName: String, workingDir: File): GemSpec {
        val spec = run(
            "exec", "gem", "specification", gemName,
            workingDir = workingDir,
            environment = mapOf("BUNDLE_PATH" to "vendor/bundle")
        ).stdout

        return GemSpec.createFromYaml(spec)
    }

    private fun getGemspecFile(workingDir: File) =
        workingDir.walk().maxDepth(1).filter { it.isFile && it.extension == "gemspec" }.firstOrNull()

    private fun installDependencies(workingDir: File) {
        requireLockfile(workingDir) { workingDir.resolve("Gemfile.lock").isFile }

        // Work around "--path" being deprecated since Bundler 2.1 and avoid tampering with the ".bundle/config" file at
        // all by using the "BUNDLER_PATH" environment variable to specify where to install the Gems to.
        run("install", workingDir = workingDir, environment = mapOf("BUNDLE_PATH" to "vendor/bundle"))
    }

    private fun queryRubygems(name: String, version: String, retryCount: Int = 3): GemSpec? {
        // See http://guides.rubygems.org/rubygems-org-api-v2/.
        val request = Request.Builder()
            .get()
            .url("https://rubygems.org/api/v2/rubygems/$name/versions/$version.json")
            .build()

        OkHttpClientHelper.execute(request).use { response ->
            when (response.code) {
                HttpURLConnection.HTTP_OK -> {
                    val body = response.body?.string()?.trim()
                    return if (body.isNullOrEmpty()) null else GemSpec.createFromJson(body)
                }

                HttpURLConnection.HTTP_NOT_FOUND -> {
                    log.info { "Gem '$name' was not found on RubyGems." }
                    return null
                }

                OkHttpClientHelper.HTTP_TOO_MANY_REQUESTS -> {
                    throw IOException(
                        "RubyGems reported too many requests when requesting meta-data for gem '$name', see " +
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
                        "RubyGems reported too many bad gateway errors when requesting meta-data for gem '$name'."
                    )
                }

                else -> {
                    throw IOException(
                        "RubyGems reported unhandled HTTP code ${response.code} when requesting meta-data for " +
                                "gem '$name'."
                    )
                }
            }
        }
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
    companion object Factory {
        fun createFromYaml(spec: String): GemSpec {
            val yaml = yamlMapper.readTree(spec)

            val runtimeDependencies = yaml["dependencies"]?.asIterable()?.mapNotNull { dependency ->
                dependency["name"]?.textValue()?.takeIf { dependency["type"]?.textValue() == ":runtime" }
            }?.toSet()

            val homepage = yaml["homepage"].textValueOrEmpty()
            return GemSpec(
                yaml["name"].textValue(),
                yaml["version"]["version"].textValue(),
                homepage,
                yaml["authors"]?.asIterable()?.mapTo(sortedSetOf()) { it.textValue() } ?: sortedSetOf(),
                yaml["licenses"]?.asIterable()?.mapTo(sortedSetOf()) { it.textValue() } ?: sortedSetOf(),
                yaml["description"].textValueOrEmpty(),
                runtimeDependencies.orEmpty(),
                VcsHost.toVcsInfo(homepage),
                RemoteArtifact.EMPTY
            )
        }

        fun createFromJson(spec: String): GemSpec {
            val json = jsonMapper.readTree(spec)

            val runtimeDependencies = json["dependencies"]?.get("runtime")?.mapNotNull { dependency ->
                dependency["name"]?.textValue()
            }?.toSet()

            val vcs = if (json.hasNonNull("source_code_uri")) {
                VcsHost.toVcsInfo(json["source_code_uri"].textValue())
            } else {
                VcsInfo.EMPTY
            }

            val artifact = if (json.hasNonNull("gem_uri") && json.hasNonNull("sha")) {
                val sha = json["sha"].textValue()
                RemoteArtifact(json["gem_uri"].textValue(), Hash.create(sha))
            } else {
                RemoteArtifact.EMPTY
            }

            return GemSpec(
                json["name"].textValue(),
                json["version"].textValue(),
                json["homepage_uri"].textValueOrEmpty(),
                json["authors"]?.asIterable()?.mapTo(sortedSetOf()) { it.textValue() } ?: sortedSetOf(),
                json["licenses"]?.asIterable()?.mapTo(sortedSetOf()) { it.textValue() } ?: sortedSetOf(),
                json["description"].textValueOrEmpty(),
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
