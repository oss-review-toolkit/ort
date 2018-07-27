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
import { Icon, List, Steps } from 'antd';

const Step = Steps.Step;

// Generates the HTML to display the path(s) from root package to current package
export class PackagesTablePaths extends React.Component {
    constructor(props) {
        super();

        this.state = {
            expanded: props.expanded || false
        };

        if (props.data) {
            this.state = {
                ...this.state,
                data: props.data
            };
        }
    }

    onClick = () => {
        this.setState(prevState => ({ expanded: !prevState.expanded }));
    };

    render() {
        const { data: pkgObj, expanded } = this.state;

        if (Array.isArray(pkgObj.paths) && pkgObj.paths.length === 0) {
            return null;
        }

        if (!expanded) {
            return (
                <h4 onClick={this.onClick} className="ort-clickable">
                    <span>
                        Package Dependency Paths
                        {' '}
                    </span>
                    <Icon type="plus-square-o" />
                </h4>
            );
        }

        return (
            <div className="ort-package-deps-paths">
                <h4 onClick={this.onClick} className="ort-clickable">
                    Package Dependency Paths
                    {' '}
                    <Icon type="minus-square-o" />
                </h4>
                <List
                    grid={{
                        gutter: 16, xs: 1, sm: 2, md: 2, lg: 2, xl: 2, xxl: 2
                    }}
                    itemLayout="vertical"
                    size="small"
                    pagination={{
                        hideOnSinglePage: true,
                        pageSize: 2,
                        size: 'small'
                    }}
                    dataSource={pkgObj.paths}
                    renderItem={pathsItem => (
                        <List.Item>
                            <h5>
                                {pathsItem.scope}
                            </h5>
                            <Steps progressDot direction="vertical" size="small" current={pathsItem.path.length + 1}>
                                {pathsItem.path.map(item => <Step key={item} title={item} />)}
                                <Step key={pkgObj.id} title={pkgObj.id} />
                            </Steps>
                        </List.Item>
                    )}
                />
            </div>
        );
    }
}
