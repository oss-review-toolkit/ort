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

import { ReportLoadError, toReportErrorDetail, toReportErrorKind } from "@/lib/reportErrors";

describe("reportErrors", () => {
    it("keeps the kind it was thrown with", () => {
        const err = new ReportLoadError("unsupported-browser", "This report needs a newer browser.");

        expect(toReportErrorKind(err)).toBe("unsupported-browser");
        expect(toReportErrorDetail(err)).toBe("This report needs a newer browser.");
    });

    it("treats anything it did not throw itself as unknown", () => {
        expect(toReportErrorKind(new Error("Unexpected end of JSON input"))).toBe("unknown");
        expect(toReportErrorKind("a string")).toBe("unknown");
        expect(toReportErrorKind(undefined)).toBe("unknown");
    });

    it("always yields something to copy, whatever it was handed", () => {
        expect(toReportErrorDetail(new Error("boom"))).toBe("boom");
        expect(toReportErrorDetail("boom")).toBe("boom");
        expect(toReportErrorDetail(undefined)).toBe("undefined");
        // An Error is allowed an empty message, and the worker can forward one.
        expect(toReportErrorDetail(new Error(""))).toBe("Error");
        expect(toReportErrorDetail(new ReportLoadError("unknown", ""))).toBe("ReportLoadError");
        expect(toReportErrorDetail("")).not.toBe("");
    });

    it("is an Error, so it still survives a promise rejection unchanged", () => {
        const err = new ReportLoadError("missing-data", "No data.");

        expect(err).toBeInstanceOf(Error);
        expect(err.name).toBe("ReportLoadError");
    });
});
