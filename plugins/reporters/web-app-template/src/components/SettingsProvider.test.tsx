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

import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { JSX } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { SettingsProvider, useSettings } from "@/components/SettingsProvider";

// A minimal consumer that surfaces one setting and lets a click flip it through the provider's setter.
function Probe(): JSX.Element {
    const { settings, updateSettings } = useSettings();
    return (
        <div>
            <span>deepLinking={String(settings.deepLinking)}</span>
            <button onClick={() => updateSettings({ deepLinking: !settings.deepLinking })} type="button">
                toggle
            </button>
        </div>
    );
}

describe("SettingsProvider", () => {
    beforeEach(() => {
        // Persisted settings survive between tests via the in-memory localStorage stub; reset for determinism.
        window.localStorage.clear();
    });

    it("provides the default settings to consumers", () => {
        render(
            <SettingsProvider>
                <Probe />
            </SettingsProvider>,
        );
        expect(screen.getByText("deepLinking=true")).toBeInTheDocument();
    });

    it("updates the settings when a consumer calls updateSettings", async () => {
        const user = userEvent.setup();
        render(
            <SettingsProvider>
                <Probe />
            </SettingsProvider>,
        );

        expect(screen.getByText("deepLinking=true")).toBeInTheDocument();
        await user.click(screen.getByRole("button", { name: "toggle" }));

        expect(screen.getByText("deepLinking=false")).toBeInTheDocument();
    });

    it("throws when useSettings is used outside a SettingsProvider", () => {
        // React logs the render error; silence it so the test output stays clean.
        const spy = vi.spyOn(console, "error").mockImplementation(() => {});
        expect(() => render(<Probe />)).toThrow(/useSettings must be used within a SettingsProvider/);
        spy.mockRestore();
    });
});
