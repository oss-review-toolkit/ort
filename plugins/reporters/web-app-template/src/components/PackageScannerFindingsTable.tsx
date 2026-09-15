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
import { Copyright, FileText, FileX } from "lucide-react";
import type { JSX } from "react";
import { useMemo } from "react";
import { ExcludeStatusIcon, IconHeader, LicenseExpression, Url } from "@/components/Shared";
import {
    createExpandColumn,
    DataTable,
    DataTableColumnHeader,
    DataTableColumnSearch,
    DataTableFacetedFilter,
    LARGE_TABLE_PAGE_SIZES,
} from "@/components/ui/data-table";
import { parseSpdxLicenseExpression } from "@/lib/spdx-license-expressions";
import { cn } from "@/lib/utils";
import type WebAppFinding from "@/models/WebAppFinding";

export interface PackageScannerFindingsTableProps {
    // The package's effective (non-excluded) SPDX simple license ids. When given, license ids in the
    // filter dropdown that are not part of the effective license are shown muted, so it is easy to see
    // which detected licenses are still applicable.
    effectiveLicenseIds?: ReadonlySet<string>;
    // The license and copyright findings the scanner reported for the package's source artifact.
    scannerFindings: readonly WebAppFinding[];
}

interface ScannerFindingRow {
    copyright: string;
    endLine: number | undefined;
    isExcluded: boolean;
    key: string;
    license: string;
    licenseSimpleExpressions: string[];
    lineRange: string;
    lineRangeStart: number;
    path: string;
    pathExcludeReasons: string;
    scannerFinding: WebAppFinding;
    startLine: number | undefined;
    type: string;
}

const URL_RE = /^(https?:\/\/|www\.)/i;

function isUrl(value: string): boolean {
    return URL_RE.test(value);
}

// Explains why an excluded file path is struck through. Excludes define which code is distributed to
// third parties versus only used internally (see the ORT .ort.yml excludes documentation).
function excludedPathTooltip(reason: string): string {
    const base =
        "Excluded: This file is part of the package sources but is not included in the build/release artifact. " +
        "It is used only internally, e.g. for building, documenting or testing, so its findings are not relevant.";
    return reason ? `${base} Reason: ${reason}.` : base;
}

const EXCLUDE_OPTIONS = [
    { label: "Excluded", value: "excluded" },
    { label: "Included", value: "included" },
];

function excludeFilter(row: Row<ScannerFindingRow>, columnId: string, value: unknown): boolean {
    const filter = value as string[] | undefined;
    if (!filter || filter.length === 0) return true;
    return filter.includes(row.getValue<string>(columnId));
}

// The license column value is an array of the scanner finding's SPDX simple expressions (a composite
// like "Apache-2.0 AND MIT" decomposes to ["Apache-2.0", "MIT"]), so a row matches if any selected id
// is one of its simple expressions.
function licenseFilter(row: Row<ScannerFindingRow>, columnId: string, value: unknown): boolean {
    const filter = value as string[] | undefined;
    if (!filter || filter.length === 0) return true;
    const cell = row.getValue<string[]>(columnId) ?? [];
    return filter.some((v) => cell.includes(v));
}

function buildRow(scannerFinding: WebAppFinding, index: number): ScannerFindingRow {
    const start = scannerFinding.startLine;
    const end = scannerFinding.endLine;
    const lineRange =
        start !== undefined && end !== undefined
            ? start === end
                ? `${start}`
                : `${start} – ${end}`
            : start !== undefined
              ? `${start}`
              : "";
    const reasons = Array.from(scannerFinding.pathExcludeReasons ?? []).join(", ");
    const isLicense = scannerFinding.type === "LICENSE";
    const license = isLicense ? (scannerFinding.license ?? "") : "";
    // Decompose the (possibly composite) license expression so the column filters by each SPDX simple
    // license id, matching how the report-wide table's license columns filter.
    const licenseSimpleExpressions = license ? [...parseSpdxLicenseExpression(license).simpleExpressions].sort() : [];
    return {
        copyright: !isLicense ? (scannerFinding.copyright ?? "") : "",
        endLine: end,
        isExcluded: scannerFinding.isExcluded,
        key: scannerFinding.key ?? `finding-${index}`,
        license,
        licenseSimpleExpressions,
        lineRange,
        lineRangeStart: start ?? Number.MAX_SAFE_INTEGER,
        path: scannerFinding.path ?? "",
        pathExcludeReasons: reasons,
        scannerFinding,
        startLine: start,
        type: scannerFinding.type ?? "",
    };
}

function renderSubRow(row: Row<ScannerFindingRow>): JSX.Element {
    const { endLine, isExcluded, path, pathExcludeReasons, scannerFinding, startLine, type } = row.original;
    return (
        <div className="space-y-2 p-4 text-sm">
            <div className="grid grid-cols-[max-content_1fr] gap-x-4 gap-y-1">
                <span className="font-medium text-muted-foreground">Type</span>
                <span className="font-mono">{type}</span>
                <span className="font-medium text-muted-foreground">Path</span>
                <span className="break-all font-mono">{path}</span>
                {startLine !== undefined && (
                    <>
                        <span className="font-medium text-muted-foreground">Lines</span>
                        <span className="font-mono">
                            {startLine}
                            {endLine !== undefined && endLine !== startLine ? ` – ${endLine}` : ""}
                        </span>
                    </>
                )}
                {scannerFinding.scanResult ? (
                    <>
                        <span className="font-medium text-muted-foreground">Scanner</span>
                        <span className="font-mono">
                            {scannerFinding.scanResult.scanner?.name ?? ""}
                            {scannerFinding.scanResult.scanner?.version
                                ? ` ${scannerFinding.scanResult.scanner.version}`
                                : ""}
                        </span>
                    </>
                ) : null}
                {isExcluded && pathExcludeReasons ? (
                    <>
                        <span className="font-medium text-muted-foreground">Excluded</span>
                        <span>{pathExcludeReasons}</span>
                    </>
                ) : null}
            </div>
        </div>
    );
}

