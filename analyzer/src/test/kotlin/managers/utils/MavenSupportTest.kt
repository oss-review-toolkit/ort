/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

package com.here.ort.analyzer.managers.utils

import com.here.ort.model.VcsInfo
import com.here.ort.model.VcsType

import io.kotlintest.shouldBe
import io.kotlintest.specs.WordSpec

import org.apache.maven.model.Scm
import org.apache.maven.project.MavenProject

class MavenSupportTest : WordSpec({
    "parseVcsInfo()" should {
        "handle GitRepo URLs" {
            val mavenProject = MavenProject().apply {
                scm = Scm().apply {
                    connection = "scm:git-repo:ssh://host.com/project/foo?path/to/manifest.xml"
                    tag = "v1.2.3"
                }
            }

            MavenSupport.parseVcsInfo(mavenProject) shouldBe VcsInfo(
                type = VcsType.GIT_REPO,
                url = "ssh://host.com/project/foo",
                revision = "v1.2.3",
                path = "path/to/manifest.xml"
            )
        }
    }
})
