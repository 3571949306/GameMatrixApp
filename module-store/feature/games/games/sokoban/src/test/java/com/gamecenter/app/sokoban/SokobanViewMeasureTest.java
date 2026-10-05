package com.gamecenter.app.sokoban;

import android.app.Application;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Real View measurement must leave the parent enough height for the game controls. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class, manifest = Config.NONE)
public class SokobanViewMeasureTest {
    @Test
    public void exactHeightUsesTheSpaceAssignedByTheParent() {
        SokobanView view = viewWithWideBoard();

        view.measure(spec(320, View.MeasureSpec.AT_MOST), spec(128, View.MeasureSpec.EXACTLY));

        assertEquals("The parent assigned exactly 128 pixels to the board", 128, view.getMeasuredHeight());
        assertTrue("The board must have usable width", view.getMeasuredWidth() > 0);
        assertTrue("Height constraints must not make the board exceed its width limit",
                view.getMeasuredWidth() <= 320);
    }

    @Test
    public void atMostHeightDoesNotConsumeSpaceReservedForControls() {
        SokobanView view = viewWithWideBoard();

        view.measure(spec(320, View.MeasureSpec.EXACTLY), spec(128, View.MeasureSpec.AT_MOST));

        assertEquals("An exact width remains exact when height is constrained", 320, view.getMeasuredWidth());
        assertTrue("The board must have usable height", view.getMeasuredHeight() > 0);
        assertTrue("The board must fit within the 128 pixels left above the controls",
                view.getMeasuredHeight() <= 128);
    }

    @Test
    public void unconstrainedHeightRetainsTheWideBoardsNaturalSize() {
        SokobanView view = viewWithWideBoard();

        view.measure(spec(320, View.MeasureSpec.AT_MOST), spec(0, View.MeasureSpec.UNSPECIFIED));

        assertEquals("The unconstrained board uses the available bounded width", 320, view.getMeasuredWidth());
        assertEquals("The built-in 6 by 8 board naturally needs 240 pixels at this width",
                240, view.getMeasuredHeight());
    }

    private static SokobanView viewWithWideBoard() {
        SokobanGame game = new SokobanGame();
        game.startLevel(4);
        SokobanView view = new SokobanView(ApplicationProvider.getApplicationContext());
        view.setMap(game.getMap());
        return view;
    }

    private static int spec(int size, int mode) {
        return View.MeasureSpec.makeMeasureSpec(size, mode);
    }
}
