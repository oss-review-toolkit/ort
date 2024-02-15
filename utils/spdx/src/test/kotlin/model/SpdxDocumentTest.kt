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

package org.ossreviewtoolkit.utils.spdx.model

import com.fasterxml.jackson.databind.exc.ValueInstantiationException

import io.kotest.assertions.json.ArrayOrder
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.string.shouldContain

import java.io.File

import org.ossreviewtoolkit.utils.spdx.SpdxModelMapper
import org.ossreviewtoolkit.utils.spdx.yamlMapper

/**
 * This test uses the following test assets copied from the SPDX 2.2.2 specification examples.
 *
 * 1. https://github.com/spdx/spdx-spec/blob/development/v2.2.2/examples/SPDXYAMLExample-2.2.spdx.yaml
 * 2. https://github.com/spdx/spdx-spec/blob/development/v2.2.2/examples/SPDXJSONExample-v2.2.spdx.json
 *
 * The "*-no-ranges.spdx.*" resource files have the "ranges" property removed, which is actually broken in the
 * specification and impossible to implement.
 */
class SpdxDocumentTest : WordSpec({
    val spdxExamplesDir = File("src/test/assets/spdx-spec-examples").absoluteFile

    "The official YAML example from the SPDX specification version 2.2" should {
        "be deserializable" {
            val yaml = spdxExamplesDir.resolve("SPDXYAMLExample-2.2.spdx.yaml")

            SpdxModelMapper.read<SpdxDocument>(yaml)
        }
    }

    "The official YAML example without ranges from the SPDX specification version 2.2" should {
        "have idempotent (de)-serialization" {
            val yaml = spdxExamplesDir.resolve("SPDXYAMLExample-2.2-no-ranges.spdx.yaml").readText()

            val reSerializedYaml = SpdxModelMapper.fromYaml<SpdxDocument>(yaml).let {
                SpdxModelMapper.toYaml(it)
            }

            reSerializedYaml lenientShouldEqualYaml yaml
        }
    }

    "The official JSON example from the SPDX specification version 2.2" should {
        "be deserializable" {
            val json = spdxExamplesDir.resolve("SPDXJSONExample-v2.2.spdx.json")

            SpdxModelMapper.read<SpdxDocument>(json)
        }
    }

    "The official JSON example without ranges from the SPDX specification version 2.2" should {
        "have idempotent (de-)serialization" {
            val json = spdxExamplesDir.resolve("SPDXJSONExample-v2.2-no-ranges.spdx.json").readText()

            val reSerializedJson = SpdxModelMapper.fromJson<SpdxDocument>(json).let {
                SpdxModelMapper.toJson(it)
            }

            reSerializedJson lenientShouldEqualJson json
        }
    }

    "Parsing an SPDX document" should {
        "fail if no relationship of type DESCRIBES or documentDescribes is contained" {
            val yaml = spdxExamplesDir.resolve(
                "SPDXYAMLExample-2.2-no-relationship-describes-or-document-describes.spdx.yaml"
            )

            val exception = shouldThrow<ValueInstantiationException> {
                SpdxModelMapper.read<SpdxDocument>(yaml)
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
    val json = yamlMapper.readTree(this).toString()
    val expectedJson = yamlMapper.readTree(expected).toString()

    return json.lenientShouldEqualJson(expectedJson)
}
