import React from 'react';
import { connect } from 'react-redux';
import { Col, Collapse, Row } from 'antd';
import { PackagesTable } from './PackagesTable';

const Panel = Collapse.Panel;

class TableView extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            view: {
                showProjects: [],
                forceRender: false
            }
        };

        if (props.reportData) {
            this.state = {
                ...this.state,
                data: props.reportData
            };

            // Expand all project panels with 0.5 second delay
            // to ensure smooth UI when tabs switching
            window.setTimeout(() => {
                const { data } = this.state;
                const projects = Object.values(
                    data.projects.data
                ).reduce((accumulator, project) => {
                    accumulator.push(`panel-${project.id}`);
                    return accumulator;
                }, []);

                this.setState({
                    view: { showProjects: projects }
                });
            }, 500);
        }

        // Bind so `this` works in the Collapse's onChange callback
        this.onChangeProjectCollapse = this.onChangeProjectCollapse.bind(this);
    }

    onChangeProjectCollapse(activeKeys) {
        this.setState({
            view: {
                showProjects: activeKeys
            }
        });
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
            <Collapse activeKey={view.showProjects} onChange={this.onChangeProjectCollapse}>
                {Object.values(data.projects.data).map(project => (
                    <Panel key={`panel-${project.id}`} header={panelHeader(project)}>
                        <PackagesTable project={project} />
                    </Panel>
                ))}
            </Collapse>
        );
    }
}

export default connect(
    state => ({ reportData: state }),
    () => ({})
)(TableView);
