/*
 * Copyright (C) 2026 The ORT Project Copyright Holders <https://github.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://apache.org
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

package org.ossreviewtoolkit.utils.spdxexpression

import io.kotest.core.spec.style.WordSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.model.toYaml

class SpdxOrLaterLicenseMapperTest : WordSpec({
    "INSTANCE's satisfyingIdsForId" should {
        "be as expected" {
            val expectedSuccorsForId = javaClass.getResource(
                "/spdx-or-later-mapper-expected-satisfying-ids-for-id.yml"
            ).shouldNotBeNull().readText()

            SpdxOrLaterLicenseMapper.INSTANCE.satisfyingIdsForId.mapValuesTo(sortedMapOf()) {
                it.value.sorted()
            }.toYaml() shouldBe expectedSuccorsForId
        }

        "not contain any license IDs with '+', '-only', or '-or-later' suffixes" {
            val ids = buildSet {
                addAll(SpdxOrLaterLicenseMapper.INSTANCE.satisfyingIdsForId.values.flatten())
                addAll(SpdxOrLaterLicenseMapper.INSTANCE.satisfyingIdsForId.keys)
            }

            ids.forAll { id ->
                id.endsWith("+") shouldBe false
                id.endsWith("-only") shouldBe false
                id.endsWith("-or-later") shouldBe false
            }
        }
    }

    "INSTANCE's satisfies()" should {
        val mapper = SpdxOrLaterLicenseMapper.INSTANCE

        "handle the plus operator for non-GNU licenses correctly" {
            mapper.satisfies(offered = "Apache-2.0", required = "Apache-2.0+") shouldBe true
            mapper.satisfies(offered = "Apache-2.0+", required = "Apache-2.0") shouldBe false
        }

        "handle GPL licenses correctly" {
            mapper.satisfies(offered = "GPL-2.0-only", required = "GPL-1.0-or-later") shouldBe true
            mapper.satisfies(offered = "GPL-3.0-only", required = "GPL-1.0-or-later") shouldBe true
            mapper.satisfies(offered = "GPL-3.0-or-later", required = "GPL-2.0-or-later") shouldBe true

            mapper.satisfies(offered = "GPL-1.0-only", required = "GPL-3.0-only") shouldBe false
            mapper.satisfies(offered = "GPL-2.0-only", required = "GPL-3.0-only") shouldBe false
            mapper.satisfies(offered = "GPL-2.0-only", required = "GPL-3.0-or-later") shouldBe false
            mapper.satisfies(offered = "GPL-2.0-or-later", required = "GPL-3.0-or-later") shouldBe false
        }

        "handle licenses with differing base ID correctly" {
            mapper.satisfies(offered = "LGPL-2.1-only", required = "GPL-2.0-or-later") shouldBe false
            mapper.satisfies(offered = "MIT", required = "Apache-2.0+") shouldBe false
        }

        "reject licenses with older version" {
            mapper.satisfies(offered = "GPL-1.0-only", required = "GPL-2.0-or-later") shouldBe false
        }

        "evaluate identity correctly (every license satisfies itself)" {
            mapper.satisfies(offered = "GPL-2.0-only", required = "GPL-2.0-only") shouldBe true
            mapper.satisfies(offered = "GPL-2.0-or-later", required = "GPL-2.0-or-later") shouldBe true
            mapper.satisfies(offered = "GPL-2.0+", required = "GPL-2.0+") shouldBe true
            mapper.satisfies(offered = "Apache-2.0", required = "Apache-2.0") shouldBe true
            mapper.satisfies(offered = "Apache-2.0+", required = "Apache-2.0+") shouldBe true

            mapper.satisfies(offered = "GPL-1.0-only+", required = "GPL-1.0-only+") shouldBe true
        }

        "handle malformed edge cases" {
            mapper.satisfies(offered = "GPL-3.0-only", required = "GPL-1.0-only+") shouldBe true
            mapper.satisfies(offered = "GPL-1.0-only+", required = "GPL-1.0-or-later") shouldBe true
            mapper.satisfies(offered = "GPL-2.0-or-later+", required = "GPL-2.0-or-later") shouldBe true
        }
    }

    "satisfies()" should {
        val testMapper = SpdxOrLaterLicenseMapper(mapOf("A" to "B", "B" to "C"))

        "handle exact base matches correctly" {
            testMapper.satisfies(offered = "A", required = "A") shouldBe true
            testMapper.satisfies(offered = "A+", required = "A+") shouldBe true
            testMapper.satisfies(offered = "A-only", required = "A-only") shouldBe true
        }

        "allow a stricter offer to satisfy a broader requirement of the same version" {
            testMapper.satisfies(offered = "A", required = "A+") shouldBe true
            testMapper.satisfies(offered = "A-only", required = "A-or-later") shouldBe true
        }

        "block a broader offer from satisfying a strict requirement of the same version" {
            testMapper.satisfies(offered = "A+", required = "A") shouldBe false
            testMapper.satisfies(offered = "A-or-later", required = "A-only") shouldBe false
        }

        "allow a valid evolution step if the requirement permits later versions" {
            testMapper.satisfies(offered = "B", required = "A+") shouldBe true
            testMapper.satisfies(offered = "C-only", required = "A-or-later") shouldBe true
            testMapper.satisfies(offered = "B+", required = "A+") shouldBe true
        }

        "block an evolution step if the requirement is strictly fixed to a version" {
            testMapper.satisfies(offered = "B", required = "A") shouldBe false
            testMapper.satisfies(offered = "C-only", required = "A-only") shouldBe false
        }

        "block backward evolution (older version offered for newer requirement)" {
            testMapper.satisfies(offered = "A", required = "B+") shouldBe false
        }
    }
})
