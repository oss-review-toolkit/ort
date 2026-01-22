# How to correct copyrights

When ORT's scanner reports invalid copyright statements (encoding artifacts, garbled text, or false positives), you can filter them out using [copyright garbage][copyright-garbage].

## Removing invalid findings with exact match

To remove a specific invalid copyright statement, add it to the `items` list in your `copyright-garbage.yml` file:

```yaml
items:
- "(c) \x00\x1a garbled text"
- "Copyright <OWNER>"
```

## Removing invalid findings with patterns

To remove multiple similar invalid findings, use regex patterns:

```yaml
patterns:
- "^\\(c\\) https?://stackoverflow\\.com/questions/.+$"
- "^Copyright \\d{4} <.*>$"
```

## Correcting copyright findings

Unlike license findings, ORT does not yet support curating individual copyright findings. This feature is still under discussion in [issue #4519](https://github.com/oss-review-toolkit/ort/issues/4519). If you are interested in making this feature a reality, please join the conversation on [Slack](http://slack.oss-review-toolkit.org/).

## Related resources

* Reference
  * [Copyright garbage][copyright-garbage]

[copyright-garbage]: ../reference/configuration/copyright-garbage.md
