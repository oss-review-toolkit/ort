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
import { describe, expect, it } from "vitest";

import { TemplatePage } from "@/pages/TemplatePage";
import { render } from "@/test/render";

describe("TemplatePage", () => {
    it("says what the file is, before anything else", () => {
        render(<TemplatePage />);

        expect(screen.getByRole("heading", { name: "An empty report" })).toBeInTheDocument();
        expect(screen.getByText(/Nothing has gone wrong/)).toBeInTheDocument();
    });

    it("gives the commands that produce a report, in the order they run", () => {
        render(<TemplatePage />);

        const steps = screen.getAllByRole("listitem").map((item) => item.textContent);
        expect(steps).toHaveLength(3);
        expect(steps[0]).toContain("ort analyze -i . -o ort-results");
        expect(steps[1]).toContain("ort scan -i ort-results/analyzer-result.yml -o ort-results");
        expect(steps[2]).toContain("ort report -f WebApp -i ort-results/scan-result.yml -o ort-results");
    });

    it("chains the steps, each reading what the one before it wrote", () => {
        render(<TemplatePage />);

        const steps = screen.getAllByRole("listitem").map((item) => item.textContent ?? "");
        expect(steps[1]).toContain("-i ort-results/analyzer-result.yml");
        expect(steps[2]).toContain("-i ort-results/scan-result.yml");
        // Writing elsewhere than the working directory keeps ORT's output out of the analyzed sources,
        // and a step refuses to overwrite a result file that is already there.
        for (const step of steps) {
            expect(step).toContain("-o ort-results");
            expect(step).not.toMatch(/-o \.(\s|$)/);
        }
    });

    it("links only to top-level URLs, which a rewrite of the documentation cannot break", () => {
        render(<TemplatePage />);

        const links = screen.getAllByRole("link");
        expect(links.map((link) => link.getAttribute("href"))).toEqual([
            "https://oss-review-toolkit.org/",
            "https://github.com/oss-review-toolkit/ort",
        ]);
    });

    it("opens those links in a new tab without handing them the opener", () => {
        render(<TemplatePage />);

        for (const link of screen.getAllByRole("link")) {
            expect(link).toHaveAttribute("target", "_blank");
            expect(link).toHaveAttribute("rel", "noopener noreferrer");
        }
    });

    it("keeps the ORT wordmark, as the loading page shown before it does", () => {
        render(<TemplatePage />);

        expect(screen.getAllByAltText("OSS Review Toolkit").length).toBeGreaterThan(0);
    });
});
