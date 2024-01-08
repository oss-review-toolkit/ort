/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import React from 'react';

import {
    BugOutlined,
    CodeOutlined,
    CheckCircleOutlined,
    ExclamationCircleOutlined,
    ExceptionOutlined,
    SecurityScanOutlined,
    TagsOutlined
} from '@ant-design/icons';
import {
    Col, Row, Tabs, Timeline
} from 'antd';
import PropTypes from 'prop-types';

import { connect } from 'react-redux';

import {
    getOrtResult,
    getSummaryCharts,
    getSummaryColumns,
    getSummaryStats,
    getSummaryViewShouldComponentUpdate
} from '../reducers/selectors';
import store from '../store';

import IssuesTable from './IssuesTable';
import LicenseChart from './LicenseChart';
import LicenseStatsTable from './LicenseStatsTable';
import RuleViolationsTable from './RuleViolationsTable';
import VulnerabilitiesTable from './VulnerabilitiesTable';

class SummaryView extends React.Component {
    static onChangeDeclaredLicensesTable(pagination, filters, sorter, extra) {
        store.dispatch({
            type: 'SUMMARY::CHANGE_DECLARED_LICENSES_TABLE',
            payload: {
                charts: {
                    declaredLicenses: extra.currentDataSource
                },
                columns: {
                    declaredLicenses: {
                        filteredInfo: filters,
                        sortedInfo: sorter
                    }
                }
            }
        });
    }

    static onChangeDetectedLicensesTable(pagination, filters, sorter, extra) {
        store.dispatch({
            type: 'SUMMARY::CHANGE_DETECTED_LICENSES_TABLE',
            payload: {
                charts: {
                    detectedLicensesProcessed: extra.currentDataSource
                },
                columns: {
                    detectedLicensesProcessed: {
                        filteredInfo: filters,
                        sortedInfo: sorter
                    }
                }
            }
        });
    }

    static onChangeIssuesTable(pagination, filters, sorter) {
        store.dispatch({
            type: 'SUMMARY::CHANGE_ISSUES_TABLE',
            payload: {
                columns: {
                    issues: {
                        filteredInfo: filters,
                        sortedInfo: sorter
                    }
                }
            }
        });
    }

    static onChangeRuleViolationsTable(pagination, filters, sorter) {
        store.dispatch({
            type: 'SUMMARY::CHANGE_RULE_VIOLATIONS_TABLE',
            payload: {
                columns: {
                    ruleViolations: {
                        filteredInfo: filters,
                        sortedInfo: sorter
                    }
                }
            }
        });
    }

