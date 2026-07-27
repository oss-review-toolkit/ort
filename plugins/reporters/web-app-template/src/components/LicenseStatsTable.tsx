/*
 * Copyright (C) 2020 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

import {
    DataTable,
    DataTableColumnHeader,
    DataTableFacetedFilter,
    LARGE_TABLE_PAGE_SIZES,
} from "@/components/data-table";
import { LicenseBadge } from "@/components/Shared";
import { Button } from "@/components/ui/button";

export interface LicenseStatRow {
    color: string;
    name: string;
    value: number;
}

export interface LicenseStatsTableProps {
    emptyText?: string;
    handleClick?: (license: string) => void;
    licenseStats: readonly LicenseStatRow[];
}

// A table of per-license statistics: each license with its package/project counts.
function LicenseStatsTable({
    emptyText = "No licenses",
    handleClick,
    licenseStats,
}: LicenseStatsTableProps): JSX.Element {
    const data = useMemo(() => licenseStats.slice(), [licenseStats]);

    const columns = useMemo<ColumnDef<LicenseStatRow, unknown>[]>(
        () => [
            {
                id: "name",
                accessorKey: "name",
                header: ({ column }) => (
                    <div className="flex items-center gap-2">
                        <DataTableColumnHeader column={column} title="License" />
                        <DataTableFacetedFilter column={column} title="License" />
                    </div>
                ),
                filterFn: "arrIncludesSome",
                enableColumnFilter: true,
                cell: ({ row }) => {
                    const license = row.original;
                    return (
                        <Button
                            className="h-auto p-0 hover:bg-transparent"
                            onClick={(event) => {
                                event.stopPropagation();
                                handleClick?.(license.name);
                            }}
                            size="sm"
                            variant="ghost"
                        >
                            <LicenseBadge name={license.name} />
                        </Button>
                    );
                },
            },
            {
                id: "value",
                accessorKey: "value",
                header: ({ column }) => <DataTableColumnHeader column={column} title="Packages" />,
                meta: { align: "right" },
                cell: ({ row }) => {
                    const license = row.original;
                    return (
                        <Button
                            className="h-auto p-0"
                            onClick={(event) => {
                                event.stopPropagation();
                                handleClick?.(license.name);
                            }}
                            size="sm"
                            variant="link"
                        >
                            {license.value}
                        </Button>
                    );
                },
            },
        ],
        [handleClick],
    );

    return (
        <DataTable<LicenseStatRow>
            columns={columns}
            data={data}
            emptyText={emptyText}
            enableColumnVisibility={false}
            getRowId={(row) => row.name}
            hidePaginationWhenSinglePage
            initialState={{ sorting: [{ id: "value", desc: true }] }}
            pageSizeOptions={LARGE_TABLE_PAGE_SIZES}
        />
    );
}

export { LicenseStatsTable };
export default LicenseStatsTable;
