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

/** User preferences persisted in the browser's local storage (theme is handled separately by next-themes). */
export interface Settings {
    /** Reflect the focused package / vulnerability in the URL (e.g. ?pkg-id=…, ?vul-id=…). */
    deepLinking: boolean;
    /** Result-table column ids to show by default; null means use the table's built-in defaults. */
    defaultVisibleColumns: string[] | null;
}

export const DEFAULT_SETTINGS: Settings = {
    deepLinking: true,
    defaultVisibleColumns: null,
};

const STORAGE_KEY = "ort-settings";

/** Read the persisted settings, falling back to defaults for anything missing or unparseable. */
export function loadSettings(): Settings {
    if (typeof window === "undefined") {
        return DEFAULT_SETTINGS;
    }
    try {
        const raw = window.localStorage.getItem(STORAGE_KEY);
        if (!raw) {
            return DEFAULT_SETTINGS;
        }
        const parsed = JSON.parse(raw) as Partial<Settings>;
        return {
            defaultVisibleColumns:
                Array.isArray(parsed.defaultVisibleColumns) &&
                parsed.defaultVisibleColumns.every((id) => typeof id === "string")
                    ? parsed.defaultVisibleColumns
                    : DEFAULT_SETTINGS.defaultVisibleColumns,
            deepLinking: typeof parsed.deepLinking === "boolean" ? parsed.deepLinking : DEFAULT_SETTINGS.deepLinking,
        };
    } catch {
        return DEFAULT_SETTINGS;
    }
}

/** Persist the settings; write failures (private mode, quota) are ignored so the app keeps working. */
export function saveSettings(settings: Settings): void {
    if (typeof window === "undefined") {
        return;
    }
    try {
        window.localStorage.setItem(STORAGE_KEY, JSON.stringify(settings));
    } catch {
        // Ignore: settings simply won't persist across reloads.
    }
}
