package com.gamecenter.app.games.chinesechess;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.gamecenter.app.R;

/**
 * 新手引导对话框
 */
public class TutorialDialog extends Dialog {

    private TutorialStep currentStep;
    private TextView tvStepTitle;
    private TextView tvStepDescription;
    private TextView tvStepProgress;
    private Button btnNext;
    private Button btnSkip;

    private View.OnClickListener onNextClickListener;
    private View.OnClickListener onSkipClickListener;

    public TutorialDialog(@NonNull Context context) {
        super(context);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_tutorial);

        bindViews();
        setupClickListeners();
        updateUI();
    }

    private void bindViews() {
        tvStepTitle = findViewById(R.id.tv_tutorial_title);
        tvStepDescription = findViewById(R.id.tv_tutorial_description);
        tvStepProgress = findViewById(R.id.tv_tutorial_progress);
        btnNext = findViewById(R.id.btn_tutorial_next);
        btnSkip = findViewById(R.id.btn_tutorial_skip);
    }

    private void setupClickListeners() {
        btnNext.setOnClickListener(onNextClickListener);
        btnSkip.setOnClickListener(onSkipClickListener);
    }

    private void updateUI() {
        if (currentStep == null) return;

        tvStepTitle.setText(currentStep.getTitle());
        tvStepDescription.setText(currentStep.getDescription());
        tvStepProgress.setText(String.format("第 %d 步", currentStep.getStepIndex() + 1));
    }

    public void setStep(TutorialStep step) {
        this.currentStep = step;
        if (tvStepTitle != null) {
            updateUI();
        }
    }

    public void setOnNextClickListener(View.OnClickListener listener) {
        this.onNextClickListener = listener;
        if (btnNext != null) {
            btnNext.setOnClickListener(listener);
        }
    }

    public void setOnSkipClickListener(View.OnClickListener listener) {
        this.onSkipClickListener = listener;
        if (btnSkip != null) {
            btnSkip.setOnClickListener(listener);
        }
    }

    public void setStepIndex(int index) {
        if (tvStepProgress != null) {
            tvStepProgress.setText(String.format("第 %d 步", index + 1));
        }
    }
}
