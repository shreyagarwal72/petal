package com.petal.browser.account.sync

import android.app.job.JobParameters
import android.app.job.JobService

/** Runs the periodic background sync scheduled by [PetalFolderSync]. */
class PetalSyncJobService : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        PetalFolderSync.init(applicationContext)
        PetalFolderSync.syncFromJob(applicationContext) { jobFinished(params, false) }
        return true // work continues on a background thread until jobFinished
    }

    override fun onStopJob(params: JobParameters): Boolean = true // retry later if the system stops us
}
