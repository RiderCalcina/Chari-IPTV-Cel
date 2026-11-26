package com.example.chariiptv

import com.example.chariiptv.domain.provider.M3uParser
import org.junit.Assert.assertEquals
import org.junit.Test

class M3uParserTest {

    @Test
    fun parse_extractsHeadersAndSkipsTestChannels() {
        val sample = """
            #EXTM3U
            #EXTINF:-1 tvg-id="SampleSports.pe@SD" tvg-logo="https://cdn.validstream.tv/logo.png" group-title="Sports",Sample Sports (1080p)
            #EXTVLCOPT:http-referrer=https://vlc.validstream.tv/
            #EXTVLCOPT:http-user-agent=CustomAgent/1.0
            https://cdn.validstream.tv/live/index.m3u8
            #EXTINF:-1 tvg-id="PipeChannel.es@SD" group-title="General",Pipe Channel
            https://cdn2.validstream.tv/stream.m3u8|User-Agent=PipeUA/2.0&Referer=https://pipe.validstream.tv/
            #EXTINF:-1 group-title="General",Canal de prueba
            https://test.com/stream-cel.m3u8
        """.trimIndent()

        val channels = M3uParser.parse(sample)
        assertEquals(2, channels.size)

        val first = channels[0]
        assertEquals("Sample Sports (1080p)", first.name)
        assertEquals("https://cdn.validstream.tv/live/index.m3u8", first.url)
        assertEquals("https://vlc.validstream.tv/", first.referer)
        assertEquals("CustomAgent/1.0", first.userAgent)

        val second = channels[1]
        assertEquals("https://cdn2.validstream.tv/stream.m3u8", second.url)
        assertEquals("PipeUA/2.0", second.userAgent)
        assertEquals("https://pipe.validstream.tv/", second.referer)
    }
}
