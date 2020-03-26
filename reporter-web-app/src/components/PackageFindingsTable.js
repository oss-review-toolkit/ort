/*
 * Copyright (C) 2017-2020 HERE Europe B.V.
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
import { CopyrightOutlined, FileTextOutlined } from '@ant-design/icons';

// Generates the HTML to display scanFindings as a Table
class PackageFindingsTable extends React.Component {
    constructor(props) {
        super(props);

        const {
            webAppPackage
        } = props;

        this.state = {
            webAppPackage
        };
    }

    render() {
        const {
            webAppPackage
        } = this.state;
        const { findings } = webAppPackage;

        const columns = [
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
                title: 'Value',
                dataIndex: 'value',
                filters: (
                    () => Array.from(webAppPackage.detectedLicenses)
                        .map((license) => ({ text: license, value: license }))
                        .sort(
                            (a, b) => {
                                if (a.text.localeCompare(b.text) < 0) {
                                    return -1;
                                }
                                if (a.text.localeCompare(b.text) > 0) {
                                    return 1;
                                }

                                return 0;
                            }
                        )
                )(),
                onFilter: (value, row) => value === row.value,
                key: 'value',
                render: (value) => (
                    <span className="ort-word-break-wrap">
                        {value}
                    </span>
                )
            },
            {
                title: 'Path',
                dataIndex: 'path',
                defaultSortOrder: 'ascend',
                key: 'path',
                sorter: (a, b) => {
                    const idA = a.path.length;
                    const idB = b.path.length;
                    if (idA < idB) {
                        return -1;
                    }
                    if (idA > idB) {
                        return 1;
                    }

                    return 0;
                },
                render: (path) => (
                    <div className="ort-word-break-wrap">
                        {path}
                    </div>
                ),
                style: { minWidth: '50%' }
            },
            {
                title: 'Start',
                dataIndex: 'startLine',
                key: 'startLine',
                align: 'center'
            },
            {
                title: 'End',
                dataIndex: 'endLine',
                key: 'endLine',
                align: 'center'
            }
        ];

        return (
            <Table
                columns={columns}
                dataSource={findings}
                locale={{
                    emptyText: 'No scan results'
                }}
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
    }
}

PackageFindingsTable.propTypes = {
    webAppPackage: PropTypes.object.isRequired
};

export default PackageFindingsTable;
