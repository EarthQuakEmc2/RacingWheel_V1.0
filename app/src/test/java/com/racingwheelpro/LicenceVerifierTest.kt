package com.racingwheelpro

import com.racingwheelpro.catalog.LicenceVerifier
import org.json.JSONObject
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LicenceVerifierTest {
    @Test
    fun signedFixtureUnlocksOnlyTheNamedTemplate() {
        val fixture = JSONObject(resource("/licence-fixture.json"))
        val licence = LicenceVerifier.verify(fixture.getJSONObject("licence"), fixture.getString("publicKey"))
        assertNotNull(licence)
        assertTrue(licence!!.templates.contains("porsche_gt3r"))
        assertTrue("ferrari_sf1000" !in licence.templates)
        assertNull(LicenceVerifier.verify(fixture.getJSONObject("tampered"), fixture.getString("publicKey")))
    }

    private fun resource(path: String): String {
        return LicenceVerifierTest::class.java.getResourceAsStream(path)!!.bufferedReader().readText()
    }
}
