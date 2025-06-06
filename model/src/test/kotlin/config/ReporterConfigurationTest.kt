/*
 * Copyright (C) 2022 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.maps.beEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should

import java.io.File

import org.ossreviewtoolkit.model.fromYaml
import org.ossreviewtoolkit.model.toYaml
import org.ossreviewtoolkit.utils.ort.ORT_REFERENCE_CONFIG_FILENAME

class ReporterConfigurationTest : WordSpec({
    "Reporter secrets" should {
        "not be serialized as they contain sensitive information" {
            rereadReporterConfig(loadReporterConfig()).reporters?.get("FossId") shouldNotBeNull {
                secrets should beEmpty()
            }
        }
    }
})

/**
 * Load the ORT reference configuration and extract the reporter configuration.
 */
private fun loadReporterConfig(): ReporterConfiguration =
    OrtConfiguration.load(file = File("src/main/resources/$ORT_REFERENCE_CONFIG_FILENAME")).reporter

/**
 * Perform a serialization round-trip of the given reporter [config] and return the result. This is used to check
 * whether serialization and deserialization of reporter configurations work as expected.
 */
private fun rereadReporterConfig(config: ReporterConfiguration): ReporterConfiguration = config.toYaml().fromYaml()
