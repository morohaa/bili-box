package io.github.bilibox.data.localvideo

import android.content.Context

internal class LocalVideoSettingsStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("local_video_display", Context.MODE_PRIVATE)

    fun load(): LocalVideoSettings = LocalVideoSettings(
        mode = preferences.enumValue("mode", LocalVideoViewMode.ALL_FOLDERS),
        layout = preferences.enumValue("layout", LocalVideoLayout.LIST),
        sort = preferences.enumValue("sort", LocalVideoSort.TITLE),
        ascending = preferences.getBoolean("ascending", true),
        fields = LocalVideoDisplayFields(
            thumbnail = preferences.getBoolean("field_thumbnail", true),
            duration = preferences.getBoolean("field_duration", true),
            extension = preferences.getBoolean("field_extension", true),
            played = preferences.getBoolean("field_played", false),
            resolution = preferences.getBoolean("field_resolution", false),
            frameRate = preferences.getBoolean("field_frame_rate", false),
            path = preferences.getBoolean("field_path", false),
            size = preferences.getBoolean("field_size", true),
            date = preferences.getBoolean("field_date", true),
        ),
        folderFields = LocalVideoFolderFields(
            size = preferences.getBoolean("folder_field_size", true),
            date = preferences.getBoolean("folder_field_date", true),
            duration = preferences.getBoolean("folder_field_duration", true),
            status = preferences.getBoolean("folder_field_status", true),
        ),
        durationOnThumbnail = preferences.getBoolean("duration_on_thumbnail", true),
        showHidden = preferences.getBoolean("show_hidden", false),
        recognizeNomedia = preferences.getBoolean("recognize_nomedia", true),
    )

    fun save(settings: LocalVideoSettings) {
        preferences.edit()
            .putString("mode", settings.mode.name)
            .putString("layout", settings.layout.name)
            .putString("sort", settings.sort.name)
            .putBoolean("ascending", settings.ascending)
            .putBoolean("field_thumbnail", settings.fields.thumbnail)
            .putBoolean("field_duration", settings.fields.duration)
            .putBoolean("field_extension", settings.fields.extension)
            .putBoolean("field_played", settings.fields.played)
            .putBoolean("field_resolution", settings.fields.resolution)
            .putBoolean("field_frame_rate", settings.fields.frameRate)
            .putBoolean("field_path", settings.fields.path)
            .putBoolean("field_size", settings.fields.size)
            .putBoolean("field_date", settings.fields.date)
            .putBoolean("folder_field_size", settings.folderFields.size)
            .putBoolean("folder_field_date", settings.folderFields.date)
            .putBoolean("folder_field_duration", settings.folderFields.duration)
            .putBoolean("folder_field_status", settings.folderFields.status)
            .putBoolean("duration_on_thumbnail", settings.durationOnThumbnail)
            .putBoolean("show_hidden", settings.showHidden)
            .putBoolean("recognize_nomedia", settings.recognizeNomedia)
            .apply()
    }
}

private inline fun <reified T : Enum<T>> android.content.SharedPreferences.enumValue(
    key: String,
    fallback: T,
): T {
    val raw = getString(key, null) ?: return fallback
    return runCatching { enumValueOf<T>(raw) }.getOrDefault(fallback)
}
