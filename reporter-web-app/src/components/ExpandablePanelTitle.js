/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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
import { Icon } from 'antd';

const expandablePanelTitle = (props) => {
    const {
        children,
        onToggle,
        show,
        showIcon,
        titleElem
    } = props;

    const titleBtn = (
        <button
            className="ort-btn-expand"
            onClick={onToggle}
            onKeyUp={onToggle}
            type="button"
        >
            {children}
            {showIcon && ' '}
            {showIcon && <Icon type={show ? 'down' : 'right'} />}
        </button>
    );

    switch (titleElem) {
    case 'h1':
        return (
            <h1>
                {titleBtn}
            </h1>
        );
    case 'h2':
        return (
            <h2>
                {titleBtn}
            </h2>
        );
    case 'h3':
        return (
            <h3>
                {titleBtn}
            </h3>
        );
    case 'h4':
        return (
            <h4>
                {titleBtn}
            </h4>
        );
    case 'h5':
        return (
            <h5>
                {titleBtn}
            </h5>
        );
    case 'h6':
        return (
            <h6>
                {titleBtn}
            </h6>
        );
    default:
        return (
            <div>
                {titleBtn}
            </div>
        );
    }
};

expandablePanelTitle.propTypes = {
    children: PropTypes.node.isRequired,
    onToggle: PropTypes.func,
    show: PropTypes.bool,
    showIcon: PropTypes.bool,
    titleElem: PropTypes.string
};

expandablePanelTitle.defaultProps = {
    onToggle: () => {},
    show: true,
    showIcon: true,
    titleElem: ''
};

export default expandablePanelTitle;
