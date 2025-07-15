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

package org.ossreviewtoolkit.utils.ort

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.utils.common.Os

class JavaBootstrapperTest : StringSpec({
    "The Java version running the test should be detected as a JDK" {
        JavaBootstrapper.isRunningOnJdk(Environment.JAVA_VERSION) shouldBe true
    }

    "A JDK for Temurin 21 can be found" {
        JavaBootstrapper.findJdkPackage("TEMURIN", "21") shouldBeSuccess {
            it.distribution shouldBe "temurin"
            it.jdkVersion shouldBe 21
            Os.Name.fromString(it.operatingSystem) shouldBe Os.Name.current
            Os.Arch.fromString(it.architecture) shouldBe Os.Arch.current
        }
    }
})
