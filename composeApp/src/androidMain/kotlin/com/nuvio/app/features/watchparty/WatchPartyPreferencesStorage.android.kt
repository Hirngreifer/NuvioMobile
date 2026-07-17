package com.nuvio.app.features.watchparty

import android.content.Context
import android.content.SharedPreferences
import com.nuvio.app.core.storage.ProfileScopedKey

internal actual object WatchPartyPreferencesStorage {
    private const val preferencesName = "nuvio_watch_party"
    private const val lastRoomCodeKey = "last_room_code"

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    }

    actual fun loadLastRoomCode(): String? =
        preferences?.getString(ProfileScopedKey.of(lastRoomCodeKey), null)

    actual fun saveLastRoomCode(code: String) {
        preferences
            ?.edit()
            ?.putString(ProfileScopedKey.of(lastRoomCodeKey), code)
            ?.apply()
    }

    actual fun clearLastRoomCode() {
        preferences
            ?.edit()
            ?.remove(ProfileScopedKey.of(lastRoomCodeKey))
            ?.apply()
    }
}
