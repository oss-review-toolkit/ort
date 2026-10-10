/*
 * Copyright (C) 2024 The ORT Project Copyright Holders
 * <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.packagemanagers.python

import java.io.File
import java.lang.invoke.MethodHandles

import net.peanuuutz.tomlkt.Toml
import net.peanuuutz.tomlkt.getStringOrNull
import net.peanuuutz.tomlkt.getTableOrNull
import net.peanuuutz.tomlkt.parseToTomlTable

import org.apache.logging.log4j.kotlin.loggerOf

import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.PackageManagerFactory
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.common.div
import org.ossreviewtoolkit.utils.ort.showStackTrace

private const val PYPI_TYPE = "PyPI"

private val pyProjectToml = Toml {
    ignoreUnknownKeys = true
}

private val logger = loggerOf(MethodHandles.lookup().lookupClass())

@OrtPlugin(
    id = "UV",
    displayName = "uv",
    description = "The uv package manager for Python.",
    factory = PackageManagerFactory::class
)
class Uv(
    override val descriptor: PluginDescriptor = UvFactory.descriptor
) : PackageManager("UV") {
    override val globsForDefinitionFiles = listOf("uv.lock")

    override fun resolveDependencies(
        analysisRoot: File,
        definitionFile: File,
        excludes: org.ossreviewtoolkit.model.config.Excludes,
        analyzerConfig: org.ossreviewtoolkit.model.config.AnalyzerConfiguration,
        labels: Map<String, String>
    ): List<ProjectAnalyzerResult> {
        val lockfile = runCatching { definitionFile.parseUvLockfile() }.getOrElse { failure ->
            failure.showStackTrace()

            val message = buildString {
                appendLine("Unable to parse '${definitionFile.invariantSeparatorsPath}':")
                appendLine(failure.collectMessages())
            }

            throw IllegalArgumentException(message, failure)
        }

        val pyProject = definitionFile.parentFile.resolve("pyproject.toml")
        val pyProjectMetadata = pyProject.readMetadata()

        val projectPackage = lockfile.findProjectPackage(pyProjectMetadata?.name, definitionFile.parentFile)
            ?: throw IllegalStateException(
                "No entry representing the current project was found in '${definitionFile.invariantSeparatorsPath}'."
            )

        val packageIndex = PackageIndex(lockfile.packages)

        val packages = lockfile.packages
            .filterNot { it === projectPackage }
            .mapNotNullTo(mutableSetOf()) { pkg ->
                val identifier = pkg.toIdentifier()
                packageIndex.registerOrtId(pkg, identifier)
                pkg.toOrtPackage(definitionFile.parentFile, identifier)
            }

        val projectScopes = projectPackage.collectScopeDependencies().mapTo(mutableSetOf()) { (name, deps) ->
            Scope(name, deps.toPackageReferences(packageIndex))
        }

        val project = createProject(analysisRoot, definitionFile, pyProjectMetadata, projectScopes)

        return listOf(ProjectAnalyzerResult(project, packages))
    }

    private fun createProject(
        analysisRoot: File,
        definitionFile: File,
        metadata: PyProjectMetadata?,
        scopes: Set<Scope>
    ): Project {
        val definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path
        val projectId = Identifier(
            type = projectType,
            namespace = "",
            name = definitionFile.relativeTo(analysisRoot).invariantSeparatorsPath,
            version = VersionControlSystem.getCloneInfo(definitionFile.parentFile).revision
        )

        return Project(
            id = projectId,
            definitionFilePath = definitionFilePath,
            declaredLicenses = emptySet(),
            vcs = VcsInfo.EMPTY,
            vcsProcessed = processProjectVcs(definitionFile.parentFile),
            homepageUrl = metadata?.homepageUrl.orEmpty(),
            scopeDependencies = scopes
        )
    }
}

private data class PyProjectMetadata(
    val name: String?,
    val version: String?,
    val homepageUrl: String?
)

private fun File.readMetadata(): PyProjectMetadata? {
    if (!isFile) return null

    val table = runCatching { pyProjectToml.parseToTomlTable(reader()) }.getOrElse { return null }
    val project = table.getTableOrNull("project")

    val homepage = project?.getStringOrNull("homepage")
        ?: project?.getTableOrNull("urls")?.entries?.firstOrNull()?.value?.toString()

    return PyProjectMetadata(
        name = project?.getStringOrNull("name"),
        version = project?.getStringOrNull("version"),
        homepageUrl = homepage
    )
}

private class PackageIndex(packages: List<UvPackage>) {
    private val packagesByName = packages.groupBy { it.name.lowercase() }
    private val ortIds = mutableMapOf<UvPackage, Identifier>()

    fun findPackage(dependency: UvDependency): UvPackage? {
        val candidates = packagesByName[dependency.name.lowercase()].orEmpty()
        if (candidates.isEmpty()) return null

        val versionFiltered = dependency.version?.let { version ->
            candidates.filter { it.version == version }
        }.orEmpty().ifEmpty { candidates }

        val sourceFiltered = dependency.source?.let { source ->
            versionFiltered.filter { it.source.isEquivalentTo(source) }
        }.orEmpty().ifEmpty { versionFiltered }

        return sourceFiltered.singleOrNull()
    }

    fun registerOrtId(pkg: UvPackage, identifier: Identifier) {
        ortIds[pkg] = identifier
    }

    fun identifierFor(pkg: UvPackage): Identifier =
        ortIds[pkg] ?: Identifier(
            type = PYPI_TYPE,
            namespace = "",
            name = pkg.name,
            version = pkg.version.orEmpty()
        )
}

private fun List<UvDependency>.toPackageReferences(
    packageIndex: PackageIndex,
    visited: Set<Identifier> = emptySet()
): Set<PackageReference> =
    mapNotNullTo(mutableSetOf()) { dependency ->
        val pkg = packageIndex.findPackage(dependency)

        if (pkg == null) {
            logger.warn { "Unable to find package information for dependency '${dependency.name}'." }
            null
        } else {
            pkg.toPackageReference(packageIndex, visited)
        }
    }

private fun UvPackage.toPackageReference(
    packageIndex: PackageIndex,
    visited: Set<Identifier>
): PackageReference {
    val id = packageIndex.identifierFor(this)
    if (id in visited) return PackageReference(id = id)

    val nextVisited = visited + id
    return PackageReference(
        id = id,
        dependencies = dependencies.toPackageReferences(packageIndex, nextVisited)
    )
}

private fun UvPackage.collectScopeDependencies(): Map<String, List<UvDependency>> {
    val scopes = mutableMapOf<String, List<UvDependency>>()

    if (dependencies.isNotEmpty()) scopes["main"] = dependencies

    fun Map<String, List<UvDependency>>.mergeInto(target: MutableMap<String, List<UvDependency>>) {
        for ((name, deps) in this) {
            if (deps.isEmpty()) continue

            val existing = target[name].orEmpty()
            target[name] = existing + deps
        }
    }

    devDependencies.mergeInto(scopes)
    dependencyGroups.mergeInto(scopes)
    metadata?.dependencyGroups?.mergeInto(scopes)
    metadata?.requiresDev?.mergeInto(scopes)

    return scopes.ifEmpty { mapOf("main" to emptyList()) }
}

private fun UvPackage.toIdentifier(): Identifier {
    val versionValue = version.orEmpty()
    if (versionValue.isEmpty()) {
        return Identifier(type = PYPI_TYPE, namespace = "", name = name, version = "")
    }

    return Identifier(type = PYPI_TYPE, namespace = "", name = name, version = versionValue)
}

private fun UvPackage.toOrtPackage(definitionDir: File, id: Identifier): Package? {
    if (id.version.isEmpty()) {
        logger.warn { "Skipping package '$name' because it does not declare a version." }
        return null
    }

    val binaryArtifact = wheels.firstOrNull()?.toRemoteArtifact()
    val sourceArtifact = sdist?.toRemoteArtifact()

    val vcsInfo = source.toVcsInfo(definitionDir)
    val processedVcs = PackageManager.processPackageVcs(vcsInfo)

    return Package(
        id = id,
        declaredLicenses = emptySet(),
        description = "",
        homepageUrl = "",
        binaryArtifact = binaryArtifact ?: RemoteArtifact.EMPTY,
        sourceArtifact = sourceArtifact ?: RemoteArtifact.EMPTY,
        vcs = vcsInfo,
        vcsProcessed = processedVcs
    )
}

private fun UvDistribution.toRemoteArtifact(): RemoteArtifact {
    val hashValue = hash.orEmpty()
    val parsedHash = when {
        hashValue.isBlank() -> Hash.NONE
        ":" in hashValue -> {
            val (algorithm, value) = hashValue.split(':', limit = 2)
            Hash(value, algorithm)
        }
        else -> Hash.create(hashValue)
    }

    return RemoteArtifact(
        url = url.orEmpty(),
        hash = parsedHash
    )
}

private fun UvSource?.isEquivalentTo(other: UvSource): Boolean {
    if (this == null) return false

    return registry == other.registry &&
        url == other.url &&
        normalizeGitUrl(git) == normalizeGitUrl(other.git) &&
        rev == other.rev &&
        resolved == other.resolved &&
        path == other.path &&
        editable == other.editable &&
        virtual == other.virtual &&
        workspace == other.workspace &&
        subdirectory == other.subdirectory
}

private fun UvSource?.toVcsInfo(definitionDir: File): VcsInfo {
    if (this == null) return VcsInfo.EMPTY

    git?.let { gitUrl ->
        val (url, revision) = gitUrl.split('#', limit = 2).let { segments ->
            val base = normalizeGitUrl(segments.first()) ?: segments.first()
            val rev = segments.getOrNull(1)?.takeIf { it.isNotBlank() } ?: resolved ?: rev
            base to rev
        }

        return VcsInfo(
            type = VcsType.GIT,
            url = url,
            revision = revision.orEmpty(),
            path = subdirectory.orEmpty()
        )
    }

    if (url != null) {
        return VcsInfo(VcsType.UNKNOWN, url, revision = resolved.orEmpty(), path = subdirectory.orEmpty())
    }

    fun resolveLocal(pathValue: String?): File? {
        if (pathValue.isNullOrBlank()) return null
        val pathFile = File(pathValue)
        return if (pathFile.isAbsolute) pathFile.normalize() else (definitionDir / pathFile).normalize()
    }

    resolveLocal(editable)?.let {
        return VcsInfo(VcsType.UNKNOWN, it.toURI().toString(), revision = resolved.orEmpty(), path = "")
    }

    resolveLocal(path)?.let {
        return VcsInfo(VcsType.UNKNOWN, it.toURI().toString(), revision = resolved.orEmpty(), path = "")
    }

    resolveLocal(virtual)?.let {
        return VcsInfo(VcsType.UNKNOWN, it.toURI().toString(), revision = resolved.orEmpty(), path = "")
    }

    return VcsInfo.EMPTY
}

private fun normalizeGitUrl(url: String?): String? =
    url?.substringBefore('?')

private fun UvLockfile.findProjectPackage(projectName: String?, definitionDir: File): UvPackage? {
    if (projectName != null) {
        packages.find { it.name == projectName }?.let { return it }
    }

    return packages.find { pkg ->
        pkg.source?.pointsTo(definitionDir) == true
    }
}

private fun UvSource.pointsTo(directory: File): Boolean {
    val candidates = listOfNotNull(editable, path, virtual)
    if (candidates.isEmpty()) return false

    return candidates.any {
        val candidatePath = File(it)
        val resolved = if (candidatePath.isAbsolute) candidatePath.normalize() else directory.resolve(candidatePath).normalize()
        resolved == directory.normalize()
    }
}
