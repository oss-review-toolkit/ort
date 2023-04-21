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
import { Table } from 'antd';

import RuleViolationCollapsable from './RuleViolationCollapsable'
import { getDefaultViolationsTableColumns } from './Shared';

// Generates the HTML to display violations as a Table
class RuleViolationsSubTable extends React.Component {
    constructor(props) {
        super(props)

        this.state = {
            filteredInfo: this.props.filteredInfo,
            sortedInfo: this.props.sortedInfo
        }
    }

    onChange = (pagination, filters, sorter, extra) => {
        this.setState((previous, props) => ({
            filteredInfo: filters,
            sortedInfo: sorter
        }))
    }

    componentDidUpdate(prevProps) {
        let filterChange = this.props.filteredInfo !== prevProps.filteredInfo
        let sorterChange = this.props.sortedInfo !== prevProps.sortedInfo

        // Typical usage (don't forget to compare props):
        if (filterChange || sorterChange) {
            this.setState({
                filteredInfo: this.props.filteredInfo,
                sortedInfo: this.props.sortedInfo
            })
        }
    }

    render() {
        const {
            ruleViolations,
            showExcludesColumn,
        } = this.props;
        const {
            filteredInfo,
            sortedInfo
        } = this.state;

        const columns = getDefaultViolationsTableColumns(showExcludesColumn, filteredInfo, sortedInfo, this)

        return (
            <Table
                className="ort-table-rule-violations nested"
                columns={columns}
                dataSource={ruleViolations}
                onChange={this.onChange}
                locale={{
                    emptyText: 'No violations'
                }}
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
                expandedRowRender={(webAppRuleViolation) => {
                    let defaultActiveKey = [0];
                    const webAppPackage = webAppRuleViolation.package;

                    if (webAppRuleViolation.isResolved) {
                        defaultActiveKey = [1];
                    }

                    return (
                        <RuleViolationCollapsable defaultActiveKey={defaultActiveKey} webAppRuleViolation={webAppRuleViolation} webAppPackage={webAppPackage} />
                    )
                }}
            />
        );
    };
}

RuleViolationsSubTable.propTypes = {
    ruleViolations: PropTypes.array.isRequired,
    showExcludesColumn: PropTypes.bool,
    state: PropTypes.object.isRequired
};

RuleViolationsSubTable.defaultProps = {
    showExcludesColumn: false
};

export default RuleViolationsSubTable;
