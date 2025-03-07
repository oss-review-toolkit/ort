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

package org.ossreviewtoolkit.plugins.packagemanagers.maven.tycho

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beInstanceOf

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify

import org.codehaus.plexus.DefaultPlexusContainer
import org.codehaus.plexus.logging.Logger
import org.codehaus.plexus.logging.LoggerManager

import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.artifact.DefaultArtifact

import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.HashAlgorithm
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.plugins.packagemanagers.maven.utils.MavenLogger

class P2ArtifactTrackerTest : WordSpec({
    "install()" should {
        "install a log manager that handles other components correctly" {
            val (_, logManager) = getInstanceWithLoggerManager()

            val logger = logManager.getLoggerForComponent("org.eclipse.tycho.p2maven.transport.OtherComponent")

            logger should beInstanceOf<MavenLogger>()
        }
    }

    "getBinaryArtifactFor()" should {
        "return an empty artifact if no information about the artifact is available" {
            val (tracker, _) = getInstanceWithLogger()

            tracker.getBinaryArtifactFor(testArtifact) shouldBe RemoteArtifact.EMPTY
        }

        "return an artifact with the correct source URL" {
            val (tracker, logger) = getInstanceWithLogger()
            logger.logDownload(ARTIFACT_URL)

            val artifact = tracker.getBinaryArtifactFor(testArtifact)

            artifact.url shouldBe ARTIFACT_URL
        }

        "return an artifact with a correct SHA-512 hash" {
            val hashValue = "ada37fcc95884a4d2cbc64495f5f67556c847e7724e26ccfbb15cc42a476436fa54b5d4fd4d9ed340241" +
                "d848999d415e1cff07045d9e97d451c16aeed4911045"
            val properties = mapOf(
                "download.checksum.sha-512" to hashValue,
                "download.checksum.sha-256" to hashValue.take(64),
                "download.checksum.sha-1" to hashValue.take(40)
            )
            val repositoryHelper = mockk<LocalRepositoryHelper> {
                every { p2Properties(testArtifact) } returns properties
            }

            val (tracker, logger) = getInstanceWithLogger(repositoryHelper)
            logger.logDownload(ARTIFACT_URL)

            val artifact = tracker.getBinaryArtifactFor(testArtifact)

            artifact.hash shouldBe Hash(hashValue, HashAlgorithm.SHA512)
        }

        "return an artifact with a correct SHA-256 hash if no stronger hash is available" {
            val hashValue = "adf46d5e34940bdf148ecdd26a9ee8eea94496a72034ff7141066b3eea5c4e9d"
            val properties = mapOf(
                "download.checksum.sha-256" to hashValue,
                "download.checksum.sha-1" to hashValue.take(40)
            )
            val repositoryHelper = mockk<LocalRepositoryHelper> {
                every { p2Properties(testArtifact) } returns properties
            }

            val (tracker, logger) = getInstanceWithLogger(repositoryHelper)
            logger.logDownload(ARTIFACT_URL)

            val artifact = tracker.getBinaryArtifactFor(testArtifact)

            artifact.hash shouldBe Hash(hashValue, HashAlgorithm.SHA256)
        }

        "return an artifact with a correct SHA-1 hash if no stronger hash is available" {
            val hashValue = "073d7b3086e14beb604ced229c302feff6449723"
            val properties = mapOf("download.checksum.sha-1" to hashValue)
            val repositoryHelper = mockk<LocalRepositoryHelper> {
                every { p2Properties(testArtifact) } returns properties
            }

            val (tracker, logger) = getInstanceWithLogger(repositoryHelper)
            logger.logDownload(ARTIFACT_URL)

            val artifact = tracker.getBinaryArtifactFor(testArtifact)

            artifact.hash shouldBe Hash(hashValue, HashAlgorithm.SHA1)
        }

        "return an artifact with an empty hash if no hash property is available" {
            val repositoryHelper = mockk<LocalRepositoryHelper> {
                every { p2Properties(testArtifact) } returns emptyMap()
            }

            val (tracker, logger) = getInstanceWithLogger(repositoryHelper)
            logger.logDownload(ARTIFACT_URL)

            val artifact = tracker.getBinaryArtifactFor(testArtifact)

            artifact.hash shouldBe Hash.NONE
        }

        "return an artifact with an empty hash if the file with properties is not available" {
            val repositoryHelper = mockk<LocalRepositoryHelper> {
                every { p2Properties(testArtifact) } returns null
            }

            val (tracker, logger) = getInstanceWithLogger(repositoryHelper)
            logger.logDownload(ARTIFACT_URL)

            val artifact = tracker.getBinaryArtifactFor(testArtifact)

            artifact.hash shouldBe Hash.NONE
        }
    }

    "getSourceArtifactFor()" should {
        "return an empty artifact if no information about the artifact is available" {
            val (tracker, _) = getInstanceWithLogger()

            tracker.getSourceArtifactFor(testArtifact) shouldBe RemoteArtifact.EMPTY
        }

        "return an artifact with the information available" {
            val sourceArtifactId = "org.some.component.source"
            val sourceArtifactUrl = "$REPOSITORY_URL/plugins/${sourceArtifactId}_${testArtifact.version}.jar"
            val hashValue = "073d7b3086e14beb604ced229c302feff6449723"
            val properties = mapOf("download.checksum.sha-1" to hashValue)
            val repositoryHelper = mockk<LocalRepositoryHelper> {
                every { p2Properties(any()) } returns properties
            }

            val (tracker, logger) = getInstanceWithLogger(repositoryHelper)
            logger.logDownload(sourceArtifactUrl)

            val artifact = tracker.getSourceArtifactFor(testArtifact)

            artifact.url shouldBe sourceArtifactUrl
            artifact.hash shouldBe Hash(hashValue, HashAlgorithm.SHA1)

            val slotArtifact = slot<Artifact>()
            verify {
                repositoryHelper.p2Properties(capture(slotArtifact))
            }

            slotArtifact.captured.artifactId shouldBe sourceArtifactId
        }
    }
})

