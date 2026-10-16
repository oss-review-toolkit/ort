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

package org.ossreviewtoolkit.plugins.packagemanagers.spdx

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType

import org.spdx.library.SpdxModelFactory
import org.spdx.library.model.v3_0_1.core.Element
import org.spdx.library.model.v3_0_1.core.LifecycleScopeType
import org.spdx.library.model.v3_0_1.core.LifecycleScopedRelationship
import org.spdx.library.model.v3_0_1.core.Relationship
import org.spdx.library.model.v3_0_1.core.RelationshipType
import org.spdx.library.model.v3_0_1.software.SpdxPackage
import org.spdx.storage.simple.InMemSpdxStore

class SpdxDependencyHandlerTest : WordSpec({
    beforeSpec {
        SpdxModelFactory.init()
    }

    "dependenciesFor()" should {
        "follow dependencies of a root package that is not the output of a build" {
            val root = createPackage("root")
            val runtimeDep = createPackage("runtime-dep")
            val unscopedDep = createPackage("unscoped-dep")
            val buildDep = createPackage("build-dep")
            val transitiveDep = createPackage("transitive-dep")

            val relationships = listOf(
                createRelationship(root, runtimeDep, LifecycleScopeType.RUNTIME),
                createRelationship(root, unscopedDep),
                createRelationship(root, buildDep, LifecycleScopeType.BUILD),
                createRelationship(unscopedDep, transitiveDep)
            )

            val handler = SpdxDependencyHandler()
            handler.setDocument(relationships, listOf(root, runtimeDep, unscopedDep, buildDep, transitiveDep))

            handler.dependenciesFor(SpdxDependency(root, LifecycleScopeType.RUNTIME)) should containExactlyInAnyOrder(
                SpdxDependency(runtimeDep, LifecycleScopeType.RUNTIME),
                SpdxDependency(unscopedDep, LifecycleScopeType.RUNTIME)
            )

            handler.dependenciesFor(SpdxDependency(unscopedDep, LifecycleScopeType.RUNTIME)) should
                containExactlyInAnyOrder(SpdxDependency(transitiveDep, LifecycleScopeType.RUNTIME))

            handler.dependenciesFor(SpdxDependency(runtimeDep, LifecycleScopeType.RUNTIME)) should beEmpty()
        }
    }

    "setDocument()" should {
        "replace the relationships of a previous document" {
            val first = createPackage("first")
            val second = createPackage("second")
            val dep = createPackage("dep")

            val handler = SpdxDependencyHandler()

            handler.setDocument(listOf(createRelationship(first, dep)), listOf(first, dep))
            handler.dependenciesFor(SpdxDependency(first, LifecycleScopeType.RUNTIME)) should
                containExactlyInAnyOrder(SpdxDependency(dep, LifecycleScopeType.RUNTIME))

            handler.setDocument(listOf(createRelationship(second, dep)), listOf(second, dep))
            handler.dependenciesFor(SpdxDependency(first, LifecycleScopeType.RUNTIME)) should beEmpty()
            handler.dependenciesFor(SpdxDependency(second, LifecycleScopeType.RUNTIME)) should
                containExactlyInAnyOrder(SpdxDependency(dep, LifecycleScopeType.RUNTIME))
        }
    }

    "getVcsInfo()" should {
        "parse a Git URL with a revision" {
            val pkg = createPackage(downloadLocation = "git+https://git.yoctoproject.org/linux-yocto.git@2a17dc58")

            val info = pkg.getVcsInfo()

            info shouldBe VcsInfo(
                type = VcsType.GIT,
                url = "https://git.yoctoproject.org/linux-yocto.git",
                revision = "2a17dc58"
            )
        }

        "parse a Git URL with a revision and a path" {
            val pkg = createPackage(downloadLocation = "git+https://github.com/org/repo.git@v1.0#sub/dir")

            val info = pkg.getVcsInfo()

            info shouldBe VcsInfo(
                type = VcsType.GIT,
                url = "https://github.com/org/repo.git",
                revision = "v1.0",
                path = "sub/dir"
            )
        }

        "not confuse the user info with a revision" {
            val pkg = createPackage(downloadLocation = "git+ssh://git@github.com/org/repo.git")

            val info = pkg.getVcsInfo()

            info shouldBe VcsInfo(
                type = VcsType.GIT,
                url = "ssh://git@github.com/org/repo.git",
                revision = ""
            )
        }

        "parse a Git URL with both user info and a revision" {
            val pkg = createPackage(downloadLocation = "git+ssh://git@github.com/org/repo.git@main")

            val info = pkg.getVcsInfo()

            info shouldBe VcsInfo(
                type = VcsType.GIT,
                url = "ssh://git@github.com/org/repo.git",
                revision = "main"
            )
        }

        "parse Mercurial VCS URLs" {
            val info = createPackage(downloadLocation = "hg+https://hg.example.org/repo@abc").getVcsInfo()

            info shouldBe VcsInfo(
                type = VcsType.MERCURIAL,
                url = "https://hg.example.org/repo",
                revision = "abc"
            )
        }

        "parse Subversion VCS URLs" {
            val info = createPackage(downloadLocation = "svn+https://svn.example.org/repo@123").getVcsInfo()

            info shouldBe VcsInfo(
                type = VcsType.SUBVERSION,
                url = "https://svn.example.org/repo",
                revision = "123"
            )
        }

        "return null for a non-VCS download location" {
            createPackage(downloadLocation = "https://zlib.net/zlib-1.3.1.tar.gz").getVcsInfo() should beNull()
        }

        "return null for a missing download location" {
            createPackage().getVcsInfo() should beNull()
        }
    }

    "getSourcePackageId()" should {
        "derive the name and version from an archive name" {
            val pkg = createPackage("zlib-1.3.1.tar.gz", "https://zlib.net/zlib-1.3.1.tar.gz")

            pkg.getSourcePackageId() shouldBe Identifier("SPDX:zlib.net:zlib:1.3.1")
        }

        "handle an underscore as the separator and an 'orig' suffix" {
            val pkg = createPackage(
                "v86d_0.1.10.orig.tar.gz",
                "http://snapshot.debian.org/archive/debian/pool/main/v/v86d/v86d_0.1.10.orig.tar.gz"
            )

            pkg.getSourcePackageId() shouldBe Identifier("SPDX:snapshot.debian.org:v86d:0.1.10")
        }

        "handle names that contain separators and digits" {
            createPackage(
                "autoconf-archive-2023.02.20.tar.xz",
                "https://ftpmirror.gnu.org/gnu/autoconf-archive/autoconf-archive-2023.02.20.tar.xz"
            ).getSourcePackageId() shouldBe Identifier("SPDX:ftpmirror.gnu.org:autoconf-archive:2023.02.20")

            createPackage(
                "bzip2-1.0.8.tar.gz",
                "https://sourceware.org/pub/bzip2/bzip2-1.0.8.tar.gz"
            ).getSourcePackageId() shouldBe Identifier("SPDX:sourceware.org:bzip2:1.0.8")
        }

        "keep separators in the version" {
            val pkg = createPackage(
                "libedit-20230828-3.1.tar.gz",
                "http://www.thrysoee.dk/editline/libedit-20230828-3.1.tar.gz"
            )

            pkg.getSourcePackageId() shouldBe Identifier("SPDX:www.thrysoee.dk:libedit:20230828-3.1")
        }

        "use an empty version if the archive name contains none" {
            val pkg = createPackage("foo.tar.gz", "https://example.org/foo.tar.gz")

            pkg.getSourcePackageId() shouldBe Identifier("SPDX:example.org:foo:")
        }

        "fall back to the file name from the download location for unnamed packages" {
            val pkg = createPackage(downloadLocation = "https://zlib.net/zlib-1.3.1.tar.gz")

            pkg.getSourcePackageId() shouldBe Identifier("SPDX:zlib.net:zlib:1.3.1")
        }

        "derive the name and version from a Git URL and revision" {
            val pkg = createPackage(
                "git.yoctoproject.org.yocto-kernel-cache",
                "git+https://git.yoctoproject.org/yocto-kernel-cache@50530c85"
            )

            pkg.getSourcePackageId() shouldBe Identifier("SPDX:git.yoctoproject.org:yocto-kernel-cache:50530c85")
        }

        "strip the user info from the namespace and the '.git' suffix from the name" {
            val pkg = createPackage("repo", "git+ssh://git@github.com/org/repo.git@main")

            pkg.getSourcePackageId() shouldBe Identifier("SPDX:github.com/org:repo:main")
        }

        "distinguish repositories of the same name by their parent path" {
            val pkgA = createPackage(downloadLocation = "git+https://github.com/team-a/common.git@main")
            val pkgB = createPackage(downloadLocation = "git+https://github.com/team-b/common.git@main")

            pkgA.getSourcePackageId() shouldBe Identifier("SPDX:github.com/team-a:common:main")
            pkgB.getSourcePackageId() shouldBe Identifier("SPDX:github.com/team-b:common:main")
        }

        "distinguish directories in the same repository by their path" {
            val pkgA = createPackage(downloadLocation = "git+https://github.com/org/mono.git@v1.0#libs/a")
            val pkgB = createPackage(downloadLocation = "git+https://github.com/org/mono.git@v1.0#libs/b/")

            pkgA.getSourcePackageId() shouldBe Identifier("SPDX:github.com/org:mono/libs/a:v1.0")
            pkgB.getSourcePackageId() shouldBe Identifier("SPDX:github.com/org:mono/libs/b:v1.0")
        }

        "prefer an explicitly set package version" {
            val pkg = createPackage("zlib-1.3.1.tar.gz", "https://zlib.net/zlib-1.3.1.tar.gz", packageVersion = "1.3")

            pkg.getSourcePackageId() shouldBe Identifier("SPDX:zlib.net:zlib:1.3")
        }

        "return null if neither a name nor a download location is present" {
            createPackage().getSourcePackageId() should beNull()
        }
    }
})

private val store by lazy { InMemSpdxStore() }

private var packageCount = 0
private var relationshipCount = 0

private fun createPackage(
    name: String? = null,
    downloadLocation: String? = null,
    packageVersion: String? = null
): SpdxPackage =
    SpdxPackage(store, "https://example.org/spdx/package-${++packageCount}", null, true, null).apply {
        name?.let { setName(it) }
        downloadLocation?.let { setDownloadLocation(it) }
        packageVersion?.let { setPackageVersion(it) }
    }

private fun createRelationship(from: Element, to: Element, scope: LifecycleScopeType? = null): Relationship {
    val uri = "https://example.org/spdx/relationship-${++relationshipCount}"

    val relationship = if (scope != null) {
        LifecycleScopedRelationship(store, uri, null, true, null).setScope(scope)
    } else {
        Relationship(store, uri, null, true, null)
    }

    return relationship.apply {
        setFrom(from)
        setRelationshipType(RelationshipType.DEPENDS_ON)
        tos += to
    }
}
