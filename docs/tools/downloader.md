---
sidebar_position: 3
---

# Downloader

Taking an ORT result file with an *analyzer* result as the input (`-i`), the *downloader* retrieves the source code of all contained packages to the specified output directory (`-o`).
The *downloader* takes care of things like normalizing URLs and using the [appropriate VCS tool](https://github.com/oss-review-toolkit/ort/blob/main/plugins/version-control-systems) to check out source code from version control.

Currently, the following Version Control Systems (VCS) are supported:

* [Git](https://git-scm.com/)
* [Git-Repo](https://source.android.com/setup/develop/repo)
* [Mercurial](https://www.mercurial-scm.org/)
* [Subversion](https://subversion.apache.org/)
