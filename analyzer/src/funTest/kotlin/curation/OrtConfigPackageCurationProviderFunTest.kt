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

package org.ossreviewtoolkit.analyzer.curation

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.maps.beEmpty as beEmptyMap
import io.kotest.matchers.maps.shouldContainKeys
import io.kotest.matchers.should
import io.kotest.matchers.shouldNot

import org.ossreviewtoolkit.model.Identifier

class OrtConfigPackageCurationProviderFunTest : StringSpec() {
    init {
        "provider can load curations from the ort-config repository" {
            val azureCore = Identifier("NuGet::Azure.Core:1.22.0")
            val azureCoreAmqp = Identifier("NuGet::Azure.Core.Amqp:1.2.0")

            val curations = OrtConfigPackageCurationProvider().getCurationsFor(listOf(azureCore, azureCoreAmqp))

            curations.shouldContainKeys(azureCore, azureCoreAmqp)
            curations.getValue(azureCore) shouldNot beEmpty()
            curations.getValue(azureCoreAmqp) shouldNot beEmpty()
        }

        "provider does not fail for packages which have no curations" {
            val id = Identifier("Some:Bogus:Package:Id")

            val curations = OrtConfigPackageCurationProvider().getCurationsFor(listOf(id))

            curations should beEmptyMap()
        }
    }
}
