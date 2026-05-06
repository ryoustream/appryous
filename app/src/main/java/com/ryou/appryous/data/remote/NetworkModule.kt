package com.ryou.appryous.data.remote

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Singleton network module.
 *
 * Dua Retrofit instance:
 *  1. [gistRetrofit]    → base URL GitHub Gist raw (statis)
 *  2. [backendRetrofit] → base URL tunnel Cloudflare (dinamis, di-update setelah resolve)
 *
 * OkHttp yang sama dipakai keduanya + ExoPlayer datasource (via media3-datasource-okhttp).
 */
object NetworkModule {

    // Timeout values
    private const val CONNECT_TIMEOUT = 15L
    private const val READ_TIMEOUT    = 30L
    private const val WRITE_TIMEOUT   = 15L

    // Base URLs
    private const val GIST_RAW_BASE   = "https://gist.githubusercontent.com/"

    val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    /** OkHttp client bersama — dipakai Retrofit + ExoPlayer */
    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
            // Retry sekali saat network error (tunnel restart)
            .retryOnConnectionFailure(true)
            .addInterceptor(buildLoggingInterceptor())
            .build()
    }

    private fun buildLoggingInterceptor() = HttpLoggingInterceptor().apply {
        level = if (com.ryou.appryous.BuildConfig.DEBUG)
            HttpLoggingInterceptor.Level.BASIC
        else
            HttpLoggingInterceptor.Level.NONE
    }

    // ── Gist Retrofit (base URL statis) ──────────────────────────────────────

    private val gistRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(GIST_RAW_BASE)
            .client(
                okHttpClient.newBuilder()
                    .connectTimeout(8, TimeUnit.SECONDS)
                    .readTimeout(8, TimeUnit.SECONDS)
                    .build()
            )
            .addConverterFactory(ScalarsConverterFactory)   // plain text response
            .build()
    }

    val gistApi: GistApi by lazy { gistRetrofit.create(GistApi::class.java) }

    // ── Backend Retrofit (base URL dinamis) ──────────────────────────────────

    /**
     * Buat instance BackendApi baru dengan base URL yang sudah di-resolve.
     * Dipanggil oleh [GistRepository] setelah URL tunnel diketahui.
     *
     * @param baseUrl URL tunnel Cloudflare, contoh "https://xxx.trycloudflare.com"
     */
    fun createBackendApi(baseUrl: String): BackendApi {
        val normalizedBase = baseUrl.trimEnd('/') + "/"
        return Retrofit.Builder()
            .baseUrl(normalizedBase)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(BackendApi::class.java)
    }

    // ── Scalars converter untuk plain text (Gist raw) ────────────────────────

    /**
     * Converter ringan untuk response plain text String.
     * Tidak butuh library scalars — pakai custom converter.
     */
    private object ScalarsConverterFactory : retrofit2.Converter.Factory() {
        override fun responseBodyConverter(
            type: java.lang.reflect.Type,
            annotations: Array<out Annotation>,
            retrofit: Retrofit
        ): retrofit2.Converter<okhttp3.ResponseBody, *>? {
            return if (type == String::class.java) {
                retrofit2.Converter<okhttp3.ResponseBody, String> { body -> body.string() }
            } else null
        }
    }
}
