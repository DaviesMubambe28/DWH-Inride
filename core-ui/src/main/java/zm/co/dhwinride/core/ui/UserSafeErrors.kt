package zm.co.dhwinride.core.ui

/** Prevents database URLs, authorization headers and implementation details from reaching app UI. */
internal fun userSafeErrorMessage(error: Throwable, fallback: String): String {
    val detail = error.message?.trim().orEmpty()
    if (detail.isBlank() || detail.length > 240) return fallback
    val unsafeMarkers = listOf(
        "\n", "\r", "http://", "https://", "authorization=", "bearer ", "headers:",
        "/rest/v1/", "supabase.co", "postgrest", "sqlstate", "column ", "pgrst",
    )
    return if (unsafeMarkers.any { detail.contains(it, ignoreCase = true) }) fallback else detail
}
