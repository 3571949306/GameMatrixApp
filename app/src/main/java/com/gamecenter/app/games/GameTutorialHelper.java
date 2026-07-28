package com.gamecenter.app.games;

import android.content.Context;

import androidx.appcompat.app.AlertDialog;

import com.gamecenter.app.R;

public final class GameTutorialHelper {
    private GameTutorialHelper() {
    }

    public static void showGomokuTutorial(Context context) {
        new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.tutorial_gomoku_title))
                .setMessage(context.getString(R.string.tutorial_gomoku_msg))
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    public static void showKlotskiTutorial(Context context) {
        new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.tutorial_klotski_title))
                .setMessage(context.getString(R.string.tutorial_klotski_msg))
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    public static void showChineseChessTutorial(Context context) {
        new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.tutorial_chess_title))
                .setMessage(context.getString(R.string.tutorial_chess_msg))
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
}
