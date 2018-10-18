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
import { Icon, Table, Tabs } from 'antd';
import PropTypes from 'prop-types';
import LicenseChart from './LicenseChart';

const { TabPane } = Tabs;

// Generates the HTML to display summary of licenses
const SummaryViewLicenses = (props) => {
    const { data } = props;
    const columns = [
        {
            title: 'License',
            dataIndex: 'name',
            sorter: (a, b) => {
                const nameA = a.name.toUpperCase(); // ignore upper and lowercase
                const nameB = b.name.toUpperCase(); // ignore upper and lowercase
                if (nameA < nameB) {
                    return -1;
                }
                if (nameA > nameB) {
                    return 1;
                }

                // names must be equal
                return 0;
            },
            key: 'name'
        }, {
            title: 'Packages',
            dataIndex: 'value',
            sorter: (a, b) => a.value - b.value,
            key: 'value',
            width: 150
        }
    ];
    const pagination = {
        hideOnSinglePage: true,
        pageSize: 50,
        pageSizeOptions: ['50', '100', '250', '500'],
        position: 'bottom',
        showSizeChanger: true,
        showQuickJumper: true
    };
    const sortReverseLicensesByName = (licenses) => {
        const arr = Object.keys(licenses).sort().reduce((accumulator, key) => {
            accumulator.push(licenses[key]);

            return accumulator;
        }, []).reverse();

        return arr;
    };
    const sortReverseLicensesByValue = (licenses) => {
        const arr = Object.values(licenses);

        return arr.sort((a, b) => a.value - b.value).reverse();
    };

    return (
        <Tabs tabPosition="top">
            <TabPane
                tab={(
                    <span>
                        Detected licenses (
                        {data.totalDetectedLicenses}
                        )
                    </span>
                )}
                key="1"
            >
                <Tabs tabPosition="left">
                    <TabPane
                        tab={<Icon type="pie-chart" theme="outlined" />}
                        key="ort-dectected-licenses-pie-chart"
                    >
                        <LicenseChart
                            label="Detected Licenses"
                            licenses={sortReverseLicensesByName(data.detectedLicenses)}
                            width={800}
                            height={500}
                        />
                    </TabPane>
                    <TabPane
                        tab={<Icon type="table" theme="outlined" />}
                        key="ort-dectected-licenses-table"
                    >
                        <Table
                            bordered={false}
                            columns={columns}
                            dataSource={sortReverseLicensesByValue(data.detectedLicenses)}
                            locale={{
                                emptyText: 'No detected licenses'
                            }}
                            pagination={pagination}
                            size="small"
                            rowClassName="ort-dectected-licenses-table-row"
                            rowKey="name"
                        />
                    </TabPane>
                </Tabs>
            </TabPane>
            <TabPane
                tab={(
                    <span>
                        Declared Licenses (
                        {data.totalDeclaredLicenses}
                        )
                    </span>
                )}
                key="2"
            >
                <Tabs tabPosition="left">
                    <TabPane
                        tab={<Icon type="pie-chart" theme="outlined" />}
                        key="ort-declared-licenses-pie-chart"
                    >
                        <LicenseChart
                            label="Declared licenses"
                            licenses={sortReverseLicensesByName(data.declaredLicenses)}
                            width={800}
                            height={500}
                        />
                    </TabPane>
                    <TabPane
                        tab={<Icon type="table" theme="outlined" />}
                        key="ort-declared-licenses-table"
                    >
                        <Table
                            bordered={false}
                            columns={columns}
                            dataSource={sortReverseLicensesByValue(data.declaredLicenses)}
                            locale={{
                                emptyText: 'No declared licenses'
                            }}
                            pagination={pagination}
                            size="small"
                            rowClassName="ort-dectected-licenses-table-row"
                            rowKey="name"
                        />
                    </TabPane>
                </Tabs>
            </TabPane>
        </Tabs>
    );
};

SummaryViewLicenses.propTypes = {
    data: PropTypes.object.isRequired
};

export default SummaryViewLicenses;
