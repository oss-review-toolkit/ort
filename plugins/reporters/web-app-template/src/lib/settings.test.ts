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

import { beforeEach, describe, expect, it } from "vitest";

import { DEFAULT_SETTINGS, loadSettings, type Settings, saveSettings } from "@/lib/settings";

const STORAGE_KEY = "ort-settings";

describe("settings", () => {
    beforeEach(() => {
        window.localStorage.clear();
    });

    it("defaults to no column override, deep linking on and the report's own date format", () => {
        expect(DEFAULT_SETTINGS).toEqual({
            defaultVisibleColumns: null,
            deepLinking: true,
            useBrowserDateFormat: false,
        });
    });

    it("returns the defaults when nothing is stored", () => {
        expect(loadSettings()).toEqual(DEFAULT_SETTINGS);
    });

    it("round-trips saved settings", () => {
        const settings: Settings = {
            defaultVisibleColumns: ["package", "scopes"],
            deepLinking: false,
            useBrowserDateFormat: true,
        };
        saveSettings(settings);
        expect(loadSettings()).toEqual(settings);
    });

    it("persists as JSON under the expected storage key", () => {
        saveSettings({ defaultVisibleColumns: null, deepLinking: false, useBrowserDateFormat: false });
        expect(window.localStorage.getItem(STORAGE_KEY)).toBe(
            JSON.stringify({ defaultVisibleColumns: null, deepLinking: false, useBrowserDateFormat: false }),
        );
    });

    it("falls back to the defaults for unparseable JSON", () => {
        window.localStorage.setItem(STORAGE_KEY, "{ not json");
        expect(loadSettings()).toEqual(DEFAULT_SETTINGS);
    });

    it("fills in missing fields from the defaults", () => {
        window.localStorage.setItem(STORAGE_KEY, JSON.stringify({ deepLinking: false }));
        expect(loadSettings()).toEqual({
            defaultVisibleColumns: null,
            deepLinking: false,
            useBrowserDateFormat: false,
        });
    });

    it("ignores a defaultVisibleColumns value that is not a string array", () => {
        window.localStorage.setItem(STORAGE_KEY, JSON.stringify({ defaultVisibleColumns: [1, 2], deepLinking: true }));
        expect(loadSettings().defaultVisibleColumns).toBeNull();

        window.localStorage.setItem(STORAGE_KEY, JSON.stringify({ defaultVisibleColumns: "package" }));
        expect(loadSettings().defaultVisibleColumns).toBeNull();
    });

    it("preserves an explicit empty column list", () => {
        saveSettings({ defaultVisibleColumns: [], deepLinking: true, useBrowserDateFormat: false });
        expect(loadSettings().defaultVisibleColumns).toEqual([]);
    });

    it("ignores a non-boolean useBrowserDateFormat value", () => {
        window.localStorage.setItem(STORAGE_KEY, JSON.stringify({ useBrowserDateFormat: "yes" }));
        expect(loadSettings().useBrowserDateFormat).toBe(DEFAULT_SETTINGS.useBrowserDateFormat);
    });

    it("keeps useBrowserDateFormat:true", () => {
        window.localStorage.setItem(STORAGE_KEY, JSON.stringify({ useBrowserDateFormat: true }));
        expect(loadSettings().useBrowserDateFormat).toBe(true);
    });

    it("ignores a non-boolean deepLinking value", () => {
        window.localStorage.setItem(STORAGE_KEY, JSON.stringify({ deepLinking: "yes" }));
        expect(loadSettings().deepLinking).toBe(DEFAULT_SETTINGS.deepLinking);
    });

    it("keeps deepLinking:false (does not treat a falsy value as missing)", () => {
        window.localStorage.setItem(STORAGE_KEY, JSON.stringify({ defaultVisibleColumns: null, deepLinking: false }));
        expect(loadSettings().deepLinking).toBe(false);
    });
});
