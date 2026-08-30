package com.gamecenter.app.browser;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.util.Arrays;

/**
 * Prevents the retired WebView home-page asset from being bundled again.
 *
 * <p>The production browser home page is native ({@code BrowserHomeHelper}); keeping
 * the old HTML implementation in assets would reintroduce its untrusted DOM-to-location
 * navigation sink.</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class BrowserAssetRegressionTest {

    @Test
    public void legacyHtmlHomePageIsNotBundled() throws IOException {
        Context context = ApplicationProvider.getApplicationContext();
        String[] browserAssets = context.getAssets().list("browser");

        assertNotNull(browserAssets);
        assertFalse(Arrays.asList(browserAssets).contains("browser_home.html"));
    }
}
