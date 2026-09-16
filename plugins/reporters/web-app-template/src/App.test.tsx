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

import { screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it } from "vitest";

import App from "@/App";
import { render } from "@/test/render";

// App caches the single in-flight load on window, so it has to be cleared between tests.
const LOAD_KEY = "__ortReportDataLoad__";

function addReportDataScript(text: string, type?: string): void {
    const script = document.createElement("script");
    script.id = "ort-report-data";
    if (type !== undefined) {
        script.type = type;
    }
    script.textContent = text;
    document.body.appendChild(script);
}

describe("App", () => {
    beforeEach(() => {
        delete (window as unknown as Record<string, unknown>)[LOAD_KEY];
    });

    afterEach(() => {
        document.getElementById("ort-report-data")?.remove();
    });

    it("reports a missing data script element instead of rendering nothing", async () => {
        // Reading the payload throws, and a throw escaping the effect unmounts the app. A truncated
        // download hits this: the inlined bundle comes long before the data in a built report.
        const { container } = render(<App />);

        expect(await screen.findByText(/#ort-report-data not found/i)).toBeInTheDocument();
        expect(container.innerHTML).not.toBe("");
    });

    it("reports a data script element it cannot read", async () => {
        addReportDataScript('{"foo":1}', "text/plain");
        render(<App />);

        expect(await screen.findByText(/unsupported report data type "text\/plain"/i)).toBeInTheDocument();
    });

    it("keeps the message the reader needs on screen, not just in the console", async () => {
        render(<App />);

        expect(await screen.findByText("Oops, something went wrong...")).toBeInTheDocument();
        expect(screen.getByRole("link", { name: /issue on github/i })).toBeInTheDocument();
    });

    it("tells the reader a template carries no results yet", async () => {
        // The reporter ships index.html with a 27 character placeholder in place of the report data.
        addReportDataScript("ORT_REPORT_DATA_PLACEHOLDER", "application/json");
        render(<App />);

        expect(await screen.findByRole("heading", { name: "An empty report" })).toBeInTheDocument();
    });

    it("does not dress the empty template as a failure", async () => {
        addReportDataScript("ORT_REPORT_DATA_PLACEHOLDER", "application/json");
        render(<App />);

        await screen.findByRole("heading", { name: "An empty report" });
        // Reusing the error page made a template indistinguishable from a report that failed to load.
        expect(screen.queryByRole("alert")).not.toBeInTheDocument();
        expect(screen.queryByText(/something went wrong/i)).not.toBeInTheDocument();
        expect(screen.queryByRole("link", { name: /issue on github/i })).not.toBeInTheDocument();
    });
});
