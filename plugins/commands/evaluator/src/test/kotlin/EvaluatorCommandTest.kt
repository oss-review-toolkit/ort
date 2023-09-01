/*
 * Copyright (C) 2023 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.commands.evaluator

import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.file.exist
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.shouldContain

import java.io.FileNotFoundException

import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.ort.ORT_CONFIG_DIR_ENV_NAME
import org.ossreviewtoolkit.utils.ort.ORT_EVALUATOR_RULES_FILENAME
import org.ossreviewtoolkit.utils.ort.ortConfigDirectory

class EvaluatorCommandTest : StringSpec({
    Os.env[ORT_CONFIG_DIR_ENV_NAME] = tempdir().path

    "If no rules are specified, the default rules file should be required" {
        val args = "--check-syntax".split(' ')

        ortConfigDirectory.resolve(ORT_EVALUATOR_RULES_FILENAME) shouldNot exist()
        shouldThrow<FileNotFoundException> {
            EvaluatorCommand().parse(args)
        }.message shouldContain ORT_EVALUATOR_RULES_FILENAME
    }

    "If a rules resource is specified, the default rules file should not be required" {
        val args = "--check-syntax --rules-resource /rules/osadl.rules.kts".split(' ')

        ortConfigDirectory.resolve(ORT_EVALUATOR_RULES_FILENAME) shouldNot exist()
        shouldNotThrow<FileNotFoundException> {
            EvaluatorCommand().parse(args)
        }
    }
})
