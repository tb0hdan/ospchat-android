package com.ospchat.android.seed

import javax.inject.Qualifier

/**
 * Marks the [io.ktor.client.HttpClient] used for Seed Mode (GitHub API
 * fetches and large-file downloads). Distinct from the default messaging
 * client, which has a short request timeout that would kill long
 * streams.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SeedHttpClient
