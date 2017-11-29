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

class K9MailTest : BaseGradleSpec() {

    override val pkg = Package(
            packageManager = "Gradle",
            namespace = "com.fsck.k9",
            name = "k9mail",
            version = "",
            declaredLicenses = sortedSetOf(),
            description = "",
            homepageUrl = "",
            binaryArtifact = RemoteArtifact.createEmpty(),
            sourceArtifact = RemoteArtifact.createEmpty(),
            vcsProvider = "Git",
            vcsUrl = "https://github.com/k9mail/k-9.git",
            vcsRevision = "934bbbe88299b1d468315917924123e4a8b89883",
            vcsPath = ""
    )

    override val expectedResultsDir = ""

}
