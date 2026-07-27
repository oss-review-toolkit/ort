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

// Computes the CVSS sub-scores that make up a "severity radar", following the same idea as the
// metaeffekt universal CVSS calculator (https://github.com/org-metaeffekt/metaeffekt-universal-cvss-calculator):
// the radar plots the base / impact / exploitability sub-scores (each 0-10), not the raw metric
// letters, so its shape reflects how the score is actually composed. The base, impact and
// exploitability formulas for CVSS v2 and v3.0/v3.1 are implemented here from the official
// specifications. CVSS v4.0's macro-vector scoring is not reproduced; for it (and any unrecognised
// vector) the caller-supplied authoritative score is used as the overall value.

export type CvssVersion = "2.0" | "3.0" | "3.1" | "4.0" | "unknown";

export interface CvssScores {
    base?: number;
    exploitability?: number;
    impact?: number;
    /** Overall/base score used as the fallback for axes that cannot be computed. */
    overall?: number;
}

export interface ParsedCvssVector {
    metrics: Map<string, string>;
    version: CvssVersion;
}

// Parse "CVSS:3.1/AV:N/AC:L/..." into its version and METRIC:VALUE pairs. CVSS v2 vectors have no
// "CVSS:" version prefix and are recognised by their mandatory Authentication (Au) metric.
export function parseCvssVector(vector: string): ParsedCvssVector {
    const metrics = new Map<string, string>();
    let version: CvssVersion = "unknown";

    for (const part of vector.split("/")) {
        const [key, value] = part.split(":");
        if (!key || !value) {
            continue;
        }
        if (key === "CVSS") {
            if (value.startsWith("4.0")) {
                version = "4.0";
            } else if (value.startsWith("3.1")) {
                version = "3.1";
            } else if (value.startsWith("3.0")) {
                version = "3.0";
            } else if (value.startsWith("2")) {
                version = "2.0";
            }
            continue;
        }
        metrics.set(key, value);
    }

    if (version === "unknown" && metrics.has("Au")) {
        version = "2.0";
    }

    return { metrics, version };
}

// A short, human-readable label for a CVSS version, e.g. "3.1" -> "v3.1".
export function cvssVersionLabel(version: CvssVersion): string {
    return version === "unknown" ? "" : `v${version}`;
}

// Orders versions oldest-to-newest so a version switch reads left-to-right (v2 -> v3 -> v4).
const VERSION_RANK: Record<CvssVersion, number> = {
    "2.0": 0,
    "3.0": 1,
    "3.1": 2,
    "4.0": 3,
    unknown: 99,
};

export function cvssVersionRank(version: CvssVersion): number {
    return VERSION_RANK[version];
}

function round1(value: number): number {
    return Math.round(value * 10) / 10;
}

function weight(table: Record<string, number>, key: string | undefined): number | undefined {
    return key !== undefined ? table[key] : undefined;
}

// CVSS v3.1 "Roundup": round up to one decimal place, avoiding binary floating-point drift.
function roundUp1(value: number): number {
    const scaled = Math.round(value * 100000);
    if (scaled % 10000 === 0) {
        return scaled / 100000;
    }
    return (Math.floor(scaled / 10000) + 1) / 10;
}

const V3_AV: Record<string, number> = { A: 0.62, L: 0.55, N: 0.85, P: 0.2 };
const V3_AC: Record<string, number> = { H: 0.44, L: 0.77 };
const V3_UI: Record<string, number> = { N: 0.85, R: 0.62 };
const V3_PR_UNCHANGED: Record<string, number> = { H: 0.27, L: 0.62, N: 0.85 };
const V3_PR_CHANGED: Record<string, number> = { H: 0.5, L: 0.68, N: 0.85 };
const V3_CIA: Record<string, number> = { H: 0.56, L: 0.22, N: 0 };

function computeV3(metrics: Map<string, string>, version: "3.0" | "3.1"): CvssScores {
    const changed = (metrics.get("S") ?? "U") === "C";
    const av = weight(V3_AV, metrics.get("AV"));
    const ac = weight(V3_AC, metrics.get("AC"));
    const ui = weight(V3_UI, metrics.get("UI"));
    const pr = weight(changed ? V3_PR_CHANGED : V3_PR_UNCHANGED, metrics.get("PR"));
    const c = weight(V3_CIA, metrics.get("C"));
    const i = weight(V3_CIA, metrics.get("I"));
    const a = weight(V3_CIA, metrics.get("A"));

    if (
        av === undefined ||
        ac === undefined ||
        ui === undefined ||
        pr === undefined ||
        c === undefined ||
        i === undefined ||
        a === undefined
    ) {
        return {};
    }

    const iss = 1 - (1 - c) * (1 - i) * (1 - a);
    const impact = changed ? 7.52 * (iss - 0.029) - 3.25 * (iss - 0.02) ** 15 : 6.42 * iss;
    const exploitability = 8.22 * av * ac * pr * ui;

    let base: number;
    if (impact <= 0) {
        base = 0;
    } else {
        const raw = Math.min(changed ? 1.08 * (impact + exploitability) : impact + exploitability, 10);
        base = version === "3.1" ? roundUp1(raw) : Math.ceil(raw * 10) / 10;
    }

    return {
        base,
        overall: base,
        impact: round1(Math.max(impact, 0)),
        exploitability: round1(exploitability),
    };
}

const V2_AV: Record<string, number> = { A: 0.646, L: 0.395, N: 1.0 };
const V2_AC: Record<string, number> = { H: 0.35, L: 0.71, M: 0.61 };
const V2_AU: Record<string, number> = { M: 0.45, N: 0.704, S: 0.56 };
const V2_CIA: Record<string, number> = { C: 0.66, N: 0, P: 0.275 };

function computeV2(metrics: Map<string, string>): CvssScores {
    const av = weight(V2_AV, metrics.get("AV"));
    const ac = weight(V2_AC, metrics.get("AC"));
    const au = weight(V2_AU, metrics.get("Au"));
    const c = weight(V2_CIA, metrics.get("C"));
    const i = weight(V2_CIA, metrics.get("I"));
    const a = weight(V2_CIA, metrics.get("A"));

    if (
        av === undefined ||
        ac === undefined ||
        au === undefined ||
        c === undefined ||
        i === undefined ||
        a === undefined
    ) {
        return {};
    }

    const impact = 10.41 * (1 - (1 - c) * (1 - i) * (1 - a));
    const exploitability = 20 * av * ac * au;
    const fImpact = impact === 0 ? 0 : 1.176;
    const base = round1((0.6 * impact + 0.4 * exploitability - 1.5) * fImpact);

    return {
        base,
        overall: base,
        impact: round1(impact),
        exploitability: round1(exploitability),
    };
}

// Compute the CVSS sub-scores for a vector. `fallbackScore` (the authoritative score carried by the
// vulnerability reference) is used as the overall value for CVSS v4.0 and any vector whose sub-scores
// cannot be derived, so a radar can still be drawn.
export function computeCvssScores(vector: string, fallbackScore?: number): CvssScores {
    const { metrics, version } = parseCvssVector(vector);

    let scores: CvssScores = {};
    if (version === "3.0" || version === "3.1") {
        scores = computeV3(metrics, version);
    } else if (version === "2.0") {
        scores = computeV2(metrics);
    }

    if (scores.base === undefined && fallbackScore !== undefined) {
        scores = { ...scores, base: fallbackScore, overall: fallbackScore };
    } else if (scores.overall === undefined && fallbackScore !== undefined) {
        scores = { ...scores, overall: fallbackScore };
    }

    return scores;
}
