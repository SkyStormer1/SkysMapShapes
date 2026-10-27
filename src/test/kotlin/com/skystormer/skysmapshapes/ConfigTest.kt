package com.skystormer.skysmapshapes

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class ConfigTest {

    private fun number(key: String, default: Int) =
        Setting(key, default, { it.asInt }, { JsonPrimitive(it) }, { it.coerceIn(0, 10) })

    @Test
    fun defaultsComeFromTheDeclarations() {
        assertEquals(0.15f, Config.default(Config::fillOpacity))
        assertEquals(Colours.AQUA.argb, Config.default(Config::spawnProofColour))
        assertEquals(Config.HIGHEST_Y, Config.default(Config::beaconMaxY))
        assertEquals(null, Config.default(Config::spawnProofBlocks))
    }

    @Test
    fun aSettingIsReadKeptInBoundsAndWrittenBack() {
        val setting = number("n", 3)
        setting.load(JsonParser.parseString("""{"n": 42}""").asJsonObject)
        val out = JsonObject()
        setting.save(out)
        assertEquals(10, out.get("n").asInt)
    }

    @Test
    fun aValueThatCannotBeReadKeepsWhatItHad() {
        val setting = number("n", 3)
        setting.load(JsonParser.parseString("""{"n": "lots"}""").asJsonObject)
        setting.load(JsonParser.parseString("""{"other": 5}""").asJsonObject)
        val out = JsonObject()
        setting.save(out)
        assertEquals(3, out.get("n").asInt)
    }

    @Test
    fun aSettingWithNothingToWriteIsLeftOut() {
        val setting = Setting<String?>("s", null, { it.asString }, { it?.let(::JsonPrimitive) }, { it })
        val out = JsonObject()
        setting.save(out)
        assertFalse(out.has("s"))
    }
}
