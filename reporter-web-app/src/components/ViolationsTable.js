/*
 * Copyright (C) 2019 HERE Europe B.V.
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

// Generates the HTML to display violations as a Table
const ViolationsTable = (props) => {
    const {
        expandedRowRender,
        showPackageColumn,
        violations
    } = props;

    // If return null to prevent React render error
    if (!violations) {
        return null;
    }

    const columns = [];
    const totalViolations = violations.length;

    columns.push({
        align: 'right',
        dataIndex: 'severity',
        filters: (() => [
            { text: 'Errors', value: 'ERROR' },
            { text: 'Warnings', value: 'WARNING' },
            { text: 'Resolved', value: 'RESOLVED' }
        ])(),
        onFilter: (value, record) => record.severity.includes(value),
        render: (text, row) => (
            <span>
                {
                    row.severity === 'ERROR'
                    && (
                        <Icon
                            type="exclamation-circle"
                            className="ort-error"
                        />
                    )
                }
                {
                    row.severity === 'WARNING'
                    && (
                        <Icon
                            type="warning"
                            className="ort-warning"
                        />
                    )
                }
                {
                    row.severity === 'HINT'
                    && (
                        <Icon
                            type="info-circle"
                            className="ort-hint"
                        />
                    )
                }
            </span>
        ),
        width: '1em'
    });

    if (showPackageColumn) {
        columns.push({
            title: 'Package',
            dataIndex: 'pkg',
            key: 'pkg',
            render: text => (
                <span
                    className="ort-package-id ort-word-break-wrap"
                >
                    {text}
                </span>
            ),
            width: '25%'
        });
    }

    columns.push({
        title: 'Rule',
        dataIndex: 'rule',
        key: 'rule',
        render: text => (
            <span
                className="ort-package-id ort-word-break-wrap"
            >
                {text}
            </span>
        ),
        width: '20%'
    });

    columns.push({
        title: 'Message',
        dataIndex: 'message',
        key: 'message'
    });

    return (
        <Table
            columns={columns}
            dataSource={violations}
            expandedRowRender={expandedRowRender}
            locale={{
                emptyText: 'No violations'
            }}
            pagination={
                {
                    defaultPageSize: 25,
                    hideOnSinglePage: true,
                    pageSizeOptions: ['50', '100', '250', '500'],
                    position: 'bottom',
                    showQuickJumper: true,
                    showSizeChanger: true,
                    showTotal: (total, range) => `${range[0]}-${range[1]} of ${total} violations`
                }
            }
            rowKey="key"
            showHeader={totalViolations > 1}
            size="small"
        />
    );
};

ViolationsTable.propTypes = {
    expandedRowRender: PropTypes.func,
    violations: PropTypes.array.isRequired,
    showPackageColumn: PropTypes.bool
};

ViolationsTable.defaultProps = {
    expandedRowRender: null,
    showPackageColumn: false
};

export default ViolationsTable;
