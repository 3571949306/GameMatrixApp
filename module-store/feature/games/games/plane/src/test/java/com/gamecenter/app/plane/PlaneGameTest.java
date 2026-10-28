package com.gamecenter.app.plane;

import android.app.Application;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 飞机大战难度配置、生命状态与游戏循环单元测试。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, manifest = Config.NONE, application = Application.class)
public class PlaneGameTest {

    @Test
    public void testDifficultyFactorsAndStart() {
        Context context = ApplicationProvider.getApplicationContext();
        PlaneView planeView = new PlaneView(context);

        // 默认 0.5f
        assertEquals(0.5f, planeView.getDifficultyFactor(), 0.001f);

        // 设置简单
        planeView.setDifficultyFactor(0.3f);
        assertEquals(0.3f, planeView.getDifficultyFactor(), 0.001f);

        // 开始第一波
        planeView.startGame(1);
        assertTrue(planeView.isGameRunning());
        assertFalse(planeView.isGameOver());
        assertFalse(planeView.isPaused());
        assertEquals(1, planeView.getWave());
        assertEquals(0, planeView.getScore());
        assertEquals(3, planeView.getLives());
    }

    @Test
    public void testPauseResumeStop() {
        Context context = ApplicationProvider.getApplicationContext();
        PlaneView planeView = new PlaneView(context);
        planeView.startGame(1);

        planeView.pauseGame();
        assertTrue(planeView.isPaused());

        planeView.resumeGame();
        assertFalse(planeView.isPaused());

        planeView.stopGame();
        assertFalse(planeView.isGameRunning());
    }
}
