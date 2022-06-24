/*
 * Copyright (C) 2021 HERE Europe B.V.
 * Copyright (C) 2021 Dr. Ing. h.c. F. Porsche AG
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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.node.ObjectNode

import com.vdurmont.semver4j.Requirement

import java.io.File
import java.util.SortedSet

import org.ossreviewtoolkit.analyzer.AbstractPackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManager
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
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.model.orEmpty
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.model.yamlMapper
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.stashDirectories
import org.ossreviewtoolkit.utils.common.textValueOrEmpty
import org.ossreviewtoolkit.utils.ort.log

/**
 * The [CocoaPods](https://cocoapods.org/) package manager for Objective-C.
 *
 * As pre-condition for the analysis each respective definition file must have a sibling lockfile named 'Podfile.lock'.
 * The dependency tree is constructed solely based on parsing that lockfile. So, the dependency tree can be constructed
 * on any platform. Note that obtaining the dependency tree from the 'pod' command without a lock file has Xcode
 * dependencies and is not supported by this class.
 *
 * The only interactions with the 'pod' command happen in order to obtain metadata for dependencies. Therefore
 * 'pod spec which' gets executed, which works also under Linux.
 */
class CocoaPods(
    name: String,
    analysisRoot: File,
    analyzerConfig: AnalyzerConfiguration,
    repoConfig: RepositoryConfiguration
) : PackageManager(name, analysisRoot, analyzerConfig, repoConfig), CommandLineTool {
    class Factory : AbstractPackageManagerFactory<CocoaPods>("CocoaPods") {
        override val globsForDefinitionFiles = listOf("Podfile")

        override fun create(
            analysisRoot: File,
            analyzerConfig: AnalyzerConfiguration,
            repoConfig: RepositoryConfiguration
        ) = CocoaPods(managerName, analysisRoot, analyzerConfig, repoConfig)
    }

    private val podspecCache = mutableMapOf<String, Podspec>()

    override fun command(workingDir: File?) = "pod"

    override fun getVersionRequirement(): Requirement = Requirement.buildIvy("[1.11.0,)")

    override fun getVersionArguments() = "--version --allow-root"

    override fun beforeResolution(definitionFiles: List<File>) = checkVersion()

    override fun resolveDependencies(definitionFile: File, labels: Map<String, String>): List<ProjectAnalyzerResult> {
        // CocoaPods originally used and may still use the Specs repository on GitHub [1] as package metadata database.
        // Using [1] requires an initial clone which is slow to do and consumes already more than 5 GB on disk, see
        // also [2]. (Final) CDN support has been added in version 1.7.2 [3] to speed things up.
        //
        // Temporarily re-configure the repos such that 'https://cdn.cocoapods.org' is the only repository in order to
        // avoid having to clone and fetch the large Git repository. This ensures that the repositories used are
        // independent of the hosts current setup.
        //
        // [1] https://github.com/CocoaPods/Specs
        // [2] https://github.com/CocoaPods/CocoaPods/issues/7046.
        // [3] https://blog.cocoapods.org/CocoaPods-1.7.2/

        return stashDirectories(File("~/.cocoapods/repos")).use {
            run("repo", "add-cdn", "trunk", "https://cdn.cocoapods.org", "--allow-root")

            try {
                resolveDependenciesInternal(definitionFile)
            } finally {
                // The cache entries are not re-usable across definition files because the keys do not contain the
                // dependency version. If non-default Specs repositories were supported, then these would also need to
                // be part of the key. As that's more complicated and not giving much performance prefer the more memory
                // consumption friendly option of clearing the cache.
                podspecCache.clear()
            }
        }
    }

    private fun resolveDependenciesInternal(definitionFile: File): List<ProjectAnalyzerResult> {
        val workingDir = definitionFile.parentFile
        val lockfile = workingDir.resolve(LOCKFILE_FILENAME)

        val scopes = sortedSetOf<Scope>()
        val packages = sortedSetOf<Package>()
        val issues = mutableListOf<OrtIssue>()

        if (lockfile.isFile) {
            val dependencies = getPackageReferences(lockfile)

            scopes += Scope(SCOPE_NAME, dependencies)
            packages += scopes.flatMap { it.collectDependencies() }.map { getPackage(it, workingDir) }
        } else {
            issues += createAndLogIssue(
                source = managerName,
                message = "Missing lockfile '${lockfile.relativeTo(analysisRoot).invariantSeparatorsPath}' for " +
                        "definition file '${definitionFile.relativeTo(analysisRoot).invariantSeparatorsPath}'. The " +
                        "analysis of a Podfile without a lockfile is not supported at all."
            )
        }

        val projectAnalyzerResult = ProjectAnalyzerResult(
            packages = packages,
            project = Project(
                id = Identifier(
                    type = managerName,
                    namespace = "",
                    name = definitionFile.relativeTo(analysisRoot).invariantSeparatorsPath,
                    version = ""
                ),
                definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
                authors = sortedSetOf(),
                declaredLicenses = sortedSetOf(),
                vcs = VcsInfo.EMPTY,
                vcsProcessed = processProjectVcs(workingDir),
                scopeDependencies = scopes,
                homepageUrl = ""
            ),
            issues = issues
        )

        return listOf(projectAnalyzerResult)
    }

    private fun getPackage(id: Identifier, workingDir: File): Package {
        val podspec = getPodspec(id, workingDir) ?: run {
            log.warn { "Could not find a '.podspec' file for package '${id.toCoordinates()}'." }

            return Package.EMPTY.copy(id = id)
        }

        val vcs = podspec.source["git"]?.let { url ->
            VcsInfo(
                type = VcsType.GIT,
                url = url,
                revision = podspec.source["tag"].orEmpty()
            )
        }.orEmpty()

        return Package(
            id = id,
            authors = sortedSetOf(),
            declaredLicenses = podspec.license.takeUnless { it.isEmpty() }?.let { sortedSetOf(it) } ?: sortedSetOf(),
            description = podspec.summary,
            homepageUrl = podspec.homepage,
            binaryArtifact = RemoteArtifact.EMPTY,
            sourceArtifact = podspec.source["http"]?.let { RemoteArtifact(it, Hash.NONE) }.orEmpty(),
            vcs = vcs,
            vcsProcessed = processPackageVcs(vcs, podspec.homepage)
        )
    }

    private fun getPodspec(id: Identifier, workingDir: File): Podspec? {
        podspecCache[id.name]?.let { return it }

        val podspecName = id.name.substringBefore("/")

        val podspecCommand = run(
            "spec", "which", podspecName, "--version=${id.version}", "--allow-root", "--regex", workingDir = workingDir
        ).takeIf { it.isSuccess } ?: return null

        val podspecFile = File(podspecCommand.stdout.trim())

        podspecFile.readValue<Podspec>().withSubspecs().associateByTo(podspecCache) { it.name }

        return podspecCache.getValue(id.name)
    }
}

