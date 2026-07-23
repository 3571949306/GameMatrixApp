package com.gamecenter.app.update

import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun `extracts version code from published GitHub tag`() {
        assertEquals(598, UpdateChecker.parseGitHubVersionCode("v1.4.1-vc598"))
    }

    @Test
    fun `rejects GitHub tag without internal version code`() {
        assertEquals(0, UpdateChecker.parseGitHubVersionCode("v1.4.1"))
    }
}
