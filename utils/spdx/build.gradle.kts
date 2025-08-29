/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import de.undercouch.gradle.tasks.download.Download

import groovy.json.JsonSlurper

import java.net.Authenticator
import java.net.PasswordAuthentication
import java.net.URI

val spdxLicenseListVersion: String by project

plugins {
    // Apply precompiled plugins.
    id("ort-library-conventions")

    // Apply third-party plugins.
    alias(libs.plugins.download)
}

dependencies {
    api(jacksonLibs.jacksonDatabind)

    implementation(projects.utils.commonUtils)

    implementation(jacksonLibs.jacksonDataformatYaml)
    implementation(jacksonLibs.jacksonModuleKotlin)

    testImplementation(libs.kotest.assertions.json)
    testImplementation(libs.kotest.framework.engine)
    testImplementation(projects.model)
}

if (Authenticator.getDefault() == null) {
    Authenticator.setDefault(object : Authenticator() {
        override fun getPasswordAuthentication(): PasswordAuthentication {
            if (requestorType != RequestorType.PROXY) return super.getPasswordAuthentication()

            val proxyUser = System.getProperty("$requestingProtocol.proxyUser")
            val proxyPassword = System.getProperty("$requestingProtocol.proxyPassword")
            if (proxyUser == null || proxyPassword == null) return super.getPasswordAuthentication()

            return PasswordAuthentication(proxyUser, proxyPassword.toCharArray())
        }
    })
}

data class LicenseInfo(
    val id: String,
    val name: String,
    val isDeprecated: Boolean,
    val isException: Boolean
)

fun interface SpdxLicenseTextProvider {
    fun getLicenseUrl(info: LicenseInfo): URI?
}

class ScanCodeLicenseTextProvider : SpdxLicenseTextProvider {
    private val url = "https://scancode-licensedb.aboutcode.org"

    private val spdxIdToScanCodeKeyMap: Map<String, String> by lazy {
        val jsonSlurper = JsonSlurper()
        val url = URI("https://scancode-licensedb.aboutcode.org/index.json").toURL()

        logger.quiet("Downloading ScanCode license index...")

        @Suppress("UNCHECKED_CAST")
        val json = jsonSlurper.parse(url, "UTF-8") as List<Map<String, Any?>>

        logger.quiet("Found ${json.size} ScanCode license entries.")

        json.mapNotNull { map ->
            (map["spdx_license_key"] as? String)?.let { it to (map["license_key"] as String) }
        }.toMap()
    }

    override fun getLicenseUrl(info: LicenseInfo): URI? {
        val key = spdxIdToScanCodeKeyMap[info.id] ?: return null
        return URI("$url/$key.LICENSE")
    }
}

class SpdxLicenseListDataProvider : SpdxLicenseTextProvider {
    private val url = "https://raw.githubusercontent.com/spdx/license-list-data/v$spdxLicenseListVersion"

    override fun getLicenseUrl(info: LicenseInfo): URI {
        val prefix = "deprecated_".takeIf { info.isDeprecated && !info.isException }.orEmpty()
        return URI("$url/text/$prefix${info.id}.txt")
    }
}

// Prefer the texts from ScanCode as these have better formatting than those from SPDX.
val providers = sequenceOf(ScanCodeLicenseTextProvider(), SpdxLicenseListDataProvider())

val licensesResourcePath = "licenses"
val exceptionsResourcePath = "exceptions"

fun getLicenseHeader(year: Int = 2017) =
    """
    |/*
    | * Copyright (C) $year The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
    | *
    | * Licensed under the Apache License, Version 2.0 (the "License");
    | * you may not use this file except in compliance with the License.
    | * You may obtain a copy of the License at
    | *
    | *     https://www.apache.org/licenses/LICENSE-2.0
    | *
    | * Unless required by applicable law or agreed to in writing, software
    | * distributed under the License is distributed on an "AS IS" BASIS,
    | * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    | * See the License for the specific language governing permissions and
    | * limitations under the License.
    | *
    | * SPDX-License-Identifier: Apache-2.0
    | * License-Filename: LICENSE
    | */
    |
    |
    """.trimMargin()

