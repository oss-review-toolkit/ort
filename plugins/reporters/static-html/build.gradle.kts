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

plugins {
    // Apply precompiled plugins.
    id("ort-library-conventions")
}

dependencies {
    api(project(":reporter"))

    implementation(project(":downloader"))
    implementation(project(":model"))
    implementation(project(":utils:common-utils"))
    implementation(project(":utils:ort-utils"))
    implementation(project(":utils:spdx-utils"))

    implementation(libs.flexmark)
    implementation(libs.kotlinxHtml)

    // This is required to not depend on the version of Apache Xalan bundled with the JDK. Otherwise, the formatting of
    // the HTML generated in StaticHtmlReporter is slightly different with different Java versions.
    runtimeOnly(libs.saxonHe)
}
