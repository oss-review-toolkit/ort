import React from 'react';
import { connect } from 'react-redux';
import { Col, Collapse, Row } from 'antd';
import PropTypes from 'prop-types';
import PackagesTable from './PackagesTable';
import {
    getSingleTable,
    getSingleTableColumns,
    getTableView,
    getTableViewShouldComponentUpdate,
    getReportData
} from '../reducers/selectors';
import store from '../store';

const { Panel } = Collapse;

class TableView extends React.Component {
    shouldComponentUpdate() {
        const { shouldComponentUpdate } = this.props;
        return shouldComponentUpdate;
    }

    render() {
        const {
            table,
            tableColumns,
            tableView: {
                expandedProjectsKeys
            },
            reportData
        } = this.props;

        const panelHeader = (project) => {
            const nrPackagesText = nrPackages => (`${nrPackages} package${(nrPackages > 1) ? 's' : ''}`);

            return (
                <Row>
                    <Col span={12}>
                        Dependencies defined in
                        {' '}
                        <b>
                            {' '}
                            {project.definition_file_path}
                        </b>
                    </Col>
                    <Col span={2} offset={10}>
                        {nrPackagesText(project.packages.total)}
                    </Col>
                </Row>
            );
        };

        return (
            <Collapse
                activeKey={expandedProjectsKeys}
                className="ort-table"
                onChange={
                    (projectsKeys) => {
                        store.dispatch({
                            type: 'TABLE::PROJECT_EXPAND',
                            expandedProjectsKeys: projectsKeys
                        });
                    }
                }
            >
                {Object.values(reportData.projects).map(project => (
                    <Panel key={`ort-table-panel-${project.id}`} header={panelHeader(project)}>
                        <PackagesTable project={project} />
                    </Panel>
                ))}
            </Collapse>
        );
    }
}

TableView.propTypes = {
    shouldComponentUpdate: PropTypes.bool.isRequired,
    table: PropTypes.array.isRequired,
    tableColumns: PropTypes.object.isRequired,
    tableView: PropTypes.object.isRequired,
    reportData: PropTypes.object.isRequired
};

const mapStateToProps = state => ({
    shouldComponentUpdate: getTableViewShouldComponentUpdate(state),
    table: getSingleTable(state),
    tableColumns: getSingleTableColumns(state),
    tableView: getTableView(state),
    reportData: getReportData(state)
});

export default connect(
    mapStateToProps,
    () => ({})
)(TableView);
