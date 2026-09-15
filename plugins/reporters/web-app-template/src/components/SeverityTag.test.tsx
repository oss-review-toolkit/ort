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
import { describe, expect, it } from "vitest";

import { SeverityTag } from "@/components/SeverityTag";

describe("SeverityTag", () => {
    it("renders the severity label in upper case", () => {
        render(<SeverityTag severity="ERROR" />);
        expect(screen.getByText("ERROR")).toBeInTheDocument();
    });

    it("strikes through the label when the finding is resolved", () => {
        render(<SeverityTag isResolved severity="WARNING" />);
        const label = screen.getByText("WARNING");
        expect(label.tagName).toBe("S");
    });
});
