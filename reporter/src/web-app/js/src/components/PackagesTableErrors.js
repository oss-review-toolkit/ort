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
import { Icon } from 'antd';

// Generates the HTML for packages errors in an expanded row of projectTable
export class PackagesTableErrors extends React.Component {
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

        if (Array.isArray(pkgObj.errors) && pkgObj.errors.length === 0) {
            return null;
        }

        if (!expanded) {
            return (
                <h4 onClick={this.onClick} className="ort-clickable">
                    <span>
                        Package Errors
                        {' '}
                    </span>
                    <Icon type="plus-square-o" />
                </h4>
            );
        }

        return (
            <div className="ort-package-erors">
                <h4 onClick={this.onClick} className="ort-clickable">
                    Package Errors
                    {' '}
                    <Icon type="minus-square-o" />
                </h4>
                {pkgObj.errors.map(error => (
                    <p key={`package-error-${error.code}`}>
                        {error.message}
                    </p>
                ))}
            </div>
        );
    }
}
