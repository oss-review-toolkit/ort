/*
 * Copyright (C) 2020 Bosch.IO GmbH
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

package org.ossreviewtoolkit.scanner.scanners

import com.fasterxml.jackson.databind.JsonNode

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.model.CopyrightResultOption
import org.ossreviewtoolkit.model.EmailResultOption
import org.ossreviewtoolkit.model.IgnoreFilterOption
import org.ossreviewtoolkit.model.IncludeFilterOption
import org.ossreviewtoolkit.model.LicenseResultOption
import org.ossreviewtoolkit.model.MetadataResultOption
import org.ossreviewtoolkit.model.OutputFormatOption
import org.ossreviewtoolkit.model.PackageResultOption
import org.ossreviewtoolkit.model.ScannerOptionWithSubOptions
import org.ossreviewtoolkit.model.ScannerOptions
import org.ossreviewtoolkit.model.SubOptionType
import org.ossreviewtoolkit.model.SubOptions
import org.ossreviewtoolkit.model.TimeoutOption
import org.ossreviewtoolkit.model.UnclassifiedOption
import org.ossreviewtoolkit.model.UrlResultOption
import org.ossreviewtoolkit.model.jsonMapper

class ScanCodeOptionsParserTest : WordSpec({
    "parseScanCodeOptionsFromCommandLine()" should {
        "generate a correct ScannerOptions object" {
            val commandLineOptions = listOf(
                "--copyright", "--consolidate", "--license", "--license-score", "33", "--license-text",
                "--license-url-template", "file:/some/template.ftl", "--license-text-diagnostics",
                "--is-license-text", "--package", "--email", "--max-email", "47", "--url", "--max-url", "11",
                "--info", "--mark-source", "--generated", "--shallow", "--verbose", "--quiet",
                "--processes", "3", "--timeout", "300", "--reindex-licenses", "--max-in-memory", "1024",
                "--json", "output.json", "--strip-root", "--ignore-author", "author1", "--ignore-author", "author2",
                "--ignore-copyright-holder", "crh", "--only-findings", "--ignore", "*/target",
                "--ignore", ".idea", "--include", "/docs", "--classify", "--facet", "fc",
                "--filter-clues", "--license-clarity-score", "--summary"
            )
            val copyrightOption = CopyrightResultOption(
                SubOptions.create { putStringOption(key = "consolidate", value = "true") }
            )
            val licenseOption = LicenseResultOption(
                SubOptions.create {
                    putStringOption(key = "license-text", value = "true")
                    putStringOption(key = "license-url-template", value = "file:/some/template.ftl")
                    putStringOption(key = "license-text-diagnostics", value = "true")
                    putStringOption(key = "is-license-text", value = "true")
                    putThresholdOption(key = "license-score", value = 33.0)
                }
            )
            val packageOption = PackageResultOption(
                SubOptions.create { putStringOption(key = "consolidate", value = "true") }
            )
            val emailOption = EmailResultOption(
                SubOptions.create { putThresholdOption(key = "max-email", value = 47.0) }
            )
            val urlOption = UrlResultOption(
                SubOptions.create { putThresholdOption(key = "max-url", value = 11.0) }
            )
            val metadataOption = MetadataResultOption(
                SubOptions.create { putStringOption(key = "mark-source", value = "true") }
            )
            val ignoreOption = IgnoreFilterOption(
                setOf(
                    "author:author1",
                    "author:author2",
                    "copyright:crh",
                    "path:*/target",
                    "path:.idea"
                )
            )
            val includeOption = IncludeFilterOption(setOf("/docs"))
            val timeoutOption = TimeoutOption(
                SubOptions.create { putThresholdOption(300.0) }
            )
            val outputOption = OutputFormatOption(
                SubOptions.create { putStringOption("JSON") }
            )
            val unclassifiedOption = UnclassifiedOption(
                SubOptions.create {
                    putStringOption(key = "generated", value = "true")
                    putStringOption(key = "shallow", value = "true")
                    putStringOption(key = "strip-root", value = "true")
                    putStringOption(key = "only-findings", value = "true")
                    putStringOption(key = "classify", value = "true")
                    putStringOption(key = "summary", value = "true")
                    putStringOption(key = "filter-clues", value = "true")
                    putStringOption(key = "license-clarity-score", value = "true")
                    putStringOption(key = "facet", value = "fc")
                    putStringOption(key = "verbose", value = "true", relevant = false)
                    putStringOption(key = "quiet", value = "true", relevant = false)
                    putStringOption(key = "processes", value = "3", relevant = false)
                    putStringOption(key = "reindex-licenses", value = "true", relevant = false)
                    putStringOption(key = "max-in-memory", value = "1024", relevant = false)
                    putStringOption(key = "json", value = "output.json", relevant = false)
                }
            )
            val expectedOptions = ScannerOptions(
                setOf(
                    copyrightOption, licenseOption, packageOption, emailOption, urlOption, metadataOption, ignoreOption,
                    includeOption, timeoutOption, outputOption, unclassifiedOption
                )
            )

            val options = parseScannerOptionsFromCommandLine(commandLineOptions, emptyList(), includeDebug = true)

            options.isSubsetOf(expectedOptions, strict = true) shouldBe true
            expectedOptions.isSubsetOf(options, strict = true) shouldBe true
        }

        "skip debug options if they are disabled" {
            val options = parseScannerOptionsFromCommandLine(
                listOf("--copyright"),
                listOf("--verbose"),
                includeDebug = false
            )

            val unclassifiedOption = options.getOption<UnclassifiedOption>()
            unclassifiedOption.shouldNotBeNull()
            unclassifiedOption.subOptions[SubOptionType.STRINGS_IGNORE, "verbose"].shouldBeNull()
        }

        "not crash for a command line that does not start with an option" {
            val options = parseScannerOptionsFromCommandLine(
                listOf("foo", "--ignore", "a_pattern", "--alpha"),
                emptyList(), includeDebug = false
            )

            options.valueNode<UnclassifiedOption>(SubOptionType.STRINGS, "").asText() shouldBe "foo"
        }

        "replace aliases by their full option names" {
            val aliases = listOf("-clp", "-e", "-ui", "-n", "10")
            val options = parseScannerOptionsFromCommandLine(aliases, emptyList(), includeDebug = false)

            options.contains<CopyrightResultOption>() shouldBe true
            options.contains<LicenseResultOption>() shouldBe true
            options.contains<PackageResultOption>() shouldBe true
            options.contains<EmailResultOption>() shouldBe true
            options.contains<UrlResultOption>() shouldBe true
            options.contains<MetadataResultOption>() shouldBe true

            options.valueNode<UnclassifiedOption>(SubOptionType.STRINGS_IGNORE, "processes").asInt() shouldBe 10
        }

        "handle unknown aliases gracefully" {
            val options = parseScannerOptionsFromCommandLine(listOf("-exi"), emptyList(), includeDebug = false)

            options.valueNode<UnclassifiedOption>(SubOptionType.STRINGS, "x").asText() shouldBe "true"
        }

        "only contain result options referenced on the command line" {
            val options = parseScannerOptionsFromCommandLine(
                listOf("--copyright", "--license"),
                emptyList(),
                includeDebug = false
            )

            options.contains<MetadataResultOption>() shouldBe false
            options.contains<PackageResultOption>() shouldBe false
        }

        "always generate an unclassified option" {
            val options = parseScannerOptionsFromCommandLine(
                listOf("--copyright", "--license"),
                emptyList(),
                includeDebug = false
            )

            val unclassifiedOption = options.getOption<UnclassifiedOption>()
            unclassifiedOption.shouldNotBeNull()
            unclassifiedOption.subOptions.values.fieldNames().asSequence().toList() should beEmpty()
        }

        "generate a timeout option with the standard timeout" {
            val options = parseScannerOptionsFromCommandLine(listOf("--info"), emptyList(), includeDebug = false)

            options.valueNode<TimeoutOption>(SubOptionType.THRESHOLD, SubOptions.DEFAULT_KEY).asInt() shouldBe 120
        }

        "generate an output option for json-pp format" {
            testOutputOption("json-pp", "JSON")
        }

        "generate an output option for json-lines format" {
            testOutputOption("json-lines", "JSON")
        }

        "generate an output option for csv format" {
            testOutputOption("csv", "CSV")
        }

        "generate an output option for html format" {
            testOutputOption("html", "HTML")
        }

        "generate an output option for SPDX RDF format" {
            testOutputOption("spdx-rdf", "SPDX-RDF")
        }

        "generate an output option for SPDX TV format" {
            testOutputOption("spdx-tv", "SPDX-TV")
        }
    }

    "parseScannerOptionsFromResult" should {
        "return a basic options object for a null JSON node" {
            val options = parseScannerOptionsFromResult(null)

            options.options shouldHaveSize 2 // contains only default options
            val unclassifiedOption = options.getOption<UnclassifiedOption>()
            unclassifiedOption.shouldNotBeNull()
            unclassifiedOption.subOptions.values.fieldNames().asSequence().toList() should beEmpty()
            options.valueNode<TimeoutOption>(SubOptionType.THRESHOLD, SubOptions.DEFAULT_KEY).asInt() shouldBe 120
        }

        "parse boolean options" {
            val json = """{
                |"--info": true,
                |"--copyright": true,
                |"--license": true
                |}
            """.trimMargin()

            val options = parseOptionsFromJson(json)

            options.contains<MetadataResultOption>() shouldBe true
            options.contains<CopyrightResultOption>() shouldBe true
            options.contains<LicenseResultOption>() shouldBe true
            options.contains<PackageResultOption>() shouldBe false
        }

        "drop boolean options with a value of false" {
            val json = """{
                |"--info": false,
                |"--copyright": true,
                |"--license": true
                |}
            """.trimMargin()

            val options = parseOptionsFromJson(json)

            options.contains<MetadataResultOption>() shouldBe false
        }

        "parse options with a different type" {
            val json = """{
                |"--info": true,
                |"--timeout": 42,
                |"--license": true,
                |"--license-score": "50"
                |}
            """.trimMargin()

            val options = parseOptionsFromJson(json)

            options.valueNode<TimeoutOption>(SubOptionType.THRESHOLD, SubOptions.DEFAULT_KEY).intValue() shouldBe 42
            options.valueNode<LicenseResultOption>(
                SubOptionType.THRESHOLD,
                "license-score"
            ).doubleValue() shouldBe 50.0
        }

        "handle options with multiple values" {
            val json = """{
                |"--info": true,
                |"--ignore": [
                |  "a_pattern",
                |  "c pattern",
                |  "b_pattern"
                |]
                |}
            """.trimMargin()

            val options = parseOptionsFromJson(json)

            val ignoreOption = options.getOption<IgnoreFilterOption>()
            ignoreOption.shouldNotBeNull()
            ignoreOption.patterns should containExactlyInAnyOrder(
                "path:a_pattern",
                "path:b_pattern", "path:c pattern"
            )
        }
    }
})

