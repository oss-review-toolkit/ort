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

import {
    useMemo
} from 'react';

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

import IssuesTable from './IssuesTable';
import LicenseChart from './LicenseChart';
import LicenseStatsTable from './LicenseStatsTable';
import RuleViolationsTable from './RuleViolationsTable';
import VulnerabilitiesTable from './VulnerabilitiesTable';

const ResultsSummary = ({ webAppOrtResult }) => {
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

    const declaredLicensesProcessedAsNameValueColor = useMemo(
        () => {
            const licenses = [];
            const {
                statistics: {
                    licenses: {
                        declared
                    }
                }
            } = webAppOrtResult;

            declared.forEach((value, name) => {
                const license = webAppOrtResult.getLicenseByName(name);

                if (license) {
                    licenses.push({
                        name,
                        value,
                        color: license.color
                    });
                }
            });

            return licenses;
        },
        []
    );

    const detectedLicensesProcessedAsNameValueColor = useMemo(
        () => {
            const licenses = [];
            const {
                statistics: {
                    licenses: {
                        detected
                    }
                }
            } = webAppOrtResult;

            detected.forEach((value, name) => {
                const license = webAppOrtResult.getLicenseByName(name);

                if (license) {
                    licenses.push({
                        name,
                        value,
                        color: license.color
                    });
                }
            });

            return licenses;
        },
        []
    );

    return (
        <div className="ort-summary">
            <Row>
                <Col span={22} offset={1}>
                    <Timeline
                        className="ort-summary-timeline"
                        items={(
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
                                items={(() => {
                                    const tabItems = [
                                        {
                                            label: (
                                                <span>
                                                    <ExceptionOutlined style={{ marginRight: 5 }}/>
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
                                                    webAppRuleViolations={webAppOrtResult.ruleViolations}
                                                    showExcludesColumn={webAppOrtResult.hasExcludes()}
                                                />
                                            )
                                        },
                                        {
                                            label: (
                                                <span>
                                                    <BugOutlined style={{ marginRight: 5 }}/>
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
                                                    webAppOrtIssues={webAppOrtResult.issues}
                                                    showExcludesColumn={webAppOrtResult.hasExcludes()}
                                                />
                                            )
                                        },
                                        {
                                            label: (
                                                <span>
                                                    <SecurityScanOutlined style={{ marginRight: 5 }}/>
                                                    Vulnerabilities (
                                                    {vulnerabilities.length}
                                                    )
                                                </span>
                                            ),
                                            key: 'ort-summary-vulnerabilities-table',
                                            children: (
                                                <VulnerabilitiesTable
                                                    webAppVulnerabilitiess={webAppOrtResult.vulnerabilities}
                                                    showExcludesColumn={webAppOrtResult.hasExcludes()}
                                                />
                                            )
                                        }
                                    ];

                                    if (webAppOrtResult.hasDeclaredLicensesProcessed()) {
                                        tabItems.push({
                                            label: (
                                                <span>
                                                    <TagsOutlined style={{ marginRight: 5 }}/>
                                                    Declared Licenses ({declaredLicensesProcessed.length})
                                                </span>
                                            ),
                                            key: 'ort-summary-declared-licenses-table',
                                            children: (
                                                <Row>
                                                    <Col xs={24} sm={24} md={24} lg={24} xl={9}>
                                                        <LicenseStatsTable
                                                            emptyText="No declared licenses"
                                                            licenses={declaredLicensesProcessed}
                                                            licenseStats={declaredLicensesProcessedAsNameValueColor}
                                                        />
                                                    </Col>
                                                    <Col xs={24} sm={24} md={24} lg={24} xl={15}>
                                                        <LicenseChart
                                                            licenses={declaredLicensesProcessedAsNameValueColor}
                                                        />
                                                    </Col>
                                                </Row>
                                            )
                                        });
                                    }

                                    if (webAppOrtResult.hasDetectedLicensesProcessed()) {
                                        tabItems.push({
                                            label: (
                                                <span>
                                                    <CodeOutlined style={{ marginRight: 5 }}/>
                                                    Detected Licenses ({detectedLicensesProcessed.length})
                                                </span>
                                            ),
                                            key: 'ort-summary-detected-licenses-table',
                                            children: (
                                                <Row>
                                                    <Col xs={24} sm={24} md={24} lg={24} xl={9}>
                                                        <LicenseStatsTable
                                                            emptyText="No detected licenses"
                                                            licenses={detectedLicensesProcessed}
                                                            licenseStats={detectedLicensesProcessedAsNameValueColor}
                                                        />
                                                    </Col>
                                                    <Col xs={24} sm={24} md={24} lg={24} xl={15}>
                                                        <LicenseChart
                                                            licenses={detectedLicensesProcessedAsNameValueColor}
                                                        />
                                                    </Col>
                                                </Row>
                                            )
                                        });
                                    }

                                    return tabItems;
                                })()}
                            />
                        </Col>
                    </Row>
                )
            }
        </div>
    )
};

export default ResultsSummary;
