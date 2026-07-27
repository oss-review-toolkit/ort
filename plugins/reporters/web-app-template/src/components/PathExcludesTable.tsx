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
import type WebAppPathExclude from "@/models/WebAppPathExclude";

export interface PathExcludesTableProps {
    pathExcludes: readonly WebAppPathExclude[];
}

// A table of the repository's path excludes (pattern, reason, comment).
function PathExcludesTable({ pathExcludes }: PathExcludesTableProps): JSX.Element {
    const data = useMemo(() => pathExcludes.slice(), [pathExcludes]);

    const columns = useMemo<ColumnDef<WebAppPathExclude, unknown>[]>(
        () => [
            {
                id: "reason",
                accessorFn: (row) => row.reason ?? "",
                header: ({ column }) => (
                    <div className="flex items-center gap-2">
                        <DataTableColumnHeader column={column} title="Reason" />
                        <DataTableFacetedFilter column={column} title="Reason" />
                    </div>
                ),
                filterFn: "arrIncludesSome",
                enableColumnFilter: true,
            },
            {
                id: "pattern",
                accessorFn: (row) => row.pattern ?? "",
                header: ({ column }) => <DataTableColumnHeader column={column} title="Pattern" />,
                cell: ({ getValue }) => <span className="font-mono">{String(getValue() ?? "")}</span>,
            },
            {
                id: "comment",
                accessorFn: (row) => row.comment ?? "",
                header: ({ column }) => <DataTableColumnHeader column={column} title="Comment" />,
                meta: { cellClassName: "whitespace-pre-wrap" },
            },
        ],
        [],
    );

    return (
        <DataTable<WebAppPathExclude>
            columns={columns}
            data={data}
            emptyText="No path excludes"
            getRowId={(row, index) => row.key ?? String(index)}
            pageSizeOptions={LARGE_TABLE_PAGE_SIZES}
        />
    );
}

export { PathExcludesTable };
export default PathExcludesTable;
