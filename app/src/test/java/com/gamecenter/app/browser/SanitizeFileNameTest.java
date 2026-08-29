package com.gamecenter.app.browser;

import static org.junit.Assert.assertEquals;

import com.gamecenter.app.browser.core.security.FileNameSanitizer;

import org.junit.Test;

/** Phase A-2 / B 文件名净化单测：非法字符、控制字符、首尾点号、空值兜底。 */
public class SanitizeFileNameTest {

    @Test
    public void stripsIllegalChars() {
        assertEquals("a_b_c__", FileNameSanitizer.sanitize("a/b:c*?"));
        assertEquals("foo_bar_", FileNameSanitizer.sanitize("foo<bar>"));
    }

    @Test
    public void removesControlCharacters() {
        // NUL 与控制字符被丢弃
        String input = "a" + (char) 0 + "b" + (char) 0x1F + "c";
        assertEquals("abc", FileNameSanitizer.sanitize(input));
    }

    @Test
    public void collapsesLeadingTrailingDotsAndWhitespace() {
        assertEquals("download", FileNameSanitizer.sanitize("  ..  "));
        assertEquals("file", FileNameSanitizer.sanitize("...file..."));
    }

    @Test
    public void nullAndEmptyFallback() {
        assertEquals("download", FileNameSanitizer.sanitize(null));
        assertEquals("download", FileNameSanitizer.sanitize(""));
    }
}
