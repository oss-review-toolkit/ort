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

/**
 * Why a report failed to load.
 *
 * The error page tells these apart so it can name what happened and say what to do about it. Nothing
 * ORT does can help a browser too old to inflate the payload, whereas a report carrying no readable
 * payload usually points back at how it was written.
 */
export type ReportErrorKind = "missing-data" | "unknown" | "unsupported-browser";

/** An error carrying the reason the report could not be loaded, classified at the throw site. */
export class ReportLoadError extends Error {
    readonly kind: ReportErrorKind;

    constructor(kind: ReportErrorKind, message: string) {
        super(message);
        this.name = "ReportLoadError";
        this.kind = kind;
    }
}

/** The kind of a caught value, treating anything not thrown by the load path as unknown. */
export function toReportErrorKind(err: unknown): ReportErrorKind {
    return err instanceof ReportLoadError ? err.kind : "unknown";
}

/**
 * The message of a caught value, for the detail block the reader can copy into a bug report.
 *
 * An `Error` is allowed an empty message and the worker can forward one, so fall back to its name, and
 * to a placeholder for anything else that stringifies to nothing. An empty block would leave the reader
 * with nothing to report.
 */
export function toReportErrorDetail(err: unknown): string {
    const detail = err instanceof Error ? err.message || err.name : String(err);
    return detail === "" ? "No detail was reported." : detail;
}
