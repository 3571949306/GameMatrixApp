package com.gamecenter.app.tools;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.gamecenter.app.R;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SoundMeterToolBinder implements ToolBinder {

    private MediaRecorder recorder;
    private boolean running = false;
    private static final int SAMPLE_INTERVAL_MS = 80;
    private float peakDb = -160f;
    private float sumDb = 0f;
    private int sampleCount = 0;

    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        if (contentView == null) return;

        Button btnStart = contentView.findViewById(R.id.btn_sound_start);
        ProgressBar pbLevel = contentView.findViewById(R.id.pb_sound_level);
        TextView tvCurrent = contentView.findViewById(R.id.tv_sound_current);
        TextView tvMax = contentView.findViewById(R.id.tv_sound_max);
        TextView tvAvg = contentView.findViewById(R.id.tv_sound_avg);
        TextView tvStatus = contentView.findViewById(R.id.tv_sound_status);

        Context appContext = context.getApplicationContext();
        boolean hasPermission = appContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;

        if (!hasPermission) {
            if (tvStatus != null) tvStatus.setText("麦克风未授权");
            if (btnStart != null) {
                btnStart.setOnClickListener(v -> {
                    try {
                        Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        intent.setData(android.net.Uri.parse("package:" + context.getPackageName()));
                        if (!(context instanceof android.app.Activity)) {
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        }
                        context.startActivity(intent);
                    } catch (Exception ignored) {
                    }
                });
            }
            return;
        }

        if (btnStart != null) {
            btnStart.setOnClickListener(v -> {
                if (!running) {
                    if (startRecording(context, tvStatus)) {
                        btnStart.setText("停止检测");
                        peakDb = -160f;
                        sumDb = 0f;
                        sampleCount = 0;
                    }
                } else {
                    stopRecording(tvStatus);
                    btnStart.setText("开始检测");
                }
            });
        }

        if (contentView instanceof View) {
            contentView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                    stopRecording(null);
                }
            });
        }

        final AtomicBoolean alive = new AtomicBoolean(true);

        if (executor != null) {
            executor.submit(() -> {
                while (alive.get()) {
                    try {
                        Thread.sleep(SAMPLE_INTERVAL_MS);
                    } catch (InterruptedException e) {
                        break;
                    }
                    if (!running || recorder == null) continue;
                    try {
                        int maxAmp = recorder.getMaxAmplitude();
                        float db = -160f;
                        if (maxAmp > 0) {
                            db = 20f * (float) Math.log10(maxAmp / 32767f) - 160f;
                        }

                        float finalDb = db;
                        if (contentView != null) {
                            contentView.post(() -> {
                                if (pbLevel != null) {
                                    int progress = Math.max(0, Math.min(100, (int) finalDb + 100));
                                    pbLevel.setProgress(progress);
                                }
                                if (tvCurrent != null) {
                                    tvCurrent.setText(String.format(Locale.getDefault(), "当前: %.1f dB", finalDb));
                                }
                            });
                        }

                        if (finalDb > peakDb) peakDb = finalDb;
                        sumDb += finalDb;
                        sampleCount++;

                        if (contentView != null) {
                            contentView.post(() -> {
                                if (tvMax != null) {
                                    tvMax.setText(String.format(Locale.getDefault(), "峰值: %.1f dB", peakDb));
                                }
                                if (tvAvg != null && sampleCount > 0) {
                                    float avg = sumDb / sampleCount;
                                    tvAvg.setText(String.format(Locale.getDefault(), "平均: %.1f dB", avg));
                                }
                            });
                        }
                    } catch (Exception ignored) {
                    }
                }
            });
        }
    }

    private boolean startRecording(Context context, TextView tvStatus) {
        try {
            recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            recorder.setOutputFile("/dev/null");
            recorder.prepare();
            recorder.start();
            running = true;
            if (tvStatus != null) tvStatus.setText("正在检测...");
            return true;
        } catch (IOException | RuntimeException e) {
            if (tvStatus != null) tvStatus.setText("启动失败");
            recorder = null;
            running = false;
            return false;
        }
    }

    private void stopRecording(TextView tvStatus) {
        running = false;
        if (recorder != null) {
            try {
                recorder.stop();
            } catch (RuntimeException ignored) {
            }
            try {
                recorder.release();
            } catch (RuntimeException ignored) {
            }
            recorder = null;
        }
        if (tvStatus != null) tvStatus.setText("已停止");
    }
}
