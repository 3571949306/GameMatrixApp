package com.gamecenter.app.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

/** Regression coverage for visibility migration when adding local device tools. */
public class ToolSectionStoreTest {

    @Test
    public void addsDeviceToolsToExistingVisibleSet() {
        Set<String> migrated = ToolSectionStore.migrateDeviceToolsVisibility(
                new HashSet<>(Collections.singletonList("battery")), false);

        assertEquals(new HashSet<>(Arrays.asList(
                "battery", "device_overview", "installed_apps", "compass")), migrated);
    }

    @Test
    public void preservesHideAllAndAlreadyMigratedSets() {
        Set<String> empty = Collections.emptySet();
        Set<String> existing = new HashSet<>(Collections.singletonList("battery"));

        assertSame(empty, ToolSectionStore.migrateDeviceToolsVisibility(empty, false));
        assertSame(existing, ToolSectionStore.migrateDeviceToolsVisibility(existing, true));
    }

    @Test
    public void addsSatelliteWithItsOwnVersionedMigration() {
        Set<String> original = new HashSet<>(Arrays.asList("battery", "compass"));

        Set<String> migrated = ToolSectionStore.migrateSatelliteToolVisibility(original, false);

        assertEquals(new HashSet<>(Arrays.asList("battery", "compass", "satellite")), migrated);
        assertSame(original, ToolSectionStore.migrateSatelliteToolVisibility(original, true));
        assertSame(Collections.emptySet(),
                ToolSectionStore.migrateSatelliteToolVisibility(Collections.emptySet(), false));
    }
}
