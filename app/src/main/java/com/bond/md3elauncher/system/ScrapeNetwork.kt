package com.bond.md3elauncher.system

import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.*

/** Android's native DNS can outlive HttpURLConnection.connectTimeout and ignore interruption.
 * Bound the caller's whole request, including DNS, with a small bounded worker pool.
 * A stuck resolver cannot retain the UI job or create an unlimited number of threads.
 */
internal object ScrapeNetwork {
    private val executor = ThreadPoolExecutor(4, 4, 30L, TimeUnit.SECONDS, ArrayBlockingQueue(32),
        ThreadFactory { task -> Thread(task, "GameHub-scrape-network").apply { isDaemon = true } }).apply { allowCoreThreadTimeOut(true) }

    fun <T> request(timeoutMillis: Long = 20_000, block: () -> T): T {
        val future = try { executor.submit(Callable(block)) }
        catch (_: RejectedExecutionException) { throw IOException("Network requests are still pending") }
        try { return future.get(timeoutMillis, TimeUnit.MILLISECONDS) }
        catch (_: TimeoutException) { throw SocketTimeoutException("Scraper request timed out") }
        catch (failure: ExecutionException) { throw failure.cause ?: failure }
        finally { if (!future.isDone) future.cancel(true) }
    }
}
