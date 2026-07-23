package com.gamecenter.app.update

import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun `uses a version metadata asset for the GitHub fallback`() {
        assertEquals(
            "https://github.com/3571949306/GameMatrixApp/releases/latest/download/version.json",
            UpdateChecker.GITHUB_VERSION_JSON_URL
        )
    }

    @Test
    fun `does not append a second suffix to a metadata URL`() {
        assertEquals(
            UpdateChecker.GITHUB_VERSION_JSON_URL,
            UpdateChecker().buildVersionJsonUrl(UpdateChecker.GITHUB_VERSION_JSON_URL, false)
        )
    }
}
