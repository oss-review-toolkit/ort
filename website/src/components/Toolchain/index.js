/*
 * Copyright (C) 2025 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
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
import styles from './styles.module.css';
import clsx from 'clsx';
import Link from '@docusaurus/Link';
import AdvisorSvg from '@site/static/img/advisor.svg';
import AnalyzerSvg from '@site/static/img/analyzer.svg';
import EvaluatorSvg from '@site/static/img/evaluator.svg';
import ReporterSvg from '@site/static/img/reporter.svg';
import ScannerSvg from '@site/static/img/scanner.svg';

function Tool({ title, Svg, description }) {
  const targetId = title.toLowerCase();

  const handleClick = (e) => {
    e.preventDefault();

    // Update the URL manually to support back button navigation.
    window.history.pushState(null, '', `#${targetId}`);

    const element = document.getElementById(targetId);
    if (element) {
      element.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  };

  return (
    <div className={clsx(styles.tool, 'shadow--tl')}>
      <a
        href={`#${targetId}`}
        onClick={handleClick}
        className={styles.toolLink}
      >
        <h3>{title}</h3>
        <Svg className={styles.toolSvg} role="img" />
        <p>{description}</p>
      </a>
    </div>
  );
}

export default function Toolchain() {
  const tools = [
    { title: 'Analyzer', Svg: AnalyzerSvg, description: 'Find dependencies' },
    { title: 'Scanner', Svg: ScannerSvg, description: 'Scan source code' },
    { title: 'Advisor', Svg: AdvisorSvg, description: 'Find vulnerabilities' },
    {
      title: 'Evaluator',
      Svg: EvaluatorSvg,
      description: 'Apply policy rules',
    },
    { title: 'Reporter', Svg: ReporterSvg, description: 'Generate reports' },
  ];

  return (
    <section className={styles.toolchain}>
      <div className={styles.content}>
        <h1>ORT Toolchain</h1>
        <p>
          The <b>OSS Review Toolkit (ORT)</b> is a set of tools that work
          together to help you manage and analyze your software projects. It
          provides a comprehensive solution for Software Composition Analysis
          (SCA), license compliance, vulnerability management, and more, helping
          you to manage the risks in your software supply chain.
        </p>
      </div>
      <div className={styles.tools}>
        {tools.map((tool, index) => (
          <React.Fragment key={index}>
            <Tool {...tool} />
            {index < tools.length - 1 && (
              <span className={styles.arrow}>&#8594;</span>
            )}
          </React.Fragment>
        ))}
      </div>
      <div className={styles.content}>
        <p>
          Each tool in the ORT toolchain has a specific role, and you can use
          them individually or together to achieve your goals. The tools are
          designed to be modular and can be integrated into your existing
          workflows, whether you are working on a small project or a large
          enterprise application.
        </p>
        <p>
          For more information on how to use these tools, check out the{' '}
          <Link to="/docs/category/tools">documentation</Link>.
        </p>
      </div>
    </section>
  );
}
