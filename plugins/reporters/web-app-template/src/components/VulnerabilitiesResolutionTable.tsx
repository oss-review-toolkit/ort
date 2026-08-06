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

import {
    DataTable,
    DataTableColumnHeader,
    DataTableFacetedFilter,
    LARGE_TABLE_PAGE_SIZES,
} from "@/components/ui/data-table";
import type WebAppVulnerabilityResolution from "@/models/WebAppVulnerabilityResolution";

export interface VulnerabilitiesResolutionTableProps {
    resolutions: readonly WebAppVulnerabilityResolution[];
}

// A table of the repository's vulnerability resolutions (reason, id pattern, comment).
function VulnerabilitiesResolutionTable({ resolutions }: VulnerabilitiesResolutionTableProps): JSX.Element {
    const data = useMemo(() => resolutions.slice(), [resolutions]);

    const columns = useMemo<ColumnDef<WebAppVulnerabilityResolution, unknown>[]>(
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
                id: "id",
                accessorFn: (row) => row.id ?? "",
                header: ({ column }) => <DataTableColumnHeader column={column} title="Id" />,
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
        <DataTable<WebAppVulnerabilityResolution>
            columns={columns}
            data={data}
            emptyText="No resolutions"
            getRowId={(row, index) => row.key ?? String(index)}
            pageSizeOptions={LARGE_TABLE_PAGE_SIZES}
        />
    );
}

export { VulnerabilitiesResolutionTable };
export default VulnerabilitiesResolutionTable;
