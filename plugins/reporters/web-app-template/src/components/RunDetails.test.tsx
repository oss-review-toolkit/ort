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

import { screen } from "@testing-library/react";
import { beforeAll, describe, expect, it } from "vitest";
import { RunDetails } from "@/components/RunDetails";
import type WebAppEvaluatedModel from "@/models/WebAppEvaluatedModel";
import { buildResult, loadSampleEvaluatedModel } from "@/test/fixture";
import { render } from "@/test/render";

describe("RunDetails", () => {
    let result: WebAppEvaluatedModel;

    beforeAll(async () => {
        result = await buildResult(loadSampleEvaluatedModel());
    });

    it("renders a tab per available run-configuration section", () => {
        render(<RunDetails webAppEvaluatedModel={result} />);
        // The sample report carries an .ort.yml and always exposes the Tools metadata.
        expect(screen.getByRole("tab", { name: ".ort.yml" })).toBeInTheDocument();
        expect(screen.getByRole("tab", { name: /tools/i })).toBeInTheDocument();
    });

    // jsdom has no layout engine, so the geometry cannot be asserted here. These guard the class
    // contract that makes the switcher wrap instead of overflowing, which is what actually regressed:
    // the tabs used to sit outside the switcher in a narrow window.
    it("lets the switcher wrap rather than pinning it to one row's height", () => {
        render(<RunDetails webAppEvaluatedModel={result} />);
        const list = screen.getByRole("tablist");

        expect(list.className).toContain("flex-wrap");
        expect(list.className).toContain("min-h-9");
        // A fixed height would keep wrapped rows outside the switcher's background.
        expect(list.className).not.toMatch(/(^|:)h-9(\s|$)/);
    });

    it("gives the tabs a content-based width, so a wrapped row is not stretched to fill", () => {
        render(<RunDetails webAppEvaluatedModel={result} />);

        // flex-1 would stretch a wrapped row's tabs to fill the line, so that row reads as a second
        // switcher of a different size.
        for (const tab of screen.getAllByRole("tab")) {
            expect(tab.className).toContain("flex-none");
            expect(tab.className).not.toMatch(/(^|\s)flex-1(\s|$)/);
        }
    });

    it("offers a License Choices tab for a run that applied some", () => {
        expect(result.hasLicenseChoices()).toBe(true);

        render(<RunDetails webAppEvaluatedModel={result} />);
        expect(screen.getByRole("tab", { name: /license choices/i })).toBeInTheDocument();
    });

    it("opens the License Choices tab when the Summary deep-links to it", () => {
        render(<RunDetails focusTab="license-choices" webAppEvaluatedModel={result} />);

        expect(screen.getByRole("tab", { name: /license choices/i })).toHaveAttribute("aria-selected", "true");
    });

    it("opens the focused tab first when focusTab is set", () => {
        render(<RunDetails focusTab="tools" webAppEvaluatedModel={result} />);

        expect(screen.getByRole("tab", { name: /tools/i })).toHaveAttribute("aria-selected", "true");
        expect(screen.getByRole("tab", { name: ".ort.yml" })).toHaveAttribute("aria-selected", "false");
    });
});
