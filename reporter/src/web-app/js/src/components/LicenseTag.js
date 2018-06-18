/*
 * Copyright (c) 2018 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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
import { Button, Modal, Tag, Tooltip } from 'antd';
import { config } from '../config';
import 'antd/dist/antd.css';

var correctToSPDX = require('spdx-correct'),
  licenses = {},
  licensesDataFromConfig = (config && config.hasOwnProperty('licenses')) ? config.licenses : {}, 
  licensesDataFromSPDX = require('spdx-license-list/full');

  // FIXME Remove window vars added for debugging purposes
  window.config = config;
  window.correctToSPDX = correctToSPDX;
  window.licenses = licenses;
  window.licensesDataFromConfig = licensesDataFromConfig
  window.licensesDataFromSPDX = licensesDataFromSPDX;

export class LicenseTag extends React.Component {
  constructor(props) {
    super(props);

    let licenseValueHandler = {
        get: function(target, name) {
          let licenseDataFromConfig,
              licenseDataFromSPDX,
              spdxId = target.spdxId;
      
          if (!target.hasOwnProperty(name)) {

            // Check if property name has been defined in Reporter's config
            licenseDataFromConfig = licensesDataFromConfig[spdxId] ? licensesDataFromConfig[spdxId] : licensesDataFromConfig[this.tagText];

            if (licenseDataFromConfig && licenseDataFromConfig.hasOwnProperty(name)) {
              return licenseDataFromConfig[name];
            }

            // If property name is not found in user specified config try to see if it's in SPDX licenses metadata
            licenseDataFromSPDX = licensesDataFromSPDX[spdxId];
        
            if (licenseDataFromSPDX && licenseDataFromSPDX.hasOwnProperty(name)) {
              return licenseDataFromSPDX[name];
            }

            return '';
          } else {
            return target[name];
          }
        }
      };

    this.tagText = props.text;

    /* Using ES6 Proxy to create Object that with default values for specific attribute coming for SPDX license list e.g.
     *
     * licenses[Apache-2.0] = {
     *  spdxId: Apache-2.0
     * }
     *
     * results in effectively
     * 
     * licenses[Apache-2.0] = {
     *   spdxId: Apache-2.0,
     *.  name: Apache License 2.0,
     *.  licenseText: "Apache License↵Version 2.0, January 2004↵http://www.apache.org/licenses/ …"
     *.  osiApproved: true,
     *.  url: "http://www.apache.org/licenses/LICENSE-2.0"
     *.}
     *
     * For more details on ES6 proxy please see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Proxy
     */
    if (!licenses[this.tagText]) {
      licenses[this.tagText] = new Proxy({
        spdxId: correctToSPDX(this.tagText),
      }, licenseValueHandler);
    }

    this.licenseColor = (licenses[this.tagText] && licenses[this.tagText].color) ? licenses[this.tagText].color : '';
    
    this.showLicenseInfoModal = () => {
      let license = licenses[props.text],
          licenseName,
          licenseText;

      if (!license.modal) {
        licenseName = license.name ? license.name : props.text;
        licenseText = licenses[props.text].licenseText.split('\n').map(
          function (elem) {
            // FIXME Throws "Warning: Encountered two children with the same key..."
            return React.createElement('p', {key: elem}, elem);
          }
        );
        
        license.modal = {
          title: licenseName,
          className: 'reporter-license-info',
          content: (
            <div>
              {licenseText}
            </div>
          ),
          onOk() {},
          okText: "Close",
          maskClosable: true,
          width: 800
        };
      }
      
      if (licenseName !== 'NONE') {
        Modal.info(license.modal);
      }
    }
  }

  render() {
    /*
      FIXME Use Button instead of Tag?
      <Button className="reporter-license-btn red" onClick={this.test}>{this.tagText}</Button>
    */
    return (
      <Tooltip placement="left" title={licenses[this.tagText].name}>
        <Tag className="reporter-license"
          color={this.licenseColor}
          checked="true"
          onClick={this.showLicenseInfoModal}>{this.tagText}</Tag>
      </Tooltip>
    );
  }
}