/*
 * Copyright (C) 2017 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

import groovy.json.JsonSlurper

import java.io.File
import java.net.Authenticator
import java.net.PasswordAuthentication
import java.net.URI

plugins {
    // Apply precompiled plugins.
    id("ort-library-conventions")

    // Apply third-party plugins.
    alias(libs.plugins.kotlinSerialization)
}

dependencies {
    api(libs.kotlinx.serialization.core)

    implementation(projects.utils.commonUtils)
    implementation(libs.kotlinx.serialization.yaml)

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

// Official SPDX license list.
val spdxLicenseListUrl = "https://spdx.org/licenses"

// The SPDX license list version, fetched lazily so it is only queried when a generation task runs.
val spdxLicenseListVersion: String by lazy {
    val licensesUrl = "$spdxLicenseListUrl/licenses.json"

    logger.quiet("Determining the SPDX license list version from '$licensesUrl'...")

    val jsonSlurper = JsonSlurper()

    @Suppress("UNCHECKED_CAST")
    val json = jsonSlurper.parse(URI(licensesUrl).toURL(), "UTF-8") as Map<String, Any>

    val version = json["licenseListVersion"] as String
    logger.quiet("The SPDX license list version is '$version'.")

    version
}

data class LicenseInfo(
    val id: String,
    val name: String,
    val isDeprecated: Boolean,
    val isException: Boolean,
    val detailsUrl: String
)

/**
 * Download each text in [info] from its [LicenseInfo.detailsUrl] (the [textKey] field, "licenseText" or
 * "licenseExceptionText") and write it to [resourceDir] named after the SPDX id, replacing any previously generated
 * files.
 */
fun importLicenseTexts(resourceDir: File, info: List<LicenseInfo>, textKey: String) {
    resourceDir.mkdirs()
    resourceDir.walk().maxDepth(1).filter { it.isFile }.forEach { it.delete() }

    val jsonSlurper = JsonSlurper()

    info.forEach {
        @Suppress("UNCHECKED_CAST")
        val details = jsonSlurper.parse(URI(it.detailsUrl).toURL(), "UTF-8") as Map<String, Any>
        val text = details[textKey] as String
        resourceDir.resolve(it.id).writeText(text)
    }

    logger.quiet("Imported ${info.size} SPDX text files into '$resourceDir'.")
}

val licensesResourcePath = "licenses"
val exceptionsResourcePath = "exceptions"

fun getLicenseHeader(year: Int = 2017) =
    """
    |/*
    | * Copyright (C) $year The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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
    listUrl: String,
    description: String,
    listKeyName: String,
    idKeyName: String,
    isException: Boolean
): List<LicenseInfo> {
    logger.quiet("Downloading SPDX $description list from '$listUrl'...")

    val jsonSlurper = JsonSlurper()

    @Suppress("UNCHECKED_CAST")
    val json = jsonSlurper.parse(URI(listUrl).toURL(), "UTF-8") as Map<String, Any>

    val licenseListVersion = json["licenseListVersion"] as String
    logger.quiet("Found SPDX $description list version $licenseListVersion.")

    @Suppress("UNCHECKED_CAST")
    return (json[listKeyName] as List<Map<String, Any>>).map {
        val id = it[idKeyName] as String
        LicenseInfo(
            id,
            it["name"] as String,
            it["isDeprecatedLicenseId"] as Boolean,
            isException = isException,
            detailsUrl = it["detailsUrl"] as String
        )
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
        """.trimMargin()
    )

    if (description == "license exception") {
        enumFile.appendText(
            """
            |
            |import com.charleskorn.kaml.Yaml
            |import com.charleskorn.kaml.YamlInput
            |import com.charleskorn.kaml.YamlScalar
            |import com.charleskorn.kaml.decodeFromStream
            |
            """.trimMargin()
        )
    } else {
        enumFile.appendText(
            """
            |
            |import com.charleskorn.kaml.YamlInput
            |import com.charleskorn.kaml.YamlScalar
            |
            """.trimMargin()
        )
    }

    enumFile.appendText(
        """
        |
        |import kotlinx.serialization.KSerializer
        |import kotlinx.serialization.KeepGeneratedSerializer
        |import kotlinx.serialization.Serializable
        |import kotlinx.serialization.encoding.Decoder
        |
        |/**
        | * An enum containing all SPDX $description IDs. This class is generated by the Gradle task
        | * '$name'.
        | */
        |@KeepGeneratedSerializer
        |@Serializable(${className}Serializer::class)
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
        |        val association: Map<String, List<SpdxLicense>> by lazy {
        |            val resource = checkNotNull(SpdxLicenseException::class.java.getResource("/exception-association.yml"))
        |            resource.openStream().use { Yaml.default.decodeFromStream(it) }
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
        |internal object ${className}Serializer : KSerializer<$className> by $className.generatedSerializer() {
        |    override fun deserialize(decoder: Decoder): $className {
        |        require(decoder is YamlInput) {
        |            "Only YAML input is supported."
        |        }
        |
        |        val node = requireNotNull(decoder.node as? YamlScalar) {
        |            "Only scalar input is supported."
        |        }
        |
        |        return checkNotNull($className.forId(node.content)) {
        |            "No SPDX license found for ID '${'$'}{node.content}'."
        |        }
        |    }
        |}
        |
        """.trimMargin()
    )

    logger.quiet("Generated SPDX $description enum file '$enumFile'.")

    return info
}

