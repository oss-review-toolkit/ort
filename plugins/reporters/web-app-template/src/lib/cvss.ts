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

import { fromVector } from "ae-cvss-calculator";

// Scoring a CVSS vector is left to the ae-cvss-calculator library, the implementation behind the
// metaeffekt universal CVSS calculator (https://github.com/org-metaeffekt/metaeffekt-universal-cvss-calculator),
// so that the report agrees with that calculator down to the last decimal and the specifications
// stay out of this code base. This module only adapts what the library returns to what the report
// needs: a version, a label to show it under, and the sub-scores the severity radar plots.

export type CvssVersion = "2.0" | "3.0" | "3.1" | "4.0" | "unknown";

/**
 * The sub-scores of a vector, each on a 0-10 scale. Which of them a vector has depends on its
 * version: CVSS v2 and v3 split into impact and exploitability, while CVSS v4.0 has neither but
 * scores the threat and environmental metric groups instead. The severity radar falls back to a
 * related score for an axis a version does not define.
 */
export interface CvssScores {
    base?: number | undefined;
    environmental?: number | undefined;
    exploitability?: number | undefined;
    impact?: number | undefined;
    /** CVSS v2 and v3 only: the impact with the environmental metrics applied. */
    modifiedImpact?: number | undefined;
    /** The score of the vector as a whole, and the fallback for axes that do not apply. */
    overall?: number | undefined;
    /** CVSS v2 and v3 only. */
    temporal?: number | undefined;
    /** CVSS v4.0 only: the base score with the threat metrics applied (CVSS-BT). */
    threat?: number | undefined;
}

// What the library returns; it types `fromVector` as `any`, so narrow it to what is actually used.
interface ScoredVector {
    calculateScores(normalize?: boolean): Partial<Record<keyof CvssScores | "baseMetricsOnly", number>>;
    getVectorName(): string;
}

// Parse a vector string, returning null for anything the library does not recognise as CVSS. ORT
// reports an EPSS percentile in the same field as a CVSS vector, so this is a normal outcome.
function parse(vector: string): ScoredVector | null {
    if (!vector) {
        return null;
    }

    try {
        return (fromVector(vector) as ScoredVector | null) ?? null;
    } catch {
        return null;
    }
}

/** The CVSS version a vector string is written in, or "unknown" if it is not a CVSS vector. */
export function cvssVersionOf(vector: string): CvssVersion {
    const name = parse(vector)?.getVectorName();
    switch (name) {
        case "CVSS:2.0":
            return "2.0";
        case "CVSS:3.0":
            return "3.0";
        case "CVSS:3.1":
            return "3.1";
        case "CVSS:4.0":
            return "4.0";
        default:
            return "unknown";
    }
}

/** A short, human-readable label for a CVSS version, e.g. "3.1" -> "v3.1". */
export function cvssVersionLabel(version: CvssVersion): string {
    return version === "unknown" ? "" : `v${version}`;
}

// Orders versions oldest-to-newest so a version reads left-to-right (v2 -> v3 -> v4).
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

/**
 * Compute the sub-scores of a vector. Scores are normalized to a 0-10 scale so that every axis of
 * the severity radar is read the same way. `fallbackScore` (the authoritative score carried by the
 * vulnerability reference) stands in as the overall score for a vector the library cannot score, so
 * that a radar can still be drawn.
 */
export function computeCvssScores(vector: string, fallbackScore?: number): CvssScores {
    const raw = parse(vector)?.calculateScores(true);

    const scores: CvssScores = {
        base: raw?.base,
        environmental: raw?.environmental,
        exploitability: raw?.exploitability,
        impact: raw?.impact,
        modifiedImpact: raw?.modifiedImpact,
        overall: raw?.overall,
        temporal: raw?.temporal,
        threat: raw?.threat,
    };

    if (scores.base === undefined && fallbackScore !== undefined) {
        return { ...scores, base: fallbackScore, overall: scores.overall ?? fallbackScore };
    }

    if (scores.overall === undefined && fallbackScore !== undefined) {
        return { ...scores, overall: fallbackScore };
    }

    return scores;
}
