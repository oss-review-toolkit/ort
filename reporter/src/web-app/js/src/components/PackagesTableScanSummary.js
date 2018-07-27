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
import { Icon, Tabs, Table } from 'antd';

const TabPane = Tabs.TabPane;

// Generates the HTML to display scan results for a package
export class PackagesTableScanSummary extends React.Component {
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

       if (Array.isArray(pkgObj.results) && pkgObj.results.length === 0) {
           return null;
       }

       if (!expanded) {
           return (
               <h4 onClick={this.onClick} className="ort-clickable">
                   <span>
                       Package Scan Summary
                       {' '}
                   </span>
                   <Icon type="plus-square-o" />
               </h4>
           );
       }

       return (
           <div className="ort-package-scan-summary">
               <h4 onClick={this.onClick} className="ort-clickable">
                   <span>
                       Package Scan Summary
                       {' '}
                   </span>
                   <Icon type="minus-square-o" />
               </h4>
               <Tabs tabPosition="top">
                   {pkgObj.results.map(scan => (
                       <TabPane
                           key={`tab-${scan.scanner.name}-${scan.scanner.version}`}
                           tab={`${scan.scanner.name} ${scan.scanner.version}`}
                       >
                           <Table
                               columns={[{
                                   title: 'license',
                                   dataIndex: 'license',
                                   render: (text, row) => (
                                       <div>
                                           <dl>
                                               <dt>
                                                   {row.license}
                                               </dt>
                                           </dl>
                                           <dl>
                                               {row.copyrights.map(holder => (
                                                   <dd key={`${row.license}-holder-${holder}`}>
                                                       {holder}
                                                   </dd>
                                               ))}
                                           </dl>
                                       </div>
                                   )
                               }]}
                               dataSource={scan.summary.license_findings}
                               locale={{
                                   emptyText: 'No findings'
                               }}
                               pagination={{
                                   hideOnSinglePage: true,
                                   pageSize: scan.summary.license_findings.length
                               }}
                               rowKey="license"
                               scroll={{
                                   y: 300
                               }}
                               showHeader={false}
                           />
                       </TabPane>
                   ))}
               </Tabs>
           </div>
       );
   }
}
