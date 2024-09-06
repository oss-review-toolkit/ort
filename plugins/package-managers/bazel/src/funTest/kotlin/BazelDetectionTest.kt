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
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain

import org.ossreviewtoolkit.analyzer.analyze
import org.ossreviewtoolkit.utils.test.getAssetFile

class BazelDetectionTest : StringSpec({
    "MODULE.bazel files present in a local registry should not be considered as definition files" {
        val definitionFile = getAssetFile("projects/synthetic/bazel-local-registry2/MODULE.bazel")

        val exception = shouldThrow<IllegalArgumentException> {
            analyze(definitionFile.parentFile, packageManagers = listOf(Bazel.Factory()))
        }

        exception.shouldNotBeNull {
            // Running the Analyzer on a project depending on packages present in a local registry currently fails
            // with this message (issue #9076). This is because the "MODULE.bazel" files present in the local
            // registry should not be considered as definition files.
            message shouldContain
                "Unable to create the AnalyzerResult as it contains packages and projects with the same ids"
        }
    }
})