/** A test artifact to be looked up. */
private val testArtifact = DefaultArtifact("test-group", "org.some.component", "jar", "0.0.1")

/** URL of a test repository. */
private const val REPOSITORY_URL = "https://p2.example.com/repo"

/** The URL of the test artifact in the test repository. */
private val ARTIFACT_URL = "$REPOSITORY_URL/plugins/${testArtifact.artifactId}_${testArtifact.version}.jar"

/**
 * Create a test [P2ArtifactTracker] instance that uses the given optional [repositoryHelper] and install it into a
 * container. Return this instance and the special [LoggerManager] that is used to gather logs about artifact
 * downloads.
 */
private fun getInstanceWithLoggerManager(
    repositoryHelper: LocalRepositoryHelper = mockk()
): Pair<P2ArtifactTracker, LoggerManager> {
    val container = mockk<DefaultPlexusContainer> {
        every { loggerManager = any() } returns Unit
    }

    val tracker = P2ArtifactTracker(repositoryHelper)
    P2ArtifactTracker.install(tracker, container)

    val slotLoggerManager = slot<LoggerManager>()
    verify {
        container.loggerManager = capture(slotLoggerManager)
    }

    return tracker to slotLoggerManager.captured
}

/**
 * Create a test [P2ArtifactTracker] instance that uses the given optional [repositoryHelper] and install it into a
 * container. Return this instance and the special [Logger] that is used to gather logs about artifact downloads.
 * By writing messages to this logger, the tracker instance is populated.
 */
private fun getInstanceWithLogger(
    repositoryHelper: LocalRepositoryHelper = mockk(relaxed = true)
): Pair<P2ArtifactTracker, Logger> {
    val (tracker, loggerManager) = getInstanceWithLoggerManager(repositoryHelper)

    return tracker to loggerManager.getLoggerForComponent(P2ArtifactTracker.TYCHO_TRANSPORT_LOGGER)
}

/**
 * Log a message about an artifact being downloaded from the given [artifactUrl].
 */
private fun Logger.logDownload(artifactUrl: String) {
    info("Downloaded from p2: $artifactUrl (42 KB at 123 KB/s)")
}
