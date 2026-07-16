/*
 * Copyright 2026 Analitica Seguridad
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar.client

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.ExistingPeriodicWorkPolicy
import java.util.concurrent.TimeUnit

// --- VIGIA DE REINICIO (watchdog) ---
// En Android 12+ (S) el viejo AlarmManager de MainFragment.kt se desactiva
// (ver ALARM_MANAGER_INTERVAL), asi que si MagicOS/Android mata el proceso
// del servicio en segundo plano, nada lo revive hasta que el usuario abra
// la app o reinicie el telefono. Este Worker corre cada 15 min (el minimo
// que permite WorkManager) y, si el rastreo deberia estar activo, relanza
// el servicio. Es seguro llamarlo aunque el servicio ya este corriendo:
// simplemente vuelve a entregar onStartCommand, sin duplicar nada.
class TrackingWatchdogWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        if (prefs.getBoolean(MainFragment.KEY_STATUS, false)) {
            ContextCompat.startForegroundService(
                applicationContext, Intent(applicationContext, TrackingService::class.java))
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "tracking_watchdog"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<TrackingWatchdogWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        // Reintento casi inmediato (usado desde onTaskRemoved): no espera
        // los 15 min del ciclo periodico.
        fun scheduleImmediate(context: Context) {
            val request = OneTimeWorkRequestBuilder<TrackingWatchdogWorker>()
                .setInitialDelay(5, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