// A table of the scanner's license and copyright findings for a package, with per-column search and an excludes filter.
function PackageScannerFindingsTable({
    effectiveLicenseIds,
    scannerFindings,
}: PackageScannerFindingsTableProps): JSX.Element {
    const data = useMemo<ScannerFindingRow[]>(
        () => scannerFindings.map((scannerFinding, index) => buildRow(scannerFinding, index)),
        [scannerFindings],
    );

    const columns = useMemo<ColumnDef<ScannerFindingRow, unknown>[]>(
        () => [
            createExpandColumn<ScannerFindingRow>(),
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
                    <ExcludeStatusIcon excluded={row.original.isExcluded} reason={row.original.pathExcludeReasons} />
                ),
                filterFn: excludeFilter,
                enableColumnFilter: true,
                enableGlobalFilter: false,
                enableSorting: false,
                meta: { align: "center", headerClassName: "w-16", cellClassName: "w-16", label: "Excludes" },
            },
            {
                id: "type",
                accessorKey: "type",
                header: ({ column }) => <DataTableColumnHeader column={column} title="Type" />,
                cell: ({ row }) => {
                    const value = row.original.type;
                    if (value === "LICENSE") {
                        return <FileText aria-label="License" className="size-4 text-muted-foreground" />;
                    }
                    if (value === "COPYRIGHT") {
                        return <Copyright aria-label="Copyright" className="size-4 text-muted-foreground" />;
                    }
                    return null;
                },
                meta: { headerClassName: "w-12", align: "center", label: "Type" },
                enableColumnFilter: false,
            },
            {
                id: "license",
                meta: { label: "License" },
                accessorFn: (row) => row.licenseSimpleExpressions,
                // Facet on each SPDX simple id rather than the whole array, so the filter dropdown lists
                // every id once (with a correct count) instead of one entry per finding.
                getUniqueValues: (row) => row.licenseSimpleExpressions,
                header: ({ column }) => (
                    <div className="flex items-center gap-2">
                        <DataTableColumnHeader column={column} title="License" />
                        <DataTableFacetedFilter
                            column={column}
                            title="License"
                            {...(effectiveLicenseIds ? { activeValues: effectiveLicenseIds } : {})}
                        />
                    </div>
                ),
                cell: ({ row }) => {
                    const value = row.original.license;
                    if (!value) return null;
                    return <LicenseExpression expression={value} />;
                },
                filterFn: licenseFilter,
                enableColumnFilter: true,
                enableSorting: false,
            },
            {
                id: "copyrights",
                meta: { label: "Copyright" },
                accessorKey: "copyright",
                header: ({ column }) => (
                    <div className="flex items-center gap-0.5">
                        <DataTableColumnHeader column={column} title="Copyright" />
                        <DataTableColumnSearch column={column} title="Copyright" />
                    </div>
                ),
                cell: ({ row }) => {
                    const value = row.original.copyright;
                    if (!value) return null;
                    return <span className="text-xs leading-relaxed">{value}</span>;
                },
                filterFn: "includesString",
                enableColumnFilter: true,
            },
            {
                id: "path",
                meta: { label: "Path" },
                accessorKey: "path",
                header: ({ column }) => (
                    <div className="flex items-center gap-0.5">
                        <DataTableColumnHeader column={column} title="Path" />
                        <DataTableColumnSearch column={column} title="Path" />
                    </div>
                ),
                cell: ({ row }) => {
                    const value = row.original.path;
                    if (!value) return null;
                    const { isExcluded, pathExcludeReasons } = row.original;
                    if (isUrl(value) && !isExcluded) {
                        return (
                            <Url className="font-mono text-xs" href={value}>
                                {value}
                            </Url>
                        );
                    }
                    const path = (
                        <span
                            className={cn(
                                "break-all font-mono text-xs",
                                isExcluded && "text-muted-foreground line-through decoration-muted-foreground",
                            )}
                        >
                            {value}
                        </span>
                    );
                    if (!isExcluded) return path;
                    return <span title={excludedPathTooltip(pathExcludeReasons)}>{path}</span>;
                },
                filterFn: "includesString",
                enableColumnFilter: true,
            },
            {
                id: "lineRange",
                accessorFn: (row) => row.lineRangeStart,
                header: ({ column }) => <DataTableColumnHeader column={column} title="Lines" />,
                cell: ({ row }) => <span className="font-mono text-xs">{row.original.lineRange}</span>,
                sortingFn: "basic",
                enableColumnFilter: false,
                meta: { align: "right", headerClassName: "text-right", label: "Lines" },
            },
        ],
        [effectiveLicenseIds],
    );

    return (
        <DataTable<ScannerFindingRow>
            columns={columns}
            data={data}
            emptyText="No scanner findings"
            enableGlobalFilter={false}
            getRowCanExpand={() => true}
            getRowClassName={(row) => (row.original.isExcluded ? "text-muted-foreground" : undefined)}
            getRowId={(row) => row.key}
            hidePaginationWhenSinglePage
            initialState={{ sorting: [{ id: "path", desc: false }] }}
            pageSizeOptions={LARGE_TABLE_PAGE_SIZES}
            renderSubComponent={renderSubRow}
        />
    );
}

export { PackageScannerFindingsTable };
export default PackageScannerFindingsTable;
