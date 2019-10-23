/*
 * Copyright (C) 2019 Bosch Software Innovations GmbH
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

pipeline {
    agent any

    parameters {
        string(
            name: 'VCS_URL',
            description: 'VCS clone URL of the project',
            defaultValue: 'https://github.com/vdurmont/semver4j.git'
        )

        string(
            name: 'VCS_BRANCH',
            description: 'VCS branch of the project',
            defaultValue: 'master'
        )

        choice(
            name: 'LOG_LEVEL',
            description: 'Log message level',
            choices: ['--info', '--debug', '']
        )
    }

    stages {
        stage('Clone project') {
            steps {
                sh 'rm -fr project'

                // See https://jenkins.io/doc/pipeline/steps/git/.
                checkout([$class: 'GitSCM',
                    userRemoteConfigs: [[url: params.VCS_URL]],
                    branches: [[name: "*/${params.VCS_BRANCH}"]],
                    extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'project/source']]
                ])
            }
        }

        stage('Build ORT distribution') {
            steps {
                sh 'docker/build.sh'
            }
        }

        stage('Run ORT analyzer') {
            steps {
                sh 'docker/run.sh "-v $WORKSPACE/project:/project" $LOG_LEVEL analyze -f JSON,YAML -i /project/source -o /project/ort/analyzer'
            }

            post {
                always {
                    archiveArtifacts(
                        artifacts: 'project/ort/analyzer/*',
                        fingerprint: true
                    )
                }
            }
        }

        stage('Run ORT scanner') {
            steps {
                sh 'docker/run.sh "-v $WORKSPACE/project:/project" $LOG_LEVEL scan -f JSON,YAML -i /project/ort/analyzer/analyzer-result.yml -o /project/ort/scanner'
            }

            post {
                always {
                    archiveArtifacts(
                        artifacts: 'project/ort/scanner/*',
                        fingerprint: true
                    )
                }
            }
        }

        stage('Run ORT reporter') {
            steps {
                sh 'docker/run.sh "-v $WORKSPACE/project:/project" $LOG_LEVEL report -f CycloneDX,NOTICE,StaticHTML,WebApp -i /project/ort/scanner/scan-result.yml -o /project/ort/reporter'
            }

            post {
                always {
                    archiveArtifacts(
                        artifacts: 'project/ort/reporter/*',
                        fingerprint: true
                    )
                }
            }
        }
    }
}
