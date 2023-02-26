/*
 * Copyright (C) 2019 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagecurationproviders.clearlydefined

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldHaveSingleElement
import io.kotest.matchers.should
import io.kotest.matchers.shouldNot

import org.ossreviewtoolkit.clients.clearlydefined.ClearlyDefinedService.Server
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.utils.spdx.toSpdx

class ClearlyDefinedPackageCurationProviderFunTest : WordSpec({
    "The production server" should {
        val provider = ClearlyDefinedPackageCurationProvider()

        "return an existing curation for the javax.servlet-api Maven package" {
            val packages = createPackagesFromIds("Maven:javax.servlet:javax.servlet-api:3.1.0")

            val curations = provider.getCurationsFor(packages)

            curations.map { it.data.concludedLicense } shouldHaveSingleElement
                    "CDDL-1.0 OR GPL-2.0-only WITH Classpath-exception-2.0".toSpdx()
        }

        "return an existing curation for the slf4j-log4j12 Maven package" {
            val packages = createPackagesFromIds("Maven:org.slf4j:slf4j-log4j12:1.7.30")

            val curations = provider.getCurationsFor(packages)

            curations.map { it.data.vcs?.revision } shouldHaveSingleElement "0b97c416e42a184ff9728877b461c616187c58f7"
        }

        "return no curation for a non-existing dummy NPM package" {
            val packages = createPackagesFromIds("NPM:@scope:name:1.2.3")

            val curations = provider.getCurationsFor(packages)

            curations should beEmpty()
        }
    }

    "The development server" should {
        val provider = ClearlyDefinedPackageCurationProvider(Server.DEVELOPMENT)

        "return an existing curation for the platform-express NPM package" {
            val packages = createPackagesFromIds("NPM:@nestjs:platform-express:6.2.3")

            val curations = provider.getCurationsFor(packages)

            curations.map { it.data.concludedLicense } shouldHaveSingleElement "Apache-1.0".toSpdx()
        }

        "return no curation for a non-existing dummy Maven package" {
            val packages = createPackagesFromIds("Maven:group:name:1.2.3")

            val curations = provider.getCurationsFor(packages)

            curations should beEmpty()
        }
    }

    "Curations" should {
        "get filtered by score" {
            val config = ClearlyDefinedPackageCurationProviderConfig(
                serverUrl = Server.PRODUCTION.apiUrl,
                minTotalLicenseScore = 80
            )
            val provider = ClearlyDefinedPackageCurationProvider(config)

            // Use an id which is known to have non-empty results from an earlier test.
            val packages = createPackagesFromIds("Maven:org.slf4j:slf4j-log4j12:1.7.30")

            val curations = provider.getCurationsFor(packages)

            curations should beEmpty()
        }

        "be retrieved for packages without a namespace" {
            val provider = ClearlyDefinedPackageCurationProvider()
            val packages = createPackagesFromIds("NPM::acorn:0.6.0")

            val curations = provider.getCurationsFor(packages)

            curations shouldNot beEmpty()
        }
    }
})

private fun createPackagesFromIds(vararg ids: String) =
    ids.map { Package.EMPTY.copy(id = Identifier(it)) }
