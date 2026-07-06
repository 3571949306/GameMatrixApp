package com.gamecenter.app.browser.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.gamecenter.app.R;
import com.google.android.material.textfield.TextInputEditText;

public class AddBookmarkDialog {

    public interface OnBookmarkAddedListener {
        void onBookmarkAdded(String title, String url);
    }

    public static void show(@NonNull Context context, @Nullable String title,
                            @Nullable String url, @NonNull OnBookmarkAddedListener listener) {
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_bookmark, null);
        TextInputEditText etTitle = dialogView.findViewById(R.id.et_title);
        TextInputEditText etUrl = dialogView.findViewById(R.id.et_url);
        if (title != null && !title.isEmpty()) etTitle.setText(title);
        if (url != null && !url.isEmpty()) etUrl.setText(url);
        new AlertDialog.Builder(context)
                .setTitle(R.string.browser_bookmark_dialog_title)
                .setView(dialogView)
                .setPositiveButton(R.string.browser_bookmark_dialog_confirm, (dialog, which) -> {
                    String inputTitle = etTitle.getText() != null ? etTitle.getText().toString().trim() : "";
                    String inputUrl = etUrl.getText() != null ? etUrl.getText().toString().trim() : "";
                    if (!inputUrl.isEmpty()) listener.onBookmarkAdded(inputTitle, inputUrl);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
