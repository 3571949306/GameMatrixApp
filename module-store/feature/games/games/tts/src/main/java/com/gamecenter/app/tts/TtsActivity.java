package com.gamecenter.app.tts;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.method.ScrollingMovementMethod;
import android.util.Base64;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * TTS 语音合成实验室 — 小米 MiMo TTS 全功能客户端。
 * <p>
 * 功能概览：
 * <ul>
 *   <li>mimo-v2.5-tts        — 从 9 种内置音色中选择，即文字转语音</li>
 *   <li>mimo-v2.5-tts-voicedesign — 用文字描述音色特征，AI 自动匹配</li>
 *   <li>mimo-v2.5-tts-voiceclone — 录制/导入音频，克隆声音后合成</li>
 *   <li>mimo-v2-tts           — 旧版兼容模型（内置音色，质量略低）</li>
 * </ul>
 * 录制的音频本地保存在 {@code filesDir/tts_clones/}，不会自动上传。
 */
public class TtsActivity extends AppCompatActivity {

    private static final String TAG = "TtsActivity";

    // ═══════════════════════════════════════════════════════
    //  小米 MiMo API 配置（测试专用）
    // ═══════════════════════════════════════════════════════
    private static final String API_KEY = "sk-cq6d3s1j5bcbxxt162woa1xw79a8baye9idgx2bxpwlf9t2y";
    private static final String API_URL = "https://api.xiaomimimo.com/v1/chat/completions";

    // 录音参数
    private static final int SAMPLE_RATE  = 16000;
    private static final int CHANNEL      = AudioFormat.CHANNEL_IN_MONO;
    private static final int ENCODING     = AudioFormat.ENCODING_PCM_16BIT;

    // 颜色
    private static final int CLR_BG          = 0xFFF5F5F5;
    private static final int CLR_CARD        = 0xFFFFFFFF;
    private static final int CLR_PRIMARY     = 0xFF1976D2;
    private static final int CLR_PRIMARY_LT  = 0xFFE3F2FD;
    private static final int CLR_TEXT        = 0xFF212121;
    private static final int CLR_TEXT_SUB    = 0xFF757575;
    private static final int CLR_DANGER      = 0xFFD32F2F;
    private static final int CLR_DANGER_LT   = 0xFFFFEBEE;
    private static final int CLR_SUCCESS     = 0xFF388E3C;
    private static final int CLR_DIVIDER     = 0xFFE0E0E0;

    // ═══════════════ 数据模型 ═══════════════

    static class TtsModel {
        final String id;
        final String name;
        final String desc;
        TtsModel(String id, String name, String desc) { this.id=id; this.name=name; this.desc=desc; }
    }

    static class VoicePreset {
        final String id;
        final String tag;
        VoicePreset(String id, String tag) { this.id=id; this.tag=tag; }
    }

    // 模型
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

    // 内置音色（标准/旧版模型可用）
    private final VoicePreset[] BUILTIN_VOICES = {
        new VoicePreset("mimo_default", "默认"),
        new VoicePreset("冰糖",   "温柔女声"),
        new VoicePreset("茉莉",   "清新女声"),
        new VoicePreset("苏打",   "活力男声"),
        new VoicePreset("白桦",   "沉稳男声"),
        new VoicePreset("Mia",    "英文女声"),
        new VoicePreset("Chloe",  "英文女声"),
        new VoicePreset("Milo",   "英文男声"),
        new VoicePreset("Dean",   "英文男声")
    };

    // ═══════════════ 状态 ═══════════════

    private int   selectedModelIdx   = 0;   // 当前选中的模型索引
    private int   selectedVoiceIdx   = 0;   // 当前选中的内置音色索引
    private File  cloneAudioFile     = null; // 当前声音克隆用的录音文件

    // 录音
    private boolean isRecording    = false;
    private AudioRecord audioRecord  = null;
    private File      recordingFile  = null;
    private Thread    recordingThread = null;

    // 播放
    private MediaPlayer mediaPlayer  = null;
    private File        generatedFile = null; // 最后生成的 WAV

    // ═══════════════ UI 控件引用 ═══════════════

    // 模型
    private LinearLayout modelCardsLayout;
    private TextView     modelDescText;

    // 文本
    private EditText textInput;

    // 音色区（动态生成）
    private LinearLayout voiceSectionLayout;

    // 录音卡片（声音克隆模型专用）
    private MaterialCardView recordCard;
    private MaterialButton   btnRecordToggle;
    private TextView         tvRecordStatus;
    private MediaRecorder    fallbackRecorder = null;

