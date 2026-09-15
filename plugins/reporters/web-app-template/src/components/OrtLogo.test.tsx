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

import { OrtLogo } from "@/components/OrtLogo";

describe("OrtLogo", () => {
    it("renders an image with the OSS Review Toolkit alt text", () => {
        render(<OrtLogo />);
        const images = screen.getAllByRole("img");
        expect(images.length).toBeGreaterThan(0);
        expect(screen.getAllByAltText("OSS Review Toolkit").length).toBeGreaterThan(0);
    });

    it("applies a passed className to the rendered image(s)", () => {
        render(<OrtLogo className="custom-logo-class" />);
        const images = screen.getAllByRole("img");
        expect(images.some((img) => img.classList.contains("custom-logo-class"))).toBe(true);
    });
});
