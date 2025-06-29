/*
 * Copyright (C) 2020 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.utils.spdxdocument.model

import com.fasterxml.jackson.databind.exc.ValueInstantiationException

import io.kotest.assertions.json.ArrayOrder
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.string.shouldContain

import org.ossreviewtoolkit.utils.spdxdocument.SpdxModelMapper
import org.ossreviewtoolkit.utils.test.readResource

/**
 * This test uses the following test assets copied from the SPDX 2.2.2 specification examples.
 *
 * 1. https://github.com/spdx/spdx-spec/blob/v2.2.2/examples/SPDXYAMLExample-2.2.spdx.yaml
 * 2. https://github.com/spdx/spdx-spec/blob/v2.2.2/examples/SPDXJSONExample-v2.2.spdx.json
 *
 * The "*-no-ranges.spdx.*" resource files have the "ranges" property removed, which is actually broken in the
 * specification and impossible to implement.
 *
 * Note: The examples files of v2.2.2, v2.2.1 and also v2.2.1-ISO-final are identical.
 */
class SpdxDocumentTest : WordSpec({
    "The official YAML example from the SPDX specification version 2.2.2" should {
        "be deserializable" {
            val yaml = readResource("/spec-examples/v2.2.2/SPDXYAMLExample-2.2.spdx.yaml")

            SpdxModelMapper.fromYaml<SpdxDocument>(yaml)
        }
    }

    "The official YAML example without ranges from the SPDX specification version 2.2.2" should {
        "have idempotent (de)-serialization" {
            val yaml = readResource("/spec-examples/v2.2.2/SPDXYAMLExample-2.2-no-ranges.spdx.yaml")

            val reSerializedYaml = SpdxModelMapper.fromYaml<SpdxDocument>(yaml).let {
                SpdxModelMapper.toYaml(it)
            }

            // Account for the fact that ORT serializes SPDX 2.3 now.
            reSerializedYaml.replace("PACKAGE-MANAGER", "PACKAGE_MANAGER") lenientShouldEqualYaml yaml
        }
    }

    "The official JSON example from the SPDX specification version 2.2.2" should {
        "be deserializable" {
            val json = readResource("/spec-examples/v2.2.2/SPDXJSONExample-v2.2.spdx.json")

            SpdxModelMapper.fromJson<SpdxDocument>(json)
        }
    }

    "The official JSON example without ranges from the SPDX specification version 2.2.2" should {
        "have idempotent (de-)serialization" {
            val json = readResource("/spec-examples/v2.2.2/SPDXJSONExample-v2.2-no-ranges.spdx.json")

            val reSerializedJson = SpdxModelMapper.fromJson<SpdxDocument>(json).let {
                SpdxModelMapper.toJson(it)
            }

            // Account for the fact that ORT serializes SPDX 2.3 now.
            reSerializedJson.replace("PACKAGE-MANAGER", "PACKAGE_MANAGER") lenientShouldEqualJson json
        }
    }

    "Parsing an SPDX document" should {
        "fail if no relationship of type DESCRIBES or documentDescribes is contained" {
            val yaml = readResource(
                "/spec-examples/v2.2.2/SPDXYAMLExample-2.2-no-relationship-describes-or-document-describes.spdx.yaml"
            )

            val exception = shouldThrow<ValueInstantiationException> {
                SpdxModelMapper.fromYaml<SpdxDocument>(yaml)
            }

            exception.message shouldContain "The document must either have at least one relationship of type " +
                "'DESCRIBES' or contain the 'documentDescribes' field."
        }
    }
})

private infix fun String.lenientShouldEqualJson(expected: String): String =
    shouldEqualJson {
        arrayOrder = ArrayOrder.Lenient
        expected
    }

private infix fun String.lenientShouldEqualYaml(expected: String): String {
    val json = SpdxModelMapper.yamlMapper.readTree(this).toString()
    val expectedJson = SpdxModelMapper.yamlMapper.readTree(expected).toString()

    return json.lenientShouldEqualJson(expectedJson)
}
