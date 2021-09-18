/*
 * Copyright (C) 2017-2021 HERE Europe B.V.
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
import { Table, Tooltip } from 'antd';
import {
    CopyrightOutlined,
    FileAddOutlined,
    FileExcelOutlined,
    FileTextOutlined,
    MinusSquareOutlined,
    PlusSquareOutlined
} from '@ant-design/icons';
import PathExcludesTable from './PathExcludesTable';

// Generates the HTML to display scanFindings as a Table
const PackageFindingsTable = (props) => {
    const { webAppPackage } = props;
    const { findings } = webAppPackage;
    const columns = [];
    let expandable = null;

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
            onFilter: (value, webAppFinding) => {
                if (value === 'excluded') {
                    return webAppFinding.isExcluded;
                }

                if (value === 'included') {
                    return !webAppFinding.isExcluded;
                }

                return false;
            },
            render: (webAppFinding) => (
                webAppFinding.isExcluded ? (
                    <span className="ort-excludes">
                        <Tooltip
                            placement="right"
                            title={Array.from(webAppFinding.pathExcludeReasons).join(', ')}
                        >
                            <FileExcelOutlined className="ort-excluded" />
                        </Tooltip>
                    </span>
                ) : (
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
                    expanded ? (
                        <MinusSquareOutlined onClick={(e) => onExpand(record, e)} />
                    ) : (
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
            key: 'type',
            onFilter: (value, row) => value === row.type,
            render: (type) => (
                <span className="ort-scan-finding-type">
                    {type === 'LICENSE' && (<FileTextOutlined />)}
                    {type === 'COPYRIGHT' && (<CopyrightOutlined />)}
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
            onFilter: (value, row) => value === row.value,
            key: 'value',
            textWrap: 'word-break',
            title: 'Value'
        },
        {
            dataIndex: 'path',
            defaultSortOrder: 'ascend',
            key: 'path',
            sorter: (a, b) => a.path.length - b.path.length,
            textWrap: 'word-break',
            title: 'Path'
        },
        {
            align: 'center',
            dataIndex: 'startLine',
            key: 'startLine',
            responsive: ['md'],
            title: 'Start'
        },
        {
            align: 'center',
            dataIndex: 'endLine',
            key: 'endLine',
            responsive: ['md'],
            title: 'End'
        }
    );

    return (
        <Table
            columns={columns}
            dataSource={findings}
            expandable={expandable}
            pagination={
                {
                    defaultPageSize: 250,
                    hideOnSinglePage: true,
                    pageSizeOptions: ['50', '100', '250', '500'],
                    position: 'bottom',
                    showQuickJumper: true,
                    showSizeChanger: true,
                    showTotal: (total, range) => `${range[0]}-${range[1]} of ${total} results`
                }
            }
            rowKey="key"
            size="small"
        />
    );
};

PackageFindingsTable.propTypes = {
    webAppPackage: PropTypes.object.isRequired
};

export default PackageFindingsTable;