    static onChangeVulnerabilitiesTable(pagination, filters, sorter) {
        store.dispatch({
            type: 'SUMMARY::CHANGE_VULNERABILITIES_TABLE',
            payload: {
                columns: {
                    vulnerabilities: {
                        filteredInfo: filters,
                        sortedInfo: sorter
                    }
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
            charts,
            columns,
            stats,
            webAppOrtResult
        } = this.props;

        const {
            declaredLicensesProcessed,
            detectedLicensesProcessed,
            issues,
            levels,
            packages,
            projects,
            repository: { vcsProcessed },
            ruleViolations,
            scopes,
            statistics,
            vulnerabilities
        } = webAppOrtResult;

        const {
            openIssues: {
                errors: unresolvedIssues
            },
            openRuleViolations: {
                errors: unresolvedRuleViolations
            }
        } = statistics;
        const { revision, type, url } = vcsProcessed;

        const hasUnresolvedIssues = unresolvedIssues > 0;
        const hasUnresolvedRuleViolations = unresolvedRuleViolations > 0;

        return (
            <div className="ort-summary">
                <Row>
                    <Col span={22} offset={1}>
                        <Timeline
                            className="ort-summary-timeline"
                            items={ (
                                () => {
                                    const timelineItems = [
                                        {
                                            children: (
                                                <span>
                                                    Scanned revision
                                                    {' '}
                                                    <b>
                                                        {revision}
                                                    </b>
                                                    {' '}
                                                    of
                                                    {' '}
                                                    {type}
                                                    {' '}
                                                    repository
                                                    {' '}
                                                    <b>
                                                        {url}
                                                    </b>
                                                </span>
                                            )
                                        },
                                        {
                                            children: (
                                                <span>
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
                                                        !!scopes && scopes.length > 0 && <span>
                                                                {' '}
                                                                and
                                                                {' '}
                                                                <b>
                                                                    {levels.length}
                                                                </b>
                                                                {' '}
                                                                dependency levels
                                                            </span>
                                                    }
                                                </span>
                                            )
                                        }
                                    ];

                                    if (declaredLicensesProcessed.length !== 0
                                        && detectedLicensesProcessed.length === 0) {
                                        timelineItems.push({
                                            children: (
                                                    <span>
                                                        {' '}
                                                        Detected
                                                        {' '}
                                                        <b>
                                                            {declaredLicensesProcessed.length}
                                                        </b>
                                                        {' '}
                                                        declared licenses
                                                    </span>
                                            )
                                        });
                                    } else if (declaredLicensesProcessed.length === 0
                                        && detectedLicensesProcessed.length !== 0) {
                                        timelineItems.push({
                                            children: (
                                                <span>
                                                    {' '}
                                                    Detected
                                                    {' '}
                                                    <b>
                                                        {detectedLicensesProcessed.length}
                                                    </b>
                                                    {' '}
                                                    licenses
                                                </span>
                                            )
                                        });
                                    } else if (declaredLicensesProcessed.length !== 0
                                        && detectedLicensesProcessed.length !== 0) {
                                        timelineItems.push({
                                            children: (
                                                <span>
                                                    Detected
                                                    {' '}
                                                    <b>
                                                        {detectedLicensesProcessed.length}
                                                    </b>
                                                    {' '}
                                                    licenses and
                                                    {' '}
                                                    <b>
                                                        {declaredLicensesProcessed.length}
                                                    </b>
                                                    {' '}
                                                    declared licenses
                                                </span>
                                            )
                                        });
                                    }

                                    timelineItems.push({
                                        dot: (hasUnresolvedIssues || hasUnresolvedRuleViolations)
                                            ? (<ExclamationCircleOutlined style={{ fontSize: 16 }} />)
                                            : (<CheckCircleOutlined style={{ fontSize: 16 }} />),
                                        children: (
                                            <span>
                                                {
                                                    !!hasUnresolvedIssues
                                                    && !hasUnresolvedRuleViolations
                                                    && <span className="ort-error">
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
                                                }
                                                {
                                                    !hasUnresolvedIssues
                                                    && !!hasUnresolvedRuleViolations
                                                    && <span className="ort-error">
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
                                                }
                                                {
                                                    !!hasUnresolvedIssues
                                                    && !!hasUnresolvedRuleViolations
                                                    && <span className="ort-error">
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
                                            </span>
                                        ),
                                        color: (hasUnresolvedIssues || hasUnresolvedRuleViolations) ? 'red' : 'green'
                                    });

                                    return timelineItems;
                                })()
                            }
                        />
                    </Col>
                </Row>
                {
                    (webAppOrtResult.hasIssues()
                    || webAppOrtResult.hasRuleViolations()
                    || webAppOrtResult.hasVulnerabilities()
                    || webAppOrtResult.hasDeclaredLicenses()
                    || webAppOrtResult.hasDetectedLicenses())
                    && (
                        <Row>
                            <Col span={22} offset={1}>
                                <Tabs
                                    className="ort-tabs-summary-overview"
                                    tabPosition="top"
                                    items={ (() => {
                                        const tabItems = [
                                            {
                                                label: (
                                                    <span>
                                                        <ExceptionOutlined />
                                                        Rule Violations (
                                                            {
                                                                ruleViolations.length !== unresolvedRuleViolations
                                                                && `${unresolvedRuleViolations}/`
                                                            }
                                                            {ruleViolations.length}
                                                        )
                                                    </span>
                                                ),
                                                key: 'ort-summary-rule-violations-table',
                                                children: (
                                                    <RuleViolationsTable
                                                        ruleViolations={webAppOrtResult.ruleViolations}
                                                        showExcludesColumn={webAppOrtResult.hasExcludes()}
                                                        state={columns.ruleViolations}
                                                        onChange={
                                                            SummaryView.onChangeRuleViolationsTable
                                                        }
                                                    />
                                                )
                                            }, {
                                                label: (
                                                    <span>
                                                        <BugOutlined />
                                                        Issues (
                                                            {
                                                                issues.length !== unresolvedIssues
                                                                && `${unresolvedIssues}/`
                                                            }
                                                            {issues.length}
                                                        )
                                                    </span>
                                                ),
                                                key: 'ort-summary-issues-table',
                                                children: (
                                                    <IssuesTable
                                                        issues={webAppOrtResult.issues}
                                                        showExcludesColumn={webAppOrtResult.hasExcludes()}
                                                        state={columns.issues}
                                                        onChange={
                                                            SummaryView.onChangeIssuesTable
                                                        }
                                                    />
                                                )
                                            }, {
                                                label: (
                                                    <span>
                                                        <SecurityScanOutlined />
                                                        Vulnerabilities (
                                                            {vulnerabilities.length}
                                                        )
                                                    </span>
                                                ),
                                                key: 'ort-summary-vulnerabilities-table',
                                                children: (
                                                    <VulnerabilitiesTable
                                                        vulnerabilities={webAppOrtResult.vulnerabilities}
                                                        showExcludesColumn={webAppOrtResult.hasExcludes()}
                                                        state={columns.vulnerabilities}
                                                        onChange={
                                                            SummaryView.onChangeVulnerabilitiesTable
                                                        }
                                                    />
                                                )
                                            }
                                        ];

                                        if (webAppOrtResult.hasDeclaredLicensesProcessed()) {
                                            tabItems.push({
                                                label: (
                                                    <span>
                                                        <TagsOutlined />
                                                        Declared Licenses (
                                                            {declaredLicensesProcessed.length}
                                                        )
                                                    </span>
                                                ),
                                                key: 'ort-summary-declared-licenses-table',
                                                children: (
                                                    <Row>
                                                        <Col xs={24} sm={24} md={24} lg={24} xl={9}>
                                                            <LicenseStatsTable
                                                                emptyText="No declared licenses"
                                                                filter={columns.declaredLicensesProcessed}
                                                                licenses={declaredLicensesProcessed}
                                                                licenseStats={stats.declaredLicensesProcessed}
                                                                onChange={
                                                                    SummaryView.onChangeDeclaredLicensesTable
                                                                }
                                                            />
                                                        </Col>
                                                        <Col xs={24} sm={24} md={24} lg={24} xl={15}>
                                                            <LicenseChart licenses={charts.declaredLicensesProcessed} />
                                                        </Col>
                                                    </Row>
                                                )
                                            });
                                        }

                                        if (webAppOrtResult.hasDetectedLicensesProcessed()) {
                                            tabItems.push({
                                                label: (
                                                    <span>
                                                        <CodeOutlined />
                                                        Detected Licenses (
                                                        {detectedLicensesProcessed.length}
                                                        )
                                                    </span>
                                                ),
                                                key: 'ort-summary-detected-licenses-table',
                                                children: (
                                                    <Row>
                                                        <Col xs={24} sm={24} md={24} lg={24} xl={9}>
                                                            <LicenseStatsTable
                                                                emptyText="No detected licenses"
                                                                filter={columns.detectedLicensesProcessed}
                                                                licenses={detectedLicensesProcessed}
                                                                licenseStats={stats.detectedLicensesProcessed}
                                                                onChange={SummaryView.onChangeDetectedLicensesTable}
                                                            />
                                                        </Col>
                                                        <Col xs={24} sm={24} md={24} lg={24} xl={15}>
                                                            <LicenseChart licenses={charts.detectedLicensesProcessed} />
                                                        </Col>
                                                    </Row>
                                                )
                                            });
                                        }

                                        return tabItems;
                                    })() }
                                />
                            </Col>
                        </Row>
                    )
                }
            </div>
        );
    }
}

SummaryView.propTypes = {
    charts: PropTypes.object.isRequired,
    columns: PropTypes.object.isRequired,
    shouldComponentUpdate: PropTypes.bool.isRequired,
    stats: PropTypes.object.isRequired,
    webAppOrtResult: PropTypes.object.isRequired
};

const mapStateToProps = (state) => ({
    charts: getSummaryCharts(state),
    columns: getSummaryColumns(state),
    shouldComponentUpdate: getSummaryViewShouldComponentUpdate(state),
    stats: getSummaryStats(state),
    webAppOrtResult: getOrtResult(state)
});

export default connect(
    mapStateToProps,
    () => ({})
)(SummaryView);
