/*
 * Copyright (C) 2020 Bosch.IO GmbH
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

package org.ossreviewtoolkit.clients.fossid.model.status

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * An enumeration for the state of a FossID scan, as returned by the "check_status" operation.
 */
enum class ScanStatus {
    /**
     * FossID is automatically applying top matched component.
     */
    @JsonProperty("AUTO-ID")
    AUTO_ID,

    /**
     * The scan has failed.
     */
    FAILED,

    /**
     * The scan has been completed.
     */
    FINISHED,

    /**
     * The scan has been stopped in the UI. The issue needs to be resolved manually and the scan restarted.
     */
    INTERRUPTED,

    /**
     * The scan has been created, but not started.
     */
    NEW,

    /**
     * The scan has not started yet.
     */
    @JsonProperty("NOT STARTED")
    NOT_STARTED,

    /**
     * The scan has been queued and is waiting for execution.
     */
    QUEUED,

    /**
     * The scan is running.
     */
    RUNNING,

    /**
     * The scan is running.
     */
    SCANNING,

    /**
     * The scan has started.
     */
    STARTED,

    /**
     * The scan is starting.
     */
    STARTING
}
