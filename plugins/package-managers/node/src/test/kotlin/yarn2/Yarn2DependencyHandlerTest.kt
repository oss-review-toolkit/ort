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
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
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

    "Locator.isPatch" should {
        "return true for a compat-patched package" {
            val locator = Locator.parse(
                "resolve@patch:resolve@npm%3A1.22.8#optional!builtin<compat/resolve>::version=1.22.8&hash=c3c19d"
            )

            locator.isPatch shouldBe true
        }

        "return false for a real npm package" {
            Locator.parse("resolve@npm:1.22.8").isPatch shouldBe false
        }

        "return false for a virtual npm package" {
            val locator = Locator.parse(
                "cookie@virtual:abc123def456abc123def456abc123def456abc123def456abc123def456abc123de#npm:1.0.2"
            )

            locator.isPatch shouldBe false
        }
    }

    "packageInfoFor()" should {
        "resolve a dependency via its realLocator" {
            val info = packageInfo("cookie@npm:1.0.2", "1.0.2")
            val handler = handlerWith(mapOf("cookie@npm:1.0.2" to info))

            handler.packageInfoFor(dep("cookie@npm:1.0.2")) shouldBe listOf(info)
        }

        "resolve a virtual dependency via its realLocator" {
            val info = packageInfo("cookie@npm:1.0.2", "1.0.2")
            val virtualLocator =
                "cookie@virtual:abc123def456abc123def456abc123def456abc123def456abc123def456abc123de#npm:1.0.2"
            val handler = handlerWith(mapOf("cookie@npm:1.0.2" to info))

            handler.packageInfoFor(dep(virtualLocator)) shouldBe listOf(info)
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

            handler.packageInfoFor(dep(virtualLocator)) shouldBe listOf(resolvedInfo)
        }

        "use the module name fallback when only a different version is installed" {
            // This handles the case where Yarn's resolutions feature causes a non-virtual dependency locator
            // to reference a version that is not present in the map.
            val resolvedInfo = packageInfo("cookie@npm:1.1.1", "1.1.1")
            val handler = handlerWith(mapOf("cookie@npm:1.1.1" to resolvedInfo))

            handler.packageInfoFor(dep("cookie@npm:1.0.2")) shouldBe listOf(resolvedInfo)
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

            handler.packageInfoFor(dep("cookie@npm:1.0.2")) shouldBe listOf(resolvedInfo)
        }

        "throw when no entry for the module is found" {
            val handler = handlerWith(mapOf("other@npm:1.0.0" to packageInfo("other@npm:1.0.0", "1.0.0")))

            val exception = shouldThrow<IllegalStateException> {
                handler.packageInfoFor(dep("cookie@npm:1.0.2"))
            }

            exception.message shouldContain "cookie"
        }

        "return all candidates when multiple real versions of the module are found" {
            val info100 = packageInfo("cookie@npm:1.0.0", "1.0.0")
            val info111 = packageInfo("cookie@npm:1.1.1", "1.1.1")
            val handler = handlerWith(mapOf("cookie@npm:1.0.0" to info100, "cookie@npm:1.1.1" to info111))

            handler.packageInfoFor(dep("cookie@npm:1.0.2")) shouldContainExactlyInAnyOrder listOf(info100, info111)
        }

        "resolve an ambiguous virtual locator whose real locator is not installed to all candidates" {
            // The real locator derived from the virtual locator is not installed, so the module name fallback has to
            // consider the installed versions, see https://github.com/oss-review-toolkit/ort/issues/12008.
            val virtualLocator =
                "debug@virtual:abc123def456abc123def456abc123def456abc123def456abc123def456abc123de#npm:4.4.1"
            val info327 = packageInfo("debug@npm:3.2.7", "3.2.7")
            val info437 = packageInfo("debug@npm:4.3.7", "4.3.7")
            val handler = handlerWith(
                mapOf(
                    "debug@npm:3.2.7" to info327,
                    "debug@npm:4.3.7" to info437,
                    virtualLocator to packageInfo(virtualLocator, "4.4.1")
                )
            )

            // The descriptor's range matches neither installed version, so all candidates have to be considered.
            val dependency = PackageInfo.Dependency(descriptor = "debug@npm:^4.4.0", locator = virtualLocator)

            handler.packageInfoFor(dependency) shouldContainExactlyInAnyOrder listOf(info327, info437)
        }

        "use the semver range from the descriptor to disambiguate multiple candidates" {
            // Two real versions are installed. The descriptor's range matches only one of them.
            val info100 = packageInfo("cookie@npm:1.0.0", "1.0.0")
            val info200 = packageInfo("cookie@npm:2.0.0", "2.0.0")
            // Descriptor "cookie@npm:^1.0.0" matches 1.0.0 but not 2.0.0.
            val dependency = PackageInfo.Dependency(descriptor = "cookie@npm:^1.0.0", locator = "cookie@npm:1.0.2")
            val handler = handlerWith(mapOf("cookie@npm:1.0.0" to info100, "cookie@npm:2.0.0" to info200))

            handler.packageInfoFor(dependency) shouldBe listOf(info100)
        }

        "return all matching candidates when the semver range still matches multiple candidates" {
            // Both installed versions satisfy the descriptor range.
            val info440 = packageInfo("debug@npm:4.4.0", "4.4.0")
            val info443 = packageInfo("debug@npm:4.4.3", "4.4.3")
            // Descriptor "debug@npm:^4.3.0" matches both 4.4.0 and 4.4.3.
            val dependency = PackageInfo.Dependency(descriptor = "debug@npm:^4.3.0", locator = "debug@npm:4.3.6")
            val handler = handlerWith(mapOf("debug@npm:4.4.0" to info440, "debug@npm:4.4.3" to info443))

            handler.packageInfoFor(dependency) shouldContainExactlyInAnyOrder listOf(info440, info443)
        }

        "widen to all installed versions when the descriptor's range cannot be parsed" {
            val info100 = packageInfo("cookie@npm:1.0.0", "1.0.0")
            val info111 = packageInfo("cookie@npm:1.1.1", "1.1.1")
            val info200 = packageInfo("cookie@npm:2.0.0", "2.0.0")
            // The descriptor's remainder has neither an "npm:" nor a "patch:" prefix, so no range can be extracted.
            val dependency =
                PackageInfo.Dependency(descriptor = "cookie@nonsense:whatever", locator = "cookie@npm:1.0.2")
            val handler = handlerWith(
                mapOf(
                    "cookie@npm:1.0.0" to info100,
                    "cookie@npm:1.1.1" to info111,
                    "cookie@npm:2.0.0" to info200
                )
            )

            handler.packageInfoFor(dependency) shouldContainExactlyInAnyOrder listOf(info100, info111, info200)
        }

        "resolve a patch locator when only a different patch version is installed" {
            val patchLocator = "resolve@patch:resolve@npm%3A2.0.0-next.7" +
                "#optional!builtin<compat/resolve>::version=2.0.0-next.7&hash=c3c19d"
            val patchInfo = packageInfo(patchLocator, "2.0.0-next.7")
            val handler = handlerWith(mapOf(patchLocator to patchInfo))

            val depLocator = "resolve@patch:resolve@npm%3A2.0.0-next.5" +
                "#optional!builtin<compat/resolve>::version=2.0.0-next.5&hash=c3c19d"
            handler.packageInfoFor(dep(depLocator)) shouldBe listOf(patchInfo)
        }

        "not confuse npm and patch variants when resolving multi-version packages" {
            // 4 installed locators for the same module name, see
            // https://github.com/oss-review-toolkit/ort/issues/12008.
            val patchLocator1 = "resolve@patch:resolve@npm%3A1.22.12" +
                "#optional!builtin<compat/resolve>::version=1.22.12&hash=c3c19d"
            val patchLocator2 = "resolve@patch:resolve@npm%3A2.0.0-next.7" +
                "#optional!builtin<compat/resolve>::version=2.0.0-next.7&hash=c3c19d"
            val npmInfo1 = packageInfo("resolve@npm:1.22.12", "1.22.12")
            val npmInfo2 = packageInfo("resolve@npm:2.0.0-next.7", "2.0.0-next.7")
            val patchInfo1 = packageInfo(patchLocator1, "1.22.12")
            val patchInfo2 = packageInfo(patchLocator2, "2.0.0-next.7")
            val handler = handlerWith(
                mapOf(
                    "resolve@npm:1.22.12" to npmInfo1,
                    "resolve@npm:2.0.0-next.7" to npmInfo2,
                    patchLocator1 to patchInfo1,
                    patchLocator2 to patchInfo2
                )
            )

            val depLocator = "resolve@patch:resolve@npm%3A2.0.0-next.5" +
                "#optional!builtin<compat/resolve>::version=2.0.0-next.5&hash=c3c19d"
            val depDescriptor = "resolve@patch:resolve@npm%3A^2.0.0-next.5#optional!builtin<compat/resolve>"
            val dependency = PackageInfo.Dependency(descriptor = depDescriptor, locator = depLocator)

            handler.packageInfoFor(dependency) shouldBe listOf(patchInfo2)
        }

        "resolve an npm locator when both npm and patch variants of the same version are installed" {
            // When a plain npm dependency is requested, it must not be confused with the patch variant.
            val patchLocator = "resolve@patch:resolve@npm%3A1.22.12" +
                "#optional!builtin<compat/resolve>::version=1.22.12&hash=c3c19d"
            val npmInfo = packageInfo("resolve@npm:1.22.12", "1.22.12")
            val patchInfo = packageInfo(patchLocator, "1.22.12")
            val handler = handlerWith(
                mapOf(
                    "resolve@npm:1.22.12" to npmInfo,
                    patchLocator to patchInfo
                )
            )

            handler.packageInfoFor(dep("resolve@npm:1.22.11")) shouldBe listOf(npmInfo)
        }

        "fall back to the opposite locator type when no candidate of the matching type is installed" {
            // Only a patch variant is installed, so the npm lookup falls back to it instead of failing.
            val patchLocator = "resolve@patch:resolve@npm%3A1.22.8" +
                "#optional!builtin<compat/resolve>::version=1.22.8&hash=c3c19d"
            val patchInfo = packageInfo(patchLocator, "1.22.8")
            val handler = handlerWith(mapOf(patchLocator to patchInfo))

            handler.packageInfoFor(dep("resolve@npm:1.22.9")) shouldBe listOf(patchInfo)
        }

        "prefer a range-matching candidate of the other type over a wrong-version candidate of the matching type" {
            // Only a patched variant of the version requested by the descriptor is installed, while the plain npm
            // variant exists at a different version. The version takes priority over the locator type.
            val patchLocator =
                "cookie@patch:cookie@npm%3A2.0.0#optional!builtin<compat/cookie>::version=2.0.0&hash=c3c19d"
            val npmInfo = packageInfo("cookie@npm:1.0.0", "1.0.0")
            val patchInfo = packageInfo(patchLocator, "2.0.0")
            val handler = handlerWith(mapOf("cookie@npm:1.0.0" to npmInfo, patchLocator to patchInfo))

            val dependency = PackageInfo.Dependency(descriptor = "cookie@npm:^2.0.0", locator = "cookie@npm:2.0.0")

            handler.packageInfoFor(dependency) shouldBe listOf(patchInfo)
        }

        "not cache a failed resolution" {
            val handler = handlerWith(mapOf("other@npm:1.0.0" to packageInfo("other@npm:1.0.0", "1.0.0")))
            val dependency = dep("cookie@npm:1.0.2")

            repeat(2) {
                shouldThrow<IllegalStateException> {
                    handler.packageInfoFor(dependency)
                }.message shouldContain "cookie"
            }
        }

        "not resolve a dependency from the cache of a previous context" {
            // The handler instance is reused across definition files, so the cached resolutions of a previous
            // project must not leak into the next one.
            val info111 = packageInfo("cookie@npm:1.1.1", "1.1.1")
            val handler = handlerWith(mapOf("cookie@npm:1.1.1" to info111))
            val dependency = dep("cookie@npm:1.0.2")

            handler.packageInfoFor(dependency) shouldBe listOf(info111)

            val info200 = packageInfo("cookie@npm:2.0.0", "2.0.0")
            handler.setContext(tempdir(), emptyMap(), mapOf("cookie@npm:2.0.0" to info200))

            handler.packageInfoFor(dependency) shouldBe listOf(info200)
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
private fun dep(locator: String) = PackageInfo.Dependency(descriptor = "dummy@npm:^1.2.3", locator = locator)

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
