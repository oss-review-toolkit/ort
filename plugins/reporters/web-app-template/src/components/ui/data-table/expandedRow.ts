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

import type { ExpandedState } from "@tanstack/react-table";

/**
 * Return the id of the most-recently-expanded row, or undefined when no row is expanded.
 *
 * TanStack Table appends a newly expanded row's id to the `expanded` record and deletes it again on
 * collapse, so the last open key is always the row the user just opened. Deep-link handlers rely on this:
 * expanding another row while earlier ones stay open moves the link to the newly opened row, rather than
 * sticking to whichever row happens to come first in the record.
 */
export function getActiveExpandedRowId(expanded: ExpandedState | undefined): string | undefined {
    if (!expanded || expanded === true) {
        return undefined;
    }
    let activeId: string | undefined;
    for (const [id, isExpanded] of Object.entries(expanded)) {
        if (isExpanded) {
            activeId = id;
        }
    }
    return activeId;
}
