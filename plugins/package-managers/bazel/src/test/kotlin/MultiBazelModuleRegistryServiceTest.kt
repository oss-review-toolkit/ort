/*
 * Copyright (C) 2024 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.bazel

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll

import java.io.File

import org.ossreviewtoolkit.clients.bazelmoduleregistry.BazelModuleRegistryService
import org.ossreviewtoolkit.clients.bazelmoduleregistry.DEFAULT_URL
import org.ossreviewtoolkit.clients.bazelmoduleregistry.LocalBazelModuleRegistryService
import org.ossreviewtoolkit.clients.bazelmoduleregistry.ModuleMetadata
import org.ossreviewtoolkit.clients.bazelmoduleregistry.ModuleSourceInfo
import org.ossreviewtoolkit.clients.bazelmoduleregistry.RemoteBazelModuleRegistryService

class MultiBazelModuleRegistryServiceTest : WordSpec({
    beforeTest {
        mockkObject(LocalBazelModuleRegistryService, RemoteBazelModuleRegistryService)
    }

    afterTest {
        unmockkAll()
    }

    "getModuleMetadata" should {
        "throw an exception if no metadata can be obtained from all registries" {
            val projectDir = tempdir()
            val mockRegistries = MockRegistryServices.create(projectDir)
            mockRegistries.prepareFailedMetadata()

            val multiRegistry = MultiBazelModuleRegistryService.create(registryUrls, projectDir)

            shouldThrow<IllegalArgumentException> {
                multiRegistry.getModuleMetadata(PACKAGE_NAME)
            }
        }

        "return the metadata from the first registry that contains it" {
            val projectDir = tempdir()
            val mockRegistries = MockRegistryServices.create(projectDir)
            val metadata = mockk<ModuleMetadata>()
            mockRegistries.prepareSuccessMetadata(1, metadata)

            val multiRegistry = MultiBazelModuleRegistryService.create(registryUrls, projectDir)

            multiRegistry.getModuleMetadata(PACKAGE_NAME) shouldBe metadata
        }

        "fall back to the default registry if no metadata can be obtained from the other registries" {
            val projectDir = tempdir()
            val mockRegistries = MockRegistryServices.create(projectDir)
            val metadata = mockk<ModuleMetadata>()
            mockRegistries.prepareSuccessMetadata(3, metadata)

            val multiRegistry = MultiBazelModuleRegistryService.create(registryUrls, projectDir)

            multiRegistry.getModuleMetadata(PACKAGE_NAME) shouldBe metadata
        }
    }

    "getModuleSourceInfo" should {
        "throw an exception if no source info can be obtained from all registries" {
            val projectDir = tempdir()
            val mockRegistries = MockRegistryServices.create(projectDir)
            mockRegistries.prepareFailedSourceInfo()

            val multiRegistry = MultiBazelModuleRegistryService.create(registryUrls, projectDir)

            shouldThrow<IllegalArgumentException> {
                multiRegistry.getModuleSourceInfo(PACKAGE_NAME, PACKAGE_VERSION)
            }
        }

        "return the source info from the first registry that contains it" {
            val projectDir = tempdir()
            val mockRegistries = MockRegistryServices.create(projectDir)
            val sourceInfo = mockk<ModuleSourceInfo>()
            mockRegistries.prepareSuccessModuleInfo(1, sourceInfo)

            val multiRegistry = MultiBazelModuleRegistryService.create(registryUrls, projectDir)

            multiRegistry.getModuleSourceInfo(PACKAGE_NAME, PACKAGE_VERSION) shouldBe sourceInfo
        }

        "fall back to the default registry if no source info can be obtained from the other registries" {
            val projectDir = tempdir()
            val mockRegistries = MockRegistryServices.create(projectDir)
            val sourceInfo = mockk<ModuleSourceInfo>()
            mockRegistries.prepareSuccessModuleInfo(3, sourceInfo)

            val multiRegistry = MultiBazelModuleRegistryService.create(registryUrls, projectDir)

            multiRegistry.getModuleSourceInfo(PACKAGE_NAME, PACKAGE_VERSION) shouldBe sourceInfo
        }
    }
})

/** Name of a test package. */
private const val PACKAGE_NAME = "test-package"

