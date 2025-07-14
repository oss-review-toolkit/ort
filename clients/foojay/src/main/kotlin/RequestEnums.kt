/*
 * Copyright (C) 2025 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.clients.foojay

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class Distribution {
    AOJ, AOJ_OPENJ9, BISHENG, CORRETTO, DRAGONWELL, GRAALVM_CE8, GRAALVM_CE11, GRAALVM_CE16, GRAALVM_CE17, GRAALVM_CE19,
    GRAALVM_CE20, GRAALVM_COMMUNITY, GRAALVM, JETBRAINS, KONA, LIBERICA, LIBERICA_NATIVE, MANDREL, MICROSOFT,
    OJDK_BUILD, OPENLOGIC, ORACLE, ORACLE_OPEN_JDK, REDHAT, SAP_MACHINE, SEMERU, SEMERU_CERTIFIED, TEMURIN, TRAVA, ZULU,
    ZULU_PRIME
}

enum class OperatingSystem {
    AIX, ALPINE_LINUX, LINUX, LINUX_MUSL, MACOS, QNX, SOLARIS, WINDOWS
}

@Serializable
enum class Architecture {
    AARCH32, AARCH64, AMD64, ARM, ARM32, ARM64, MIPS, PPC, PPC64EL, PPC64LE, PPC64, RISCV64, S390, S390X, SPARC,
    SPARCV9, X64, @SerialName("x86-64") X86_64, X86, I386, I486, I586, I686, @SerialName("x86-32") X86_32;

    // Align the string representation with the serial name to make Retrofit's GET request work. Also see:
    // https://github.com/JakeWharton/retrofit2-kotlinx-serialization-converter/issues/39
    override fun toString() = serializer().descriptor.getElementName(ordinal)
}

@Serializable
enum class ArchiveType {
    APK, CAB, DEB, DMG, EXE, MSI, PKG, RPM, TAR, @SerialName("tar.gz") TAR_GZ, TGZ, ZIP;

    // Align the string representation with the serial name to make Retrofit's GET request work. Also see:
    // https://github.com/JakeWharton/retrofit2-kotlinx-serialization-converter/issues/39
    override fun toString() = serializer().descriptor.getElementName(ordinal)
}

enum class PackageType {
    JDK, JRE
}

enum class LibCType {
    GLIBC, LIBC, MUSL, C_STD_LIB
}

@Serializable
enum class ReleaseStatus {
    @SerialName("ea") EARLY_ACCESS, @SerialName("ga") GENERAL_AVAILABILITY;

    // Align the string representation with the serial name to make Retrofit's GET request work. Also see:
    // https://github.com/JakeWharton/retrofit2-kotlinx-serialization-converter/issues/39
    override fun toString() = serializer().descriptor.getElementName(ordinal)
}

enum class Latest {
    OVERALL, PER_DISTRO, PER_VERSION, AVAILABLE, ALL_OF_VERSION
}
