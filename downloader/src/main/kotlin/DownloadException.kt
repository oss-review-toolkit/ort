package com.here.provenanceanalyzer.downloader

class DownloadException : Exception {
    constructor(cause: Exception): super(cause)
    constructor(message: String): super(message)
    constructor(message: String, cause: Exception?): super(message, cause)
}
