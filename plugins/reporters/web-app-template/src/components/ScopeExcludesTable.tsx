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
import type WebAppScopeExclude from "@/models/WebAppScopeExclude";

export interface ScopeExcludesTableProps {
    scopeExcludes: readonly WebAppScopeExclude[];
}

// A table of the repository's scope excludes (scope pattern, reason, comment).
function ScopeExcludesTable({ scopeExcludes }: ScopeExcludesTableProps): JSX.Element {
    const data = useMemo(() => scopeExcludes.slice(), [scopeExcludes]);

    const columns = useMemo<ColumnDef<WebAppScopeExclude, unknown>[]>(
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
                id: "name",
                accessorFn: (row) => row.name ?? "",
                header: ({ column }) => <DataTableColumnHeader column={column} title="Name" />,
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
        <DataTable<WebAppScopeExclude>
            columns={columns}
            data={data}
            emptyText="No scope excludes"
            getRowId={(row, index) => row.key ?? String(index)}
            pageSizeOptions={LARGE_TABLE_PAGE_SIZES}
        />
    );
}

export { ScopeExcludesTable };
export default ScopeExcludesTable;
