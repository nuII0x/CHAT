package com.null0x.chat.network

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context

object NetworkBootstrapScheduler {
    private const val BOOTSTRAP_JOB_ID = 5000

    fun schedule(context: Context) {
        val appContext = context.applicationContext
        val scheduler = appContext.getSystemService(JobScheduler::class.java) ?: return
        val component = ComponentName(appContext, NetworkBootstrapJobService::class.java)
        val job = JobInfo.Builder(BOOTSTRAP_JOB_ID, component)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPersisted(true)
            .setBackoffCriteria(30_000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
            .build()
        scheduler.schedule(job)
    }
}
