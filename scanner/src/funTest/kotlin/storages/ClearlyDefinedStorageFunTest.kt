/*
 * Copyright (C) 2022 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import io.kotest.assertions.retry
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.result.shouldBeSuccess

import java.time.Instant

import kotlin.time.Duration.Companion.seconds

import org.ossreviewtoolkit.clients.clearlydefined.ClearlyDefinedService.Server
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.HashAlgorithm
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.Package
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
    val storage = ClearlyDefinedStorage(ClearlyDefinedStorageConfiguration(Server.PRODUCTION.apiUrl))

    "Scan results for 'semver4j' from ScanCode should be read correctly" {
        val pkg = Package.EMPTY.copy(
            id = Identifier("Maven:com.vdurmont:semver4j:3.1.0"),
            binaryArtifact = RemoteArtifact(
                url = "https://repo1.maven.org/maven2/com/vdurmont/semver4j/3.1.0/semver4j-3.1.0.jar",
                hash = Hash(
                    value = "0de1248f09dfe8df3b021c84e0642ee222cceb13",
                    algorithm = HashAlgorithm.SHA1
                )
            )
        )

        withRetry {
            val results = storage.read(pkg)

            results.shouldBeSuccess {
                it shouldContain ScanResult(
                    provenance = RepositoryProvenance(
                        vcsInfo = VcsInfo(
                            type = VcsType.GIT,
                            url = "https://github.com/vdurmont/semver4j.git",
                            revision = "88912638db3f6112a2b345f1638ced33a0a606e1"
                        ),
                        resolvedRevision = "88912638db3f6112a2b345f1638ced33a0a606e1"
                    ),
                    scanner = ScannerDetails(
                        name = "ScanCode",
                        version = "30.1.0",
                        configuration = "--classify true --copyright true --email true " +
                            "--generated true --info true --is-license-text true --json-pp /tmp/cd-d8WH1p " +
                            "--license true --license-clarity-score true --license-text true " +
                            "--license-text-diagnostics true --package true --processes 2 --strip-root true " +
                            "--summary true --summary-key-files true --timeout 1000.0 --url true"
                    ),
                    summary = ScanSummary.EMPTY.copy(
                        startTime = Instant.parse("2023-09-27T08:28:44.000244665Z"),
                        endTime = Instant.parse("2023-09-27T08:29:22.000369368Z"),
                        licenseFindings = setOf(
                            LicenseFinding(
                                license = "BSD-3-Clause",
                                location = TextLocation(
                                    path = "META-INF/maven/com.vdurmont/semver4j/pom.xml",
                                    startLine = 28,
                                    endLine = 34
                                ),
                                score = 75.0f
                            ),
                            LicenseFinding(
                                license = "MIT",
                                location = TextLocation(
                                    path = "META-INF/maven/com.vdurmont/semver4j/pom.xml",
                                    startLine = 30,
                                    endLine = 31
                                ),
                                score = 83.33f
                            )
                        )
                    )
                )
            }
        }
    }

    "Scan results for 'hoplite-core' from ScanCode should be read correctly" {
        val pkg = Package.EMPTY.copy(
            id = Identifier("Maven:com.sksamuel.hoplite:hoplite-core:2.1.3"),
            binaryArtifact = RemoteArtifact(
                url = "https://repo1.maven.org/maven2/com/sksamuel/hoplite/hoplite-core/2.1.3/hoplite-core-2.1.3.jar",
                hash = Hash(
                    value = "143f3c28ac4987907473fb608bce1fe317663ba8",
                    algorithm = HashAlgorithm.SHA1
                )
            )
        )

        withRetry {
            val results = storage.read(pkg)

            results.shouldBeSuccess {
                it shouldContain ScanResult(
                    provenance = RepositoryProvenance(
                        vcsInfo = VcsInfo(
                            type = VcsType.GIT,
                            url = "https://github.com/sksamuel/hoplite.git",
                            revision = "b3bf5d7bd3814cb7576091acfecd097cb3a79e72"
                        ),
                        resolvedRevision = "b3bf5d7bd3814cb7576091acfecd097cb3a79e72"
                    ),
                    scanner = ScannerDetails(
                        name = "ScanCode",
                        version = "30.1.0",
                        configuration = "--classify true --copyright true --email true " +
                            "--generated true --info true --is-license-text true --json-pp /tmp/cd-ZLZNNN " +
                            "--license true --license-clarity-score true --license-text true " +
                            "--license-text-diagnostics true --package true --processes 2 --strip-root true " +
                            "--summary true --summary-key-files true --timeout 1000.0 --url true"
                    ),
                    summary = ScanSummary.EMPTY.copy(
                        startTime = Instant.parse("2022-05-02T07:34:28.000784295Z"),
                        endTime = Instant.parse("2022-05-02T07:34:59.000958218Z")
                    )
                )
            }
        }
    }

    "Scan results for packages without a namespace should be present" {
        val pkg = Package.EMPTY.copy(
            id = Identifier("NPM::iobroker.eusec:0.9.9"),
            binaryArtifact = RemoteArtifact(
                url = "https://registry.npmjs.org/iobroker.eusec/-/iobroker.eusec-0.9.9.tgz",
                hash = Hash(
                    value = "b3cf4aeefd31f64907cab7ea7a419f1054137a09",
                    algorithm = HashAlgorithm.SHA1
                )
            )
        )

        withRetry {
            val results = storage.read(pkg)

            results.shouldBeSuccess { result ->
                result.map { it.provenance } shouldContain RepositoryProvenance(
                    vcsInfo = VcsInfo(
                        type = VcsType.GIT,
                        url = "https://github.com/bropat/ioBroker.eusec.git",
                        revision = "327b125548c9b806490085a2dacfdfc6e7776803"
                    ),
                    resolvedRevision = "327b125548c9b806490085a2dacfdfc6e7776803"
                )
            }
        }
    }
})

private suspend fun <T> withRetry(f: suspend () -> T): T =
    retry(
        maxRetry = 5,
        timeout = (10 + 20 + 40 + 80 + 160).seconds,
        delay = 10.seconds,
        multiplier = 2,
        f = f
    )
