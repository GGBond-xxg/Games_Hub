package com.bond.md3elauncher.system

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ArtworkSearchControllerTest {
    @Test(timeout = 5000) fun lateUncooperativeProviderCannotReplaceLatestResult() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val controller = ArtworkSearchController(this) { query, type ->
            if (query == "old") withContext(NonCancellable) { started.complete(Unit); release.await() }
            ArtworkSearchResult(report = "$query/$type")
        }
        val old = controller.search("old", CoverArtworkType.BOX_ART)
        started.await()
        controller.search("new", CoverArtworkType.SCREENSHOT).join()
        assertFalse(controller.state.value.loading)
        assertEquals("new/SCREENSHOT", controller.state.value.result.report)
        release.complete(Unit)
        old.join()
        assertEquals("new/SCREENSHOT", controller.state.value.result.report)
    }

    @Test(timeout = 5000) fun replacementCancelsWorkAndClearsPreviousDiagnostics() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val controller = ArtworkSearchController(this) { query, _ ->
            if (query == "slow") {
                started.complete(Unit)
                try { awaitCancellation() } finally { cancelled.complete(Unit) }
            }
            ArtworkSearchResult(report = query)
        }
        controller.search("first", CoverArtworkType.BOX_ART).join()
        controller.search("slow", CoverArtworkType.SCREENSHOT)
        assertEquals("", controller.state.value.result.report)
        assertTrue(controller.state.value.loading)
        started.await()
        controller.search("latest", CoverArtworkType.BOX_ART).join()
        cancelled.await()
        assertEquals("latest", controller.state.value.result.report)
    }

    @Test(timeout = 5000) fun leavingSearchDiscardsLateResult() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val controller = ArtworkSearchController(this) { _, _ ->
            withContext(NonCancellable) { started.complete(Unit); release.await() }
            ArtworkSearchResult(report = "discard me")
        }
        val job = controller.search("old", CoverArtworkType.BOX_ART)
        started.await()
        controller.cancel()
        release.complete(Unit)
        job.join()
        assertEquals("", controller.state.value.result.report)
    }

    @Test(timeout = 5000) fun failedSearchCanBeRetried() = runBlocking {
        val controller = ArtworkSearchController(this) { query, _ ->
            if (query == "fail") throw java.io.IOException("provider failed")
            ArtworkSearchResult(report = query)
        }
        controller.search("fail", CoverArtworkType.BOX_ART).join()
        assertFalse(controller.state.value.loading)
        assertEquals("cover.report.network_failed", controller.state.value.result.errorKey)
        controller.search("recovered", CoverArtworkType.BOX_ART).join()
        assertNull(controller.state.value.result.errorKey)
        assertEquals("recovered", controller.state.value.result.report)
    }
}
