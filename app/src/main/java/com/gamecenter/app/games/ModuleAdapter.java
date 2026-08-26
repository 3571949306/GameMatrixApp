package com.gamecenter.app.games;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import com.gamecenter.app.R;
import com.gamecenter.app.interfaces.IModuleStore.DownloadCallback;
import com.gamecenter.app.games.ui.GameLauncherHelper;
import com.gamecenter.app.models.ModuleInfo;
import com.gamecenter.app.games.BuiltInModuleUpdater;
import java.util.ArrayList;
import java.util.List;

/**
 * 游戏列表适配器（支持"检查更新"按钮）。
 *
 * <p>在游戏列表中，每个游戏条目可展示：
 * <ul>
 *   <li>游戏图标、名称、描述</li>
 *   <li>版本号（内置/外置）</li>
 *   <li>"检查更新"按钮（点击后检查是否有更新版本）</li>
 *   <li>"更新"按钮（当检测到更新时显示，点击下载并应用更新）</li>
 *   <li>下载进度条（更新下载中显示）</li>
 * </ul>
 *
 * <p>版本比较策略遵循架构决策2：
 * <ul>
 *   <li>主逻辑：版本号判断（内置 vs 商店），商店版本更高则提示更新</li>
 *   <li>兜底机制：ClassLoader 优先级（如果内置版本加载失败，自动使用外置版本）</li>
 * </ul>
 *
 * @author Software Engineer (Alex)
 * @version 1.0
 * @since 2026-05-27
 */
public class ModuleAdapter extends RecyclerView.Adapter<ModuleAdapter.GameViewHolder> {

    /** 游戏条目列表 */
    private final List<GameRegistry.Entry> gameEntries;

    /** Android Context */
    private final Context context;

    /** 内置模块更新器 */
    private final BuiltInModuleUpdater updater;

    /** 更新信息缓存（游戏 ID -> ModuleInfo） */
    private final List<ModuleInfo> availableUpdates;

    /** 下载进度缓存（游戏 ID -> 进度 0-100） */
    private final List<Integer> downloadProgress;

    /** 下载状态缓存（游戏 ID -> 是否正在下载） */
    private final List<Boolean> downloading;

    /**
     * 构造函数。
     *
     * @param context      Android Context
     * @param gameEntries  游戏条目列表
     */
    public ModuleAdapter(@NonNull Context context,
                         @NonNull List<GameRegistry.Entry> gameEntries) {
        this.context = context != null ? context.getApplicationContext() : null;
        this.gameEntries = gameEntries != null ? gameEntries : new ArrayList<>();
        this.updater = BuiltInModuleUpdater.getInstance(this.context);
        this.availableUpdates = new ArrayList<>();
        this.downloadProgress = new ArrayList<>();
        this.downloading = new ArrayList<>();

        // 初始化缓存列表
        for (int i = 0; i < this.gameEntries.size(); i++) {
            availableUpdates.add(null);
            downloadProgress.add(0);
            downloading.add(false);
        }
    }

