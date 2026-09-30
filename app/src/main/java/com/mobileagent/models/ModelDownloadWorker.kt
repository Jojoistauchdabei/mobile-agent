package com.mobileagent.models

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

object ModelDownloadContract {
    const val KEY_MODEL_ID = "model_id"
    const val PROGRESS_DOWNLOADED = "downloaded"
    const val PROGRESS_TOTAL = "total"

    fun tagFor(modelId: String): String = "model-download-$modelId"
    fun uniqueNameFor(modelId: String): String = "model-download-$modelId"
}

/** Nur Netzwerkfehler werden wiederholt; Pruef-/Dateifehler sind endgueltig. */
object ModelDownloadPolicy {
    fun shouldRetry(error: Throwable): Boolean {
        val message = error.message.orEmpty()
        return when {
            "SHA-256" in message -> false
            "Unvollst" in message -> false
            "Umbenennen" in message -> false
            error is IOException -> true
            else -> false
        }
    }
}

object ModelDownloadScheduler {
    fun requestFor(spec: ModelSpec) = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
        .setInputData(workDataOf(ModelDownloadContract.KEY_MODEL_ID to spec.id))
        .addTag(ModelDownloadContract.tagFor(spec.id))
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresStorageNotLow(true)
                .build(),
        )
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
        .build()

    fun enqueue(context: Context, spec: ModelSpec) {
        // REPLACE statt KEEP: Die UI zeigt "Laden" nur ohne laufende Arbeit, ein
        // abgeschlossener (fehlerhafter) Lauf darf einen Neustart nicht blockieren.
        WorkManager.getInstance(context).enqueueUniqueWork(
            ModelDownloadContract.uniqueNameFor(spec.id),
            ExistingWorkPolicy.REPLACE,
            requestFor(spec),
        )
    }

    fun cancel(context: Context, spec: ModelSpec) {
        WorkManager.getInstance(context).cancelUniqueWork(
            ModelDownloadContract.uniqueNameFor(spec.id),
        )
        ModelDownloadNotifications(context.applicationContext).cancel(spec)
    }
}

/**
 * Laedt ein Modell im Hintergrund mit Fortschritts-Benachrichtigung.
 * Ueberlebt das Schliessen der App; Abschluss/Fehler melden sich per Notification.
 */
class ModelDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = coroutineScope {
        val modelId = inputData.getString(ModelDownloadContract.KEY_MODEL_ID)
            ?: return@coroutineScope Result.failure()
        val spec = DefaultModels.all.firstOrNull { it.id == modelId }
            ?: return@coroutineScope Result.failure()
        val models = ModelManager(applicationContext)
        val notifications = ModelDownloadNotifications(applicationContext)
        notifications.ensureChannel()
        setForeground(notifications.foregroundInfo(spec, 0, -1))

        val downloaded = AtomicLong(0)
        val total = AtomicLong(-1)
        val download = async {
            models.download(spec) { bytes, expected ->
                // Nicht-suspendierbarer Callback: nur Zaehler pflegen, das Melden
                // uebernimmt die Polling-Schleife unten.
                downloaded.set(bytes)
                total.set(expected)
            }
        }
        try {
            while (download.isActive) {
                val current = downloaded.get()
                val expected = total.get()
                setProgress(
                    workDataOf(
                        ModelDownloadContract.PROGRESS_DOWNLOADED to current,
                        ModelDownloadContract.PROGRESS_TOTAL to expected,
                    ),
                )
                setForeground(notifications.foregroundInfo(spec, current, expected))
                delay(1_000)
            }
            download.await().getOrThrow()
            notifications.showCompleted(spec)
            Result.success()
        } catch (error: CancellationException) {
            notifications.cancel(spec)
            throw error
        } catch (error: Throwable) {
            notifications.showFailed(spec, error.message)
            if (ModelDownloadPolicy.shouldRetry(error)) Result.retry() else Result.failure()
        }
    }
}
