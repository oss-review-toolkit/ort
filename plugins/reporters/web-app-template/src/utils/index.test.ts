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

import { describe, expect, it } from "vitest";

import { licenseToHslColor, randomStringGenerator, whiteTextContrast } from "@/utils";

describe("randomStringGenerator", () => {
    it("returns a string sized from the requested length", () => {
        // The generator inserts a single separator character, so the result is one longer than asked.
        for (const length of [1, 2, 20, 41, 100]) {
            expect(randomStringGenerator(length)).toHaveLength(length + 1);
        }
    });

    it("only uses characters from the alphanumeric and symbol alphabets", () => {
        expect(randomStringGenerator(500)).toMatch(/^[A-Z0-9\-:;_$!]+$/);
    });

    it("produces different values on subsequent calls", () => {
        expect(randomStringGenerator(64)).not.toBe(randomStringGenerator(64));
    });
});

describe("licenseToHslColor", () => {
    const parse = (hsl: string): { hue: number; saturation: number; lightness: number } => {
        const match = hsl.match(/^hsl\((\d+), (\d+)%, (\d+)%\)$/);
        expect(match).not.toBeNull();
        return {
            hue: Number(match?.[1]),
            saturation: Number(match?.[2]) / 100,
            lightness: Number(match?.[3]) / 100,
        };
    };

    it("returns a syntactically valid hsl() color", () => {
        expect(licenseToHslColor("Apache-2.0")).toMatch(/^hsl\(\d+, \d+%, \d+%\)$/);
    });

    it("is deterministic - the same license always yields the same color", () => {
        expect(licenseToHslColor("Apache-2.0")).toBe(licenseToHslColor("Apache-2.0"));
        expect(licenseToHslColor("MIT")).toBe(licenseToHslColor("MIT"));
    });

    it("gives distinct licenses distinct colors", () => {
        // Fresh identifiers so they are assigned consecutive (low) palette indices.
        const licenses = Array.from({ length: 40 }, (_, i) => `Distinct-License-${i}`);
        const colors = new Set(licenses.map((license) => licenseToHslColor(license)));
        expect(colors.size).toBe(licenses.length);
    });

    it("gives white text WCAG 2.1 AA contrast (>= 4.5:1) for every license", () => {
        const licenses = [
            "MIT",
            "Apache-2.0",
            "GPL-3.0-or-later",
            "BSD-3-Clause",
            "LGPL-2.1-only",
            "LicenseRef-scancode-public-domain-disclaimer",
            "NOASSERTION",
        ];
        for (const license of licenses) {
            const { hue, saturation, lightness } = parse(licenseToHslColor(license));
            expect(whiteTextContrast(hue, saturation, lightness)).toBeGreaterThanOrEqual(4.5);
        }
        // Cover many hues to be sure the whole hue wheel meets the contrast target.
        for (let i = 0; i < 200; i++) {
            const { hue, saturation, lightness } = parse(licenseToHslColor(`License-${i}`));
            expect(whiteTextContrast(hue, saturation, lightness)).toBeGreaterThanOrEqual(4.5);
        }
    });
});
