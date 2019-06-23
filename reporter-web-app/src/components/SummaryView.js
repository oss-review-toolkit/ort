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
import { connect } from 'react-redux';
import PropTypes from 'prop-types';
import {
    Col, Row, Tabs
} from 'antd';
import ErrorsTable from './ErrorsTable';
import SummaryViewLicenses from './SummaryViewLicenses';
import SummaryViewTimeline from './SummaryViewTimeline';
import PackageCollapse from './PackageCollapse';
import ViolationsTable from './ViolationsTable';
import {
    getOrtResult,
    getSummaryDeclaredLicenses,
    getSummaryDeclaredLicensesChart,
    getSummaryDeclaredLicensesFilter,
    getSummaryDeclaredLicensesTotal,
    getSummaryDetectedLicenses,
    getSummaryDetectedLicensesChart,
    getSummaryDetectedLicensesFilter,
    getSummaryDetectedLicensesTotal,
    getSummaryViewShouldComponentUpdate
} from '../reducers/selectors';
import store from '../store';

const { TabPane } = Tabs;

class SummaryView extends React.Component {
    shouldComponentUpdate() {
        const { shouldComponentUpdate } = this.props;
        return shouldComponentUpdate;
    }

    render() {
        const {
            licenses,
            webAppOrtResult
        } = this.props;

        return (
            <div className="ort-summary">
                <Row>
                    <Col span={22} offset={1}>
                        <SummaryViewTimeline webAppOrtResult={webAppOrtResult} />
                    </Col>
                </Row>
                <Row>
                    <Col span={22} offset={1}>
                        {(webAppOrtResult.hasErrors() || webAppOrtResult.hasViolations())
                            && (
                                <Tabs tabPosition="top" className="ort-summary-issues">
                                    { webAppOrtResult.hasViolations()
                                        && (
                                            <TabPane
                                                tab={(
                                                    <span>
                                                        Violations (
                                                        {webAppOrtResult.violations.length}
                                                        )
                                                    </span>
                                                )}
                                                key="1"
                                            >
                                                <ViolationsTable
                                                    expandedRowRender={
                                                        violation => (
                                                            <PackageCollapse
                                                                pkg={webAppOrtResult.getPackageById(violation.pkg)}
                                                                filterScanFindings={{
                                                                    type: ['LICENSE'],
                                                                    value: [violation.license]
                                                                }}
                                                                includeErrors
                                                                includeScanFindings
                                                                includeViolations={false}
                                                                showDetails={false}
                                                                showErrors={false}
                                                                showLicenses
                                                                showScanFindings
                                                                webAppOrtResult={webAppOrtResult}
                                                            />
                                                        )
                                                    }
                                                    showPackageColumn
                                                    violations={webAppOrtResult.violations}
                                                />
                                            </TabPane>
                                        )
                                    }
                                    { webAppOrtResult.hasErrors()
                                        && (
                                            <TabPane
                                                tab={(
                                                    <span>
                                                        Errors (
                                                        {webAppOrtResult.errors.length}
                                                        )
                                                    </span>
                                                )}
                                                key="2"
                                            >
                                                <ErrorsTable
                                                    expandedRowRender={
                                                        error => (
                                                            <PackageCollapse
                                                                pkg={webAppOrtResult.getPackageById(error.pkg)}
                                                                includeErrors={false}
                                                                includeScanFindings={false}
                                                                includeViolations={false}
                                                                showDetails
                                                                showLicenses={false}
                                                                showPaths
                                                                webAppOrtResult={webAppOrtResult}
                                                            />
                                                        )
                                                    }
                                                    errors={webAppOrtResult.errors}
                                                    showPackageColumn
                                                />
                                            </TabPane>
                                        )
                                    }
                                </Tabs>
                            )
                        }
                    </Col>
                </Row>
                <Row>
                    <Col span={22} offset={1}>
                        <SummaryViewLicenses
                            data={licenses}
                            onChangeDeclaredLicensesTable={
                                (pagination, filters, sorter, extra) => {
                                    store.dispatch({
                                        type: 'SUMMARY::CHANGE_DECLARED_LICENSES_TABLE',
                                        payload: {
                                            declaredChart: extra.currentDataSource,
                                            declaredFilter: {
                                                filteredInfo: filters,
                                                sortedInfo: sorter
                                            }
                                        }
                                    });
                                }
                            }
                            onChangeDetectedLicensesTable={
                                (pagination, filters, sorter, extra) => {
                                    store.dispatch({
                                        type: 'SUMMARY::CHANGE_DETECTED_LICENSES_TABLE',
                                        payload: {
                                            detectedChart: extra.currentDataSource,
                                            detectedFilter: {
                                                filteredInfo: filters,
                                                sortedInfo: sorter
                                            }
                                        }
                                    });
                                }
                            }
                        />
                    </Col>
                </Row>
            </div>
        );
    }
}

SummaryView.propTypes = {
    licenses: PropTypes.object.isRequired,
    shouldComponentUpdate: PropTypes.bool.isRequired,
    webAppOrtResult: PropTypes.object.isRequired
};

const mapStateToProps = state => ({
    licenses: {
        declared: getSummaryDeclaredLicenses(state),
        declaredChart: getSummaryDeclaredLicensesChart(state),
        declaredFilter: getSummaryDeclaredLicensesFilter(state),
        declaredTotal: getSummaryDeclaredLicensesTotal(state),
        detected: getSummaryDetectedLicenses(state),
        detectedChart: getSummaryDetectedLicensesChart(state),
        detectedFilter: getSummaryDetectedLicensesFilter(state),
        detectedTotal: getSummaryDetectedLicensesTotal(state)
    },
    shouldComponentUpdate: getSummaryViewShouldComponentUpdate(state),
    webAppOrtResult: getOrtResult(state)
});

export default connect(
    mapStateToProps,
    () => ({})
)(SummaryView);
