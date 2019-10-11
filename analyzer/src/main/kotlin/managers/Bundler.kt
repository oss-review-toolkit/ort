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

package com.here.ort.analyzer.managers

import com.fasterxml.jackson.module.kotlin.readValue

import com.here.ort.analyzer.AbstractPackageManagerFactory
import com.here.ort.analyzer.HTTP_CACHE_PATH
import com.here.ort.analyzer.PackageManager
import com.here.ort.downloader.VcsHost
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.model.Hash
import com.here.ort.model.Identifier
import com.here.ort.model.OrtIssue
import com.here.ort.model.Package
import com.here.ort.model.PackageReference
import com.here.ort.model.Project
import com.here.ort.model.ProjectAnalyzerResult
import com.here.ort.model.RemoteArtifact
import com.here.ort.model.Scope
import com.here.ort.model.VcsInfo
import com.here.ort.model.config.AnalyzerConfiguration
import com.here.ort.model.config.RepositoryConfiguration
import com.here.ort.model.jsonMapper
import com.here.ort.model.yamlMapper
import com.here.ort.utils.CommandLineTool
import com.here.ort.utils.Os
import com.here.ort.utils.OkHttpClientHelper
import com.here.ort.utils.collectMessagesAsString
import com.here.ort.utils.log
import com.here.ort.utils.showStackTrace
import com.here.ort.utils.stashDirectories
import com.here.ort.utils.textValueOrEmpty

import com.vdurmont.semver4j.Requirement

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.util.SortedSet

import okhttp3.Request

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

    override fun getVersionRequirement(): Requirement = Requirement.buildIvy("[1.16,2.1[")

    override fun beforeResolution(definitionFiles: List<File>) =
        // We do not actually depend on any features specific to a version of Bundler, but we still want to stick to
        // fixed versions to be sure to get consistent results.
        checkVersion(
            ignoreActualVersion = analyzerConfig.ignoreToolVersions,
            transform = { it.substringAfter("Bundler version ") }
        )

    override fun resolveDependencies(definitionFile: File): ProjectAnalyzerResult? {
        val workingDir = definitionFile.parentFile

        stashDirectories(File(workingDir, "vendor")).use {
            val scopes = mutableSetOf<Scope>()
            val packages = mutableSetOf<Package>()
            val issues = mutableListOf<OrtIssue>()

            installDependencies(workingDir)

            val (projectName, version, homepageUrl, declaredLicenses) = parseProject(workingDir)
            val projectId = Identifier(managerName, "", projectName, version)
            val groupedDeps = getDependencyGroups(workingDir)

            for ((groupName, dependencyList) in groupedDeps) {
                parseScope(workingDir, projectId, groupName, dependencyList, scopes, packages, issues)
            }

            val project = Project(
                id = projectId,
                definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
                declaredLicenses = declaredLicenses.toSortedSet(),
                vcs = VcsInfo.EMPTY,
                vcsProcessed = processProjectVcs(workingDir, homepageUrl = homepageUrl),
                homepageUrl = homepageUrl,
                scopes = scopes.toSortedSet()
            )

            return ProjectAnalyzerResult(project, packages.mapTo(sortedSetOf()) { it.toCuratedPackage() }, issues)
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
            val gemId = Identifier(managerName, "", gemSpec.name, gemSpec.version)

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

            val errorMsg = "Failed to parse spec for gem '$gemName': ${e.collectMessagesAsString()}"
            log.error { errorMsg }

            issues += OrtIssue(source = managerName, message = errorMsg)
        }
    }

    private fun getDependencyGroups(workingDir: File): Map<String, List<String>> {
        val scriptFile = File.createTempFile("bundler_dependencies", ".rb")
        scriptFile.writeBytes(javaClass.getResource("/scripts/bundler_dependencies.rb").readBytes())

        try {
            val scriptCmd = run(workingDir, "exec", "ruby", scriptFile.path)
            return jsonMapper.readValue(scriptCmd.stdout)
        } finally {
            if (!scriptFile.delete()) {
                log.warn { "Helper script file '$scriptFile' could not be deleted." }
            }
        }
    }

    private fun parseProject(workingDir: File): GemSpec {
        val gemspecFile = getGemspecFile(workingDir)
        return if (gemspecFile != null) {
            // Project is a gem.
            getGemspec(gemspecFile.name.substringBefore("."), workingDir)
        } else {
            GemSpec(workingDir.name, "", "", sortedSetOf(), "", emptySet(), VcsInfo.EMPTY, RemoteArtifact.EMPTY)
        }
    }

    private fun getGemspec(gemName: String, workingDir: File): GemSpec {
        val spec = run(workingDir, "exec", "gem", "specification", gemName).stdout

        return GemSpec.createFromYaml(spec)
    }

    private fun getGemspecFile(workingDir: File) =
        workingDir.listFiles { _, name -> name.endsWith(".gemspec") }.firstOrNull()

    private fun installDependencies(workingDir: File) {
        requireLockfile(workingDir) { File(workingDir, "Gemfile.lock").isFile }

        run(workingDir, "install", "--path", "vendor/bundle")
    }

    private fun queryRubygems(name: String, version: String, retryCount: Int = 3): GemSpec? {
        // See http://guides.rubygems.org/rubygems-org-api-v2/.
        val request = Request.Builder()
            .get()
            .url("https://rubygems.org/api/v2/rubygems/$name/versions/$version.json")
            .build()

        OkHttpClientHelper.execute(HTTP_CACHE_PATH, request).use { response ->
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
                yaml["licenses"]?.asIterable()?.mapTo(sortedSetOf()) { it.textValue() } ?: sortedSetOf(),
                yaml["description"].textValueOrEmpty(),
                runtimeDependencies ?: emptySet(),
                VcsHost.toVcsInfo(homepage) ?: VcsInfo.EMPTY,
                RemoteArtifact.EMPTY
            )
        }

        fun createFromJson(spec: String): GemSpec {
            val json = jsonMapper.readTree(spec)

            val runtimeDependencies = json["dependencies"]?.get("runtime")?.mapNotNull { dependency ->
                dependency["name"]?.textValue()
            }?.toSet()

            val vcs = if (json.hasNonNull("source_code_uri")) {
                VersionControlSystem.splitUrl(json["source_code_uri"].textValue())
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
                json["licenses"]?.asIterable()?.mapTo(sortedSetOf()) { it.textValue() } ?: sortedSetOf(),
                json["description"].textValueOrEmpty(),
                runtimeDependencies ?: emptySet(),
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
            declaredLicenses.takeUnless { it.isEmpty() } ?: other.declaredLicenses,
            description.takeUnless { it.isEmpty() } ?: other.description,
            runtimeDependencies.takeUnless { it.isEmpty() } ?: other.runtimeDependencies,
            vcs.takeUnless { it == VcsInfo.EMPTY } ?: other.vcs,
            artifact.takeUnless { it == RemoteArtifact.EMPTY } ?: other.artifact
        )
    }
}
