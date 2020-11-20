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

package org.ossreviewtoolkit.model

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class ScannerOptionsTest : WordSpec({
    "ScannerOptions" should {
        "return a defined option" {
            val option = OutputFormatOption(SubOptions.create { putStringOption("json") })
            val options = ScannerOptions(setOf(option))

            options.getOption<OutputFormatOption>() shouldBe option
        }

        "return null for an undefined option" {
            val options = ScannerOptions(emptySet())

            options.getOption<OutputFormatOption>().shouldBeNull()
        }

        "return a default value for an undefined option" {
            val option = OutputFormatOption(SubOptions.create { putStringOption("json") })
            val options = ScannerOptions(emptySet())

            options.getOption(option) shouldBe option
        }

        "support JSON serialization" {
            val options = completeOptions()
            val json = jsonMapper.writeValueAsString(options)
            val options2 = jsonMapper.readValue(json, ScannerOptions::class.java)

            options2 shouldBe options
            options.isSubsetOf(options2, strict = true) shouldBe true
        }
    }

    "ScannerOptions.isSubsetOf" should {
        "accept empty options" {
            val emptyOptions = ScannerOptions(emptySet())

            emptyOptions.isSubsetOf(completeOptions(), strict = true) shouldBe true
        }

        "be reflexive" {
            val options = completeOptions()

            options.isSubsetOf(options, strict = true) shouldBe true
        }

        "detect options missing in other" {
            val packageOption = PackageResultOption(
                SubOptions.create { putStringOption(key = "consolidate", value = "true") }
            )
            val licenseOption = LicenseResultOption(
                SubOptions.create { putThresholdOption(key = "threshold", value = 50.0) }
            )
            val options1 = ScannerOptions(setOf(packageOption, licenseOption))
            val options2 = ScannerOptions(setOf(licenseOption))

            options1.isSubsetOf(options2, strict = false) shouldBe false
        }

        "detect different options in strict mode" {
            val licenseOption1 = LicenseResultOption(
                SubOptions.create { putThresholdOption(50.0, "threshold") }
            )
            val licenseOption2 = LicenseResultOption(
                SubOptions.create { putThresholdOption(25.0, "threshold") }
            )
            val options1 = ScannerOptions(setOf(licenseOption1))
            val options2 = ScannerOptions(setOf(licenseOption2))

            options1.isSubsetOf(options2, strict = true) shouldBe false
        }

        "ignore different sub options in lenient mode" {
            val copyrightOption1 = CopyrightResultOption(
                SubOptions.create { putStringOption(key = "consolidate", value = "true") }
            )
            val licenseOption1 = LicenseResultOption(
                SubOptions.create { putThresholdOption(key = "threshold", value = 50.0) }
            )
            val copyrightOption2 = CopyrightResultOption(
                SubOptions(jsonMapper.createObjectNode())
            )
            val licenseOption2 = LicenseResultOption(
                SubOptions.create { putThresholdOption(key = "threshold", value = 25.0) }
            )
            val options1 = ScannerOptions(setOf(copyrightOption1, licenseOption1))
            val options2 = ScannerOptions(setOf(copyrightOption2, licenseOption2))

            options1.isSubsetOf(options2, strict = false) shouldBe true
        }
    }

    "SubOptions" should {
        "check the compatibility of equivalent string options" {
            val subOptions1 = SubOptions.create {
                putStringOption("foo")
            }
            val subOptions2 = SubOptions.create {
                putStringOption("foo")
            }

            subOptions1.isCompatible(subOptions2) shouldBe true
        }

        "check the compatibility of different string options" {
            val subOptions1 = SubOptions.create {
                putStringOption("foo")
            }
            val subOptions2 = SubOptions.create {
                putStringOption("bar")
            }

            subOptions1.isCompatible(subOptions2) shouldBe false
        }

        "check the compatibility of equivalent multi-valued string options" {
            val subOptions1 = SubOptions.create {
                putStringOption("foo")
                putStringOption(key = "mode", value = "bar")
            }
            val subOptions2 = SubOptions.create {
                putStringOption("foo")
                putStringOption(key = "mode", value = "bar")
            }

            subOptions1.isCompatible(subOptions2) shouldBe true
        }

        "check the compatibility of different multi-valued string options" {
            val subOptions1 = SubOptions.create {
                putStringOption("foo")
                putStringOption(key = "mode", value = "bar")
            }
            val subOptions2 = SubOptions.create {
                putStringOption("foo")
                putStringOption(key = "mode", value = "baz")
            }

            subOptions1.isCompatible(subOptions2) shouldBe false
        }

        "check the compatibility of multi-valued string options with different keys" {
            val subOptions1 = SubOptions.create {
                putStringOption("foo")
                putStringOption(key = "mode", value = "bar")
            }
            val subOptions2 = SubOptions.create {
                putStringOption("foo")
                putStringOption(key = "mode2", value = "bar")
            }

            subOptions1.isCompatible(subOptions2) shouldBe false
        }

        "check the compatibility of single and multi-valued string options" {
            val subOptions1 = SubOptions.create {
                putStringOption("foo")
            }
            val subOptions2 = SubOptions.create {
                putStringOption("foo")
                putStringOption(key = "other", value = "bar")
            }

            subOptions1.isCompatible(subOptions2) shouldBe false
        }

        "check the compatibility of ignored string options" {
            val subOptions1 = SubOptions.create {
                putStringOption("foo", relevant = false)
            }
            val subOptions2 = SubOptions.create {
                putStringOption("foo", relevant = false)
                putStringOption("bar", "otherField", relevant = false)
            }

            subOptions1.isCompatible(subOptions2) shouldBe true
        }

        "check the compatibility of non-existing ignored string options" {
            val subOptions1 = SubOptions.create {
                putStringOption("foo", relevant = false)
            }
            val subOptions2 = SubOptions(jsonMapper.createObjectNode())

            subOptions1.isCompatible(subOptions2) shouldBe true
        }

        "check the compatibility of equal threshold options" {
            val subOptions1 = SubOptions.create {
                putThresholdOption(42.0)
            }
            val subOptions2 = SubOptions.create {
                putThresholdOption(42.0)
            }

            subOptions1.isCompatible(subOptions2) shouldBe true
        }

        "check the compatibility of equivalent threshold options" {
            val subOptions1 = SubOptions.create {
                putThresholdOption(42.0)
            }
            val subOptions2 = SubOptions.create {
                putThresholdOption(43.0)
            }

            subOptions1.isCompatible(subOptions2) shouldBe true
        }

        "check the compatibility of too large threshold options" {
            val subOptions1 = SubOptions.create {
                putThresholdOption(43.0)
            }
            val subOptions2 = SubOptions.create {
                putThresholdOption(42.0)
            }

            subOptions1.isCompatible(subOptions2) shouldBe false
        }

        "check the compatibility of non-existing threshold options" {
            val subOptions1 = SubOptions.create {
                putThresholdOption(42.0)
            }
            val subOptions2 = SubOptions(jsonMapper.createObjectNode())

            subOptions1.isCompatible(subOptions2) shouldBe false
        }

        "check the compatibility of multiple sub options" {
            val subOptions1 = SubOptions.create {
                putThresholdOption(key = "threshold", value = 42.0)
                putStringOption(key = "format", value = "foo")
            }
            val subOptions2 = SubOptions.create {
                putStringOption(key = "format", value = "foo")
                putThresholdOption(key = "threshold", value = 100.0)
            }

            subOptions1.isCompatible(subOptions2) shouldBe true
        }

        "detect additional options when checking the compatibility" {
            val subOptions1 = SubOptions.create {
                putThresholdOption(key = "count", value = 42.0)
            }
            val subOptions2 = SubOptions.create {
                putStringOption("foo")
                putThresholdOption(key = "count", value = 100.0)
            }

            subOptions1.isCompatible(subOptions2) shouldBe false
        }

        "ignore additional options of type StringsIgnore" {
            val subOptions1 = SubOptions.create {
                putThresholdOption(key = "count", value = 42.0)
            }
            val subOptions2 = SubOptions.create {
                putStringOption(key = "someFlag", value = "foo", relevant = false)
                putThresholdOption(key = "count", value = 100.0)
            }

            subOptions1.isCompatible(subOptions2) shouldBe true
        }
    }

    "Supported options with sub options" should {
        "include OutputFormatOption" {
            checkOptionWithSubOptions { OutputFormatOption(it) }
        }

        "include CopyrightResultOption" {
            checkOptionWithSubOptions { CopyrightResultOption(it) }
        }

        "include EmailResultOption" {
            checkOptionWithSubOptions { EmailResultOption(it) }
        }

        "include LicenseResultOption" {
            checkOptionWithSubOptions { LicenseResultOption(it) }
        }

        "include MetadataResultOption" {
            checkOptionWithSubOptions { MetadataResultOption(it) }
        }

        "include PackageResultOption" {
            checkOptionWithSubOptions { PackageResultOption(it) }
        }

        "include UrlResultOption" {
            checkOptionWithSubOptions { UrlResultOption(it) }
        }
    }

    "IgnoreFilterOption" should {
        "detect a missing ignore option in strict mode" {
            val ignoreOption = IgnoreFilterOption(setOf("pattern"))
            val options = ScannerOptions(emptySet())

            ignoreOption.isCompatible(options) shouldBe false
        }

        "be compatible with an exact match of patterns" {
            val ignoreOption1 = IgnoreFilterOption(setOf("p1", "p2", "p3"))
            val ignoreOption2 = IgnoreFilterOption(setOf("p3", "p2", "p1"))
            val options = ScannerOptions(setOf(ignoreOption2))

            ignoreOption1.isCompatible(options) shouldBe true
            ignoreOption1.isCompatible(options, strict = false) shouldBe true
        }

        "not be compatible with a different set of patterns" {
            val ignoreOption1 = IgnoreFilterOption(setOf("p1", "p2", "p3"))
            val ignoreOption2 = IgnoreFilterOption(setOf("p4", "p2", "p1"))
            val options = ScannerOptions(setOf(ignoreOption2))

            ignoreOption1.isCompatible(options) shouldBe false
            ignoreOption1.isCompatible(options, strict = false) shouldBe false
        }

        "be compatible with a subset of patterns in lenient mode" {
            val ignoreOption1 = IgnoreFilterOption(setOf("p1", "p2", "p3"))
            val ignoreOption2 = IgnoreFilterOption(setOf("p2", "p1"))
            val options = ScannerOptions(setOf(ignoreOption2))

            ignoreOption1.isCompatible(options) shouldBe false
            ignoreOption1.isCompatible(options, strict = false) shouldBe true
        }

        "be compatible with a missing ignore option in lenient mode" {
            val ignoreOption = IgnoreFilterOption(setOf("p1", "p2"))
            val options = ScannerOptions(emptySet())

            ignoreOption.isCompatible(options, strict = false) shouldBe true
        }

        "not be compatible with a missing ignore option in lenient mode if there is an include option" {
            val ignoreOption = IgnoreFilterOption(setOf("p1", "p28"))
            val includeOption = IncludeFilterOption(setOf("p2", "p29"))
            val options = ScannerOptions(setOf(includeOption))

            ignoreOption.isCompatible(options, strict = false) shouldBe false
        }
    }

    "IncludeFilterOption" should {
        "detect a missing include option in strict mode" {
            val includeOption = IncludeFilterOption(setOf("aPattern"))
            val options = ScannerOptions(emptySet())

            includeOption.isCompatible(options) shouldBe false
        }

        "be compatible with an exact match of patterns" {
            val includeOption1 = IncludeFilterOption(setOf("p1", "p2", "p3"))
            val includeOption2 = IncludeFilterOption(setOf("p3", "p2", "p1"))
            val options = ScannerOptions(setOf(includeOption2))

            includeOption1.isCompatible(options) shouldBe true
            includeOption1.isCompatible(options, strict = false) shouldBe true
        }

        "not be compatible with a different set of patterns" {
            val includeOption1 = IncludeFilterOption(setOf("p1", "p2", "p3"))
            val includeOption2 = IncludeFilterOption(setOf("p3", "p2", "p4"))
            val options = ScannerOptions(setOf(includeOption2))

            includeOption1.isCompatible(options) shouldBe false
            includeOption1.isCompatible(options, strict = false) shouldBe false
        }

        "be compatible with a super set of patterns in lenient mode" {
            val includeOption1 = IncludeFilterOption(setOf("p1", "p2", "p3"))
            val includeOption2 = IncludeFilterOption(setOf("p4", "p3", "p2", "p1"))
            val options = ScannerOptions(setOf(includeOption2))

            includeOption1.isCompatible(options) shouldBe false
            includeOption1.isCompatible(options, strict = false) shouldBe true
        }

        "be compatible with a missing include option in lenient mode" {
            val includeOption = IncludeFilterOption(setOf("p1"))
            val options = ScannerOptions(emptySet())

            includeOption.isCompatible(options, strict = false) shouldBe true
        }

        "not be compatible with a missing include option in lenient mode if there is an ignore option" {
            val ignoreOption = IgnoreFilterOption(setOf("p1", "p28"))
            val includeOption = IncludeFilterOption(setOf("p2", "p29"))
            val options = ScannerOptions(setOf(ignoreOption))

            includeOption.isCompatible(options, strict = false) shouldBe false
        }
    }
})

