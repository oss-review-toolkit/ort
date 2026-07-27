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

/* Utility function to generate random numbers and letters string
 * Based on vjt@openssl.it public domain code, see
 * https://gist.github.com/vjt/2239787
 */
const randomStringGenerator = (length: number = Math.floor(Math.random() * 501) + 20): string => {
    const rand = (str: string): string => str[Math.floor(Math.random() * str.length)] ?? "";
    const get = (source: string, len: number, a: string[]): string[] => {
        for (let i = 0; i < len; i++) {
            a.push(rand(source));
        }

        return a;
    };
    const alpha = (len: number, a: string[]): string[] => get("A1BCD2EFG3HIJ4KLM5NOP6QRS7TUV8WXY9Z", len, a);
    const symbol = (len: number, a: string[]): string[] => get("-:;_$!", len, a);
    const l = Math.floor(length / 2);
    const r = Math.ceil(length / 2);

    return alpha(l, symbol(1, alpha(r, []))).join("");
};

// Convert an HSL colour (hue in [0,360), saturation & lightness in [0,1]) to sRGB channels in [0,1].
function hslToRgb(hue: number, saturation: number, lightness: number): [number, number, number] {
    const chroma = (1 - Math.abs(2 * lightness - 1)) * saturation;
    const huePrime = (((hue % 360) + 360) % 360) / 60;
    const x = chroma * (1 - Math.abs((huePrime % 2) - 1));
    const m = lightness - chroma / 2;
    let rgb: [number, number, number];
    if (huePrime < 1) rgb = [chroma, x, 0];
    else if (huePrime < 2) rgb = [x, chroma, 0];
    else if (huePrime < 3) rgb = [0, chroma, x];
    else if (huePrime < 4) rgb = [0, x, chroma];
    else if (huePrime < 5) rgb = [x, 0, chroma];
    else rgb = [chroma, 0, x];
    return [rgb[0] + m, rgb[1] + m, rgb[2] + m];
}

// WCAG 2.x relative luminance of an sRGB colour (channels in [0,1]).
function relativeLuminance([r, g, b]: [number, number, number]): number {
    const linear = (c: number): number => (c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4);
    return 0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b);
}

// The WCAG contrast ratio of white text on the given HSL background (saturation & lightness in [0,1]);
// white has a relative luminance of 1. Exported so the colour choice can be verified against WCAG.
const whiteTextContrast = (hue: number, saturation: number, lightness: number): number =>
    1.05 / (relativeLuminance(hslToRgb(hue, saturation, lightness)) + 0.05);

// The colour for the i-th distinct license. The hue follows the golden angle so consecutive licenses
// land far apart on the colour wheel (the phyllotaxis pattern gives the best spread for any count),
// and the lightness is lowered until white text meets WCAG 2.1 AA contrast (>= 4.5:1).
function colorForIndex(index: number): string {
    const hue = Math.round((index * 137.508) % 360);
    const saturation = 0.6;
    let lightness = 45;
    while (lightness > 12 && whiteTextContrast(hue, saturation, lightness / 100) < 4.5) {
        lightness -= 1;
    }
    return `hsl(${hue}, ${Math.round(saturation * 100)}%, ${lightness}%)`;
}

// Assign each license identifier a distinct WCAG-AA colour, the single source of truth used everywhere
// a license is shown. Colours are handed out in first-seen order so distinct licenses get distinct,
// well-separated colours (unlike a per-string hash, which can map two licenses to near-identical
// hues), while the same identifier always gets the same colour - so a license looks identical in
// every column and legend.
const assignedLicenseColors = new Map<string, string>();
const licenseToHslColor = (license: string): string => {
    let color = assignedLicenseColors.get(license);
    if (color === undefined) {
        color = colorForIndex(assignedLicenseColors.size);
        assignedLicenseColors.set(license, color);
    }
    return color;
};

export { licenseToHslColor, randomStringGenerator, whiteTextContrast };
