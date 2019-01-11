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
import {
    Col, Table, Tabs, Tag, Row
} from 'antd';
import PropTypes from 'prop-types';
import LicenseChart from './LicenseChart';

const { TabPane } = Tabs;

// Generates the HTML to display summary of licenses
const SummaryViewLicenses = (props) => {
    const {
        data,
        onChangeDeclaredLicensesTable,
        onChangeDetectedLicensesTable
    } = props;

    const columns = (licenses, filter) => {
        const { sortedInfo = {}, filteredInfo = {} } = filter;
        return [
            {
                title: 'License',
                dataIndex: 'name',
                filters: (() => licenses
                    .sort((a, b) => {
                        const nameA = a.name.toUpperCase();
                        const nameB = b.name.toUpperCase();
                        if (nameA < nameB) {
                            return -1;
                        }
                        if (nameA > nameB) {
                            return 1;
                        }

                        return 0;
                    })
                    .reduce((accumulator, obj) => {
                        accumulator.push({
                            text: obj.name,
                            value: obj.name
                        });

                        return accumulator;
                    }, [])
                )(),
                filteredValue: filteredInfo.name || null,
                onFilter: (license, record) => record.name === license,
                sorter: (a, b) => {
                    const nameA = a.name.toUpperCase();
                    const nameB = b.name.toUpperCase();
                    if (nameA < nameB) {
                        return -1;
                    }
                    if (nameA > nameB) {
                        return 1;
                    }

                    return 0;
                },
                sortOrder: sortedInfo.columnKey === 'name' && sortedInfo.order,
                key: 'name',
                render: (text, row) => (
                    <Tag color={row.color}>
                        {text}
                    </Tag>
                )
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
    };
    const pagination = {
        hideOnSinglePage: true,
        pageSize: 50,
        pageSizeOptions: ['50', '100', '250', '500'],
        position: 'bottom',
        showSizeChanger: true,
        showQuickJumper: true
    };

    return (
        <Tabs tabPosition="top" className="ort-summary-licenses">
            <TabPane
                tab={(
                    <span>
                        Declared Licenses (
                        {data.declaredTotal}
                        )
                    </span>
                )}
                key="ort-summary-declared-licenses-table"
            >
                <Row>
                    <Col xs={24} sm={24} md={24} lg={24} xl={9}>
                        <Table
                            bordered={false}
                            columns={columns(data.declared, data.declaredFilter)}
                            dataSource={data.declared}
                            locale={{
                                emptyText: 'No declared licenses'
                            }}
                            onChange={onChangeDeclaredLicensesTable}
                            pagination={pagination}
                            size="small"
                            rowClassName="ort-dectected-licenses-table-row"
                            rowKey="name"
                        />
                    </Col>
                    <Col xs={24} sm={24} md={24} lg={24} xl={15}>
                        <LicenseChart licenses={data.declaredChart} />
                    </Col>
                </Row>
            </TabPane>
            <TabPane
                tab={(
                    <span>
                        Detected licenses (
                        {data.detectedTotal}
                        )
                    </span>
                )}
                key="ort-summary-detected-licenses-table"
            >
                <Row>
                    <Col xs={24} sm={24} md={24} lg={24} xl={9}>
                        <Table
                            bordered={false}
                            columns={columns(data.detected, data.detectedFilter)}
                            dataSource={data.detected}
                            locale={{
                                emptyText: 'No detected licenses'
                            }}
                            onChange={onChangeDetectedLicensesTable}
                            pagination={pagination}
                            size="small"
                            rowClassName="ort-dectected-licenses-table-row"
                            rowKey="name"
                        />
                    </Col>
                    <Col xs={24} sm={24} md={24} lg={24} xl={15}>
                        <LicenseChart licenses={data.detectedChart} />
                    </Col>
                </Row>
            </TabPane>
        </Tabs>
    );
};

SummaryViewLicenses.propTypes = {
    data: PropTypes.object.isRequired,
    onChangeDeclaredLicensesTable: PropTypes.func.isRequired,
    onChangeDetectedLicensesTable: PropTypes.func.isRequired
};

export default SummaryViewLicenses;
