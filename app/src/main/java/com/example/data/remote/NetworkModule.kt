package com.example.data.remote

import com.example.BuildConfig
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * The app's HTTP stacks.
 *
 * Two clients, because the two things on the network have opposite needs. Timeouts on the forecast
 * are short on purpose: it feeds a decorative weather card, and a request that has not answered in a
 * few seconds should give up and leave the card out rather than hold a coroutine open while the
 * player walks. The OpenStreetMap services are the other way round - Overpass routinely takes ten or
 * twenty seconds to answer a query, nothing on screen is waiting for it, and giving up early would
 * mean having asked a shared service for work and then thrown the answer away.
 */
object NetworkModule {

    private const val TIMEOUT_SECONDS = 8L

    /** Clears Overpass' own 25 s query timeout, so a slow answer is waited for rather than wasted. */
    private const val OSM_TIMEOUT_SECONDS = 40L

    /**
     * How the app introduces itself to the OpenStreetMap services.
     *
     * Not optional politeness: Nominatim's usage policy requires an identifying User-Agent and
     * blocks clients that send a generic one, and Overpass uses it to tell a misbehaving application
     * from a browser. It should carry a contact address before the app is distributed widely - a
     * blocked User-Agent with nobody to write to is how an app stays blocked.
     */
    private val userAgent =
        "StompWalking/${BuildConfig.VERSION_NAME} (Android; ${BuildConfig.APPLICATION_ID})"

    // No reflective adapter factory: the DTOs carry @JsonClass(generateAdapter = true) and their
    // adapters are generated at build time by the Moshi KSP processor the module already runs.
    private val moshi: Moshi by lazy { Moshi.Builder().build() }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    /** Shares the connection pool and dispatcher with [client]; only the timeouts and header differ. */
    private val osmClient: OkHttpClient by lazy {
        client.newBuilder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(OSM_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(OSM_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", userAgent)
                        .build()
                )
            }
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

    val overpassApi: OverpassApi by lazy {
        Retrofit.Builder()
            .baseUrl(OverpassApi.BASE_URL)
            .client(osmClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(OverpassApi::class.java)
    }

    val nominatimApi: NominatimApi by lazy {
        Retrofit.Builder()
            .baseUrl(NominatimApi.BASE_URL)
            .client(osmClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(NominatimApi::class.java)
    }
}
