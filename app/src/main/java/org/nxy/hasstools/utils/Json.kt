package org.nxy.hasstools.utils

import kotlinx.serialization.json.Json

val Json = Json {
    classDiscriminator = "__type__"
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
    encodeDefaults = true
}
