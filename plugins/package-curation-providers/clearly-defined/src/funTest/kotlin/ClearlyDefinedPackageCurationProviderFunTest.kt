/*
 * Copyright (C) 2019 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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
import io.kotest.engine.TestAbortedException
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldBeSingle
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.time.Instant

import org.ossreviewtoolkit.clients.clearlydefined.ClearlyDefinedService.Server
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.utils.spdxexpression.toSpdx

import retrofit2.HttpException

class ClearlyDefinedPackageCurationProviderFunTest : WordSpec({
    val provider by lazy { ClearlyDefinedPackageCurationProviderFactory.create() }

    "The production server" should {
        "return an existing curation for the javax.servlet-api Maven package" {
            // https://clearlydefined.io/definitions/sourcearchive/mavencentral/javax.servlet/javax.servlet-api/3.1.0
            val packages = createPackagesFromIds("Maven:javax.servlet:javax.servlet-api:3.1.0")

            val curations = withIgnoreUnavailable { provider.getCurationsFor(packages) }

            with(curations.shouldBeSingle().data) {
                concludedLicense shouldBe "CDDL-1.0 OR GPL-2.0-only WITH Classpath-exception-2.0".toSpdx()
                publishedAt shouldBe Instant.parse("2013-04-25T00:00:00Z")
            }
        }

        "return an existing curation for the slf4j-log4j12 Maven package" {
            // https://clearlydefined.io/definitions/sourcearchive/mavencentral/org.slf4j/slf4j-log4j12/1.7.30
            val packages = createPackagesFromIds("Maven:org.slf4j:slf4j-log4j12:1.7.30")

            val curations = withIgnoreUnavailable { provider.getCurationsFor(packages) }

            with(curations.shouldBeSingle().data) {
                vcs?.revision shouldBe "0b97c416e42a184ff9728877b461c616187c58f7"
                publishedAt shouldBe Instant.parse("2019-12-16T00:00:00Z")
            }
        }

        "return no curation for a non-existing dummy NPM package" {
            val packages = createPackagesFromIds("NPM:@scope:name:1.2.3")

            val curations = withIgnoreUnavailable { provider.getCurationsFor(packages) }

            curations should beEmpty()
        }
    }

    "Curations" should {
        "get filtered by score" {
            val config = ClearlyDefinedPackageCurationProviderConfig(
                serverUrl = Server.PRODUCTION.apiUrl,
                minTotalLicenseScore = 80
            )
            val customProvider = ClearlyDefinedPackageCurationProvider(config = config)

            // Use an id which is known to have non-empty results from an earlier test.
            val packages = createPackagesFromIds("Maven:org.slf4j:slf4j-log4j12:1.7.30")

            val curations = withIgnoreUnavailable { customProvider.getCurationsFor(packages) }

            curations should beEmpty()
        }

        "be retrieved for packages without a namespace" {
            // https://clearlydefined.io/definitions/npm/npmjs/-/acorn/0.6.0
            val packages = createPackagesFromIds("NPM::acorn:0.6.0")

            val curations = withIgnoreUnavailable { provider.getCurationsFor(packages) }

            with(curations.shouldBeSingle().data) {
                publishedAt shouldBe Instant.parse("2014-06-06T00:00:00Z")
            }
        }
    }
})

private fun createPackagesFromIds(vararg ids: String) = ids.map { Package.EMPTY.copy(id = Identifier(it)) }

/**
 * Skip the test by throwing a `TestAbortedException` in case the service is unavailable for some reasons. Otherwise,
 * execute the [block] as usual.
 *
 * This way the tests can still act as a reminder to re-align the data model in case it deviated, without making noise
 * when network is not available.
 */
private suspend fun <T> withIgnoreUnavailable(block: suspend () -> T): T =
    runCatching {
        block()
    }.getOrElse { e ->
        throw when (e) {
            is SocketTimeoutException -> TestAbortedException()
            is HttpException -> if (e.code() == HttpURLConnection.HTTP_BAD_GATEWAY) TestAbortedException() else e
            else -> e
        }
    }
