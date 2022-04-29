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

package org.ossreviewtoolkit.scanner.storages

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.result.shouldBeSuccess

import java.time.Instant

import org.ossreviewtoolkit.clients.clearlydefined.ClearlyDefinedService
import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.HashAlgorithm
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.ScannerDetails
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.ClearlyDefinedStorageConfiguration

class ClearlyDefinedStorageFunTest : StringSpec({
    val storage = ClearlyDefinedStorage(ClearlyDefinedStorageConfiguration(ClearlyDefinedService.Server.PRODUCTION.url))

    "Scan results for ScanCode 3.0.2 should be read correctly" {
        val id = Identifier("Maven:com.vdurmont:semver4j:3.1.0")
        val result = storage.read(id)

        result.shouldBeSuccess {
            it shouldContain ScanResult(
                provenance = ArtifactProvenance(
                    sourceArtifact = RemoteArtifact(
                        url = "https://search.maven.org/remotecontent" +
                                "?filepath=com/vdurmont/semver4j/3.1.0/semver4j-3.1.0-sources.jar",
                        hash = Hash(
                            value = "0de1248f09dfe8df3b021c84e0642ee222cceb13",
                            algorithm = HashAlgorithm.SHA1
                        )
                    )
                ),
                scanner = ScannerDetails(
                    name = "ScanCode",
                    version = "3.0.2",
                    configuration = "input /tmp/cd-2rGiCR --classify true --copyright true --email true " +
                            "--generated true --info true --is-license-text true --json-pp /tmp/cd-0EjTZ7 " +
                            "--license true --license-clarity-score true --license-diag true --license-text true " +
                            "--package true --processes 2 --strip-root true --summary true --summary-key-files true " +
                            "--timeout 1000.0 --url true"
                ),
                summary = ScanSummary(
                    startTime = Instant.parse("2020-02-14T00:36:14.000335513Z"),
                    endTime = Instant.parse("2020-02-14T00:36:37.000492119Z"),
                    packageVerificationCode = "",
                    licenseFindings = sortedSetOf(
                        LicenseFinding(
                            license = "MIT",
                            location = TextLocation(
                                path = "META-INF/maven/com.vdurmont/semver4j/pom.xml",
                                startLine = 30,
                                endLine = 31
                            ),
                            score = 60.87f
                        )
                    ),
                    copyrightFindings = sortedSetOf(),
                    issues = emptyList()
                )
            )
        }
    }

    "Scan results for ScanCode 30.1.0 should be read correctly" {
        val id = Identifier("Maven:com.sksamuel.hoplite:hoplite-core:2.1.3")
        val result = storage.read(id)

        result.shouldBeSuccess {
            it shouldContain ScanResult(
                provenance = RepositoryProvenance(
                    vcsInfo = VcsInfo(
                        type = VcsType.GIT,
                        url = "https://github.com/sksamuel/hoplite/tree/b3bf5d7bd3814cb7576091acfecd097cb3a79e72",
                        revision = "b3bf5d7bd3814cb7576091acfecd097cb3a79e72"
                    ),
                    resolvedRevision = "b3bf5d7bd3814cb7576091acfecd097cb3a79e72"
                ),
                scanner = ScannerDetails(
                    name = "ScanCode",
                    version = "30.1.0",
                    configuration = "input /tmp/cd-5bxzho --classify true --copyright true --email true " +
                            "--generated true --info true --is-license-text true --json-pp /tmp/cd-ZLZNNN " +
                            "--license true --license-clarity-score true --license-text true " +
                            "--license-text-diagnostics true --package true --processes 2 --strip-root true " +
                            "--summary true --summary-key-files true --timeout 1000.0 --url true"
                ),
                summary = ScanSummary(
                    startTime = Instant.parse("2022-05-02T07:34:28.000784295Z"),
                    endTime = Instant.parse("2022-05-02T07:34:59.000958218Z"),
                    packageVerificationCode = "",
                    licenseFindings = sortedSetOf(),
                    copyrightFindings = sortedSetOf(),
                    issues = emptyList()
                )
            )
        }
    }
})
