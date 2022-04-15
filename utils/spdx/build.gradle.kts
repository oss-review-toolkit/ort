/*
 * Copyright (C) 2017-2020 HERE Europe B.V.
 * Copyright (C) 2020 Bosch.IO GmbH
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

import at.bxm.gradleplugins.svntools.tasks.SvnExport

import groovy.json.JsonSlurper

import java.io.FileNotFoundException
import java.net.URL
import java.time.Year

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val antlrVersion: String by project
val jacksonVersion: String by project
val spdxLicenseListVersion: String by project

plugins {
    // Apply core plugins.
    antlr
    `java-library`

    // Apply third-party plugins.
    id("at.bxm.svntools")
}

tasks.withType<AntlrTask>().configureEach {
    arguments = arguments + listOf("-visitor")
}

tasks.withType<KotlinCompile>().configureEach {
    val antlrTasks = tasks.withType<AntlrTask>()
    dependsOn(antlrTasks)
}

dependencies {
    antlr("org.antlr:antlr4:$antlrVersion")

    implementation(project(":utils:common-utils"))

    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
}

val importScanCodeLicenseTexts by tasks.registering(SvnExport::class) {
    description = "Imports license texts from the ScanCode repository."
    group = "SPDX"

    svnUrl = "https://github.com/nexB/scancode-toolkit/trunk/src/licensedcode/data/licenses"
    targetDir = "$buildDir/SvnExport/licenses/scancode-toolkit"

    outputs.dir(targetDir)
}

val importSpdxLicenseTexts by tasks.registering(SvnExport::class) {
    description = "Imports license texts from the SPDX repository."
    group = "SPDX"

    svnUrl = "https://github.com/spdx/license-list-data/tags/$spdxLicenseListVersion/text"
    targetDir = "$buildDir/SvnExport/licenses/spdx"

    inputs.property("spdxLicenseListVersion", spdxLicenseListVersion)
    outputs.dir(targetDir)
}

val importLicenseTexts by tasks.registering {
    description = "Imports license texts from all known sources."
    group = "SPDX"

    // TODO: Consider using https://github.com/maxhbr/LDBcollector as the single meta-source for license texts.
    val importTasks = tasks.matching { it.name.matches(Regex("import.+LicenseTexts")) }
    dependsOn(importTasks)
    outputs.files(importTasks.flatMap { it.outputs.files })
}

fun getLicenseHeader(fromYear: Int = 2017, toYear: Int = Year.now().value) =
    """
    |/*
    | * Copyright (C) $fromYear-$toYear HERE Europe B.V.
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

data class LicenseMetaData(
    val name: String,
    val deprecated: Boolean
)

fun licenseToEnumEntry(id: String, meta: LicenseMetaData): String {
    var enumEntry = id.toUpperCase().replace(Regex("[-.]"), "_").replace("+", "PLUS")
    if (enumEntry[0].isDigit()) {
        enumEntry = "_$enumEntry"
    }

    val fullName = meta.name.replace("\"", "\\\"")
    return if (meta.deprecated) {
        "$enumEntry(\"$id\", \"$fullName\", true)"
    } else {
        "$enumEntry(\"$id\", \"$fullName\")"
    }
}

fun generateEnumClass(
    taskName: String, description: String, jsonUrl: String, className: String, resourcePath: String,
    collectIds: (Map<String, Any>) -> Map<String, LicenseMetaData>
): Map<String, LicenseMetaData> {
    logger.quiet("Fetching $description list...")

    val jsonSlurper = JsonSlurper()
    val json = jsonSlurper.parse(URL(jsonUrl), "UTF-8") as Map<String, Any>

    val licenseListVersion = json["licenseListVersion"] as String
    logger.quiet("Found license list version '$licenseListVersion'.")

    val ids = collectIds(json)
    logger.quiet("Found ${ids.size} SPDX $description identifiers.")

    val enumFile = file("src/main/kotlin/$className.kt")
    logger.quiet("Writing enum entries to file '$enumFile'...")

    enumFile.writeText(getLicenseHeader())
    enumFile.appendText(
        """
        |@file:Suppress("MaxLineLength")
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
        | * '$taskName'.
        | */
        |@Suppress("EnumNaming")
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

    val enumValues = ids.map { (id, meta) ->
        licenseToEnumEntry(id, meta)
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
        |        const val LICENSE_LIST_VERSION = "$licenseListVersion"
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
        |            val resource = SpdxLicenseException::class.java.getResource("/exception-mapping.yml")
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
        |            values().find { id.equals(it.id, ignoreCase = true) || id.equals(it.fullName, ignoreCase = true) }
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
        |    val text by lazy { javaClass.getResource("/$resourcePath/${'$'}id").readText() }
        |}
        |
        """.trimMargin()
    )

    logger.quiet("Generated SPDX $description enum file '$enumFile'.")

    return ids
}

fun generateLicenseTextResources(description: String, ids: Map<String, LicenseMetaData>, resourcePath: String) {
    logger.quiet("Determining SPDX $description texts...")

    val scanCodeLicensePath = "$buildDir/SvnExport/licenses/scancode-toolkit"
    val spdxIdToScanCodeKey = mutableMapOf<String, String>()

    file(scanCodeLicensePath).walk().maxDepth(1).filter { it.isFile && it.extension == "yml" }.forEach { file ->
        file.readLines().forEach { line ->
            val keyAndValue = line.split(Regex("^spdx_license_key:"), 2)
            if (keyAndValue.size == 2) {
                spdxIdToScanCodeKey[keyAndValue.last().trim()] = file.name.removeSuffix(".yml")
            }
        }
    }

    val resourcesDir = file("src/main/resources/$resourcePath").apply {
        if (isDirectory && !deleteRecursively()) {
            throw GradleException("Failed to delete the existing '$this' directory.")
        }

        mkdirs()
    }

    ids.forEach { (id, meta) ->
        val resourceFile = resourcesDir.resolve(id)

        // Prefer the texts from ScanCode as these have better formatting than those from SPDX.
        val candidates = mutableListOf(
            "$scanCodeLicensePath/${spdxIdToScanCodeKey[id]}.LICENSE",
            "$buildDir/SvnExport/licenses/spdx/$id.txt"
        )

        if (meta.deprecated) {
            candidates += "$buildDir/SvnExport/licenses/spdx/deprecated_$id.txt"
        }

        val i = candidates.iterator()
        while (true) {
            if (i.hasNext()) {
                val candidate = i.next()

                @Suppress("SwallowedException")
                try {
                    val licenseFile = file(candidate)
                    val lines = licenseFile.readLines().map { it.trimEnd() }.asReversed().dropWhile { it.isEmpty() }
                        .asReversed().dropWhile { it.isEmpty() }
                    resourceFile.writeText(lines.joinToString("\n", postfix = "\n"))
                    logger.quiet("Got $description text for id '$id' from:\n\t$licenseFile.")
                } catch (e: FileNotFoundException) {
                    continue
                }

                break
            } else {
                throw GradleException("Failed to determine $description text for '$id' from any of $candidates.")
            }
        }
    }
}

val generateSpdxLicenseEnum by tasks.registering {
    description = "Generates the enum class of SPDX license ids and their associated texts as resources."
    group = "SPDX"

    dependsOn(importLicenseTexts)
    finalizedBy("cleanImportLicenseTexts")

    doLast {
        val description = "license"
        val resourcePath = "licenses"
        val ids = generateEnumClass(
            name,
            description,
            "https://raw.githubusercontent.com/spdx/license-list-data/$spdxLicenseListVersion/json/licenses.json",
            "SpdxLicense",
            resourcePath
        ) { json ->
            (json["licenses"] as List<Map<String, Any>>).associate {
                val id = it["licenseId"] as String
                id to LicenseMetaData(it["name"] as String, it["isDeprecatedLicenseId"] as Boolean)
            }
        }
        generateLicenseTextResources(description, ids, resourcePath)
    }
}

val generateSpdxLicenseExceptionEnum by tasks.registering {
    description = "Generates the enum class of SPDX license exception ids and their associated texts as resources."
    group = "SPDX"

    dependsOn(importLicenseTexts)
    finalizedBy("cleanImportLicenseTexts")

    doLast {
        val description = "license exception"
        val resourcePath = "exceptions"
        val ids = generateEnumClass(
            name,
            description,
            "https://raw.githubusercontent.com/spdx/license-list-data/$spdxLicenseListVersion/json/exceptions.json",
            "SpdxLicenseException",
            resourcePath
        ) { json ->
            (json["exceptions"] as List<Map<String, Any>>).associate {
                val id = it["licenseExceptionId"] as String
                id to LicenseMetaData(it["name"] as String, it["isDeprecatedLicenseId"] as Boolean)
            }
        }
        generateLicenseTextResources(description, ids, resourcePath)
    }
}

val generateSpdxEnums by tasks.registering {
    description = "Generates the enums for SPDX license and exception ids and their associated texts."
    group = "SPDX"

    val generateTasks = tasks.matching { it.name.matches(Regex("generateSpdx.+Enum")) }
    dependsOn(generateTasks)
    outputs.files(generateTasks.flatMap { it.outputs.files })
}
