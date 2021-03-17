/*
 * Copyright (C) 2020 HERE Europe B.V.
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

package org.ossreviewtoolkit.spdx

import com.fasterxml.jackson.databind.ObjectMapper

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.spdx.model.SpdxDocument

private fun format(value: String, mapper: ObjectMapper): String =
    mapper.readTree(value).let { node ->
        mapper.writeValueAsString(node)
    }

private fun formatYaml(yaml: String): String = format(yaml, SpdxModelMapper.yamlMapper)

private fun formatJson(json: String): String = format(json, SpdxModelMapper.jsonMapper)

private fun readResourceAsText(resourceFile: String): String =
    SpdxDocumentModelTest::class.java.getResource(resourceFile).readText()

/**
 * This test uses the following test assets copied from the SPDX 2.2.1 specification examples.
 *
 * 1. https://github.com/spdx/spdx-spec/blob/development/v2.2.1/examples/SPDXYAMLExample-2.2.spdx.yaml
 * 2. https://github.com/spdx/spdx-spec/blob/development/v2.2.1/examples/SPDXJSONExample-v2.2.spdx.json
 *
 * The "*-no-ranges*" resource files have the 'ranges' property removed, which is actually broken in specification and
 * thus impossible to implement.
 */
class SpdxDocumentModelTest : WordSpec({
    "The official YAML example from the SPDX specification version 2.2" should {
        "be deserializable" {
            val yaml = readResourceAsText("/spdx-spec-examples/SPDXYAMLExample-2.2.spdx.yaml")

            SpdxModelMapper.fromYaml<SpdxDocument>(yaml)
        }
    }

    "The official YAML example without ranges from the SPDX specification version 2.2" should {
        "have idempotent (de)-serialization" {
            val yaml = readResourceAsText("/spdx-spec-examples/SPDXYAMLExample-2.2-no-ranges.spdx.yaml")

            val document = SpdxModelMapper.fromYaml<SpdxDocument>(yaml)
            val serializedYaml = SpdxModelMapper.toYaml(document)

            serializedYaml shouldBe formatYaml(yaml)
        }
    }

    "The official JSON example from the SPDX specification version 2.2" should {
        "be deserializable" {
            val json = readResourceAsText("/spdx-spec-examples/SPDXJSONExample-v2.2.spdx.json")

            SpdxModelMapper.fromJson<SpdxDocument>(json)
        }
    }

    "The official JSON example without ranges from the SPDX specification version 2.2" should {
        "have idempotent (de-)serialization" {
            val json = readResourceAsText("/spdx-spec-examples/SPDXJSONExample-v2.2-no-ranges.spdx.json")

            val document = SpdxModelMapper.fromJson<SpdxDocument>(json)
            val serializedJson = SpdxModelMapper.toJson(document)

            serializedJson shouldBe formatJson(json)
        }
    }
})
