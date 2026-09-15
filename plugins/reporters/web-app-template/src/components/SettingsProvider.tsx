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

import { createContext, type JSX, type ReactNode, useCallback, useContext, useMemo, useState } from "react";

import { loadSettings, type Settings, saveSettings } from "@/lib/settings";

interface SettingsContextValue {
    settings: Settings;
    updateSettings: (partial: Partial<Settings>) => void;
}

const SettingsContext = createContext<SettingsContextValue | null>(null);

/**
 * Holds the user's persisted preferences and re-applies them on load. Settings are read from local storage
 * once on mount and written back on every change, so components can read them synchronously via useSettings.
 */
function SettingsProvider({ children }: { children: ReactNode }): JSX.Element {
    const [settings, setSettings] = useState<Settings>(() => loadSettings());

    const updateSettings = useCallback((partial: Partial<Settings>): void => {
        setSettings((prev) => {
            const next = { ...prev, ...partial };
            saveSettings(next);
            return next;
        });
    }, []);

    const value = useMemo<SettingsContextValue>(() => ({ settings, updateSettings }), [settings, updateSettings]);

    return <SettingsContext.Provider value={value}>{children}</SettingsContext.Provider>;
}

function useSettings(): SettingsContextValue {
    const context = useContext(SettingsContext);
    if (!context) {
        throw new Error("useSettings must be used within a SettingsProvider.");
    }
    return context;
}

export { SettingsProvider, useSettings };
