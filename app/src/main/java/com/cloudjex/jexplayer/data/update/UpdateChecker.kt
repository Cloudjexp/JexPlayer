package com.cloudjex.jexplayer.data.update

internal class UpdateChecker(
    private val client: ReleaseClient,
    private val store: UpdateCheckStore,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val installedVersion: String
) {
    suspend fun check(force: Boolean): CheckResult {
        val current = now()
        if (!force) {
            val success = store.lastSuccessfulCheck()
            val attempt = store.lastAttempt()
            if (success != null && current - success < UPDATE_INTERVAL_MS) return CheckResult.Skipped
            if (success == null && attempt != null && current - attempt < RETRY_INTERVAL_MS) return CheckResult.Skipped
        }
        store.saveAttempt(current)
        return try {
            val update = latestRelease(client.fetchReleases(), installedVersion)
            store.saveSuccess(current)
            CheckResult.Success(update, update?.let { store.dismissedVersion() == it.version } == true)
        } catch (_: Exception) {
            CheckResult.Failed
        }
    }

    suspend fun dismiss(version: String) = store.dismiss(version)
}

sealed interface CheckResult {
    data object Skipped : CheckResult
    data object Failed : CheckResult
    data class Success(val update: GithubRelease?, val wasDismissed: Boolean) : CheckResult
}
