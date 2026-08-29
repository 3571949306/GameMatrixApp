package com.gamecenter.app.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.util.Locale;

import org.junit.Test;

/** Regression coverage for number formatting at the long conversion boundary. */
public class UnitConverterToolBinderTest {

    @Test
    public void rendersOrdinaryWholeNumbersAsIntegers() {
        assertEquals("42", UnitConverterToolBinder.formatNumber(42.0));
        assertEquals("-17", UnitConverterToolBinder.formatNumber(-17.0));
    }

    @Test
    public void doesNotSaturateTheFirstUnrepresentableLongValue() {
        double firstUnrepresentableLong = 0x1.0p63;

        String formatted = UnitConverterToolBinder.formatNumber(firstUnrepresentableLong);

        assertEquals(String.format(Locale.US, "%.6g", firstUnrepresentableLong), formatted);
        assertNotEquals(Long.toString(Long.MAX_VALUE), formatted);
    }

    @Test
    public void stillRendersTheLowestRepresentableLongValueAsAnInteger() {
        assertEquals(Long.toString(Long.MIN_VALUE),
                UnitConverterToolBinder.formatNumber(-0x1.0p63));
    }
}
