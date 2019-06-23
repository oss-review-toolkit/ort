/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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
import { Icon, Table } from 'antd';

// Generates the HTML to display scanFindings as a Table
class ScanFindingsTable extends React.Component {
    constructor(props) {
        super(props);

        const {
            expandedRowRender,
            filter,
            pkg,
            webAppOrtResult
        } = props;
        const scanFindings = pkg.getScanFindings(webAppOrtResult);

        this.state = {
            expandedRowRender,
            filter,
            pkg,
            scanFindings,
            webAppOrtResult
        };
    }

    componentWillReceiveProps(nextProps) {
        const { pkg, webAppOrtResult } = nextProps;
        const scanFindings = pkg.getScanFindings(webAppOrtResult);
        this.setState(prevState => ({
            ...prevState,
            ...{
                pkg,
                scanFindings,
                webAppOrtResult
            }
        }));
    }

    render() {
        const {
            expandedRowRender,
            filter,
            pkg,
            scanFindings
        } = this.state;

        const columns = [
            {
                align: 'right',
                dataIndex: 'type',
                filters: (() => [
                    { text: 'Copyright', value: 'COPYRIGHT' },
                    { text: 'License', value: 'LICENSE' }
                ])(),
                filteredValue: filter.type,
                key: 'type',
                onFilter: (value, record) => record.type.includes(value),
                render: type => (
                    <span className="ort-scan-finding-type">
                        {type === 'LICENSE' && (<Icon type="file-text" />)}
                        {type === 'COPYRIGHT' && (<Icon type="copyright" />)}
                    </span>
                ),
                width: '1em'
            },
            {
                title: 'Value',
                dataIndex: 'value',
                filteredValue: filter.value,
                filters: (() => pkg.detectedLicenses.map(license => ({ text: license, value: license })))(),
                onFilter: (value, record) => record.license.includes(value),
                key: 'value',
                render: value => (
                    <div className="ort-word-break-wrap">
                        {value}
                    </div>
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
                render: path => (
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
                dataSource={scanFindings}
                expandedRowRender={expandedRowRender}
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

ScanFindingsTable.propTypes = {
    filter: PropTypes.object,
    expandedRowRender: PropTypes.func,
    pkg: PropTypes.object.isRequired,
    webAppOrtResult: PropTypes.object.isRequired
};

ScanFindingsTable.defaultProps = {
    expandedRowRender: null,
    filter: {
        type: [],
        value: []
    }
};

export default ScanFindingsTable;
