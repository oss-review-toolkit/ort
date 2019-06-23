import React from 'react';
import { connect } from 'react-redux';
import {
    Button,
    Icon,
    Table,
    // Tag,
    Tooltip
} from 'antd';
import PropTypes from 'prop-types';
import {
    getOrtResult,
    getTableView,
    getTableViewShouldComponentUpdate
} from '../reducers/selectors';
import store from '../store';
import PackageCollapse from './PackageCollapse';

class TableView extends React.Component {
    shouldComponentUpdate() {
        const { shouldComponentUpdate } = this.props;
        return shouldComponentUpdate;
    }

    render() {
        const {
            tableView: {
                filter: {
                    filteredInfo,
                    sortedInfo
                }
            },
            webAppOrtResult
        } = this.props;

        // Specifies table columns as per
        // https://ant.design/components/table/
        const columns = [
            {
                align: 'right',
                filters: (() => [
                    { text: 'Errors', value: 'errors' },
                    { text: 'Violations', value: 'violations' }
                ])(),
                filterMultiple: true,
                key: 'errors',
                onFilter: (value, pkg) => {
                    if (value === 'errors') {
                        return pkg.hasErrors(webAppOrtResult);
                    }

                    if (value === 'violations') {
                        return pkg.hasViolations(webAppOrtResult);
                    }

                    return false;
                },
                render: (pkg) => {
                    if (pkg.hasErrors(webAppOrtResult) || pkg.hasViolations(webAppOrtResult)) {
                        return (
                            <Icon
                                type="exclamation-circle"
                                className="ort-error"
                            />
                        );
                    }

                    return (
                        <Icon
                            type="check-circle"
                            className="ort-success"
                        />
                    );
                },
                width: '0.8em'
            },
            {
                align: 'right',
                dataIndex: 'projects',
                filters: (() => {
                    const projects = webAppOrtResult.getProjects();
                    return projects.map((project, index) => ({ text: project.definitionFilePath, value: index }));
                })(),
                filteredValue: filteredInfo.projects || null,
                onFilter: (value, record) => record.projectIndexes.includes(parseInt(value, 10)),
                render: (text, record) => {
                    const prj = webAppOrtResult
                        .getProjectByIndex(record.projectIndexes[0]);
                    if (prj && prj.definitionFilePath) {
                        return (
                            <span className="ort-project-id">
                                <Tooltip
                                    placement="right"
                                    title={`Defined in ${prj.definitionFilePath}`}
                                >
                                    <Icon type="file-text" />
                                </Tooltip>
                            </span>
                        );
                    }

                    return (
                        <span className="ort-project-id">
                            <Icon type="file-text" />
                        </span>
                    );
                },
                width: '0.8em'
            },
            {
                align: 'left',
                dataIndex: 'id',
                onFilter: (value, record) => record.id.includes(value),
                sorter: (a, b) => {
                    const idA = a.id.toUpperCase();
                    const idB = b.id.toUpperCase();
                    if (idA < idB) {
                        return -1;
                    }
                    if (idA > idB) {
                        return 1;
                    }

                    return 0;
                },
                sortOrder: sortedInfo.columnKey === 'id' && sortedInfo.order,
                title: 'Package',
                render: text => (
                    <span
                        className="ort-package-id ort-word-break-wrap"
                    >
                        {text}
                    </span>
                )
            },
            {
                align: 'left',
                dataIndex: 'scopes',
                filters: (() => webAppOrtResult.scopes.map(scope => ({ text: scope, value: scope })))(),
                filteredValue: filteredInfo.scopes || null,
                onFilter: (scope, component) => component.scopes.includes(scope),
                title: 'Scopes',
                render: (text, row) => (
                    <ul className="ort-table-list">
                        {row.scopes.map(scope => (
                            <li key={`scope-${scope}`}>
                                {scope}
                            </li>
                        ))}
                    </ul>
                )
            },
            {
                align: 'center',
                dataIndex: 'levels',
                filters: (() => webAppOrtResult.levels.map(level => ({ text: level, value: level })))(),
                filteredValue: filteredInfo.levels || null,
                filterMultiple: true,
                onFilter: (level, component) => component.levels.includes(parseInt(level, 10)),
                render: (text, row) => (
                    <ul className="ort-table-list">
                        {row.levels.sort().map(level => (
                            <li key={`level-${level}`}>
                                {level}
                            </li>
                        ))}
                    </ul>
                ),
                title: 'Levels',
                width: 80
            },
            {
                align: 'left',
                dataIndex: 'declaredLicenses',
                filters: (() => webAppOrtResult.declaredLicenses.map(license => ({ text: license, value: license })))(),
                filteredValue: filteredInfo.declaredLicenses || null,
                filterMultiple: true,
                key: 'declaredLicenses',
                onFilter: (value, record) => record.declaredLicenses.includes(value),
                title: 'Declared Licenses',
                render: (text, row) => (
                    <ul className="ort-table-list">
                        {row.declaredLicenses.map((license, index) => (
                            <span
                                className="ort-word-break-wrap"
                                key={`ort-package-license-${license}`}
                            >
                                {license}
                                {index !== (row.declaredLicenses.length - 1) && ', '}
                            </span>
                        ))}
                    </ul>
                ),
                width: 160
            },
            {
                align: 'left',
                dataIndex: 'detectedLicenses',
                filters: (() => webAppOrtResult.detectedLicenses.map(license => ({ text: license, value: license })))(),
                filteredValue: filteredInfo.detectedLicenses || null,
                filterMultiple: true,
                onFilter: (license, component) => component.detectedLicenses.includes(license),
                title: 'Detected Licenses',
                render: (text, row) => (
                    <ul className="ort-table-list">
                        {
                            row.detectedLicenses.map((license, index) => (
                                <span
                                    className="ort-word-break-wrap"
                                    key={`ort-package-license-${license}`}
                                >
                                    {license}
                                    {index !== (row.detectedLicenses.length - 1) && ', '}
                                </span>
                            ))
                        }
                    </ul>
                ),
                width: 160
            }
        ];

        return (
            <div>
                <div className="ort-table-operations">
                    <Button
                        onClick={() => {
                            store.dispatch({ type: 'TABLE::CLEAR_FILTERS_TABLE' });
                        }}
                        size="small"
                    >
                        Clear filters
                    </Button>
                </div>
                <Table
                    columns={columns}
                    expandedRowRender={
                        pkg => (
                            <PackageCollapse
                                pkg={pkg}
                                webAppOrtResult={webAppOrtResult}
                            />
                        )
                    }
                    dataSource={webAppOrtResult.packagesTreeFlatArray}
                    expandRowByClick
                    indentSize={0}
                    locale={{
                        emptyText: 'No packages'
                    }}
                    onChange={(pagination, filters, sorter, extra) => {
                        store.dispatch({
                            type: 'TABLE::CHANGE_PACKAGES_TABLE',
                            payload: {
                                filter: {
                                    filteredInfo: filters,
                                    sortedInfo: sorter
                                },
                                filterData: extra.currentDataSource
                            }
                        });
                    }}
                    pagination={
                        {
                            defaultPageSize: 100,
                            hideOnSinglePage: true,
                            pageSizeOptions: ['50', '100', '250', '500'],
                            position: 'both',
                            showSizeChanger: true
                        }
                    }
                    size="small"
                    rowClassName="ort-package"
                    rowKey="key"
                />
            </div>
        );
    }
}

TableView.propTypes = {
    shouldComponentUpdate: PropTypes.bool.isRequired,
    tableView: PropTypes.object.isRequired,
    webAppOrtResult: PropTypes.object.isRequired
};

const mapStateToProps = state => ({
    shouldComponentUpdate: getTableViewShouldComponentUpdate(state),
    tableView: getTableView(state),
    webAppOrtResult: getOrtResult(state)
});

export default connect(
    mapStateToProps,
    () => ({})
)(TableView);
