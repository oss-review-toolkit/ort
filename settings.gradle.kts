/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 * Copyright (C) 2019 Bosch Software Innovations GmbH
 * Copyright (C) 2020 Bosch.IO GmbH
 * Copyright (C) 2021 Sonatype, Inc.
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

rootProject.name = "oss-review-toolkit"

include(":advisor")
include(":analyzer")
include(":cli")
include(":clients:clearly-defined")
include(":clients:fossid-webapp")
include(":clients:github-graphql")
include(":clients:nexus-iq")
include(":clients:oss-index")
include(":clients:osv")
include(":clients:scanoss")
include(":clients:vulnerable-code")
include(":detekt-rules")
include(":downloader")
include(":evaluator")
include(":examples:evaluator-rules")
include(":examples:notifications")
include(":helper-cli")
include(":model")
include(":notifier")
include(":reporter")
include(":reporter-web-app")
include(":scanner")
include(":utils:common")
include(":utils:ort")
include(":utils:scripting")
include(":utils:spdx")
include(":utils:test")

project(":utils:common").name = "common-utils"
project(":utils:ort").name = "ort-utils"
project(":utils:scripting").name = "scripting-utils"
project(":utils:spdx").name = "spdx-utils"
project(":utils:test").name = "test-utils"

val buildCacheRetentionDays: String by settings

buildCache {
    local {
        removeUnusedEntriesAfterDays = buildCacheRetentionDays.toInt()
    }
}