/** Version of a test package. */
private const val PACKAGE_VERSION = "0.8.15"

/** A list of URLs for local and remote test registries. */
private val registryUrls = listOf(
    "file://local/registry/url",
    "https://bazel-remote.example.com",
    "file://%workspace%/registry"
)

private class MockRegistryServices(
    val registryServices: List<BazelModuleRegistryService>
) {
    init {
        prepareGetUrls()
    }

    companion object {
        /** An exception thrown by mock registries to simulate a failure. */
        private val testException = Exception("Test exception: Registry invocation failed.")

        /**
         * Create an instance of [MockRegistryServices] with mocks for the test registry URLs. The factory methods
         * of the local and remote service implementations have been prepared to return the mock instances.
         */
        fun create(projectDir: File): MockRegistryServices {
            val localRegistry1 = mockk<LocalBazelModuleRegistryService>()
            val localRegistry2 = mockk<LocalBazelModuleRegistryService>()
            val remoteRegistry = mockk<RemoteBazelModuleRegistryService>()
            val centralRegistry = mockk<RemoteBazelModuleRegistryService>()

            every {
                LocalBazelModuleRegistryService.create(any(), projectDir)
            } returns null
            every {
                LocalBazelModuleRegistryService.create(registryUrls[0], projectDir)
            } returns localRegistry1
            every {
                LocalBazelModuleRegistryService.create(registryUrls[2], projectDir)
            } returns localRegistry2
            every {
                RemoteBazelModuleRegistryService.create(registryUrls[1])
            } returns remoteRegistry
            every {
                RemoteBazelModuleRegistryService.create(DEFAULT_URL)
            } returns centralRegistry

            return MockRegistryServices(listOf(localRegistry1, localRegistry2, remoteRegistry, centralRegistry))
        }

        /**
         * Prepare the given [services] mocks to expect a query for module metadata and to fail with a test exception.
         */
        private fun prepareFailedMetadata(services: Collection<BazelModuleRegistryService>) {
            services.forEach { service ->
                coEvery { service.getModuleMetadata(PACKAGE_NAME) } throws testException
            }
        }

        /**
         * Prepare the given [services] mocks to expect a query for module source info and to fail with a test
         * exception.
         */
        fun prepareFailedSourceInfo(services: Collection<BazelModuleRegistryService>) {
            services.forEach { service ->
                coEvery { service.getModuleSourceInfo(PACKAGE_NAME, PACKAGE_VERSION) } throws testException
            }
        }
    }

    /**
     * Prepare the mock registries to return a URL if requested.
     */
    fun prepareGetUrls() {
        registryServices.forEach { service ->
            every { service.urls } returns listOf("")
        }
    }

    /**
     * Prepare the mock registries to expect a query for module metadata and to fail with a test exception.
     */
    fun prepareFailedMetadata() {
        prepareFailedMetadata(registryServices)
    }

    /**
     * Prepare the mock registries to expect a query for module source info and to fail with a test exception.
     */
    fun prepareFailedSourceInfo() {
        prepareFailedSourceInfo(registryServices)
    }

    /**
     * Prepare the mock registries to expect a query for module metadata that will eventually succeed. The registry
     * with the given [index] is configured to return the given [metadata].
     */
    fun prepareSuccessMetadata(index: Int, metadata: ModuleMetadata) {
        prepareFailedMetadata(registryServices.take(index))
        coEvery { registryServices[index].getModuleMetadata(PACKAGE_NAME) } returns metadata
    }

    /**
     * Prepare the mock registries to expect a query for module source info that will eventually succeed. The registry
     * with the given [index] is configured to return the given [info].
     */
    fun prepareSuccessModuleInfo(index: Int, info: ModuleSourceInfo) {
        prepareFailedSourceInfo(registryServices.take(index))
        coEvery { registryServices[index].getModuleSourceInfo(PACKAGE_NAME, PACKAGE_VERSION) } returns info
    }
}
