/*
 * Copyright (C) 2024 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.bazel

import java.io.File
import java.util.Base64

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.analyzer.AbstractPackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.clients.bazelmoduleregistry.BazelModuleRegistryService
import org.ossreviewtoolkit.clients.bazelmoduleregistry.LocalBazelModuleRegistryService
import org.ossreviewtoolkit.clients.bazelmoduleregistry.ModuleMetadata
import org.ossreviewtoolkit.clients.bazelmoduleregistry.ModuleSourceInfo
import org.ossreviewtoolkit.clients.bazelmoduleregistry.RemoteBazelModuleRegistryService
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.HashAlgorithm
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageLinkage
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.orEmpty
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.common.encodeHex
import org.ossreviewtoolkit.utils.common.withoutPrefix

import org.semver4j.RangesList
import org.semver4j.RangesListFactory

private const val LOCKFILE_NAME = "MODULE.bazel.lock"

private val JSON = Json {
    ignoreUnknownKeys = true
}

class Bazel(
    name: String,
    analysisRoot: File,
    analyzerConfig: AnalyzerConfiguration,
    repoConfig: RepositoryConfiguration
) : PackageManager(name, analysisRoot, analyzerConfig, repoConfig), CommandLineTool {
    class Factory : AbstractPackageManagerFactory<Bazel>("Bazel") {
        override val globsForDefinitionFiles = listOf("MODULE", "MODULE.bazel")

        override fun create(
            analysisRoot: File,
            analyzerConfig: AnalyzerConfiguration,
            repoConfig: RepositoryConfiguration
        ) = Bazel(type, analysisRoot, analyzerConfig, repoConfig)
    }

    override fun command(workingDir: File?) = "bazel"

    override fun getVersionArguments() = "version"

    override fun transformVersion(output: String) = transformBazelVersion(output)

    // Bazel 6.0 already supports bzlmod but it is not enabled by default.
    // Supporting it would require adding the flag "--enable_bzlmod=true" at the correct position of all bazel
    // invocations.
    override fun getVersionRequirement(): RangesList = RangesListFactory.create(">=7.0")

    override fun resolveDependencies(definitionFile: File, labels: Map<String, String>): List<ProjectAnalyzerResult> {
        val projectDir = definitionFile.parentFile
        val lockfile = projectDir.resolve(LOCKFILE_NAME)
        val projectVcs = processProjectVcs(projectDir)
        val moduleMetadata = Parser(definitionFile.readText()).parse()
        val depDirectives = moduleMetadata.dependencies.associateBy { "${it.name}@${it.version}" }
        val scopes = getDependencyGraph(projectDir, depDirectives)

        // If no lock file is present, getDependencyGraph() runs "bazel mod graph", which creates a MODULE.bazel.lock
        // file as a side effect. That file contains the URL of the Bazel module registry that was used for dependency
        // resolution.
        val registryUrl = parseLockfile(lockfile).registryUrl()

        val registry = registryUrl.withoutPrefix("file://")?.let {
            val localRegistryURL = it.replace("%workspace%", projectDir.absolutePath)
            logger.info {
                "Using local Bazel module registry at '$localRegistryURL'."
            }
            LocalBazelModuleRegistryService(File(localRegistryURL))
        } ?: RemoteBazelModuleRegistryService.create(registryUrl)

        val packages = getPackages(scopes, registry)

        return listOf(
            ProjectAnalyzerResult(
                project = Project(
                    id = Identifier(
                        type = managerName,
                        namespace = "",
                        name = moduleMetadata.module?.name ?: VersionControlSystem.getPathInfo(definitionFile).path,
                        version = moduleMetadata.module?.version.orEmpty()
                    ),
                    definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
                    declaredLicenses = emptySet(),
                    vcs = projectVcs,
                    vcsProcessed = projectVcs,
                    homepageUrl = "",
                    scopeDependencies = scopes
                ),
                packages = packages
            )
        )
    }

    private fun getPackages(scopes: Set<Scope>, registry: BazelModuleRegistryService): Set<Package> {
        val ids = scopes.flatMapTo(mutableSetOf()) { it.collectDependencies() }
        val moduleMetadataForId = ids.associateWith { getModuleMetadata(it, registry) }
        val moduleSourceInfoForId = ids.associateWith { getModuleSourceInfo(it, registry) }
        return ids.mapTo(mutableSetOf()) { getPackage(it, moduleMetadataForId[it], moduleSourceInfoForId[it]) }
    }

    private fun getPackage(id: Identifier, metadata: ModuleMetadata?, sourceInfo: ModuleSourceInfo?) =
        Package(
            id = id,
            declaredLicenses = emptySet(),
            description = "",
            homepageUrl = metadata?.homepage?.toString().orEmpty(),
            binaryArtifact = RemoteArtifact.EMPTY,
            sourceArtifact = sourceInfo?.remoteArtifact().orEmpty(),
            vcs = metadata?.vcsInfo().orEmpty()
        )

    private fun getModuleMetadata(id: Identifier, registry: BazelModuleRegistryService): ModuleMetadata? =
        runCatching {
            runBlocking { registry.getModuleMetadata(id.name) }
        }.onFailure {
            logger.warn { "Failed to fetch metadata for Bazel module '${id.name}': ${it.collectMessages()}" }
        }.getOrNull()

    private fun getModuleSourceInfo(id: Identifier, registry: BazelModuleRegistryService): ModuleSourceInfo? =
        runCatching {
            runBlocking { registry.getModuleSourceInfo(id.name, id.version) }
        }.onFailure {
            logger.warn {
                "Failed to fetch source info for Bazel module '${id.name}@${id.version}': ${it.collectMessages()}"
            }
        }.getOrNull()

    private fun getDependencyGraph(projectDir: File, depDirectives: Map<String, BazelDepDirective>): Set<Scope> {
        val modGraphProcess = run("mod", "graph", "--output", "json", workingDir = projectDir)
        val node = JSON.decodeFromString<ModuleGraphNode>(modGraphProcess.stdout)
        val devDeps = node.dependencies.filter { depDirectives[it.key]?.devDependency == true }.toSet()
        val mainDeps = node.dependencies.toSet() - devDeps

        return setOf(
            Scope(
                name = "main",
                dependencies = mainDeps.map { parseModuleGraphNode(it) }.toSet()
            ),
            Scope(
                name = "dev",
                dependencies = devDeps.map { parseModuleGraphNode(it) }.toSet()
            )
        )
    }

    private fun parseModuleGraphNode(node: ModuleGraphNode): PackageReference =
        PackageReference(
            id = Identifier(
                type = managerName,
                namespace = "",
                name = node.name ?: node.key.substringBefore("@", ""),
                version = node.version ?: node.key.substringAfter("@", "")
            ),
            // Static linking is the default for cc_binary targets. According to the documentation, it is off for
            // cc_test, but according to experiments, it is on.
            // See https://bazel.build/reference/be/c-cpp#cc_binary.linkstatic
            linkage = PackageLinkage.STATIC,
            dependencies = node.dependencies.map { parseModuleGraphNode(it) }.toSet()
        )
}

internal fun transformBazelVersion(output: String) =
    output.lineSequence()
        .find { it.startsWith("Build label") }
        .orEmpty()
        .split(':')
        .getOrElse(1) { "" }
        .trim()

private fun ModuleMetadata.vcsInfo(): VcsInfo {
    val repo = repository?.firstOrNull().orEmpty()

    // From looking at all current values of this field on BCR, it looks like only the special value "github:"
    // exists. Otherwise, it's just the repo URL.
    val url = if (repo.startsWith("github:")) {
        val path = repo.substringAfter("github:")
        "https://github.com/$path"
    } else {
        repo
    }

    return VcsInfo(
        type = VcsType.GIT,
        url = url,
        revision = "",
        path = ""
    )
}

private fun ModuleSourceInfo.remoteArtifact(): RemoteArtifact {
    val (algo, b64digest) = integrity.split("-", limit = 2)
    val digest = Base64.getDecoder().decode(b64digest).encodeHex()

    val hash = Hash(
        value = digest,
        algorithm = HashAlgorithm.fromString(algo)
    )

    return RemoteArtifact(url = url.toString(), hash = hash)
}
