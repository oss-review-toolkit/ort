/*
 * Copyright (C) 2019 Bosch Software Innovations GmbH
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

package org.ossreviewtoolkit.utils

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs

import io.mockk.mockk
import io.mockk.verify

import org.apache.logging.log4j.Level
import org.apache.logging.log4j.kotlin.KotlinLogger

class LoggerTest : WordSpec({
    "A logger instance" should {
        "be shared between different instances of the same class" {
            val a = String().log
            val b = String().log

            a shouldBeSameInstanceAs b
        }

        "not be shared between instances of different classes" {
            val a = String().log
            val b = this.log

            a shouldNotBeSameInstanceAs b
        }
    }

    "Asking to log a statement only once" should {
        class DummyClass

        val message = "This message should be logged only once."

        "log the statement only once for the same instance and level" {
            val dummyInstance = DummyClass()

            val mockedLogger = mockk<KotlinLogger>(relaxed = true)
            loggerOfClass[DummyClass::class.java] = mockedLogger

            // Log the same message at the same level for the same instance twice.
            dummyInstance.logOnce(Level.WARN) { message }
            dummyInstance.logOnce(Level.WARN) { message }

            dummyInstance.log shouldBeSameInstanceAs mockedLogger
            verify(exactly = 1) { mockedLogger.log(any(), any<String>()) }
        }

        "still show the statement multiple times for different levels" {
            val dummyInstance = DummyClass()

            val mockedLogger = mockk<KotlinLogger>(relaxed = true)
            loggerOfClass[DummyClass::class.java] = mockedLogger

            // Log the same message at the different levels for the same instance.
            dummyInstance.logOnce(Level.WARN) { message }
            dummyInstance.logOnce(Level.ERROR) { message }

            dummyInstance.log shouldBeSameInstanceAs mockedLogger
            verify(exactly = 2) { mockedLogger.log(any(), any<String>()) }
        }

        "still show the statement multiple times for different instances" {
            val dummyInstance1 = DummyClass()
            val dummyInstance2 = DummyClass()

            val mockedLogger = mockk<KotlinLogger>(relaxed = true)
            loggerOfClass[DummyClass::class.java] = mockedLogger

            // Log the same message at the same level for different instances.
            dummyInstance1.logOnce(Level.WARN) { message }
            dummyInstance2.logOnce(Level.WARN) { message }

            dummyInstance1.log shouldBeSameInstanceAs mockedLogger
            dummyInstance2.log shouldBeSameInstanceAs mockedLogger
            verify(exactly = 2) { mockedLogger.log(any(), any<String>()) }
        }
    }
})
