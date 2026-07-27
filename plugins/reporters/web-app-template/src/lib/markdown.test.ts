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

import { isMarkdown, normalizeDescription, stripSectionHeadings } from "@/lib/markdown";

describe("isMarkdown", () => {
    it("treats headings, links, lists and code as Markdown", () => {
        expect(isMarkdown("### Summary")).toBe(true);
        expect(isMarkdown("See [CVE-1](https://example.com)")).toBe(true);
        expect(isMarkdown("- one\n- two")).toBe(true);
        expect(isMarkdown("Run `npm install`")).toBe(true);
        expect(isMarkdown("This is **important**")).toBe(true);
    });

    it("treats ordinary prose as plain text", () => {
        expect(isMarkdown("A simple sentence about a vulnerability.")).toBe(false);
        expect(isMarkdown("Version 1.6.0 is affected; upgrade to 1.6.1.")).toBe(false);
    });
});

describe("stripSectionHeadings", () => {
    it("removes every ATX heading but keeps their content", () => {
        const input = "### Summary\nA scan shows the flaw.\n\n### Impact\nMore context here.";
        expect(stripSectionHeadings(input)).toBe("A scan shows the flaw.\n\nMore context here.");
    });

    it("removes Setext headings and their underline", () => {
        const input = "Overview\n-------\nThe body of the overview.";
        expect(stripSectionHeadings(input)).toBe("The body of the overview.");
    });

    it("keeps body text and inline formatting", () => {
        const input = "# Title\nSomething **bad** happens.";
        expect(stripSectionHeadings(input)).toBe("Something **bad** happens.");
    });
});

describe("normalizeDescription", () => {
    it("passes plain text through unchanged", () => {
        const result = normalizeDescription("Just a plain description.");
        expect(result).toEqual({ isMarkdown: false, content: "Just a plain description." });
    });

    it("flags Markdown and strips all section headings", () => {
        const result = normalizeDescription("### Summary\nFiona is affected.\n### Impact\nRoot cause.");
        expect(result.isMarkdown).toBe(true);
        expect(result.content).toBe("Fiona is affected.\nRoot cause.");
    });

    it("returns empty content for a blank description", () => {
        expect(normalizeDescription("   ")).toEqual({ isMarkdown: false, content: "" });
    });
});
