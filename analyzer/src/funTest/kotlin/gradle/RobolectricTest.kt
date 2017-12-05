/*
 * Copyright (c) 2017 HERE Europe B.V.
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

package com.here.ort.analyzer.integration

import com.here.ort.model.Package
import com.here.ort.model.RemoteArtifact

class RobolectricTest : BaseGradleSpec() {

    override val pkg = Package(
            packageManager = "Gradle",
            namespace = "org.robolectric",
            name = "robolectric",
            version = "3.3.2",
            declaredLicenses = sortedSetOf(),
            description = "",
            homepageUrl = "",
            binaryArtifact = RemoteArtifact.EMPTY,
            sourceArtifact = RemoteArtifact.EMPTY,
            vcsProvider = "Git",
            vcsUrl = "https://github.com/robolectric/robolectric.git",
            vcsRevision = "757dfd56499a415376ea04bfa520b317bf2e3b58",
            vcsPath = ""
    )

    override val expectedResultsDir = "src/funTest/assets/projects/synthetic/gradle-expected-results/robolectric/"

}
