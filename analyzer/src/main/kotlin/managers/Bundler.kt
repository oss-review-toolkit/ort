/*
 * Copyright (C) 2017-2018 HERE Europe B.V.
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

import com.here.ort.analyzer.HTTP_CACHE_PATH
import com.here.ort.analyzer.PackageManager
import com.here.ort.analyzer.AbstractPackageManagerFactory
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.model.Error
import com.here.ort.model.HashAlgorithm
import com.here.ort.model.Identifier
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
import com.here.ort.utils.OkHttpClientHelper
import com.here.ort.utils.OS
import com.here.ort.utils.ProcessCapture
import com.here.ort.utils.textValueOrEmpty
import com.here.ort.utils.checkCommandVersion
import com.here.ort.utils.log
import com.here.ort.utils.safeDeleteRecursively
import com.here.ort.utils.showStackTrace

import com.vdurmont.semver4j.Requirement

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.SortedSet

import okhttp3.Request

/**
 * The Bundler package manager for Ruby, see https://bundler.io/. Also see
 * http://yehudakatz.com/2010/12/16/clarifying-the-roles-of-the-gemspec-and-gemfile/.
 */
class Bundler(analyzerConfig: AnalyzerConfiguration, repoConfig: RepositoryConfiguration) :
        PackageManager(analyzerConfig, repoConfig) {
    class Factory : AbstractPackageManagerFactory<Bundler>() {
        override val globsForDefinitionFiles = listOf("Gemfile")

        override fun create(analyzerConfig: AnalyzerConfiguration, repoConfig: RepositoryConfiguration) =
                Bundler(analyzerConfig, repoConfig)
    }

    override fun command(workingDir: File) = if (OS.isWindows) "bundle.bat" else "bundle"

    override fun prepareResolution(definitionFiles: List<File>): List<File> {
        val workingDir = definitionFiles.first().parentFile

        // We do not actually depend on any features specific to a version of Bundler, but we still want to stick to
        // fixed versions to be sure to get consistent results.
        checkCommandVersion(
                command(workingDir),
                Requirement.buildIvy("1.16.+"),
                ignoreActualVersion = analyzerConfig.ignoreToolVersions,
                transform = { it.substringAfter("Bundler version ") }
        )

        return definitionFiles
    }

    override fun resolveDependencies(definitionFile: File): ProjectAnalyzerResult? {
        val workingDir = definitionFile.parentFile
        val vendorDir = File(workingDir, "vendor")
        var tempVendorDir: File? = null

        try {
            if (vendorDir.isDirectory) {
                val tempDir = createTempDir(prefix = "analyzer", directory = workingDir)
                tempVendorDir = File(tempDir, "vendor")
                log.warn { "'$vendorDir' already exists, temporarily moving it to '$tempVendorDir'." }
                Files.move(vendorDir.toPath(), tempVendorDir.toPath(), StandardCopyOption.ATOMIC_MOVE)
            }

            val scopes = mutableSetOf<Scope>()
            val packages = mutableSetOf<Package>()
            val errors = mutableListOf<Error>()

            installDependencies(workingDir)

            val (projectName, version, homepageUrl, declaredLicenses) = parseProject(workingDir)
            val projectId = Identifier(toString(), "", projectName, version)
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
        } finally {
            // Delete vendor folder to not pollute the scan.
            log.info { "Deleting temporary directory '$vendorDir'..." }
            vendorDir.safeDeleteRecursively()

            // Restore any previously existing "vendor" directory.
            if (tempVendorDir != null) {
                log.info { "Restoring original '$vendorDir' directory from '$tempVendorDir'." }
                Files.move(tempVendorDir.toPath(), vendorDir.toPath(), StandardCopyOption.ATOMIC_MOVE)
                if (!tempVendorDir.parentFile.delete()) {
                    throw IOException("Unable to delete the '${tempVendorDir.parent}' directory.")
                }
            }
        }
    }

    private fun parseScope(workingDir: File, projectId: Identifier, groupName: String, dependencyList: List<String>,
                           scopes: MutableSet<Scope>, packages: MutableSet<Package>, errors: MutableList<Error>) {
        log.debug { "Parsing scope: $groupName\nscope top level deps list=$dependencyList" }

        val scopeDependencies = mutableSetOf<PackageReference>()

        dependencyList.forEach {
            parseDependency(workingDir, projectId, it, packages, scopeDependencies, errors)
        }

        scopes += Scope(groupName, scopeDependencies.toSortedSet())
    }

    private fun parseDependency(workingDir: File, projectId: Identifier, gemName: String, packages: MutableSet<Package>,
                                scopeDependencies: MutableSet<PackageReference>, errors: MutableList<Error>) {
        log.debug { "Parsing dependency '$gemName'." }

        try {
            var gemSpec = getGemspec(gemName, workingDir)
            val gemId = Identifier(toString(), "", gemSpec.name, gemSpec.version)

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

                scopeDependencies += PackageReference(gemId, transitiveDependencies.toSortedSet())
            }
        } catch (e: Exception) {
            e.showStackTrace()

            val errorMsg = "Failed to parse package (gem) $gemName: ${e.message}"
            log.error { errorMsg }
            errors += Error(source = toString(), message = errorMsg)
        }
    }

    private fun getDependencyGroups(workingDir: File): Map<String, List<String>> {
        val scriptFile = File.createTempFile("bundler_dependencies", ".rb")
        scriptFile.writeBytes(javaClass.classLoader.getResource("bundler_dependencies.rb").readBytes())

        val scriptCmd = ProcessCapture(workingDir, command(workingDir), "exec", "ruby", scriptFile.absolutePath)

        try {
            return jsonMapper.readValue(scriptCmd.requireSuccess().stdout())
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
        val spec = ProcessCapture(workingDir, command(workingDir), "exec", "gem", "specification",
                gemName).requireSuccess().stdout()

        return GemSpec.createFromYaml(spec)
    }

    private fun getGemspecFile(workingDir: File) =
            workingDir.listFiles { _, name -> name.endsWith(".gemspec") }.firstOrNull()

    private fun installDependencies(workingDir: File) {
        require(analyzerConfig.allowDynamicVersions || File(workingDir, "Gemfile.lock").isFile) {
            "No lockfile found in ${workingDir.invariantSeparatorsPath}, dependency versions are unstable."
        }

        ProcessCapture(workingDir, command(workingDir), "install", "--path", "vendor/bundle").requireSuccess()
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
                return GemSpec.createFromJson(body!!)
            }
        } catch (e: IOException) {
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
                RemoteArtifact(json["gem_uri"].textValue(), json["sha"].textValue(), HashAlgorithm.SHA256)
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
