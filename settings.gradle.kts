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
include(":helper-cli")
include(":model")
include(":notifier")
include(":plugins:commands")
include(":plugins:commands:advisor")
include(":plugins:commands:analyzer")
include(":plugins:commands:api")
include(":plugins:commands:config")
include(":plugins:commands:downloader")
include(":plugins:commands:evaluator")
include(":plugins:commands:notifier")
include(":plugins:commands:reporter")
include(":plugins:commands:requirements")
include(":plugins:package-curation-providers")
include(":plugins:package-curation-providers:api")
include(":plugins:package-curation-providers:clearly-defined")
include(":plugins:package-curation-providers:file")
include(":plugins:package-curation-providers:ort-config")
include(":plugins:package-curation-providers:sw360")
include(":plugins:package-managers")
include(":plugins:package-managers:bower")
include(":plugins:package-managers:bundler")
include(":plugins:package-managers:cargo")
include(":plugins:package-managers:carthage")
include(":plugins:package-managers:cocoapods")
include(":plugins:package-managers:composer")
include(":plugins:package-managers:conan")
include(":plugins:package-managers:gradle")
include(":plugins:package-managers:gradle-inspector")
include(":plugins:package-managers:gradle-model")
include(":plugins:package-managers:gradle-plugin")
include(":plugins:package-managers:node")
include(":plugins:package-managers:nuget")
include(":plugins:package-managers:pub")
include(":plugins:package-managers:python")
include(":plugins:package-managers:spdx")
include(":plugins:package-managers:stack")
include(":plugins:package-managers:unmanaged")
include(":plugins:reporters")
include(":plugins:reporters:asciidoc")
include(":plugins:reporters:ctrlx")
include(":plugins:reporters:cyclonedx")
include(":plugins:reporters:evaluated-model")
include(":plugins:reporters:fossid")
include(":plugins:reporters:freemarker")
include(":plugins:reporters:gitlab")
include(":plugins:reporters:opossum")
include(":plugins:reporters:spdx")
include(":plugins:reporters:static-html")
include(":plugins:reporters:web-app")
include(":plugins:reporters:web-app-template")
include(":reporter")
include(":scanner")
include(":utils:common")
include(":utils:ort")
include(":utils:scripting")
include(":utils:spdx")
include(":utils:test")

project(":clients:clearly-defined").name = "clearly-defined-client"
project(":clients:fossid-webapp").name = "fossid-webapp-client"
project(":clients:github-graphql").name = "github-graphql-client"
project(":clients:nexus-iq").name = "nexus-iq-client"
project(":clients:oss-index").name = "oss-index-client"
project(":clients:osv").name = "osv-client"
project(":clients:scanoss").name = "scanoss-client"
project(":clients:vulnerable-code").name = "vulnerable-code-client"

project(":plugins:commands:advisor").name = "advisor-command"
project(":plugins:commands:analyzer").name = "analyzer-command"
project(":plugins:commands:api").name = "command-api"
project(":plugins:commands:config").name = "config-command"
project(":plugins:commands:downloader").name = "downloader-command"
project(":plugins:commands:evaluator").name = "evaluator-command"
project(":plugins:commands:notifier").name = "notifier-command"
project(":plugins:commands:reporter").name = "reporter-command"
project(":plugins:commands:requirements").name = "requirements-command"

project(":plugins:package-curation-providers:api").name = "package-curation-provider-api"
project(":plugins:package-curation-providers:clearly-defined").name = "clearly-defined-package-curation-provider"
project(":plugins:package-curation-providers:file").name = "file-package-curation-provider"
project(":plugins:package-curation-providers:ort-config").name = "ort-config-package-curation-provider"
project(":plugins:package-curation-providers:sw360").name = "sw360-package-curation-provider"

project(":plugins:package-managers:bower").name = "bower-package-manager"
project(":plugins:package-managers:bundler").name = "bundler-package-manager"
project(":plugins:package-managers:cargo").name = "cargo-package-manager"
project(":plugins:package-managers:carthage").name = "carthage-package-manager"
project(":plugins:package-managers:cocoapods").name = "cocoapods-package-manager"
project(":plugins:package-managers:composer").name = "composer-package-manager"
project(":plugins:package-managers:conan").name = "conan-package-manager"
project(":plugins:package-managers:gradle").name = "gradle-package-manager"
project(":plugins:package-managers:node").name = "node-package-manager"
project(":plugins:package-managers:nuget").name = "nuget-package-manager"
project(":plugins:package-managers:pub").name = "pub-package-manager"
project(":plugins:package-managers:python").name = "python-package-manager"
project(":plugins:package-managers:spdx").name = "spdx-package-manager"
project(":plugins:package-managers:stack").name = "stack-package-manager"
project(":plugins:package-managers:unmanaged").name = "unmanaged-package-manager"

project(":plugins:reporters:asciidoc").name = "asciidoc-reporter"
project(":plugins:reporters:ctrlx").name = "ctrlx-reporter"
project(":plugins:reporters:cyclonedx").name = "cyclonedx-reporter"
project(":plugins:reporters:evaluated-model").name = "evaluated-model-reporter"
project(":plugins:reporters:fossid").name = "fossid-reporter"
project(":plugins:reporters:freemarker").name = "freemarker-reporter"
project(":plugins:reporters:gitlab").name = "gitlab-reporter"
project(":plugins:reporters:opossum").name = "opossum-reporter"
project(":plugins:reporters:spdx").name = "spdx-reporter"
project(":plugins:reporters:static-html").name = "static-html-reporter"
project(":plugins:reporters:web-app").name = "web-app-reporter"

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

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Gradle cannot access the version catalog from here, so hard-code the dependency.
    id("org.gradle.toolchains.foojay-resolver-convention").version("0.5.0")
}
