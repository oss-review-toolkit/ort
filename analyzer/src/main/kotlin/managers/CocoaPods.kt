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

package com.here.ort.analyzer.managers

import com.here.ort.analyzer.AbstractPackageManagerFactory
import com.here.ort.analyzer.PackageManager
import com.here.ort.model.ProjectAnalyzerResult
import com.here.ort.model.config.AnalyzerConfiguration
import com.here.ort.model.config.RepositoryConfiguration

import java.io.File

/**
 * The CocoaPods package manager for Objective-C, see https://cocoapods.org/.
 */
class CocoaPods(name: String, analyzerConfig: AnalyzerConfiguration, repoConfig: RepositoryConfiguration) :
        PackageManager(name, analyzerConfig, repoConfig) {
    class Factory : AbstractPackageManagerFactory<CocoaPods>("CocoaPods") {
        override val globsForDefinitionFiles = listOf("Podfile.lock", "Podfile")

        override fun create(analyzerConfig: AnalyzerConfiguration, repoConfig: RepositoryConfiguration) =
                CocoaPods(managerName, analyzerConfig, repoConfig)
    }

    override fun resolveDependencies(definitionFile: File): ProjectAnalyzerResult? {
        TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
    }
}
