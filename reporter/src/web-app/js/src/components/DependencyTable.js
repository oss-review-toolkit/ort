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
import { Alert, Table, Tag } from 'antd';
import 'antd/dist/antd.css';
import { removeDuplicatesInArray } from '../utils';
import { LicenseTag } from './LicenseTag';

export class DependencyTable extends React.Component {
  constructor(props) {
    super(props);

    const data = props.data,
      packages = data.packages[props.project],
      packagesDeclaredLicenses = removeDuplicatesInArray(Object.keys(data.declaredLicenses[props.project])),
      packagesDeclaredLicensesFilter = [],
      packagesDetectedLicenses = removeDuplicatesInArray(Object.keys(data.detectedLicenses[props.project])),
      packagesDetectedLicensesFilter = [],
      packagesLevels = data.levels[props.project],
      packagesLevelsFilter = [],
      statusFilter = [];

      this.packages = packages;

    // Do not display any table column filters if table is small
    if (packages.length > 2) {
      // Create select options to filter table by declared package licenses
      if (packagesDeclaredLicenses.length > 1) {
        packagesDeclaredLicenses.map(license => { 
          packagesDeclaredLicensesFilter.push({
            'text': license,
            'value': license,
          });
    
          return {};
        })
      }

      // Create select options to filter table by detected package licenses
      if (packagesDetectedLicenses.length > 1) {
        packagesDetectedLicenses.map(license => { 
          packagesDetectedLicensesFilter.push({
            'text': license,
            'value': license,
          });
    
          return {};
        })
      }

      // Create select options to filter table by detected package levels
      if (packagesLevels.length > 1) {
        for (let i = 0; i < packagesLevels.length; i++) {
          packagesLevelsFilter.push({
            'text': packagesLevels[i],
            'value': packagesLevels[i],
          });
        }
      }

      // Create select options to filter table by package status
      statusFilter.push(
        { 'text': 'Errors', 'value': 'errors'},
        { 'text': 'OK', 'value': 'ok'},
        { 'text': 'Updates', 'value': 'updates'},
        { 'text': 'Warnings', 'value': 'warnings'},
      );
    }

    function componentOk(component) {
      return component.errors && !component.errors.total
    }

    // Specifies table columns as per
    // https://ant.design/components/table/
    this.columns = [
      {
        title: 'Id', 
        dataIndex: 'id', 
        key: 'id',
        align: 'left',
        render: (text, row, index) => {
          return <span className="reporter-package-id">{text}</span>;
        },
        onFilter: (value, record) => record.id.indexOf(value) === 0,
        sorter: (a, b) => a.id.length - b.id.length,
      },
      {
        title: 'Scopes',
        dataIndex: 'scopes',
        align: 'left',
        render: (text, row, index) => {
          const listItems = row.scopes.map((scope) =>
            <li key={scope}>{scope}</li>
          );

          return (<ul className="reporter-table-list">{listItems}</ul>);
        },
        key: 'scopes'
      },
      {
        title: 'Levels',
        dataIndex: 'levels',
        align: 'left',
        filters: (function () { return packagesLevelsFilter })(),
        onFilter: (level, component) => component.levels.includes(parseInt(level, 10)),
        filterMultiple: true,
        render: (text, row, index) => {
          const listItems = row.levels.map((level) =>
            <li key={level}>{level}</li>
          );

          return (<ul className="reporter-table-list">{listItems}</ul>);
        },
        key: 'levels'
      },
      {
        title: 'Declared Licenses',
        dataIndex: 'declaredLicenses',
        align: 'left',
        filters: (function () { return packagesDeclaredLicensesFilter })(),
        filterMultiple: true,
        render: (text, row, index) => {
          const listItems = row.declaredLicenses.map((license) =>
            <li key={license}><LicenseTag text={license}/></li>
          );

          return (<ul className="reporter-table-list">{listItems}</ul>);
        },
        key: 'declaredLicenses',
        onFilter: (value, record) => record.declaredLicenses.includes(value)
      },
      {
        title: 'Detected Licenses',
        dataIndex: 'detectedLicenses',
        align: 'left',
        filters: (function () { return packagesDetectedLicensesFilter })(),
        onFilter: (license, component) => component.detectedLicenses.includes(license),
        filterMultiple: true,
        render: (text, row, index) => {
          const listItems = row.detectedLicenses.map((license) =>
            <li key={license}><LicenseTag text={license}/></li>
          );

          return (<ul className="reporter-table-list">{listItems}</ul>);
        },
        key: 'detectedLicenses'
      },
      {
        title: 'Status',
        align: 'left',
        filters: (function () { return statusFilter })(),
        onFilter: (status, component) =>
          status === 'ok'
            ? componentOk(component)
            : status === 'errors'
              ? !componentOk(component)
              : false,
        filterMultiple: true,
        render: (text, row, index) => {
          // FIXME Remove quick hack to show 'Status' and
          // switch to using report data
        
          let errorText = '';

          if (componentOk(row)) {
           return <Tag className="reporter-status-ok" color="blue">OK</Tag>
          } else {
            errorText = row.errors.total + ' error';

            if (row.errors.total > 1) {
              errorText = errorText + 's';
            }
          
            return <Tag className="reporter-status-error" color="red">{errorText}</Tag>;
          }
        },
        key: 'status'
      }
    ];
  }

