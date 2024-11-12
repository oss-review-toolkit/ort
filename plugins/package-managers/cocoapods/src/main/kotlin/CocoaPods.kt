/*
 * Copyright (C) 2021 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.cocoapods

import java.io.File
import java.io.IOException

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.analyzer.AbstractPackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManager
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
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.collectDependencies
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.model.orEmpty
import org.ossreviewtoolkit.model.utils.toPurl
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.common.stashDirectories

import org.semver4j.RangesList
import org.semver4j.RangesListFactory

/**
 * The [CocoaPods](https://cocoapods.org/) package manager for Objective-C.
 *
 * As pre-condition for the analysis each respective definition file must have a sibling lockfile named 'Podfile.lock'.
 * The dependency tree is constructed solely based on parsing that lockfile. So, the dependency tree can be constructed
 * on any platform. Note that obtaining the dependency tree from the 'pod' command without a lockfile has Xcode
 * dependencies and is not supported by this class.
 *
 * The only interactions with the 'pod' command happen in order to obtain metadata for dependencies. Therefore,
 * 'pod spec which' gets executed, which works also under Linux.
 */
class CocoaPods(
    name: String,
    analysisRoot: File,
    analyzerConfig: AnalyzerConfiguration,
    repoConfig: RepositoryConfiguration
) : PackageManager(name, "CocoaPods", analysisRoot, analyzerConfig, repoConfig), CommandLineTool {
    class Factory : AbstractPackageManagerFactory<CocoaPods>("CocoaPods") {
        override val globsForDefinitionFiles = listOf("Podfile")

        override fun create(
            analysisRoot: File,
            analyzerConfig: AnalyzerConfiguration,
            repoConfig: RepositoryConfiguration
        ) = CocoaPods(type, analysisRoot, analyzerConfig, repoConfig)
    }

    private val podspecCache = mutableMapOf<String, Podspec>()

    override fun command(workingDir: File?) = if (Os.isWindows) "pod.bat" else "pod"

    override fun getVersionRequirement(): RangesList = RangesListFactory.create(">=1.11.0")

    override fun getVersionArguments() = "--version --allow-root"

    override fun beforeResolution(definitionFiles: List<File>) = checkVersion()

    override fun resolveDependencies(definitionFile: File, labels: Map<String, String>): List<ProjectAnalyzerResult> =
        stashDirectories(Os.userHomeDirectory.resolve(".cocoapods/repos")).use {
            // Ensure to use the CDN instead of the monolithic specs repo.
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

    private fun resolveDependenciesInternal(definitionFile: File): List<ProjectAnalyzerResult> {
        val workingDir = definitionFile.parentFile
        val lockfile = workingDir.resolve(LOCKFILE_FILENAME)

        val scopes = mutableSetOf<Scope>()
        val packages = mutableSetOf<Package>()
        val issues = mutableListOf<Issue>()

        if (lockfile.isFile) {
            val lockfileData = parseLockfile(lockfile)

            scopes += Scope(SCOPE_NAME, lockfileData.dependencies)
            packages += scopes.collectDependencies().map {
                lockfileData.packagesFromCheckoutOptionsForId[it] ?: getPackage(it, workingDir)
            }
        } else {
            issues += createAndLogIssue(
                source = managerName,
                message = "Missing lockfile '${lockfile.relativeTo(analysisRoot).invariantSeparatorsPath}' for " +
                    "definition file '${definitionFile.relativeTo(analysisRoot).invariantSeparatorsPath}'. The " +
                    "analysis of a Podfile without a lockfile is not supported."
            )
        }

        val projectAnalyzerResult = ProjectAnalyzerResult(
            packages = packages,
            project = Project(
                id = Identifier(
                    type = managerName,
                    namespace = "",
                    name = getFallbackProjectName(analysisRoot, definitionFile),
                    version = ""
                ),
                definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
                authors = emptySet(),
                declaredLicenses = emptySet(),
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
        val podspec = getPodspec(id, workingDir) ?: return Package.EMPTY.copy(id = id, purl = id.toPurl())

        val vcs = podspec.source?.git?.let { url ->
            VcsInfo(
                type = VcsType.GIT,
                url = url,
                revision = podspec.source.tag.orEmpty()
            )
        }.orEmpty()

        return Package(
            id = id,
            authors = emptySet(),
            declaredLicenses = setOfNotNull(podspec.license.takeUnless { it.isEmpty() }),
            description = podspec.summary,
            homepageUrl = podspec.homepage,
            binaryArtifact = RemoteArtifact.EMPTY,
            sourceArtifact = podspec.source?.http?.let { RemoteArtifact(it, Hash.NONE) }.orEmpty(),
            vcs = vcs,
            vcsProcessed = processPackageVcs(vcs, podspec.homepage)
        )
    }

    private fun getPodspec(id: Identifier, workingDir: File): Podspec? {
        podspecCache[id.name]?.let { return it }

        val podspecName = id.name.substringBefore("/")

        val podspecCommand = runCatching {
            run(
                "spec", "which", "^$podspecName$",
                "--version=${id.version}",
                "--allow-root",
                "--regex",
                workingDir = workingDir
            )
        }.getOrElse {
            val messages = it.collectMessages()

            logger.warn {
                "Failed to get the '.podspec' file for package '${id.toCoordinates()}': $messages"
            }

            if ("SSL peer certificate or SSH remote key was not OK" in messages) {
                // When running into this error (see e.g. https://github.com/CocoaPods/CocoaPods/issues/11159) abort
                // immediately, because connections are retried multiple times for each package's podspec to retrieve
                // which would otherwise take a very long time.
                throw IOException(messages)
            }

            return null
        }

        val podspecFile = File(podspecCommand.stdout.trim())
        val podspec = podspecFile.readText().parsePodspec()

        podspec.withSubspecs().associateByTo(podspecCache) { it.name }

        return podspecCache.getValue(id.name)
    }
}

private const val LOCKFILE_FILENAME = "Podfile.lock"

private const val SCOPE_NAME = "dependencies"

private data class LockfileData(
    val dependencies: Set<PackageReference>,
    val packagesFromCheckoutOptionsForId: Map<Identifier, Package>
)

private fun parseLockfile(podfileLock: File): LockfileData {
    val lockfile = podfileLock.readText().parseLockfile()
    val resolvedVersions = mutableMapOf<String, String>()
    val dependencyConstraints = mutableMapOf<String, MutableSet<String>>()

    // The "PODS" section lists the resolved dependencies and, nested by one level, any version constraints of their
    // direct dependencies. That is, the nesting never goes deeper than two levels.
    lockfile.pods.map { pod ->
        resolvedVersions[pod.name] = checkNotNull(pod.version)

        if (pod.dependencies.isNotEmpty()) {
            dependencyConstraints[pod.name] = pod.dependencies.mapTo(mutableSetOf()) {
                // Discard the version (which is only a constraint in this case) and just take the name.
                it.name
            }
        }
    }

    val packagesFromCheckoutOptionsForId = lockfile.checkoutOptions.mapNotNull { (name, checkoutOption) ->
        val url = checkoutOption.git ?: return@mapNotNull null
        val revision = checkoutOption.commit.orEmpty()

        // The version written to the lockfile matches the version specified in the project's ".podspec" file at the
        // given revision, so the same version might be used in different revisions. To still get a unique identifier,
        // append the revision to the version.
        val versionFromPodspec = checkNotNull(resolvedVersions[name])
        val uniqueVersion = "$versionFromPodspec-$revision"
        val id = Identifier("Pod", "", name, uniqueVersion)

        // Write the unique version back for correctly associating dependencies below.
        resolvedVersions[name] = uniqueVersion

        id to Package(
            id = id,
            declaredLicenses = emptySet(),
            description = "",
            homepageUrl = url,
            binaryArtifact = RemoteArtifact.EMPTY,
            sourceArtifact = RemoteArtifact.EMPTY,
            vcs = VcsInfo(VcsType.GIT, url, revision)
        )
    }.toMap()

    fun createPackageReference(name: String): PackageReference =
        PackageReference(
            id = Identifier("Pod", "", name, resolvedVersions.getValue(name)),
            dependencies = dependencyConstraints[name].orEmpty().filter {
                // Only use a constraint as a dependency if it has a resolved version.
                it in resolvedVersions
            }.mapTo(mutableSetOf()) {
                createPackageReference(it)
            }
        )

    // The "DEPENDENCIES" section lists direct dependencies, but only along with version constraints, not with their
    // resolved versions, and eventually additional information about the source.
    val dependencies = lockfile.dependencies.mapTo(mutableSetOf()) { dependency ->
        // Ignore the version (which is only a constraint in this case) and just take the name.
        createPackageReference(dependency.name)
    }

    return LockfileData(dependencies, packagesFromCheckoutOptionsForId)
}
