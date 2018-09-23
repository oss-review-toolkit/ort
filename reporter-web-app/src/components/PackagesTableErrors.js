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
import PropTypes from 'prop-types';

// Generates the HTML for packages errors in an expanded row of projectTable
class PackagesTableErrors extends React.Component {
    constructor(props) {
        super();

        this.state = {
            data: props.data,
            expanded: props.expanded
        };
    }

    onExpandedTitle = () => {
        this.setState(prevState => ({ expanded: !prevState.expanded }));
    };

    render() {
        const { data: pkgObj, expanded } = this.state;

        if (Array.isArray(pkgObj.errors) && pkgObj.errors.length === 0) {
            return null;
        }

        if (!expanded) {
            return (
                <h4>
                    <button
                        className="ort-btn-expand"
                        onClick={this.onExpandedTitle}
                        onKeyDown={this.onExpandedTitle}
                        type="button"
                    >
                        <span>
                            Package Errors
                            {' '}
                        </span>
                        <Icon type="right" />
                    </button>
                </h4>
            );
        }

        return (
            <div className="ort-package-errors">
                <h4>
                    <button
                        className="ort-btn-expand"
                        onClick={this.onExpandedTitle}
                        onKeyUp={this.onExpandedTitle}
                        type="button"
                    >
                        Package Errors
                        {' '}
                        <Icon type="down" />
                    </button>
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

PackagesTableErrors.propTypes = {
    data: PropTypes.object.isRequired,
    expanded: PropTypes.bool.isRequired
};

export default PackagesTableErrors;
