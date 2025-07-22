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

package org.ossreviewtoolkit.utils.ort

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.shouldHaveSingleElement
import io.kotest.matchers.should

class VersionUtilsTest : WordSpec({
    "filterVersionNames" should {
        "return an empty list for a blank version" {
            val names = listOf("dummy")

            filterVersionNames("", names) should beEmpty()
            filterVersionNames(" ", names) should beEmpty()
        }

        "return an empty list for empty names" {
            val names = listOf("")

            filterVersionNames("", names) should beEmpty()
            filterVersionNames(" ", names) should beEmpty()
            filterVersionNames("1.0", names) should beEmpty()
        }

        "return an empty list for blank names" {
            val names = listOf(" ")

            filterVersionNames("", names) should beEmpty()
            filterVersionNames(" ", names) should beEmpty()
            filterVersionNames("1.0", names) should beEmpty()
        }

        "find names separated by underscores" {
            val names = listOf(
                "A02",
                "A03",
                "A04",
                "DEV0_9_3",
                "DEV0_9_4",
                "DEV_0_9_7",
                "Exoffice",
                "PROD_1_0_1",
                "PROD_1_0_2",
                "PROD_1_0_3",
                "before_SourceForge",
                "before_debug_changes"
            )

            filterVersionNames("1.0.3", names) shouldHaveSingleElement "PROD_1_0_3"
            filterVersionNames("1", names) should beEmpty()
            filterVersionNames("0.3", names) should beEmpty()
        }

        "find names separated by underscores that include a prefix / suffix" {
            val names = listOf(
                "REL_4_0_0_FINAL",
                "REL_3_16_FINAL",
                "REL_3_16_BETA2",
                "REL_3_16_BETA1",
                "before_junit5_update"
            )

            filterVersionNames("4.0.0", names) shouldHaveSingleElement "REL_4_0_0_FINAL"
            filterVersionNames("3.16", names) shouldHaveSingleElement "REL_3_16_FINAL"
            filterVersionNames("before_junit5_update", names) shouldHaveSingleElement "before_junit5_update"
        }

        "find names separated by dots" {
            val names = listOf(
                "0.10.0",
                "0.10.1",
                "0.5.0",
                "0.6.0",
                "0.7.0",
                "0.8.0",
                "0.9.0"
            )

            filterVersionNames("0.10.0", names) shouldHaveSingleElement "0.10.0"
            filterVersionNames("0.10", names) should beEmpty()
            filterVersionNames("10.0", names) should beEmpty()
            filterVersionNames("1", names) should beEmpty()
        }

        "find names with mixed separators" {
            val names = listOf(
                "docutils-0.10",
                "docutils-0.11",
                "docutils-0.12",
                "docutils-0.13.1",
                "docutils-0.14",
                "docutils-0.14.0a",
                "docutils-0.14a0",
                "docutils-0.14rc1",
                "docutils-0.14rc2",
                "docutils-0.3.7",
                "docutils-0.3.9",
                "docutils-0.4",
                "docutils-0.5",
                "docutils-0.6",
                "docutils-0.7",
                "docutils-0.8",
                "docutils-0.8.1",
                "docutils-0.9",
                "docutils-0.9.1",
                "initial",
                "merged_to_nesting",
                "prest-0.3.10",
                "prest-0.3.11",
                "start"
            )

            filterVersionNames("0.3.9", names) shouldHaveSingleElement "docutils-0.3.9"
            filterVersionNames("0.14", names) shouldHaveSingleElement "docutils-0.14"
            filterVersionNames("0.3.10", names) shouldHaveSingleElement "prest-0.3.10"
            filterVersionNames("0.13", names) should beEmpty()
            filterVersionNames("13.1", names) should beEmpty()
            filterVersionNames("1.0", names) should beEmpty()
        }

        "find names with mixed separators and different capitalization" {
            val names = listOf("CLASSWORLDS_1_1_ALPHA_2")

            filterVersionNames("1.1-alpha-2", names) shouldHaveSingleElement "CLASSWORLDS_1_1_ALPHA_2"
        }

        "find names with a 'v' prefix" {
            val names = listOf(
                "v6.2.0",
                "v6.2.1",
                "v6.2.2",
                "v6.20.0",
                "v6.20.1",
                "v6.20.2",
                "v6.20.3",
                "v6.21.0",
                "v6.21.1",
                "v6.22.0",
                "v6.22.1",
                "v6.22.2",
                "v6.23.0",
                "v6.23.1",
                "v6.24.0",
                "v6.24.1",
                "v6.25.0",
                "v6.26.0",
                "v7.0.0-beta.0",
                "v7.0.0-beta.1",
                "v7.0.0-beta.2",
                "v7.0.0-beta.3"
            )

            filterVersionNames("6.26.0", names) shouldHaveSingleElement "v6.26.0"
            filterVersionNames("7.0.0-beta.2", names) shouldHaveSingleElement "v7.0.0-beta.2"
            filterVersionNames("7.0.0", names) should beEmpty()
            filterVersionNames("2.0", names) should beEmpty()
        }

        "find names with the project name as the prefix" {
            val names = listOf(
                "babel-plugin-transform-member-expression-literals@6.9.0",
                "babel-plugin-transform-simplify-comparison-operators@6.9.0",
                "babel-plugin-transform-property-literals@6.9.0"
            )

            filterVersionNames(
                "6.9.0",
                names,
                "babel-plugin-transform-simplify-comparison-operators"
            ) shouldHaveSingleElement "babel-plugin-transform-simplify-comparison-operators@6.9.0"
        }

        "find names when others with trailing digits are present" {
            val names = listOf(
                "1.11.6",
                "1.11.60",
                "1.11.61",
                "1.11.62",
                "1.11.63",
                "1.11.64",
                "1.11.65",
                "1.11.66",
                "1.11.67",
                "1.11.68",
                "1.11.69"
            )

            filterVersionNames("1.11.6", names) shouldHaveSingleElement "1.11.6"
        }

        "find names with only a single revision number as the version" {
            val names = listOf(
                "my_project-123",
                "my_project-4711",
                "my_project-8888"
            )

            filterVersionNames("4711", names) shouldHaveSingleElement "my_project-4711"
        }

        "find names that have a numeric suffix as part of the name" {
            val names = listOf(
                "my_project_v1-1.0.2",
                "my_project_v1-1.0.3",
                "my_project_v1-1.1.0"
            )

            filterVersionNames("1.0.3", names) shouldHaveSingleElement "my_project_v1-1.0.3"
        }

        "find names that use an abbreviated SHA1 as the suffix" {
            val names = listOf(
                "3.9.0.99-a3d9827",
                "sdk-3.9.0.99",
                "v3.9.0.99"
            )

            filterVersionNames("3.9.0.99", names) should containExactly("3.9.0.99-a3d9827", "sdk-3.9.0.99", "v3.9.0.99")
        }

        "find names that match the version without an ignorable suffix" {
            val names = listOf(
                "6.2.8",
                "6.2.9",
                "6.2.10"
            )

            filterVersionNames("6.2.9.Final", names) should containExactly("6.2.9")
        }
    }
})
