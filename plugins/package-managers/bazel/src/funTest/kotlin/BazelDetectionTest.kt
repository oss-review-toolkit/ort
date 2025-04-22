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

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.should

import org.ossreviewtoolkit.analyzer.analyze
import org.ossreviewtoolkit.analyzer.getAnalyzerResult
import org.ossreviewtoolkit.model.toYaml
import org.ossreviewtoolkit.utils.test.getAssetFile
import org.ossreviewtoolkit.utils.test.matchExpectedResult
import org.ossreviewtoolkit.utils.test.patchActualResult

class BazelDetectionTest : StringSpec({
    "MODULE.bazel files present in a local registry should not be considered as definition files" {
        val definitionFile = getAssetFile("projects/synthetic/bazel-local-registry2/MODULE.bazel")
        val expectedResultFile = getAssetFile("projects/synthetic/bazel-expected-output-local-registry2.yml")

        val result = analyze(definitionFile.parentFile, packageManagers = listOf(BazelFactory())).getAnalyzerResult()

        patchActualResult(result.toYaml(), patchStartAndEndTime = true) should matchExpectedResult(
            expectedResultFile,
            definitionFile
        )
    }
})
