package com.mobiled.android.repository

import android.content.Context
import com.mobiled.android.base.BaseRepository
import com.mobiled.android.base.network.Resource
import com.mobiled.android.base.network.Success
import com.mobiled.android.model.StaticPageResult

class HelpRepository(context: Context) : BaseRepository(context) {
    fun getContent(): Resource<StaticPageResult> {
        return Success<StaticPageResult>(StaticPageResult(1, "Success", readHtmlFromAssets("help.html")))
    }

}
