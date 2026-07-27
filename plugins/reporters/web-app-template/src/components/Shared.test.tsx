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

import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import {
    convertIso8601Date2Sentence,
    ExcludeStatusIcon,
    LicenseBadge,
    LicenseExpression,
    PackageLink,
    Url,
} from "@/components/Shared";

describe("LicenseBadge", () => {
    it("renders the license name", () => {
        render(<LicenseBadge name="Apache-2.0" />);
        expect(screen.getByText("Apache-2.0")).toBeInTheDocument();
    });

    it("renders the full id and truncates overflow with CSS (not by slicing the text)", () => {
        const longId = "LicenseRef-scancode-public-domain-disclaimer";
        render(<LicenseBadge name={longId} />);
        const badge = screen.getByText(longId);
        // The full id stays in the DOM (available via the title tooltip); overflow is clipped by CSS.
        expect(badge).toBeInTheDocument();
        expect(badge).toHaveAttribute("title", longId);
        expect(badge).toHaveClass("truncate");
    });

    it("renders the NOASSERTION sentinel", () => {
        render(<LicenseBadge name="NOASSERTION" />);
        expect(screen.getByText("NOASSERTION")).toBeInTheDocument();
    });
});

describe("PackageLink", () => {
    it("renders plain text when no handler is given", () => {
        render(<PackageLink id="NPM::acorn:8.0.0" />);
        expect(screen.getByText("NPM::acorn:8.0.0").tagName).toBe("SPAN");
    });

    it("calls onClick with the id when activated", async () => {
        const onClick = vi.fn();
        const user = userEvent.setup();
        render(<PackageLink id="NPM::acorn:8.0.0" onClick={onClick} />);

        await user.click(screen.getByRole("button", { name: "NPM::acorn:8.0.0" }));

        expect(onClick).toHaveBeenCalledTimes(1);
        expect(onClick).toHaveBeenCalledWith("NPM::acorn:8.0.0");
    });

    it("renders nothing for an empty id", () => {
        const { container } = render(<PackageLink id="" />);
        expect(container).toBeEmptyDOMElement();
    });
});

describe("ExcludeStatusIcon", () => {
    it("describes an included item", () => {
        render(<ExcludeStatusIcon excluded={false} />);
        expect(screen.getByText("Included")).toBeInTheDocument();
    });

    it("describes an excluded item with its reason", () => {
        render(<ExcludeStatusIcon excluded reason="TEST_OF" />);
        expect(screen.getByText("Excluded: TEST_OF")).toBeInTheDocument();
    });
});

describe("Url", () => {
    it("renders an external anchor to the given href", () => {
        render(<Url href="https://example.com/repo">example</Url>);
        const link = screen.getByRole("link");
        expect(link).toHaveAttribute("href", "https://example.com/repo");
        expect(link).toHaveAttribute("target", "_blank");
        expect(link).toHaveTextContent("example");
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

describe("LicenseExpression", () => {
    it("splits a composite expression into per-license badges and operators", () => {
        render(<LicenseExpression expression="Apache-2.0 AND MIT" />);
        expect(screen.getByText("Apache-2.0")).toBeInTheDocument();
        expect(screen.getByText("MIT")).toBeInTheDocument();
        expect(screen.getByText("AND")).toBeInTheDocument();
    });

    it("renders a single license as one badge", () => {
        render(<LicenseExpression expression="MIT" />);
        expect(screen.getByText("MIT")).toBeInTheDocument();
    });

    it("renders nothing for an empty expression", () => {
        const { container } = render(<LicenseExpression expression="" />);
        expect(container).toBeEmptyDOMElement();
    });
});