private const val LOCKFILE_FILENAME = "Podfile.lock"

private const val SCOPE_NAME = "dependencies"

private val NAME_AND_VERSION_REGEX = "(\\S+)\\s+(.*)".toRegex()

private fun getPackageReferences(podfileLock: File): SortedSet<PackageReference> {
    val versionForName = mutableMapOf<String, String>()
    val dependenciesForName = mutableMapOf<String, MutableSet<String>>()
    val root = yamlMapper.readTree(podfileLock)

    root.get("PODS").asIterable().forEach { node ->
        val entry = when (node) {
            is ObjectNode -> node.fieldNames().asSequence().first()
            else -> node.textValue()
        }

        val (name, version) = NAME_AND_VERSION_REGEX.find(entry)!!.groups.let {
            it[1]!!.value to it[2]!!.value.removeSurrounding("(", ")")
        }
        versionForName[name] = version

        val dependencies = node[entry]?.map { it.textValue().substringBefore(" ") }.orEmpty()
        dependenciesForName.getOrPut(name) { mutableSetOf() } += dependencies
    }

    fun createPackageReference(name: String): PackageReference =
        PackageReference(
            id = Identifier("Pod", "", name, versionForName.getValue(name)),
            dependencies = dependenciesForName.getValue(name).mapTo(sortedSetOf()) { createPackageReference(it) }
        )

    return root.get("DEPENDENCIES").mapTo(sortedSetOf()) { node ->
        val name = node.textValue().substringBefore(" ")
        createPackageReference(name)
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
private data class Podspec(
    val name: String = "",
    val version: String = "",
    @JsonDeserialize(using = LicenseDeserializer::class)
    val license: String = "",
    val summary: String = "",
    val homepage: String = "",
    val source: Map<String, String> = emptyMap(),
    private val subspecs: List<Podspec> = emptyList()
) {
    fun withSubspecs(): List<Podspec> {
        val result = mutableListOf<Podspec>()

        fun add(spec: Podspec, namePrefix: String) {
            val name = "$namePrefix${spec.name}"
            result += copy(name = "$namePrefix${spec.name}")
            spec.subspecs.forEach { add(it, "$name/") }
        }

        add(this, "")

        return result
    }
}

/**
 * Handle deserialization of the following two possible representations:
 *
 * 1. https://github.com/CocoaPods/Specs/blob/f75c24e7e9df1dac6ffa410a6fb30f01e026d4d6/Specs/8/5/e/SocketIOKit/2.0.1/SocketIOKit.podspec.json#L6-L9
 * 2. https://github.com/CocoaPods/Specs/blob/f75c24e7e9df1dac6ffa410a6fb30f01e026d4d6/Specs/8/5/e/FirebaseObjects/0.0.1/FirebaseObjects.podspec.json#L6
 */
private class LicenseDeserializer : StdDeserializer<String>(String::class.java) {
    override fun deserialize(parser: JsonParser, context: DeserializationContext): String {
        val node = parser.codec.readTree<JsonNode>(parser)

        return if (node.isTextual) {
            node.textValue()
        } else {
            node["type"].textValueOrEmpty()
        }
    }
}
