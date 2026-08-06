/*
 * Copyright (C) 2017 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

import { Url } from "@/components/Shared";
import { Separator } from "@/components/ui/Separator";
import type WebAppPackage from "@/models/WebAppPackage";

export interface PackageDetailsProps {
    pkg: WebAppPackage;
}

interface Section {
    rows: Array<{ label: string; content: ReactNode }>;
    title?: string;
}

function buildSections(pkg: WebAppPackage): Section[] {
    const sections: Section[] = [];

    const identity: Section["rows"] = [];
    if (pkg.id) {
        identity.push({ label: "Id", content: <span className="font-mono">{pkg.id}</span> });
    }
    if (pkg.purl) {
        identity.push({ label: "Package URL", content: <span className="font-mono">{pkg.purl}</span> });
    }
    if (pkg.isProject && pkg.definitionFilePath) {
        identity.push({
            content: <span className="font-mono">{pkg.definitionFilePath}</span>,
            label: "Defined in",
        });
    }
    if (pkg.hasAuthors()) {
        identity.push({ label: "Authors", content: Array.from(pkg.authors).join(", ") });
    }
    if (pkg.description) {
        identity.push({ label: "Description", content: pkg.description });
    }
    if (pkg.homepageUrl) {
        identity.push({ label: "Homepage", content: <Url href={pkg.homepageUrl} truncate /> });
    }
    if (identity.length > 0) sections.push({ rows: identity });

    const declaredVcs = pkg.vcs;
    const processedVcs = pkg.vcsProcessed;
    const vcsRevision = declaredVcs.revision || declaredVcs.resolvedRevision;
    const processedRevision = processedVcs.revision || processedVcs.resolvedRevision;

    const repository: Section["rows"] = [];
    if (declaredVcs.url) {
        repository.push({ label: "Declared Repository", content: <Url href={declaredVcs.url} truncate /> });
    }
    if (vcsRevision && vcsRevision !== processedRevision) {
        repository.push({
            content: <span className="font-mono">{vcsRevision}</span>,
            label: "Declared Repository Revision",
        });
    }
    if (declaredVcs.path) {
        repository.push({
            content: <span className="font-mono">{declaredVcs.path}</span>,
            label: "Declared Repository Sources Path",
        });
    }
    if (processedVcs.url) {
        repository.push({ label: "Processed Repository", content: <Url href={processedVcs.url} truncate /> });
    }
    if (processedRevision) {
        repository.push({
            content: <span className="font-mono">{processedRevision}</span>,
            label: "Processed Repository Revision",
        });
    }
    if (processedVcs.path) {
        repository.push({
            content: <span className="font-mono">{processedVcs.path}</span>,
            label: "Processed Repository Sources Path",
        });
    }
    if (repository.length > 0) sections.push({ rows: repository });

    const artifacts: Section["rows"] = [];
    const sourceArtifactUrl = pkg.sourceArtifact?.url;
    const binaryArtifactUrl = pkg.binaryArtifact?.url;
    if (sourceArtifactUrl) {
        artifacts.push({ label: "Source Artifact", content: <Url href={sourceArtifactUrl} truncate /> });
    }
    if (binaryArtifactUrl) {
        artifacts.push({ label: "Binary Artifact", content: <Url href={binaryArtifactUrl} truncate /> });
    }
    if (artifacts.length > 0) sections.push({ rows: artifacts });

    return sections;
}

// A definition list of a package's core metadata (id, type, VCS, source, homepage, description).
function PackageDetails({ pkg }: PackageDetailsProps): JSX.Element {
    const sections = buildSections(pkg);

    if (sections.length === 0) {
        return <p className="text-muted-foreground text-sm">No package details.</p>;
    }

    return (
        <div className="space-y-4 text-sm">
            {sections.map((section, sectionIndex) => {
                const sectionKey = section.rows.map((row) => row.label).join("|") || `section-${sectionIndex}`;
                return (
                    <div key={sectionKey}>
                        {sectionIndex > 0 ? <Separator className="mb-4" /> : null}
                        <dl className="grid grid-cols-1 gap-x-6 gap-y-2 md:grid-cols-[max-content_minmax(0,1fr)]">
                            {section.rows.map((row) => (
                                <div className="contents" key={row.label}>
                                    <dt className="font-medium text-muted-foreground">{row.label}</dt>
                                    <dd className="min-w-0 break-words">{row.content}</dd>
                                </div>
                            ))}
                        </dl>
                    </div>
                );
            })}
        </div>
    );
}

export { PackageDetails };
export default PackageDetails;
