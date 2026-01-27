/*
 * Copyright (C) 2017 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

import { useMemo } from 'react';

import {
    AppstoreAddOutlined,
    BugOutlined,
    CodeOutlined,
    ExceptionOutlined,
    SecurityScanOutlined,
    TagsOutlined
} from '@ant-design/icons';
import { Col, Flex, Row, Steps, Tabs } from 'antd';

import IssuesTable from './IssuesTable';
import LicenseChart from './LicenseChart';
import LicenseStatsTable from './LicenseStatsTable';
import RuleViolationsTable from './RuleViolationsTable';
import VulnerabilitiesTable from './VulnerabilitiesTable';

const ResultsSummary = ({ webAppOrtResult, showInResultsTable }) => {
    const {
        declaredLicensesProcessed,
        detectedLicensesProcessed,
        effectiveLicenses,
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
        dependencyTree: { includedPackages, includedProjects, includedScopes },
        openIssues: {
            errors: openIssuesErrors,
            warnings: openIssuesWarnings,
            hints: openIssuesHints,
            severe: openIssuesSevere
        },
        openRuleViolations: {
            errors: openRuleViolationsErrors,
            warnings: openRuleViolationsWarnings,
            hints: openRuleViolationsHints,
            severe: openRuleViolationsSevere
        },
        repositoryConfiguration: { issueResolutions, ruleViolationResolutions, vulnerabilityResolutions }
    } = statistics;

    const { revision: vcsRevision = 'unknown', type: vcsType = 'unknown', url: vcsUrl = 'unknown' } = vcsProcessed;

    const declaredLicensesProcessedAsNameValueColor = useMemo(() => {
        const licenses = [];
        const {
            statistics: {
                licenses: { declared }
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
    }, []);

    const detectedLicensesProcessedAsNameValueColor = useMemo(() => {
        const licenses = [];
        const {
            statistics: {
                licenses: { detected }
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
    }, []);

    const effectiveLicensesProcessedAsNameValueColor = useMemo(() => {
        const licenses = [];
        const {
            statistics: {
                licenses: { effective }
            }
        } = webAppOrtResult;

        effective.forEach((value, name) => {
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
    }, []);

    const handleLicenseStatsTableClick = (license, type) => {
        showInResultsTable({ licenseName: license, licenseType: type });
    };

    const renderErrorsWarningsHintsSentence = (errors, warnings, hints) => {
        const formatCount = (count, type) => {
            return count === 0 ? `no ${type}s` : `${count} ${type}${count > 1 ? 's' : ''}`;
        };
        const parts = [formatCount(errors, 'error'), formatCount(warnings, 'warning'), formatCount(hints, 'hint')];

        const sentence = parts.length
            ? parts.join(', ').replace(/,(?=[^,]*$)/, ' and ') // Change last comma to 'and'
            : 'No issues';

        return sentence.charAt(0).toUpperCase() + sentence.slice(1);
    };

    const stepsProps = useMemo(() => {
        const summaryStatsItems = [];

        const sourceStats = (() => {
            const totalDependencies = packages.length - projects.length;

            return {
                title: 'Source code',
                status: projects.length === 0 ? 'error' : 'finish',
                content: (
                    <div>
                        {
                            projects.length === 0
                                ? (<div>
                                    <b>No projects found, action required!</b>
                                </div>)
                                : (<div>
                                    {projects.length} project{projects.length > 1 ? 's' : ''}
                                </div>)
                        }
                        {
                            totalDependencies > 0
                                && (<div>
                                    {totalDependencies} dependenc{totalDependencies > 1 ? 'ies' : 'y'}
                                </div>)
                        }
                        {
                            scopes.length > 0
                            && (<div>
                                {scopes.length} dependency scope{scopes.length > 1 ? 's' : ''}
                            </div>)
                        }
                    </div>
                )
            };
        })();
        summaryStatsItems.push(sourceStats);

        if (openIssuesErrors > 0 || openIssuesWarnings > 0 || openIssuesHints > 0) {
            const toolIssuesStats = (() => {
                const severeMessage =
                    openIssuesSevere === 1
                        ? `${openIssuesSevere} issue requires your attention!`
                        : `${openIssuesSevere} issues require your attention!`;

                return {
                    title: 'Tool Issues',
                    status: openIssuesSevere > 0 ? 'error' : 'finish',
                    content: (
                        <div>
                            <div>
                                {
                                    renderErrorsWarningsHintsSentence(
                                        openIssuesErrors,
                                        openIssuesWarnings,
                                        openIssuesHints)
                                }
                            </div>
                            {
                                issueResolutions > 0
                                && (issueResolutions === 1
                                    ? (<div>1 issue resolution in .ort.yml</div>)
                                    : (<div>{issueResolutions} issue resolutions in .ort.yml</div>))
                            }
                            {
                                openIssuesSevere > 0
                                && (<div>
                                    <b>{severeMessage}</b>
                                </div>)
                            }
                        </div>
                    )
                };
            })();

            summaryStatsItems.push(toolIssuesStats);
        }

        const licensesStats = {
            title: 'Licenses',
            content: (
                <div>
                    {
                        declaredLicensesProcessed.length === 0
                            ? (<div>
                            <b>No declared licenses</b>
                        </div>)
                            : (<div>
                            {declaredLicensesProcessed.length} declared license
                            {declaredLicensesProcessed.length > 1 ? 's' : ''}
                        </div>)
                    }
                    {
                        webAppOrtResult.hasScanResults()
                        && (declaredLicensesProcessed.length === 0
                            ? (<div>
                                <b>No detected licenses!</b>
                            </div>)
                            : (<div>
                                {detectedLicensesProcessed.length} detected license
                                {detectedLicensesProcessed.length > 1 ? 's' : ''}
                            </div>)
                        )
                    }
                </div>
            )
        };
        summaryStatsItems.push(licensesStats);

        const vulnerabiliesStats = {
            title: 'Vulnerabilities',
            content: (
                <div>
                    <div>0 critical or high</div>
                    <div>1 medium</div>
                    <div>0 low</div>
                </div>
            )
        };
        summaryStatsItems.push(vulnerabiliesStats);

        if (openRuleViolationsErrors > 0 || openRuleViolationsWarnings > 0 || openRuleViolationsHints > 0) {
            const policyRuleViolationsStats = (() => {
                const severeMessage =
                    openRuleViolationsSevere === 1
                        ? `${openRuleViolationsSevere} violation requires your attention!`
                        : `${openRuleViolationsSevere} violations require your attention!`;

                return {
                    title: 'Policy Rule Violations',
                    status: openRuleViolationsSevere > 0 ? 'error' : 'finish',
                    content: (
                        <div>
                            <div>
                                {
                                    renderErrorsWarningsHintsSentence(
                                        openRuleViolationsErrors,
                                        openRuleViolationsWarnings,
                                        openRuleViolationsHints)
                                }
                            </div>
                            {
                                ruleViolationResolutions > 0
                                && (ruleViolationResolutions === 1
                                    ? (
                                    <div>1 violation resolution in .ort.yml</div>)
                                    : (
                                    <div>{ruleViolationResolutions} violation resolutions in .ort.yml</div>))
                            }
                            {
                                openRuleViolationsSevere > 0
                                && (<div>
                                    <b>{severeMessage}</b>
                                </div>)
                            }
                        </div>
                    )
                };
            })();

            summaryStatsItems.push(policyRuleViolationsStats);
        } else {
            summaryStatsItems.push({
                title: 'Policy Rule Violations',
                content: (
                    <div>
                        {
                            ruleViolationResolutions > 0
                            && (ruleViolationResolutions === 1
                                ? (
                                <div>1 violation resolved via a resolution</div>)
                                : (<div>
                                    {ruleViolationResolutions} violations resolved via resolutions
                                </div>))
                        }
                        <div style={{ color: 'rgb(22, 119, 255)' }}>
                            You're all good to go!
                        </div>
                    </div>
                )
            });
        }

        const artifactsStats = {
            content: (
                <div>
                    {includedProjects === 0
                        ? (
                        <div>
                            <b>No projects included!</b>
                        </div>
                            )
                        : (
                        <div>
                            {includedProjects} project{includedProjects.length > 1 ? 's' : ''}
                        </div>
                            )}
                    {includedPackages !== 0 && (
                        <div>
                            {includedPackages} dependenc{includedProjects.length > 1 ? 'ies' : 'y'}
                        </div>
                    )}
                    {includedScopes.length !== 0 && (
                        <div>
                            {includedScopes.length} dependency scope{includedScopes.length > 1 ? 's' : ''}
                        </div>
                    )}
                    {effectiveLicenses.length !== 0 && (
                        <div>
                            {effectiveLicenses.length} effective license{effectiveLicenses.length > 1 ? 's' : ''}
                        </div>
                    )}
                </div>
            ),
            status: includedProjects === 0 ? 'error' : 'finish',
            title: 'Artifacts'
        };
        summaryStatsItems.push(artifactsStats);

        return {
            size: 'small',
            current: summaryStatsItems.length,
            titlePlacement: 'vertical',
            items: summaryStatsItems
        };
    });

    return (
        <div className="ort-summary">
            <Flex align="center" justify="center" style={{ paddingTop: 24, paddingBottom: 24 }}>
                <div>
                    Scanned revision <b>{vcsRevision}</b> of {vcsType} repository <b>{vcsUrl}</b>
                </div>
            </Flex>
            <Row>
                <Col span={22} style={{ paddingTop: 24, paddingBottom: 50 }}>
                    <Steps {...stepsProps} size="small" status="error" />
                </Col>
            </Row>
            {(webAppOrtResult.hasIssues()
                || webAppOrtResult.hasRuleViolations()
                || webAppOrtResult.hasVulnerabilities()
                || webAppOrtResult.hasDeclaredLicenses()
                || webAppOrtResult.hasDetectedLicenses()
                || webAppOrtResult.hasEffectiveLicenses()) && (
                <Row>
                    <Col span={22} offset={1}>
                        <Tabs
                            className="ort-tabs-summary-overview"
                            items={(() => {
                                const tabItems = [
                                    {
                                        label: (
                                            <span>
                                                <ExceptionOutlined style={{ marginRight: 5 }} />
                                                Rule Violations
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
                                                <BugOutlined style={{ marginRight: 5 }} />
                                                Tool Issues
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
                                                <SecurityScanOutlined style={{ marginRight: 5 }} />
                                                Vulnerabilities
                                            </span>
                                        ),
                                        key: 'ort-summary-vulnerabilities-table',
                                        children: (
                                            <VulnerabilitiesTable
                                                webAppVulnerabilities={webAppOrtResult.vulnerabilities}
                                                showExcludesColumn={webAppOrtResult.hasExcludes()}
                                            />
                                        )
                                    }
                                ];

                                if (webAppOrtResult.hasEffectiveLicenses()) {
                                    tabItems.push({
                                        label: (
                                            <span>
                                                <AppstoreAddOutlined style={{ marginRight: 5 }} />
                                                Effective Licenses
                                            </span>
                                        ),
                                        key: 'ort-summary-effective-licenses-table',
                                        children: (
                                            <Row>
                                                <Col xs={24} sm={24} md={24} lg={24} xl={9}>
                                                    <LicenseStatsTable
                                                        emptyText="No effective licenses"
                                                        licenses={effectiveLicenses}
                                                        licenseStats={effectiveLicensesProcessedAsNameValueColor}
                                                        handleClick={(license) =>
                                                            handleLicenseStatsTableClick(license, 'effective')
                                                        }
                                                    />
                                                </Col>
                                                <Col xs={24} sm={24} md={24} lg={24} xl={15}>
                                                    <LicenseChart
                                                        licenses={effectiveLicensesProcessedAsNameValueColor}
                                                    />
                                                </Col>
                                            </Row>
                                        )
                                    });
                                }

                                if (webAppOrtResult.hasDeclaredLicensesProcessed()) {
                                    tabItems.push({
                                        label: (
                                            <span>
                                                <TagsOutlined style={{ marginRight: 5 }} />
                                                Declared Licenses
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
                                                        handleClick={(license) =>
                                                            handleLicenseStatsTableClick(license, 'declared')
                                                        }
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
                                                <CodeOutlined style={{ marginRight: 5 }} />
                                                Detected Licenses
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
                                                        handleClick={(license) =>
                                                            handleLicenseStatsTableClick(license, 'detected')
                                                        }
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
            )}
        </div>
    );
};

export default ResultsSummary;
