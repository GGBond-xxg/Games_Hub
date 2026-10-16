package com.bond.md3elauncher.system

import com.bond.md3elauncher.data.CoverCandidate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class ArtworkSearchResult(
    val candidates: List<CoverCandidate> = emptyList(),
    val report: String = "",
    val errorKey: String? = null
)

internal data class ArtworkSearchState(val loading: Boolean = false, val result: ArtworkSearchResult = ArtworkSearchResult())

/** Only the latest query/type may publish, even if a provider ignores cancellation. */
internal class ArtworkSearchController(
    private val scope: CoroutineScope,
    private val search: suspend (String, CoverArtworkType) -> ArtworkSearchResult
) {
    private val mutableState = MutableStateFlow(ArtworkSearchState())
    val state = mutableState.asStateFlow()
    private var generation = 0L
    private var job: Job? = null

    fun search(query: String, type: CoverArtworkType): Job {
        val current = ++generation
        job?.cancel()
        mutableState.value = ArtworkSearchState(loading = true)
        return scope.launch {
            val result = try { search.invoke(query, type) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { ArtworkSearchResult(errorKey = "cover.report.network_failed") }
            if (current == generation) mutableState.value = ArtworkSearchState(result = result)
        }.also { job = it }
    }

    fun cancel() {
        generation++
        job?.cancel()
    }
}
