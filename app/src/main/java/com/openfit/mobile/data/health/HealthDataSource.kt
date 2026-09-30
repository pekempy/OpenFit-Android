package com.openfit.mobile.data.health

import com.openfit.mobile.model.HealthSnapshotBundle

/** Either connector - Health Connect (on-device) or the Google Health API
 * v4 (cloud OAuth) - produces the same [HealthSnapshotBundle] shape, so the
 * UI layer never needs to know which one is active. */
interface HealthDataSource {
    suspend fun sync(selectedDate: String): HealthSnapshotBundle
    suspend fun isConnected(): Boolean
}
