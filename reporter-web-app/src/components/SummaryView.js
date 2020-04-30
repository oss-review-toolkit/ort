/*
 * Copyright (C) 2017-2020 HERE Europe B.V.
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
    Col, Row, Tabs, Timeline
} from 'antd';
import {
    CheckCircleOutlined, ExclamationCircleOutlined
} from '@ant-design/icons';
import IssuesTable from './IssuesTable';
import LicenseChart from './LicenseChart';
import LicenseStatsTable from './LicenseStatsTable';
import RuleViolationsTable from './RuleViolationsTable';
import {
    getOrtResult,
    getSummaryDeclaredLicenses,
    getSummaryDeclaredLicensesChart,
    getSummaryDeclaredLicensesFilter,
    getSummaryDetectedLicenses,
    getSummaryDetectedLicensesChart,
    getSummaryDetectedLicensesFilter,
    getSummaryIssuesFilter,
    getSummaryRuleViolationsFilter,
    getSummaryViewShouldComponentUpdate
} from '../reducers/selectors';
import store from '../store';

const { Item } = Timeline;
const { TabPane } = Tabs;

class SummaryView extends React.Component {
    static onChangeDeclaredLicensesTable(pagination, filters, sorter, extra) {
        store.dispatch({
            type: 'SUMMARY::CHANGE_DECLARED_LICENSES_TABLE',
            payload: {
                declaredLicensesChart: extra.currentDataSource,
                declaredLicensesFilter: {
                    filteredInfo: filters,
                    sortedInfo: sorter
                }
            }
        });
    }

    static onChangeDetectedLicensesTable(pagination, filters, sorter, extra) {
        store.dispatch({
            type: 'SUMMARY::CHANGE_DETECTED_LICENSES_TABLE',
            payload: {
                detectedLicensesChart: extra.currentDataSource,
                detectedLicensesFilter: {
                    filteredInfo: filters,
                    sortedInfo: sorter
                }
            }
        });
    }

    static onChangeIssuesTable(pagination, filters) {
        store.dispatch({
            type: 'SUMMARY::CHANGE_ISSUES_TABLE',
            payload: {
                issuesFilter: {
                    filteredInfo: filters
                }
            }
        });
    }

    static onChangeRuleViolationsTable(pagination, filters) {
        store.dispatch({
            type: 'SUMMARY::CHANGE_RULE_VIOLATIONS_TABLE',
            payload: {
                ruleViolationsFilter: {
                    filteredInfo: filters
                }
            }
        });
    }

    shouldComponentUpdate() {
        const { shouldComponentUpdate } = this.props;
        return shouldComponentUpdate;
    }

    render() {
        const {
            declaredLicensesChart,
            declaredLicensesFilter,
            declaredLicenseStats,
            detectedLicensesChart,
            detectedLicensesFilter,
            detectedLicensesStats,
            issuesFilter,
            ruleViolationsFilter,
            webAppOrtResult
        } = this.props;

        const {
            declaredLicenses,
            detectedLicenses,
            issues,
            levels,
            packages,
            projects,
            scopes,
            statistics,
            ruleViolations
        } = webAppOrtResult;

        const {
            openIssues: {
                errors: unresolvedIssues
            },
            openRuleViolations: {
                errors: unresolvedRuleViolations
            }
        } = statistics;

        const hasUnresolvedIssues = unresolvedIssues > 0;
        const hasUnresolvedRuleViolations = unresolvedRuleViolations > 0;

        return (
            <div className="ort-summary">
                <Row>
                    <Col span={22} offset={1}>
                        <Timeline className="ort-summary-timeline">
                            <Item>
                                Found
                                {' '}
                                <b>
                                    {projects.length}
                                </b>
                                {' '}
                                files defining
                                {' '}
                                <b>
                                    {packages.length}
                                </b>
                                {' '}
                                unique dependencies within
                                {' '}
                                <b>
                                    {scopes.length}
                                </b>
                                {' '}
                                scopes
                                {
                                    scopes && scopes.length > 0
                                    && (
                                        <span>
                                            {' '}
                                            and
                                            {' '}
                                            <b>
                                                {levels.length}
                                            </b>
                                            {' '}
                                            dependency levels
                                        </span>
                                    )
                                }
                            </Item>
                            <Item>
                                {
                                    detectedLicenses.length === 0
                                    && (
                                        <span>
                                            {' '}
                                            Detected
                                            {' '}
                                            <b>
                                                {declaredLicenses.length}
                                            </b>
                                            {' '}
                                            declared licenses
                                        </span>
                                    )
                                }
                                {
                                    detectedLicenses.length !== 0
                                    && (
                                        <span>
                                            Detected
                                            {' '}
                                            <b>
                                                {detectedLicenses.length}
                                            </b>
                                            {' '}
                                            licenses and
                                            {' '}
                                            <b>
                                                {declaredLicenses.length}
                                            </b>
                                            {' '}
                                            declared licenses
                                        </span>
                                    )
                                }
                            </Item>
                            <Item
                                dot={
                                    (hasUnresolvedIssues || hasUnresolvedRuleViolations)
                                        ? (<ExclamationCircleOutlined style={{ fontSize: 16 }} />)
                                        : (<CheckCircleOutlined style={{ fontSize: 16 }} />)
                                }
                                color={(hasUnresolvedIssues || hasUnresolvedRuleViolations) ? 'red' : 'green'}
                            >
                                {
                                    hasUnresolvedIssues && !hasUnresolvedRuleViolations
                                    && (
                                        <span className="ort-error">
                                            <b>
                                                Completed scan with
                                                {' '}
                                                {unresolvedIssues}
                                                {' '}
                                                unresolved issue
                                                {unresolvedIssues > 1 && 's'}
                                                {
                                                    webAppOrtResult.hasExcludes()
                                                    && (
                                                        <span>
                                                            {' '}
                                                            in non-excluded source code or dependencies
                                                        </span>
                                                    )
                                                }
                                            </b>
                                        </span>
                                    )
                                }
                                {
                                    !hasUnresolvedIssues && hasUnresolvedRuleViolations
                                    && (
                                        <span className="ort-error">
                                            <b>
                                                Completed scan with
                                                {' '}
                                                {unresolvedRuleViolations}
                                                {' '}
                                                unresolved policy violation
                                                {unresolvedRuleViolations > 1 && 's'}
                                                {
                                                    webAppOrtResult.hasExcludes()
                                                    && (
                                                        <span>
                                                            {' '}
                                                            in non-excluded source code or dependencies
                                                        </span>
                                                    )
                                                }
                                            </b>
                                        </span>
                                    )
                                }
                                {
                                    hasUnresolvedIssues && hasUnresolvedRuleViolations
                                    && (
                                        <span className="ort-error">
                                            <b>
                                                Completed scan with
                                                {' '}
                                                {unresolvedIssues}
                                                {' '}
                                                unresolved issue
                                                {unresolvedIssues > 1 && 's'}
                                                {' '}
                                                and
                                                {' '}
                                                {unresolvedRuleViolations}
                                                {' '}
                                                unresolved policy violation
                                                {unresolvedRuleViolations > 1 && 's'}
                                                {
                                                    webAppOrtResult.hasExcludes()
                                                    && (
                                                        <span>
                                                            {' '}
                                                            in non-excluded source code or dependencies
                                                        </span>
                                                    )
                                                }
                                            </b>
                                        </span>
                                    )
                                }
                                {
                                    !hasUnresolvedIssues && !hasUnresolvedRuleViolations
                                    && (
                                        <span className="ort-ok">
                                            <b>
                                                Completed scan successfully
                                            </b>
                                        </span>
                                    )
                                }
                            </Item>
                        </Timeline>
                    </Col>
                </Row>
                {
                    (webAppOrtResult.hasIssues()
                    || webAppOrtResult.hasRuleViolations()
                    || webAppOrtResult.hasDeclaredLicenses()
                    || webAppOrtResult.hasDetectedLicenses())
                    && (
                        <Row>
                            <Col span={22} offset={1}>
                                <Tabs tabPosition="top" className="ort-tabs-summary-overview">
                                    {
                                        webAppOrtResult.hasRuleViolations()
                                        && (
                                            <TabPane
                                                tab={(
                                                    <span>
                                                        Violations (
                                                        {ruleViolations.length}
                                                        )
                                                    </span>
                                                )}
                                                key="1"
                                            >
                                                <RuleViolationsTable
                                                    filter={ruleViolationsFilter}
                                                    onChange={
                                                        SummaryView.onChangeRuleViolationsTable
                                                    }
                                                    showExcludesColumn={webAppOrtResult.hasExcludes()}
                                                    ruleViolations={webAppOrtResult.ruleViolations}
                                                />
                                            </TabPane>
                                        )
                                    }
                                    {
                                        webAppOrtResult.hasIssues()
                                        && (
                                            <TabPane
                                                tab={(
                                                    <span>
                                                        Issues (
                                                        {issues.length}
                                                        )
                                                    </span>
                                                )}
                                                key="2"
                                            >
                                                <IssuesTable
                                                    filter={issuesFilter}
                                                    issues={webAppOrtResult.issues}
                                                    onChange={
                                                        SummaryView.onChangeIssuesTable
                                                    }
                                                    showExcludesColumn={webAppOrtResult.hasExcludes()}
                                                />
                                            </TabPane>
                                        )
                                    }
                                    {
                                        webAppOrtResult.hasDeclaredLicenses()
                                        && (
                                            <TabPane
                                                tab={(
                                                    <span>
                                                        Declared Licenses (
                                                        {declaredLicenses.length}
                                                        )
                                                    </span>
                                                )}
                                                key="ort-summary-declared-licenses-table"
                                            >
                                                <Row>
                                                    <Col xs={24} sm={24} md={24} lg={24} xl={9}>
                                                        <LicenseStatsTable
                                                            emptyText="No declared licenses"
                                                            filter={declaredLicensesFilter}
                                                            licenses={declaredLicenses}
                                                            licenseStats={declaredLicenseStats}
                                                            onChange={
                                                                SummaryView.onChangeDeclaredLicensesTable
                                                            }
                                                        />
                                                    </Col>
                                                    <Col xs={24} sm={24} md={24} lg={24} xl={15}>
                                                        <LicenseChart licenses={declaredLicensesChart} />
                                                    </Col>
                                                </Row>
                                            </TabPane>
                                        )
                                    }
                                    {
                                        webAppOrtResult.hasDetectedLicenses()
                                        && (
                                            <TabPane
                                                tab={(
                                                    <span>
                                                        Detected Licenses (
                                                        {detectedLicenses.length}
                                                        )
                                                    </span>
                                                )}
                                                key="ort-summary-detected-licenses-table"
                                            >
                                                <Row>
                                                    <Col xs={24} sm={24} md={24} lg={24} xl={9}>
                                                        <LicenseStatsTable
                                                            emptyText="No detected licenses"
                                                            filter={detectedLicensesFilter}
                                                            licenses={detectedLicenses}
                                                            licenseStats={detectedLicensesStats}
                                                            onChange={SummaryView.onChangeDetectedLicensesTable}
                                                        />
                                                    </Col>
                                                    <Col xs={24} sm={24} md={24} lg={24} xl={15}>
                                                        <LicenseChart licenses={detectedLicensesChart} />
                                                    </Col>
                                                </Row>
                                            </TabPane>
                                        )
                                    }
                                </Tabs>
                            </Col>
                        </Row>
                    )
                }
            </div>
        );
    }
}

SummaryView.propTypes = {
    declaredLicensesChart: PropTypes.array.isRequired,
    declaredLicensesFilter: PropTypes.object.isRequired,
    declaredLicenseStats: PropTypes.array.isRequired,
    detectedLicensesChart: PropTypes.array.isRequired,
    detectedLicensesFilter: PropTypes.object.isRequired,
    detectedLicensesStats: PropTypes.array.isRequired,
    issuesFilter: PropTypes.object.isRequired,
    shouldComponentUpdate: PropTypes.bool.isRequired,
    ruleViolationsFilter: PropTypes.object.isRequired,
    webAppOrtResult: PropTypes.object.isRequired
};

const mapStateToProps = (state) => ({
    declaredLicensesChart: getSummaryDeclaredLicensesChart(state),
    declaredLicensesFilter: getSummaryDeclaredLicensesFilter(state),
    declaredLicenseStats: getSummaryDeclaredLicenses(state),
    detectedLicensesChart: getSummaryDetectedLicensesChart(state),
    detectedLicensesFilter: getSummaryDetectedLicensesFilter(state),
    detectedLicensesStats: getSummaryDetectedLicenses(state),
    issuesFilter: getSummaryIssuesFilter(state),
    shouldComponentUpdate: getSummaryViewShouldComponentUpdate(state),
    ruleViolationsFilter: getSummaryRuleViolationsFilter(state),
    webAppOrtResult: getOrtResult(state)
});

export default connect(
    mapStateToProps,
    () => ({})
)(SummaryView);
