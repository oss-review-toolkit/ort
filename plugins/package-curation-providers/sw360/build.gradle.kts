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
    id("ort-plugin-conventions")
}

dependencies {
    api(projects.plugins.packageCurationProviders.packageCurationProviderApi)

    ksp(projects.plugins.packageCurationProviders.packageCurationProviderApi)

    implementation(libs.sw360Client) {
        constraints {
            implementation("commons-io:commons-io:2.20.0")
                .because("commons-io 2.11.0 is vulnerable by CVE-2024-47554")
        }
    }
}
