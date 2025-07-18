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
    // Apply third-party plugins.
    id("com.vanniktech.maven.publish")
}

runCatching {
    components["java"] as AdhocComponentWithVariants
}.onSuccess {
    it.withVariantsFromConfiguration(configurations["funTestApiElements"]) { skip() }
    it.withVariantsFromConfiguration(configurations["funTestRuntimeElements"]) { skip() }
}

fun getGroupId(parent: Project?): String =
    parent?.let { "${getGroupId(it.parent)}.${it.name.replace("-", "")}" }.orEmpty()

group = "org${getGroupId(parent)}"

mavenPublishing {
    publishToMavenCentral()

    pom {
        name = project.name
        description = provider {
            project.description
                ?: "Part of the OSS Review Toolkit (ORT), a suite to automate software compliance checks."
        }
        url = "https://oss-review-toolkit.org/"

        developers {
            developer {
                name = "The ORT Project Authors"
                email = "ort@ossreviewtoolkit.org"
                url = "https://github.com/oss-review-toolkit/ort/blob/main/NOTICE"
            }
        }

        licenses {
            license {
                name = "Apache-2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0"
            }
        }

        scm {
            connection = "scm:git:https://github.com/oss-review-toolkit/ort.git"
            developerConnection = "scm:git:git@github.com:oss-review-toolkit/ort.git"
            tag = version.toString()
            url = "https://github.com/oss-review-toolkit/ort"
        }
    }
}
