package com.example.chariiptv

import com.example.chariiptv.util.Constants
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConstantsTest {

    @Test
    fun isForeignOrBlocked_blocksGeoAndForeignTokens() {
        assertTrue(Constants.isForeignOrBlocked("Teledeporte GEO", "https://rtvelivestream.rtve.es/live.m3u8"))
        assertTrue(Constants.isForeignOrBlocked("Canal Sur (geo-blocked)", "https://cdn.validstream.tv/live.m3u8"))
        assertTrue(Constants.isForeignOrBlocked("Canal Alemán (Germany)", "https://cdn.validstream.tv/live.m3u8"))
    }

    @Test
    fun isForeignOrBlocked_preservesValidDocumentaryAndStandardChannels() {
        assertFalse(Constants.isForeignOrBlocked("Nat Geo Wild (1080p)", "https://cdn.validstream.tv/live.m3u8"))
        assertFalse(Constants.isForeignOrBlocked("National Geographic", "https://cdn.validstream.tv/live.m3u8"))
        assertFalse(Constants.isForeignOrBlocked("Teledeporte", "https://rtvelivestream.rtve.es/live.m3u8"))
    }

    @Test
    fun isTestOrDummyChannel_blocksTestAndDummyChannels() {
        assertTrue(Constants.isTestOrDummyChannel("Canal Prueba Celular", "https://test.com/stream-cel.m3u8"))
        assertTrue(Constants.isTestOrDummyChannel("Canal de prueba", "https://cdn.validstream.tv/live.m3u8"))
        assertTrue(Constants.isTestOrDummyChannel("Canal Real", "https://example.com/stream.m3u8"))
        assertFalse(Constants.isTestOrDummyChannel("Latina TV (1080p)", "https://live.latina.pe/playlist.m3u8"))
    }
}
