package com.ospchat.android.di

import android.content.Context
import android.net.nsd.NsdManager
import android.net.wifi.WifiManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideNsdManager(@ApplicationContext context: Context): NsdManager =
        context.getSystemService(Context.NSD_SERVICE) as NsdManager

    @Provides
    @Singleton
    fun provideWifiManager(@ApplicationContext context: Context): WifiManager =
        context.getSystemService(Context.WIFI_SERVICE) as WifiManager
}
