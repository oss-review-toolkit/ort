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

import type { ColumnDef } from "@tanstack/react-table";
import type { JSX } from "react";
import { useMemo } from "react";
import { LicenseBadge, LicenseExpression, PackageLink } from "@/components/Shared";
import {
    DataTable,
    DataTableColumnHeader,
    DataTableColumnSearch,
    DataTableFacetedFilter,
    LARGE_TABLE_PAGE_SIZES,
} from "@/components/ui/data-table";
import type WebAppLicenseChoices from "@/models/WebAppLicenseChoices";

/** The label used for choices that are not restricted to one package. */
export const ALL_PACKAGES = "All packages";

export interface LicenseChoicesTableProps {
    licenseChoices: WebAppLicenseChoices;
    // Jumps to a package in the results table; package-specific rows link their package id when set.
    onSelectPackage?: (packageId: string) => void;
}

/** One applied license choice: a disjunctive expression and the license picked out of it. */
interface LicenseChoiceRow {
    appliesTo: string;
    choice: string;
    comment: string;
    given: string;
    key: string;
    // Unset for repository-wide choices, which apply to every package rather than a named one.
    packageId: string | undefined;
}

/**
 * Flatten both kinds of choice into one row per choice.
 *
 * The evaluated model records what a choice applies to, but not where it was configured: a repository-wide
 * choice and a package-specific one look the same whether they came from the .ort.yml or the global
 * configuration, because ORT merges both into the resolved configuration before writing the report.
 */
function buildRows(licenseChoices: WebAppLicenseChoices): LicenseChoiceRow[] {
    const rows: LicenseChoiceRow[] = [];

    licenseChoices.repositoryLicenseChoices.forEach((licenseChoice, index) => {
        rows.push({
            appliesTo: ALL_PACKAGES,
            choice: licenseChoice.choice ?? "",
            comment: licenseChoice.comment ?? "",
            given: licenseChoice.given ?? "",
            key: `repository-${index}`,
            packageId: undefined,
        });
    });

    licenseChoices.packageLicenseChoices.forEach((packageLicenseChoice, packageIndex) => {
        const packageId = packageLicenseChoice.packageId ?? "";
        packageLicenseChoice.licenseChoices.forEach((licenseChoice, index) => {
            rows.push({
                appliesTo: packageId,
                choice: licenseChoice.choice ?? "",
                comment: licenseChoice.comment ?? "",
                given: licenseChoice.given ?? "",
                key: `package-${packageIndex}-${index}`,
                packageId,
            });
        });
    });

    return rows;
}

// A table of the license choices applied to this run: which disjunctive (OR) expression each one resolves,
// the license it selects, whether it applies repository-wide or to a single package, and the comment
// explaining it, if one was configured.
function LicenseChoicesTable({ licenseChoices, onSelectPackage }: LicenseChoicesTableProps): JSX.Element {
    const data = useMemo(() => buildRows(licenseChoices), [licenseChoices]);

    const columns = useMemo<ColumnDef<LicenseChoiceRow, unknown>[]>(
        () => [
            {
                id: "appliesTo",
                accessorFn: (row) => row.appliesTo,
                header: ({ column }) => (
                    <div className="flex items-center gap-2">
                        <DataTableColumnHeader column={column} title="Applies to" />
                        <DataTableFacetedFilter column={column} title="Applies to" />
                    </div>
                ),
                cell: ({ row }) =>
                    row.original.packageId ? (
                        <PackageLink
                            id={row.original.packageId}
                            {...(onSelectPackage ? { onClick: onSelectPackage } : {})}
                        />
                    ) : (
                        <span className="text-muted-foreground text-xs">{ALL_PACKAGES}</span>
                    ),
                filterFn: "arrIncludesSome",
                enableColumnFilter: true,
            },
            {
                id: "given",
                accessorFn: (row) => row.given,
                header: ({ column }) => (
                    <div className="flex items-center gap-0.5">
                        <DataTableColumnHeader column={column} title="Given" />
                        <DataTableColumnSearch column={column} title="Given" />
                    </div>
                ),
                cell: ({ row }) => <LicenseExpression expression={row.original.given} />,
                filterFn: "includesString",
                enableColumnFilter: true,
            },
            {
                id: "choice",
                accessorFn: (row) => row.choice,
                header: ({ column }) => (
                    <div className="flex items-center gap-2">
                        <DataTableColumnHeader column={column} title="Choice" />
                        <DataTableFacetedFilter column={column} title="Choice" />
                    </div>
                ),
                cell: ({ row }) => <LicenseBadge name={row.original.choice} />,
                filterFn: "arrIncludesSome",
                enableColumnFilter: true,
            },
            {
                id: "comment",
                accessorFn: (row) => row.comment,
                header: ({ column }) => <DataTableColumnHeader column={column} title="Comment" />,
                meta: { cellClassName: "whitespace-pre-wrap" },
            },
        ],
        [onSelectPackage],
    );

    return (
        <DataTable<LicenseChoiceRow>
            columns={columns}
            data={data}
            emptyText="No license choices"
            getRowId={(row, index) => row.key ?? String(index)}
            hidePaginationWhenSinglePage
            pageSizeOptions={LARGE_TABLE_PAGE_SIZES}
        />
    );
}

export { LicenseChoicesTable };
export default LicenseChoicesTable;
