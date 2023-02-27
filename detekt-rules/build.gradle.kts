/*
 * Copyright (C) 2020 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import org.gradle.internal.logging.text.StyledTextOutput
import org.gradle.internal.logging.text.StyledTextOutputFactory
import org.gradle.kotlin.dsl.support.serviceOf

plugins {
    // Apply core plugins.
    `java-library`
}

dependencies {
    compileOnly(libs.detektApi)

    testImplementation(libs.detektApi)
    testImplementation(libs.detektTest)
}

tasks.named<Jar>("jar").configure {
    doLast {
        val out = serviceOf<StyledTextOutputFactory>().create("detekt-rules")
        val message = "The detekt-rules have changed. You need to stop the Gradle daemon to allow the detekt plugin " +
                "to reload for the rule changes to take effect."
        out.withStyle(StyledTextOutput.Style.Info).println(message)
    }
}
