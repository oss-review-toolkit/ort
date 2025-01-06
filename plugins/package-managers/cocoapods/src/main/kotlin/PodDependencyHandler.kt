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

package org.ossreviewtoolkit.plugins.packagemanagers.cocoapods

import java.io.File
import java.io.IOException

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.analyzer.PackageManager.Companion.processPackageVcs
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageLinkage
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.orEmpty
import org.ossreviewtoolkit.model.utils.DependencyHandler
import org.ossreviewtoolkit.model.utils.toPurl

internal class PodDependencyHandler : DependencyHandler<Lockfile.Pod> {
    private val podspecCache = mutableMapOf<String, Podspec>()

    fun clearPodspecCache() = podspecCache.clear()

    override fun identifierFor(dependency: Lockfile.Pod): Identifier =
        with(dependency) {
            // The version written to the lockfile matches the version specified in the project's ".podspec" file at the
            // given revision, so the same version might be used in different revisions. To still get a unique
            // identifier, append the revision to the version.
            val uniqueVersion = listOfNotNull(version, checkoutOption?.commit).joinToString("-")
            Identifier("Pod", "", name, uniqueVersion)
        }

    override fun dependenciesFor(dependency: Lockfile.Pod): List<Lockfile.Pod> =
        dependency.dependencies.mapNotNull { it.resolvedPod }

    override fun linkageFor(dependency: Lockfile.Pod): PackageLinkage = PackageLinkage.DYNAMIC

    override fun createPackage(dependency: Lockfile.Pod, issues: MutableCollection<Issue>): Package {
        val id = identifierFor(dependency)

        return if (dependency.checkoutOption != null) {
            val url = dependency.checkoutOption.git.orEmpty()
            val revision = dependency.checkoutOption.commit.orEmpty()

            Package(
                id = id,
                declaredLicenses = emptySet(),
                description = "",
                homepageUrl = url,
                binaryArtifact = RemoteArtifact.EMPTY,
                sourceArtifact = RemoteArtifact.EMPTY,
                vcs = VcsInfo(VcsType.GIT, url, revision)
            )
        } else {
            val basePodName = dependency.name.substringBefore('/')
            val podspec = podspecCache.getOrPut(basePodName) {
                getPodspec(basePodName, dependency.version) ?: return Package.EMPTY.copy(id = id, purl = id.toPurl())
            }

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
    }

    private fun getPodspec(name: String, version: String): Podspec? {
        val podspecProcess = CocoaPodsCommand.run(
            "spec", "which", "^$name$",
            "--version=$version",
            "--allow-root",
            "--regex"
        )

        if (podspecProcess.isError) {
            logger.warn {
                "Failed to get the '.podspec' file for package '$name' and version '$version': " +
                    podspecProcess.errorMessage
            }

            if (podspecProcess.errorMessage == "SSL peer certificate or SSH remote key was not OK") {
                // When running into this error (see e.g. https://github.com/CocoaPods/CocoaPods/issues/11159) abort
                // immediately, because connections are retried multiple times for each package's podspec to retrieve
                // which would otherwise take a very long time.
                throw IOException(podspecProcess.errorMessage)
            }

            return null
        }

        val podspecFile = File(podspecProcess.stdout.trim())
        return podspecFile.readText().parsePodspec()
    }
}
