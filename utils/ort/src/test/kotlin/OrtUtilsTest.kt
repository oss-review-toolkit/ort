/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import kotlin.coroutines.EmptyCoroutineContext

import org.apache.logging.log4j.kotlin.CoroutineThreadContext
import org.apache.logging.log4j.kotlin.withLoggingContext

class OrtUtilsTest : WordSpec({
    "runBlocking" should {
        "preserve Log4j's MDC context which kotlinx.coroutines.runBlocking does not" {
            withLoggingContext(mapOf("key" to "value")) {
                @Suppress("ForbiddenMethodCall")
                kotlinx.coroutines.runBlocking(EmptyCoroutineContext) {
                    coroutineContext[CoroutineThreadContext.Key]?.contextData?.map?.get("key") should beNull()
                }

                runBlocking(EmptyCoroutineContext) {
                    coroutineContext[CoroutineThreadContext.Key]?.contextData?.map?.get("key") shouldBe "value"
                }
            }
        }
    }
})
