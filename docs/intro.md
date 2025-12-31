---
sidebar_position: 1
---

# Introduction

The OSS Review Toolkit (ORT) is a FOSS policy automation and orchestration toolkit that you can use to manage your (open source) software dependencies in a strategic, safe and efficient manner.

You can use it to:

* Generate CycloneDX, SPDX SBOMs, or custom FOSS attribution documentation for your software project
* Automate your FOSS policy using risk-based Policy as Code to do licensing, security vulnerability, InnerSource and engineering standards checks for your software project and its dependencies
* Create a source code archive for your software project and its dependencies to comply with certain licenses or have your own copy as nothing on the internet is forever
* Correct package metadata or licensing findings yourself, using InnerSource or with the help of the FOSS community

ORT can be used as a library (for programmatic use), via a command line interface (for scripted use), or via its CI integrations.
It consists of the following tools which can be combined into a *highly customizable* pipeline:

* [*Analyzer*](tools/analyzer.md) - determines the dependencies of projects and their metadata, abstracting which package managers or build systems are actually being used.
* [*Downloader*](tools/downloader.md) - fetches all source code of the projects and their dependencies, abstracting which Version Control System (VCS) or other means are used to retrieve the source code.
* [*Scanner*](tools/scanner.md) - uses configured source code scanners to detect license / copyright findings, abstracting the type of scanner.
* [*Advisor*](tools/advisor.md) - retrieves security advisories for used dependencies from configured vulnerability data services.
* [*Evaluator*](tools/evaluator.md) - evaluates custom policy rules along with custom license classifications against the data gathered in preceding stages and returns a list of policy violations, e.g. to flag license findings.
* [*Reporter*](tools/reporter.md) - presents results in various formats such as visual reports, Open Source notices or Bill-Of-Materials (BOMs) to easily identify dependencies, licenses, copyrights or policy rule violations.
* *Notifier* - sends result notifications via different channels (like emails and / or JIRA tickets).

Also see the [list of related tools](related-tools.md) that help with running ORT.
