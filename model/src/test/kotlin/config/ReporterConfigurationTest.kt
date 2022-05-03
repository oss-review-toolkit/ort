/*
 * Copyright (C) 2022 Bosch.IO GmbH
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

package org.ossreviewtoolkit.model.config

import com.fasterxml.jackson.module.kotlin.readValue

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should

import java.io.File

import org.ossreviewtoolkit.model.yamlMapper

class ReporterConfigurationTest : WordSpec({
    "Generic reporter options" should {
        "not be serialized as they might contain sensitive information" {
            rereadReporterConfig(loadReporterConfig()).options should beNull()
        }
    }
})

/**
 * Load the ORT reference configuration and extract the reporter configuration.
 */
private fun loadReporterConfig(): ReporterConfiguration =
    OrtConfiguration.load(file = File("src/main/resources/reference.conf")).reporter

/**
 * Perform a serialization round-trip of the given reporter [config] and return the result. This is used to check
 * whether serialization and deserialization of reporter configurations work as expected.
 */
private fun rereadReporterConfig(config: ReporterConfiguration): ReporterConfiguration {
    val yaml = yamlMapper.writeValueAsString(config)
    return yamlMapper.readValue(yaml)
}
