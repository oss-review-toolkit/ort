import React from 'react';
import { connect } from 'react-redux';
import {
    Button,
    Icon,
    Table,
    Tag,
    Tooltip
} from 'antd';
import PropTypes from 'prop-types';
import {
    getSingleTable,
    getTableView,
    getTableViewShouldComponentUpdate
} from '../reducers/selectors';
import store from '../store';
import LicenseTag from './LicenseTag';
import PackageDetails from './PackageDetails';
import PackageErrors from './PackageErrors';
import PackagePaths from './PackagePaths';
import PackageScansSummary from './PackageScansSummary';

class TableView extends React.Component {
    shouldComponentUpdate() {
        const { shouldComponentUpdate } = this.props;
        return shouldComponentUpdate;
    }

    render() {
        const {
            table: {
                columns: {
                    levelsFilter,
                    licensesDeclaredFilter,
                    licensesDetectedFilter,
                    projectsFilter,
                    scopesFilter
                },
                data
            },
            tableView: {
                filter: {
                    filteredInfo,
                    sortedInfo
                }
            }
        } = this.props;

        // Specifies table columns as per
        // https://ant.design/components/table/
        const columns = [
            {
                align: 'left',
                dataIndex: 'definition_file_path',
                filters: projectsFilter,
                filteredValue: filteredInfo.definition_file_path || null,
                onFilter: (value, record) => record.projectKey === parseInt(value, 10),
                title: 'Project',
                render: text => (
                    <span className="ort-project-id">
                        <Tooltip placement="right" title={`Defined in ${text}`}>
                            <Icon type="file-text" />
                        </Tooltip>
                    </span>
                ),
                width: 80
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
                    <span className="ort-package-id">
                        {text}
                    </span>
                )
            },
            {
                align: 'left',
                dataIndex: 'scopes',
                filters: scopesFilter,
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
                align: 'left',
                dataIndex: 'levels',
                filters: levelsFilter,
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
                dataIndex: 'declared_licenses',
                filters: licensesDeclaredFilter,
                filteredValue: filteredInfo.declared_licenses || null,
                filterMultiple: true,
                key: 'declared_licenses',
                onFilter: (value, record) => record.declared_licenses.includes(value),
                title: 'Declared Licenses',
                render: (text, row) => (
                    <ul className="ort-table-list">
                        {row.declared_licenses.map(license => (
                            <li key={license}>
                                <LicenseTag text={license} ellipsisAtChar={20} />
                            </li>
                        ))}
                    </ul>
                ),
                width: 160
            },
            {
                align: 'left',
                dataIndex: 'detected_licenses',
                filters: licensesDetectedFilter,
                filteredValue: filteredInfo.detected_licenses || null,
                filterMultiple: true,
                onFilter: (license, component) => component.detected_licenses.includes(license),
                title: 'Detected Licenses',
                render: (text, row) => (
                    <ul className="ort-table-list">
                        {row.detected_licenses.map(license => (
                            <li key={license}>
                                <LicenseTag text={license} ellipsisAtChar={20} />
                            </li>
                        ))}
                    </ul>),
                width: 160
            },
            {
                align: 'left',
                filters: (() => [
                    { text: 'Errors', value: 'errors' },
                    { text: 'OK', value: 'ok' }
                ])(),
                filterMultiple: true,
                key: 'status',
                onFilter: (status, component) => {
                    if (status === 'ok') {
                        return component.errors.length === 0;
                    }

                    if (status === 'errors') {
                        return component.errors.length !== 0;
                    }

                    return false;
                },
                render: (text, row) => {
                    const nrErrorsText = errors => `${errors.length} error${(errors.length > 1) ? 's' : ''}`;

                    if (Array.isArray(row.errors) && row.errors.length > 0) {
                        return (
                            <Tag className="ort-status-error" color="red">
                                {nrErrorsText(row.errors)}
                            </Tag>
                        );
                    }

                    return (
                        <Tag className="ort-status-ok" color="blue">
                            OK
                        </Tag>
                    );
                },
                title: 'Status',
                width: 80
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
                    expandedRowRender={(record) => {
                        if (!record) {
                            return (
                                <span>
                                    No additional data available for this package
                                </span>
                            );
                        }

                        return (
                            <div>
                                <PackageDetails data={record} show={false} />
                                <PackagePaths data={record} show={false} />
                                <PackageErrors data={record} show />
                                <PackageScansSummary data={record} show={false} />
                            </div>
                        );
                    }}
                    dataSource={data}
                    expandRowByClick
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
                            hideOnSinglePage: true,
                            pageSize: 100,
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
    table: PropTypes.object.isRequired,
    tableView: PropTypes.object.isRequired
};

const mapStateToProps = state => ({
    shouldComponentUpdate: getTableViewShouldComponentUpdate(state),
    table: getSingleTable(state),
    tableView: getTableView(state)
});

export default connect(
    mapStateToProps,
    () => ({})
)(TableView);
