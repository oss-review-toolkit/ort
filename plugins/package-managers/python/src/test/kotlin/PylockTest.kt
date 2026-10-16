/*
 * Copyright (C) 2026 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.packagemanagers.python

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration

import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

import java.io.File

import org.ossreviewtoolkit.analyzer.resolveSingleProject
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.Includes
import org.ossreviewtoolkit.plugins.packagemanagers.python.utils.PythonIndexClient
import org.ossreviewtoolkit.utils.common.div

private const val WHEEL_PATH = "/files/attrs-26.1.0-py3-none-any.whl"
private const val SDIST_PATH = "/files/attrs-26.1.0.tar.gz"

class PylockTest : WordSpec({
    val server = WireMockServer(WireMockConfiguration.options().dynamicPort())

    beforeSpec { server.start() }
    beforeEach { server.resetAll() }
    afterSpec { server.stop() }

    fun createPylock() = Pylock(config = PylockConfig(indexUrl = null), indexClientFactory = { PythonIndexClient() })

    fun writeLockfile(dir: File, name: String = "pylock.toml"): File =
        (dir / name).apply {
            writeText(
                """
                    lock-version = "1.0"
                    created-by = "uv"

                    [[packages]]
                    name = "attrs"
                    version = "26.1.0"
                    index = "${server.baseUrl()}/simple"
                    wheels = [{ url = "${server.baseUrl()}$WHEEL_PATH", hashes = { sha256 = "${"c647".repeat(16)}" } }]

                    [[packages]]
                    name = "pylock-example"
                    directory = { path = ".", editable = true }

                    [[packages]]
                    name = "workspace-member"
                    directory = { path = "packages/member" }
                """.trimIndent()
            )
        }

    "resolveDependencies()" should {
        "name the project after the pyproject file and skip local directory entries" {
            server.stubFor(
                get(urlPathEqualTo("$WHEEL_PATH.metadata"))
                    .willReturn(aResponse().withStatus(200).withBody("Name: attrs\nLicense-Expression: MIT\n"))
            )

            val dir = tempdir()
            (dir / PYPROJECT_FILENAME).writeText("[project]\nname = \"pylock-example\"\nversion = \"1.0.0\"\n")

            val result = createPylock().resolveSingleProject(writeLockfile(dir), resolveScopes = true)

            result.project.id shouldBe Identifier("Pylock", "", "pylock-example", "1.0.0")
            result.packages.map { it.id } should containExactly(Identifier("PyPI", "", "attrs", "26.1.0"))
            result.packages.single().declaredLicenses should containExactly("MIT")
            result.project.scopes.shouldBeSingleton {
                it.name shouldBe DEFAULT_SCOPE_NAME
                it.dependencies.map { dependency -> dependency.id.name } should containExactly("attrs")
            }

            result.issues should beEmpty()
        }

        "add the name of a named lockfile to the project name" {
            server.stubFor(get(urlPathEqualTo("$WHEEL_PATH.metadata")).willReturn(aResponse().withStatus(404)))

            val dir = tempdir()
            (dir / PYPROJECT_FILENAME).writeText("[project]\nname = \"pylock-example\"\nversion = \"1.0.0\"\n")

            val result = createPylock().resolveSingleProject(writeLockfile(dir, "pylock.linux.toml"))

            result.project.id shouldBe Identifier("Pylock", "", "pylock-example-linux", "1.0.0")
        }

        "create a package for a VCS entry without contacting the index" {
            val lockfile = (tempdir() / "pylock.toml").apply {
                writeText(
                    """
                        lock-version = "1.0"
                        created-by = "pip"

                        [[packages]]
                        name = "sampleproject"
                        vcs = { type = "git", url = "https://github.com/pypa/sampleproject", commit-id = "621e4974" }
                    """.trimIndent()
                )
            }

            val result = createPylock().resolveSingleProject(lockfile)

            result.packages.shouldBeSingleton {
                it.id shouldBe Identifier("PyPI", "", "sampleproject", "621e4974")
                it.vcsProcessed.url shouldBe "https://github.com/pypa/sampleproject.git"
                it.vcsProcessed.revision shouldBe "621e4974"
            }

            // A VCS entry has no metadata on an index, so its absence must not be reported.
            result.issues should beEmpty()
            server.verify(0, anyRequestedFor(anyUrl()))
        }

        "fall back to the path of the lockfile as the project name" {
            server.stubFor(get(urlPathEqualTo("$WHEEL_PATH.metadata")).willReturn(aResponse().withStatus(404)))

            val dir = tempdir()

            val result = createPylock().resolveSingleProject(writeLockfile(dir))

            result.project.id.name shouldBe "pylock.toml"
        }

        "ignore a blank index URL option" {
            server.stubFor(get(urlPathEqualTo("$WHEEL_PATH.metadata")).willReturn(aResponse().withStatus(404)))

            var configuredIndexUrl: String? = "unset"
            val pylock = Pylock(
                config = PylockConfig(indexUrl = " "),
                indexClientFactory = {
                    configuredIndexUrl = it
                    PythonIndexClient()
                }
            )

            pylock.resolveSingleProject(writeLockfile(tempdir()))

            configuredIndexUrl should beNull()
        }

        "report packages whose metadata cannot be retrieved" {
            server.stubFor(get(urlPathEqualTo("$WHEEL_PATH.metadata")).willReturn(aResponse().withStatus(404)))
            server.stubFor(get(urlPathEqualTo("/pypi/attrs/26.1.0/json")).willReturn(aResponse().withStatus(404)))

            val result = createPylock().resolveSingleProject(writeLockfile(tempdir()))

            result.packages.single().declaredLicenses should beEmpty()
            result.issues.shouldBeSingleton {
                it.severity shouldBe Severity.WARNING
                it.message shouldContain "1 package(s)"
                it.message shouldContain "PyPI::attrs:26.1.0"
            }
        }

        "unify packages that several lockfiles lock with different distribution files" {
            server.stubFor(get(urlPathEqualTo("$WHEEL_PATH.metadata")).willReturn(aResponse().withStatus(404)))

            val root = tempdir()
            val lockfile = writeLockfile(root)

            // A second lockfile that locks the same package from another index and also has a source distribution,
            // which must be kept for the scanner.
            val otherLockfile = (root / "pyodide" / "pylock.toml").apply {
                parentFile.mkdirs()
                writeText(
                    """
                        lock-version = "1.0"
                        created-by = "uv"

                        [[packages]]
                        name = "attrs"
                        version = "26.1.0"
                        index = "${server.baseUrl()}/pyodide/simple"
                        sdist = { url = "${server.baseUrl()}/pyodide$SDIST_PATH", hashes = { sha256 = "${"8028".repeat(16)}" } }
                        wheels = [{ url = "${server.baseUrl()}/pyodide$WHEEL_PATH", hashes = { sha256 = "${"d03c".repeat(16)}" } }]
                    """.trimIndent()
                )
            }

            val result = createPylock().resolveDependencies(
                root,
                listOf(lockfile, otherLockfile),
                Excludes.EMPTY,
                Includes.EMPTY,
                AnalyzerConfiguration(),
                emptyMap()
            )

            val packages = result.projectResults.values.flatten().flatMap { it.packages }.distinct()

            packages.shouldBeSingleton {
                it.id shouldBe Identifier("PyPI", "", "attrs", "26.1.0")
                it.binaryArtifact.url shouldBe "${server.baseUrl()}/pyodide$WHEEL_PATH"
                it.sourceArtifact.url shouldBe "${server.baseUrl()}/pyodide$SDIST_PATH"
            }
        }
    }

    "filterNotManagedByPylock()" should {
        "drop definition files next to a PEP 751 lockfile if Pylock is enabled" {
            val dir = tempdir()
            val requirements = (dir / "requirements.txt").apply { writeText("attrs\n") }
            val nestedRequirements = (dir / "nested" / "requirements.txt").apply {
                parentFile.mkdirs()
                writeText("attrs\n")
            }

            writeLockfile(dir, "pylock.linux.toml")

            val config = AnalyzerConfiguration(enabledPackageManagers = listOf("PIP", "Pylock"))

            listOf(requirements, nestedRequirements).filterNotManagedByPylock(config) should
                containExactly(nestedRequirements)
        }

        "keep all definition files if Pylock is disabled" {
            val dir = tempdir()
            val requirements = (dir / "requirements.txt").apply { writeText("attrs\n") }

            writeLockfile(dir)

            val config = AnalyzerConfiguration(enabledPackageManagers = listOf("PIP"))

            listOf(requirements).filterNotManagedByPylock(config) should containExactly(requirements)
        }
    }

    "The other Python package managers" should {
        "yield to Pylock for a directory with a PEP 751 lockfile" {
            val dir = tempdir()
            val definitionFiles = listOf("requirements.txt", "Pipfile.lock", "poetry.lock").map { name ->
                (dir / name).apply { writeText("") }
            }

            writeLockfile(dir)

            val config = AnalyzerConfiguration(enabledPackageManagers = listOf("PIP", "Pipenv", "Poetry", "Pylock"))

            listOf(PipFactory.create(), PipenvFactory.create(), PoetryFactory.create()).forEach { packageManager ->
                packageManager.mapDefinitionFiles(dir, definitionFiles, config) should beEmpty()
            }
        }
    }
})
