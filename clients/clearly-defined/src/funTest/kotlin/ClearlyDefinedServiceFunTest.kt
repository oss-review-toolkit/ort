/*
 * Copyright (C) 2019 Bosch Software Innovations GmbH
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

package org.ossreviewtoolkit.clients.clearlydefined

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.include
import io.kotest.matchers.string.shouldStartWith

import java.io.File

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.decodeFromStream

import org.ossreviewtoolkit.clients.clearlydefined.ClearlyDefinedService.ContributionInfo
import org.ossreviewtoolkit.clients.clearlydefined.ClearlyDefinedService.ContributionPatch
import org.ossreviewtoolkit.clients.clearlydefined.ClearlyDefinedService.Server

class ClearlyDefinedServiceFunTest : WordSpec({
    "A contribution patch" should {
        "be correctly deserialized when using empty facet arrays" {
            // See https://github.com/clearlydefined/curated-data/blob/0b2db78/curations/maven/mavencentral/com.google.code.gson/gson.yaml#L10-L11.
            val curation = File("src/funTest/assets/gson.json").inputStream().use {
                ClearlyDefinedService.JSON.decodeFromStream<Curation>(it)
            }

            curation.described?.facets?.dev.shouldNotBeNull() should beEmpty()
            curation.described?.facets?.tests.shouldNotBeNull() should beEmpty()
        }
    }

    "Downloading a contribution patch" should {
        val coordinates = Coordinates(
            ComponentType.MAVEN,
            Provider.MAVEN_CENTRAL,
            "javax.servlet",
            "javax.servlet-api",
            "3.1.0"
        )

        "return single curation data" {
            val service = ClearlyDefinedService.create(Server.PRODUCTION)

            val curation = service.getCuration(
                coordinates.type,
                coordinates.provider,
                coordinates.namespace.orEmpty(),
                coordinates.name,
                coordinates.revision.orEmpty()
            )

            curation.licensed?.declared shouldBe "CDDL-1.0 OR GPL-2.0-only WITH Classpath-exception-2.0"
        }

        "return bulk curation data" {
            val service = ClearlyDefinedService.create(Server.PRODUCTION)

            val curations = service.getCurations(listOf(coordinates))
            val curation = curations[coordinates]?.curations?.get(coordinates)

            curation?.licensed?.declared shouldBe "CDDL-1.0 OR GPL-2.0-only WITH Classpath-exception-2.0"
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
            val service = ClearlyDefinedService.create(Server.DEVELOPMENT)

            val summary = service.putCuration(ContributionPatch(info, listOf(patch)))

            summary.shouldNotBeNull().run {
                prNumber shouldBeGreaterThan 0
                url shouldStartWith "https://github.com/clearlydefined/curated-data-dev/pull/"
            }
        }
    }
})
