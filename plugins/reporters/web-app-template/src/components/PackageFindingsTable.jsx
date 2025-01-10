/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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
    useMemo,
    useState
} from 'react';

import {
    CopyrightOutlined,
    FileAddOutlined,
    FileExcelOutlined,
    FileTextOutlined,
    MinusSquareOutlined,
    PlusSquareOutlined
} from '@ant-design/icons';
import {
    Empty,
    Table,
    Tooltip
} from 'antd';

import PathExcludesTable from './PathExcludesTable';
import { getColumnSearchProps } from './Shared';

// Generates the HTML to display scanFindings as a table
const PackageFindingsTable = ({ webAppPackage }) => {
    // Convert scan findings as Antd only accepts vanilla objects as input
    const findings = useMemo(
        () => {
            return webAppPackage.findings
                .map(
                    (finding) => ({
                        endLine: finding.endLine,
                        isExcluded: finding.isExcluded,
                        key: finding.key,
                        path: finding.path,
                        pathExcludes: finding.pathExcludes,
                        pathExcludeReasonsText: Array.from(finding.pathExcludeReasons).join(', '),
                        startLine: finding.startLine,
                        value: finding.value
                    })
                )
        },
        []
    );

    let expandable = null;

    /* === Table state handling === */

    // State variable for displaying table in various pages
    const [pagination, setPagination] = useState({ current: 1, pageSize: 100 });

    // State variable for filtering the contents of table columns
    const filteredInfoDefault = {
        value: '',
        path: '',
        startLine: '',
        endLine: ''
    };
    const [filteredInfo, setFilteredInfo] = useState(filteredInfoDefault);

    // State variable for sorting table columns
    const [sortedInfo, setSortedInfo] = useState({});

    /* === Table columns === */

    const columns = [];

    if (webAppPackage.hasDetectedExcludedLicenses() || webAppPackage.hasExcludedFindings()) {
        columns.push({
            align: 'right',
            filters: (() => [
                {
                    text: (
                        <span>
                            <FileExcelOutlined className="ort-excluded" />
                            {' '}
                            Excluded
                        </span>
                    ),
                    value: 'excluded'
                },
                {
                    text: (
                        <span>
                            <FileAddOutlined />
                            {' '}
                            Included
                        </span>
                    ),
                    value: 'included'
                }
            ])(),
            key: 'excludes',
            onFilter: (value, record) => {
                if (value === 'excluded') {
                    return record.isExcluded;
                }

                if (value === 'included') {
                    return !record.isExcluded;
                }

                return false;
            },
            render: (record) => (
                record.isExcluded
                    ? (
                        <span className="ort-excludes">
                            <Tooltip
                                placement="right"
                                title={record.pathExcludeReasonsText}
                            >
                                <FileExcelOutlined className="ort-excluded" />
                            </Tooltip>
                        </span>
                        )
                    : (
                        <FileAddOutlined />
                        )
            ),
            width: '2em'
        });

        expandable = {
            expandedRowRender: (webAppFinding) => (
                <PathExcludesTable
                    excludes={webAppFinding.pathExcludes}
                />
            ),
            expandIcon: (obj) => {
                const { expanded, onExpand, record } = obj;

                if (record.isExcluded === false) {
                    return null;
                }

                return (
                    expanded
                        ? (
                            <MinusSquareOutlined onClick={(e) => onExpand(record, e)} />
                            )
                        : (
                            <PlusSquareOutlined onClick={(e) => onExpand(record, e)} />
                            )
                );
            },
            indentSize: 0
        };
    }

    columns.push(
        {
            align: 'right',
            dataIndex: 'type',
            filters: (() => [
                {
                    text: (
                        <span>
                            <CopyrightOutlined />
                            {' '}
                            Copyright
                        </span>
                    ),
                    value: 'COPYRIGHT'
                },
                {
                    text: (
                        <span>
                            <FileTextOutlined />
                            {' '}
                            License
                        </span>
                    ),
                    value: 'LICENSE'
                }
            ])(),
            filteredValue: filteredInfo.type || null,
            key: 'type',
            onFilter: (value, record) => value === record.type,
            render: (text) => (
                <span className="ort-scan-finding-type">
                    {text === 'LICENSE' && (<FileTextOutlined />)}
                    {text === 'COPYRIGHT' && (<CopyrightOutlined />)}
                </span>
            ),
            width: '1em'
        },
        {
            dataIndex: 'value',
            filters: (
                () => Array.from(webAppPackage.detectedLicenses)
                    .map(
                        (license) => {
                            if (webAppPackage.hasDetectedExcludedLicenses()) {
                                const { detectedExcludedLicenses } = webAppPackage;

                                if (detectedExcludedLicenses.has(license)) {
                                    return {
                                        text: (
                                            <span>
                                                <FileExcelOutlined
                                                    className="ort-excluded"
                                                />
                                                {' '}
                                                {license}
                                            </span>
                                        ),
                                        value: license
                                    };
                                }

                                return {
                                    text: (
                                        <span>
                                            <FileAddOutlined />
                                            {' '}
                                            {license}
                                        </span>
                                    ),
                                    value: license
                                };
                            }

                            return {
                                text: license,
                                value: license
                            };
                        }
                    )
            )(),
            filteredValue: filteredInfo.value || null,
            onFilter: (value, row) => value === row.value,
            key: 'value',
            textWrap: 'word-break',
            title: 'Value'
        },
        {
            dataIndex: 'path',
            defaultSortOrder: 'ascend',
            filteredValue: filteredInfo.path,
            key: 'path',
            sorter: (a, b) => a.path.localeCompare(b.path),
            textWrap: 'word-break',
            title: 'Path',
            ...getColumnSearchProps(
                'path',
                filteredInfo.path,
                (value) => setFilteredInfo({ ...filteredInfo, path: value })
            )
        },
        {
            align: 'center',
            dataIndex: 'startLine',
            key: 'startLine',
            filteredValue: filteredInfo.startLine || null,
            responsive: ['md'],
            title: 'Start',
            ...getColumnSearchProps(
                'startLine',
                filteredInfo.startLine,
                (value) => setFilteredInfo({ ...filteredInfo, startLine: value })
            )
        },
        {
            align: 'center',
            dataIndex: 'endLine',
            filteredValue: filteredInfo.endLine || null,
            key: 'endLine',
            responsive: ['md'],
            title: 'End',
            ...getColumnSearchProps(
                'endLine',
                filteredInfo.endLine,
                (value) => setFilteredInfo({ ...filteredInfo, endLine: value })
            )
        }
    );

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
            dataSource={findings}
            expandable={expandable}
            indentSize={0}
            rowClassName="ort-package"
            rowKey="key"
            size="small"
            locale={{
                emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="No findings"></Empty>
            }}
            pagination={{
                current: pagination.current,
                hideOnSinglePage: true,
                onChange: handlePaginationChange,
                pageSize: pagination.pageSize,
                pageSizeOptions: ['50', '100', '250', '500', '1000', '5000'],
                position: 'bottom',
                showSizeChanger: true,
                showTotal: (total, range) => `${range[0]}-${range[1]} of ${total} results`
            }}
            onChange={handleTableChange}
        />
    );
};

export default PackageFindingsTable;
