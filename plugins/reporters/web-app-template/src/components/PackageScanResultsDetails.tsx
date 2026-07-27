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

import type { JSX, ReactNode } from "react";

import { Paginated } from "@/components/Paginated";
import { convertIso8601Date2Sentence, Url } from "@/components/Shared";
import type WebAppPackage from "@/models/WebAppPackage";
import type WebAppScanResult from "@/models/WebAppScanResult";

export interface PackageScanResultsDetailsProps {
    pkg: WebAppPackage;
}

interface DetailRow {
    content: ReactNode;
    label: string;
}

function buildRows(scanResult: WebAppScanResult): DetailRow[] {
    const rows: DetailRow[] = [];
    const provenance = scanResult.provenance;
    const sourceArtifact = provenance?.sourceArtifact;
    const vcsInfo = provenance?.vcsInfo;
    const scanner = scanResult.scanner;

    if (sourceArtifact?.url) {
        rows.push({ label: "Scanned Source Artifact", content: <Url href={sourceArtifact.url} /> });
    }
    if (sourceArtifact?.hash?.value) {
        rows.push({
            content: <span className="font-mono">{sourceArtifact.hash.value}</span>,
            label: "Scanned Source Artifact Hash",
        });
    }
    if (sourceArtifact?.hash?.algorithm) {
        rows.push({ label: "Scanned Source Artifact Hash Algorithm", content: sourceArtifact.hash.algorithm });
    }
    if (vcsInfo?.url) {
        rows.push({ label: "Scanned Repository", content: <Url href={vcsInfo.url} /> });
    }
    if (vcsInfo?.revision) {
        rows.push({
            content: <span className="font-mono">{vcsInfo.revision}</span>,
            label: "Scanned Repository Revision",
        });
    }
    if (vcsInfo?.path) {
        rows.push({ label: "Scanned Repository Path", content: <span className="font-mono">{vcsInfo.path}</span> });
    }
    if (scanner?.name) {
        rows.push({ label: "Scanner", content: scanner.name });
    }
    if (scanner?.version) {
        rows.push({ label: "Scanner Version", content: scanner.version });
    }
    if (scanner?.configuration) {
        rows.push({
            content: <code className="text-xs">{scanner.configuration}</code>,
            label: "Scanner Configuration",
        });
    }
    if (scanResult.startTime) {
        rows.push({ label: "Scanner Start Time", content: convertIso8601Date2Sentence(scanResult.startTime) });
    }
    if (scanResult.endTime) {
        rows.push({ label: "Scanner End Time", content: convertIso8601Date2Sentence(scanResult.endTime) });
    }

    return rows;
}

// Details of a package's raw scan results (scanner name/version and scanned provenance).
function PackageScanResultsDetails({ pkg }: PackageScanResultsDetailsProps): JSX.Element {
    const scanResults = (pkg.scanResults ?? []).filter(
        (scanResult): scanResult is WebAppScanResult => scanResult !== null,
    );

    if (scanResults.length === 0) {
        return <p className="text-muted-foreground text-sm">No scan results.</p>;
    }

    return (
        <Paginated
            containerClassName="grid grid-cols-1 gap-4 md:grid-cols-2"
            getKey={(scanResult, index) => scanResult.key ?? `scan-${index}`}
            itemLabel="scanners"
            items={scanResults}
            pageSize={2}
            renderItem={(scanResult) => {
                const rows = buildRows(scanResult);
                return (
                    <dl className="space-y-2.5 rounded-md border p-4 text-sm">
                        {rows.map((row) => (
                            <div className="flex flex-col gap-0.5" key={row.label}>
                                <dt className="font-medium text-muted-foreground text-xs">{row.label}</dt>
                                <dd className="break-all">{row.content}</dd>
                            </div>
                        ))}
                    </dl>
                );
            }}
        />
    );
}

export { PackageScanResultsDetails };
export default PackageScanResultsDetails;
