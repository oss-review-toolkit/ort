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

import type { ColumnDef, Row } from "@tanstack/react-table";
import { FileX } from "lucide-react";
import type { JSX } from "react";
import { useMemo } from "react";

import { ExcludeStatusIcon, IconHeader, PackageLink } from "@/components/Shared";
import { Badge } from "@/components/ui/Badge";
import {
    DataTable,
    DataTableColumnHeader,
    DataTableColumnSearch,
    DataTableFacetedFilter,
    LARGE_TABLE_PAGE_SIZES,
} from "@/components/ui/data-table";
import { cn } from "@/lib/utils";
import type WebAppPackage from "@/models/WebAppPackage";

/** A package together with the licenses it declared that could not be mapped to an SPDX identifier. */
export interface UnmappedLicenseRow {
    excludeReasons: string[];
    isExcluded: boolean;
    key: string;
    licenses: string[];
    packageId: string;
}

const EXCLUDE_OPTIONS = [
    { label: "Excluded", value: "excluded" },
    { label: "Included", value: "included" },
];

function excludeFilter(row: Row<UnmappedLicenseRow>, columnId: string, value: unknown): boolean {
    const filter = value as string[] | undefined;
    if (!filter || filter.length === 0) return true;
    return filter.includes(row.getValue<string>(columnId));
}

export interface UnmappedLicensesTableProps {
    // Jumps to the package in the results table and opens its row.
    onSelectPackage?: (packageId: string) => void;
    packages: readonly WebAppPackage[];
}

/** One row per package that declared something ORT could not map, with the licenses it could not map. */
export function buildUnmappedLicenseRows(packages: readonly WebAppPackage[]): UnmappedLicenseRow[] {
    const rows: UnmappedLicenseRow[] = [];

    packages.forEach((webAppPackage, index) => {
        if (webAppPackage.declaredLicensesUnmapped.size === 0) {
            return;
        }

        rows.push({
            excludeReasons: Array.from(webAppPackage.excludeReasons).sort(),
            isExcluded: webAppPackage.isExcluded,
            key: webAppPackage.key ?? `unmapped-${index}`,
            licenses: Array.from(webAppPackage.declaredLicensesUnmapped).sort(),
            packageId: webAppPackage.id ?? "",
        });
    });

    return rows;
}

/**
 * The declared licenses ORT could not map to an SPDX identifier, listed per package.
 *
 * Deliberately not the license-statistics table the other tabs use: an unmapped license has no SPDX
 * identity to count or chart, and what a reader needs is the package to go and look at. The package id
 * therefore leads straight to its row in the results table.
 *
 * With only two columns, both of them searched in place, the table needs neither a global search box nor
 * a column visibility menu. The toolbar still appears to reset an active search or to scroll a table too
 * wide for its container.
 */
function UnmappedLicensesTable({ onSelectPackage, packages }: UnmappedLicensesTableProps): JSX.Element {
    const data = useMemo(() => buildUnmappedLicenseRows(packages), [packages]);

    const columns = useMemo<ColumnDef<UnmappedLicenseRow, unknown>[]>(
        () => [
            {
                id: "excludes",
                accessorFn: (row) => (row.isExcluded ? "excluded" : "included"),
                header: ({ column }) => (
                    <div className="flex items-center justify-center gap-1">
                        <IconHeader Icon={FileX} label="Included / excluded" />
                        <DataTableFacetedFilter column={column} options={EXCLUDE_OPTIONS} title="Excludes" />
                    </div>
                ),
                cell: ({ row }) => (
                    <ExcludeStatusIcon
                        excluded={row.original.isExcluded}
                        reason={row.original.excludeReasons.join(", ")}
                    />
                ),
                filterFn: excludeFilter,
                enableColumnFilter: true,
                enableGlobalFilter: false,
                enableSorting: false,
                meta: {
                    align: "center",
                    headerClassName: "w-16",
                    cellClassName: "w-16",
                    label: "Excludes",
                },
            },
            {
                id: "package",
                accessorFn: (row) => row.packageId,
                header: ({ column }) => (
                    <div className="flex items-center gap-0.5">
                        <DataTableColumnHeader column={column} title="Package" />
                        <DataTableColumnSearch column={column} title="Package" />
                    </div>
                ),
                cell: ({ row }) => {
                    const { excludeReasons, isExcluded, packageId } = row.original;
                    if (!isExcluded) {
                        return (
                            <PackageLink id={packageId} {...(onSelectPackage ? { onClick: onSelectPackage } : {})} />
                        );
                    }
                    // Struck through and muted like the packages table, with the same "why" on hover, so an
                    // excluded package reads the same wherever it appears.
                    return (
                        <span
                            className={cn(
                                "break-all font-mono text-xs",
                                "text-muted-foreground line-through decoration-muted-foreground",
                            )}
                            title={excludeReasons.length > 0 ? `Excluded: ${excludeReasons.join(", ")}` : "Excluded"}
                        >
                            {packageId}
                        </span>
                    );
                },
                filterFn: "includesString",
                enableColumnFilter: true,
            },
            {
                id: "licenses",
                // Join for the accessor so searching and sorting work on the text: a faceted filter would
                // treat each package's list as one value and offer the same license once per package.
                accessorFn: (row) => row.licenses.join(", "),
                header: ({ column }) => (
                    <div className="flex items-center gap-0.5">
                        <DataTableColumnHeader column={column} title="Unmapped Licenses" />
                        <DataTableColumnSearch column={column} title="Unmapped Licenses" />
                    </div>
                ),
                cell: ({ row }) => (
                    <div className="flex flex-wrap gap-1">
                        {row.original.licenses.map((license) => (
                            // An outline badge rather than the coloured LicenseBadge the SPDX licenses use:
                            // these strings have no SPDX identity, and colouring them as if they did would
                            // suggest ORT understood them.
                            <Badge className="font-mono text-xs" key={license} variant="outline">
                                {license}
                            </Badge>
                        ))}
                    </div>
                ),
                filterFn: "includesString",
                enableColumnFilter: true,
            },
        ],
        [onSelectPackage],
    );

    return (
        <DataTable<UnmappedLicenseRow>
            columns={columns}
            data={data}
            emptyText="No unmapped declared licenses"
            enableColumnVisibility={false}
            enableGlobalFilter={false}
            getRowId={(row, index) => row.key ?? String(index)}
            hidePaginationWhenSinglePage
            pageSizeOptions={LARGE_TABLE_PAGE_SIZES}
        />
    );
}

export { UnmappedLicensesTable };
export default UnmappedLicensesTable;
