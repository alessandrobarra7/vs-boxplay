package com.boxplay.multitrack.data.json

import org.junit.Assert.assertEquals
import org.junit.Test

class JsonRoundTripTest {

    @Test
    fun objectWithNestedArrayRoundTrips() {
        val track = JsonValue.Obj()
            .put("name", "Bateria")
            .put("volume", 0.8f)
            .put("muted", false)
            .put("offset", 350_000L)
            .put("routing", null as String?)

        val wrapper = JsonValue.Obj().put("tracks", JsonValue.Arr.of(listOf(track)))

        val json = writeJson(wrapper)
        val parsed = parseJson(json) as JsonValue.Obj
        val parsedTrack = parsed.arr("tracks")!!.items.first() as JsonValue.Obj

        assertEquals("Bateria", parsedTrack.str("name"))
        assertEquals(0.8f, parsedTrack.float("volume"))
        assertEquals(false, parsedTrack.bool("muted"))
        assertEquals(350_000L, parsedTrack.long("offset"))
    }

    @Test
    fun specialCharactersAreEscapedAndRestored() {
        val original = "Projeto \"especial\" — ção\nnova linha\ttab"
        val obj = JsonValue.Obj().put("name", original)
        val parsed = parseJson(writeJson(obj)) as JsonValue.Obj
        assertEquals(original, parsed.str("name"))
    }

    @Test(expected = JsonParseException::class)
    fun unterminatedObjectThrows() {
        parseJson("{\"a\":1")
    }

    @Test(expected = JsonParseException::class)
    fun trailingGarbageThrows() {
        parseJson("{}garbage")
    }

    @Test(expected = JsonParseException::class)
    fun unterminatedStringThrows() {
        parseJson("{\"a\": \"sem fechar")
    }
}
