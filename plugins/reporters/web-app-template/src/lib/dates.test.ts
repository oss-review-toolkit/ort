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

import {
    BROWSER_DATE_FORMAT,
    convertIso8601Date2LongDate,
    convertIso8601Date2Sentence,
    REPORT_DATE_FORMAT,
} from "@/lib/dates";

describe("convertIso8601Date2LongDate", () => {
    it("returns a dash for missing or invalid input", () => {
        expect(convertIso8601Date2LongDate(null)).toBe("—");
        expect(convertIso8601Date2LongDate(undefined)).toBe("—");
        expect(convertIso8601Date2LongDate("")).toBe("—");
        expect(convertIso8601Date2LongDate("not-a-date")).toBe("—");
    });

    it("formats a valid ISO timestamp as a date without the time of day", () => {
        // Noon UTC so the calendar day/year cannot shift under whatever local timezone the test runs in.
        const result = convertIso8601Date2LongDate("2026-06-15T12:00:00.000Z");
        expect(result).not.toBe("—");
        expect(result).toContain("2026");
        // The month is spelled out rather than written as a number, and no time of day is shown.
        expect(result).not.toMatch(/\d{4}-\d{2}-\d{2}/);
        expect(result).not.toContain(" on ");
    });
});

describe("convertIso8601Date2Sentence", () => {
    it("returns a dash for missing or invalid input", () => {
        expect(convertIso8601Date2Sentence(null)).toBe("—");
        expect(convertIso8601Date2Sentence(undefined)).toBe("—");
        expect(convertIso8601Date2Sentence("")).toBe("—");
        expect(convertIso8601Date2Sentence("not-a-date")).toBe("—");
    });

    it("formats a valid ISO timestamp into a sentence carrying the date", () => {
        // Noon UTC so the calendar day/year cannot shift under whatever local timezone the test runs in.
        const result = convertIso8601Date2Sentence("2026-06-15T12:00:00.000Z");
        expect(result).not.toBe("—");
        expect(result).toContain(" on ");
        expect(result).toContain("2026");
    });
});

describe("date format preferences", () => {
    // 01:52 UTC is the previous day in the Americas, so this instant is exactly where a reader's timezone
    // changes the calendar date - which is what the report format exists to prevent.
    const LATE_NIGHT_UTC = "2022-06-29T01:52:10Z";

    it("renders the same date for every reader by default", () => {
        expect(convertIso8601Date2LongDate(LATE_NIGHT_UTC)).toBe("June 29, 2022");
        expect(convertIso8601Date2LongDate(LATE_NIGHT_UTC, REPORT_DATE_FORMAT)).toBe("June 29, 2022");
    });

    it("names the timezone it resolved the instant in", () => {
        expect(convertIso8601Date2Sentence(LATE_NIGHT_UTC)).toBe("1:52 AM UTC on June 29, 2022");
    });

    it("follows an explicit locale and timezone", () => {
        expect(convertIso8601Date2LongDate(LATE_NIGHT_UTC, { locale: "de-DE", timeZone: "Europe/Berlin" })).toBe(
            "29. Juni 2022",
        );
        // Same instant, a timezone behind UTC: the calendar date steps back a day.
        expect(convertIso8601Date2LongDate(LATE_NIGHT_UTC, { locale: "en-US", timeZone: "America/New_York" })).toBe(
            "June 28, 2022",
        );
    });

    it("defers to the browser when asked to", () => {
        // Whatever the runtime's locale and zone are, the call must not throw and must not fall back to "—".
        expect(convertIso8601Date2LongDate(LATE_NIGHT_UTC, BROWSER_DATE_FORMAT)).not.toBe("—");
    });
});
