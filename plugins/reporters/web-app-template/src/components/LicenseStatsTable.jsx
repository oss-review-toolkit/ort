/*
 * Copyright (C) 2020 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import {
    useState
} from 'react';

import { FileTextOutlined } from '@ant-design/icons';
import { Table } from 'antd';

// Generates the HTML to display license stats as a table
const LicenseStatsTable = ({ emptyText, licenses, licenseStats }) => {
    /* === Table state handling === */

    // State variable for displaying table in various pages
    const [pagination, setPagination] = useState({ current: 1, pageSize: 100 });

    // State variable for filtering the contents of table columns
    const filteredInfoDefault = {
        license: [],
        packages: []
    };
    const [filteredInfo, setFilteredInfo] = useState(filteredInfoDefault);

    // State variable for sorting table columns
    const [sortedInfo, setSortedInfo] = useState({});

    const columns = [
        {
            title: 'License',
            dataIndex: 'name',
            filteredValue: filteredInfo.name || null,
            filters: (
                () => licenses.map((license) => ({ text: license, value: license }))
            )(),
            onFilter: (license, row) => row.name === license,
            sorter: (a, b) => a.name.localeCompare(b.name),
            sortOrder: sortedInfo.columnKey === 'name' && sortedInfo.order,
            key: 'name',
            render: (text, row) => (
                <span>
                    <FileTextOutlined style={{ color: row.color }} />
                    {` ${text}`}
                </span>
            ),
            textWrap: 'word-break'
        }, {
            title: 'Packages',
            dataIndex: 'value',
            defaultSortOrder: 'descend',
            sorter: (a, b) => a.value - b.value,
            sortOrder: sortedInfo.columnKey === 'value' && sortedInfo.order,
            key: 'value',
            width: 150
        }
    ];

    // Handle for table pagination changes
    const handlePaginationChange = (page, pageSize) => {
        setPagination({ current: page, pageSize });
    };

    // Handle for any table content changes
    const handleTableChange = (pagination, filters, sorter) => {
        setFilteredInfo(filters);
        setSortedInfo(sorter);
    };

    return (
        <Table
            columns={columns}
            dataSource={licenseStats}
            rowKey="name"
            size="small"
            locale={{
                emptyText: { emptyText }
            }}
            pagination={
                {
                    current: pagination.current,
                    defaultPageSize: 50,
                    hideOnSinglePage: true,
                    onChange: handlePaginationChange,
                    position: 'bottom',
                    pageSizeOptions: ['50', '100', '250', '500', '1000', '5000'],
                    showTotal: (total, range) => `${range[0]}-${range[1]} of ${total} results`
                }
            }
            onChange={handleTableChange}
        />
    );
};

export default LicenseStatsTable;
