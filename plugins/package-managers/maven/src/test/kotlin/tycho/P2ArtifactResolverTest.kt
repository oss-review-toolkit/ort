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

import io.kotest.core.TestConfiguration
import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempdir
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll

import org.apache.maven.artifact.repository.MavenArtifactRepository
import org.apache.maven.project.MavenProject

import org.eclipse.aether.artifact.DefaultArtifact

import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.HashAlgorithm
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.Severity

class P2ArtifactResolverTest : WordSpec({
    afterEach {
        unmockkAll()
    }

    "collectP2Repositories()" should {
        "collect P2 repositories from the projects found during the build" {
            val repositoryUrl1 = "https://p2.example1.com/repo"
            val repositoryUrl2 = "https://p2.example2.com/repo"
            val repositoryUrl3 = "https://p2.example3.com/repo"
            val project1 = createMavenProject(
                listOf(
                    createRepository("p2", repositoryUrl1),
                    createRepository("p2", repositoryUrl2)
                )
            )
            val project2 = createMavenProject(listOf(createRepository("default", "https://maven.example.com/repo")))
            val project3 = createMavenProject(listOf(createRepository("default", "https://repo1.maven.org/maven2/")))
            val project4 = createMavenProject(listOf(createRepository("p2", repositoryUrl3)))

            val targetHandler = mockk<TargetHandler> {
                every { repositoryUrls } returns emptySet()
            }

            val repositories = P2ArtifactResolver.collectP2Repositories(
                targetHandler,
                listOf(project1, project2, project3, project4)
            )

            repositories should containExactlyInAnyOrder(repositoryUrl1, repositoryUrl2, repositoryUrl3)
        }

        "collect P2 repositories from target files" {
            val targetRepositories = setOf(
                "https://p2.example.com/repo/download.eclipse.org/modeling/tmf/xtext/updates/releases/2.37.0/",
                "https://p2.example.org/repo/download.eclipse.org/modeling/emft/mwe/updates/releases/2.20.0/",
                "https://p2.example.com/repository/download.eclipse.org/releases/2024-12",
                "https://p2.other.example.com/repo/other/test/"
            )
            val targetHandler = mockk<TargetHandler> {
                every { repositoryUrls } returns targetRepositories
            }

            val repositories = P2ArtifactResolver.collectP2Repositories(targetHandler, emptyList())

            repositories shouldContainExactlyInAnyOrder targetRepositories
        }
    }

    "getBinaryArtifactFor()" should {
        "return an empty artifact if no information about the artifact is available" {
            val resolver = createResolver(emptyList())

            val artifact = resolver.getBinaryArtifactFor(testArtifact)

            artifact shouldBe RemoteArtifact.EMPTY
        }

        "return a correct RemoteArtifact" {
            val repositoryContent = P2RepositoryContent(
                REPOSITORY_URL,
                mapOf(P2Identifier(TEST_ARTIFACT_KEY) to TEST_HASH),
                emptySet()
            )

            val resolver = createResolver(listOf(repositoryContent))

            val artifact = resolver.getBinaryArtifactFor(testArtifact)

            artifact.url shouldBe ARTIFACT_URL
            artifact.hash shouldBe TEST_HASH
        }

        "return a correct RemoteArtifact for a Tycho binary artifact" {
            val repositoryContent = P2RepositoryContent(
                REPOSITORY_URL,
                mapOf(P2Identifier(TEST_ARTIFACT_KEY, "binary") to TEST_HASH),
                emptySet()
            )

            val resolver = createResolver(listOf(repositoryContent))

            val artifact = resolver.getBinaryArtifactFor(testArtifact)

            artifact.url shouldBe "$REPOSITORY_URL/binary/${TEST_ARTIFACT_ID}_${TEST_ARTIFACT_VERSION}"
            artifact.hash shouldBe TEST_HASH
        }
    }

    "getSourceArtifactFor()" should {
        "return an empty artifact if no information about the artifact is available" {
            val resolver = createResolver(emptyList())

            val artifact = resolver.getSourceArtifactFor(testArtifact)

            artifact shouldBe RemoteArtifact.EMPTY
        }

        "return a correct RemoteArtifact" {
            val otherHash = Hash("beb48gdd06995b5e3dcd75506g6g78667d958f88", HashAlgorithm.SHA1)
            val sourceArtifactUrl = "$REPOSITORY_URL/plugins/${TEST_ARTIFACT_ID}.source_${TEST_ARTIFACT_VERSION}.jar"
            val repositoryContent = P2RepositoryContent(
                REPOSITORY_URL,
                mapOf(
                    P2Identifier(TEST_ARTIFACT_KEY) to otherHash,
                    P2Identifier("$TEST_ARTIFACT_ID.source:$TEST_ARTIFACT_VERSION") to TEST_HASH
                ),
                emptySet()
            )

            val resolver = createResolver(listOf(repositoryContent))

            val artifact = resolver.getSourceArtifactFor(testArtifact)

            artifact.url shouldBe sourceArtifactUrl
            artifact.hash shouldBe TEST_HASH
        }
    }

    "resolverIssues" should {
        "contain the issues reported by the content loader" {
            val issues = listOf(
                Issue(source = "Tycho", message = "Problem with P2 repository 'repo1'."),
                Issue(source = "source2", message = "some other problem", severity = Severity.WARNING)
            )

            val resolver = createResolver(emptyList(), issues)

            resolver.resolverIssues shouldContainExactlyInAnyOrder issues
        }
    }

    "isFeature()" should {
        "return false if the artifact cannot be resolved" {
            val resolver = createResolver(emptyList())

            val isFeature = resolver.isFeature(testArtifact)

            isFeature shouldBe false
        }

        "return true for an artifact having only the feature classifier" {
            val repositoryContent = P2RepositoryContent(
                REPOSITORY_URL,
                mapOf(P2Identifier(TEST_ARTIFACT_KEY, "org.eclipse.update.feature") to TEST_HASH),
                emptySet()
            )

            val resolver = createResolver(listOf(repositoryContent))
            val isFeature = resolver.isFeature(testArtifact)

            isFeature shouldBe true
        }

        "return false for an artifact with a different classifier" {
            val repositoryContent = P2RepositoryContent(
                REPOSITORY_URL,
                mapOf(P2Identifier(TEST_ARTIFACT_KEY) to TEST_HASH),
                emptySet()
            )

            val resolver = createResolver(listOf(repositoryContent))
            val isFeature = resolver.isFeature(testArtifact)

            isFeature shouldBe false
        }

        "return false for an artifact that includes a feature classifier, but has no matching group ID" {
            val repositoryContent1 = P2RepositoryContent(
                REPOSITORY_URL,
                mapOf(P2Identifier(TEST_ARTIFACT_KEY) to TEST_HASH),
                emptySet()
            )
            val repositoryContent2 = P2RepositoryContent(
                "${REPOSITORY_URL}_other",
                mapOf(P2Identifier(TEST_ARTIFACT_KEY, "org.eclipse.update.feature") to TEST_HASH),
                emptySet()
            )

            val resolver = createResolver(listOf(repositoryContent1, repositoryContent2))
            val isFeature = resolver.isFeature(testArtifact)

            isFeature shouldBe false
        }

        "return true for an artifact that includes a feature classifier and has a matching group ID" {
            val repositoryContent1 = P2RepositoryContent(
                REPOSITORY_URL,
                mapOf(P2Identifier(TEST_ARTIFACT_KEY) to TEST_HASH),
                emptySet()
            )
            val repositoryContent2 = P2RepositoryContent(
                "${REPOSITORY_URL}_other",
                mapOf(P2Identifier(TEST_ARTIFACT_KEY, "org.eclipse.update.feature") to TEST_HASH),
                emptySet()
            )
            val featureGroupIds = listOf(
                "p2.eclipse.feature",
                "feature.eclipse.p2",
                "some.feature.group"
            )
            val resolver = createResolver(listOf(repositoryContent1, repositoryContent2))

            featureGroupIds.forAll { groupId ->
                val featureArtifact = DefaultArtifact(groupId, TEST_ARTIFACT_ID, "jar", TEST_ARTIFACT_VERSION)
                val isFeature = resolver.isFeature(featureArtifact)

                isFeature shouldBe true
            }
        }

        "return false for an artifact that includes 'feature' in its group ID, but not as a component" {
            val repositoryContent1 = P2RepositoryContent(
                REPOSITORY_URL,
                mapOf(P2Identifier(TEST_ARTIFACT_KEY) to TEST_HASH),
                emptySet()
            )
            val repositoryContent2 = P2RepositoryContent(
                "${REPOSITORY_URL}_other",
                mapOf(P2Identifier(TEST_ARTIFACT_KEY, "org.eclipse.update.feature") to TEST_HASH),
                emptySet()
            )
            val artifact = DefaultArtifact("my.crazy-features.group", TEST_ARTIFACT_ID, "jar", TEST_ARTIFACT_VERSION)

            val resolver = createResolver(listOf(repositoryContent1, repositoryContent2))
            val isFeature = resolver.isFeature(artifact)

            isFeature shouldBe false
        }

        "return true for an artifact referencing a feature declared in a target file" {
            val targetHandler = TargetHandler(
                featureIds = setOf(TEST_ARTIFACT_ID),
                repositoryUrls = emptySet(),
                mavenDependencies = emptyMap()
            )

            val resolver = createResolver(emptyList(), targetHandler = targetHandler)
            val isFeature = resolver.isFeature(testArtifact)

            isFeature shouldBe true
        }
    }

    "isBinary()" should {
        "return false if the artifact cannot be resolved" {
            val resolver = createResolver(emptyList())

            val isBinary = resolver.isBinary(testArtifact)

            isBinary shouldBe false
        }

        "return false if the artifact does not have the binary classifier" {
            val repositoryContent = P2RepositoryContent(
                REPOSITORY_URL,
                mapOf(P2Identifier(TEST_ARTIFACT_KEY, "org.eclipse.update.feature") to TEST_HASH),
                emptySet()
            )

            val resolver = createResolver(listOf(repositoryContent))
            val isBinary = resolver.isBinary(testArtifact)

            isBinary shouldBe false
        }

        "return true if the artifact has the binary classifier" {
            val repositoryContent = P2RepositoryContent(
                REPOSITORY_URL,
                mapOf(P2Identifier(TEST_ARTIFACT_KEY, "binary") to TEST_HASH),
                emptySet()
            )

            val resolver = createResolver(listOf(repositoryContent))
            val isBinary = resolver.isBinary(testArtifact)

            isBinary shouldBe true
        }
    }
})

