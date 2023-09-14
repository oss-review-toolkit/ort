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
    `maven-publish`
    signing

    // Apply precompiled plugins.
    id("ort-kotlin-conventions")
}

configure<PublishingExtension> {
    publications {
        val publicationName = name.replace(Regex("([a-z])-([a-z])")) {
            "${it.groupValues[1]}${it.groupValues[2].uppercase()}"
        }

        create<MavenPublication>(publicationName) {
            fun getGroupId(parent: Project?): String =
                parent?.let { "${getGroupId(it.parent)}.${it.name.replace("-", "")}" }.orEmpty()

            groupId = "org${getGroupId(parent)}"

            from(components["java"])
            artifact(tasks["sourcesJar"])
            artifact(tasks["docsJavadocJar"])

            pom {
                name = project.name
                description = "Part of the OSS Review Toolkit (ORT), a suite to automate software compliance checks."
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
    }

    repositories {
        maven {
            name = "OSSRH"

            val releasesRepoUrl = "https://oss.sonatype.org/service/local/staging/deploy/maven2"
            val snapshotsRepoUrl = "https://oss.sonatype.org/content/repositories/snapshots"
            url = uri(if (version.toString().endsWith("-SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl)

            credentials {
                username = System.getenv("OSSRH_USERNAME") ?: return@credentials
                password = System.getenv("OSSRH_PASSWORD") ?: return@credentials
            }
        }
    }
}

signing {
    val signingKey = System.getenv("SIGNING_KEY")
    val signingPassword = System.getenv("SIGNING_PASSWORD")
    useInMemoryPgpKeys(signingKey, signingPassword)

    setRequired {
        // Do not require signing for `PublishToMavenLocal` tasks only.
        gradle.taskGraph.allTasks.any { it is PublishToMavenRepository }
    }

    sign(publishing.publications)
}
