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

import { ToolsMetadataCards } from "@/components/ToolsMetadataCards";
import ToolsMetadata from "@/models/ToolsMetadata";

describe("ToolsMetadataCards", () => {
    it("renders a card for each tool that has a run", () => {
        const metadata = new ToolsMetadata({
            analyzer: {
                end_time: "2026-01-01T00:01:00Z",
                environment: {
                    java_version: "17.0.1",
                    max_memory: 4 * 1024 ** 2,
                    ort_version: "1.0.0",
                    os: "Linux",
                    processors: 8,
                },
                start_time: "2026-01-01T00:00:00Z",
            },
            scanner: {
                end_time: "2026-01-01T00:05:00Z",
                start_time: "2026-01-01T00:00:00Z",
            },
        });

        render(<ToolsMetadataCards metadata={metadata} />);

        expect(screen.getByText("Analyzer")).toBeInTheDocument();
        expect(screen.getByText("Scanner")).toBeInTheDocument();
        expect(screen.getByText("1.0.0")).toBeInTheDocument();
        expect(screen.queryByText("Advisor")).not.toBeInTheDocument();
        expect(screen.queryByText("Evaluator")).not.toBeInTheDocument();
    });

    it("shows the empty state when no runs are available", () => {
        render(<ToolsMetadataCards metadata={new ToolsMetadata()} />);
        expect(screen.getByText("No run details available.")).toBeInTheDocument();
    });
});
