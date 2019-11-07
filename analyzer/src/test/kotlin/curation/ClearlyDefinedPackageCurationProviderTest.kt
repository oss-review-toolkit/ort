/*
 * Copyright (C) 2019 Bosch Software Innovations GmbH
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

package com.here.ort.analyzer.curation

import com.here.ort.clearlydefined.ClearlyDefinedService.Server
import com.here.ort.model.Identifier

import io.kotlintest.matchers.haveSize
import io.kotlintest.should
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec

class ClearlyDefinedPackageCurationProviderTest : StringSpec() {
    init {
        "Provider can read curations from development server" {
            val provider = ClearlyDefinedPackageCurationProvider(Server.DEVELOPMENT)

            val identifier = Identifier("NPM", "@nestjs", "platform-express", "6.2.3")
            val curations = provider.getCurationsFor(identifier)

            curations should haveSize(1)
            curations.first().data.declaredLicenses shouldBe sortedSetOf("Apache-1.0")
        }
    }
}
