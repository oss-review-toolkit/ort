/*
 * Copyright (C) 2021 HERE Europe B.V.
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

import {
    Button,
    Input,
    Space,
    Tooltip
} from 'antd';
import {
    SearchOutlined,
    ExclamationCircleOutlined,
    FileAddOutlined,
    FileExcelOutlined,
    InfoCircleOutlined,
    IssuesCloseOutlined,
    WarningOutlined
} from '@ant-design/icons';

/* eslint import/prefer-default-export: 0 */

// Implements a customized Ant Design table column search
const getColumnSearchProps = (dataIndex, filteredInfo, that) => ({
    filterDropdown: ({
        setSelectedKeys, selectedKeys, confirm, clearFilters
    }) => (
        <div style={{ padding: 8 }}>
            <Input
                ref={(node) => {
                    that.searchInput = node;
                }}
                placeholder="Search..."
                value={selectedKeys[0]}
                onChange={(e) => setSelectedKeys(e.target.value ? [e.target.value] : [])}
                onPressEnter={() => confirm()}
                style={{ width: 188, marginBottom: 8, display: 'block' }}
            />
            <Space>
                <Button
                    onClick={() => clearFilters()}
                    size="small"
                    style={{ width: 90 }}
                >
                    Reset
                </Button>
                <Button
                    icon={<SearchOutlined />}
                    onClick={() => confirm()}
                    size="small"
                    style={{ width: 90 }}
                    type="primary"
                >
                    Search
                </Button>
            </Space>
        </div>
    ),
    filterIcon: (filtered) => <SearchOutlined style={{ color: filtered ? '#1890ff' : undefined }} />,
    filteredValue: filteredInfo ? filteredInfo[dataIndex] : '',
    onFilter: (value, record) => {
        return (record[dataIndex]
            ? record[dataIndex].toString().toLowerCase().includes(value.toLowerCase())
            : false
        )
    },
    onFilterDropdownVisibleChange: (visible) => {
        if (visible) {
            setTimeout(() => that.searchInput.select());
        }
    }
});

const getDefaultViolationsTableColumns = (showExcludesColumn, filteredInfo, sortedInfo, node) => {
    let columns = [
        {
            align: 'center',
            dataIndex: 'severityIndex',
            key: 'severityIndex',
            filters: [
                {
                    text: 'Errors',
                    value: 0
                },
                {
                    text: 'Warnings',
                    value: 1
                },
                {
                    text: 'Hint',
                    value: 2
                },
                {
                    text: 'Resolved',
                    value: 3
                }
            ],
            filteredValue: filteredInfo.severityIndex || null,
            onFilter: (value, webAppRuleViolation) => webAppRuleViolation.severityIndex === Number(value),
            render: (text, webAppRuleViolation) => (
                webAppRuleViolation.isResolved
                    ? (
                        <Tooltip
                            placement="right"
                            title={Array.from(webAppRuleViolation.resolutionReasons).join(', ')}
                        >
                            <IssuesCloseOutlined
                                className="ort-ok"
                            />
                        </Tooltip>
                    ) : (
                        <span>
                            {
                                webAppRuleViolation.severity === 'ERROR'
                                && (
                                    <ExclamationCircleOutlined
                                        className="ort-error"
                                    />
                                )
                            }
                            {
                                webAppRuleViolation.severity === 'WARNING'
                                && (
                                    <WarningOutlined
                                        className="ort-warning"
                                    />
                                )
                            }
                            {
                                webAppRuleViolation.severity === 'HINT'
                                && (
                                    <InfoCircleOutlined
                                        className="ort-hint"
                                    />
                                )
                            }
                        </span>
                    )
            ),
            sorter: (a, b) => a.severityIndex - b.severityIndex,
            sortOrder: sortedInfo.field === 'severityIndex' && sortedInfo.order,
            width: '5em'
        }
    ];

    if (showExcludesColumn) {
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
            filteredValue: filteredInfo.excludes || null,
            key: 'excludes',
            onFilter: (value, webAppRuleViolation) => {
                if (!webAppRuleViolation.hasPackage()) return true;

                const { isExcluded } = webAppRuleViolation.package;

                return (isExcluded && value === 'excluded') || (!isExcluded && value === 'included');
            },
            render: (webAppRuleViolation) => {
                const webAppPackage = webAppRuleViolation.package;

                if (webAppPackage) {
                    return webAppPackage.isExcluded ? (
                        <span className="ort-excludes">
                            <Tooltip
                                placement="right"
                                title={Array.from(webAppPackage.excludeReasons).join(', ')}
                            >
                                <FileExcelOutlined className="ort-excluded" />
                            </Tooltip>
                        </span>
                    ) : (
                        <FileAddOutlined />
                    );
                }

                return null;
            },
            responsive: ['md'],
            width: '2em'
        });
    }

    columns.push(
        {
            dataIndex: 'packageName',
            ellipsis: true,
            key: 'packageName',
            responsive: ['md'],
            sorter: (a, b) => a.packageName.localeCompare(b.packageName),
            sortOrder: sortedInfo.field === 'packageName' && sortedInfo.order,
            title: 'Package',
            width: '25%',
            ...getColumnSearchProps('packageName', filteredInfo, node)
        },
        {
            dataIndex: 'rule',
            key: 'rule',
            responsive: ['md'],
            sorter: (a, b) => a.rule.localeCompare(b.rule),
            sortOrder: sortedInfo.field === 'rule' && sortedInfo.order,
            title: 'Rule',
            width: '25%',
            ellipsis: {
                showTitle: false,
            },
            render: rule => (
                <Tooltip placement="topLeft" title={rule}>
                    {rule}
                </Tooltip>
            ),
            ...getColumnSearchProps('rule', filteredInfo, node)
        },
        {
            dataIndex: 'message',
            key: 'message',
            textWrap: 'word-break',
            title: 'Message',
            ...getColumnSearchProps('message', filteredInfo, node)
        }
    );

    return columns
}

export { getColumnSearchProps, getDefaultViolationsTableColumns };
