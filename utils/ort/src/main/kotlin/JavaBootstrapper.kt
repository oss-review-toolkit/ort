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

package org.ossreviewtoolkit.utils.ort

import eu.hansolo.jdktools.ArchiveType
import eu.hansolo.jdktools.Latest
import eu.hansolo.jdktools.LibCType
import eu.hansolo.jdktools.Match
import eu.hansolo.jdktools.OperatingSystem
import eu.hansolo.jdktools.PackageType
import eu.hansolo.jdktools.TermOfSupport
import eu.hansolo.jdktools.util.Helper

import io.foojay.api.discoclient.DiscoClient
import io.foojay.api.discoclient.pkg.Scope

import java.io.File

import kotlin.time.measureTime
import kotlin.time.measureTimedValue

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.safeMkdirs
import org.ossreviewtoolkit.utils.common.unpack

import org.semver4j.Semver

object JavaBootstrapper {
    private val discoClient by lazy { DiscoClient(Environment.ORT_USER_AGENT) }

    /**
     * Return the single top-level directory contained in this directory, if any, or return this directory otherwise.
     */
    private fun File.singleContainedDirectoryOrThis() =
        walk().maxDepth(1).filter { it != this && it.isDirectory }.singleOrNull() ?: this

    /**
     * Return whether ORT is running on a JDK (not JRE) of the specified [version].
     */
    fun isRunningOnJdk(version: String): Boolean {
        val requestedVersion = Semver.coerce(version)
        val runningVersion = Semver.coerce(Environment.JAVA_VERSION)
        if (requestedVersion != runningVersion) return false

        val javaHome = System.getProperty("java.home") ?: return false
        val javac = File(javaHome).resolve("bin").resolve("javac")
        return Os.resolveExecutable(javac) != null
    }

    /**
     * Install a JDK matching [distributionName] and [version] below [ortToolsDirectory] and return its directory on
     * success, or an exception on failure.
     */
    fun installJdk(distributionName: String, version: String): Result<File> {
        val versionResult = eu.hansolo.jdktools.versioning.Semver.fromText(version)
        if (versionResult.error1 != null) return Result.failure(versionResult.error1)

        val semVer = versionResult.semver1

        logger.info { "Setting up JDK '$distributionName' in version '$semVer'..." }

        val operatingSystem = Helper.getOperatingSystem()
        val architecture = Helper.getArchitecture()

        val libcType = when (operatingSystem) {
            OperatingSystem.LINUX -> LibCType.GLIBC
            OperatingSystem.LINUX_MUSL, OperatingSystem.ALPINE_LINUX -> LibCType.MUSL
            else -> LibCType.NONE
        }

        val pkgs = discoClient.getPkgs(
            /* distributions = */ null,
            semVer.versionNumber,
            Latest.PER_VERSION,
            operatingSystem,
            libcType,
            architecture,
            architecture.bitness,
            if (operatingSystem == OperatingSystem.WINDOWS) ArchiveType.ZIP else ArchiveType.TAR_GZ,
            PackageType.JDK,
            /* javafxBundled = */ false,
            /* directlyDownloadable = */ true,
            listOf(semVer.releaseStatus),
            TermOfSupport.NONE,
            listOf(Scope.PUBLIC),
            Match.ANY
        )

        val pkg = pkgs.sortedBy { it.id }.find { it.distributionName == distributionName }
            ?: return Result.failure(
                IllegalArgumentException(
                    "No package found for JDK '$distributionName' in version '$version'."
                )
            )

        val installDir = ortToolsDirectory.resolve("jdks").resolve(pkg.id).apply {
            if (isDirectory) {
                logger.info { "Not downloading the JDK again as the directory '$this' already exists." }
                return Result.success(singleContainedDirectoryOrThis())
            }

            safeMkdirs()
        }

        val url = discoClient.getPkgDirectDownloadUri(pkg.id)
        logger.info { "Downloading the JDK package from $url..." }

        val (archive, downloadDuration) = measureTimedValue {
            okHttpClient.downloadFile(url, installDir).getOrElse {
                return Result.failure(it)
            }
        }

        logger.info { "Downloading the JDK took $downloadDuration." }

        val unpackDuration = measureTime { archive.unpack(installDir) }

        logger.info { "Unpacking the JDK took $unpackDuration." }

        if (!archive.delete()) {
            logger.warn { "Unable to delete the JDK archive from '$archive'." }
        }

        return Result.success(installDir.singleContainedDirectoryOrThis())
    }
}
