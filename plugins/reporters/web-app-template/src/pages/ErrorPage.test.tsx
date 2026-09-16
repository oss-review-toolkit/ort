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
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";

import type { ReportErrorKind } from "@/lib/reportErrors";
import { ErrorPage } from "@/pages/ErrorPage";
import { render } from "@/test/render";

const KINDS: ReportErrorKind[] = ["missing-data", "unknown", "unsupported-browser"];

describe("ErrorPage", () => {
    it("names what went wrong, per cause", () => {
        const titles = KINDS.map((kind) => {
            const { unmount } = render(<ErrorPage detail="boom" kind={kind} />);
            const title = screen.getByRole("heading").textContent;
            unmount();
            return title;
        });

        expect(titles).toEqual(["No report data found", "Failed to load the report", "Unsupported browser"]);
    });

    it("offers Slack and the issue tracker for every cause, so nobody is left without a way to ask", () => {
        for (const kind of KINDS) {
            const { unmount } = render(<ErrorPage detail="boom" kind={kind} />);

            expect(screen.getByRole("link", { name: /ask on slack/i })).toHaveAttribute(
                "href",
                "https://oss-review-toolkit.slack.com",
            );
            expect(screen.getByRole("link", { name: /report an issue/i })).toHaveAttribute(
                "href",
                "https://github.com/oss-review-toolkit/ort/issues",
            );
            unmount();
        }
    });

    it("announces itself, mounting only after the load has already failed", () => {
        render(<ErrorPage detail="boom" kind="unknown" />);

        // Replacing the Alert with a Card dropped the implicit role, leaving a screen reader with no
        // notice that anything had gone wrong.
        const alert = screen.getByRole("alert");
        expect(alert).toHaveAttribute("aria-live", "assertive");
        expect(alert).toHaveTextContent("Failed to load the report");
    });

    it("shows the thrown message verbatim, so it can go into a bug report unchanged", () => {
        render(<ErrorPage detail={'Unsupported report data type "text/plain".'} kind="missing-data" />);

        expect(screen.getByText('Unsupported report data type "text/plain".')).toBeInTheDocument();
    });

    it("copies the detail with the button the run details tabs use", async () => {
        // userEvent installs its own clipboard stub, so read the value back through it rather than
        // replacing navigator.clipboard, which it would overwrite.
        const user = userEvent.setup();
        render(<ErrorPage detail="Report data worker failed." kind="unknown" />);

        await user.click(screen.getByRole("button", { name: /copy the error details/i }));

        await expect(navigator.clipboard.readText()).resolves.toBe("Report data worker failed.");
    });

    it("marks what is broken, but not a browser that is merely too old", () => {
        const faulted = KINDS.map((kind) => {
            const { container, unmount } = render(<ErrorPage detail="boom" kind={kind} />);
            const isFault = container.querySelector(".text-destructive") !== null;
            unmount();
            return isFault;
        });

        expect(faulted).toEqual([true, true, false]);
    });
});
