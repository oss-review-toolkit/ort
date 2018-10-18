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
import { Tabs } from 'antd';
import PropTypes from 'prop-types';
import LicenseChart from './LicenseChart';

const { TabPane } = Tabs;

// Generates the HTML to display summary of licenses
const SummaryViewLicenseCharts = (props) => {
    const { data } = props;

    return (
        <Tabs tabPosition="top">
            <TabPane
                tab={(
                    <span>
                        Detected licenses (
                        {data.totalDetectedLicenses}
                        )
                    </span>
                )}
                key="1"
            >
                <LicenseChart
                    label="Detected Licenses"
                    licenses={data.detectedLicenses}
                    width={800}
                    height={500}
                />
            </TabPane>
            <TabPane
                tab={(
                    <span>
                        Declared Licenses (
                        {data.totalDeclaredLicenses}
                        )
                    </span>
                )}
                key="2"
            >
                <LicenseChart
                    label="Declared licenses"
                    licenses={data.declaredLicenses}
                    width={800}
                    height={500}
                />
            </TabPane>
        </Tabs>
    );
};

SummaryViewLicenseCharts.propTypes = {
    data: PropTypes.object.isRequired
};

export default SummaryViewLicenseCharts;
