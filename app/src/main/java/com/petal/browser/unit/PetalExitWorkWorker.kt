/*
 * MIT License
 * Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.unit

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.petal.browser.logger.PetalAppLogger

/**
 * PetalExitWorkWorker
 * ─────────────────────────────────────────────────────────────────────────
 * Background worker to guarantee execution of heavy exit cleanup and backup
 * tasks even if the activity or application process terminates unexpectedly.
 */
class PetalExitWorkWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {

    override fun doWork(): Result {
        return try {
            val sp = PreferenceManager.getDefaultSharedPreferences(applicationContext)
            val clearQuit = sp.getBoolean("sp_clear_quit", false) || sp.getBoolean("sp_clear_on_exit", false)
            if (clearQuit) {
                PetalBrowserUtils.performClearOnExitInternal(applicationContext)
            }
            if (sp.getBoolean("sp_backup_quit", false)) {
                PetalBackupManager.backupData(applicationContext, 0)
            }
            Result.success()
        } catch (t: Throwable) {
            PetalAppLogger.w(TAG, "Error in PetalExitWorkWorker", t)
            Result.failure()
        }
    }

    companion object {
        private const val TAG = "PetalExitWorkWorker"
        const val WORK_NAME = "petal_exit_work"
    }
}
