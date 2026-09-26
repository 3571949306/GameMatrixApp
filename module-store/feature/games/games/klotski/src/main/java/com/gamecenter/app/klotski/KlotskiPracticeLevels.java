package com.gamecenter.app.klotski;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Progressive practice endgames; reference moves count individual one-cell moves. */
public final class KlotskiPracticeLevels {
    private KlotskiPracticeLevels() { }

    public static final class Level {
        public final String id;
        public final String title;
        public final String initialStateCsv;
        public final int referenceMoves;

        private Level(String id, String title, String initialStateCsv, int referenceMoves) {
            this.id = id;
            this.title = title;
            this.initialStateCsv = initialStateCsv;
            this.referenceMoves = referenceMoves;
        }
    }

    // Fixed states from the final 4, 8 and 16 single-cell moves of a classic solution.
    // practice_exit_04~08 are machine-verified layouts (BFS shortest distances 20/26/32/40/48,
    // see repo-external klotski_factory.py); every referenceMoves is the true optimum.
    // Keep IDs stable: saves and practice records refer to these IDs, not UI positions.
    private static final List<Level> LEVELS = Collections.unmodifiableList(Arrays.asList(
            new Level("practice_exit_01", "关羽让路",
                    "0,0,3,3,0,2,0,1,0,0,0,2,3,0,2,2,4,3,4,1,2", 4),
            new Level("practice_exit_02", "双兵腾位",
                    "0,0,3,3,0,2,0,1,0,0,0,2,3,2,2,2,4,3,4,3,2", 8),
            new Level("practice_exit_03", "先腾底路",
                    "0,0,2,3,0,2,0,1,0,0,0,2,4,3,3,0,4,1,4,3,2", 16),
            new Level("practice_exit_04", "偏师让道",
                    "0,0,2,0,0,1,0,2,2,3,1,2,4,2,1,3,0,0,4,1,4", 20),
            new Level("practice_exit_05", "兵分两路",
                    "0,0,0,0,2,1,3,3,1,3,3,2,0,2,1,2,2,2,3,2,4", 26),
            new Level("practice_exit_06", "关门递卒",
                    "0,0,0,0,3,1,2,2,2,3,2,2,1,1,4,2,0,3,0,3,4", 32),
            new Level("practice_exit_07", "腾挪辗转",
                    "0,2,0,0,3,1,1,3,2,1,3,0,0,2,4,2,3,3,4,2,2", 40),
            new Level("practice_exit_08", "横扫底开",
                    "0,0,0,1,2,2,1,3,1,3,3,2,0,0,2,0,3,0,4,1,4", 48)
    ));

    public static List<Level> all() {
        return LEVELS;
    }

    public static Level find(String id) {
        for (Level level : LEVELS) {
            if (level.id.equals(id)) return level;
        }
        return null;
    }
}
