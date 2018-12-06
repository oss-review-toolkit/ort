/*
 * Copyright (C) 2017-2018 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

package com.here.ort.commands

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.here.ort.CommandWithHelp
import com.here.ort.model.*
import com.here.ort.utils.PARAMETER_ORDER_MANDATORY
import com.here.ort.utils.PARAMETER_ORDER_OPTIONAL
import com.here.ort.utils.expandTilde
import java.io.File

@Parameters(commandNames = ["list-licenses"], commandDescription = "List licenses.")
object ListLicensesCommand : CommandWithHelp() {
    @Parameter(description = "The input ort result file.",
        names = ["-i"],
        required = true,
        order = PARAMETER_ORDER_MANDATORY
    )
    private lateinit var inputFile: File

    @Parameter(description = "Outputfile.",
        names = ["--output-file", "-o"],
        required = true,
        order = PARAMETER_ORDER_MANDATORY
    )
    private lateinit var outputFile: File

    @Parameter(
        description = "A file containing the repository configuration. If set the .ort.yml " +
                "overrides the repository configuration contained in the ort result from the input file.",
        names = ["--repository-configuration-file"],
        order = PARAMETER_ORDER_OPTIONAL
    )
    private var repositoryConfigurationFile: File? = null

    @Parameter(description = "Directory containing what was scanned.",
        names = ["--scan-dir", "-s"],
        required = false,
        order = PARAMETER_ORDER_OPTIONAL
    )
    private var scanDir: File? = null

    fun OrtResult.getPackages() : Set<com.here.ort.model.Package> {
        val projectPackages = analyzer!!.result.projects.map { it.toPackage() }
        val dependencyPackages = analyzer!!.result.packages.map { it.pkg }
        return projectPackages.union(dependencyPackages).toSet()
    }

    override fun runCommand(jc: JCommander): Int {
        var ortResult = inputFile.readValue<OrtResult>()
        repositoryConfigurationFile?.expandTilde()?.let {
            ortResult = ortResult.replaceConfig(it.readValue())
        }

        fun isExcluded(path: String): Boolean {
            val excludes = ortResult.repository.config.excludes!!.findPathExcludes(path)
            return excludes.isNotEmpty()
        }

        val provenanceFindings = mutableMapOf<Provenance, MutableMap<String, MutableSet<TextLocation>>>()
        ortResult.scanner!!.results.scanResults.forEach { container ->
            container.results.forEach { scanResult ->
                val licenses = provenanceFindings.getOrPut(scanResult.provenance, { mutableMapOf() } )
                scanResult.summary.licenseFindings.forEach { licenseFinding ->
                    val files = licenses.getOrPut(licenseFinding.license, { mutableSetOf<TextLocation>() } )
                    files.addAll(licenseFinding.locations.map { it } )
                }
            }
        }

        val packageFindings = mutableMapOf<Identifier, MutableMap<String, MutableSet<TextLocation>>>()
        ortResult.getPackages().forEach { pkg ->
            provenanceFindings.keys.forEach { provenance ->
                if (provenance.matches(pkg)) {
                    packageFindings[pkg.id] = provenanceFindings[provenance]!!
                }
            }
        }

        val result = buildString {
            packageFindings.toSortedMap( compareBy { it.toCoordinates() } ).keys.forEach { id ->
                appendln("################################################################################")
                appendln("#")
                appendln("# ${id.toCoordinates()}")
                appendln("# ")
                appendln("################################################################################")
                appendln("")
                val licenses = packageFindings[id]!!
                licenses.keys.sorted().forEach { license ->
                    val distinctFindings = getDistinctFindings(licenses[license]!!)
                    appendln("  $license:")
                    if (distinctFindings != null) {
                        distinctFindings.keys.forEachIndexed { groupIndex, text ->
                            distinctFindings[text]!!.forEach { finding ->
                                val group = if (text == "N/A") "E" else groupIndex.toString()
                                if (!isExcluded(finding.path)) {
                                    appendln("    [$group] ${finding.path}: ${finding.startLine}-${finding.endLine}")
                                } else {
                                    appendln("    [$group] (e) ${finding.path}: ${finding.startLine}-${finding.endLine} ")
                                }
                            }
                        }
                        distinctFindings.keys.forEachIndexed { groupIndex, text ->
                            if (text != "N/A") {
                                appendln("    group [$groupIndex]: ")
                                appendln()
                                appendln(text.lines().joinToString( prefix = "    ", separator = "\n    "))
                                appendln()
                            }
                        }
                    }
                    else {
                        licenses[license]!!.forEach { finding ->
                            if (!isExcluded(finding.path)) {
                                appendln("    ${finding.path}: ${finding.startLine}-${finding.endLine}")
                            } else {
                                appendln("    (e) ${finding.path}: ${finding.startLine}-${finding.endLine}")
                            }
                        }
                    }
                    appendln()
                }
            }
        }

        outputFile.writeText(result)
        return 0
    }

    private fun getDistinctFindings(textLocations: Set<TextLocation>): Map<String, Set<TextLocation>>? {
        val result = mutableMapOf<String, MutableSet<TextLocation>>()
        textLocations.forEach { textLocation ->
            val text = try { getText(textLocation) } catch (e: Throwable) { "N/A" }
            val locations = result.getOrPut(text, { mutableSetOf() })
            locations.add(textLocation)
        }
        return result
    }

    private fun getText(textLocation: TextLocation): String {
        val file = scanDir!!.resolve(textLocation.path)
        val text = file.readText()
        val matchedText = text.lines().subList(textLocation.startLine - 1, textLocation.endLine).joinToString( separator = "\n" )
        return matchedText
    }
}
