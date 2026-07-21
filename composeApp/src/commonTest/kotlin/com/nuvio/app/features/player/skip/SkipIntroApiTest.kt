package com.nuvio.app.features.player.skip

import kotlin.test.Test
import kotlin.test.assertEquals

class SkipIntroApiTest {

    @Test
    fun `aniskip url repeats the types parameter for every segment type`() {
        val url = SkipIntroApi.aniSkipTimesUrl(malId = "16498", episode = 1)

        assertEquals(
            "https://api.aniskip.com/v2/skip-times/16498/1" +
                "?types=op&types=ed&types=recap&types=mixed-op&types=mixed-ed&episodeLength=0",
            url,
        )
    }
}
