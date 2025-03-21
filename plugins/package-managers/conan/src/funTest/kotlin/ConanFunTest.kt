/*
 * Copyright (C) 2019 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.conan

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.should

import org.ossreviewtoolkit.analyzer.resolveSingleProject
import org.ossreviewtoolkit.model.toYaml
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.test.getAssetFile
import org.ossreviewtoolkit.utils.test.matchExpectedResult
import org.ossreviewtoolkit.utils.test.patchActualResult

/**
 * This test class performs tests with both Conan 1 and Conan 2. For it to be successful, it needs both a "conan"
 * command for Conan 1 and a "conan2" command for Conan 2 in the PATH environment variable (as in ORT Docker image).
 *
 * A word of caution about Conan 2 tests: If there is no lockfile, when Conan resolves the dependencies it read its
 * cache and relies on the package name, ignoring the version. This means, for instance, that if a test reported
 * dependency at version 1.3.1, this version will still be reported by "conan graph info" in another test, even if the
 * latter uses a lower version of this dependency. This leads to some side effects when running the tests locally
 * several times (CI checks are not impacted because they always start with an empty cache). A workaround is to delete
 * the Conan cache before executing the tests, i.e. rm -Rf ~/.conan2/p/.
 */
class ConanFunTest : StringSpec({
    "Project dependencies are detected correctly for conanfile.txt" {
        val definitionFile = getAssetFile("projects/synthetic/conan-txt/conanfile.txt")
        val expectedResultFile = getAssetFile("projects/synthetic/conan-expected-output-txt.yml")

        val result = ConanFactory.create().resolveSingleProject(definitionFile, allowDynamicVersions = true)

        patchActualResult(result.toYaml()) should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "Project dependencies are detected correctly for conanfile.py" {
        val definitionFile = getAssetFile("projects/synthetic/conan-py/conanfile.py")
        val expectedResultFile = getAssetFile("projects/synthetic/conan-expected-output-py.yml")

        val result = ConanFactory.create().resolveSingleProject(definitionFile, allowDynamicVersions = true)

        patchActualResult(result.toYaml()) should matchExpectedResult(expectedResultFile, definitionFile)
    }

    /**
     * This test cannot complete successfully when you run it locally as `package id` differs depending on operating
     * system. The `package id` is set to the one calculated on Linux.
     */
    "Project dependencies are detected correctly with the lockfile".config(enabled = Os.isLinux) {
        val definitionFile = getAssetFile("projects/synthetic/conan-py-lockfile/conanfile.py")
        val expectedResultFile = getAssetFile("projects/synthetic/conan-expected-output-py-lockfile.yml")

        val result = ConanFactory.create(lockfileName = "lockfile.lock").resolveSingleProject(definitionFile)

        patchActualResult(result.toYaml()) should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "Project dependencies are detected correctly for conanfile.txt with Conan 2" {
        // Compared to the results with Conan 1, these results contains an additional package "Conan::libtool:2.4.7".
        // This is a build dependency of "libcurl", while "libcurl" itself is a dependency the project.
        val definitionFile = getAssetFile("projects/synthetic/conan-txt/conanfile.txt")
        val expectedResultFile = getAssetFile("projects/synthetic/conan2-expected-output-txt.yml")

        val result = ConanFactory.create(useConan2 = true)
            .resolveSingleProject(definitionFile, allowDynamicVersions = true)

        patchActualResult(result.toYaml()) should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "Project dependencies are detected correctly for conanfile.py with Conan 2" {
        // Conan 2 resolves "Conan::expat" at version 2.6.4 while Conan 1 resolves it at version 2.6.3.
        val definitionFile = getAssetFile("projects/synthetic/conan-py/conanfile.py")
        val expectedResultFile = getAssetFile("projects/synthetic/conan2-expected-output-py.yml")

        val result = ConanFactory.create(useConan2 = true)
            .resolveSingleProject(definitionFile, allowDynamicVersions = true)

        patchActualResult(result.toYaml()) should matchExpectedResult(expectedResultFile, definitionFile)
    }

    /**
     * Same test as above, but with Conan 2.
     */
    "Project dependencies are detected correctly with Conan 2 and the lockfile".config(enabled = Os.isLinux) {
        val definitionFile = getAssetFile("projects/synthetic/conan-py-lockfile/conanfile.py")
        val expectedResultFile = getAssetFile("projects/synthetic/conan-expected-output-py-lockfile.yml")

        val result = ConanFactory.create(lockfileName = "lockfile_conan2.lock", useConan2 = true)
            .resolveSingleProject(definitionFile)

        patchActualResult(result.toYaml()) should matchExpectedResult(expectedResultFile, definitionFile)
    }
})
