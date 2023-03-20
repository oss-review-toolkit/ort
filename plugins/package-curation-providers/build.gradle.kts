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
    // Apply core plugins.
    `java-platform`
    `maven-publish`
}

javaPlatform {
    allowDependencies()
}

dependencies {
    api(project(":plugins:package-curation-providers:clearly-defined-package-curation-provider"))
    api(project(":plugins:package-curation-providers:file-package-curation-provider"))
    api(project(":plugins:package-curation-providers:ort-config-package-curation-provider"))
    api(project(":plugins:package-curation-providers:sw360-package-curation-provider"))
}

configure<PublishingExtension> {
    publications {
        create<MavenPublication>(name) {
            groupId = "org.ossreviewtoolkit.plugins"

            from(components["javaPlatform"])

            pom {
                licenses {
                    license {
                        name.set("Apache-2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                    }
                }

                scm {
                    connection.set("scm:git:https://github.com/oss-review-toolkit/ort.git")
                    developerConnection.set("scm:git:git@github.com:oss-review-toolkit/ort.git")
                    tag.set(version.toString())
                    url.set("https://github.com/oss-review-toolkit/ort")
                }
            }
        }
    }
}
