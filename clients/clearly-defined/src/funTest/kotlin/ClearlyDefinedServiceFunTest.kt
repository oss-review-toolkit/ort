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

package org.ossreviewtoolkit.clients.clearlydefined

import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.TestAbortedException
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.maps.shouldMatchExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.include
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith

import java.net.SocketTimeoutException

import org.ossreviewtoolkit.clients.clearlydefined.ClearlyDefinedService.ContributionInfo
import org.ossreviewtoolkit.clients.clearlydefined.ClearlyDefinedService.ContributionPatch
import org.ossreviewtoolkit.clients.clearlydefined.ClearlyDefinedService.Server

import retrofit2.HttpException

class ClearlyDefinedServiceFunTest : WordSpec({
    val service by lazy { ClearlyDefinedService.create() }

    "Harvests" should {
        "not contain curation data" {
            // https://clearlydefined.io/definitions/sourcearchive/mavencentral/org.slf4j/slf4j-log4j12/1.7.30
            val coordinates = Coordinates(
                type = ComponentType.SOURCE_ARCHIVE,
                provider = Provider.MAVEN_CENTRAL,
                namespace = "org.slf4j",
                name = "slf4j-log4j12",
                revision = "1.7.30"
            )

            val data = service.getLatestHarvestToolData(coordinates, "clearlydefined").withIgnoreUnavailable()

            data.toString() shouldNotContain "0b97c416e42a184ff9728877b461c616187c58f7"
        }
    }

    "A contribution patch" should {
        "be correctly deserialized when using empty facet arrays" {
            // See https://github.com/clearlydefined/curated-data/blob/0b2db78/curations/maven/mavencentral/com.google.code.gson/gson.yaml#L10-L11.
            val gsonJson = javaClass.getResource("/gson.json").readText()
            val curation = ClearlyDefinedService.JSON.decodeFromString<Curation>(gsonJson)

            curation.described?.facets shouldNotBeNull {
                dev.shouldNotBeNull() should beEmpty()
                tests.shouldNotBeNull() should beEmpty()
            }
        }
    }

    "Downloading a contribution patch" should {
        // https://clearlydefined.io/definitions/sourcearchive/mavencentral/javax.servlet/javax.servlet-api/3.1.0
        val coordinates = Coordinates(
            type = ComponentType.MAVEN,
            provider = Provider.MAVEN_CENTRAL,
            namespace = "javax.servlet",
            name = "javax.servlet-api",
            revision = "3.1.0"
        )

        "return single curation data" {
            val curation = runCatching { service.getCuration(coordinates) }.withIgnoreUnavailable()

            with(curation.shouldBeSuccess()) {
                licensed?.declared shouldBe "CDDL-1.0 OR GPL-2.0-only WITH Classpath-exception-2.0"
            }
        }

        "return bulk curation data" {
            val curations = runCatching { service.getCurations(listOf(coordinates)) }.withIgnoreUnavailable()

            with(curations.shouldBeSuccess()) {
                val curation = get(coordinates)?.curations?.get(coordinates)
                curation?.licensed?.declared shouldBe "CDDL-1.0 OR GPL-2.0-only WITH Classpath-exception-2.0"
            }
        }
    }

    "Uploading a contribution patch" should {
        val info = ContributionInfo(
            type = ContributionType.OTHER,
            summary = "summary",
            details = "details",
            resolution = "resolution",
            removedDefinitions = false
        )

        val revisions = mapOf(
            "6.2.3" to Curation(licensed = CurationLicensed(declared = "Apache-1.0"))
        )

        // https://clearlydefined.io/definitions/npm/npmjs/@nestjs/platform-express/6.2.3
        val patch = Patch(
            Coordinates(
                type = ComponentType.NPM,
                provider = Provider.NPM_JS,
                namespace = "@nestjs",
                name = "platform-express",
                revision = "6.2.3"
            ),
            revisions
        )

        "only serialize non-null values" {
            val contributionPatch = ContributionPatch(info, listOf(patch))

            val patchJson = ClearlyDefinedService.JSON.encodeToString(contributionPatch)

            patchJson shouldNot include("null")
        }

        // Disable this test by default as it talks to the real development instance of ClearlyDefined and creates
        // pull-requests at https://github.com/clearlydefined/curated-data-dev.
        "return a summary of the created pull-request".config(enabled = false) {
            val developmentService = ClearlyDefinedService.create(Server.DEVELOPMENT)

            val summary = developmentService.putCuration(ContributionPatch(info, listOf(patch)))

            summary shouldNotBeNull {
                prNumber shouldBeGreaterThan 0
                url shouldStartWith "${Server.DEVELOPMENT.projectUrl}/pull/"
            }
        }
    }

    "Definitions" should {
        "contain defined data properties for single requests" {
            // https://clearlydefined.io/definitions/npm/npmjs/-/eslint-plugin-tsdoc/0.2.2
            val coordinates = Coordinates(
                type = ComponentType.NPM,
                provider = Provider.NPM_JS,
                name = "eslint-plugin-tsdoc",
                revision = "0.2.2"
            )

            val defined = runCatching { service.getDefinition(coordinates) }.withIgnoreUnavailable()

            with(defined.shouldBeSuccess()) {
                files?.get(11)?.facets.shouldNotBeNull() shouldContain "tests"
                described.releaseDate shouldBe "2020-02-22"
            }
        }

        "contain defined data properties for batch requests" {
            // https://clearlydefined.io/definitions/npm/npmjs/-/eslint-plugin-tsdoc/0.2.2
            val coordinates = Coordinates(
                type = ComponentType.NPM,
                provider = Provider.NPM_JS,
                name = "eslint-plugin-tsdoc",
                revision = "0.2.2"
            )

            val definitions = runCatching { service.getDefinitions(listOf(coordinates)) }.withIgnoreUnavailable()

            definitions.shouldBeSuccess().shouldMatchExactly(
                coordinates to { defined ->
                    defined.files?.get(11)?.facets.shouldNotBeNull() shouldContain "tests"
                    defined.described.releaseDate shouldBe "2020-02-22"
                }
            )
        }

        "contain curation data" {
            // https://clearlydefined.io/definitions/sourcearchive/mavencentral/org.slf4j/slf4j-log4j12/1.7.30
            val coordinates = Coordinates(
                type = ComponentType.SOURCE_ARCHIVE,
                provider = Provider.MAVEN_CENTRAL,
                namespace = "org.slf4j",
                name = "slf4j-log4j12",
                revision = "1.7.30"
            )

            val defined = runCatching { service.getDefinition(coordinates) }.withIgnoreUnavailable()

            defined.toString() shouldContain "0b97c416e42a184ff9728877b461c616187c58f7"
        }
    }
})

/**
 * Skip the test by throwing a `TestAbortedException` in case the service is unavailable for some reason. Otherwise,
 * return the [Result] as-is.
 *
 * This way the tests can still act as a reminder to re-align the data model in case it deviated, without making noise
 * when network is not available.
 */
private fun <T> Result<T>.withIgnoreUnavailable(): Result<T> =
    onFailure { e ->
        fun Int.isServerSideError(): Boolean = this / 100 == 5

        throw when (e) {
            is SocketTimeoutException -> TestAbortedException()
            is HttpException -> if (e.code().isServerSideError()) TestAbortedException() else e
            else -> e
        }
    }
