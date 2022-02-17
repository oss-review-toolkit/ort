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

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull

import org.ossreviewtoolkit.clients.fossid.FossIdRestService
import org.ossreviewtoolkit.clients.fossid.FossIdServiceWithVersion
import org.ossreviewtoolkit.clients.fossid.checkResponse
import org.ossreviewtoolkit.clients.fossid.createIgnoreRule
import org.ossreviewtoolkit.clients.fossid.listIgnoreRules
import org.ossreviewtoolkit.clients.fossid.model.rules.IgnoreRule
import org.ossreviewtoolkit.clients.fossid.model.rules.RuleScope
import org.ossreviewtoolkit.clients.fossid.model.rules.RuleType

private const val SCAN_CODE = "semver4j_20210609_144524"

class FossIdRulesTest : StringSpec({
    val server = WireMockServer(
        WireMockConfiguration.options()
            .dynamicPort()
            .usingFilesUnderDirectory("src/test/assets/rules")
    )
    lateinit var service: FossIdServiceWithVersion

    beforeSpec {
        server.start()
        service = FossIdServiceWithVersion.instance(FossIdRestService.create("http://localhost:${server.port()}"))
    }

    afterSpec {
        server.stop()
    }

    beforeTest {
        server.resetAll()
    }

    "Ignore rules can be listed" {
        service.listIgnoreRules("", "", SCAN_CODE).shouldNotBeNull().run {
            checkResponse("list ignore rules")

            data.shouldNotBeNull().run {
                shouldHaveSize(3)

                shouldContainExactly(
                    IgnoreRule(1135, RuleType.DIRECTORY, ".git", 3538, "2021-06-09 14:45:25"),
                    IgnoreRule(2099, RuleType.FILE, "bla", 3538, "2022-01-05 14:59:14"),
                    IgnoreRule(2100, RuleType.EXTENSION, ".txt", 3538, "2022-01-05 14:59:22")
                )
            }
        }
    }

    "An ignore rule can be created" {
        service.createIgnoreRule(
            "",
            "",
            SCAN_CODE,
            RuleType.EXTENSION,
            ".docx",
            RuleScope.SCAN
        ).shouldNotBeNull().checkResponse("create ignore rule")
    }
})
