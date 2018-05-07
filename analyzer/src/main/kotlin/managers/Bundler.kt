/*
 * Copyright (c) 2017-2018 HERE Europe B.V.
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

import com.here.ort.analyzer.Main
import com.here.ort.analyzer.PackageManager
import com.here.ort.analyzer.PackageManagerFactory
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.model.AnalyzerResult
import com.here.ort.model.HashAlgorithm
import com.here.ort.model.Identifier
import com.here.ort.model.Package
import com.here.ort.model.PackageReference
import com.here.ort.model.Project
import com.here.ort.model.RemoteArtifact
import com.here.ort.model.Scope
import com.here.ort.model.VcsInfo
import com.here.ort.model.jsonMapper
import com.here.ort.model.yamlMapper
import com.here.ort.utils.OkHttpClientHelper
import com.here.ort.utils.OS
import com.here.ort.utils.ProcessCapture
import com.here.ort.utils.asTextOrEmpty
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

const val DEPS_LIST_RUBY = """#!/usr/bin/ruby
require 'bundler'
require 'json'

groups = {}

Bundler.load.current_dependencies.each do |dep|
    dep.groups.each do |group|
        (groups[group] ||= []) << dep.name
    end
end

puts JSON.generate(groups)
"""

class Bundler : PackageManager() {
    companion object : PackageManagerFactory<Bundler>(
            "http://bundler.io/",
            "Ruby",
            // See http://yehudakatz.com/2010/12/16/clarifying-the-roles-of-the-gemspec-and-gemfile/.
            listOf("Gemfile")
    ) {
        override fun create() = Bundler()

        val bundle = if (OS.isWindows) "bundle.bat" else "bundle"
    }

    private val DEVELOPMENT_SCOPES = listOf("development", "test")

    override fun command(workingDir: File) = bundle

    override fun prepareResolution(definitionFiles: List<File>): List<File> {
        // We do not actually depend on any features specific to a version of Bundler, but we still want to stick to
        // fixed versions to be sure to get consistent results.
        checkCommandVersion(
                bundle,
                Requirement.buildStrict("1.16.1"),
                ignoreActualVersion = Main.ignoreVersions,
                transform = { it.substringAfter("Bundler version ") }
        )

        return definitionFiles
    }

    override fun resolveDependencies(definitionFile: File): AnalyzerResult? {
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
            val errors = mutableListOf<String>()

            installDependencies(workingDir)

            val (projectName, version, homepageUrl, declaredLicenses) = parseProject(workingDir)
            val groupedDeps = getDependencyGroups(workingDir)

            for ((groupName, dependencyList) in groupedDeps) {
                parseScope(workingDir, groupName, dependencyList, scopes, packages, errors)
            }

            val project = Project(
                    id = Identifier(
                            provider = javaClass.simpleName,
                            namespace = "",
                            name = projectName,
                            version = version
                    ),
                    definitionFilePath = VersionControlSystem.getPathToRoot(definitionFile) ?: "",
                    declaredLicenses = declaredLicenses.toSortedSet(),
                    aliases = emptyList(),
                    vcs = VcsInfo.EMPTY,
                    vcsProcessed = processProjectVcs(workingDir),
                    homepageUrl = homepageUrl,
                    scopes = scopes.toSortedSet()
            )

            return AnalyzerResult(
                    Main.allowDynamicVersions,
                    project,
                    packages.map { it.toCuratedPackage() }.toSortedSet(),
                    errors
            )
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

    private fun parseScope(workingDir: File, groupName: String, dependencyList: List<String>, scopes: MutableSet<Scope>,
                           packages: MutableSet<Package>, errors: MutableList<String>) {
        log.debug { "Parsing scope: $groupName\nscope top level deps list=$dependencyList" }

        val scopeDependencies = mutableSetOf<PackageReference>()

        dependencyList.forEach {
            parseDependency(workingDir, it, packages, scopeDependencies, errors)
        }

        val delivered = groupName.toLowerCase() !in DEVELOPMENT_SCOPES
        scopes.add(Scope(groupName, delivered, scopeDependencies.toSortedSet()))
    }

    private fun parseDependency(workingDir: File, gemName: String, packages: MutableSet<Package>,
                                scopeDependencies: MutableSet<PackageReference>, errors: MutableList<String>) {
        log.debug { "Parsing dependency '$gemName'." }

        try {
            val localGemSpec = getGemspec(gemName, workingDir)
            val gemSpec = queryRubygems(localGemSpec.name, localGemSpec.version).let {
                it?.merge(localGemSpec) ?: localGemSpec
            }

            packages.add(Package(
                    id = Identifier(
                            provider = javaClass.simpleName,
                            namespace = "",
                            name = gemSpec.name,
                            version = gemSpec.version
                    ),
                    declaredLicenses = gemSpec.declaredLicenses,
                    description = gemSpec.description,
                    homepageUrl = gemSpec.homepageUrl,
                    binaryArtifact = gemSpec.binaryArtifact,
                    sourceArtifact = RemoteArtifact.EMPTY,
                    vcs = gemSpec.vcs,
                    vcsProcessed = processPackageVcs(gemSpec.vcs))
            )

            val transitiveDependencies = mutableSetOf<PackageReference>()

            gemSpec.runtimeDependencies.forEach {
                parseDependency(workingDir, it, packages, transitiveDependencies, errors)
            }

            scopeDependencies.add(PackageReference(
                    id = Identifier(
                            provider = javaClass.simpleName,
                            namespace = "",
                            name = gemSpec.name,
                            version = gemSpec.version
                    ),
                    dependencies = transitiveDependencies.toSortedSet())
            )
        } catch (e: Exception) {
            e.showStackTrace()

            val errorMsg = "Failed to parse package (gem) $gemName: ${e.message}"
            log.error { errorMsg }
            errors.add(errorMsg)
        }
    }

    private fun getDependencyGroups(workingDir: File): Map<String, List<String>> {
        val scriptFile = createDepsListRubyScript(workingDir)
        val scriptCmd = ProcessCapture(workingDir, command(workingDir), "exec", "ruby", scriptFile.name)

        try {
            return jsonMapper.readValue(scriptCmd.requireSuccess().stdout())
        } finally {
            scriptFile.delete()
        }
    }

    /**
     * Creates a simple ruby script that produces top level dependencies list with group information. No bundle
     * command except 'bundle viz' seem to produce dependency list with corresponding groups.
     * Parsing dot/svg `bundle viz` output seemed to be overhead.
     */
    private fun createDepsListRubyScript(workingDir: File): File {
        val depsScript = File(workingDir, "list-deps.rb")
        depsScript.writeText(DEPS_LIST_RUBY)
        return depsScript
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
        require(Main.allowDynamicVersions || File(workingDir, "Gemfile.lock").isFile) {
            "No lockfile found in $workingDir, dependency versions are unstable."
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

            OkHttpClientHelper.execute(Main.HTTP_CACHE_PATH, request).use { response ->
                val body = response.body()?.string()?.trim()

                if (response.code() != HttpURLConnection.HTTP_OK || body.isNullOrEmpty()) {
                    log.warn { "Unable to retrieve rubygems.org meta-data for gem '$name'." }
                    if (body != null) {
                        log.warn { "Response was '$body'." }
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
        val binaryArtifact: RemoteArtifact
) {
    companion object Factory {
        fun createFromYaml(spec: String): GemSpec {
            val yaml = yamlMapper.readTree(spec)!!

            val runtimeDependencies = yaml["dependencies"]?.asIterable()?.mapNotNull { dependency ->
                dependency["name"]?.asText()?.takeIf { dependency["type"]?.asText() == ":runtime" }
            }?.toSet()

            return GemSpec(
                    yaml["name"].asText(),
                    yaml["version"]["version"].asText(),
                    yaml["homepage"].asTextOrEmpty(),
                    yaml["licenses"]?.asIterable()?.map { it.asText() }?.toSortedSet() ?: sortedSetOf(),
                    yaml["description"].asTextOrEmpty(),
                    runtimeDependencies ?: emptySet(),
                    parseVcs(yaml["homepage"].asText()),
                    RemoteArtifact.EMPTY
            )
        }

        fun createFromJson(spec: String): GemSpec {
            val json = jsonMapper.readTree(spec)!!
            val runtimeDependencies = json["dependencies"]?.get("runtime")?.mapNotNull { it["name"]?.asText() }?.toSet()

            val vcs = if (json.hasNonNull("source_code_uri")) {
                VersionControlSystem.splitUrl(json["source_code_uri"].asText())
            } else {
                VcsInfo.EMPTY
            }

            val binaryArtifact = if (json.hasNonNull("gem_uri") && json.hasNonNull("sha")) {
                RemoteArtifact(json["gem_uri"].asText(), json["sha"].asText(), HashAlgorithm.SHA256)
            } else {
                RemoteArtifact.EMPTY
            }

            return GemSpec(
                    json["name"].asText(),
                    json["version"].asText(),
                    json["homepage_uri"].asTextOrEmpty(),
                    json["licenses"]?.asIterable()?.map { it.asText() }?.toSortedSet() ?: sortedSetOf(),
                    json["description"].asTextOrEmpty(),
                    runtimeDependencies ?: emptySet(),
                    vcs,
                    binaryArtifact
            )
        }

        // Gems tend to have GitHub URL set as homepage. Seems like it is the only way to get any VCS information out of
        // gemspec files.
        private fun parseVcs(homepageUrl: String): VcsInfo =
                if (Regex("https*:\\/\\/github.com\\/(?<owner>[\\w-]+)\\/(?<repo>[\\w-]+)").matches(homepageUrl)) {
                    log.debug { "$homepageUrl is a GitHub URL." }
                    VcsInfo("Git", "$homepageUrl.git", "", "")
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
                binaryArtifact.takeUnless { it == RemoteArtifact.EMPTY } ?: other.binaryArtifact
        )
    }
}
