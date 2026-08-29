package com.gamecenter.app.browser.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.gamecenter.app.browser.core.player.BrowserVideoJs;

import org.junit.Test;

/** 纯 JVM 播放器脚本边界回归测试。 */
public class BrowserVideoJsTest {

    @Test
    public void actionRejectsUntrustedActionBeforeConcatenation() {
        String script = BrowserVideoJs.action("play');window.evil();//", 0d);

        assertEquals("(function(){return 0;})();", script);
        assertFalse(script.contains("window.evil"));
    }

    @Test
    public void actionAcceptsOnlyKnownActionConstants() {
        String script = BrowserVideoJs.action(BrowserVideoJs.ACTION_SEEK, 12.5d);

        assertTrue(script.contains("case 'seek':"));
        assertTrue(script.contains("12.500000"));
    }

    @Test
    public void setRectConvertsAndroidPixelsUsingDevicePixelRatio() {
        String script = BrowserVideoJs.setRect(10, 20, 300, 169);

        assertTrue(script.contains("window.devicePixelRatio"));
        assertTrue(script.contains("(l/d)+'px'"));
        assertTrue(script.contains("(w/d)+'px'"));
    }

    @Test
    public void takeoverAndReleaseKeepAReversibleMarker() {
        assertTrue(BrowserVideoJs.TAKE_OVER.contains("__gmTakeoverSaved"));
        assertTrue(BrowserVideoJs.TAKE_OVER.contains("__gmSavedOverflow"));
        assertTrue(BrowserVideoJs.RELEASE.contains("__gmSavedParent"));
        assertTrue(BrowserVideoJs.RELEASE.contains("removeProperty('overflow')"));
    }
}
