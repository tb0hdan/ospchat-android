package com.ospchat.android

import android.app.Application
import com.ospchat.android.service.CallServiceController
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class OSPChatApp : Application() {
    @Inject lateinit var callServiceController: CallServiceController

    override fun onCreate() {
        super.onCreate()
        // Start the call-lifecycle observer so the mic FG service is started
        // / stopped automatically as calls transition through CONNECTING /
        // CONNECTED / ENDED.
        callServiceController.start()
    }
}
