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

/**
 * Rendering of the timestamps ORT records as UTC instants.
 *
 * A report is an artifact people archive, screenshot and discuss across timezones, so by default every
 * reader sees the same text for the same instant (see REPORT_DATE_FORMAT). Readers who would rather see
 * their own conventions can switch to BROWSER_DATE_FORMAT in the settings.
 */

const ISO_TRIM_REGEX = /(\.\d{3})\d*Z$/;

// A spelled-out month, so the date cannot be read as a different day the way a purely numeric date can:
// 06/29/2022 and 29/06/2022 are the same eight characters for two different days, "June 29, 2022" is not.
const LONG_DATE_FORMAT: Intl.DateTimeFormatOptions = {
    year: "numeric",
    month: "long",
    day: "numeric",
};

const TIME_FORMAT: Intl.DateTimeFormatOptions = {
    hour: "numeric",
    minute: "2-digit",
    hour12: true,
    timeZoneName: "short",
};

/** Which locale and timezone timestamps are rendered in. `undefined` means "whatever the browser uses". */
export interface DateFormat {
    locale: string | undefined;
    timeZone: string | undefined;
}

/**
 * The report's own format, and the default. Fixing both the locale and the timezone means one instant
 * reads as one date for everyone: without it the same package renders as "June 29" in Berlin and
 * "June 28" in New York, which turns a shared report into an argument.
 */
export const REPORT_DATE_FORMAT: DateFormat = { locale: "en-US", timeZone: "UTC" };

/** The reader's own conventions, for those who prefer them over a report that reads the same for everyone. */
export const BROWSER_DATE_FORMAT: DateFormat = { locale: undefined, timeZone: undefined };

// ORT timestamps can carry sub-millisecond precision, which Date cannot parse; trim it first.
function parseIso8601Date(iso8601Date: string | undefined | null): Date | null {
    if (!iso8601Date) return null;
    const date = new Date(iso8601Date.replace(ISO_TRIM_REGEX, "$1Z"));

    return Number.isNaN(date.getTime()) ? null : date;
}

/** The date alone, e.g. "June 29, 2022", for places where the time of day would only add noise. */
export function convertIso8601Date2LongDate(
    iso8601Date: string | undefined | null,
    format: DateFormat = REPORT_DATE_FORMAT,
): string {
    const date = parseIso8601Date(iso8601Date);
    if (!date) return "—";

    return new Intl.DateTimeFormat(format.locale, { ...LONG_DATE_FORMAT, timeZone: format.timeZone }).format(date);
}

/** The full instant, e.g. "1:52 AM UTC on June 29, 2022", for places that have room for the exact time. */
export function convertIso8601Date2Sentence(
    iso8601Date: string | undefined | null,
    format: DateFormat = REPORT_DATE_FORMAT,
): string {
    const date = parseIso8601Date(iso8601Date);
    if (!date) return "—";

    const timeFormatter = new Intl.DateTimeFormat(format.locale, { ...TIME_FORMAT, timeZone: format.timeZone });

    return `${timeFormatter.format(date)} on ${convertIso8601Date2LongDate(iso8601Date, format)}`;
}
