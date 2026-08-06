/*
 * Copyright (C) 2024 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

import kotlinx.coroutines.runBlocking

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.div
import org.ossreviewtoolkit.utils.common.safeMkdirs
import org.ossreviewtoolkit.utils.common.unpack

import org.semver4j.Semver

/**
 * JDK bootstrapper that manages downloading and installing JDKs.
 *
 * @param jdkService The [JdkService] used to discover JDK packages.
 */
class JavaBootstrapper(
    private val jdkService: JdkService
) {
    companion object {
        /**
         * Return whether ORT is running on a JDK (not JRE) of the specified [version].
         */
        @JvmStatic
        fun isRunningOnJdk(version: String): Boolean {
            val requestedVersion = Semver.coerce(version)
            val runningVersion = Semver.coerce(Environment.JAVA_VERSION)
            if (requestedVersion != runningVersion) return false

            val javaHome = System.getProperty("java.home") ?: return false
            val javac = File(javaHome) / "bin" / "javac"
            return Os.resolveExecutable(javac) != null
        }
    }

    /**
     * Return the single top-level directory contained in this directory, if any, or return this directory otherwise.
     */
    private fun File.singleContainedDirectoryOrThis(): File {
        val dir = walk().maxDepth(1).singleOrNull { it != this && it.isDirectory } ?: this
        return if (Os.isMac) dir / "Contents" / "Home" else dir
    }

    /**
     * Find a JDK package matching [distributionName] and [version]. Return it on success, or an exception on failure.
     */
    internal fun findJdkPackage(distributionName: String, version: String): Result<JdkPackage> {
        logger.info { "Setting up JDK '$distributionName' in version $version..." }

        return runBlocking {
            jdkService.findJdkPackage(distributionName, version)
        }
    }

    /**
     * Install a JDK matching [distributionName] and [version] below [ortToolsDirectory] and return its directory on
     * success, or an exception on failure.
     */
    fun installJdk(distributionName: String, version: String): Result<File> {
        val pkg = findJdkPackage(distributionName, version).getOrElse {
            return Result.failure(it)
        }

        return downloadJdk(
            pkg.downloadUrl,
            ortToolsDirectory / "jdks" / pkg.downloadUrl
        )
    }

    /**
     * Download a JDK from [url] and unpack it to [installDir]. Return a result with the actual installation directory.
     */
    fun downloadJdk(url: String, installDir: File): Result<File> {
        with(installDir) {
            if (isDirectory) {
                logger.info { "Not downloading the JDK again as the directory '$this' already exists." }
                return Result.success(singleContainedDirectoryOrThis())
            }

            safeMkdirs()
        }

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
