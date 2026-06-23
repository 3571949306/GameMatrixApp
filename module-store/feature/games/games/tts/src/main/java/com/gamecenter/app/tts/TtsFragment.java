package com.gamecenter.app.tts;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import com.gamecenter.app.BuildConfig;

/**
 * TTS 语音合成实验室 — 小米 MiMo TTS 全功能客户端（Fragment 版本）。
 * <p>
 * 从 {@link TtsActivity} 演变而来，适配动态模块加载架构。
 * 动态模块通过 DexClassLoader 加载，无法直接启动未经 Manifest 声明的 Activity，
 * 因此将 TTS UI 改造为 Fragment，由 {@code DynamicGameActivity} 作为宿主承载。
 */
public class TtsFragment extends Fragment {

    private static final String TAG = "TtsFragment";

    // API 配置（从 local.properties 经 BuildConfig 注入）
    private static final String API_URL = "https://api.xiaomimimo.com/v1/chat/completions";

    // 录音参数
    private static final int SAMPLE_RATE = 16000;

    // 颜色
    private static final int CLR_PRIMARY = 0xFF1976D2;
    private static final int CLR_PRIMARY_LT = 0xFFE3F2FD;
    private static final int CLR_TEXT = 0xFF212121;
    private static final int CLR_TEXT_SUB = 0xFF757575;
    private static final int CLR_DANGER = 0xFFD32F2F;
    private static final int CLR_SUCCESS = 0xFF388E3C;
    private static final int CLR_CARD = 0xFFFFFFFF;
    private static final int CLR_DIVIDER = 0xFFE0E0E0;

    // ═══════════════ 数据模型 ═══════════════

    static class TtsModel {
        final String id;
        final String name;
        final String desc;
        TtsModel(String id, String name, String desc) { this.id = id; this.name = name; this.desc = desc; }
    }

    static class VoicePreset {
        final String id;
        final String tag;
        VoicePreset(String id, String tag) { this.id = id; this.tag = tag; }
    }

    private final TtsModel[] MODELS = {
            new TtsModel("mimo-v2.5-tts",
                    "标准 TTS",
                    "💡 推荐首选！9种内置音色（4中文+4英文+1默认），音质最优，适合日常使用。"),
            new TtsModel("mimo-v2.5-tts-voicedesign",
                    "声音设计",
                    "💡 用自然语言描述你想要的声音，如【温柔知性女声】【磁性绅士男声】，AI 实时合成。"),
            new TtsModel("mimo-v2.5-tts-voiceclone",
                    "声音克隆",
                    "💡 录制≥3秒样本音频，系统将克隆该声音读出文本。支持 WAV/MP3/FLAC，最大10MB、180秒。"),
            new TtsModel("mimo-v2-tts",
                    "旧版 TTS",
                    "💡 兼容 v2 模型，音质稍弱，用于特殊容错或测试场景。")
    };

    private final VoicePreset[] BUILTIN_VOICES = {
            new VoicePreset("mimo_default", "默认"),
            new VoicePreset("冰糖", "温柔女声"),
            new VoicePreset("茉莉", "清新女声"),
            new VoicePreset("苏打", "活力男声"),
            new VoicePreset("白桦", "沉稳男声"),
            new VoicePreset("Mia", "英文女声"),
            new VoicePreset("Chloe", "英文女声"),
            new VoicePreset("Milo", "英文男声"),
            new VoicePreset("Dean", "英文男声")
    };

    // ═══════════════ 状态 ═══════════════

    private Context ctx;
    private int selectedModelIdx = 0;
    private int selectedVoiceIdx = 0;
    private File cloneAudioFile = null;

    private boolean isRecording = false;
    private File recordingFile = null;
    private MediaRecorder fallbackRecorder = null;

    private MediaPlayer mediaPlayer = null;
    private File generatedFile = null;

    // ═══════════════ UI 控件引用 ═══════════════

