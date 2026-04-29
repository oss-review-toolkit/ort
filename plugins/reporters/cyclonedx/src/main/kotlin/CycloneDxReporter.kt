/*
 * Copyright (C) 2019 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.reporters.cyclonedx

import java.io.File

import org.cyclonedx.Format
import org.cyclonedx.Version
import org.cyclonedx.model.Component

import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.OrtPluginEnumEntry
import org.ossreviewtoolkit.plugins.api.OrtPluginOption
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterFactory
import org.ossreviewtoolkit.reporter.ReporterInput

internal const val DEFAULT_SCHEMA_VERSION_NAME = "1.6" // Version.VERSION_16.versionString
internal val DEFAULT_SCHEMA_VERSION = Version.entries.single { it.versionString == DEFAULT_SCHEMA_VERSION_NAME }

@Suppress("EnumEntryNameCase", "EnumNaming")
enum class SchemaVersion(val version: Version) {
    @OrtPluginEnumEntry(alternativeName = "1.0")
    VERSION_10(Version.VERSION_10),

    @OrtPluginEnumEntry(alternativeName = "1.1")
    VERSION_11(Version.VERSION_11),

    @OrtPluginEnumEntry(alternativeName = "1.2")
    VERSION_12(Version.VERSION_12),

    @OrtPluginEnumEntry(alternativeName = "1.3")
    VERSION_13(Version.VERSION_13),

    @OrtPluginEnumEntry(alternativeName = "1.4")
    VERSION_14(Version.VERSION_14),

    @OrtPluginEnumEntry(alternativeName = "1.5")
    VERSION_15(Version.VERSION_15),

    @OrtPluginEnumEntry(alternativeName = "1.6")
    VERSION_16(Version.VERSION_16)
}

data class CycloneDxReporterConfig(
    /**
     * The CycloneDX schema version to use. Defaults to "1.6".
     */
    @OrtPluginOption(
        defaultValue = DEFAULT_SCHEMA_VERSION_NAME,
        aliases = ["schema.version"]
    )
    val schemaVersion: SchemaVersion,

    /**
     * The license for the data contained in the report. Defaults to "CC0-1.0".
     */
    @OrtPluginOption(
        defaultValue = "CC0-1.0",
        aliases = ["data.license"]
    )
    val dataLicense: String,

    /**
     * If true (the default), a single SBOM for all projects is created; if set to false, separate SBOMs are created for
     * each project.
     */
    @OrtPluginOption(
        defaultValue = "true",
        aliases = ["single.bom"]
    )
    val singleBom: Boolean,

    /**
     * Allows overriding the component name in the metadata of the generated report in [singleBom] mode. Per default,
     * the name is derived from a single top-level project (if any) or falls back to the VCS URL. Using this property,
     * an arbitrary name can be set.
     */
    @OrtPluginOption(defaultValue = "")
    val singleBomComponentName: String,

    /**
     * Allows specifying the component type in the metadata of the generated report in [singleBom] mode.
     */
    @OrtPluginOption(defaultValue = "APPLICATION")
    val singleBomComponentType: Component.Type,

    /**
     * A comma-separated list of (case-insensitive) output formats to export to. Supported are XML and JSON.
     */
    @OrtPluginOption(
        defaultValue = "JSON",
        aliases = ["output.file.formats"]
    )
    val outputFileFormats: List<Format>
)

/**
 * A [Reporter] that creates software bills of materials (SBOM) in the [CycloneDX](https://cyclonedx.org) format. For
 * each [Project] contained in the ORT result a separate SBOM is created.
 */
@OrtPlugin(
    id = "CycloneDX",
    displayName = "CycloneDX SBOM",
    description = "Creates software bills of materials (SBOM) in the CycloneDX format.",
    factory = ReporterFactory::class
)
class CycloneDxReporter(
    override val descriptor: PluginDescriptor = CycloneDxReporterFactory.descriptor,
    private val config: CycloneDxReporterConfig
) : Reporter {
    companion object {
        const val REPORT_BASE_FILENAME = "bom.cyclonedx"
    }

    override fun generateReport(input: ReporterInput, outputDir: File): List<Result<File>> {
        val outputFormats = config.outputFileFormats.toSet()

        require(outputFormats.isNotEmpty()) {
            "No valid CycloneDX output formats specified."
        }

        val modelMapper = CycloneDxModelMapper(input, config)
        val reportFileResults = mutableListOf<Result<File>>()
        val projects = input.ortResult.getProjects(omitExcluded = true).sortedBy { it.id }

        if (config.singleBom) {
            val bom = modelMapper.createSingleBom(projects)

            reportFileResults += bom.writeFormats(
                config.schemaVersion.version,
                outputDir,
                REPORT_BASE_FILENAME,
                outputFormats
            )
        } else {
            projects.forEach { project ->
                val reportName = "$REPORT_BASE_FILENAME-${project.id.toPath("-")}"
                val bom = modelMapper.createProjectBom(project)

                reportFileResults += bom.writeFormats(
                    config.schemaVersion.version,
                    outputDir,
                    reportName,
                    outputFormats
                )
            }
        }

        return reportFileResults
    }
}
