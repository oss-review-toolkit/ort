import React from 'react';
import clsx from 'clsx';
import styles from './styles.module.css';

const FeatureList = [
    {
        title: 'Analyzer',
        Svg: require('@site/static/img/analyzer.svg').default,
        description: (
            <>
                Get the dependencies of your projects, supporting over a dozen different package managers.
            </>
        ),
    },
    {
        title: 'Downloader',
        Svg: require('@site/static/img/downloader.svg').default,
        description: (
            <>
                Download the source code of your dependencies from version control systems or source artifacts.
            </>
        ),
    },
    {
        title: 'Scanner',
        Svg: require('@site/static/img/scanner.svg').default,
        description: (
            <>
                Scan the source code using the supported license, copyright, and snippet scanners.
            </>
        ),
    },
    {
        title: 'Advisor',
        Svg: require('@site/static/img/advisor.svg').default,
        description: (
            <>
                Get the vulnerabilities of your dependencies from different providers.
            </>
        ),
    },
    {
        title: 'Evaluator',
        Svg: require('@site/static/img/evaluator.svg').default,
        description: (
            <>
                Apply custom policy rules against the gathered data using Kotlin scripting.
            </>
        ),
    },
    {
        title: 'Reporter',
        Svg: require('@site/static/img/reporter.svg').default,
        description: (
            <>
                Generate visual reports, open source notices, SBOMs, and more.
            </>
        ),
    },
];

function Feature({Svg, title, description}) {
  return (
    <div className={clsx('col col--4')}>
      <div className="text--center">
        <Svg className={styles.featureSvg} role="img" />
      </div>
      <div className="text--center padding-horiz--md">
        <h3>{title}</h3>
        <p>{description}</p>
      </div>
    </div>
  );
}

export default function HomepageFeatures() {
  return (
    <section className={styles.features}>
      <div className="container">
        <div className="row">
          {FeatureList.map((props, idx) => (
            <Feature key={idx} {...props} />
          ))}
        </div>
      </div>
    </section>
  );
}
