package com.gamecenter.app.td.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Validated, data-only campaign level. It contains no executable behaviour from content files. */
public final class TdLevelDefinition {
    public enum Theme { GARDEN, BRAMBLE, CRYSTAL, VALLEY, STORM }

    public static final class Wave {
        public final int routeIndex;
        public final List<MonsterType> types;
        public final int count;
        public final float intervalSec, delaySec, hpMultiplier, speedMultiplier;

        Wave(int routeIndex, List<MonsterType> types, int count, float intervalSec,
             float delaySec, float hpMultiplier, float speedMultiplier) {
            this.routeIndex = routeIndex;
            this.types = Collections.unmodifiableList(new ArrayList<>(types));
            this.count = count;
            this.intervalSec = intervalSec;
            this.delaySec = delaySec;
            this.hpMultiplier = hpMultiplier;
            this.speedMultiplier = speedMultiplier;
        }
    }

    /**
     * name/subtitle are the authoritative Chinese source texts; nameEn/subtitleEn carry the
     * English display text (or a logged fallback copy of the source when a chapter omits them).
     */
    public final String id, name, subtitle;
    public final String nameEn, subtitleEn;
    /** 每关剧情：开战前引子与胜利后尾声；中文为源文本，英文仅英文系统使用；空串表示无剧情。 */
    public final String storyIntro, storyIntroEn, storyOutro, storyOutroEn;
    public final int order, rows, cols, eggRow, eggCol, startCoin, mascotHp;
    public final Theme theme;
    public final List<Wave> waves;
    private final List<int[][]> routes;

    TdLevelDefinition(String id, int order, String name, String subtitle,
                      String nameEn, String subtitleEn, Theme theme,
                      int rows, int cols, int eggRow, int eggCol, int startCoin, int mascotHp,
                      List<int[][]> routes, List<Wave> waves) {
        this(id, order, name, subtitle, nameEn, subtitleEn, "", "", "", "", theme,
                rows, cols, eggRow, eggCol, startCoin, mascotHp, routes, waves);
    }

    TdLevelDefinition(String id, int order, String name, String subtitle,
                      String nameEn, String subtitleEn,
                      String storyIntro, String storyIntroEn,
                      String storyOutro, String storyOutroEn, Theme theme,
                      int rows, int cols, int eggRow, int eggCol, int startCoin, int mascotHp,
                      List<int[][]> routes, List<Wave> waves) {
        this.id = id;
        this.order = order;
        this.name = name;
        this.subtitle = subtitle;
        this.nameEn = nameEn;
        this.subtitleEn = subtitleEn;
        this.storyIntro = storyIntro != null ? storyIntro : "";
        this.storyIntroEn = storyIntroEn != null ? storyIntroEn : this.storyIntro;
        this.storyOutro = storyOutro != null ? storyOutro : "";
        this.storyOutroEn = storyOutroEn != null ? storyOutroEn : this.storyOutro;
        this.theme = theme;
        this.rows = rows;
        this.cols = cols;
        this.eggRow = eggRow;
        this.eggCol = eggCol;
        this.startCoin = startCoin;
        this.mascotHp = mascotHp;
        this.routes = copyRoutes(routes);
        this.waves = Collections.unmodifiableList(new ArrayList<>(waves));
    }

    /** Builds a fresh deterministic session; a prior session cannot mutate this definition. */
    public TdGame newGame() {
        List<TdGame.Wave> gameWaves = new ArrayList<>();
        for (Wave wave : waves) {
            gameWaves.add(new TdGame.Wave(wave.types.toArray(new MonsterType[0]), wave.routeIndex,
                    wave.count, wave.intervalSec, wave.delaySec, wave.hpMultiplier,
                    wave.speedMultiplier));
        }
        return new TdGame(cols, rows, copyRoutesArray(), eggRow, eggCol, startCoin, mascotHp,
                gameWaves).setVisualTheme(TdGame.VisualTheme.valueOf(theme.name()));
    }

    public List<int[][]> copyRoutes() { return copyRoutes(routes); }

    private int[][][] copyRoutesArray() {
        int[][][] result = new int[routes.size()][][];
        for (int i = 0; i < routes.size(); i++) result[i] = copyRoute(routes.get(i));
        return result;
    }

    private static List<int[][]> copyRoutes(List<int[][]> source) {
        List<int[][]> result = new ArrayList<>();
        for (int[][] route : source) result.add(copyRoute(route));
        return Collections.unmodifiableList(result);
    }

    private static int[][] copyRoute(int[][] route) {
        int[][] copy = new int[route.length][2];
        for (int i = 0; i < route.length; i++) {
            copy[i][0] = route[i][0];
            copy[i][1] = route[i][1];
        }
        return copy;
    }
}