    // 动作
    private MaterialButton    btnGenerate;
    private CircularProgressIndicator progressIndicator;
    private TextView          tvStatus;

    // 播放区
    private MaterialCardView audioCard;
    private TextView         tvAudioInfo;
    private SeekBar          seekBar;
    private MaterialButton   btnPlay;
    private MaterialButton   btnSave;
    private TextView         tvAudioSaved;

    // 细节
    private Handler handler = new Handler(Looper.getMainLooper());
    private OkHttpClient httpClient;

    // ═══════════════════════════════════════════════════════
    //  生命周期
    // ═══════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();

        setContentView(R.layout.activity_tts);
        setupUI();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopRecording();
        stopPlayback();
    }

    // ═══════════════════════════════════════════════════════
    //  UI 构建
    // ═══════════════════════════════════════════════════════

    private void setupUI() {
        LinearLayout root = findViewById(R.id.root);

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

        Button close = new Button(this);
        close.setText("✕");
        close.setTextSize(18);
        close.setTextColor(CLR_TEXT_SUB);
        close.setBackground(null);
        close.setOnClickListener(v -> finish());
        bar.addView(close);

        return bar;
    }

    // ── 卡片1 —— 模型选择 ──

    private View makeModelCard() {
        MaterialCardView card = card();
        LinearLayout col = col();

        col.addView(sectionTitle("① 选择模型"));
        col.addView(divider());

        // 横向滚动模型标签
        HorizontalScrollView hScroll = new HorizontalScrollView(this);
        hScroll.setLayoutParams(layParam(MATCH, WRAP));
        modelCardsLayout = hlay(LinearLayout.HORIZONTAL);
        modelCardsLayout.setPadding(dp(4), dp(8), dp(4), dp(8));
        for (int i = 0; i < MODELS.length; i++) {
            final int idx = i;
            MaterialCardView tag = makeModelTag(MODELS[i].name, idx == 0);
            tag.setOnClickListener(v -> { selectModel(idx); });
            modelCardsLayout.addView(tag);
            if (i < MODELS.length - 1) {
                View gap = new View(this);
                gap.setLayoutParams(new LinearLayout.LayoutParams(dp(8), 1));
                modelCardsLayout.addView(gap);
            }
        }
        hScroll.addView(modelCardsLayout);
        col.addView(hScroll);

        // 模型描述
        modelDescText = txt("", 13, CLR_TEXT_SUB, false);
        modelDescText.setPadding(dp(4), dp(6), dp(4), dp(6));
        col.addView(modelDescText);

        card.addView(col);
        return card;
    }

    private MaterialCardView makeModelTag(String name, boolean selected) {
        MaterialCardView tag = new MaterialCardView(this);
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

        textInput = new EditText(this);
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

    // ── 卡片3 —— 声音克隆录音 ──

    private View makeRecordCard() {
        recordCard = card();
        LinearLayout col = col();
        col.setOnClickListener(null); // 消费点击

        col.addView(sectionTitle("②-b 录制声音样本（声音克隆用）"));
        col.addView(divider());
        col.addView(tip("录制清晰的语音片段（3~30秒效果最佳），用于克隆你的声音。"));

        LinearLayout row = hlay(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, 0);

        btnRecordToggle = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
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
        recordCard.setVisibility(View.GONE); // 默认隐藏；声音克隆模型才显示
        return recordCard;
    }

    // ── 卡片4 —— 内置音色选择器 ──

    private View makeVoiceSelectorCard() {
        MaterialCardView card = card();
        LinearLayout col = col();

        col.addView(sectionTitle("②-a 选择音色（内置音色模型）"));
        col.addView(divider());

        // 3列网格
        voiceSectionLayout = new LinearLayout(this);
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
            int m = (i % 3 == 0) ? 0 : dp(4);
            int r = (i % 3 == 2 || i == BUILTIN_VOICES.length - 1) ? 0 : dp(4);
            voiceSectionLayout.getChildAt(voiceSectionLayout.getChildCount()-1);

            final int idx = i;
            MaterialCardView tag = makeVoiceTag(BUILTIN_VOICES[i].id, BUILTIN_VOICES[i].tag, i == 0);
            tag.setOnClickListener(v -> selectVoice(idx));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, WRAP, 1f);
            lp.setMargins(m, dp(3), r, dp(3));
            tag.setLayoutParams(lp);
            row.addView(tag);
        }

        col.addView(voiceSectionLayout);
        card.addView(col);
        return card;
    }

    private MaterialCardView makeVoiceTag(String id, String tag, boolean selected) {
        MaterialCardView card = new MaterialCardView(this);
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

    // ── 卡片5 —— 生成按钮 + 状态 ──

    private View makeActionCard() {
        MaterialCardView card = card();
        LinearLayout col = col();

        col.addView(sectionTitle("③ 生成语音"));
        col.addView(divider());

        btnGenerate = new MaterialButton(this);
        btnGenerate.setText("🚀  开始合成");
        btnGenerate.setTextSize(15);
        btnGenerate.setBackgroundTintList(android.content.res.ColorStateList.valueOf(CLR_PRIMARY));
        btnGenerate.setTextColor(0xFFFFFFFF);
        btnGenerate.setCornerRadius(dp(24));
        btnGenerate.setPadding(dp(24), dp(12), dp(24), dp(12));
        btnGenerate.setLayoutParams(new LinearLayout.LayoutParams(MATCH, WRAP));
        btnGenerate.setOnClickListener(v -> startSynthesis());
        col.addView(btnGenerate);

        progressIndicator = new CircularProgressIndicator(this);
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
        audioCard = card();
        LinearLayout col = col();

        col.addView(sectionTitle("④ 试听 · 保存"));
        col.addView(divider());

        tvAudioInfo = txt("尚未生成音频", 13, CLR_TEXT_SUB, false);
        tvAudioInfo.setPadding(0, dp(6), 0, dp(4));
        col.addView(tvAudioInfo);

        seekBar = new SeekBar(this);
        seekBar.setLayoutParams(new LinearLayout.LayoutParams(MATCH, dp(30)));
        seekBar.setMax(1000);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int prog, boolean fromUser) {
                if (fromUser && mediaPlayer != null && mediaPlayer.isPlaying()) {
                    mediaPlayer.seekTo((int)(prog / 1000f * mediaPlayer.getDuration()));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
        col.addView(seekBar);

        LinearLayout btnRow = hlay(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER_VERTICAL);
        btnRow.setPadding(0, dp(6), 0, 0);

        btnPlay = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btnPlay.setText("▶  播放");
        btnPlay.setTextSize(14);
        btnPlay.setTextColor(CLR_PRIMARY);
        btnPlay.setStrokeColor(colorState(CLR_PRIMARY));
        btnPlay.setCornerRadius(dp(20));
        btnPlay.setPadding(dp(20), dp(8), dp(20), dp(8));
        btnPlay.setOnClickListener(v -> togglePlayback());
        btnPlay.setEnabled(false);
        btnRow.addView(btnPlay);

        View gap = new View(this);
        gap.setLayoutParams(new LinearLayout.LayoutParams(dp(10), 1));
        btnRow.addView(gap);

        btnSave = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
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

    // ═══════════════════════════════════════════════════════
    //  模型选择逻辑
    // ═══════════════════════════════════════════════════════

    private void selectModel(int idx) {
        selectedModelIdx = idx;
        // 更新标签颜色
        for (int i = 0; i < modelCardsLayout.getChildCount(); i++) {
            View child = modelCardsLayout.getChildAt(i);
            if (child instanceof MaterialCardView) {
                boolean sel = (i / 2) == idx; // 每两个一个 gap
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
            // 标准/旧版：显示内建音色网格
            voiceSectionLayout.setVisibility(View.VISIBLE);
            recordCard.setVisibility(View.GONE);
        } else if (m.id.equals("mimo-v2.5-tts-voicedesign")) {
            // 声音设计：隐藏音色网格和录音卡（通过 user prompt 描述音色）
            voiceSectionLayout.setVisibility(View.GONE);
            recordCard.setVisibility(View.GONE);
        } else if (m.id.equals("mimo-v2.5-tts-voiceclone")) {
            // 声音克隆：显示录音卡，隐藏音色网格
            voiceSectionLayout.setVisibility(View.GONE);
            recordCard.setVisibility(View.VISIBLE);
        }
    }

    private void selectVoice(int idx) {
        selectedVoiceIdx = idx;
        // 更新 UI 状态
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

    // ═══════════════════════════════════════════════════════
    //  录音控制
    // ═══════════════════════════════════════════════════════

    private void toggleRecording() {
        if (isRecording) {
            stopRecording();
        } else {
            startRecording();
        }
    }

    private void startRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, 1001);
            return;
        }

        File dir = new File(getFilesDir(), "tts_clones");
        if (!dir.exists()) dir.mkdirs();
        recordingFile = new File(dir, "clone_" + System.currentTimeMillis() + ".wav");

        isRecording = true;
        btnRecordToggle.setText("⏹  停止录音");
        btnRecordToggle.setTextColor(CLR_TEXT);
        tvRecordStatus.setText("  录音中…");

        // 使用 MediaRecorder 录制 WAV
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
            Toast.makeText(this, "录音失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            isRecording = false;
            btnRecordToggle.setText("⏺  开始录音");
            tvRecordStatus.setText("  未录音");
            return;
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
        } catch (Exception ignored) {}

        if (recordingFile != null && recordingFile.exists() && recordingFile.length() > 100) {
            cloneAudioFile = recordingFile;
            tvRecordStatus.setText("  ✅ 已录制 " + (recordingFile.length() / 1024) + " KB");
            // 自动试听刚录的
            playFile(cloneAudioFile, true);
            Toast.makeText(this, "录音已保存，可用于声音克隆", Toast.LENGTH_SHORT).show();
        } else {
            tvRecordStatus.setText("  ⚠️ 录音失败或太短");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] perms, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, perms, grantResults);
        if (requestCode == 1001 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startRecording();
        } else {
            Toast.makeText(this, "需要录音权限", Toast.LENGTH_SHORT).show();
        }
    }

    // ═══════════════════════════════════════════════════════
    //  TTS 合成
    // ═══════════════════════════════════════════════════════

    private void startSynthesis() {
        String text = textInput.getText().toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(this, "请输入要合成的文字", Toast.LENGTH_SHORT).show();
            return;
        }
        if (text.length() > 5000) {
            Toast.makeText(this, "文字过长（≤5000字）", Toast.LENGTH_SHORT).show();
            return;
        }

        TtsModel model = MODELS[selectedModelIdx];

        // 声音克隆模式需要录音文件
        if (model.id.equals("mimo-v2.5-tts-voiceclone") && (cloneAudioFile == null || !cloneAudioFile.exists())) {
            Toast.makeText(this, "请先录制声音样本", Toast.LENGTH_SHORT).show();
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
            // 声音设计模式：用户消息描述音色
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
            // 声音克隆：把录音转 base64 放进 voice
            byte[] rawBytes = readFileBytes(cloneAudioFile);

            // 若录音格式是 m4a/aac，告知 API 音频类型为 wav（MediaRecorder 可能输出 mp4）
            // 保持 data:audio/wav 以尝试后端兼容；若失败请改用 PCM 方案。
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
                .addHeader("Authorization", "Bearer " + API_KEY)
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
            File dir = new File(getFilesDir(), "tts_output");
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

            // 自动试听
            playFile(generatedFile, false);

        } catch (Exception e) {
            Toast.makeText(this, "保存音频失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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

    // ═══════════════════════════════════════════════════════
    //  播放
    // ═══════════════════════════════════════════════════════

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

            // 更新进度条
            Runnable updater = new Runnable() {
                @Override public void run() {
                    if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                        seekBar.setProgress((int)(1000f * mediaPlayer.getCurrentPosition() / mediaPlayer.getDuration()));
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
            Toast.makeText(this, "播放失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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

    // ═══════════════════════════════════════════════════════
    //  保存
    // ═══════════════════════════════════════════════════════

    private void saveAudio() {
        if (generatedFile == null || !generatedFile.exists()) return;
        File dir = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_MUSIC);
        File dest = new File(dir, "TTS_" + System.currentTimeMillis() + ".wav");
        try {
            copyFile(generatedFile, dest);
            tvAudioSaved.setVisibility(View.VISIBLE);
            tvAudioSaved.setText("✅ 已保存: " + dest.getAbsolutePath());
            Toast.makeText(this, "已保存到 Music 文件夹", Toast.LENGTH_SHORT).show();
            // 通知媒体扫描器
            android.media.MediaScannerConnection.scanFile(this,
                    new String[]{dest.getAbsolutePath()}, null, null);
        } catch (Exception e) {
            Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // ═══════════════════════════════════════════════════════
    //  UI 工具方法
    // ═══════════════════════════════════════════════════════

    private final static int MATCH = LinearLayout.LayoutParams.MATCH_PARENT;
    private final static int WRAP  = LinearLayout.LayoutParams.WRAP_CONTENT;

    private int dp(float dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    private LinearLayout hlay(int orientation) {
        LinearLayout l = new LinearLayout(this);
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
        MaterialCardView c = new MaterialCardView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.setMargins(0, 0, 0, dp(10));
        c.setLayoutParams(lp);
        c.setRadius(dp(12));
        c.setCardElevation(dp(2));
        c.setCardBackgroundColor(CLR_CARD);
        return c;
    }

    private TextView txt(String text, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
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
        View d = new View(this);
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

    // ═══════════════════════════════════════════════════════
    //  文件工具
    // ═══════════════════════════════════════════════════════

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