/**
 * Check whether a specific [ScannerOptionWithSubOptions] class implements all compatibility checks correctly.
 * Instances of the class under test are created using the [factory] function.
 */
private fun checkOptionWithSubOptions(factory: (SubOptions) -> ScannerOptionWithSubOptions<*>) {
    // check compatibility to equivalent option in strict mode
    val compatibleSubOptions1 = SubOptions.create {
        putStringOption(key = "some-option", value = "someValue")
        putThresholdOption(key = "max-count", value = 75.0)
        putStringOption(key = "another-option", value = "foo", relevant = false)
    }
    val compatibleSubOptions2 = SubOptions.create {
        putStringOption(key = "some-option", value = "someValue")
        putThresholdOption(key = "max-count", value = 50.0)
    }
    val compatibleOption1 = factory(compatibleSubOptions1)
    val compatibleOption2 = factory(compatibleSubOptions2)
    val options1 = ScannerOptions(setOf(compatibleOption1))

    compatibleOption2.isCompatible(options1) shouldBe true

    // check compatibility to a missing option
    val unmatchedOption = factory(SubOptions(jsonMapper.createObjectNode()))
    val emptyOptions = ScannerOptions(emptySet())
    unmatchedOption.isCompatible(emptyOptions, strict = false) shouldBe false

    // check compatibility to different option in strict mode
    val incompatibleSubOptions1 = SubOptions.create {
        putStringOption("foo")
    }
    val incompatibleSubOptions2 = SubOptions.create {
        putStringOption("bar")
    }
    val incompatibleOption1 = factory(incompatibleSubOptions1)
    val incompatibleOption2 = factory(incompatibleSubOptions2)
    val options2 = ScannerOptions(setOf(incompatibleOption1))

    incompatibleOption2.isCompatible(options2) shouldBe false

    // check compatibility to different option in lenient mode
    incompatibleOption2.isCompatible(options2, strict = false) shouldBe true
}

/**
 * Return a [ScannerOptions] instance that includes all supported options.
 */
private fun completeOptions(): ScannerOptions {
    val outputOption = OutputFormatOption(SubOptions.create { putStringOption("xml") })
    val copyrightOption = CopyrightResultOption(
        SubOptions.create { putStringOption(key = "consolidate", value = "true") }
    )
    val emailOption = EmailResultOption(
        SubOptions.create { putThresholdOption(100.0) }
    )
    val licenseOption = LicenseResultOption(
        SubOptions.create {
            putStringOption(key = "consolidate", value = "true")
            putStringOption(key = "license-text", value = "true")
            putThresholdOption(key = "license-score", value = 75.0)
        }
    )
    val metadataOption = MetadataResultOption(
        SubOptions.create { putStringOption(key = "mark-source", value = "true") }
    )
    val packageOption = PackageResultOption(
        SubOptions.create { putStringOption(key = "consolidate", value = "true") }
    )
    val urlOption = UrlResultOption(
        SubOptions.create { putThresholdOption(key = "max-url", value = 64.0) }
    )

    return ScannerOptions(
        setOf(
            outputOption,
            copyrightOption,
            emailOption,
            licenseOption,
            metadataOption,
            packageOption,
            urlOption
        )
    )
}
