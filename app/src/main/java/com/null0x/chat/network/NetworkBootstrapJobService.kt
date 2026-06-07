package com.null0x.chat.network

import android.app.job.JobParameters
import android.app.job.JobService

class NetworkBootstrapJobService : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        if (!BackgroundNetworkPreference.isEnabled(applicationContext)) {
            jobFinished(params, false)
            return false
        }
        ChatNodeManager.ensureBackgroundNetwork(applicationContext)
        jobFinished(params, false)
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        return true
    }
}
