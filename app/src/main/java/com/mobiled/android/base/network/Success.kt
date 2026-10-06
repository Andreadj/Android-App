package com.mobiled.android.base.network

import androidx.annotation.Keep

@Keep
class Success<T>(var value: T) : Resource<T>(com.mobiled.android.base.network.Status.SUCCESS)