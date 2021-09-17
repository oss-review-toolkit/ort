![OSS Review Toolkit Logo](./logos/ort.png)

&nbsp;

[![Slack][1]][2]

[![Linux build status][4]][3] [![Windows build status][5]][3] [![Docker build status][6]][3] [![JitPack build status][10]][11]

[![Linux analyzer tests][7]][3] [![Windows analyzer tests][8]][3] [![Code coverage][12]][13]

[![TODOs][14]][15] [![Static Analysis][9]][3] [![LGTM][16]][17] [![REUSE status][18]][19] [![CII][20]][21]

[1]: https://img.shields.io/badge/Join_us_on_Slack!-ort--talk-blue.svg?longCache=true&logo=slack
[2]: https://join.slack.com/t/ort-talk/shared_invite/enQtMzk3MDU5Njk0Njc1LThiNmJmMjc5YWUxZTU4OGI5NmY3YTFlZWM5YTliZmY5ODc0MGMyOWIwYmRiZWFmNGMzOWY2NzVhYTI0NTJkNmY
[3]: https://github.com/oss-review-toolkit/ort/actions/workflows/static-analysis.yml
[4]: https://dev.azure.com/oss-review-toolkit/ort/_apis/build/status/oss-review-toolkit.ort?branchName=master&jobName=LinuxTest&label=Linux%20Build
[5]: https://dev.azure.com/oss-review-toolkit/ort/_apis/build/status/oss-review-toolkit.ort?branchName=master&jobName=WindowsTest&label=Windows%20Build
[6]: https://dev.azure.com/oss-review-toolkit/ort/_apis/build/status/oss-review-toolkit.ort?branchName=master&jobName=DockerBuild&label=Docker%20Build
[7]: https://dev.azure.com/oss-review-toolkit/ort/_apis/build/status/oss-review-toolkit.ort?branchName=master&jobName=LinuxAnalyzerTest&label=Linux%20Analyzer%20Tests
[8]: https://dev.azure.com/oss-review-toolkit/ort/_apis/build/status/oss-review-toolkit.ort?branchName=master&jobName=WindowsAnalyzerTest&label=Windows%20Analyzer%20Tests
[9]: https://github.com/oss-review-toolkit/ort/actions/workflows/static-analysis.yml/badge.svg?branch=master
[10]: https://jitpack.io/v/oss-review-toolkit/ort.svg
[11]: https://jitpack.io/#oss-review-toolkit/ort
[12]: https://codecov.io/gh/oss-review-toolkit/ort/branch/master/graph/badge.svg
[13]: https://codecov.io/gh/oss-review-toolkit/ort/
[14]: https://badgen.net/https/api.tickgit.com/badgen/github.com/oss-review-toolkit/ort
[15]: https://www.tickgit.com/browse?repo=github.com/oss-review-toolkit/ort
[16]: https://img.shields.io/lgtm/alerts/g/oss-review-toolkit/ort.svg?logo=lgtm&logoWidth=18
[17]: https://lgtm.com/projects/g/oss-review-toolkit/ort/alerts/
[18]: https://api.reuse.software/badge/github.com/oss-review-toolkit/ort
[19]: https://api.reuse.software/info/github.com/oss-review-toolkit/ort
[20]: https://bestpractices.coreinfrastructure.org/projects/4618/badge
[21]: https://bestpractices.coreinfrastructure.org/projects/4618

# About OSS Review Toolkit (ORT)
## What It Is

The OSS Review Toolkit (ORT) assists with the tasks commonly performed in the context of license
compliance checks, especially for (but not limited to) Free and Open Source Software dependencies. ORT:

* determines what open source components a piece of software uses (dependencies)
* determines what licenses and copyrights apply
* assesses whether the software complies with the licenses applicable to the dependencies
* assesses whether the software complies with its owners' compliance rules and therefore ...
* helps determine whether the release of the software can be authorized without adverse legal exposure

## How It Works
Under the hood, ORT is a _highly customizable_ group of tools that can be run individually or as a pipeline.

Each tool is implemented as a library (for programmatic use) and exposed via a command line interface (for scripted use):

* [_Analyzer_](#analyzer) - determines the dependencies of projects and their metadata, abstracting which package
  managers or build systems are actually being used.
* [_Downloader_](#downloader) - fetches all source code of the projects and their dependencies, abstracting which
  Version Control System (VCS) or other means are used to retrieve the source code.
* [_Scanner_](#scanner) - uses configured source code scanners to detect license / copyright findings, abstracting
  the type of scanner.
* [_Advisor_](#advisor) - retrieves security advisories for used dependencies from configured vulnerability data
  services.
* [_Evaluator_](#evaluator) - evaluates license / copyright findings against customizable policy rules and license
  classifications.
* [_Reporter_](#reporter) - presents results in various formats such as visual reports, Open Source notices or
  Bill-Of-Materials (BOMs) to easily identify dependencies, licenses, copyrights or policy rule violations.


## Using ORT

For further information, including how to use ORT and the details of the individual tools, please follow these links:

* [System Requirements](./docs/system-requirements.md)
* [Getting Started](./docs/getting-started.md)
* [Configuring ORT](./docs/configuring-ort.md)
* [Details of ORT Tools](./docs/details-of-ort-tools.md)
* [Development](./docs/development.md)


# License

Copyright (C) 2017-2021 HERE Europe B.V.\
Copyright (C) 2019-2020 Bosch Software Innovations GmbH\
Copyright (C) 2020-2021 Bosch.IO GmbH

See the [LICENSE](./LICENSE) file in the root of this project for license details.

OSS Review Toolkit (ORT) is a [Linux Foundation project](https://www.linuxfoundation.org) and part of [ACT](https://automatecompliance.org/).
