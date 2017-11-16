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

package com.here.ort.analyzer.managers

import com.here.ort.analyzer.PackageManager
import com.here.ort.analyzer.PackageManagerFactory

import java.io.File

class SBT : PackageManager() {
    companion object : PackageManagerFactory<SBT>(
            "http://www.scala-sbt.org/",
            "Scala",
            listOf("build.sbt", "build.scala")
    ) {
        override fun create() = SBT()
    }

    override fun command(workingDir: File): String {
        return "sbt"
    }
}
