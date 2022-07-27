/*
 * Copyright (C) 2019 Bosch Software Innovations GmbH
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

package org.ossreviewtoolkit.utils.ort

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs

import test.other.OrtLogTestExtension

private class DummyClass
private class OtherClass

class LoggerTest : WordSpec({
    "A logger instance" should {
        "be shared between different instances of the same class" {
            val a = DummyClass().log
            val b = DummyClass().log

            a shouldBeSameInstanceAs b
        }

        "not be shared between instances of different classes" {
            val a = DummyClass().log
            val b = OtherClass().log

            a shouldNotBeSameInstanceAs b
        }

        "refuse to be used on a non-ORT class" {
            shouldThrow<IllegalArgumentException> {
                String().log.info { "Hello from a String." }
            }
        }

        "be available in non-ORT classes extending ORT base classes" {
            val command = OrtLogTestExtension()

            command.command() shouldBe "success"
        }
    }
})
