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

import { parseSpdxLicenseExpression, type SpdxToken } from "@/lib/spdx-license-expressions";

const types = (tokens: SpdxToken[]): string[] => tokens.map((token) => token.type);

describe("parseSpdxLicenseExpression", () => {
    it("parses a simple license expression", () => {
        const parsed = parseSpdxLicenseExpression("Apache-2.0");
        expect(parsed.simpleExpressions).toEqual(["Apache-2.0"]);
        expect(parsed.tokens).toEqual([{ type: "license", value: "Apache-2.0" }]);
    });

    it("treats a '+' suffix and a license-ref as a single simple expression", () => {
        expect(parseSpdxLicenseExpression("CDDL-1.0+").simpleExpressions).toEqual(["CDDL-1.0+"]);
        expect(parseSpdxLicenseExpression("LicenseRef-23").simpleExpressions).toEqual(["LicenseRef-23"]);
        expect(
            parseSpdxLicenseExpression("DocumentRef-spdx-tool-1.2:LicenseRef-MIT-Style-2").simpleExpressions,
        ).toEqual(["DocumentRef-spdx-tool-1.2:LicenseRef-MIT-Style-2"]);
    });

    it("splits AND / OR expressions and collects the distinct simple expressions", () => {
        const parsed = parseSpdxLicenseExpression("LGPL-2.1-only OR MIT OR BSD-3-Clause");
        expect(parsed.simpleExpressions).toEqual(["LGPL-2.1-only", "MIT", "BSD-3-Clause"]);
        expect(types(parsed.tokens)).toEqual(["license", "operator", "license", "operator", "license"]);
    });

    it("accepts lower-case operators", () => {
        const parsed = parseSpdxLicenseExpression("MIT and Apache-2.0 or BSD-3-Clause");
        expect(parsed.tokens.filter((token) => token.type === "operator")).toEqual([
            { type: "operator", value: "AND" },
            { type: "operator", value: "OR" },
        ]);
    });

    it("keeps a WITH exception as its own token and excludes it from the simple expressions", () => {
        const parsed = parseSpdxLicenseExpression("GPL-2.0-or-later WITH Bison-exception-2.2");
        expect(parsed.simpleExpressions).toEqual(["GPL-2.0-or-later"]);
        expect(parsed.tokens).toEqual([
            { type: "license", value: "GPL-2.0-or-later" },
            { type: "operator", value: "WITH" },
            { type: "exception", value: "Bison-exception-2.2" },
        ]);
    });

    it("tokenises parentheses used for precedence", () => {
        const parsed = parseSpdxLicenseExpression("MIT AND (LGPL-2.1-or-later OR BSD-3-Clause)");
        expect(parsed.simpleExpressions).toEqual(["MIT", "LGPL-2.1-or-later", "BSD-3-Clause"]);
        expect(types(parsed.tokens)).toEqual([
            "license",
            "operator",
            "open",
            "license",
            "operator",
            "license",
            "close",
        ]);
    });

    it("de-duplicates repeated simple expressions", () => {
        expect(parseSpdxLicenseExpression("MIT OR MIT AND Apache-2.0").simpleExpressions).toEqual([
            "MIT",
            "Apache-2.0",
        ]);
    });

    it("marks well-formed expressions as valid", () => {
        expect(parseSpdxLicenseExpression("Apache-2.0").isValid).toBe(true);
        expect(parseSpdxLicenseExpression("MIT AND Apache-2.0").isValid).toBe(true);
        expect(
            parseSpdxLicenseExpression("(MIT OR Apache-2.0) AND GPL-2.0-only WITH Classpath-exception-2.0").isValid,
        ).toBe(true);
    });

    it("marks malformed expressions as invalid", () => {
        for (const invalid of ["", "MIT AND", "AND MIT", "(MIT", "MIT)", "MIT OR OR Apache-2.0", "MIT WITH"]) {
            expect(parseSpdxLicenseExpression(invalid).isValid).toBe(false);
        }
    });
});
