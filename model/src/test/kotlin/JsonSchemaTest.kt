/*
 * Copyright (C) 2021 Bosch.IO GmbH
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

package org.ossreviewtoolkit.model

import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion

import io.kotest.core.spec.style.StringSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.should

import java.io.File

class JsonSchemaTest : StringSpec() {
    private val mapper = FileFormat.YAML.mapper

    private val repositoryConfigurationSchema = JsonSchemaFactory
        .builder(JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7))
        .objectMapper(mapper)
        .build()
        .getSchema(File("../integrations/schemas/repository-configuration-schema.json").toURI())

    init {
        ".ort.yml validates successfully" {
            val repositoryConfiguration = File("../.ort.yml").toJsonNode()

            val errors = repositoryConfigurationSchema.validate(repositoryConfiguration)

            errors should beEmpty()
        }

        ".ort.yml examples validate successfully" {
            val examplesDir = File("../examples")
            val exampleFiles =
                examplesDir.walk().filterTo(mutableListOf()) { it.isFile && it.name.endsWith(".ort.yml") }

            exampleFiles.forAll {
                val repositoryConfiguration = it.toJsonNode()

                val errors = repositoryConfigurationSchema.validate(repositoryConfiguration)

                errors should beEmpty()
            }
        }

        "curation.yml example validates successfully" {
            val curationsSchema = JsonSchemaFactory
                .builder(JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7))
                .objectMapper(mapper)
                .build()
                .getSchema(File("../integrations/schemas/curations-schema.json").toURI())
            val curationsExample = File("../examples/curations.yml").toJsonNode()

            val errors = curationsSchema.validate(curationsExample)

            errors should beEmpty()
        }

        "package-configuration.yml validates successfully" {
            val packageConfigurationSchema = JsonSchemaFactory
                .builder(JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7))
                .objectMapper(mapper)
                .build()
                .getSchema(File("../integrations/schemas/package-configuration-schema.json").toURI())
            val packageConfiguration = File("src/test/assets/package-configuration.yml").toJsonNode()

            val errors = packageConfigurationSchema.validate(packageConfiguration)

            errors should beEmpty()
        }

        "resolutions.yml example validates successfully" {
            val curationsSchema = JsonSchemaFactory
                .builder(JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7))
                .objectMapper(mapper)
                .build()
                .getSchema(File("../integrations/schemas/resolutions-schema.json").toURI())
            val curationsExample = File("../examples/resolutions.yml").toJsonNode()

            val errors = curationsSchema.validate(curationsExample)

            errors should beEmpty()
        }
    }

    private fun File.toJsonNode() = mapper.readTree(inputStream())
}