fun licenseToEnumEntry(info: LicenseInfo): String {
    var enumEntry = info.id.uppercase().replace(Regex("[-.]"), "_").replace("+", "PLUS")
    if (enumEntry.first().isDigit()) {
        enumEntry = "_$enumEntry"
    }

    val fullName = info.name.replace("\"", "\\\"")
    return if (info.isDeprecated) {
        "$enumEntry(\"${info.id}\", \"$fullName\", true)"
    } else {
        "$enumEntry(\"${info.id}\", \"$fullName\")"
    }
}

fun getLicenseInfo(
    jsonUrl: String,
    description: String,
    listKeyName: String,
    idKeyName: String,
    isException: Boolean
): List<LicenseInfo> {
    logger.quiet("Downloading SPDX $description list...")

    val jsonSlurper = JsonSlurper()

    @Suppress("UNCHECKED_CAST")
    val json = jsonSlurper.parse(URI(jsonUrl).toURL(), "UTF-8") as Map<String, Any>

    val licenseListVersion = json["licenseListVersion"] as String
    logger.quiet("Found SPDX $description list version $licenseListVersion.")

    @Suppress("UNCHECKED_CAST")
    return (json[listKeyName] as List<Map<String, Any>>).map {
        val id = it[idKeyName] as String
        LicenseInfo(id, it["name"] as String, it["isDeprecatedLicenseId"] as Boolean, isException = isException)
    }
}

fun Task.generateEnumClass(
    className: String,
    description: String,
    info: List<LicenseInfo>,
    resourcePath: String
): List<LicenseInfo> {
    logger.quiet("Collected ${info.size} SPDX $description identifiers.")

    val enumFile = file("src/main/kotlin/$className.kt")

    enumFile.writeText(getLicenseHeader())
    enumFile.appendText(
        """
        |@file:Suppress("EnumEntryNameCase", "MaxLineLength")
        |
        |package org.ossreviewtoolkit.utils.spdx
        |
        |import com.fasterxml.jackson.annotation.JsonCreator
        |
        """.trimMargin()
    )

    if (description == "license exception") {
        enumFile.appendText(
            """
            |import com.fasterxml.jackson.module.kotlin.readValue
            |
            """.trimMargin()
        )
    }

    enumFile.appendText(
        """
        |
        |/**
        | * An enum containing all SPDX $description IDs. This class is generated by the Gradle task
        | * '$name'.
        | */
        |@Suppress("EnumEntryName", "EnumNaming")
        |enum class $className(
        |    /**
        |     * The SPDX id of the $description.
        |     */
        |    val id: String,
        |
        |    /**
        |     * The human-readable name of the $description.
        |     */
        |    val fullName: String,
        |
        |    /**
        |     * Whether the [id] is deprecated or not.
        |     */
        |    val deprecated: Boolean = false
        |) {
        |
        """.trimMargin()
    )

    val enumValues = info.map {
        licenseToEnumEntry(it)
    }.sorted().joinToString(",\n") {
        "    $it"
    } + ";"

    enumFile.appendText(enumValues)
    enumFile.appendText(
        """
        |
        |
        |    companion object {
        """.trimMargin()
    )

    if (description == "license") {
        enumFile.appendText(
            """
        |
        |        /**
        |         * The version of the license list.
        |         */
        |        const val LICENSE_LIST_VERSION = "$spdxLicenseListVersion"
        |
            """.trimMargin()
        )
    }

    if (description == "license exception") {
        enumFile.appendText(
            """
        |
        |        /**
        |         * The map which associates SPDX exceptions with their applicable SPDX licenses.
        |         */
        |        val mapping by lazy {
        |            val resource = checkNotNull(SpdxLicenseException::class.java.getResource("/exception-mapping.yml"))
        |            yamlMapper.readValue<Map<String, List<SpdxLicense>>>(resource)
        |        }
        |
            """.trimMargin()
        )
    }

    enumFile.appendText(
        """
        |
        |        /**
        |         * Return the enum value for the given [id], or null if it is no SPDX $description id.
        |         */
        |        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        |        @JvmStatic
        |        fun forId(id: String) =
        |            entries.find { id.equals(it.id, ignoreCase = true) || id.equals(it.fullName, ignoreCase = true) }
        |    }
        |
        |
        """.trimMargin()
    )

    enumFile.appendText(
        """
        |    /**
        |     * The full $description text as a string.
        |     */
        |    val text by lazy { checkNotNull(javaClass.getResource("/$resourcePath/${'$'}id")).readText() }
        |}
        |
        """.trimMargin()
    )

    logger.quiet("Generated SPDX $description enum file '$enumFile'.")

    return info
}

