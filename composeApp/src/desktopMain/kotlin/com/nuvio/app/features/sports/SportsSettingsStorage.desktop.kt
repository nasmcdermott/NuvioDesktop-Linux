package com.nuvio.app.features.sports

import com.nuvio.app.core.storage.DesktopStorage
import com.nuvio.app.core.storage.ProfileScopedKey

/**
 * The Sports settings, in `nuvio_sports.properties` under the active account folder.
 *
 * Profile-scoped like `nuvio_iptv`, so two viewers on one machine keep their own follows and channel
 * picks, and Settings > Backup carries every profile's copy without any Sports-specific backup code.
 */
internal actual object SportsSettingsStorage {
    private val store = DesktopStorage.store(SPORTS_STORE_NAME)

    actual fun loadPayload(): String? = store.getString(ProfileScopedKey.of("sports"))

    actual fun savePayload(payload: String) {
        store.putString(ProfileScopedKey.of("sports"), payload)
    }
}