/**
 * Wrap [line] at whitespace boundaries so no resulting line exceeds [maxLineLength] characters. A single word longer than
 * the limit is kept intact, and text without whitespace (e.g. Chinese or Japanese script) is left untouched.
 */
fun wrapLine(line: String, maxLineLength: Int): List<String> {
    if (line.length <= maxLineLength) return listOf(line)

    val wrapped = mutableListOf<String>()
    var remainder = line

    while (remainder.length > maxLineLength) {
        val breakIndex = (maxLineLength downTo 1).firstOrNull { remainder[it].isWhitespace() }
            ?: (maxLineLength + 1..remainder.lastIndex).firstOrNull { remainder[it].isWhitespace() }
            ?: break

        wrapped += remainder.substring(0, breakIndex)
        remainder = remainder.substring(breakIndex + 1)
    }

    wrapped += remainder
    return wrapped
}

val fixupLicenseTextResources = tasks.register("fixupLicenseTextResources") {
    doLast {
        val resourcePaths = listOf(licensesResourcePath, exceptionsResourcePath).map {
            file("src/main/resources/$it")
        }

        resourcePaths.forEach { path ->
            path.walk().maxDepth(1).filter { it.isFile }.forEach { file ->
                val lines = file.readLines()
                    // Trim trailing whitespace and blank lines.
                    .map { it.trimEnd() }
                    .dropWhile { it.isEmpty() }.dropLastWhile { it.isEmpty() }
                    // Wrap sentences longer than 120 characters.
                    .flatMap { wrapLine(it, maxLineLength = 120) }
                file.writeText(lines.joinToString("\n", postfix = "\n"))
            }
        }
    }
}

val generateSpdxLicenseEnum = tasks.register("generateSpdxLicenseEnum") {
    description = "Generates the enum class of SPDX license ids and their associated texts as resources."
    group = "SPDX"

    doLast {
        val description = "license"
        val licenseInfo = getLicenseInfo(
            "$spdxLicenseListUrl/licenses.json",
            description,
            "licenses",
            "licenseId",
            isException = false
        )

        importLicenseTexts(file("src/main/resources/$licensesResourcePath"), licenseInfo, textKey = "licenseText")

        generateEnumClass(
            "SpdxLicense",
            description,
            licenseInfo,
            licensesResourcePath
        )
    }

    finalizedBy(fixupLicenseTextResources)
}

val generateSpdxLicenseExceptionEnum = tasks.register("generateSpdxLicenseExceptionEnum") {
    description = "Generates the enum class of SPDX license exception ids and their associated texts as resources."
    group = "SPDX"

    doLast {
        val description = "license exception"
        val licenseInfo = getLicenseInfo(
            "$spdxLicenseListUrl/exceptions.json",
            description,
            "exceptions",
            "licenseExceptionId",
            isException = true
        )

        importLicenseTexts(
            file("src/main/resources/$exceptionsResourcePath"),
            licenseInfo,
            textKey = "licenseExceptionText"
        )

        generateEnumClass(
            "SpdxLicenseException",
            description,
            licenseInfo,
            exceptionsResourcePath
        )
    }

    finalizedBy(fixupLicenseTextResources)
}

val generateSpdxEnums = tasks.register("generateSpdxEnums") {
    description = "Generates the enums for SPDX license and exception ids and their associated texts."
    group = "SPDX"

    val generateTasks = tasks.matching { it.name.matches(Regex("generateSpdx.+Enum")) }
    dependsOn(generateTasks)
    outputs.files(generateTasks.flatMap { it.outputs.files })
}
