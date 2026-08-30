/*
 * Copyright (c) 2020 QuroAI <dev@quro.ai>
 * All Rights Reserved.
 */

package com.ai.assistance.quro.file.util

import android.location.Address
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

val isGeocoderPresent by lazy { Geocoder.isPresent() }

@Throws(IOException::class)
suspend fun Geocoder.awaitGetFromLocation(
    latitude: Double,
    longitude: Double,
    maxResults: Int
): List<Address> =
    withContext(Dispatchers.IO) {
        getFromLocation(latitude, longitude, maxResults)
            ?: throw IOException(NullPointerException())
    }
