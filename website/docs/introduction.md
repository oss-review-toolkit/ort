# Introduction

The OSS Review Toolkit (ORT) is a FOSS policy automation and orchestration toolkit that you can use to manage your (open source) software dependencies in a strategic, safe and efficient manner.

## Features

You can use ORT to:

* Generate [CycloneDX, SPDX SBOMs][how-to-generate-sboms], or [custom FOSS attribution documentation][reporter-templates] for your software project
* Automate your FOSS policy using risk-based Policy as Code to do licensing, security vulnerability, InnerSource and engineering standards checks for your software project and its dependencies
* Create a [source code archive][how-to-download-sources] for your software project and its dependencies to comply with certain licenses or have your own copy as nothing on the internet is forever
* Correct [package metadata][package-curations] or [licensing findings][how-to-correct-licenses] yourself, using InnerSource or with the help of the FOSS community

ORT can be used as a library (for programmatic use), via a command line interface (for scripted use), or via its CI integrations.
It consists of the following tools which can be combined into a *highly customizable* pipeline:

* [*Analyzer*](reference/cli/analyzer.md) - determines the dependencies of projects and their metadata, abstracting which package managers or build systems are actually being used.
* [*Downloader*](reference/cli/downloader.md) - fetches all source code of the projects and their dependencies, abstracting which Version Control System (VCS) or other means are used to retrieve the source code.
* [*Scanner*](reference/cli/scanner.md) - uses configured source code scanners to detect license / copyright findings, abstracting the type of scanner.
* [*Advisor*](reference/cli/advisor.md) - retrieves security advisories for used dependencies from configured vulnerability data services.
* [*Evaluator*](reference/cli/evaluator.md) - evaluates custom policy rules along with custom license classifications against the data gathered in preceding stages and returns a list of policy violations, e.g. to flag license findings.
* [*Reporter*](reference/cli/reporter.md) - presents results in various formats such as visual reports, Open Source notices or Bill-Of-Materials (BOMs) to easily identify dependencies, licenses, copyrights or policy rule violations.
* [*Notifier*](reference/cli/notifier.md) - sends result notifications via different channels (like emails and / or JIRA tickets).

## Documentation system

ORT documentation is organized using the following [system][documentation-system]:

* *Getting Started* - Begin here if you are new to ORT.
  * [Installing ORT](getting-started/installation.md)
  * [Usage](getting-started/usage.md)
  * [CI integrations](getting-started/ci-integrations.md)
* *Tutorials* - Learn via practical, step-by-step guides.
  * [Using ORT on your first project](tutorials/walkthrough/index.md)
  * [Addressing WebApp report findings](tutorials/adresssing-webapp-report-findings.md)
  * [Automating policy checks](tutorials/automating-policy-checks.md)
  * [Running ORT with Docker](tutorials/docker.md)
* *How-to guides* - Goal-oriented guides for specific tasks:
  * [How to exclude dirs, files, or scopes](how-to-guides/how-to-exclude-dirs-files-or-scopes.md)
  * [How to address tool issues](how-to-guides/how-to-address-tool-issues.md)
  * [How to define package sources](how-to-guides/how-to-define-package-sources.md)
  * [How to correct licenses](how-to-guides/how-to-correct-licenses.md)
  * [How to address a license policy violation](how-to-guides/how-to-address-a-license-policy-violation.md)
  * [How to make a license choice](how-to-guides/how-to-make-a-license-choice.md)
  * [How to check and remediate vulnerabilities in dependencies](how-to-guides/how-to-check-and-remediate-vulnerabilities-in-dependencies.md)
  * [How to generate SBOMs](how-to-guides/how-to-generate-sboms.md)
  * [How to download sources for projects and dependencies](how-to-guides/how-to-download-sources-for-projects-and-dependencies.md)
* *Reference* - Consult the reference to find CLI parameters.
  * [ORT CLI reference](reference/cli/index.md)
  * [ORT Helper CLI reference](reference/cli/orth.md)
* *Explanation* - Deepen your understanding of ORT key concepts.
  * [Types of licenses](explanation/types-of-licenses.md)
  * [License clearance strategies](explanation/license-clearance-strategies.md)
  * [Documentation system](explanation/documentation-system.md)

## Staying informed

* [GitHub]
* [Slack]
* [ORT weekly meeting][ort-weekly-meeting]
* [LinkedIn]

## Something missing?

If you find issues with the documentation or have suggestions on how to improve the documentation or the project in general, please [file an issue][ort-github-issues] for us, or send a message on *general* channel on our [Slack].

For new feature requests, please review the existing [GitHub issues][ort-github-issues] before creating a new one. Refrain from making a Pull Request for large new features without [talking to us first][Slack]. This helps us agree on features together, incorporate them into our [roadmap], and prevent duplicated efforts.

[documentation-system]: explanation/documentation-system
[GitHub]: https://github.com/oss-review-toolkit/ort
[how-to-correct-licenses]: ./how-to-guides/how-to-correct-licenses
[how-to-download-sources]: ./how-to-guides/how-to-download-sources-for-projects-and-dependencies
[how-to-generate-sboms]: ./how-to-guides/how-to-generate-sboms
[LinkedIn]: https://www.linkedin.com/company/oss-review-toolkit
[ort-github-issues]: https://github.com/oss-review-toolkit/ort/issues
[ort-weekly-meeting]: https://github.com/oss-review-toolkit/ort/wiki/ORT-Weekly-Meeting
[package-curations]: ./reference/configuration/package-curations
[reporter-templates]: ./reference/configuration/reporter-templates
[roadmap]: https://github.com/orgs/oss-review-toolkit/projects/3/views/1
[Slack]: http://slack.oss-review-toolkit.org/
