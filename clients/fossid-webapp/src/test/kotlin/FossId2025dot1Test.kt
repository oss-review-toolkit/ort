/*
 * Copyright (C) 2025 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.clients.fossid

import com.fasterxml.jackson.module.kotlin.readValue

import io.kotest.core.spec.style.StringSpec

import org.ossreviewtoolkit.clients.fossid.model.result.Snippet

class FossId2025dot1Test : StringSpec({
    "Snippet model can be deserialized" {
        val responseJson = """
            {
                "id": "30",
                "created": "2025-10-14 14:31:10",
                "scan_id": "49",
                "scan_file_id": "112254",
                "file_id": "63380",
                "match_type": "partial",
                "reason": "",
                "author": "someAuthor",
                "artifact": "adbc",
                "version": "0.2.4-rc0",
                "purl": "pkg:github/aaa/bbb@0.2.4-rc0",
                "artifact_license": "Apache-2.0",
                "artifact_license_category": "PERMISSIVE",
                "release_date": "2024-03-29 00:00:00",
                "mirror": "asdf123aaaaa",
                "file": "3rd_party/example/CONTRIBUTING.md",
                "file_license": "Apache-2.0",
                "url": "https://github.com/oss-review-toolkit/example/archive/v0.2.4-rc0.tar.gz",
                "hits": "10 (100%)",
                "size": "13249",
                "updated": "2025-10-14 14:31:10",
                "cpe": null,
                "score": "1",
                "match_file_id": "cae2442400f7293382f118cb00000000",
                "classification": null,
                "highlighting": "",
                "projectscan_type": ""
            }
        """.trimIndent()

        FossIdRestService.JSON_MAPPER.readValue<Snippet>(responseJson)
    }
})
