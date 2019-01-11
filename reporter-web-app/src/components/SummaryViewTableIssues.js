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
    Table, Tabs
} from 'antd';
import { hashCode } from '../utils';

const { TabPane } = Tabs;

// Generates the HTML to display errors related to scanned project
const SummaryViewTableIssues = (props) => {
    const { data } = props;
    const { errors, violations } = data;

    const renderErrorTable = (errorData, pageSize) => (
        <Table
            columns={[{
                title: 'id',
                dataIndex: 'id',
                render: (text, row) => (
                    <div>
                        <dl>
                            <dt>
                                {row.id}
                            </dt>
                            <dd>
                                Dependency defined in
                                {' '}
                                {Array.from(row.files).join(', ')}
                            </dd>
                        </dl>
                        <dl>
                            <dd>
                                {Array.from(row.messages).map(message => (
                                    <p key={`ort-error-message-${hashCode(message)}`}>
                                        {message}
                                    </p>
                                ))}
                            </dd>
                        </dl>
                    </div>
                )
            }]}
            dataSource={errorData}
            locale={{
                emptyText: 'No errors'
            }}
            pagination={{
                hideOnSinglePage: true,
                pageSize
            }}
            rowKey="id"
            scroll={{
                y: 300
            }}
            showHeader={false}
        />);

    const renderViolationsTable = (violationData, pageSize) => (
        <Table
            columns={[{
                title: 'id',
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
            }]}
            dataSource={violationData}
            locale={{
                emptyText: 'No violations'
            }}
            pagination={{
                hideOnSinglePage: true,
                pageSize
            }}
            rowKey="source"
            scroll={{
                y: 300
            }}
            showHeader={false}
        />);

    if (errors.totalOpen !== 0 || errors.totalAddressed !== 0
            || violations.totalOpen !== 0 || violations.totalAddressed !== 0) {
        return (
            <Tabs tabPosition="top" className="ort-summary-issues">
                <TabPane
                    tab={(
                        <span>
                            Errors (
                            {errors.openTotal}
                            )
                        </span>
                    )}
                    key="1"
                >
                    {renderErrorTable(errors.open, errors.openTotal)}
                </TabPane>
                <TabPane
                    tab={(
                        <span>
                            Addressed Errors (
                            {errors.addressedTotal}
                            )
                        </span>
                    )}
                    key="2"
                >
                    {
                        renderErrorTable(errors.addressed, errors.addressedTotal)
                    }
                </TabPane>
                <TabPane
                    tab={(
                        <span>
                            Violations (
                            {violations.openTotal}
                            )
                        </span>
                    )}
                    key="3"
                >
                    {renderViolationsTable(violations.open, violations.openTotal)}
                </TabPane>
                <TabPane
                    tab={(
                        <span>
                            Addressed Violations (
                            {violations.addressedTotal}
                            )
                        </span>
                    )}
                    key="4"
                >
                    {
                        renderViolationsTable(violations.addressed, violations.addressedTotal)
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
