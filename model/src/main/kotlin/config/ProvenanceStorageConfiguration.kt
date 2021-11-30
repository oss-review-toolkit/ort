/*
 * Copyright (C) 2021 Bosch.IO GmbH
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

package org.ossreviewtoolkit.model.config

import org.ossreviewtoolkit.utils.core.log

/**
 * Configuration of the storage to use for provenance information.
 */
data class ProvenanceStorageConfiguration(
    /**
     * Configuration of a file storage.
     */
    val fileStorage: FileStorageConfiguration? = null,

    /**
     * Configuration of a PostgreSQL storage.
     */
    val postgresStorage: PostgresStorageConfiguration? = null,
) {
    init {
        if (fileStorage != null && postgresStorage != null) {
            log.warn {
                "'fileStorage' and 'postgresStorage' are both configured but only one storage can be used. Using " +
                        "'fileStorage'."
            }
        }
    }
}
