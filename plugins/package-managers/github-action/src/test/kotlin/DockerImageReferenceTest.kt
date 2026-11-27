/*
 * Copyright (C) 2026 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.packagemanagers.githubaction

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

class DockerImageReferenceTest : WordSpec({
    "parse()" should {
        "default to the latest tag" {
            DockerImageReference.parse("alpine") shouldBe DockerImageReference("", "", "alpine", "latest")
        }

        "parse the tag" {
            DockerImageReference.parse("alpine:3.20") shouldBe DockerImageReference("", "", "alpine", "3.20")
        }

        "parse the namespace" {
            DockerImageReference.parse("hashicorp/terraform:1.9") shouldBe
                DockerImageReference("", "hashicorp", "terraform", "1.9")
        }

        "parse the registry" {
            DockerImageReference.parse("ghcr.io/owner/group/image:1.0") shouldBe
                DockerImageReference("ghcr.io", "owner/group", "image", "1.0")
        }

        "parse a registry with a port" {
            DockerImageReference.parse("localhost:5000/image:1.0") shouldBe
                DockerImageReference("localhost:5000", "", "image", "1.0")
        }

        "prefer the digest over the tag" {
            DockerImageReference.parse("alpine:3.20@sha256:0123abcd") shouldBe
                DockerImageReference("", "", "alpine", "sha256:0123abcd")
        }
    }
})
