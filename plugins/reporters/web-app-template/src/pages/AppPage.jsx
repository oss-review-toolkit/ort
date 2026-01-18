/*
 * Copyright (C) 2025 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
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

import { useState } from 'react';

import {
    ControlOutlined,
    PartitionOutlined,
    PieChartOutlined,
    TableOutlined
} from '@ant-design/icons';
import {
    Col,
    Layout,
    Row,
    Tabs
} from 'antd';

import AboutModal from '../components/AboutModal';
import ResultsSummary from '../components/ResultsSummary';
import ResultsTable from '../components/ResultsTable';
import ResultsTree from '../components/ResultsTree';

const { Content } = Layout;

const AppPage = ({ webAppOrtResult }) => {
    const [activeTab, setActiveTab] = useState('ort-tabs-summary');
    const [isAboutModalVisible, setIsAboutModalVisible] = useState(false);

    /* === ResultsTable state handling === */

    // State variable for displaying ResultsTable in various pages
    const [resultsTablePagination, setResultsTablePagination] = useState({ current: 1, pageSize: 100 });

    // State variable with default ResultsTable columns to show
    const columnsToShowDefault = [
        'declaredLicensesProcessed',
        'detectedLicensesProcessed',
        'levels',
        'scopeIndexes'
    ];

    // State variable with default for filtering the contents of ResultsTable columns
    const filteredInfoDefault = {
        excludes: [],
        id: [],
        homepageUrl: [],
        vcsProcessedUrl: []
    };

    // State variable for filtering ResultsTable columns
    const [resultsTableFilteredInfo, setResultsTableFilteredInfo] = useState(filteredInfoDefault);

    // State variable for sorting ResultsTable columns
    const [resultsTableSortedInfo, setResultsTableSortedInfo] = useState({});

    // State variable for showing or hiding ResultsTable columns
    const [resultsTableColumnsToShow, setResultsTableColumnsToShow] = useState(columnsToShowDefault);

    const handleAboutClick = (key) => {
        setIsAboutModalVisible(true);
    };

    const handleAboutModalCancel = () => {
        setIsAboutModalVisible(false);
    };

    const handleTabChange = (key) => {
        setActiveTab(key);
    };

    return (
        <Layout className="ort-app">
            <Content>
                <Row align="top" style={{ minHeight: '100vh' }}>
                    <Col span={24}>
                        {
                            !!isAboutModalVisible
                            && <AboutModal
                                webAppOrtResult={webAppOrtResult}
                                isModalVisible={isAboutModalVisible}
                                handleModalCancel={handleAboutModalCancel}
                            />
                        }
                        <Tabs
                            activeKey={activeTab}
                            animated={false}
                            items={[
                                {
                                    label: (
                                        <span>
                                            <PieChartOutlined style={{ marginRight: 5 }}/>
                                            Summary
                                        </span>
                                    ),
                                    key: 'ort-tabs-summary',
                                    children: (
                                        <ResultsSummary webAppOrtResult={ webAppOrtResult }/>
                                    )
                                },
                                {
                                    label: (
                                        <span>
                                            <TableOutlined style={{ marginRight: 5 }}/>
                                            Table
                                        </span>
                                    ),
                                    key: 'ort-tabs-table',
                                    children: (
                                        <ResultsTable
                                            columnsToShow={resultsTableColumnsToShow}
                                            filteredInfo={resultsTableFilteredInfo}
                                            pagination={resultsTablePagination}
                                            setColumnsToShow={setResultsTableColumnsToShow}
                                            setFilteredInfo={setResultsTableFilteredInfo}
                                            setPagination={setResultsTablePagination}
                                            sortedInfo={resultsTableSortedInfo}
                                            setSortedInfo={setResultsTableSortedInfo}
                                            webAppOrtResult={webAppOrtResult}
                                        />
                                    )
                                },
                                {
                                    label: (
                                        <span>
                                            <PartitionOutlined style={{ marginRight: 5 }}/>
                                            Tree
                                        </span>
                                    ),
                                    key: 'ort-tabs-tree',
                                    children: (
                                        <ResultsTree webAppOrtResult={ webAppOrtResult } />
                                    )
                                }
                            ]}
                            tabBarExtraContent={(
                                <ControlOutlined
                                    className="ort-control"
                                    onClick={handleAboutClick}
                                />
                            )}
                            onChange={handleTabChange}
                        />
                    </Col>
                </Row>
            </Content>
        </Layout>
    );
};

export default AppPage;