  render() {
    let props = this.props;

    if (!props.data && !props.project) {
      return (
        <div className="reporter-package-info-error">
          <Alert
            message="Oops, something went wrong. Missing data to be able to create ProjectTable" 
            type="error"
            showIcon/>
        </div>
      );
    }

    return (
      <Table
        columns={this.columns}
        expandedRowRender={record => {
          let className = "reporter-package-expand", 
              props = this.props,
              packageId = record.id,
              packageMetaData;

          if (props.data.packagesMetaInfo && props.data.packagesMetaInfo[packageId]) {
            packageMetaData = props.data.packagesMetaInfo[packageId];
          }

          if (record.errors && record.errors.total !== 0) {
            className += "-error"; 
          }

          return (
            <div className={className}>
              <PackageInfo packageData={record} packageMetaData={packageMetaData}/>
            </div>
            )
        }}
        dataSource={this.packages}
        pagination={false}
        size='small'/>
    );
  }
}

// Generates the HTML to display the paths from root package to current package
// in an expanded row of projectTable
// Example
// Gradle:helloworld:app: → Maven:org.jacoco:org.jacoco.agent:0.7.4.201502262128
const PackageDependencyPaths = function (props) {
  var dependencyPaths = props.dependencyPaths,
      dependencyPathsStrArr = [],
      listElements,
      ulElement;

  // Combine dependency path Array into a string
  if (dependencyPaths && dependencyPaths.length > 0 && dependencyPaths[0].length > 1) {
    for (let i = 0; i < dependencyPaths.length; i++) {
     dependencyPathsStrArr.push(dependencyPaths[i]
        .filter(function (value) {return value.toString();})
        .join(' → '));
    }

    listElements = dependencyPathsStrArr.map(
      function (elem) {
        return React.createElement('li', {key: elem}, elem);
      }
    );

    ulElement = React.createElement('ul', {className: 'reporter-package-deps-paths'}, listElements);

    return (
      <div className="reporter-package-deps-path">
        <h4>Dependency Paths</h4>
        {ulElement}
      </div>
    );
  } else {
    return (
      <div className="reporter-package-deps-path"></div>
    );
  }
}

// Generates the HTML for packages errors in an expanded row of projectTable
const PackageErrors = function (props) {
  var packageErrors,
      analyzerPackageErrors,
      analyzerUlElement,
      scannerPackageErrors,
      scannerUlElement,
      listElements;

  if (props.errors) {
    packageErrors = props.errors;
    
    if (packageErrors && packageErrors.analyzer) {
      analyzerPackageErrors = packageErrors.analyzer;
      listElements = analyzerPackageErrors.map(
        function (text) {
          return <Alert message={text} type="error" key={text} showIcon/>;
        }
      );

      analyzerUlElement = React.createElement('span', {className: 'reporter-analyzer-errors'}, listElements);
    }

    if (packageErrors && packageErrors.scanner) {
      scannerPackageErrors = packageErrors.scanner;
      listElements = scannerPackageErrors.map(
        function (text) {
          return <Alert message={text} type="error" key={text} showIcon/>;
        }
      );
      
      scannerUlElement = React.createElement('span', {className: 'reporter-analyzer-errors'}, listElements);
    }

    return (
      <div className="reporter-package-errors">
        {analyzerPackageErrors.length > 0 && <h4>Analyzer Errors</h4>}
        {analyzerUlElement}
        {scannerPackageErrors.length > 0 && <h4>Scanner Errors</h4>}
        {scannerUlElement}
      </div>
    );
  } else {
    return (
      <div className="package-errors"></div>
    );
  }
}

// Generates the HTML for the additional package information in an expanded row of projectTable
const PackageInfo = function (props) {
  if (!props.packageData || !props.packageMetaData) {
    return (
      <div className="reporter-package-info-error">
        <Alert
          message="Oops, something went wrong. Unable to retrieve information for this package." 
          type="error"
          showIcon/>
      </div>
    );
  }

  var packageData = props.packageData,
    packageMetadata = props.packageMetaData,
    packageDescription = "No package description available";
  
  if (packageMetadata) {
    if (packageMetadata.description) {
      packageDescription = packageMetadata.description;
    }
  }
  return (
    <div className="package-info">
      <p className="package-description">{packageDescription}</p>
      <PackageDependencyPaths dependencyPaths={packageData.dependencyPaths}/>
      <PackageErrors errors={packageData.errors}/>
    </div>
  );
}