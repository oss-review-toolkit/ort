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

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import net.peanuuutz.tomlkt.TomlTable

import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.HashAlgorithm
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.plugins.packagemanagers.python.utils.PythonCoreMetadata

private fun indexPackage(
    name: String,
    version: String,
    marker: String? = null,
    dependencies: List<TomlTable> = emptyList(),
    wheels: List<PylockFile.Distribution> = listOf(
        PylockFile.Distribution(
            url = "https://files.pythonhosted.org/$name-$version-py3-none-any.whl",
            hashes = mapOf("sha256" to "c647".repeat(16))
        )
    )
) = PylockFile.Package(
    name = name,
    version = version,
    marker = marker,
    dependencies = dependencies,
    sdist = PylockFile.Distribution(
        url = "https://files.pythonhosted.org/$name-$version.tar.gz",
        hashes = mapOf("sha256" to "d03c".repeat(16))
    ),
    wheels = wheels
)

private fun dependency(name: String, version: String? = null) =
    TomlTable(listOfNotNull("name" to name, version?.let { "version" to it }).toMap())

private fun id(name: String, version: String) = Identifier("PyPI", "", name, version)

class PylockExtensionsTest : WordSpec({
    "toOrtPackage()" should {
        "use the lockfile for identifier, purl and artifacts and the metadata for everything else" {
            val entry = indexPackage("Typed_Ast", "1.5.5")
            val metadata = PythonCoreMetadata(
                summary = "A fork of the ast module.",
                homepage = "https://example.org/typed-ast",
                projectUrls = mapOf("source" to "https://github.com/python/typed_ast"),
                authorEmail = "Jane Doe <jane@example.org>",
                licenseExpression = "Apache-2.0",
                classifiers = listOf("License :: OSI Approved :: MIT License")
            )

            val pkg = entry.toOrtPackage(metadata)

            pkg.id shouldBe id("typed-ast", "1.5.5")
            pkg.purl shouldBe "pkg:pypi/typed-ast@1.5.5"
            pkg.authors should containExactly("Jane Doe <jane@example.org>")
            pkg.declaredLicenses should containExactly("Apache-2.0")
            pkg.declaredLicensesProcessed.spdxExpression.toString() shouldBe "Apache-2.0"
            pkg.description shouldBe "A fork of the ast module."
            pkg.homepageUrl shouldBe "https://example.org/typed-ast"
            pkg.binaryArtifact shouldBe RemoteArtifact(
                "https://files.pythonhosted.org/Typed_Ast-1.5.5-py3-none-any.whl",
                Hash("c647".repeat(16), HashAlgorithm.SHA256)
            )
            pkg.sourceArtifact shouldBe RemoteArtifact(
                "https://files.pythonhosted.org/Typed_Ast-1.5.5.tar.gz",
                Hash("d03c".repeat(16), HashAlgorithm.SHA256)
            )
            pkg.vcs shouldBe VcsInfo.EMPTY
            pkg.vcsProcessed shouldBe VcsInfo(VcsType.GIT, "https://github.com/python/typed_ast.git", "")
        }

        "leave the metadata parts empty if there is no metadata" {
            val pkg = indexPackage("attrs", "26.1.0").toOrtPackage(null)

            pkg.id shouldBe id("attrs", "26.1.0")
            pkg.declaredLicenses should beEmpty()
            pkg.authors should beEmpty()
            pkg.description shouldBe ""
            pkg.vcsProcessed shouldBe VcsInfo.EMPTY
        }

        "not pick a platform-specific wheel out of several as the binary artifact" {
            val wheels = listOf("win_amd64", "manylinux_2_17_x86_64.manylinux2014_x86_64").map {
                PylockFile.Distribution(url = "https://files.pythonhosted.org/numpy-2.2.3-cp312-cp312-$it.whl")
            }

            val pkg = indexPackage("numpy", "2.2.3", wheels = wheels).toOrtPackage(null)

            pkg.binaryArtifact shouldBe RemoteArtifact.EMPTY
            pkg.sourceArtifact.url shouldBe "https://files.pythonhosted.org/numpy-2.2.3.tar.gz"
        }

        "pick the single wheel as the binary artifact even if it is platform-specific" {
            val wheel = PylockFile.Distribution(
                url = "https://files.pythonhosted.org/numpy-2.2.3-cp312-cp312-win_amd64.whl"
            )

            val pkg = indexPackage("numpy", "2.2.3", wheels = listOf(wheel)).toOrtPackage(null)

            pkg.binaryArtifact.url shouldBe wheel.url
        }

        "record the VCS location of a VCS entry" {
            val entry = PylockFile.Package(
                name = "sampleproject",
                version = "4.0.0",
                vcs = PylockFile.Vcs(
                    type = "git",
                    url = "https://github.com/pypa/sampleproject",
                    requestedRevision = "main",
                    commitId = "621e4974ca25ce531773def586ba3ed8e736b3fc",
                    subdirectory = "src"
                )
            )

            val pkg = entry.toOrtPackage(null)

            pkg.purl shouldBe "pkg:pypi/sampleproject@4.0.0?" +
                "vcs_url=git%2Bhttps%3A%2F%2Fgithub.com%2Fpypa%2Fsampleproject%40621e4974ca25ce531773def586ba3ed8e736b3fc"
            pkg.vcs shouldBe VcsInfo(
                VcsType.GIT,
                "https://github.com/pypa/sampleproject",
                "621e4974ca25ce531773def586ba3ed8e736b3fc",
                "src"
            )
            pkg.vcsProcessed.url shouldBe "https://github.com/pypa/sampleproject.git"
            pkg.sourceArtifact shouldBe RemoteArtifact.EMPTY
        }

        "use the commit ID as the version of a VCS entry without a version" {
            val entry = PylockFile.Package(
                name = "sampleproject",
                vcs = PylockFile.Vcs(
                    type = "git",
                    url = "https://github.com/pypa/sampleproject",
                    commitId = "621e4974ca25ce531773def586ba3ed8e736b3fc"
                )
            )

            val pkg = entry.toOrtPackage(null)

            pkg.id shouldBe id("sampleproject", "621e4974ca25ce531773def586ba3ed8e736b3fc")
            pkg.purl shouldBe "pkg:pypi/sampleproject@621e4974ca25ce531773def586ba3ed8e736b3fc?" +
                "vcs_url=git%2Bhttps%3A%2F%2Fgithub.com%2Fpypa%2Fsampleproject%40621e4974ca25ce531773def586ba3ed8e736b3fc"
        }

        "record a directly referenced archive as an artifact" {
            val entry = PylockFile.Package(
                name = "six",
                version = "1.17.0",
                archive = PylockFile.Archive(
                    url = "https://files.pythonhosted.org/six-1.17.0-py2.py3-none-any.whl",
                    hashes = mapOf("sha256" to "4721".repeat(16))
                )
            )

            val pkg = entry.toOrtPackage(null)

            pkg.purl shouldBe "pkg:pypi/six@1.17.0?" +
                "download_url=https%3A%2F%2Ffiles.pythonhosted.org%2Fsix-1.17.0-py2.py3-none-any.whl"
            pkg.binaryArtifact shouldBe RemoteArtifact(
                "https://files.pythonhosted.org/six-1.17.0-py2.py3-none-any.whl",
                Hash("4721".repeat(16), HashAlgorithm.SHA256)
            )
            pkg.sourceArtifact shouldBe RemoteArtifact.EMPTY
        }

        "recognize a wheel archive despite a URL fragment" {
            val url = "https://example.org/dl/six-1.17.0-py2.py3-none-any.whl#sha256=${"4721".repeat(16)}"
            val entry = PylockFile.Package(name = "six", version = "1.17.0", archive = PylockFile.Archive(url = url))

            val pkg = entry.toOrtPackage(null)

            pkg.binaryArtifact.url shouldBe url
            pkg.sourceArtifact shouldBe RemoteArtifact.EMPTY
        }
    }

    "resolveDependencies()" should {
        "resolve dependencies through local directory entries" {
            val appCli = indexPackage("app-cli", "1.0", dependencies = listOf(dependency("app-core")))
            val appCore = PylockFile.Package(
                name = "app-core",
                directory = PylockFile.Directory(path = "packages/core"),
                dependencies = listOf(dependency("attrs"))
            )
            val attrs = indexPackage("attrs", "26.1.0")

            val dependencies = listOf(appCli, appCore, attrs).resolveDependencies()

            dependencies shouldContainExactly mapOf(appCli to listOf(attrs), attrs to emptyList())
        }

        "resolve a dependency by version if the same package is locked in several versions" {
            val oldAttrs = indexPackage("attrs", "25.1.0", marker = "python_version < '3.12'")
            val newAttrs = indexPackage("attrs", "26.1.0", marker = "python_version >= '3.12'")
            val cattrs = indexPackage("cattrs", "24.1.2", dependencies = listOf(dependency("attrs", "26.1.0")))

            val dependencies = listOf(oldAttrs, newAttrs, cattrs).resolveDependencies()

            dependencies.getValue(cattrs) should containExactly(newAttrs)
        }

        "resolve a dependency by any key the reference names" {
            val linuxAttrs = indexPackage("attrs", "26.1.0", marker = "sys_platform == 'linux'")
            val windowsAttrs = indexPackage("attrs", "26.1.0", marker = "sys_platform == 'win32'")
            val cattrs = indexPackage(
                "cattrs",
                "24.1.2",
                dependencies = listOf(TomlTable("name" to "attrs", "marker" to "sys_platform == 'win32'"))
            )

            val dependencies = listOf(linuxAttrs, windowsAttrs, cattrs).resolveDependencies()

            dependencies.getValue(cattrs) should containExactly(windowsAttrs)
        }

        "ignore a dependency that cannot be resolved unambiguously" {
            val cattrs = indexPackage("cattrs", "24.1.2", dependencies = listOf(dependency("attrs")))
            val entries = listOf(indexPackage("attrs", "25.1.0"), indexPackage("attrs", "26.1.0"), cattrs)

            val dependencies = entries.resolveDependencies()

            dependencies.getValue(cattrs) should beEmpty()
        }
    }

    "getDirectDependenciesByScope()" should {
        "put all entries without group markers into the default scope" {
            val entries = listOf(indexPackage("attrs", "26.1.0"), indexPackage("cattrs", "24.1.2"))

            val scopes = entries.getDirectDependenciesByScope(entries.resolveDependencies())

            scopes shouldContainExactly mapOf(DEFAULT_SCOPE_NAME to entries)
        }

        "create a scope per dependency group named in the markers" {
            val cattrs = indexPackage("cattrs", "24.1.2", marker = "\"default\" in dependency_groups")
            val pytest = indexPackage("pytest", "8.3.5", marker = "\"dev\" in dependency_groups")
            val pluggy = indexPackage(
                "pluggy",
                "1.6.0",
                marker = "\"default\" in dependency_groups or \"dev\" in dependency_groups"
            )
            val entries = listOf(cattrs, pytest, pluggy)

            val scopes = entries.getDirectDependenciesByScope(entries.resolveDependencies())

            scopes shouldContainExactly mapOf("default" to listOf(cattrs, pluggy), "dev" to listOf(pytest, pluggy))
        }

        "keep an entry in the default scope if its marker can hold without any group" {
            val colorama = indexPackage(
                "colorama",
                "0.4.6",
                marker = "sys_platform == 'win32' or 'dev' in dependency_groups"
            )
            val pytest = indexPackage(
                "pytest",
                "8.3.5",
                marker = "'dev' in dependency_groups and sys_platform == 'win32'"
            )
            val entries = listOf(colorama, pytest)

            val scopes = entries.getDirectDependenciesByScope(entries.resolveDependencies())

            scopes shouldContainExactly mapOf(DEFAULT_SCOPE_NAME to listOf(colorama), "dev" to listOf(colorama, pytest))
        }

        "leave out the entries that another entry of the scope depends on" {
            val attrs = indexPackage("attrs", "26.1.0")
            val cattrs = indexPackage("cattrs", "24.1.2", dependencies = listOf(dependency("attrs")))
            val colorama = indexPackage("colorama", "0.4.6")
            val entries = listOf(attrs, cattrs, colorama)

            val scopes = entries.getDirectDependenciesByScope(entries.resolveDependencies())

            scopes.getValue(DEFAULT_SCOPE_NAME) should containExactlyInAnyOrder(cattrs, colorama)
        }

        "keep the first entry of a dependency cycle" {
            val a = indexPackage("a", "1.0", dependencies = listOf(dependency("b")))
            val b = indexPackage("b", "1.0", dependencies = listOf(dependency("a")))
            val entries = listOf(a, b)

            val scopes = entries.getDirectDependenciesByScope(entries.resolveDependencies())

            scopes.getValue(DEFAULT_SCOPE_NAME) should containExactly(a)
        }

        "not count dependencies of entries outside the scope" {
            val attrs = indexPackage(
                "attrs",
                "26.1.0",
                marker = "'default' in dependency_groups or 'dev' in dependency_groups"
            )
            val pytest = indexPackage(
                "pytest",
                "8.3.5",
                marker = "'dev' in dependency_groups",
                dependencies = listOf(dependency("attrs"))
            )
            val entries = listOf(attrs, pytest)

            val scopes = entries.getDirectDependenciesByScope(entries.resolveDependencies())

            // Only a development tool depends on the package, so within the default scope it is a direct dependency.
            scopes shouldContainExactly mapOf("default" to listOf(attrs), "dev" to listOf(pytest))
        }
    }
})
