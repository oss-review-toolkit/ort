object : HowToFixTextProvider {
    fun OrtIssue.matchesMessage(pattern: String): Boolean = pattern.toRegex().matches(message)

    override fun getHowToFixText(issue: OrtIssue): String? {
        // How-to-fix instruction Markdown for scan timeout errors.
        if (issue.matchesMessage("ERROR: Timeout after .* seconds while scanning file.*")) {
            return """
                | To fix this issue please proceed with the following steps:
                |
                |1. Manually verify that the file does not contain any license information.
                |2. File an error resolution in the resolutions file of your configuration directory with a comment
                |   stating that the file does not contain any license information.
                |   ```
                |
            """.trimIndent()
        }

        return null
    }
}
