package com.ryou.appryous.util

import com.ryou.appryous.data.repository.BackendRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

/**
 * Poll status scan backend secara periodik.
 *
 * Mirror dari backoff logic di app.js epsilon:
 *   15s normal → 30s setelah error pertama → 60s setelah error berikutnya
 *
 * Emit [ScanPollState] ke collector (biasanya ViewModel).
 */
object ScanPoller {

    sealed class ScanPollState {
        data object Idle    : ScanPollState()
        data object Running : ScanPollState()
        data class  Progress(val msg: String) : ScanPollState()
        data object Done    : ScanPollState()
        data class  Error(val msg: String)    : ScanPollState()
    }

    // Delay intervals (mirror dari epsilon backoff)
    private const val INTERVAL_NORMAL_MS  = 15_000L
    private const val INTERVAL_ERROR1_MS  = 30_000L
    private const val INTERVAL_ERROR2_MS  = 60_000L
    private const val MAX_POLLS           = 120  // max 30 menit polling

    /**
     * Flow yang poll /api/scan/status sampai scan selesai atau max polls tercapai.
     *
     * @param repo BackendRepository
     * @param onDone Callback saat scan selesai (refresh library)
     */
    fun pollFlow(repo: BackendRepository): Flow<ScanPollState> = flow {
        var errorCount = 0
        var pollCount  = 0

        emit(ScanPollState.Running)

        while (pollCount < MAX_POLLS) {
            val interval = when {
                errorCount == 0 -> INTERVAL_NORMAL_MS
                errorCount == 1 -> INTERVAL_ERROR1_MS
                else            -> INTERVAL_ERROR2_MS
            }
            delay(interval)

            val result = withContext(Dispatchers.IO) { repo.getScanStatus() }

            if (result.isFailure) {
                errorCount++
                emit(ScanPollState.Error("Polling error #$errorCount: ${result.exceptionOrNull()?.message}"))
                if (errorCount >= 5) break  // stop setelah 5 error berturut-turut
                continue
            }

            errorCount = 0
            val status = result.getOrNull() ?: continue

            if (status.running) {
                emit(ScanPollState.Progress(status.progress))
            } else {
                emit(ScanPollState.Done)
                break
            }

            pollCount++
        }
    }
}