    @NonNull
    @Override
    public GameViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_game_card, parent, false);
        return new GameViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GameViewHolder holder, int position) {
        if (position < 0 || position >= gameEntries.size()) {
            return;
        }

        GameRegistry.Entry entry = gameEntries.get(position);

        // 设置基本信息
        holder.gameName.setText(entry.name);
        holder.gameDesc.setText(entry.desc);

        if (entry.iconRes != 0) {
            holder.gameIcon.setImageResource(entry.iconRes);
        }

        // 设置版本号
        int builtInVersion = GameRegistry.getBuiltInVersionCode(entry.id);
        holder.versionText.setText(holder.itemView.getContext().getString(R.string.version_format_simple, String.valueOf(builtInVersion)));

        // 设置更新状态
        ModuleInfo updateInfo = availableUpdates.get(position);
        boolean isDownloading = downloading.get(position);
        int progress = downloadProgress.get(position);

        if (isDownloading) {
            // 下载中状态
            holder.checkUpdateButton.setVisibility(View.GONE);
            holder.updateButton.setVisibility(View.GONE);
            holder.progressBar.setVisibility(View.VISIBLE);
            holder.progressBar.setProgress(progress);
            holder.progressText.setVisibility(View.VISIBLE);
            holder.progressText.setText(progress + "%");
        } else if (updateInfo != null) {
            // 有可用更新
            holder.checkUpdateButton.setVisibility(View.GONE);
            holder.updateButton.setVisibility(View.VISIBLE);
            holder.updateButton.setText(holder.itemView.getContext().getString(R.string.module_update_button_format, updateInfo.getVersionName()));
            holder.progressBar.setVisibility(View.GONE);
            holder.progressText.setVisibility(View.GONE);

            holder.updateButton.setOnClickListener(v -> {
                startUpdate(entry, updateInfo, position);
            });
        } else {
            // 默认状态：显示"检查更新"按钮
            holder.checkUpdateButton.setVisibility(View.VISIBLE);
            holder.updateButton.setVisibility(View.GONE);
            holder.progressBar.setVisibility(View.GONE);
            holder.progressText.setVisibility(View.GONE);

            holder.checkUpdateButton.setOnClickListener(v -> {
                checkForUpdate(entry, position);
            });
        }

        // 设置游戏点击事件
        holder.itemView.setOnClickListener(v -> {
            GameLauncherHelper.launchGameWithDialog(context, entry.id);
        });
    }

    @Override
    public int getItemCount() {
        return gameEntries.size();
    }

    /**
     * 检查指定游戏的更新。
     *
     * @param entry    游戏条目
     * @param position 列表位置
     */
    private void checkForUpdate(@NonNull GameRegistry.Entry entry, int position) {
        if (context == null) return;

        // 禁用按钮防止重复点击
        notifyItemChanged(position);

        // 异步检查更新
        new Thread(() -> {
            ModuleInfo updateInfo = GameRegistry.checkBuiltInGameUpdate(context, entry.id);

            if (updateInfo != null) {
                availableUpdates.set(position, updateInfo);
            }

            // 更新 UI（切回主线程）
            if (context instanceof android.app.Activity) {
                ((android.app.Activity) context).runOnUiThread(() -> {
                    notifyItemChanged(position);
                    if (updateInfo != null) {
                        Toast.makeText(context,
                                entry.name + " 有更新: v" + updateInfo.getVersionName(),
                                Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(context,
                                entry.name + " 已是最新版本",
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).start();
    }

    /**
     * 开始下载并应用更新。
     *
     * @param entry      游戏条目
     * @param updateInfo 更新信息
     * @param position    列表位置
     */
    private void startUpdate(@NonNull GameRegistry.Entry entry,
                             @NonNull ModuleInfo updateInfo,
                             int position) {
        if (context == null) return;

        downloading.set(position, true);
        downloadProgress.set(position, 0);
        notifyItemChanged(position);

        // 开始下载
        updater.downloadUpdate(entry.id, new DownloadCallback() {
            @Override
            public void onProgress(@NonNull String moduleId, int progress, long downloadedBytes,
                                   long totalBytes) {
                downloadProgress.set(position, progress);

                if (context instanceof android.app.Activity) {
                    ((android.app.Activity) context).runOnUiThread(() -> {
                        notifyItemChanged(position);
                    });
                }
            }

            @Override
            public void onSuccess(@NonNull String moduleId, @NonNull String filePath) {
                downloading.set(position, false);
                downloadProgress.set(position, 100);

                // 应用更新
                boolean applied = updater.applyUpdate(moduleId, filePath);

                if (context instanceof android.app.Activity) {
                    ((android.app.Activity) context).runOnUiThread(() -> {
                        if (applied) {
                            availableUpdates.set(position, null);
                            Toast.makeText(context,
                                    entry.name + " 更新成功",
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(context,
                                    entry.name + " 更新失败，已回退到内置版本",
                                    Toast.LENGTH_SHORT).show();
                        }
                        notifyItemChanged(position);
                    });
                }
            }

            @Override
            public void onError(@NonNull String moduleId, int errorCode,
                                @NonNull String errorMessage) {
                downloading.set(position, false);
                downloadProgress.set(position, 0);

                if (context instanceof android.app.Activity) {
                    ((android.app.Activity) context).runOnUiThread(() -> {
                        Toast.makeText(context,
                                entry.name + " 更新失败: " + errorMessage,
                                Toast.LENGTH_SHORT).show();
                        notifyItemChanged(position);
                    });
                }
            }

            @Override
            public void onCancelled(@NonNull String moduleId) {
                downloading.set(position, false);
                downloadProgress.set(position, 0);

                if (context instanceof android.app.Activity) {
                    ((android.app.Activity) context).runOnUiThread(() -> {
                        Toast.makeText(context,
                                entry.name + " 更新已取消",
                                Toast.LENGTH_SHORT).show();
                        notifyItemChanged(position);
                    });
                }
            }

            @Override
            public void onPaused(@NonNull String moduleId) {
                if (context instanceof android.app.Activity) {
                    ((android.app.Activity) context).runOnUiThread(() -> {
                        Toast.makeText(context,
                                entry.name + " 下载已暂停",
                                Toast.LENGTH_SHORT).show();
                    });
                }
            }

            @Override
            public void onResumed(@NonNull String moduleId) {
                if (context instanceof android.app.Activity) {
                    ((android.app.Activity) context).runOnUiThread(() -> {
                        Toast.makeText(context,
                                entry.name + " 下载已恢复",
                                Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }

    /**
     * 更新游戏列表数据。
     *
     * @param newEntries 新的游戏条目列表
     */
    public void updateData(@NonNull List<GameRegistry.Entry> newEntries) {
        if (newEntries == null) return;

        gameEntries.clear();
        gameEntries.addAll(newEntries);

        availableUpdates.clear();
        downloadProgress.clear();
        downloading.clear();

        for (int i = 0; i < newEntries.size(); i++) {
            availableUpdates.add(null);
            downloadProgress.add(0);
            downloading.add(false);
        }

        notifyDataSetChanged();
    }

    /**
     * ViewHolder。
     */
    static final class GameViewHolder extends RecyclerView.ViewHolder {

        final ImageView gameIcon;
        final TextView gameName;
        final TextView gameDesc;
        final TextView versionText;
        final Button checkUpdateButton;
        final Button updateButton;
        final ProgressBar progressBar;
        final TextView progressText;

        GameViewHolder(@NonNull View itemView) {
            super(itemView);
            gameIcon = itemView.findViewById(R.id.iv_game_icon);
            gameName = itemView.findViewById(R.id.tv_game_name);
            gameDesc = itemView.findViewById(R.id.tv_game_desc);
            versionText = itemView.findViewById(R.id.tv_version);
            checkUpdateButton = itemView.findViewById(R.id.btn_check_update);
            updateButton = itemView.findViewById(R.id.btn_update);
            progressBar = itemView.findViewById(R.id.progress_download);
            progressText = itemView.findViewById(R.id.tv_progress);
        }
    }
}
