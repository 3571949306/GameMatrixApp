package com.gamecenter.app.update;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class UpdatePresenterTest {

    private static String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        return String.format("%.1f MB", size / (1024.0 * 1024.0));
    }

    private static String formatDownloadProgress(long downloaded, long total) {
        String downloadedStr = formatFileSize(downloaded);
        int percent = total > 0 ? (int) (downloaded * 100 / total) : 0;
        if (total > 0) {
            String totalStr = formatFileSize(total);
            return downloadedStr + " / " + totalStr + " (" + percent + "%)";
        }
        return downloadedStr + " (" + percent + "%)";
    }

    @Test
    public void formatFileSize_bytes() {
        assertEquals("512 B", formatFileSize(512));
    }

    @Test
    public void formatFileSize_zeroBytes() {
        assertEquals("0 B", formatFileSize(0));
    }

    @Test
    public void formatFileSize_oneByte() {
        assertEquals("1 B", formatFileSize(1));
    }

    @Test
    public void formatFileSize_justBelowKB() {
        assertEquals("1023 B", formatFileSize(1023));
    }

    @Test
    public void formatFileSize_oneKB() {
        assertEquals("1.0 KB", formatFileSize(1024));
    }

    @Test
    public void formatFileSize_kilobytes() {
        assertEquals("1.5 KB", formatFileSize(1536));
    }

    @Test
    public void formatFileSize_justBelowMB() {
        assertEquals("1024.0 KB", formatFileSize(1024 * 1024 - 1));
    }

    @Test
    public void formatFileSize_oneMB() {
        assertEquals("1.0 MB", formatFileSize(1024 * 1024));
    }

    @Test
    public void formatFileSize_megabytes() {
        assertEquals("10.0 MB", formatFileSize(10L * 1024 * 1024));
    }

    @Test
    public void formatFileSize_largeMB() {
        assertEquals("50.5 MB", formatFileSize(50L * 1024 * 1024 + 512 * 1024));
    }

    @Test
    public void formatDownloadProgress_withTotal() {
        assertEquals("512 B / 1.0 KB (50%)", formatDownloadProgress(512, 1024));
    }

    @Test
    public void formatDownloadProgress_withoutTotal() {
        assertEquals("512 B (0%)", formatDownloadProgress(512, 0));
    }

    @Test
    public void formatDownloadProgress_complete() {
        assertEquals("1.0 KB / 1.0 KB (100%)", formatDownloadProgress(1024, 1024));
    }

    @Test
    public void formatDownloadProgress_withKBTotal() {
        assertEquals("512 B / 1.5 KB (33%)", formatDownloadProgress(512, 1536));
    }

    @Test
    public void formatDownloadProgress_withMBTotal() {
        assertEquals("1.0 MB / 10.0 MB (10%)", formatDownloadProgress(1024 * 1024, 10L * 1024 * 1024));
    }

    @Test
    public void formatDownloadProgress_zeroDownloadedWithTotal() {
        assertEquals("0 B / 1.0 KB (0%)", formatDownloadProgress(0, 1024));
    }
}
