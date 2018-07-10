/*
 * Copyright (C) 2017-2018 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

package com.here.ort.utils

import com.here.ort.analyzer.managers.NPM

import io.kotlintest.shouldBe
import io.kotlintest.specs.WordSpec

class NpmTest : WordSpec({
    "expandShortcutURL" should {
        "do nothing for empty URLs" {
            NPM.expandShortcutURL("") shouldBe ""
        }

        "properly handle NPM shortcut URLs" {
            val packages = mapOf(
                    "npm/npm"
                            to "https://github.com/npm/npm.git",
                    "gist:11081aaa281"
                            to "https://gist.github.com/11081aaa281",
                    "bitbucket:example/repo"
                            to "https://bitbucket.org/example/repo.git",
                    "gitlab:another/repo"
                            to "https://gitlab.com/another/repo.git"
            )

            packages.forEach { actualUrl, expectedUrl ->
                NPM.expandShortcutURL(actualUrl) shouldBe expectedUrl
            }
        }

        "not mess with crazy URLs" {
            val packages = mapOf(
                    "git@github.com/cisco/node-jose.git"
                            to "git@github.com/cisco/node-jose.git",
                    "https://git@github.com:hacksparrow/node-easyimage.git"
                            to "https://git@github.com:hacksparrow/node-easyimage.git",
                    "github.com/improbable-eng/grpc-web"
                            to "github.com/improbable-eng/grpc-web"
            )

            packages.forEach { actualUrl, expectedUrl ->
                NPM.expandShortcutURL(actualUrl) shouldBe expectedUrl
            }
        }
    }
})
