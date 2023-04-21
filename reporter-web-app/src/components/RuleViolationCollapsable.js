/*
 * Copyright (C) 2022 Bosch.IO GmbH
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
import { Collapse } from 'antd';

import Markdown from 'markdown-to-jsx';
import PackageDetails from './PackageDetails';
import PackageLicenses from './PackageLicenses';
import PackagePaths from './PackagePaths';
import PackageFindingsTable from './PackageFindingsTable';
import PathExcludesTable from './PathExcludesTable';
import ResolutionTable from './ResolutionTable';
import ScopeExcludesTable from './ScopeExcludesTable';

const { Panel } = Collapse;

// Generates the HTML to display violations as a Table
class RuleViolationCollapsable extends React.Component {

    render() {
        const { defaultActiveKey, webAppRuleViolation, webAppPackage } = this.props

        return (
            < Collapse
                className="ort-package-collapse"
                bordered={false}
                defaultActiveKey={defaultActiveKey}
            >
                {
                    webAppRuleViolation.hasHowToFix()
                    && (
                        <Panel header="How to fix" key="0">
                            <Markdown
                                className="ort-how-to-fix"
                            >
                                {webAppRuleViolation.howToFix}
                            </Markdown>
                        </Panel>
                    )
                }
                {
                    webAppRuleViolation.isResolved
                    && (
                        <Panel header="Resolutions" key="1">
                            <ResolutionTable
                                resolutions={webAppRuleViolation.resolutions}
                            />
                        </Panel>
                    )
                }
                {
                    webAppRuleViolation.hasPackage()
                    && (
                        <Panel header="Details" key="2">
                            <PackageDetails webAppPackage={webAppPackage} />
                        </Panel>
                    )
                }
                {
                    webAppRuleViolation.hasPackage()
                    && webAppPackage.hasLicenses()
                    && (
                        <Panel header="Licenses" key="3">
                            <PackageLicenses webAppPackage={webAppPackage} />
                        </Panel>
                    )
                }
                {
                    webAppRuleViolation.hasPackage()
                    && webAppPackage.hasPaths()
                    && (
                        <Panel header="Paths" key="4">
                            <PackagePaths paths={webAppPackage.paths} />
                        </Panel>
                    )
                }
                {
                    webAppRuleViolation.hasPackage()
                    && webAppPackage.hasFindings()
                    && (
                        <Panel header="Scan Results" key="5">
                            <PackageFindingsTable
                                webAppPackage={webAppPackage}
                            />
                        </Panel>
                    )
                }
                {
                    webAppRuleViolation.hasPackage()
                    && webAppPackage.hasPathExcludes()
                    && (
                        <Panel header="Path Excludes" key="6">
                            <PathExcludesTable
                                excludes={webAppPackage.pathExcludes}
                            />
                        </Panel>
                    )
                }
                {
                    webAppRuleViolation.hasPackage()
                    && webAppPackage.hasScopeExcludes()
                    && (
                        <Panel header="Scope Excludes" key="7">
                            <ScopeExcludesTable
                                excludes={webAppPackage.scopeExcludes}
                            />
                        </Panel>
                    )
                }
            </Collapse >
        )
    }
}

RuleViolationCollapsable.propTypes = {
    defaultActiveKey: PropTypes.array.isRequired,
    webAppRuleViolation: PropTypes.object.isRequired,
    webAppPackage: PropTypes.object.isRequired
};

export default RuleViolationCollapsable;
