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
import {
    Icon, Table, Tabs
} from 'antd';
import { hashCode } from '../utils';

const { TabPane } = Tabs;

// Generates the HTML to display errors related to scanned project
const SummaryViewTableIssues = (props) => {
    const { data } = props;
    const { errors, violations } = data;

    const renderErrorTable = errorData => (
        <Table
            columns={[
                {
                    dataIndex: 'severity',
                    filters: (() => [
                        { text: 'Errors', value: 'ERROR' },
                        { text: 'Warning', value: 'WARNING' }
                    ])(),
                    onFilter: (value, record) => record.severity.includes(value),
                    render: (text, row) => (
                        row.severity === 'ERROR'
                            ? (
                                <Icon type="exclamation-circle" className="ort-error" />
                            ) : (
                                <Icon type="warning" className="ort-warning" />
                            )
                    )
                },
                {
                    title: 'Description',
                    dataIndex: 'id',
                    render: (text, row) => (
                        <div>
                            <dl>
                                <dt>
                                    {row.source}
                                </dt>
                                <dd>
                                    Dependency defined in
                                    {' '}
                                    {Array.from(row.files).join(', ')}
                                </dd>
                            </dl>
                            <dl>
                                <dd>
                                    {Array.from(row.message).map(message => (
                                        <p key={hashCode(message)}>
                                            {message}
                                        </p>
                                    ))}
                                </dd>
                            </dl>
                        </div>
                    )
                }
            ]}
            dataSource={errorData}
            locale={{
                emptyText: 'No errors'
            }}
            pagination={
                {
                    defaultPageSize: 25,
                    hideOnSinglePage: true,
                    pageSizeOptions: ['50', '100', '250', '500'],
                    position: 'bottom',
                    showQuickJumper: true,
                    showSizeChanger: true,
                    showTotal: (total, range) => `${range[0]}-${range[1]} of ${total} errors`
                }
            }
            rowKey="key"
            size="small"
        />
    );

    const renderViolationsTable = violationData => (
        <Table
            columns={[
                {
                    dataIndex: 'severity',
                    filters: (() => [
                        { text: 'Errors', value: 'ERROR' },
                        { text: 'Warning', value: 'WARNING' }
                    ])(),
                    onFilter: (value, record) => record.severity.includes(value),
                    render: (text, row) => (
                        row.severity === 'ERROR'
                            ? (
                                <Icon type="exclamation-circle" className="ort-error" />
                            ) : (
                                <Icon type="warning" className="ort-warning" />
                            )
                    )
                },
                {
                    title: 'Description',
                    dataIndex: 'id',
                    render: (text, row) => (
                        <div>
                            <dl>
                                <dt>
                                    {row.source}
                                </dt>
                                <dd>
                                    {row.message}
                                </dd>
                            </dl>
                        </div>
                    )
                }
            ]}
            dataSource={violationData}
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
            size="small"
        />
    );

    if (errors.totalOpen !== 0 || errors.totalResolved !== 0
            || violations.totalOpen !== 0 || violations.totalResolved !== 0) {
        return (
            <Tabs tabPosition="top" className="ort-summary-issues">
                <TabPane
                    tab={(
                        <span>
                            Violations (
                            {violations.openTotal}
                            )
                        </span>
                    )}
                    key="1"
                >
                    {renderViolationsTable(violations.open)}
                </TabPane>
                <TabPane
                    tab={(
                        <span>
                            Errors (
                            {errors.openTotal}
                            )
                        </span>
                    )}
                    key="2"
                >
                    {renderErrorTable(errors.open)}
                </TabPane>
                <TabPane
                    tab={(
                        <span>
                            Resolved Violations (
                            {violations.resolvedTotal}
                            )
                        </span>
                    )}
                    key="3"
                >
                    {
                        renderViolationsTable(violations.resolved)
                    }
                </TabPane>
                <TabPane
                    tab={(
                        <span>
                            Resolved Errors (
                            {errors.resolvedTotal}
                            )
                        </span>
                    )}
                    key="4"
                >
                    {
                        renderErrorTable(errors.resolved)
                    }
                </TabPane>
            </Tabs>
        );
    }

    // If return null to prevent React render error
    return null;
};

SummaryViewTableIssues.propTypes = {
    data: PropTypes.object.isRequired
};

export default SummaryViewTableIssues;
