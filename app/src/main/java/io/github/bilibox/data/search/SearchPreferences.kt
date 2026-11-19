package io.github.bilibox.data.search

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

internal class SearchPreferences(private val preferences: SharedPreferences) {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences("search_preferences", Context.MODE_PRIVATE),
    )

    var anonymousSearch: Boolean
        get() = preferences.getBoolean("anonymous_search", false)
        set(value) = preferences.edit { putBoolean("anonymous_search", value) }
}
