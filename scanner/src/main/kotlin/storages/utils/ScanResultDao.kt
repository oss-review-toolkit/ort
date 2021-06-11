/*
 * Copyright (C) 2021 HERE Europe B.V.
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

package org.ossreviewtoolkit.scanner.storages.utils

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Column

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.ScanResult

object ScanResults : IntIdTable("scan_results") {
    val identifier: Column<String> = text("identifier").index("identifier")
    val scanResult: Column<ScanResult> = jsonb("scan_result", ScanResult::class)
}

class ScanResultDao(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<ScanResultDao>(ScanResults)

    var identifier: Identifier by ScanResults.identifier.transform({ it.toCoordinates() }, { Identifier(it) })
    var scanResult: ScanResult by ScanResults.scanResult
}
