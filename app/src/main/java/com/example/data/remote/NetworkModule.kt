package com.example.data.remote

import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * The app's single HTTP stack.
 *
 * Timeouts are short on purpose: the only thing on the network is a decorative weather card, and a
 * request that has not answered in a few seconds should give up and leave the card out rather than
 * hold a coroutine open while the player walks.
 */
object NetworkModule {

    private const val TIMEOUT_SECONDS = 8L

    // No reflective adapter factory: the DTOs carry @JsonClass(generateAdapter = true) and their
    // adapters are generated at build time by the Moshi KSP processor the module already runs.
    private val moshi: Moshi by lazy { Moshi.Builder().build() }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    private val openMeteo: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(OpenMeteoApi.BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    val openMeteoApi: OpenMeteoApi by lazy { openMeteo.create(OpenMeteoApi::class.java) }

    /** Same host and same client as the forecast; only the path differs. */
    val elevationApi: ElevationApi by lazy { openMeteo.create(ElevationApi::class.java) }
}
