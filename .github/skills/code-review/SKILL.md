## Commits in the Git history

As outlined in the [contributing guidelines], each commit in a pull request should adhere to the following rules:

* The title should follow the [Conventional Commits] specification with the [commitlint configuration].
* The description part of the title should start with an uppercase character.
* The title should not exceed 75 characters in length.
* Body lines should not exceed 75 characters and must be hard-wrapped with line-end characters.
* The body should provide a rationale for the change and not just repeat the code diff.
* Symbols should be enclosed in backticks so they render nicely in a Markdown view.
* The language should be American English without spelling- or grammar-mistakes.

## Tests written in Kotlin using the Kotest framework

* Prefer the syntax where Kotest specs get the body passed as a lambda constructor argument.
* Infix matcher syntax should be preferred.
* Specific matchers like `collection should haveSize(2)` should be preferred over general matchers like `collection.size shouldBe 2`.

[contributing guidelines]: https://github.com/oss-review-toolkit/.github/blob/main/CONTRIBUTING.md
[Conventional Commits]: https://www.conventionalcommits.org/
[commitlint configuration]: https://github.com/oss-review-toolkit/ort/blob/main/.commitlintrc.yml
