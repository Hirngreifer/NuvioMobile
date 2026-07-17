package com.nuvio.app.features.watchparty

import com.nuvio.app.core.storage.ProfileScopedKey
import platform.Foundation.NSUserDefaults

internal actual object WatchPartyPreferencesStorage {
    private const val lastRoomCodeKey = "nuvio_watch_party_last_room_code"

    actual fun loadLastRoomCode(): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(ProfileScopedKey.of(lastRoomCodeKey))

    actual fun saveLastRoomCode(code: String) {
        NSUserDefaults.standardUserDefaults.setObject(code, ProfileScopedKey.of(lastRoomCodeKey))
    }

    actual fun clearLastRoomCode() {
        NSUserDefaults.standardUserDefaults.removeObjectForKey(ProfileScopedKey.of(lastRoomCodeKey))
    }
}
