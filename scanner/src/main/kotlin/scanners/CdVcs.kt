/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
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

package com.here.ort.scanner.scanners

import com.here.ort.model.Package
import com.here.ort.model.VcsInfo
import com.here.ort.scanner.ScanException
import com.here.ort.scanner.Scanner
import com.here.ort.utils.jsonMapper
import com.here.ort.utils.log

import java.io.File

object CdVcs : Scanner() {

    override val resultFileExtension = "json"

    override fun canScan(pkg: Package): Boolean {
        return true
    }

    override fun scanPath(path: File, resultsFile: File): Result {
        val vcsInfoFile = File(path, "vscinfo.json") 
        val vcsInfo = jsonMapper.readValue(vcsInfoFile, VcsInfo::class.java)
        jsonMapper.writerWithDefaultPrettyPrinter().writeValue(resultsFile, vcsInfo)
        return Result(sortedSetOf<String>(), sortedSetOf<String>())
    }

    override fun getResult(resultsFile: File): Result {
        return Result(sortedSetOf<String>(), sortedSetOf<String>())
    }
}
