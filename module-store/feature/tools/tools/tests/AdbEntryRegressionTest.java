package com.gamecenter.app.tools;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import com.gamecenter.app.core.common.AdbWorkbenchLauncher;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Runs production entry/preference code against deterministic Android boundary fakes. */
public final class AdbEntryRegressionTest {
    public static void main(String[] args) {
        oldHostDoesNotResolveNewResourcesOrConsumeMigration();
        resourceMismatchDoesNotExposeBrokenCard();
        migrationPreservesOldChoicesAndLaterAdbHiding();
        explicitEmptyVisibilityStaysEmpty();
        onlySuccessfulClicksCountAsUse();
        entryContractIsExplicitAndLaunchOnly();
        System.out.println("ADB entry regression: 6 groups passed");
    }

    private static void oldHostDoesNotResolveNewResourcesOrConsumeMigration() {
        Context context = new Context();
        context.pm.available = false;
        context.prefs.edit().putStringSet("tools_visible", Set.of("ping")).apply();
        ToolSectionStore store = new ToolSectionStore(context);
        check(find(store.loadSections(), "adb_workbench") == null, "old host must omit ADB");
        check(context.resources.lookups == 0, "must probe component before new resources");
        check(!context.prefs.getBoolean("adb_visibility_v1", false), "old host must defer migration");
        check(!AdbWorkbenchToolBinder.launch(context), "old host cannot launch");
        check(context.starts == 0, "failed capability must not dispatch activity");
        context.pm.available = true;
        check(find(store.loadSections(), "adb_workbench").visible, "later host upgrade gets the card");
    }

    private static void resourceMismatchDoesNotExposeBrokenCard() {
        Context context = new Context();
        context.resources.available = false;
        check(AdbWorkbenchToolBinder.createSection(context) == null, "missing layout/title/id must omit ADB");
        context.resources.available = true;
        context.pm.info.exported = true;
        check(AdbWorkbenchToolBinder.createSection(context) == null, "entry must be private");
        context.pm.info.exported = false;
        context.pm.info.enabled = false;
        check(AdbWorkbenchToolBinder.createSection(context) == null, "disabled component must omit ADB");
    }

    private static void migrationPreservesOldChoicesAndLaterAdbHiding() {
        Context context = new Context();
        context.prefs.edit().putStringSet("tools_visible", Set.of("ping"))
                .putString("tools_order", "wifi,ping")
                .putStringSet("tools_favorites", Set.of("wifi")).apply();
        ToolSectionStore store = new ToolSectionStore(context);
        List<ToolSection> sections = store.loadSections();
        check(sections.get(0).id.equals("wifi") && sections.get(1).id.equals("ping"), "existing order remains");
        check(find(sections, "ping").visible, "visible old card remains");
        check(!find(sections, "wifi").visible, "hidden old card stays hidden");
        check(find(sections, "adb_workbench").visible, "new card is visible for nonempty old selection");
        check(store.isFavorite("wifi"), "favorites remain unchanged");
        find(sections, "adb_workbench").visible = false;
        store.saveVisibility(sections);
        check(!find(store.loadSections(), "adb_workbench").visible, "migration must not reverse active hiding");
        check(!find(store.loadSections(), "wifi").visible, "repeat load must preserve old hiding");
    }

    private static void explicitEmptyVisibilityStaysEmpty() {
        Context context = new Context();
        context.prefs.edit().putStringSet("tools_visible", Collections.emptySet()).apply();
        ToolSectionStore store = new ToolSectionStore(context);
        for (ToolSection section : store.loadSections()) check(!section.visible, "explicit hide-all must remain hidden");
        check(context.prefs.getStringSet("tools_visible", new HashSet<>()).isEmpty(), "empty preference must remain empty");
        Context fresh = new Context();
        check(find(new ToolSectionStore(fresh).loadSections(), "adb_workbench").visible, "absent visibility means default visible");
    }

    private static void onlySuccessfulClicksCountAsUse() {
        Context context = new Context();
        ToolSectionStore store = new ToolSectionStore(context);
        AdbWorkbenchToolBinder binder = new AdbWorkbenchToolBinder();
        View button = new View(context);
        binder.bind(context, button, null);
        binder.bind(context, button, null);
        check(store.getUsageCount("adb_workbench") == 0 && store.getRecentIds().isEmpty(), "binding must not record a use");
        context.denyStart = true;
        button.performClick();
        check(store.getUsageCount("adb_workbench") == 0, "rejected launch must not count");
        context.denyStart = false;
        button.performClick();
        check(store.getUsageCount("adb_workbench") == 1, "one successful click counts once");
        check(store.getRecentIds().equals(List.of("adb_workbench")), "recent uses record actual opens");
        Intent reference = AdbWorkbenchLauncher.createIntent(context, AdbWorkbenchLauncher.SOURCE_TOOLS);
        check(context.started.component.name.equals(reference.component.name), "compatibility adapter targets shared workbench");
        check(context.started.component.pkg.equals(context.getPackageName()), "debug/applicationId suffix is preserved");
        check(context.started.extras.equals(reference.extras), "adapter extras match common contract");
        context.pm.available = false;
        button.performClick();
        check(store.getUsageCount("adb_workbench") == 1 && context.starts == 1, "component disappearance must not dispatch or count");
    }

    private static void entryContractIsExplicitAndLaunchOnly() {
        android.app.Activity activity = new android.app.Activity();
        Intent intent = AdbWorkbenchLauncher.createIntent(activity, AdbWorkbenchLauncher.SOURCE_HALL);
        check(intent.flags == 0, "Activity launch must preserve caller task");
        check(intent.extras.equals(java.util.Map.of("source", "hall_avatar")), "entry must send only source");
        check(intent.component.pkg.equals(activity.getPackageName()), "host package is not hard coded");
        Context application = new Context();
        check((AdbWorkbenchLauncher.createIntent(application, "tools").flags & Intent.FLAG_ACTIVITY_NEW_TASK) != 0,
                "non-Activity context requires a task flag");
        boolean rejected = false;
        try { AdbWorkbenchLauncher.createIntent(activity, "shell:reboot"); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "source is an allowlist, not arbitrary executable input");
    }

    private static ToolSection find(List<ToolSection> sections, String id) {
        for (ToolSection section : sections) if (id.equals(section.id)) return section;
        return null;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
