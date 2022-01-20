/*
 * Copyright (C) 2019-2021 HERE Europe B.V.
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
import { Table } from 'antd';

import { getDefaultViolationsTableColumns } from './Shared';
import RuleViolationsSubTable from './RuleViolationsSubTable';
import RuleViolationCollapsable from './RuleViolationCollapsable'

// Generates the HTML to display violations as a Table
class RuleViolationsTable extends React.Component {
    constructor(props) {
        super(props)

        this.state = {
            mergedViolations: []
        }
    }

    checkDuplicates = (list, key) => {
        let hasDuplicates = false

        for (let entry of list) {
            if (entry === key) {
                hasDuplicates = true
                break
            }
        }

        return hasDuplicates
    }

    componentDidMount = () => {
        let rawList = [...this.props.ruleViolations]
        let ruleViolationsMap = new Map()

        for (let entry of rawList) {
            if (entry.package) {
                let mergedRuleViolation = ruleViolationsMap.get(entry.packageName) || entry
                let ruleNames = mergedRuleViolation.rule.split(', ')
                let newRuleName = mergedRuleViolation.rule

                if (!this.checkDuplicates(ruleNames, entry.rule))
                    newRuleName += `, ${entry.rule}`

                switch (true) {
                    // case: n doubled violation
                    case mergedRuleViolation && mergedRuleViolation.subList && mergedRuleViolation.subList.length !== 0:

                        // add the entry to the sublist
                        mergedRuleViolation.subList.push(entry)

                        // extend the rule name
                        mergedRuleViolation.rule = newRuleName

                        // update the message
                        mergedRuleViolation.message = `Expand the entry to see all ${mergedRuleViolation.subList.length} violations.`
                        break;

                    // case: first doubled violation
                    case mergedRuleViolation && !mergedRuleViolation.subList:
                        let violationsParent = {
                            key: `${mergedRuleViolation.packageName}-box`,
                            severity: mergedRuleViolation.severity,
                            severityIndex: mergedRuleViolation.severityIndex,
                            package: mergedRuleViolation.package,
                            packageName: `${mergedRuleViolation.packageName}`,
                            rule: newRuleName,
                            message: 'Expand the entry to see all 2 violations.',
                            subList: [mergedRuleViolation, entry]
                        }

                        mergedRuleViolation = violationsParent

                        break;

                    // case: first violation
                    default:
                        mergedRuleViolation = entry
                        break;
                }

                ruleViolationsMap.set(entry.package.id, mergedRuleViolation)
            }
        }

        this.setState((previousState, props) => ({
            mergedViolations: Array.from(ruleViolationsMap.values())
        }))
    }

    render() {
        const {
            onChange,
            ruleViolations,
            showExcludesColumn,
            state
        } = this.props;
        const {
            filteredInfo = {},
            sortedInfo = {}
        } = state;

        // If return null to prevent React render error
        if (!ruleViolations) {
            return null;
        }

        const columns = getDefaultViolationsTableColumns(showExcludesColumn, filteredInfo, sortedInfo, this)

        return (
            <Table
                className="ort-table-rule-violations"
                columns={columns}
                dataSource={this.state.mergedViolations}
                expandedRowRender={
                    (webAppRuleViolation) => {
                        const webAppPackage = webAppRuleViolation.package;
                        let defaultActiveKey = webAppRuleViolation.isResolved ? [1] : [0];
                        let Component = null

                        if (webAppRuleViolation.subList) {
                            Component = (
                                <RuleViolationsSubTable
                                    onChange={onChange}
                                    ruleViolations={webAppRuleViolation.subList}
                                    showExcludesColumn={showExcludesColumn}
                                    state={state}
                                    filteredInfo={filteredInfo}
                                    sortedInfo={sortedInfo}
                                />
                            )
                        } else {
                            Component = (
                                <RuleViolationCollapsable
                                    defaultActiveKey={defaultActiveKey}
                                    webAppRuleViolation={webAppRuleViolation}
                                    webAppPackage={webAppPackage}
                                />
                            )
                        }

                        return Component
                    }
                }
                locale={{
                    emptyText: 'No violations'
                }}
                onChange={onChange}
                pagination={
                    {
                        defaultPageSize: 25,
                        hideOnSinglePage: true,
                        pageSizeOptions: ['50', '100', '250', '500'],
                        position: 'bottom',
                        showQuickJumper: true,
                        showSizeChanger: true,
                        showTotal: (total, range) => `${range[0]}-${range[1]} of ${total} violations`
                    }
                }
                rowKey="key"
                size="small"
            />
        );
    };
}

RuleViolationsTable.propTypes = {
    onChange: PropTypes.func.isRequired,
    ruleViolations: PropTypes.array.isRequired,
    showExcludesColumn: PropTypes.bool,
    state: PropTypes.object.isRequired
};

RuleViolationsTable.defaultProps = {
    showExcludesColumn: false
};

export default RuleViolationsTable;
