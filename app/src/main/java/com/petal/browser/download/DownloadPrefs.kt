package com.petal.browser.download

/** SharedPreferences keys and defaults for the download engine. Read by the engine, written through SettingsRepository. */
object DownloadPrefs {
    const val KEY_PARALLEL_ENABLED = "sp_parallel_downloads"
    const val KEY_PARALLEL_CONNECTIONS = "sp_parallel_connections"
    const val KEY_SMART_ROUTING = "sp_smart_download_routing"
    const val KEY_MAX_CONCURRENT = "sp_dl_max_concurrent"
    const val KEY_WIFI_ONLY = "sp_dl_wifi_only"
    const val KEY_SPEED_LIMIT_KBPS = "sp_dl_speed_limit_kbps"
    const val KEY_VERIFY_CHECKSUM = "sp_dl_verify_checksum"
    const val KEY_POST_ACTIONS = "sp_dl_post_actions"
    const val KEY_OFFER_CLIPBOARD = "sp_dl_offer_clipboard"

    const val DEFAULT_MAX_CONCURRENT = 3
    const val MAX_CONCURRENT_LIMIT = 8
    const val GLOBAL_CONNECTION_CAP = 16
    const val PER_HOST_CONNECTION_CAP = 8
}
