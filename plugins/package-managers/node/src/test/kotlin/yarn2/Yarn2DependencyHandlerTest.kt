/*
 * Copyright (C) 2025 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.packagemanagers.node.yarn2

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.TestConfiguration
import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

import org.ossreviewtoolkit.plugins.packagemanagers.node.ModuleInfoResolver

class Yarn2DependencyHandlerTest : WordSpec({
    "Locator.parse()" should {
        "work for patched packages" {
            val locator = Locator.parse(
                "resolve@patch:resolve@npm%3A1.22.8#optional!builtin<compat/resolve>::version=1.22.8&hash=c3c19d"
            )

            locator.moduleName shouldBe "resolve"
            locator.remainder shouldBe
                "patch:resolve@npm%3A1.22.8#optional!builtin<compat/resolve>::version=1.22.8&hash=c3c19d"
        }

        "work for a package with a scope" {
            val locator = Locator.parse("@failing/package-with-lightningcss@workspace:packages/spark")

            locator.moduleName shouldBe "@failing/package-with-lightningcss"
            locator.remainder shouldBe "workspace:packages/spark"
        }
    }

    "Locator.isProject" should {
        "work for a workspace package" {
            val locator = Locator.parse("@failing/package-with-lightningcss@workspace:packages/spark")

            locator.isProject shouldBe true
        }

        "work for a virtual workspace package" {
            val locator = Locator.parse(
                "@failing/package-with-lightningcss@virtual:f87a972e7ee54256c6d8f979d7f3914b32522893226eba595e4ef" +
                    "e4ecc641a239c6d88e01eccc6f32db30829d6ac493bfc98cb406a9b0d6059ee4112c084" +
                    "3da9#workspace:packages/spark"
            )

            locator.isProject shouldBe true
        }
    }

    "Locator.isVirtual" should {
        "return true for a virtual npm package" {
            val locator = Locator.parse(
                "cookie@virtual:abc123def456abc123def456abc123def456abc123def456abc123def456abc123de#npm:1.0.2"
            )

            locator.isVirtual shouldBe true
        }

        "return false for a real npm package" {
            Locator.parse("cookie@npm:1.0.2").isVirtual shouldBe false
        }

        "return false for a workspace project" {
            Locator.parse("myapp@workspace:.").isVirtual shouldBe false
        }

        "return false for a virtual workspace project" {
            val locator = Locator.parse(
                "@failing/package-with-lightningcss@virtual:f87a972e7ee54256c6d8f979d7f3914b32522893226eba595e4ef" +
                    "e4ecc641a239c6d88e01eccc6f32db30829d6ac493bfc98cb406a9b0d6059ee4112c084" +
                    "3da9#workspace:packages/spark"
            )

            locator.isVirtual shouldBe false
        }
    }

    "packageInfoFor()" should {
        "resolve a dependency via its realLocator" {
            val info = packageInfo("cookie@npm:1.0.2", "1.0.2")
            val handler = handlerWith(mapOf("cookie@npm:1.0.2" to info))

            handler.packageInfoFor(dep("cookie@npm:1.0.2")) shouldBe info
        }

        "resolve a virtual dependency via its realLocator" {
            val info = packageInfo("cookie@npm:1.0.2", "1.0.2")
            val virtualLocator =
                "cookie@virtual:abc123def456abc123def456abc123def456abc123def456abc123def456abc123de#npm:1.0.2"
            val handler = handlerWith(mapOf("cookie@npm:1.0.2" to info))

            handler.packageInfoFor(dep(virtualLocator)) shouldBe info
        }

        "use the virtual package fallback when the realLocator is not in the map" {
            // This handles virtual packages whose children.version was overridden by Yarn's resolutions feature.
            // The virtual locator encodes version 1.0.2, but children.version reflects the resolved version 1.1.1.
            val virtualLocator =
                "cookie@virtual:abc123def456abc123def456abc123def456abc123def456abc123def456abc123de#npm:1.0.2"
            val virtualInfo = packageInfo(virtualLocator, "1.1.1")
            val resolvedInfo = packageInfo("cookie@npm:1.1.1", "1.1.1")
            val handler = handlerWith(
                mapOf(
                    virtualLocator to virtualInfo,
                    "cookie@npm:1.1.1" to resolvedInfo
                )
            )

            handler.packageInfoFor(dep(virtualLocator)) shouldBe resolvedInfo
        }

        "use the module name fallback when only a different version is installed" {
            // This handles the case where Yarn's resolutions feature causes a non-virtual dependency locator
            // to reference a version that is not present in the map.
            val resolvedInfo = packageInfo("cookie@npm:1.1.1", "1.1.1")
            val handler = handlerWith(mapOf("cookie@npm:1.1.1" to resolvedInfo))

            handler.packageInfoFor(dep("cookie@npm:1.0.2")) shouldBe resolvedInfo
        }

        "ignore virtual packages in the module name fallback" {
            val virtualLocator =
                "cookie@virtual:abc123def456abc123def456abc123def456abc123def456abc123def456abc123de#npm:1.1.1"
            val resolvedInfo = packageInfo("cookie@npm:1.1.1", "1.1.1")
            val virtualInfo = packageInfo(virtualLocator, "1.1.1")
            val handler = handlerWith(
                mapOf(
                    "cookie@npm:1.1.1" to resolvedInfo,
                    virtualLocator to virtualInfo
                )
            )

            handler.packageInfoFor(dep("cookie@npm:1.0.2")) shouldBe resolvedInfo
        }

        "throw when no entry for the module is found" {
            val handler = handlerWith(mapOf("other@npm:1.0.0" to packageInfo("other@npm:1.0.0", "1.0.0")))

            val exception = shouldThrow<IllegalStateException> {
                handler.packageInfoFor(dep("cookie@npm:1.0.2"))
            }

            exception.message shouldContain "cookie"
        }

        "throw when multiple real versions of the module are found" {
            val handler = handlerWith(
                mapOf(
                    "cookie@npm:1.0.0" to packageInfo("cookie@npm:1.0.0", "1.0.0"),
                    "cookie@npm:1.1.1" to packageInfo("cookie@npm:1.1.1", "1.1.1")
                )
            )

            val exception = shouldThrow<IllegalStateException> {
                handler.packageInfoFor(dep("cookie@npm:1.0.2"))
            }

            exception.message shouldContain "2"
            exception.message shouldContain "cookie"
        }
    }
})

/**
 * Create a minimal [PackageInfo] for the given [locator] and [version].
 */
private fun packageInfo(locator: String, version: String, deps: List<PackageInfo.Dependency> = emptyList()) =
    PackageInfo(
        value = locator,
        children = PackageInfo.Children(version = version, dependencies = deps)
    )

/**
 * Create a [PackageInfo.Dependency] with the given [locator]. The descriptor is set to a dummy value.
 */
private fun dep(locator: String) = PackageInfo.Dependency(descriptor = "dummy", locator = locator)

/**
 * Create a [Yarn2DependencyHandler] with the given [packageInfoForLocator] map set via
 * [Yarn2DependencyHandler.setContext].
 */
private fun TestConfiguration.handlerWith(packageInfoForLocator: Map<String, PackageInfo>): Yarn2DependencyHandler {
    val workingDir = tempdir()
    val resolver = ModuleInfoResolver { _, _ -> emptySet() }
    resolver.workingDir = workingDir
    return Yarn2DependencyHandler(resolver).apply { setContext(workingDir, emptyMap(), packageInfoForLocator) }
}
