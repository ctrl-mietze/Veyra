package ctrl.mietze.veyraroot

/**
 * Legacy update metadata type kept temporarily so existing UI state compiles.
 *
 * Veyra Root currently has no in-app update endpoint. The Root My Galaxy Next
 * update path was intentionally removed and no repository is contacted here.
 */
data class UpdateInfo(
    val versionName: String,
    val apkUrl: String?,
    val releaseUrl: String,
)
