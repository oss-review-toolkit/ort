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

import type { LucideIcon } from "lucide-react";
import { AlertTriangle, FileX, MessageCircle, MonitorX, SquareArrowOutUpRight } from "lucide-react";
import type { JSX } from "react";

import { CopyToClipboard } from "@/components/Shared";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import type { ReportErrorKind } from "@/lib/reportErrors";
import { cn } from "@/lib/utils";

const ORT_ISSUES_URL = "https://github.com/oss-review-toolkit/ort/issues";
const ORT_SLACK_URL = "https://oss-review-toolkit.slack.com";

interface ErrorPresentation {
    /** Tints the icon to mark something as broken, as opposed to merely unsupported. */
    fault: boolean;
    Icon: LucideIcon;
    summary: string;
    title: string;
}

const PRESENTATIONS: Record<ReportErrorKind, ErrorPresentation> = {
    "missing-data": {
        fault: true,
        Icon: FileX,
        summary:
            "The file contains no readable #ort-report-data payload. Usually the report was not written " +
            "completely, most often because encoding the scan results into it failed.",
        title: "No report data found",
    },
    unknown: {
        fault: true,
        Icon: AlertTriangle,
        summary: "The report data could not be decoded. If the file is intact, this is likely a bug in ORT.",
        title: "Failed to load the report",
    },
    "unsupported-browser": {
        fault: false,
        Icon: MonitorX,
        summary:
            "This report inflates its embedded data with DecompressionStream, which this browser does " +
            "not implement.",
        title: "Unsupported browser",
    },
};

export interface ErrorPageProps {
    /** The thrown message, shown verbatim so it can be copied into a bug report. */
    detail: string;
    kind: ReportErrorKind;
}

/**
 * Shown when the report cannot be loaded.
 *
 * The page names what went wrong rather than leading with "Error" and a fixed apology, because the
 * causes call for different things: a browser too old to inflate the data is not something ORT can fix,
 * while a report written without a readable payload usually is. Slack and the issue tracker are offered
 * either way, so nobody is left without a way to ask.
 */
function ErrorPage({ detail, kind }: ErrorPageProps): JSX.Element {
    const { Icon, fault, summary, title } = PRESENTATIONS[kind];

    return (
        <main className="flex min-h-screen items-center justify-center bg-background p-6">
            {/* The page mounts only once the load has failed, so it needs to announce itself; the Alert
                this replaced carried the role implicitly. */}
            <Card
                aria-live="assertive"
                className="grid w-full max-w-lg justify-items-center p-8 text-center"
                role="alert"
            >
                <span
                    className={cn(
                        "mb-4 flex size-12 items-center justify-center rounded-full",
                        fault ? "bg-destructive/10 text-destructive" : "bg-muted text-muted-foreground",
                    )}
                >
                    <Icon aria-hidden="true" className="size-5.5" />
                </span>
                <h1 className="mb-2 font-semibold text-lg tracking-tight">{title}</h1>
                <p className="mb-5 text-muted-foreground text-sm">{summary}</p>
                <div className="relative mb-5 w-full">
                    <CopyToClipboard
                        className="absolute top-2 right-2 z-10 border bg-background shadow-sm"
                        label="Copy the error details"
                        value={detail}
                    />
                    <p className="overflow-x-auto rounded-md border bg-muted py-2 pr-11 pl-3 text-left font-mono text-xs">
                        {detail}
                    </p>
                </div>
                <div className="flex flex-wrap justify-center gap-2">
                    <Button asChild size="sm" variant="outline">
                        <a href={ORT_SLACK_URL} rel="noopener noreferrer" target="_blank">
                            <MessageCircle aria-hidden="true" />
                            Ask on Slack
                        </a>
                    </Button>
                    <Button asChild size="sm" variant="outline">
                        <a href={ORT_ISSUES_URL} rel="noopener noreferrer" target="_blank">
                            <SquareArrowOutUpRight aria-hidden="true" />
                            Report an issue
                        </a>
                    </Button>
                </div>
            </Card>
        </main>
    );
}

export { ErrorPage };
export default ErrorPage;
