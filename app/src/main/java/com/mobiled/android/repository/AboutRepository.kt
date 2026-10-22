package com.mobiled.android.repository

import android.content.Context
import com.mobiled.android.BuildConfig
import com.mobiled.android.base.BaseRepository
import com.mobiled.android.base.network.Resource
import com.mobiled.android.base.network.Success
import com.mobiled.android.model.StaticPageResult

class AboutRepository(context: Context) : BaseRepository(context) {
    fun getContent(): Resource<StaticPageResult> {
        val content = readHtmlFromAssets("aboutus.html")
            .replace("{{APP_VERSION}}", BuildConfig.VERSION_NAME)
        return Success(StaticPageResult(1, "Success", content))
    }

}
