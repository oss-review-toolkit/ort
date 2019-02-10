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

import ch.frankel.slf4k.*

import com.fasterxml.jackson.module.kotlin.readValue

import com.here.ort.analyzer.AbstractPackageManagerFactory
import com.here.ort.analyzer.HTTP_CACHE_PATH
import com.here.ort.analyzer.PackageManager
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.model.HashAlgorithm
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
import com.here.ort.utils.OS
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
 * The Bundler package manager for Ruby, see https://bundler.io/. Also see
 * http://yehudakatz.com/2010/12/16/clarifying-the-roles-of-the-gemspec-and-gemfile/.
 */
class Bundler(name: String, analyzerConfig: AnalyzerConfiguration, repoConfig: RepositoryConfiguration) :
        PackageManager(name, analyzerConfig, repoConfig), CommandLineTool {
    class Factory : AbstractPackageManagerFactory<Bundler>("Bundler") {
        override val globsForDefinitionFiles = listOf("Gemfile")

        override fun create(analyzerConfig: AnalyzerConfiguration, repoConfig: RepositoryConfiguration) =
                Bundler(managerName, analyzerConfig, repoConfig)
    }

    override fun command(workingDir: File?) = if (OS.isWindows) "bundle.bat" else "bundle"

    override fun getVersionRequirement(): Requirement = Requirement.buildIvy("[1.16,2.1[")

    override fun prepareResolution(definitionFiles: List<File>) =
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
            val errors = mutableListOf<OrtIssue>()

            installDependencies(workingDir)

            val (projectName, version, homepageUrl, declaredLicenses) = parseProject(workingDir)
            val projectId = Identifier(managerName, "", projectName, version)
            val groupedDeps = getDependencyGroups(workingDir)

            for ((groupName, dependencyList) in groupedDeps) {
                parseScope(workingDir, projectId, groupName, dependencyList, scopes, packages, errors)
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

            return ProjectAnalyzerResult(project, packages.map { it.toCuratedPackage() }.toSortedSet(), errors)
        }
    }

    private fun parseScope(workingDir: File, projectId: Identifier, groupName: String, dependencyList: List<String>,
                           scopes: MutableSet<Scope>, packages: MutableSet<Package>, errors: MutableList<OrtIssue>) {
        log.debug { "Parsing scope: $groupName\nscope top level deps list=$dependencyList" }

        val scopeDependencies = mutableSetOf<PackageReference>()

        dependencyList.forEach {
            parseDependency(workingDir, projectId, it, packages, scopeDependencies, errors)
        }

        scopes += Scope(groupName, scopeDependencies.toSortedSet())
    }

    private fun parseDependency(workingDir: File, projectId: Identifier, gemName: String, packages: MutableSet<Package>,
                                scopeDependencies: MutableSet<PackageReference>, errors: MutableList<OrtIssue>) {
        log.debug { "Parsing dependency '$gemName'." }

        try {
            var gemSpec = getGemspec(gemName, workingDir)
            val gemId = Identifier(managerName, "", gemSpec.name, gemSpec.version)

            // The project itself can be listed as a dependency if the project is a Gem (i.e. there is a .gemspec file
            // for it, and the Gemfile refers to it). In that case, skip querying Rubygems and adding Package and
            // PackageReference objects and continue with the projects dependencies.
            if (gemId == projectId) {
                gemSpec.runtimeDependencies.forEach {
                    parseDependency(workingDir, projectId, it, packages, scopeDependencies, errors)
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
                    parseDependency(workingDir, projectId, it, packages, transitiveDependencies, errors)
                }

                scopeDependencies += PackageReference(gemId, dependencies = transitiveDependencies.toSortedSet())
            }
        } catch (e: Exception) {
            e.showStackTrace()

            val errorMsg = "Failed to parse package (gem) $gemName: ${e.collectMessagesAsString()}"
            log.error { errorMsg }

            errors += OrtIssue(source = managerName, message = errorMsg)
        }
    }

    private fun getDependencyGroups(workingDir: File): Map<String, List<String>> {
        val scriptFile = File.createTempFile("bundler_dependencies", ".rb")
        scriptFile.writeBytes(javaClass.getResource("/scripts/bundler_dependencies.rb").readBytes())

        try {
            val scriptCmd = run(workingDir, "exec", "ruby", scriptFile.absolutePath)
            return jsonMapper.readValue(scriptCmd.stdout)
        } finally {
            if (!scriptFile.delete()) {
                log.warn { "Helper script file '${scriptFile.absolutePath}' could not be deleted." }
            }
        }
    }

    private fun parseProject(workingDir: File): GemSpec {
        val gemspecFile = getGemspecFile(workingDir)
        return if (gemspecFile != null) {
            // Project is a Gem
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
        require(analyzerConfig.allowDynamicVersions || File(workingDir, "Gemfile.lock").isFile) {
            "No lockfile found in ${workingDir.invariantSeparatorsPath}, dependency versions are unstable."
        }

        run(workingDir, "install", "--path", "vendor/bundle")
    }

    private fun queryRubygems(name: String, version: String): GemSpec? {
        return try {
            // See http://guides.rubygems.org/rubygems-org-api-v2/.
            val request = Request.Builder()
                    .get()
                    .url("https://rubygems.org/api/v2/rubygems/$name/versions/$version.json")
                    .build()

            OkHttpClientHelper.execute(HTTP_CACHE_PATH, request).use { response ->
                val body = response.body()?.string()?.trim()

                if (response.code() != HttpURLConnection.HTTP_OK || body.isNullOrEmpty()) {
                    log.warn { "Unable to retrieve rubygems.org meta-data for gem '$name'." }
                    if (body != null) {
                        log.warn { "The response was '$body' (code ${response.code()})." }
                    }
                    return null
                }

                return GemSpec.createFromJson(body)
            }
        } catch (e: IOException) {
            e.showStackTrace()

            log.warn { "Unable to parse rubygems.org meta-data for gem '$name': ${e.message}" }

            null
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
            val yaml = yamlMapper.readTree(spec)!!

            val runtimeDependencies = yaml["dependencies"]?.asIterable()?.mapNotNull { dependency ->
                dependency["name"]?.textValue()?.takeIf { dependency["type"]?.textValue() == ":runtime" }
            }?.toSet()

            val homepage = yaml["homepage"].textValueOrEmpty()
            return GemSpec(
                    yaml["name"].textValue(),
                    yaml["version"]["version"].textValue(),
                    homepage,
                    yaml["licenses"]?.asIterable()?.map { it.textValue() }?.toSortedSet() ?: sortedSetOf(),
                    yaml["description"].textValueOrEmpty(),
                    runtimeDependencies ?: emptySet(),
                    parseVcs(homepage),
                    RemoteArtifact.EMPTY
            )
        }

        fun createFromJson(spec: String): GemSpec {
            val json = jsonMapper.readTree(spec)!!
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
                RemoteArtifact(json["gem_uri"].textValue(), sha, HashAlgorithm.fromHash(sha))
            } else {
                RemoteArtifact.EMPTY
            }

            return GemSpec(
                    json["name"].textValue(),
                    json["version"].textValue(),
                    json["homepage_uri"].textValueOrEmpty(),
                    json["licenses"]?.asIterable()?.map { it.textValue() }?.toSortedSet() ?: sortedSetOf(),
                    json["description"].textValueOrEmpty(),
                    runtimeDependencies ?: emptySet(),
                    vcs,
                    artifact
            )
        }

        private val GITHUB_REGEX = Regex("https?://github.com/(?<owner>[^/]+)/(?<repo>[^/]+)")

        // Gems tend to have GitHub URL set as homepage. Seems like it is the only way to get any VCS information out of
        // gemspec files.
        private fun parseVcs(homepageUrl: String): VcsInfo =
                if (GITHUB_REGEX.matches(homepageUrl)) {
                    log.debug { "$homepageUrl is a GitHub URL." }
                    VcsInfo("git", "$homepageUrl.git", "", "")
                } else {
                    VcsInfo.EMPTY
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
