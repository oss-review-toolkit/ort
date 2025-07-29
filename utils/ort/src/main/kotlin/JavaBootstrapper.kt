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

import java.io.File

import kotlin.time.measureTime
import kotlin.time.measureTimedValue

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.clients.foojay.Architecture
import org.ossreviewtoolkit.clients.foojay.ArchiveType
import org.ossreviewtoolkit.clients.foojay.DiscoService
import org.ossreviewtoolkit.clients.foojay.Distribution
import org.ossreviewtoolkit.clients.foojay.Latest
import org.ossreviewtoolkit.clients.foojay.LibCType
import org.ossreviewtoolkit.clients.foojay.OperatingSystem
import org.ossreviewtoolkit.clients.foojay.Package
import org.ossreviewtoolkit.clients.foojay.PackageType
import org.ossreviewtoolkit.clients.foojay.ReleaseStatus
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.div
import org.ossreviewtoolkit.utils.common.enumSetOf
import org.ossreviewtoolkit.utils.common.safeMkdirs
import org.ossreviewtoolkit.utils.common.unpack

import org.semver4j.Semver

object JavaBootstrapper {
    private val discoService = DiscoService.create()

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
        val javac = File(javaHome) / "bin" / "javac"
        return Os.resolveExecutable(javac) != null
    }

    /**
     * Find a JDK package matching [distributionName] and [version]. Return it on success, or an exception on failure.
     */
    internal fun findJdkPackage(distributionName: String, version: String): Result<Package> {
        val distro = runCatching {
            Distribution.valueOf(distributionName.uppercase())
        }.getOrElse {
            return Result.failure(
                IllegalArgumentException("No JDK package for unsupported distribution '$distributionName' found.")
            )
        }

        val os = when (Os.Name.current) {
            Os.Name.LINUX -> OperatingSystem.LINUX
            Os.Name.MAC -> OperatingSystem.MACOS
            Os.Name.WINDOWS -> OperatingSystem.WINDOWS
            else -> return Result.failure(
                IllegalArgumentException("No JDK package for unsupported operating system '${Os.Name.current}' found.")
            )
        }

        val arch = when (Os.Arch.current) {
            Os.Arch.X86_64 -> Architecture.X86_64
            Os.Arch.AARCH64 -> Architecture.AARCH64
            else -> Architecture.X86
        }

        logger.info { "Setting up JDK '$distributionName' in version $version..." }

        val packages = runCatching {
            runBlocking {
                discoService.getPackages(
                    version,
                    enumSetOf(distro),
                    enumSetOf(arch),
                    enumSetOf(ArchiveType.TAR, ArchiveType.TAR_GZ, ArchiveType.TGZ, ArchiveType.ZIP),
                    enumSetOf(PackageType.JDK),
                    enumSetOf(os),
                    if (os == OperatingSystem.LINUX) enumSetOf(LibCType.GLIBC) else enumSetOf(),
                    enumSetOf(ReleaseStatus.GENERAL_AVAILABILITY),
                    directlyDownloadable = true,
                    Latest.AVAILABLE,
                    freeToUseInProduction = true
                )
            }
        }.getOrElse {
            return Result.failure(it)
        }

        val pkg = packages.result.firstOrNull() ?: return Result.failure(
            IllegalArgumentException("No JDK package for distribution '$distributionName' and version $version found.")
        )

        return Result.success(pkg)
    }

    /**
     * Install a JDK matching [distributionName] and [version] below [ortToolsDirectory] and return its directory on
     * success, or an exception on failure.
     */
    fun installJdk(distributionName: String, version: String): Result<File> {
        val pkg = findJdkPackage(distributionName, version).getOrElse {
            return Result.failure(it)
        }

        val installDir = (ortToolsDirectory / "jdks" / pkg.distribution / pkg.distributionVersion)
            .apply {
                if (isDirectory) {
                    logger.info { "Not downloading the JDK again as the directory '$this' already exists." }
                    return Result.success(singleContainedDirectoryOrThis())
                }

                safeMkdirs()
            }

        val url = pkg.links.pkgDownloadRedirect
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
