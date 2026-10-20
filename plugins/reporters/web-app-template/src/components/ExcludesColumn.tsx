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
import { FileX } from "lucide-react";

import { ExcludeStatusIcon, IconHeader } from "@/components/Shared";
import { DataTableFacetedFilter } from "@/components/ui/data-table";
import type WebAppPackage from "@/models/WebAppPackage";

const EXCLUDE_OPTIONS = [
    { label: "Excluded", value: "excluded" },
    { label: "Included", value: "included" },
];

export interface ExcludableRow {
    excludeReason: string;
    isExcluded: boolean;
}

// The reasons a package is excluded for, as shown in the tooltips of the excludes column and the package name.
export function getPackageExcludeReason(pkg: WebAppPackage | undefined): string {
    if (!pkg?.isExcluded) return "";
    return Array.from(pkg.excludeReasons).sort().join(", ");
}

// The include/exclude status column with its filter, matching the one in the packages table. Tables insert it
// as the first data column, right after the expand toggle.
export function createExcludesColumn<TData extends ExcludableRow>(): ColumnDef<TData, unknown> {
    return {
        id: "excludes",
        accessorFn: (row) => (row.isExcluded ? "excluded" : "included"),
        header: ({ column }) => (
            <div className="flex items-center justify-center gap-1">
                <IconHeader Icon={FileX} label="Included / excluded" />
                <DataTableFacetedFilter column={column} options={EXCLUDE_OPTIONS} title="Excludes" />
            </div>
        ),
        cell: ({ row }) => <ExcludeStatusIcon excluded={row.original.isExcluded} reason={row.original.excludeReason} />,
        filterFn: "arrIncludesSome",
        enableColumnFilter: true,
        enableGlobalFilter: false,
        enableSorting: false,
        meta: {
            align: "center",
            headerClassName: "w-16",
            cellClassName: "w-16",
            label: "Excludes",
        },
    };
}
