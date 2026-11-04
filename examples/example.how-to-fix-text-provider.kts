object : HowToFixTextProvider {
    fun Issue.matchesMessage(pattern: String): Boolean = pattern.toRegex().matches(message)

    fun buildFossIdScanUrl(serverUrl: String, scanId: String): String =
        "${if (serverUrl.endsWith('/')) serverUrl else "$serverUrl/"}?form=main_interface&action=scanview&sid=$scanId"

    fun findScanResultAdditionalData(issue: Issue): Map<String, String>? =
        ortResult.getScanResults().values
            .flatten()
            .firstOrNull { issue in it.summary.issues }
            ?.additionalData

    override fun getHowToFixText(issue: Issue): String? {
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

        if (issue.source == "FossId" && issue.matchesMessage(".*pending identification in FossID.*")) {
            val additionalData = findScanResultAdditionalData(issue).orEmpty()
            val scanId = additionalData["scanid"]
            val serverUrl = additionalData["serverurl"]

            if (!scanId.isNullOrBlank() && !serverUrl.isNullOrBlank()) {
                val scanUrl = buildFossIdScanUrl(serverUrl, scanId)

                return """
                    | This scan still has file(s) pending identification in FossID.
                    | Please review and resolve them at: $scanUrl
                    |
                """.trimIndent()
            }
        }

        return null
    }
}