/** The ID of a test artifact. */
private const val TEST_ARTIFACT_ID = "org.some.component"

/** The version of the test artifact. */
private const val TEST_ARTIFACT_VERSION = "0.0.1"

/** The internal key of the test artifact. */
private const val TEST_ARTIFACT_KEY = "$TEST_ARTIFACT_ID:$TEST_ARTIFACT_VERSION"

/** A test artifact to be looked up. */
private val testArtifact = DefaultArtifact("test-group", TEST_ARTIFACT_ID, "jar", TEST_ARTIFACT_VERSION)

/** URL of a test repository. */
private const val REPOSITORY_URL = "https://p2.example.com/repo"

/** The URL of the test artifact in the test repository. */
private const val ARTIFACT_URL = "$REPOSITORY_URL/plugins/${TEST_ARTIFACT_ID}_${TEST_ARTIFACT_VERSION}.jar"

/** A test hash value. */
private val TEST_HASH = Hash("ada37fcc95884a4d2cbc64495f5f67556c847e77", HashAlgorithm.SHA1)

/**
 * Create a mock [MavenArtifactRepository] with the given [layout] and [url].
 */
@Suppress("DEPRECATION") // ArtifactRepositoryLayout is deprecated.
private fun createRepository(layout: String, url: String): MavenArtifactRepository {
    val repositoryLayout = mockk<org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout> {
        every { id } returns layout
    }

    return mockk {
        every { this@mockk.layout } returns repositoryLayout
        every { this@mockk.url } returns url
    }
}

/**
 * Create a mock [MavenProject] that returns the given [repositories].
 */
private fun createMavenProject(repositories: List<MavenArtifactRepository>): MavenProject =
    mockk {
        every { remoteArtifactRepositories } returns repositories
    }

/**
 * Create a [P2ArtifactResolver] object that is initialized with the given [contents] and [issues]. Optionally,
 * a [targetHandler] can be provided; otherwise, a dummy instance is created.
 */
private fun TestConfiguration.createResolver(
    contents: List<P2RepositoryContent>,
    issues: List<Issue> = emptyList(),
    targetHandler: TargetHandler? = null
): P2ArtifactResolver {
    val repositoryUrl = "https://p2.example1.com/repo"
    val repository = createRepository("p2", repositoryUrl)

    mockkObject(P2RepositoryContentLoader)
    every {
        P2RepositoryContentLoader.loadAllRepositoryContents(setOf(repositoryUrl))
    } returns (contents to issues)

    val handler = targetHandler ?: TargetHandler.create(tempdir())

    return P2ArtifactResolver.create(handler, listOf(createMavenProject(listOf(repository))))
}
