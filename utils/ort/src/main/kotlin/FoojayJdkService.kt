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

import org.ossreviewtoolkit.clients.foojay.Architecture
import org.ossreviewtoolkit.clients.foojay.ArchiveType
import org.ossreviewtoolkit.clients.foojay.DiscoService
import org.ossreviewtoolkit.clients.foojay.Distribution
import org.ossreviewtoolkit.clients.foojay.Latest
import org.ossreviewtoolkit.clients.foojay.LibCType
import org.ossreviewtoolkit.clients.foojay.OperatingSystem
import org.ossreviewtoolkit.clients.foojay.PackageType
import org.ossreviewtoolkit.clients.foojay.ReleaseStatus
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.enumSetOf

/**
 * Foojay Disco-based JDK service implementation.
 * Wraps [DiscoService] and provides the [JdkService] interface.
 */
class FoojayJdkService(
    private val discoService: DiscoService
) : JdkService {

    companion object {
        @JvmStatic
        fun create(serverUrl: String? = null): JdkService =
            FoojayJdkService(DiscoService.create(serverUrl, OkHttpClientHelper.buildClient()))
    }

    override suspend fun findJdkPackage(distributionName: String, version: String): Result<JdkPackage> {
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

        val packages = runCatching {
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
        }.getOrElse {
            return Result.failure(it)
        }

        val pkg = packages.result.firstOrNull() ?: return Result.failure(
            IllegalArgumentException("No JDK package for distribution '$distributionName' and version $version found.")
        )

        return Result.success(
            JdkPackage(
                pkg.distribution,
                pkg.jdkVersion,
                pkg.links.pkgDownloadRedirect
            )
        )
    }
}
