package com.xxxx.emby_tv.data.remote

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.xxxx.emby_tv.data.model.NameGuidPairDto
import java.lang.reflect.Type

/**
 * Emby sometimes sends studio and genre ids as numbers. Gson must accept both.
 */
object EmbyJson {
    val gson: Gson = GsonBuilder()
        .registerTypeAdapter(NameGuidPairDto::class.java, NameGuidPairDeserializer())
        .create()
}

private class NameGuidPairDeserializer : JsonDeserializer<NameGuidPairDto> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext,
    ): NameGuidPairDto {
        val obj = json.asJsonObject
        val name = obj.get("Name")?.takeUnless { it.isJsonNull }?.asString
        val idElement = obj.get("Id")
        val id = when {
            idElement == null || idElement.isJsonNull -> null
            idElement.isJsonPrimitive && idElement.asJsonPrimitive.isNumber -> idElement.asLong.toString()
            idElement.isJsonPrimitive -> idElement.asString
            else -> null
        }
        return NameGuidPairDto(name = name, id = id)
    }
}
