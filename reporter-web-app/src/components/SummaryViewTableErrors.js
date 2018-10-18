/*
 * Copyright (c) 2018 HERE Europe B.V.
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
const SummaryViewTableErrors = (props) => {
    const { data } = props;

    const renderErrorTable = (errors, pageSize) => (
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
            dataSource={errors}
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

    if (data.totalOpen !== 0) {
        return (
            <Tabs tabPosition="top">
                <TabPane
                    tab={(
                        <span>
                            Errors (
                            {data.totalOpen}
                            )
                        </span>
                    )}
                    key="1"
                >
                    {renderErrorTable(data.open, data.totalOpen)}
                </TabPane>
                <TabPane
                    tab={(
                        <span>
                            Addressed Errors (
                            {data.totalAddressed}
                            )
                        </span>
                    )}
                    key="2"
                >
                    {
                        renderErrorTable(
                            data.addressed,
                            data.totalAddressed
                        )
                    }
                </TabPane>
            </Tabs>
        );
    }

    // If return null to prevent React render error
    return null;
};

SummaryViewTableErrors.propTypes = {
    data: PropTypes.object.isRequired
};

export default SummaryViewTableErrors;
