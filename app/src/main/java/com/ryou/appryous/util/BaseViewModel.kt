package com.ryou.appryous.util

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ryou.appryous.data.repository.BackendException
import com.ryou.appryous.data.repository.GistException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Base ViewModel dengan standard loading/error state.
 * Semua ViewModel di app extend ini.
 */
abstract class BaseViewModel : ViewModel() {

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    protected fun setLoading(v: Boolean) { _loading.value = v }
    protected fun setError(msg: String?)  { _error.value = msg }
    fun clearError()                       { _error.value = null }

    /**
     * Launch coroutine dengan auto loading state dan error handling.
     *
     * @param showLoading Set loading=true selama execute
     * @param onError Override error handling (opsional)
     * @param block Coroutine block
     */
    protected fun launchSafe(
        showLoading: Boolean = true,
        onError: ((String) -> Unit)? = null,
        block: suspend CoroutineScope.() -> Unit
    ) {
        viewModelScope.launch {
            if (showLoading) setLoading(true)
            try {
                block()
            } catch (e: GistException) {
                val msg = "Gagal resolve URL backend:\n${e.message}"
                onError?.invoke(msg) ?: setError(msg)
            } catch (e: BackendException) {
                val msg = when (e.statusCode) {
                    404  -> "Data tidak ditemukan di server."
                    408  -> "Server timeout — pastikan Termux + tunnel berjalan."
                    500  -> "Server error: ${e.message}"
                    else -> e.message ?: "Terjadi kesalahan."
                }
                onError?.invoke(msg) ?: setError(msg)
            } catch (e: Exception) {
                val msg = e.message ?: "Error tidak diketahui."
                onError?.invoke(msg) ?: setError(msg)
            } finally {
                if (showLoading) setLoading(false)
            }
        }
    }

    /**
     * Unwrap Result<T> dan handle error secara otomatis.
     * Return value jika sukses, null jika gagal (error di-set ke _error).
     */
    protected fun <T> Result<T>.unwrap(onError: ((String) -> Unit)? = null): T? {
        return if (isSuccess) {
            getOrNull()
        } else {
            val msg = exceptionOrNull()?.message ?: "Error tidak diketahui."
            onError?.invoke(msg) ?: setError(msg)
            null
        }
    }
}
