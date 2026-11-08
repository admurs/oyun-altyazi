package com.admurs.oyunaltyazi.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HttpParserTest {

    private fun parse(raw: String) = HttpParser.read(raw.byteInputStream(Charsets.UTF_8))

    @Test
    fun `POST govdesini Turkce karakterlerle okur`() {
        val body = """{"type":"show","text":"Çığ düştü, şimdi ne yapacağız?"}"""
        val bytes = body.toByteArray(Charsets.UTF_8).size
        val request = parse("POST /subtitle HTTP/1.1\r\nHost: tv\r\nContent-Length: $bytes\r\n\r\n$body")!!
        assertEquals("POST", request.method)
        assertEquals("/subtitle", request.path)
        assertEquals(body, request.body)
    }

    @Test
    fun `GET isteginde govde bos ve sorgu dizesi atilir`() {
        val request = parse("GET /hello?x=1 HTTP/1.1\r\nHost: tv\r\n\r\n")!!
        assertEquals("GET", request.method)
        assertEquals("/hello", request.path)
        assertEquals("", request.body)
    }

    @Test
    fun `basliklar bitmeden kapanan baglanti null doner`() {
        assertNull(parse("GET /hello HTTP/1.1\r\nHost: tv\r\n"))
    }

    @Test
    fun `asiri buyuk govde reddedilir`() {
        assertNull(parse("POST /subtitle HTTP/1.1\r\nContent-Length: 999999\r\n\r\n{}"))
    }

    @Test
    fun `buyuk kucuk harf duyarsiz Content-Length`() {
        val request = parse("POST /subtitle HTTP/1.1\r\ncontent-length: 2\r\n\r\n{}")!!
        assertEquals("{}", request.body)
    }
}
