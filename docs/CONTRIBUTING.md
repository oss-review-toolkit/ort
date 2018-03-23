The [OSS Review Toolkit](https://github.com/heremaps/oss-review-toolkit) gratefully accepts contributions via
[pull requests](https://help.github.com/articles/about-pull-requests/) filed against the
[GitHub project](https://github.com/heremaps/oss-review-toolkit/pulls). As part of filing a pull request we ask you to
accept our [Contributor License Agreement](https://gist.github.com/heremaps-bot/dcb932a07b424f8ed68341054e7e3a53) (CLA)
to grant us proper rights for your contribution.

In order to maintain a high software quality standard, we strongly prefer contributions to follow these rules:

- We pay more attention to the quality of commit messages than most other projects on GitHub. In general, we share the
  view on how commit message should be written with [the Git project itself](https://github.com/git/git/blob/master/Documentation/SubmittingPatches):

  - [Make separate commits for logically separate changes.](https://github.com/git/git/blob/e6932248fcb41fb94a0be484050881e03c7eb298/Documentation/SubmittingPatches#L43)
    For example, pure formatting changes that do not change any behavior do usually not belong into the same commit as
    changes to logic.
  - [Describe your changes well.](https://github.com/git/git/blob/e6932248fcb41fb94a0be484050881e03c7eb298/Documentation/SubmittingPatches#L101)
    Do not just repeat in prose what is "obvious" from the code, but provide a rationale explaining *why* you believe
    your change is a good one.
  - [Describe your changes in imperative mood.](https://github.com/git/git/blob/e6932248fcb41fb94a0be484050881e03c7eb298/Documentation/SubmittingPatches#L133)
    Instead of writing "Fixes an issue with encoding" prefer "Fix an encoding issue". Think about it like the commit
    only does something *if* it is applied. This usually results in more concise commit messages.
  - [We are picky about whitespaces.](https://github.com/git/git/blob/e6932248fcb41fb94a0be484050881e03c7eb298/Documentation/SubmittingPatches#L95)
    Trailing whitespace and duplicate blank lines are simply a superfluous annoyance, and most Git tools flag them red
    in the diff anyway. We generally use four spaces for indentation in Kotlin code, and two spaces for indentation in
    JSON / YAML files.

  If you have ever wondered how a "perfect" commit message is supposed to look like, just look at basically any of
  [Jeff King's commits](https://github.com/git/git/commits?author=peff) in the Git project.

- When addressing review comments in a pull request, please fix the issue in the commit where it appears, not in a new
  commit on top of the pull request's history. While this requires force-pushing of the new iteration of you pull
  request's branch, it has several advantages:

  - Reviewers that go through (larger) pull requests commit by commit are always up-to-date with latest fixes, instead
    of coming across a commit that addresses one of possibly also their remarks only at the end.
  - It maintains a cleaner history without distracting commits like "Address review comments".
  - As a result, tools like [git-bisect](https://git-scm.com/docs/git-bisect) can operate in a more meaningful way.
  - Fixing up commits allows for making fixes to commit messages, which is not possible by only adding new commits.

  If you are unfamiliar with fixing up existing commits, please read about [rewriting history](https://git-scm.com/book/id/v2/Git-Tools-Rewriting-History)
  and `git rebase --interactive` in particular.

- To resolve conflicts, rebase pull request branches onto their target branch instead of merging the target branch into
  the pull request branch. This again results in a cleaner history without "criss-cross" merges.

As GitHub is not particularly good at reviewing pull requests commit by commit, it does not support adding review
comments to commit messages at all, and it cannot show the diff between two iterations of force-pushed pull request
branches, we encourage you to give [Reviewable](https://reviewable.io/) a try which addresses these GitHub limitations.
Reviewable is integrated with pull requests to the OSS Review Toolkit and you can find a button to initiate reviews at
the bottom of the first post in a pull request's conversation.

Thank you for reading and happy contributing!
