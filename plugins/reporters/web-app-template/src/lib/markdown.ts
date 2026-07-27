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

// Vulnerability descriptions arrive either as plain text or as Markdown (advisory sources such as
// GitHub Security Advisories use Markdown, typically split into "### Summary" / "### Details" /
// "### Impact" sections). These helpers detect the format and tidy the Markdown for display.

export interface NormalizedDescription {
    content: string;
    isMarkdown: boolean;
}

// Signals that a string is Markdown rather than plain prose. Kept conservative to avoid treating
// ordinary text (which may contain a stray "*" or "-") as Markdown.
const MARKDOWN_PATTERNS: RegExp[] = [
    /^\s{0,3}#{1,6}\s+\S/m, // ATX heading, e.g. "### Summary"
    /\[[^\]]+\]\([^)]+\)/, // inline link, e.g. "[CVE-1](https://...)"
    /(\*\*|__)(?=\S)[\s\S]+?\1/, // bold
    /^\s{0,3}(?:[-*+]|\d+\.)\s+\S/m, // list item
    /```/, // fenced code block
    /`[^`]+`/, // inline code
    /^\s{0,3}>\s+\S/m, // block quote
];

export function isMarkdown(text: string): boolean {
    return MARKDOWN_PATTERNS.some((pattern) => pattern.test(text));
}

// Remove every Markdown section heading (ATX "### Anything" or Setext "Anything\n-------") while
// keeping the body content, then collapse the blank lines left behind. Advisory descriptions split
// their prose into headings (Summary/Details/Impact/...) that just duplicate the panel structure.
export function stripSectionHeadings(markdown: string): string {
    const lines = markdown.split(/\r?\n/);
    const kept: string[] = [];

    for (let i = 0; i < lines.length; i++) {
        const line = lines[i];
        if (line === undefined) {
            continue;
        }

        // ATX heading, e.g. "### Summary".
        if (/^\s{0,3}#{1,6}\s+\S/.test(line)) {
            continue;
        }

        // Setext heading: a text line underlined by "===" (h1) or "---" (h2).
        const underline = lines[i + 1];
        if (underline !== undefined && /\S/.test(line) && /^\s{0,3}[=-]{2,}\s*$/.test(underline)) {
            i += 1; // also skip the underline
            continue;
        }

        kept.push(line);
    }

    return kept
        .join("\n")
        .replace(/\n{3,}/g, "\n\n")
        .trim();
}

// Detect the format of a description and, when it is Markdown, strip the section headings.
export function normalizeDescription(description: string): NormalizedDescription {
    const trimmed = description.trim();
    if (trimmed === "") {
        return { isMarkdown: false, content: "" };
    }
    if (!isMarkdown(trimmed)) {
        return { content: trimmed, isMarkdown: false };
    }
    return { isMarkdown: true, content: stripSectionHeadings(trimmed) };
}
