import React from 'react';
import { connect } from 'react-redux';
import { Col, Collapse, Row } from 'antd';
import PropTypes from 'prop-types';
import PackagesTable from './PackagesTable';

const { Panel } = Collapse;

class TableView extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            view: {
                expandedProjects: [],
                projectsToBeExpanded: Object.values(props.reportData.projects.data)
                    .reduce((accumulator, project) => {
                        accumulator.push(`ort-table-panel-${project.id}`);
                        return accumulator;
                    }, [])
                    .reverse()
            }
        };

        if (props.reportData) {
            this.state = {
                ...this.state,
                data: props.reportData
            };
        }

        // Bind so `this` works in the Collapse's onChange callback
        this.onToggleProject = this.onToggleProject.bind(this);
    }

    componentDidMount() {
        const { view: { projectsToBeExpanded } } = this.state;

        if (projectsToBeExpanded.length !== 0) {
            this.timerProjectsToBeExpanded = setInterval(
                () => this.expandProjects(),
                1000
            );
        }
    }

    componentWillUnmount() {
        clearInterval(this.timerProjectsToBeExpanded);
    }

    onToggleProject(activeKeys) {
        this.setState((prevState) => {
            const state = { ...prevState };

            state.view.expandedProjects = activeKeys;

            return state;
        });
    }

    expandProjects() {
        const { view: { projectsToBeExpanded } } = this.state;
        const panel = projectsToBeExpanded.pop();

        if (panel) {
            this.setState((prevState) => {
                const state = { ...prevState };

                state.view.expandedProjects.push(panel);
                state.view.projectsToBeExpanded = projectsToBeExpanded;

                return state;
            });
        }

        if (projectsToBeExpanded.length === 0) {
            clearInterval(this.timerProjectsToBeExpanded);
        }
    }

    render() {
        const { data, view } = this.state;
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
                activeKey={view.expandedProjects}
                className="ort-table"
                onChange={this.onToggleProject}
            >
                {Object.values(data.projects.data).map(project => (
                    <Panel key={`ort-table-panel-${project.id}`} header={panelHeader(project)}>
                        <PackagesTable project={project} />
                    </Panel>
                ))}
            </Collapse>
        );
    }
}

TableView.propTypes = {
    reportData: PropTypes.object.isRequired
};

export default connect(
    state => ({ reportData: state.data.report }),
    () => ({})
)(TableView);
