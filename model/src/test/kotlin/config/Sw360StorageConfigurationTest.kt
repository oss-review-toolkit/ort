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

package org.ossreviewtoolkit.model.config

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldNotContain

import org.ossreviewtoolkit.model.yamlMapper

class Sw360StorageConfigurationTest : StringSpec({
    "Credentials should be ignored in serialization" {
        val sw360StorageConfiguration = Sw360StorageConfiguration(
            "restUrl",
            "authUrl",
            "username",
            "password",
            "clientId",
            "clientPassword",
            "token"
        )
        val yaml = yamlMapper.writeValueAsString(sw360StorageConfiguration).lowercase()

        yaml shouldNotContain "password"
        yaml shouldNotContain "token"
    }
})
