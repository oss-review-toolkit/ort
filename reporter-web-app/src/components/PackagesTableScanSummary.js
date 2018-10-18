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
import { Tabs, Table } from 'antd';
import PropTypes from 'prop-types';
import ExpandablePanel from './ExpandablePanel';
import ExpandablePanelContent from './ExpandablePanelContent';
import ExpandablePanelTitle from './ExpandablePanelTitle';

const { TabPane } = Tabs;

// Generates the HTML to display scan results for a package
const PackagesTableScanSummary = (props) => {
    const { data } = props;
    const pkgObj = data;

    // Do not render anything if no scan results
    if (Array.isArray(pkgObj.results) && pkgObj.results.length === 0) {
        return null;
    }

    return (
        <ExpandablePanel key="ort-metadata-props">
            <ExpandablePanelTitle titleElem="h4">Package Scan Summary</ExpandablePanelTitle>
            <ExpandablePanelContent>
                <Tabs tabPosition="top">
                    {pkgObj.results.map(scan => (
                        <TabPane
                            key={`tab-${scan.scanner.name}-${scan.scanner.version}`}
                            tab={`${scan.scanner.name} ${scan.scanner.version}`}
                        >
                            <Table
                                columns={[{
                                    title: 'license',
                                    dataIndex: 'license',
                                    render: (text, row) => (
                                        <div>
                                            <dl>
                                                <dt>
                                                    {row.license}
                                                </dt>
                                            </dl>
                                            <dl>
                                                {row.copyrights.map(holder => (
                                                    <dd key={`${row.license}-holder-${holder}`}>
                                                        {holder}
                                                    </dd>
                                                ))}
                                            </dl>
                                        </div>
                                    )
                                }]}
                                dataSource={scan.summary.license_findings}
                                locale={{
                                    emptyText: 'No findings'
                                }}
                                pagination={{
                                    hideOnSinglePage: true,
                                    pageSize: scan.summary.license_findings.length
                                }}
                                rowKey="license"
                                scroll={{
                                    y: 300
                                }}
                                showHeader={false}
                            />
                        </TabPane>
                    ))}
                </Tabs>
            </ExpandablePanelContent>
        </ExpandablePanel>
    );
};

PackagesTableScanSummary.propTypes = {
    data: PropTypes.object.isRequired
};

export default PackagesTableScanSummary;