/**
 * Parse the given [json] string and pass the resulting node to the _parseScannerOptionsFromResult()_ function.
 */
private fun parseOptionsFromJson(json: String): ScannerOptions {
    val node = jsonMapper.readTree(json)
    return parseScannerOptionsFromResult(node)
}

/**
 * Check that there is an option of the given option type, which has a value of a specific [type] and [key].
 * Return the node storing this value.
 */
private inline fun <reified T : ScannerOptionWithSubOptions<T>> ScannerOptions.valueNode(
    type: SubOptionType,
    key: String
): JsonNode {
    val option = getOption<T>()
    option.shouldNotBeNull()
    val node = option.subOptions[type, key]
    node.shouldNotBeNull()
    return node
}

/**
 * Test whether an output format option is created with the correct format.
 */
private fun testOutputOption(key: String, expectedFormat: String) {
    val options = parseScannerOptionsFromCommandLine(listOf("--$key", "output.txt"), emptyList(), includeDebug = false)

    options.valueNode<OutputFormatOption>(
        SubOptionType.STRINGS,
        SubOptions.DEFAULT_KEY
    ).textValue() shouldBe expectedFormat
    options.valueNode<UnclassifiedOption>(SubOptionType.STRINGS_IGNORE, key).textValue() shouldBe "output.txt"
}
