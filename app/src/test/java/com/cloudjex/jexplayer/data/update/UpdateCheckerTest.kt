package com.cloudjex.jexplayer.data.update

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class UpdateCheckerTest {
    private class Store : UpdateCheckStore {
        var success: Long? = null
        var attempt: Long? = null
        var dismissed: String? = null
        override suspend fun lastSuccessfulCheck() = success
        override suspend fun lastAttempt() = attempt
        override suspend fun dismissedVersion() = dismissed
        override suspend fun saveSuccess(timestamp: Long) { success = timestamp }
        override suspend fun saveAttempt(timestamp: Long) { attempt = timestamp }
        override suspend fun dismiss(version: String) { dismissed = version }
    }

    private class Client(private val result: List<GithubRelease> = emptyList(), private val error: Boolean = false) : ReleaseClient {
        var calls = 0
        override suspend fun fetchReleases(): List<GithubRelease> {
            calls++
            if (error) error("network")
            return result
        }
    }

    @Test fun automaticCheckWaits72HoursAfterSuccess() = runBlocking {
        val store = Store().apply { success = 100L }
        val client = Client()
        val checker = UpdateChecker(client, store, now = { 100L + UPDATE_INTERVAL_MS - 1 }, installedVersion = "1.0.0")
        assertSame(CheckResult.Skipped, checker.check(force = false))
        assertEquals(0, client.calls)
    }

    @Test fun semanticComparisonOrdersPreReleasesBeforeStable() {
        val releases = listOf(
            GithubRelease("v0.1.0-beta.2", "", "", true),
            GithubRelease("0.1.0", "", "", false),
            GithubRelease("0.1.0-beta.1", "", "", true)
        )
        assertEquals("0.1.0", latestRelease(releases, "0.1.0-beta.1")?.version)
        assertTrue(AppVersion.parse("0.1.0-beta.2")!! < AppVersion.parse("0.1.0")!!)
        assertTrue(AppVersion.parse("0.1.0-beta.1")!! < AppVersion.parse("0.1.0-beta.2")!!)
    }

    @Test fun networkErrorDoesNotRecordSuccessfulCheckAndCanRetryAfterBackoff() = runBlocking {
        val store = Store()
        val client = Client(error = true)
        val checker = UpdateChecker(client, store, now = { 1_000L }, installedVersion = "1.0.0")
        assertSame(CheckResult.Failed, checker.check(force = false))
        assertNull(store.success)
        assertEquals(1, client.calls)
        assertSame(CheckResult.Skipped, checker.check(force = false))
        assertEquals(1, client.calls)
    }

    @Test fun dismissedAutomaticUpdateIsSuppressedButManualCheckReturnsIt() = runBlocking {
        val store = Store().apply { dismissed = "1.1.0" }
        val client = Client(listOf(GithubRelease("1.1.0", "notes", "url", false)))
        val checker = UpdateChecker(client, store, now = { 1_000L }, installedVersion = "1.0.0")
        val result = checker.check(force = false) as CheckResult.Success
        assertTrue(result.wasDismissed)
        val manual = checker.check(force = true) as CheckResult.Success
        assertEquals("1.1.0", manual.update?.version)
    }
}
