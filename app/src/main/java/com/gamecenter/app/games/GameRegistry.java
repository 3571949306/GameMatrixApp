package com.gamecenter.app.games;

import android.content.Context;

import com.gamecenter.app.R;
import com.gamecenter.app.games.doudizhu.DouDiZhuMenuActivity;
import com.gamecenter.app.games.gomoku.GomokuActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GameRegistry {
    public static final String CATEGORY_CLASSICS = "classics";
    public static final String CATEGORY_PUZZLE = "puzzle";
    public static final String CATEGORY_CASUAL = "casual";

    private static final Map<String, List<Entry>> dynamicEntries = new LinkedHashMap<>();

    private GameRegistry() {
    }

    public static Class<?> getActivityClassById(Context context, String gameId) {
        for (Category category : getCategories(context)) {
            for (Entry entry : category.games) {
                if (entry.id.equals(gameId)) {
                    return entry.activityClass;
                }
            }
        }
        return null;
    }

    public static Class<? extends androidx.fragment.app.Fragment> getFragmentClassById(
            Context context,
            String gameId
    ) {
        return null;
    }

    public static boolean register(Entry entry) {
        synchronized (dynamicEntries) {
            for (List<Entry> entries : dynamicEntries.values()) {
                for (Entry existing : entries) {
                    if (existing.id.equals(entry.id)) {
                        return false;
                    }
                }
            }

            List<Entry> list = dynamicEntries.get(entry.categoryKey);
            if (list == null) {
                list = new ArrayList<>();
                dynamicEntries.put(entry.categoryKey, list);
            }
            list.add(entry);
            return true;
        }
    }

    public static boolean unregister(String gameId) {
        synchronized (dynamicEntries) {
            boolean removed = false;
            for (List<Entry> list : dynamicEntries.values()) {
                removed |= list.removeIf(entry -> entry.id.equals(gameId));
            }
            return removed;
        }
    }

    public static void clearDynamicEntries() {
        synchronized (dynamicEntries) {
            dynamicEntries.clear();
        }
    }

    public static List<Category> getCategories(Context context) {
        List<Category> staticCategories = buildStaticCategories(context);
        Map<String, List<Entry>> merged = new LinkedHashMap<>();
        Map<String, String> names = new LinkedHashMap<>();

        for (Category category : staticCategories) {
            merged.put(category.categoryKey, new ArrayList<>(category.games));
            names.put(category.categoryKey, category.name);
        }

        synchronized (dynamicEntries) {
            for (Map.Entry<String, List<Entry>> entry : dynamicEntries.entrySet()) {
                String key = entry.getKey();
                List<Entry> target = merged.get(key);
                if (target == null) {
                    target = new ArrayList<>();
                    merged.put(key, target);
                    names.put(key, categoryName(context, key));
                }
                target.addAll(entry.getValue());
            }
        }

        List<Category> result = new ArrayList<>();
        for (Map.Entry<String, String> name : names.entrySet()) {
            List<Entry> games = merged.get(name.getKey());
            if (games != null && !games.isEmpty()) {
                result.add(new Category(name.getValue(), games, name.getKey()));
            }
        }
        return Collections.unmodifiableList(result);
    }

    public static List<Entry> flatten(List<Category> categories) {
        List<Entry> games = new ArrayList<>();
        for (Category category : categories) {
            games.addAll(category.games);
        }
        return Collections.unmodifiableList(games);
    }

    private static List<Category> buildStaticCategories(Context context) {
        String classicsLabel = context.getString(R.string.category_classics);
        List<Entry> classics = new ArrayList<>();
        classics.add(new Entry(
                "gomoku",
                R.drawable.ic_gomoku,
                context.getString(R.string.gomoku),
                context.getString(R.string.gomoku_desc),
                GomokuActivity.class,
                classicsLabel,
                CATEGORY_CLASSICS
        ));
        classics.add(new Entry(
                "doudizhu",
                R.drawable.ic_doudizhu,
                context.getString(R.string.game_doudizhu),
                context.getString(R.string.game_doudizhu_desc),
                DouDiZhuMenuActivity.class,
                classicsLabel,
                CATEGORY_CLASSICS
        ));

        List<Category> categories = new ArrayList<>();
        categories.add(new Category(classicsLabel, classics, CATEGORY_CLASSICS));
        return categories;
    }

    private static String categoryName(Context context, String key) {
        if (CATEGORY_PUZZLE.equals(key)) return context.getString(R.string.category_puzzle);
        if (CATEGORY_CASUAL.equals(key)) return context.getString(R.string.category_casual);
        return context.getString(R.string.category_classics);
    }

    public static final class Category {
        public final String name;
        public final List<Entry> games;
        public final String categoryKey;

        private Category(String name, List<Entry> games, String categoryKey) {
            this.name = name;
            this.games = Collections.unmodifiableList(games);
            this.categoryKey = categoryKey;
        }
    }

    public static final class Entry {
        public final String id;
        public final int iconRes;
        public final String name;
        public final String desc;
        public final Class<?> activityClass;
        public final String category;
        public final String categoryKey;

        public Entry(String id, int iconRes, String name, String desc,
                     Class<?> activityClass, String category, String categoryKey) {
            this.id = id;
            this.iconRes = iconRes;
            this.name = name;
            this.desc = desc;
            this.activityClass = activityClass;
            this.category = category;
            this.categoryKey = categoryKey;
        }
    }
}
