/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

package org.ossreviewtoolkit.analyzer.integration

import org.ossreviewtoolkit.analyzer.PackageManagerFactory
import org.ossreviewtoolkit.analyzer.managers.Composer
import org.ossreviewtoolkit.analyzer.managers.Npm
import org.ossreviewtoolkit.analyzer.managers.Yarn
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType

class DrupalIntegrationFunTest : AbstractIntegrationSpec() {
    override val pkg = Package(
        id = Identifier(
            type = "Composer",
            namespace = "",
            name = "Drupal",
            version = ""
        ),
        declaredLicenses = sortedSetOf(),
        description = "",
        homepageUrl = "",
        binaryArtifact = RemoteArtifact.EMPTY,
        sourceArtifact = RemoteArtifact.EMPTY,
        vcs = VcsInfo(
            type = VcsType.GIT,
            url = "https://github.com/drupal/drupal.git",
            revision = "4a765491d80d1bcb11e542ffafccf10aef05b853"
        )
    )

    override val expectedManagedFiles by lazy {
        mapOf(
            Composer.Factory() as PackageManagerFactory to listOf(
                outputDir.resolve("core/lib/Drupal/Component/Uuid/composer.json"),
                outputDir.resolve("core/lib/Drupal/Component/Utility/composer.json"),
                outputDir.resolve("core/lib/Drupal/Component/Transliteration/composer.json"),
                outputDir.resolve("core/lib/Drupal/Component/Serialization/composer.json"),
                outputDir.resolve("core/lib/Drupal/Component/Render/composer.json"),
                outputDir.resolve("core/lib/Drupal/Component/ProxyBuilder/composer.json"),
                outputDir.resolve("core/lib/Drupal/Component/Plugin/composer.json"),
                outputDir.resolve("core/lib/Drupal/Component/PhpStorage/composer.json"),
                outputDir.resolve("core/lib/Drupal/Component/HttpFoundation/composer.json"),
                outputDir.resolve("core/lib/Drupal/Component/Graph/composer.json"),
                outputDir.resolve("core/lib/Drupal/Component/Gettext/composer.json"),
                outputDir.resolve("core/lib/Drupal/Component/FileSystem/composer.json"),
                outputDir.resolve("core/lib/Drupal/Component/FileCache/composer.json"),
                outputDir.resolve("core/lib/Drupal/Component/EventDispatcher/composer.json"),
                outputDir.resolve("core/lib/Drupal/Component/Discovery/composer.json"),
                outputDir.resolve("core/lib/Drupal/Component/Diff/composer.json"),
                outputDir.resolve("core/lib/Drupal/Component/DependencyInjection/composer.json"),
                outputDir.resolve("core/lib/Drupal/Component/Datetime/composer.json"),
                outputDir.resolve("core/lib/Drupal/Component/ClassFinder/composer.json"),
                outputDir.resolve("core/lib/Drupal/Component/Bridge/composer.json"),
                outputDir.resolve("core/lib/Drupal/Component/Assertion/composer.json"),
                outputDir.resolve("core/lib/Drupal/Component/Annotation/composer.json"),
                outputDir.resolve("core/composer.json"),
                outputDir.resolve("composer.json")
            ),
            Npm.Factory() as PackageManagerFactory to listOf(
                outputDir.resolve("core/package.json"),
                outputDir.resolve("core/assets/vendor/jquery.ui/package.json")
            ),
            Yarn.Factory() as PackageManagerFactory to listOf(
                outputDir.resolve("core/package.json"),
                outputDir.resolve("core/assets/vendor/jquery.ui/package.json")
            )
        )
    }

    override val managedFilesForTest by lazy {
        mapOf(
            // Limit to definition files that come long with a lock file.
            Composer.Factory() as PackageManagerFactory to listOf(outputDir.resolve("composer.json"))
        )
    }
}
