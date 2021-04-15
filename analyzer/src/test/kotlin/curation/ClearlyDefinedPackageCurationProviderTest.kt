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

package org.ossreviewtoolkit.analyzer.curation

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.haveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.clients.clearlydefined.ClearlyDefinedService.Server
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.spdx.toSpdx

class ClearlyDefinedPackageCurationProviderTest : WordSpec({
    "The production server" should {
        "return an existing curation for the javax.servlet-api Maven package" {
            val provider = ClearlyDefinedPackageCurationProvider()

            val identifier = Identifier("Maven:javax.servlet:javax.servlet-api:3.1.0")
            val curations = provider.getCurationsFor(identifier)

            curations should haveSize(1)
            curations.first().data.concludedLicense shouldBe
                    "CDDL-1.0 OR GPL-2.0-only WITH Classpath-exception-2.0".toSpdx()
        }

        "return no curation for a non-existing dummy NPM package" {
            val provider = ClearlyDefinedPackageCurationProvider()

            val identifier = Identifier("NPM:@scope:name:1.2.3")
            val curations = provider.getCurationsFor(identifier)

            curations should beEmpty()
        }
    }

    "The development server" should {
        "return an existing curation for the platform-express NPM package" {
            val provider = ClearlyDefinedPackageCurationProvider(Server.DEVELOPMENT)

            val identifier = Identifier("NPM:@nestjs:platform-express:6.2.3")
            val curations = provider.getCurationsFor(identifier)

            curations should haveSize(1)
            curations.first().data.concludedLicense shouldBe "Apache-1.0".toSpdx()
        }

        "return no curation for a non-existing dummy Maven package" {
            val provider = ClearlyDefinedPackageCurationProvider()

            val identifier = Identifier("Maven:group:name:1.2.3")
            val curations = provider.getCurationsFor(identifier)

            curations should beEmpty()
        }
    }
})
