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
import { describe, expect, it } from "vitest";

import { buildEntries, CvssVectorChart, FALLBACK_STYLE, SERIES_STYLES } from "@/components/CvssVectorChart";

const V2 = "AV:N/AC:L/Au:N/C:P/I:P/A:P";
const V31 = "CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H";
const V4 = "CVSS:4.0/AV:N/AC:L/AT:N/PR:N/UI:N/VC:H/VI:H/VA:H/SC:N/SI:N/SA:N";
const V4_WITH_THREAT =
    "CVSS:4.0/AV:N/AC:L/AT:N/PR:N/UI:N/VC:N/VI:N/VA:H/SC:N/SI:N/SA:N/E:U/S:N/AU:Y/R:U/V:D/RE:M/U:Amber";

// The two grounds a series is drawn on: the card in the light theme and in the dark theme.
const LIGHT_CARD: [number, number, number] = [1, 1, 1];
const DARK_CARD: [number, number, number] = [0.0905, 0.0905, 0.0905];

function hslToRgb(hsl: string): [number, number, number] {
    const [hue, saturation, lightness] = [...hsl.matchAll(/-?[\d.]+/g)].map((match) => Number(match[0]));
    const [h, s, l] = [hue ?? 0, (saturation ?? 0) / 100, (lightness ?? 0) / 100];
    const chroma = (1 - Math.abs(2 * l - 1)) * s;
    const sector = (((h % 360) + 360) % 360) / 60;
    const x = chroma * (1 - Math.abs((sector % 2) - 1));
    const m = l - chroma / 2;
    const rgb: [number, number, number] =
        sector < 1
            ? [chroma, x, 0]
            : sector < 2
              ? [x, chroma, 0]
              : sector < 3
                ? [0, chroma, x]
                : sector < 4
                  ? [0, x, chroma]
                  : sector < 5
                    ? [x, 0, chroma]
                    : [chroma, 0, x];

    return [rgb[0] + m, rgb[1] + m, rgb[2] + m];
}

function relativeLuminance([r, g, b]: [number, number, number]): number {
    const linear = (channel: number): number =>
        channel <= 0.03928 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4;

    return 0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b);
}

function contrastRatio(a: [number, number, number], b: [number, number, number]): number {
    const [light, dark] = [relativeLuminance(a) + 0.05, relativeLuminance(b) + 0.05].sort((x, y) => y - x);

    return (light ?? 1) / (dark ?? 1);
}

describe("CvssVectorChart", () => {
    it("renders nothing for an empty list of vectors", () => {
        const { container } = render(<CvssVectorChart vectors={[]} />);
        expect(container).toBeEmptyDOMElement();
    });

    it("renders nothing for a vector whose sub-scores cannot be derived", () => {
        const { container } = render(<CvssVectorChart vectors={[{ vector: "not-a-cvss-vector" }]} />);
        expect(container).toBeEmptyDOMElement();
    });

    it("charts a CVSS v4.0 vector that carries no score of its own", () => {
        // Until the v4.0 metrics were scored, such a vector left every axis at zero and the whole
        // chart disappeared.
        const { container } = render(<CvssVectorChart vectors={[{ vector: V4 }]} />);
        expect(container).not.toBeEmptyDOMElement();
        expect(screen.getByText("Base")).toBeInTheDocument();
    });

    it("charts every version a vulnerability was scored under, oldest first", () => {
        const entries = buildEntries([
            { score: 9.8, vector: V31 },
            { score: 7.5, vector: V2 },
            { score: 9.3, vector: V4 },
        ]);
        expect(entries.map((entry) => entry.label)).toEqual(["v2.0", "v3.1", "v4.0"]);

        render(
            <CvssVectorChart
                vectors={[
                    { score: 9.8, vector: V31 },
                    { score: 7.5, vector: V2 },
                    { score: 9.3, vector: V4 },
                ]}
            />,
        );
        // Each version gets its own legend entry, naming it and the score it was reported with.
        expect(screen.getByRole("button", { name: /CVSS v2\.0 \(7\.5\)/ })).toBeInTheDocument();
        expect(screen.getByRole("button", { name: /CVSS v3\.1 \(9\.8\)/ })).toBeInTheDocument();
        expect(screen.getByRole("button", { name: /CVSS v4\.0 \(9\.3\)/ })).toBeInTheDocument();
    });

    it("keeps the highest-scored vector of a version when several report it", () => {
        const entries = buildEntries([
            { score: 5.0, vector: V4_WITH_THREAT },
            { score: 9.3, vector: V4 },
        ]);
        expect(entries).toHaveLength(1);
        expect(entries[0]?.score).toBe(9.3);
    });

    it("gives every version its own colour and line, so the colour is never the only cue", () => {
        const styles = Object.values(SERIES_STYLES);
        expect(new Set(styles.map((style) => style.color)).size).toBe(styles.length);
        // WCAG 2.2 SC 1.4.1: a reader who cannot distinguish the colours still has the dash pattern.
        expect(new Set(styles.map((style) => String(style.dash))).size).toBe(styles.length);
    });

    it("draws every series at the contrast WCAG 2.2 asks of a graphical object", () => {
        for (const style of [...Object.values(SERIES_STYLES), FALLBACK_STYLE]) {
            const rgb = hslToRgb(style.color);
            // SC 1.4.11 Non-text Contrast requires at least 3:1 against the adjacent colour, which
            // here is the card the chart sits on, in either theme.
            expect(contrastRatio(rgb, LIGHT_CARD)).toBeGreaterThanOrEqual(3);
            expect(contrastRatio(rgb, DARK_CARD)).toBeGreaterThanOrEqual(3);
        }
    });

    it("names each series in the legend rather than leaving the colour to speak for it", () => {
        render(<CvssVectorChart vectors={[{ score: 9.3, vector: V4 }]} />);
        expect(screen.getByRole("button", { name: /CVSS v4\.0 \(9\.3\)/ })).toBeInTheDocument();
    });

    it("is titled after what it shows rather than after the versions it happens to hold", () => {
        render(<CvssVectorChart vectors={[{ score: 9.3, vector: V4 }]} />);
        expect(screen.getAllByText("Severity Radar").length).toBeGreaterThan(0);
    });

    it("hides and shows a version when its legend entry is clicked", async () => {
        const user = userEvent.setup();
        render(
            <CvssVectorChart
                vectors={[
                    { score: 9.8, vector: V31 },
                    { score: 9.3, vector: V4 },
                ]}
            />,
        );

        const v31 = screen.getByRole("button", { name: /CVSS v3\.1/ });
        expect(v31).toHaveAttribute("aria-pressed", "true");

        await user.click(v31);
        expect(screen.getByRole("button", { name: /CVSS v3\.1/ })).toHaveAttribute("aria-pressed", "false");

        await user.click(screen.getByRole("button", { name: /CVSS v3\.1/ }));
        expect(screen.getByRole("button", { name: /CVSS v3\.1/ })).toHaveAttribute("aria-pressed", "true");
    });

    it("will not let the last shown version be hidden, which would empty the chart", async () => {
        const user = userEvent.setup();
        render(
            <CvssVectorChart
                vectors={[
                    { score: 9.8, vector: V31 },
                    { score: 9.3, vector: V4 },
                ]}
            />,
        );

        await user.click(screen.getByRole("button", { name: /CVSS v3\.1/ }));
        expect(screen.getByRole("button", { name: /CVSS v4\.0/ })).toBeDisabled();
        expect(screen.getByRole("button", { name: /CVSS v3\.1/ })).toBeEnabled();
    });
});
