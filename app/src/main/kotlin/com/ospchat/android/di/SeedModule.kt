package com.ospchat.android.di

import android.content.Context
import com.ospchat.android.seed.GitHubReleaseClient
import com.ospchat.android.seed.SeedCache
import com.ospchat.android.seed.SeedHttpClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import javax.inject.Singleton

/**
 * Hilt graph for Seed Mode. A dedicated [HttpClient] (qualified
 * `@SeedHttpClient`) is needed because the default network module's client
 * has a 5 s request timeout — fine for peer message POSTs, fatal for a
 * 140 MB DMG download.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object SeedModule {
    @Provides
    @Singleton
    fun provideSeedCache(
        @ApplicationContext context: Context,
    ): SeedCache = SeedCache(context.filesDir)

    @Provides
    @Singleton
    @SeedHttpClient
    fun provideSeedHttpClient(): HttpClient =
        HttpClient(CIO) {
            // No requestTimeoutMillis — large downloads stream for minutes.
            // Long connect timeout so GitHub redirects to the CDN have room
            // to establish on flaky links.
            install(HttpTimeout) {
                connectTimeoutMillis = 10_000L
                socketTimeoutMillis = 30_000L
            }
            // Follow `Location` headers — GitHub redirects asset downloads to
            // `objects.githubusercontent.com`, which is on a different host
            // than `api.github.com`.
            followRedirects = true
            engine {
                requestTimeout = 0L
            }
        }

    @Provides
    @Singleton
    fun provideGitHubReleaseClient(
        @SeedHttpClient client: HttpClient,
    ): GitHubReleaseClient = GitHubReleaseClient(client)
}
