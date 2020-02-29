/*
 * Copyright (C) 2019-2020 HERE Europe B.V.
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
import { Descriptions, List, Steps } from 'antd';
import PropTypes from 'prop-types';

const { Item } = Descriptions;
const { Step } = Steps;

// Generates the HTML for packages issues
const PackagePaths = (props) => {
    const { paths } = props;
    let grid;

    // Change layout grid to use all available width for single path
    if (paths.length === 1) {
        grid = {
            gutter: 16,
            xs: 1,
            sm: 1,
            md: 1,
            lg: 1,
            xl: 1,
            xxl: 1
        };
    } else {
        grid = {
            gutter: 16,
            xs: 1,
            sm: 2,
            md: 2,
            lg: 2,
            xl: 2,
            xxl: 2
        };
    }

    return (
        <List
            grid={grid}
            itemLayout="vertical"
            size="small"
            pagination={{
                hideOnSinglePage: true,
                pageSize: 2,
                size: 'small'
            }}
            dataSource={paths}
            renderItem={
                (webAppPath) => {
                    const steps = [];
                    steps.push(
                        <Step
                            description={(
                                <Descriptions
                                    className="ort-package-path-description"
                                    column={1}
                                    size="small"
                                >
                                    <Item
                                        key="ort-package-path-definition-file-path"
                                        label="Defined in"
                                    >
                                        {webAppPath.project.definitionFilePath}
                                    </Item>
                                    <Item
                                        key="ort-package-path-scope"
                                        label="Scope"
                                    >
                                        {webAppPath.scopeName}
                                    </Item>
                                </Descriptions>
                            )}
                            key={webAppPath.projectName}
                            title={webAppPath.projectName}
                        />
                    );

                    webAppPath.path.forEach(
                        (pathWebAppPackage) => {
                            steps.push(<Step key={pathWebAppPackage.id} title={pathWebAppPackage.id} />);
                        }
                    );

                    steps.push(<Step key={webAppPath.packageName} title={webAppPath.packageName} />);

                    return (
                        <List.Item>
                            <Steps
                                className="ort-package-path"
                                current={steps.length - 1}
                                direction="vertical"
                                progressDot
                                size="small"
                            >
                                {steps}
                            </Steps>
                        </List.Item>
                    );
                }
            }
        />
    );
};

PackagePaths.propTypes = {
    paths: PropTypes.array.isRequired
};

export default PackagePaths;
