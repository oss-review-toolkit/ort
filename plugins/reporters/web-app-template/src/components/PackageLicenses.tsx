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
import { useMemo } from "react";

import {
    CopyToClipboard,
    DefinedTerm,
    LICENSE_TERM_DEFINITIONS,
    LicenseBadgeList,
    LicenseExpression,
    LicenseExpressionList,
} from "@/components/Shared";
import type WebAppPackage from "@/models/WebAppPackage";

export interface PackageLicensesProps {
    pkg: WebAppPackage;
}

interface LicenseRow {
    content: ReactNode;
    // When set, a button copies this exact license string to the clipboard.
    copyValue?: string;
    // When set, the key shows a dotted underline and a hover tooltip with this definition.
    definition?: string;
    label: string;
}

// A definition list of a package's license fields (effective / declared / detected / ...) with copy buttons and tooltips.
function PackageLicenses({ pkg }: PackageLicensesProps): JSX.Element {
    const rows = useMemo<LicenseRow[]>(() => {
        const result: LicenseRow[] = [];

        if (pkg.hasConcludedLicense()) {
            result.push({
                content: <LicenseExpression expression={pkg.concludedLicense ?? ""} />,
                label: "Concluded SPDX",
            });
        }

        if (pkg.hasEffectiveLicense()) {
            result.push({
                content: <LicenseExpression expression={pkg.effectiveLicense ?? ""} />,
                copyValue: pkg.effectiveLicense ?? "",
                definition: LICENSE_TERM_DEFINITIONS.effective,
                label: "Effective (SPDX)",
            });
        }

        if (pkg.hasDeclaredLicenses()) {
            const declared = Array.from(pkg.declaredLicenses);
            result.push({
                content: <LicenseBadgeList names={declared} />,
                copyValue: declared.join(", "),
                definition: LICENSE_TERM_DEFINITIONS.declared,
                label: "Declared",
            });
        }

        if (pkg.hasDeclaredLicensesSpdxExpression()) {
            result.push({
                content: <LicenseExpression expression={pkg.declaredLicensesSpdxExpression ?? ""} />,
                copyValue: pkg.declaredLicensesSpdxExpression ?? "",
                label: "Declared (SPDX)",
            });
        }

        if (pkg.hasDeclaredLicensesUnmapped()) {
            result.push({
                content: <LicenseBadgeList names={Array.from(pkg.declaredLicensesUnmapped)} />,
                definition: LICENSE_TERM_DEFINITIONS.declaredNonMapped,
                label: "Declared (non-SPDX)",
            });
        }

        if (pkg.hasDetectedLicenses()) {
            const excluded = pkg.detectedExcludedLicenses;
            const detected = Array.from(pkg.detectedLicenses).filter((license) => !excluded.has(license));
            result.push({
                content: <LicenseExpressionList expressions={detected} />,
                copyValue: detected.join(", "),
                definition: LICENSE_TERM_DEFINITIONS.detected,
                label: "Detected",
            });
        }

        if (pkg.hasDetectedExcludedLicenses()) {
            result.push({
                content: <LicenseExpressionList expressions={Array.from(pkg.detectedExcludedLicenses)} />,
                label: "Detected Excluded",
            });
        }

        return result;
    }, [pkg]);

    if (rows.length === 0) {
        return <p className="text-muted-foreground text-sm">No licenses.</p>;
    }

    return (
        <dl className="grid grid-cols-[max-content_1fr] gap-x-4 gap-y-2 text-sm">
            {rows.map((row) => (
                <div className="contents" key={row.label}>
                    <dt className="font-medium text-muted-foreground">
                        {row.definition ? <DefinedTerm definition={row.definition} term={row.label} /> : row.label}
                    </dt>
                    <dd className="flex items-start gap-1">
                        <span className="min-w-0 flex-1">{row.content}</span>
                        {row.copyValue ? (
                            <CopyToClipboard
                                className="-my-1 size-6 shrink-0 text-muted-foreground"
                                label={`Copy ${row.label.toLowerCase().replace("(spdx)", "(SPDX)")} license`}
                                value={row.copyValue}
                            />
                        ) : null}
                    </dd>
                </div>
            ))}
        </dl>
    );
}

export { PackageLicenses };
export default PackageLicenses;
