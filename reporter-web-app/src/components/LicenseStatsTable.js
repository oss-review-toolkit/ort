/*
 * Copyright (C) 2020 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
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

import React from 'react';
import PropTypes from 'prop-types';
import { Table } from 'antd';
import { FileTextOutlined } from '@ant-design/icons';

// Generates the HTML to display license stats as a Table
const LicenseStatsTable = (props) => {
    const {
        emptyText,
        filter: {
            sortedInfo = {},
            filteredInfo = {}
        },
        licenses,
        licenseStats,
        onChange
    } = props;

    const columns = [
        {
            title: 'License',
            dataIndex: 'name',
            filters: (
                () => licenses.map((license) => ({ text: license, value: license }))
            )(),
            filteredValue: filteredInfo.name || null,
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

    return (
        <Table
            columns={columns}
            dataSource={licenseStats}
            locale={{
                emptyText: { emptyText }
            }}
            onChange={onChange}
            pagination={
                {
                    defaultPageSize: 50,
                    hideOnSinglePage: true,
                    position: 'bottom',
                    showTotal: (total, range) => `${range[0]}-${range[1]} of ${total} results`
                }
            }
            rowKey="name"
            size="small"
        />
    );
};

LicenseStatsTable.propTypes = {
    emptyText: PropTypes.string.isRequired,
    filter: PropTypes.object.isRequired,
    licenses: PropTypes.array.isRequired,
    licenseStats: PropTypes.array.isRequired,
    onChange: PropTypes.func.isRequired
};

export default LicenseStatsTable;
