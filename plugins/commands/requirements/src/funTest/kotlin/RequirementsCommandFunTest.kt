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

package org.ossreviewtoolkit.plugins.commands.requirements

import com.github.ajalt.clikt.testing.test

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.shouldNot
import io.kotest.matchers.shouldNotBe

class RequirementsCommandFunTest : StringSpec({
    "All tool classes can be instantiated via reflection" {
        // Status code 1 is only returned if there was an error while instantiating a class via reflection.
        RequirementsCommand().test().statusCode shouldNotBe 1
    }

    "Core plugins are found" {
        val plugins = RequirementsCommand().getPluginsByType()

        plugins.keys shouldNot beEmpty()
        plugins.values.flatten() shouldNot beEmpty()
    }
})
