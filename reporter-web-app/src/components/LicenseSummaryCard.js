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
import {
    Badge, Card, Popover, List
} from 'antd';
import { LICENSES_PROVIDERS } from '../data/licenses';

export const LicenseSummaryCard = (props) => {
    const summary = props.summary;
    let licenseDataAttributed = false;
    const licenseAttributionText = (item) => {
        let provider;
        const providerName = item.provider;

        if (providerName && !licenseDataAttributed) {
            provider = LICENSES_PROVIDERS[providerName];

            licenseDataAttributed = true;

            return (
                <div className="ort-data-attribution">
                    <span>
                        Source:
                        <a
                            href={provider.packageHomePage}
                            rel="noopener noreferrer"
                            target="_blank"
                        >
                            {provider.packageName}
                        </a>
                    </span>
                    <p>
                        {provider.packageCopyrightText}
                        {' '}
                        {provider.packageLicenseDeclared}
                    </p>
                </div>
            );
        }

        return null;
    };
    const listItemStatus = (color) => {
        switch (color) {
        case 'green':
            return 'success';
        case 'orange':
            return 'warning';
        case 'red':
            return 'error';
        default:
            return 'default';
        }
    };
    let listItems;

    if (summary) {
        listItems = (items, itemColor) => items.map((item) => {
            const content = (
                <div style={{ width: 300 }}>
                    <p>
                        {item.description}
                    </p>
                </div>
            );

            return (
                <li key={item.tag}>
                    <Popover content={content} title={item.label} arrowPointAtCenter trigger="hover">
                        <Badge status={listItemStatus(itemColor)} text={item.tag} />
                    </Popover>
                </li>
            );
        });

        return (
            <List
                grid={
                    {
                        gutter: 16,
                        xs: 3,
                        sm: 3,
                        md: 3,
                        lg: 3,
                        xl: 3,
                        xxl: 3
                    }
                }
                size="large"
                dataSource={summary}
                renderItem={item => (
                    <List.Item>
                        <Card bordered={false} title={item.title}>
                            <ul>
                                {listItems(item.tags, item.color)}
                            </ul>
                        </Card>
                        {licenseAttributionText(item)}
                    </List.Item>
                )}
            />
        );
    }

    return null;
};