val fixupLicenseTextResources by tasks.registering {
    doLast {
        val resourcePaths = listOf(licensesResourcePath, exceptionsResourcePath).map {
            file("src/main/resources/$it")
        }

        resourcePaths.forEach { path ->
            path.walk().maxDepth(1).filter { it.isFile }.forEach { file ->
                // Trim trailing whitespace and blank lines.
                val lines = file.readLines().map { it.trimEnd() }
                    .dropWhile { it.isEmpty() }.dropLastWhile { it.isEmpty() }
                file.writeText(lines.joinToString("\n", postfix = "\n"))
            }
        }
    }
}

val generateSpdxLicenseEnum by tasks.registering(Download::class) {
    description = "Generates the enum class of SPDX license ids and their associated texts as resources."
    group = "SPDX"

    val description = "license"
    val licenseInfo = getLicenseInfo(
        "https://raw.githubusercontent.com/spdx/license-list-data/v$spdxLicenseListVersion/json/licenses.json",
        description,
        "licenses",
        "licenseId",
        isException = false
    )

    val licenseUrlMap = licenseInfo.associate { info ->
        providers.mapNotNull { it.getLicenseUrl(info) }.first() to info.id
    }

    src(licenseUrlMap.keys.sortedBy { it.toString().lowercase() })
    dest("src/main/resources/$licensesResourcePath")
    eachFile { name = licenseUrlMap[sourceURL.toURI()] }

    doLast {
        generateEnumClass(
            "SpdxLicense",
            description,
            licenseInfo,
            licensesResourcePath
        )
    }

    finalizedBy(fixupLicenseTextResources)
}

val generateSpdxLicenseExceptionEnum by tasks.registering(Download::class) {
    description = "Generates the enum class of SPDX license exception ids and their associated texts as resources."
    group = "SPDX"

    val description = "license exception"
    val licenseInfo = getLicenseInfo(
        "https://raw.githubusercontent.com/spdx/license-list-data/v$spdxLicenseListVersion/json/exceptions.json",
        description,
        "exceptions",
        "licenseExceptionId",
        isException = true
    )

    val licenseExceptionUrlMap = licenseInfo.associate { info ->
        providers.mapNotNull { it.getLicenseUrl(info) }.first() to info.id
    }

    src(licenseExceptionUrlMap.keys.sortedBy { it.toString().lowercase() })
    dest("src/main/resources/$exceptionsResourcePath")
    eachFile { name = licenseExceptionUrlMap[sourceURL.toURI()] }

    doLast {
        generateEnumClass(
            "SpdxLicenseException",
            description,
            licenseInfo,
            exceptionsResourcePath
        )
    }

    finalizedBy(fixupLicenseTextResources)
}

val generateSpdxEnums by tasks.registering {
    description = "Generates the enums for SPDX license and exception ids and their associated texts."
    group = "SPDX"

    val generateTasks = tasks.matching { it.name.matches(Regex("generateSpdx.+Enum")) }
    dependsOn(generateTasks)
    outputs.files(generateTasks.flatMap { it.outputs.files })
}