    private LinearLayout modelCardsLayout;
    private TextView modelDescText;
    private EditText textInput;
    private LinearLayout voiceSectionLayout;
    private MaterialCardView recordCard;
    private MaterialButton btnRecordToggle;
    private TextView tvRecordStatus;
    private MaterialButton btnGenerate;
    private CircularProgressIndicator progressIndicator;
    private TextView tvStatus;
    private SeekBar seekBar;
    private MaterialButton btnPlay;
    private MaterialButton btnSave;
    private TextView tvAudioSaved;
    private TextView tvAudioInfo;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private OkHttpClient httpClient;

    // ═══════════════ 生命周期 ═══════════════

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // 纯代码构建布局，不依赖 XML 资源（模块资源非宿主可访问）
        ScrollView scrollView = new ScrollView(requireContext());
        scrollView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(0xFFF5F5F5);

        LinearLayout root = new LinearLayout(requireContext());
        root.setLayoutParams(new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(12);
        root.setPadding(pad, pad, pad, pad);
        root.setClipToPadding(false);
        scrollView.addView(root);

        return scrollView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ctx = requireContext();

        httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();

        LinearLayout root = (LinearLayout) ((ViewGroup) view).getChildAt(0);
        setupUI(root);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopRecording();
        stopPlayback();
    }

    // ═══════════════ UI 构建 ═══════════════

    private void setupUI(LinearLayout root) {
        root.addView(makeToolbar());
        root.addView(makeModelCard());
        root.addView(makeTextCard());
        root.addView(makeRecordCard());
        root.addView(makeVoiceSelectorCard());
        root.addView(makeActionCard());
        root.addView(makeAudioCard());
        root.addView(makeFooter());

        updateModelDesc();
        voiceSectionLayout.setVisibility(View.GONE);
    }

    // ── 工具栏 ──

    private View makeToolbar() {
        LinearLayout bar = hlay(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(4), dp(8), dp(4), dp(8));

        TextView title = txt("🎙 语音合成实验室", 20, CLR_TEXT, true);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, dp(40), 1f));
        bar.addView(title);

        Button close = new Button(ctx);
        close.setText("✕");
        close.setTextSize(18);
        close.setTextColor(CLR_TEXT_SUB);
        close.setBackground(null);
        close.setOnClickListener(v -> {
            // 从宿主 Activity 的 Fragment 栈弹出
            if (getParentFragmentManager().getBackStackEntryCount() > 0) {
                getParentFragmentManager().popBackStack();
            } else {
                requireActivity().onBackPressed();
            }
        });
        bar.addView(close);

        return bar;
    }

    // ── 卡片1 —— 模型选择 ──

    private View makeModelCard() {
        MaterialCardView card = card();
        LinearLayout col = col();

        col.addView(sectionTitle("① 选择模型"));
        col.addView(divider());

        HorizontalScrollView hScroll = new HorizontalScrollView(ctx);
        hScroll.setLayoutParams(layParam(MATCH, WRAP));
        modelCardsLayout = hlay(LinearLayout.HORIZONTAL);
        modelCardsLayout.setPadding(dp(4), dp(8), dp(4), dp(8));
        for (int i = 0; i < MODELS.length; i++) {
            final int idx = i;
            MaterialCardView tag = makeModelTag(MODELS[i].name, idx == 0);
            tag.setOnClickListener(v -> selectModel(idx));
            modelCardsLayout.addView(tag);
            if (i < MODELS.length - 1) {
                View gap = new View(ctx);
                gap.setLayoutParams(new LinearLayout.LayoutParams(dp(8), 1));
                modelCardsLayout.addView(gap);
            }
        }
        hScroll.addView(modelCardsLayout);
        col.addView(hScroll);

        modelDescText = txt("", 13, CLR_TEXT_SUB, false);
        modelDescText.setPadding(dp(4), dp(6), dp(4), dp(6));
        col.addView(modelDescText);

        card.addView(col);
        return card;
    }

    private MaterialCardView makeModelTag(String name, boolean selected) {
        MaterialCardView tag = new MaterialCardView(ctx);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
        tag.setLayoutParams(lp);
        tag.setRadius(dp(16));
        tag.setCardElevation(0f);
        tag.setCardBackgroundColor(selected ? CLR_PRIMARY : CLR_CARD);
        tag.setStrokeColor(selected ? CLR_PRIMARY : CLR_DIVIDER);
        tag.setStrokeWidth(dp(1));

        TextView t = txt(name, 13, selected ? 0xFFFFFFFF : CLR_TEXT, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(14), dp(6), dp(14), dp(6));
        tag.addView(t);
        tag.setTag(selected);
        return tag;
    }

    // ── 卡片2 —— 文本输入 ──

    private View makeTextCard() {
        MaterialCardView card = card();
        LinearLayout col = col();

        col.addView(sectionTitle("② 输入文本（≤5000字）"));
        col.addView(divider());

        textInput = new EditText(ctx);
        textInput.setHint("请输入需要合成的文字…");
        textInput.setTextSize(15);
        textInput.setTextColor(CLR_TEXT);
        textInput.setHintTextColor(0xFFBDBDBD);
        textInput.setBackground(roundBg(0xFFF9F9F9, dp(6), dp(1), CLR_DIVIDER));
        textInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        textInput.setMinHeight(dp(100));
        textInput.setMaxHeight(dp(200));
        textInput.setSingleLine(false);
        textInput.setGravity(Gravity.TOP);
        textInput.setLayoutParams(new LinearLayout.LayoutParams(MATCH, WRAP));
        col.addView(textInput);

        card.addView(col);
        return card;
    }

    // ── 卡片3 —— 录音 ──

    private View makeRecordCard() {
        recordCard = card();
        LinearLayout col = col();
        col.setOnClickListener(null);

        col.addView(sectionTitle("②-b 录制声音样本（声音克隆用）"));
        col.addView(divider());
        col.addView(tip("录制清晰的语音片段（3~30秒效果最佳），用于克隆你的声音。"));

        LinearLayout row = hlay(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, 0);

        btnRecordToggle = new MaterialButton(ctx);
        btnRecordToggle.setText("⏺  开始录音");
        btnRecordToggle.setTextSize(14);
        btnRecordToggle.setTextColor(CLR_DANGER);
        btnRecordToggle.setStrokeColor(colorState(CLR_DANGER));
        btnRecordToggle.setCornerRadius(dp(20));
        btnRecordToggle.setPadding(dp(20), dp(10), dp(20), dp(10));
        btnRecordToggle.setOnClickListener(v -> toggleRecording());
        row.addView(btnRecordToggle);

        tvRecordStatus = txt("  未录音", 13, CLR_TEXT_SUB, false);
        row.addView(tvRecordStatus);
        col.addView(row);

        recordCard.addView(col);
        recordCard.setVisibility(View.GONE);
        return recordCard;
    }

    // ── 卡片4 —— 音色选择 ──

    private View makeVoiceSelectorCard() {
        MaterialCardView card = card();
        LinearLayout col = col();

        col.addView(sectionTitle("②-a 选择音色（内置音色模型）"));
        col.addView(divider());

        voiceSectionLayout = new LinearLayout(ctx);
        voiceSectionLayout.setOrientation(LinearLayout.VERTICAL);
        voiceSectionLayout.setLayoutParams(layParam(MATCH, WRAP));
        voiceSectionLayout.setPadding(0, dp(6), 0, 0);

        LinearLayout row = null;
        for (int i = 0; i < BUILTIN_VOICES.length; i++) {
            if (i % 3 == 0) {
                row = hlay(LinearLayout.HORIZONTAL);
                row.setLayoutParams(new LinearLayout.LayoutParams(MATCH, WRAP));
                voiceSectionLayout.addView(row);
            }

            final int idx = i;
            MaterialCardView tag = makeVoiceTag(BUILTIN_VOICES[i].id, BUILTIN_VOICES[i].tag, i == 0);
            tag.setOnClickListener(v -> selectVoice(idx));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, WRAP, 1f);
            int m = (i % 3 == 0) ? 0 : dp(4);
            int r = (i % 3 == 2 || i == BUILTIN_VOICES.length - 1) ? 0 : dp(4);
            lp.setMargins(m, dp(3), r, dp(3));
            tag.setLayoutParams(lp);
            row.addView(tag);
        }

        col.addView(voiceSectionLayout);
        card.addView(col);
        return card;
    }

    private MaterialCardView makeVoiceTag(String id, String tag, boolean selected) {
        MaterialCardView card = new MaterialCardView(ctx);
        card.setRadius(dp(8));
        card.setCardElevation(0f);
        card.setCardBackgroundColor(selected ? CLR_PRIMARY_LT : CLR_CARD);
        card.setStrokeColor(selected ? CLR_PRIMARY : CLR_DIVIDER);
        card.setStrokeWidth(dp(1));

        LinearLayout inner = col();
        inner.setGravity(Gravity.CENTER);
        inner.setPadding(dp(6), dp(8), dp(6), dp(8));

        TextView name = txt(id, 14, selected ? CLR_PRIMARY : CLR_TEXT, true);
        name.setGravity(Gravity.CENTER);
        inner.addView(name);

        TextView desc = txt(tag, 11, CLR_TEXT_SUB, false);
        desc.setGravity(Gravity.CENTER);
        inner.addView(desc);

        card.addView(inner);
        card.setTag(Boolean.valueOf(selected));
        return card;
    }

    // ── 卡片5 —— 生成按钮 ──

    private View makeActionCard() {
        MaterialCardView card = card();
        LinearLayout col = col();

        col.addView(sectionTitle("③ 生成语音"));
        col.addView(divider());

        btnGenerate = new MaterialButton(ctx);
        btnGenerate.setText("🚀  开始合成");
        btnGenerate.setTextSize(15);
        btnGenerate.setBackgroundTintList(android.content.res.ColorStateList.valueOf(CLR_PRIMARY));
        btnGenerate.setTextColor(0xFFFFFFFF);
        btnGenerate.setCornerRadius(dp(24));
        btnGenerate.setPadding(dp(24), dp(12), dp(24), dp(12));
        btnGenerate.setLayoutParams(new LinearLayout.LayoutParams(MATCH, WRAP));
        btnGenerate.setOnClickListener(v -> startSynthesis());
        col.addView(btnGenerate);

        progressIndicator = new CircularProgressIndicator(ctx);
        progressIndicator.setIndeterminate(true);
        progressIndicator.setIndicatorColor(CLR_PRIMARY);
        progressIndicator.setVisibility(View.GONE);
        progressIndicator.setLayoutParams(layParam(WRAP, WRAP));
        ((LinearLayout.LayoutParams) progressIndicator.getLayoutParams()).gravity = Gravity.CENTER;
        col.addView(progressIndicator);

        tvStatus = txt("等待合成…", 13, CLR_TEXT_SUB, false);
        tvStatus.setPadding(0, dp(6), 0, 0);
        col.addView(tvStatus);

        card.addView(col);
        return card;
    }

    // ── 卡片6 —— 播放与保存 ──

    private View makeAudioCard() {
        MaterialCardView audioCard = card();
        LinearLayout col = col();

        col.addView(sectionTitle("④ 试听 · 保存"));
        col.addView(divider());

        tvAudioInfo = txt("尚未生成音频", 13, CLR_TEXT_SUB, false);
        tvAudioInfo.setPadding(0, dp(6), 0, dp(4));
        col.addView(tvAudioInfo);

        seekBar = new SeekBar(ctx);
        seekBar.setLayoutParams(new LinearLayout.LayoutParams(MATCH, dp(30)));
        seekBar.setMax(1000);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int prog, boolean fromUser) {
                if (fromUser && mediaPlayer != null && mediaPlayer.isPlaying()) {
                    mediaPlayer.seekTo((int) (prog / 1000f * mediaPlayer.getDuration()));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {}

            @Override
            public void onStopTrackingTouch(SeekBar sb) {}
        });
        col.addView(seekBar);

        LinearLayout btnRow = hlay(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER_VERTICAL);
        btnRow.setPadding(0, dp(6), 0, 0);

        btnPlay = new MaterialButton(ctx);
        btnPlay.setText("▶  播放");
        btnPlay.setTextSize(14);
        btnPlay.setTextColor(CLR_PRIMARY);
        btnPlay.setStrokeColor(colorState(CLR_PRIMARY));
        btnPlay.setCornerRadius(dp(20));
        btnPlay.setPadding(dp(20), dp(8), dp(20), dp(8));
        btnPlay.setOnClickListener(v -> togglePlayback());
        btnPlay.setEnabled(false);
        btnRow.addView(btnPlay);

        View gap = new View(ctx);
        gap.setLayoutParams(new LinearLayout.LayoutParams(dp(10), 1));
        btnRow.addView(gap);

        btnSave = new MaterialButton(ctx);
        btnSave.setText("💾  保存到手机");
        btnSave.setTextSize(14);
        btnSave.setTextColor(CLR_SUCCESS);
        btnSave.setStrokeColor(colorState(CLR_SUCCESS));
        btnSave.setCornerRadius(dp(20));
        btnSave.setPadding(dp(20), dp(8), dp(20), dp(8));
        btnSave.setOnClickListener(v -> saveAudio());
        btnSave.setEnabled(false);
        btnRow.addView(btnSave);

        col.addView(btnRow);

        tvAudioSaved = txt("", 12, CLR_SUCCESS, false);
        tvAudioSaved.setPadding(0, dp(6), 0, 0);
        tvAudioSaved.setVisibility(View.GONE);
        col.addView(tvAudioSaved);

        audioCard.addView(col);
        return audioCard;
    }

    // ── 页脚 ──

    private View makeFooter() {
        TextView footer = txt("Powered by Xiaomi MiMo TTS · API Key 仅用于测试", 10, 0xFFBDBDBD, false);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(12), 0, dp(24));
        return footer;
    }

    // ═══════════════ 模型选择逻辑 ═══════════════

    private void selectModel(int idx) {
        selectedModelIdx = idx;
        for (int i = 0; i < modelCardsLayout.getChildCount(); i++) {
            View child = modelCardsLayout.getChildAt(i);
            if (child instanceof MaterialCardView) {
                boolean sel = (i / 2) == idx;
                MaterialCardView tag = (MaterialCardView) child;
                tag.setCardBackgroundColor(sel ? CLR_PRIMARY : CLR_CARD);
                tag.setStrokeColor(sel ? CLR_PRIMARY : CLR_DIVIDER);
                View tv = ((ViewGroup) tag).getChildAt(0);
                if (tv instanceof TextView) ((TextView) tv).setTextColor(sel ? 0xFFFFFFFF : CLR_TEXT);
            }
        }
        updateModelDesc();
        updateVoiceArea();
    }

    private void updateModelDesc() {
        modelDescText.setText(MODELS[selectedModelIdx].desc);
    }

    private void updateVoiceArea() {
        TtsModel m = MODELS[selectedModelIdx];
        if (m.id.equals("mimo-v2.5-tts") || m.id.equals("mimo-v2-tts")) {
            voiceSectionLayout.setVisibility(View.VISIBLE);
            recordCard.setVisibility(View.GONE);
        } else if (m.id.equals("mimo-v2.5-tts-voicedesign")) {
            voiceSectionLayout.setVisibility(View.GONE);
            recordCard.setVisibility(View.GONE);
        } else if (m.id.equals("mimo-v2.5-tts-voiceclone")) {
            voiceSectionLayout.setVisibility(View.GONE);
            recordCard.setVisibility(View.VISIBLE);
        }
    }

    private void selectVoice(int idx) {
        selectedVoiceIdx = idx;
        for (int i = 0; i < voiceSectionLayout.getChildCount(); i++) {
            ViewGroup row = (ViewGroup) voiceSectionLayout.getChildAt(i);
            for (int j = 0; j < row.getChildCount(); j++) {
                View v = row.getChildAt(j);
                if (v instanceof MaterialCardView) {
                    int vi = i * 3 + j;
                    boolean sel = vi == idx;
                    MaterialCardView c = (MaterialCardView) v;
                    c.setCardBackgroundColor(sel ? CLR_PRIMARY_LT : CLR_CARD);
                    c.setStrokeColor(sel ? CLR_PRIMARY : CLR_DIVIDER);
                    ViewGroup inner = (ViewGroup) c.getChildAt(0);
                    for (int k = 0; k < inner.getChildCount(); k++) {
                        View tv = inner.getChildAt(k);
                        if (tv instanceof TextView && k == 0)
                            ((TextView) tv).setTextColor(sel ? CLR_PRIMARY : CLR_TEXT);
                    }
                }
            }
        }
    }

    // ═══════════════ 录音控制 ═══════════════

    private void toggleRecording() {
        if (isRecording) {
            stopRecording();
        } else {
            startRecording();
        }
    }

    @SuppressWarnings("MissingPermission")
    private void startRecording() {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1001);
            return;
        }

        File dir = new File(ctx.getFilesDir(), "tts_clones");
        if (!dir.exists()) dir.mkdirs();
        recordingFile = new File(dir, "clone_" + System.currentTimeMillis() + ".wav");

        isRecording = true;
        btnRecordToggle.setText("⏹  停止录音");
        btnRecordToggle.setTextColor(CLR_TEXT);
        tvRecordStatus.setText("  录音中…");

        try {
            fallbackRecorder = new MediaRecorder();
            fallbackRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            fallbackRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            fallbackRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            fallbackRecorder.setAudioSamplingRate(SAMPLE_RATE);
            fallbackRecorder.setAudioEncodingBitRate(128000);
            fallbackRecorder.setOutputFile(recordingFile.getAbsolutePath());
            fallbackRecorder.prepare();
            fallbackRecorder.start();
        } catch (Exception e) {
            Toast.makeText(ctx, "录音失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            isRecording = false;
            btnRecordToggle.setText("⏺  开始录音");
            tvRecordStatus.setText("  未录音");
        }
    }

    private void stopRecording() {
        if (!isRecording) return;
        isRecording = false;
        btnRecordToggle.setText("⏺  开始录音");
        btnRecordToggle.setTextColor(CLR_DANGER);

        try {
            if (fallbackRecorder != null) {
                fallbackRecorder.stop();
                fallbackRecorder.release();
                fallbackRecorder = null;
            }
        } catch (Exception ignored) {
        }

        if (recordingFile != null && recordingFile.exists() && recordingFile.length() > 100) {
            cloneAudioFile = recordingFile;
            tvRecordStatus.setText("  ✅ 已录制 " + (recordingFile.length() / 1024) + " KB");
            playFile(cloneAudioFile, true);
            Toast.makeText(ctx, "录音已保存，可用于声音克隆", Toast.LENGTH_SHORT).show();
        } else {
            tvRecordStatus.setText("  ⚠️ 录音失败或太短");
        }
    }

    // ═══════════════ 权限结果回调 ═══════════════

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1001 && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startRecording();
        } else {
            Toast.makeText(ctx, "需要录音权限", Toast.LENGTH_SHORT).show();
        }
    }

    // ═══════════════ TTS 合成 ═══════════════

    private void startSynthesis() {
        String text = textInput.getText().toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(ctx, "请输入要合成的文字", Toast.LENGTH_SHORT).show();
            return;
        }
        if (text.length() > 5000) {
            Toast.makeText(ctx, "文字过长（≤5000字）", Toast.LENGTH_SHORT).show();
            return;
        }

        TtsModel model = MODELS[selectedModelIdx];

        if (model.id.equals("mimo-v2.5-tts-voiceclone")
                && (cloneAudioFile == null || !cloneAudioFile.exists())) {
            Toast.makeText(ctx, "请先录制声音样本", Toast.LENGTH_SHORT).show();
            return;
        }

        setGenerating(true);
        stopPlayback();

        new Thread(() -> {
            try {
                byte[] audio = callTtsApi(text, model);
                if (audio != null) {
                    handler.post(() -> onSynthesisSuccess(audio));
                } else {
                    handler.post(() -> onSynthesisError("API 返回空结果"));
                }
            } catch (Exception e) {
                Log.e(TAG, "合成失败", e);
                handler.post(() -> onSynthesisError(e.getMessage()));
            }
        }).start();
    }

    private byte[] callTtsApi(String text, TtsModel model) throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", model.id);
        body.put("stream", false);

        JSONArray messages = new JSONArray();
        JSONObject assistantMsg = new JSONObject();
        assistantMsg.put("role", "assistant");
        assistantMsg.put("content", text);

        if (model.id.equals("mimo-v2.5-tts-voicedesign")) {
            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", "温柔知性的女性声音，语速适中");
            messages.put(userMsg);
        }
        messages.put(assistantMsg);
        body.put("messages", messages);

        JSONObject audioObj = new JSONObject();
        audioObj.put("format", "wav");

        if (model.id.equals("mimo-v2.5-tts-voiceclone")) {
            byte[] rawBytes = readFileBytes(cloneAudioFile);
            String b64 = Base64.encodeToString(rawBytes, Base64.NO_WRAP);
            audioObj.put("voice", "data:audio/wav;base64," + b64);
        } else {
            audioObj.put("voice", BUILTIN_VOICES[selectedVoiceIdx].id);
        }

        if (model.id.equals("mimo-v2.5-tts-voicedesign")) {
            audioObj.put("optimize_text_preview", true);
        }

        body.put("audio", audioObj);

        RequestBody reqBody = RequestBody.create(
                body.toString(), MediaType.parse("application/json"));
        Request request = new Request.Builder()
                .url(API_URL)
                .addHeader("Authorization", "Bearer " + BuildConfig.MIMO_API_KEY)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .post(reqBody)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errBody = response.body() != null ? response.body().string() : "";
                throw new IOException("HTTP " + response.code() + ": " + errBody);
            }
            String respBody = response.body().string();
            JSONObject json = new JSONObject(respBody);
            return Base64.decode(
                    json.getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getJSONObject("audio")
                            .getString("data"),
                    Base64.DEFAULT);
        }
    }

    private void onSynthesisSuccess(byte[] wavData) {
        setGenerating(false);
        try {
            File dir = new File(ctx.getFilesDir(), "tts_output");
            if (!dir.exists()) dir.mkdirs();
            generatedFile = new File(dir, "tts_" + System.currentTimeMillis() + ".wav");
            try (FileOutputStream fos = new FileOutputStream(generatedFile)) {
                fos.write(wavData);
            }
            tvAudioInfo.setText("✅ 已生成  " + (wavData.length / 1024) + " KB  ·  " + MODELS[selectedModelIdx].name);
            btnPlay.setEnabled(true);
            btnSave.setEnabled(true);
            tvAudioSaved.setVisibility(View.GONE);
            seekBar.setProgress(0);
            playFile(generatedFile, false);
        } catch (Exception e) {
            Toast.makeText(ctx, "保存音频失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void onSynthesisError(String msg) {
        setGenerating(false);
        tvStatus.setText("❌ 合成失败：" + msg);
    }

    private void setGenerating(boolean gen) {
        btnGenerate.setEnabled(!gen);
        progressIndicator.setVisibility(gen ? View.VISIBLE : View.GONE);
        tvStatus.setText(gen ? "正在合成，请稍候…" : "等待合成…");
    }

    // ═══════════════ 播放 ═══════════════

    private void togglePlayback() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            stopPlayback();
        } else if (generatedFile != null && generatedFile.exists()) {
            playFile(generatedFile, false);
        }
    }

    private void playFile(File file, boolean autoStop) {
        stopPlayback();
        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(file.getAbsolutePath());
            mediaPlayer.prepare();
            mediaPlayer.start();
            btnPlay.setText("⏸  暂停");

            Runnable updater = new Runnable() {
                @Override
                public void run() {
                    if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                        seekBar.setProgress((int) (1000f * mediaPlayer.getCurrentPosition() / mediaPlayer.getDuration()));
                        handler.postDelayed(this, 200);
                    }
                }
            };
            handler.post(updater);

            mediaPlayer.setOnCompletionListener(mp -> {
                seekBar.setProgress(1000);
                btnPlay.setText("▶  播放");
                if (autoStop) mp.release();
            });
        } catch (Exception e) {
            Toast.makeText(ctx, "播放失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void stopPlayback() {
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
            btnPlay.setText("▶  播放");
            seekBar.setProgress(0);
        }
    }

    // ═══════════════ 保存 ═══════════════

    private void saveAudio() {
        if (generatedFile == null || !generatedFile.exists()) return;
        File dir = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_MUSIC);
        File dest = new File(dir, "TTS_" + System.currentTimeMillis() + ".wav");
        try {
            copyFile(generatedFile, dest);
            tvAudioSaved.setVisibility(View.VISIBLE);
            tvAudioSaved.setText("✅ 已保存: " + dest.getAbsolutePath());
            Toast.makeText(ctx, "已保存到 Music 文件夹", Toast.LENGTH_SHORT).show();
            android.media.MediaScannerConnection.scanFile(ctx,
                    new String[]{dest.getAbsolutePath()}, null, null);
        } catch (Exception e) {
            Toast.makeText(ctx, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // ═══════════════ UI 工具方法 ═══════════════

    private static final int MATCH = LinearLayout.LayoutParams.MATCH_PARENT;
    private static final int WRAP = LinearLayout.LayoutParams.WRAP_CONTENT;

    private int dp(float dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, ctx.getResources().getDisplayMetrics());
    }

    private LinearLayout hlay(int orientation) {
        LinearLayout l = new LinearLayout(ctx);
        l.setOrientation(orientation);
        l.setLayoutParams(layParam(MATCH, WRAP));
        return l;
    }

    private LinearLayout col() {
        LinearLayout l = hlay(LinearLayout.VERTICAL);
        l.setPadding(dp(14), dp(12), dp(14), dp(12));
        return l;
    }

    private MaterialCardView card() {
        MaterialCardView c = new MaterialCardView(ctx);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.setMargins(0, 0, 0, dp(10));
        c.setLayoutParams(lp);
        c.setRadius(dp(12));
        c.setCardElevation(dp(2));
        c.setCardBackgroundColor(CLR_CARD);
        return c;
    }

    private TextView txt(String text, float sp, int color, boolean bold) {
        TextView t = new TextView(ctx);
        t.setText(text);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(null, bold ? Typeface.BOLD : Typeface.NORMAL);
        t.setLayoutParams(layParam(WRAP, WRAP));
        return t;
    }

    private TextView sectionTitle(String text) {
        TextView t = txt(text, 15, CLR_PRIMARY, true);
        t.setPadding(0, 0, 0, dp(2));
        return t;
    }

    private View divider() {
        View d = new View(ctx);
        d.setBackgroundColor(CLR_DIVIDER);
        d.setLayoutParams(new LinearLayout.LayoutParams(MATCH, dp(1)));
        return d;
    }

    private TextView tip(String text) {
        TextView t = txt("💡 " + text, 12, CLR_TEXT_SUB, false);
        t.setPadding(dp(4), dp(4), dp(4), 0);
        return t;
    }

    private LinearLayout.LayoutParams layParam(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    private android.content.res.ColorStateList colorState(int color) {
        return android.content.res.ColorStateList.valueOf(color);
    }

    private GradientDrawable roundBg(int fill, int corner, int strokeW, int strokeC) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(corner);
        d.setColor(fill);
        if (strokeW > 0) d.setStroke(strokeW, strokeC);
        return d;
    }

    // ═══════════════ 文件工具 ═══════════════

    private byte[] readFileBytes(File f) throws IOException {
        try (FileInputStream fis = new FileInputStream(f);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = fis.read(buf)) != -1) bos.write(buf, 0, n);
            return bos.toByteArray();
        }
    }

    private static void copyFile(File src, File dst) throws IOException {
        dst.getParentFile().mkdirs();
        try (FileInputStream in = new FileInputStream(src);
             FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        }
    }
}
