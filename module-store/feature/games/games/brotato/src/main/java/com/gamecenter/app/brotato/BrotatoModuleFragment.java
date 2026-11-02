package com.gamecenter.app.brotato;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.gamecenter.app.R;
import com.gamecenter.app.brotato.engine.BrotatoArena;
import com.gamecenter.app.brotato.engine.BrotatoContent;
import com.gamecenter.app.games.GameUsageStore;
import com.gamecenter.app.modules.ModuleManager;

/**
 * 土豆兄弟游戏 Fragment（五星级深度现代化改造版）。
 *
 * <p>特性增强：
 * 1. 顶部统一导航栏，新增「英雄工坊」胶囊选择入口，支持 6 位特色英雄一键切换与持久化；
 * 2. 状态看板 HUD 整合积分、翡翠宝石战利品统计、当前波次与历史纪录；
 * 3. 难度胶囊分级（简单/普通/困难）与无缝重开；
 * 4. 详细的现代战场规则指引弹窗（英雄特异、多枪瞄准、宝石磁吸、走位风筝）；
 * 5. 对局结算弹窗丰富呈现战功与掉落收获。</p>
 */
public class BrotatoModuleFragment extends Fragment {

    private static final String GAME_ID = "brotato";
    private static final String PREFS_NAME = "brotato_prefs";
    private static final String KEY_SELECTED_HERO = "selected_hero";
    private static final long FRAME_INTERVAL_MS = BrotatoArena.TICK_MS;

    private BrotatoView brotatoView;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int highScore = 0;
    private int totalGames = 0;
    private float difficultyFactor = 0.5f;

    private TextView tvScore;
    private TextView tvGems;
    private TextView tvWave;
    private TextView tvBest;
    private Button btnHero;
    private Button btnEasy;
    private Button btnNormal;
    private Button btnHard;
    private Button btnRestart;
    private GameUsageStore usageStore;

    private BrotatoHero selectedHero = BrotatoHero.ALL_ROUNDER;

    private final Runnable gameLoop = new Runnable() {
        @Override
        public void run() {
            if (brotatoView != null && brotatoView.isGameRunning()) {
                brotatoView.update();
                handler.postDelayed(this, FRAME_INTERVAL_MS);
            }
        }
    };

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Context ctx = requireContext();
        float dp = ctx.getResources().getDisplayMetrics().density;
        int colorBg = isNightMode() ? 0xFF0F172A : 0xFFF8FAFC;
        int colorTextPrimary = isNightMode() ? 0xFFF1F5F9 : 0xFF0F172A;
        int colorTextSecondary = isNightMode() ? 0xFF94A3B8 : 0xFF64748B;

        // 读取持久化选中的英雄
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String savedHeroId = prefs.getString(KEY_SELECTED_HERO, BrotatoHero.ALL_ROUNDER.getId());
        selectedHero = BrotatoHero.fromId(savedHeroId);

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(colorBg);
        root.setPadding((int) (12 * dp), (int) (8 * dp), (int) (12 * dp), (int) (10 * dp));

        // 1. 顶部统一导航栏
        LinearLayout topNavBar = new LinearLayout(ctx);
        topNavBar.setOrientation(LinearLayout.HORIZONTAL);
        topNavBar.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams topNavLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int) (44 * dp));
        topNavLp.bottomMargin = (int) (6 * dp);
        topNavBar.setLayoutParams(topNavLp);

        Button btnBack = new Button(ctx);
        btnBack.setText("❮ 返回");
        btnBack.setTextSize(13f);
        btnBack.setTextColor(colorTextPrimary);
        btnBack.setBackground(createCapsuleDrawable(isNightMode() ? 0xFF1E293B : 0xFFE2E8F0, (int) (14 * dp)));
        btnBack.setPadding((int) (12 * dp), 0, (int) (12 * dp), 0);
        btnBack.setMinHeight((int) (32 * dp));
        btnBack.setMinimumHeight((int) (32 * dp));
        btnBack.setOnClickListener(v -> handleBackPress());
        topNavBar.addView(btnBack);

        TextView tvTitle = new TextView(ctx);
        tvTitle.setText(getString(R.string.game_title_brotato));
        tvTitle.setTextSize(17f);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setTextColor(colorTextPrimary);
        tvTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tvTitle.setLayoutParams(titleLp);
        topNavBar.addView(tvTitle);

        // 英雄工坊切换胶囊按键
        btnHero = new Button(ctx);
        btnHero.setText(selectedHero.getEmoji() + " " + selectedHero.getNameCn() + " ▾");
        btnHero.setTextSize(12f);
        btnHero.setTypeface(null, android.graphics.Typeface.BOLD);
        btnHero.setTextColor(0xFF38BDF8);
        btnHero.setBackground(createCapsuleDrawable(isNightMode() ? 0xFF1E293B : 0xFFE0F2FE, (int) (14 * dp)));
        btnHero.setPadding((int) (10 * dp), 0, (int) (10 * dp), 0);
        btnHero.setMinHeight((int) (32 * dp));
        btnHero.setMinimumHeight((int) (32 * dp));
        LinearLayout.LayoutParams heroLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        heroLp.rightMargin = (int) (6 * dp);
        btnHero.setLayoutParams(heroLp);
        btnHero.setOnClickListener(v -> showHeroSelectionDialog());
        topNavBar.addView(btnHero);

        Button btnRules = new Button(ctx);
        btnRules.setText("ℹ 规则");
        btnRules.setTextSize(13f);
        btnRules.setTextColor(colorTextPrimary);
        btnRules.setBackground(createCapsuleDrawable(isNightMode() ? 0xFF1E293B : 0xFFE2E8F0, (int) (14 * dp)));
        btnRules.setPadding((int) (10 * dp), 0, (int) (10 * dp), 0);
        btnRules.setMinHeight((int) (32 * dp));
        btnRules.setMinimumHeight((int) (32 * dp));
        btnRules.setOnClickListener(v -> showRulesDialog());
        topNavBar.addView(btnRules);

        root.addView(topNavBar);

        // 2. 状态战况 HUD 看板（积分、翡翠宝石、波次、纪录）
        LinearLayout statBar = new LinearLayout(ctx);
        statBar.setOrientation(LinearLayout.HORIZONTAL);
        statBar.setGravity(Gravity.CENTER);
        statBar.setBackground(createCapsuleDrawable(isNightMode() ? 0xFF1E293B : 0xFFEDF2F7, (int) (12 * dp)));
        statBar.setPadding((int) (12 * dp), (int) (6 * dp), (int) (12 * dp), (int) (6 * dp));
        LinearLayout.LayoutParams statBarLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        statBarLp.bottomMargin = (int) (6 * dp);
        statBar.setLayoutParams(statBarLp);

        tvScore = new TextView(ctx);
        tvScore.setTextSize(13.5f);
        tvScore.setTypeface(null, android.graphics.Typeface.BOLD);
        tvScore.setTextColor(0xFF2563EB);
        tvScore.setText(getString(R.string.game_score_init));
        LinearLayout.LayoutParams scoreLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.1f);
        tvScore.setLayoutParams(scoreLp);
        statBar.addView(tvScore);

        tvGems = new TextView(ctx);
        tvGems.setTextSize(13.5f);
        tvGems.setTypeface(null, android.graphics.Typeface.BOLD);
        tvGems.setTextColor(0xFF10B981);
        tvGems.setText("💎 0");
        LinearLayout.LayoutParams gemsLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.9f);
        tvGems.setLayoutParams(gemsLp);
        statBar.addView(tvGems);

        tvWave = new TextView(ctx);
        tvWave.setTextSize(13.5f);
        tvWave.setTypeface(null, android.graphics.Typeface.BOLD);
        tvWave.setTextColor(0xFF059669);
        tvWave.setGravity(Gravity.CENTER);
        tvWave.setText(getString(R.string.game_wave_init));
        LinearLayout.LayoutParams waveLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.1f);
        tvWave.setLayoutParams(waveLp);
        statBar.addView(tvWave);

        tvBest = new TextView(ctx);
        tvBest.setTextSize(13.5f);
        tvBest.setTextColor(colorTextSecondary);
        tvBest.setGravity(Gravity.END);
        tvBest.setText(getString(R.string.game_high_score_init));
        LinearLayout.LayoutParams bestLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.1f);
        tvBest.setLayoutParams(bestLp);
        statBar.addView(tvBest);

        root.addView(statBar);

        // 3. 难度胶囊选择区
        LinearLayout diffBar = new LinearLayout(ctx);
        diffBar.setOrientation(LinearLayout.HORIZONTAL);
        diffBar.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams diffBarLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        diffBarLp.bottomMargin = (int) (6 * dp);
        diffBar.setLayoutParams(diffBarLp);

        btnEasy = createDiffButton(ctx, getString(R.string.game_diff_easy), dp);
        btnEasy.setOnClickListener(v -> setDifficulty(0.3f));
        diffBar.addView(btnEasy);

        btnNormal = createDiffButton(ctx, getString(R.string.game_diff_normal), dp);
        btnNormal.setOnClickListener(v -> setDifficulty(0.5f));
        diffBar.addView(btnNormal);

        btnHard = createDiffButton(ctx, getString(R.string.game_diff_hard), dp);
        btnHard.setOnClickListener(v -> setDifficulty(0.8f));
        diffBar.addView(btnHard);

        root.addView(diffBar);

        // 4. 游戏容器
        FrameLayout gameContainer = new FrameLayout(ctx);
        LinearLayout.LayoutParams containerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        gameContainer.setLayoutParams(containerLp);
        root.addView(gameContainer);

        com.gamecenter.app.modular.ModuleResourceLoader.ModuleResources moduleResources = null;
        try {
            moduleResources = ModuleManager.INSTANCE.getModuleResources("brotato");
        } catch (RuntimeException exception) {
            android.util.Log.e("BrotatoFragment", "module resources unavailable", exception);
        }
        if (moduleResources == null) {
            Toast.makeText(ctx, "土豆兄弟模块资源装载失败，请重新安装该模块", Toast.LENGTH_LONG).show();
            return new FrameLayout(requireContext());
        }
        BrotatoContent content;
        try {
            content = BrotatoAssets.load(moduleResources.getAssetManager());
        } catch (IllegalStateException exception) {
            android.util.Log.e("BrotatoFragment", "brotato content assets invalid", exception);
            Toast.makeText(ctx, "土豆兄弟内容文件校验失败：" + exception.getMessage(), Toast.LENGTH_LONG).show();
            return new FrameLayout(requireContext());
        }
        final int totalWavesForHud = content.totalWaves();
        brotatoView = new BrotatoView(ctx, content);
        brotatoView.setHero(selectedHero);
        applyDifficulty();
        brotatoView.setOnGameListener(new BrotatoView.OnGameListener() {
            @Override
            public void onScoreChanged(int score) {
                if (tvScore != null) {
                    tvScore.setText(getString(R.string.game_score_format, score));
                }
            }

            @Override
            public void onGameOver(int score, int wave) {
                finishGame(score, false);
            }

            @Override
            public void onWin(int score) {
                finishGame(score, true);
            }

            @Override
            public void onWaveComplete(int wave) {
                if (tvWave != null) {
                    tvWave.setText(getString(R.string.game_wave_format,
                            Math.min(wave + 1, totalWavesForHud)));
                }
            }

            @Override
            public void onMaterialsChanged(int materials) {
                if (tvGems != null) {
                    tvGems.setText("💎 " + materials);
                }
            }
        });
        gameContainer.addView(brotatoView);

        // 5. 底部控制栏
        btnRestart = new Button(ctx);
        btnRestart.setText(getString(R.string.game_btn_restart));
        btnRestart.setTextSize(15f);
        btnRestart.setTypeface(null, android.graphics.Typeface.BOLD);
        btnRestart.setTextColor(Color.WHITE);
        btnRestart.setBackground(createCapsuleDrawable(0xFF2563EB, (int) (22 * dp)));
        LinearLayout.LayoutParams restartLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int) (44 * dp));
        restartLp.topMargin = (int) (6 * dp);
        btnRestart.setLayoutParams(restartLp);
        btnRestart.setOnClickListener(v -> startGame());
        root.addView(btnRestart);

        updateDifficultyButtons();
        return root;
    }

    private Button createDiffButton(Context ctx, String text, float dp) {
        Button btn = new Button(ctx);
        btn.setText(text);
        btn.setTextSize(12f);
        btn.setMinHeight((int) (34 * dp));
        btn.setMinimumHeight((int) (34 * dp));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins((int) (3 * dp), 0, (int) (3 * dp), 0);
        btn.setLayoutParams(lp);
        return btn;
    }

    private GradientDrawable createCapsuleDrawable(int color, int radius) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color);
        gd.setCornerRadius(radius);
        return gd;
    }

    private void handleBackPress() {
        if (brotatoView != null && brotatoView.isGameRunning()) {
            new AlertDialog.Builder(requireContext())
                    .setTitle("退出确认")
                    .setMessage("当前战斗正处于激烈波次中，确定要退出战场吗？")
                    .setPositiveButton("确定退出", (dialog, which) -> exitModule())
                    .setNegativeButton("继续战斗", null)
                    .show();
        } else {
            exitModule();
        }
    }

    private void exitModule() {
        if (getActivity() != null) {
            if (getParentFragmentManager().getBackStackEntryCount() > 0) {
                getParentFragmentManager().popBackStack();
            } else {
                getActivity().finish();
            }
        }
    }

    /**
     * 弹出英雄工坊选择弹窗（6 位特色英雄卡牌式选取）
     */
    private void showHeroSelectionDialog() {
        Context ctx = requireContext();
        float dp = ctx.getResources().getDisplayMetrics().density;

        ScrollView scrollView = new ScrollView(ctx);
        LinearLayout listLayout = new LinearLayout(ctx);
        listLayout.setOrientation(LinearLayout.VERTICAL);
        listLayout.setPadding((int) (14 * dp), (int) (10 * dp), (int) (14 * dp), (int) (10 * dp));
        scrollView.addView(listLayout);

        AlertDialog dialog = new AlertDialog.Builder(ctx)
                .setTitle("⚔️ 特工工坊 · 选择出战特工")
                .setView(scrollView)
                .setNegativeButton("取消", null)
                .create();

        for (BrotatoHero hero : BrotatoHero.values()) {
            boolean isCurrent = (hero == selectedHero);

            LinearLayout card = new LinearLayout(ctx);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding((int) (12 * dp), (int) (10 * dp), (int) (12 * dp), (int) (10 * dp));
            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            cardLp.bottomMargin = (int) (10 * dp);
            card.setLayoutParams(cardLp);

            // 卡片边框与背景
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(isNightMode() ? 0xFF1E293B : 0xFFF1F5F9);
            bg.setCornerRadius(12 * dp);
            if (isCurrent) {
                bg.setStroke((int) (2.5f * dp), 0xFFF59E0B); // 金色选中框
            } else {
                bg.setStroke((int) (1 * dp), isNightMode() ? 0xFF334155 : 0xFFCBD5E1);
            }
            card.setBackground(bg);

            // 头部：图标 + 名称 + 称号
            LinearLayout headRow = new LinearLayout(ctx);
            headRow.setOrientation(LinearLayout.HORIZONTAL);
            headRow.setGravity(Gravity.CENTER_VERTICAL);

            TextView tvEmoji = new TextView(ctx);
            tvEmoji.setText(hero.getEmoji());
            tvEmoji.setTextSize(22f);
            headRow.addView(tvEmoji);

            TextView tvName = new TextView(ctx);
            tvName.setText("  " + hero.getNameCn() + " · " + hero.getTitle());
            tvName.setTextSize(15f);
            tvName.setTypeface(null, android.graphics.Typeface.BOLD);
            tvName.setTextColor(isCurrent ? 0xFFF59E0B : (isNightMode() ? 0xFFF8FAFC : 0xFF0F172A));
            LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            tvName.setLayoutParams(nameLp);
            headRow.addView(tvName);

            if (isCurrent) {
                TextView tvBadge = new TextView(ctx);
                tvBadge.setText("当前出战");
                tvBadge.setTextSize(11f);
                tvBadge.setTextColor(Color.WHITE);
                tvBadge.setBackground(createCapsuleDrawable(0xFFF59E0B, (int) (8 * dp)));
                tvBadge.setPadding((int) (8 * dp), (int) (2 * dp), (int) (8 * dp), (int) (2 * dp));
                headRow.addView(tvBadge);
            }
            card.addView(headRow);

            // 属性标签行
            TextView tvStats = new TextView(ctx);
            tvStats.setText(formatHeroStats(hero));
            tvStats.setTextSize(12f);
            tvStats.setTextColor(0xFF38BDF8);
            tvStats.setPadding(0, (int) (4 * dp), 0, (int) (2 * dp));
            card.addView(tvStats);

            // 描述
            TextView tvDesc = new TextView(ctx);
            tvDesc.setText(hero.getDescription());
            tvDesc.setTextSize(12.5f);
            tvDesc.setTextColor(isNightMode() ? 0xFF94A3B8 : 0xFF475569);
            card.addView(tvDesc);

            // 点击卡片换装出战
            card.setOnClickListener(v -> {
                selectedHero = hero;
                SharedPreferences.Editor editor = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
                editor.putString(KEY_SELECTED_HERO, hero.getId());
                editor.apply();

                if (btnHero != null) {
                    btnHero.setText(hero.getEmoji() + " " + hero.getNameCn() + " ▾");
                }
                if (brotatoView != null) {
                    brotatoView.setHero(hero);
                }
                Toast.makeText(ctx, "已选择「" + hero.getNameCn() + "」出战！重新开局即刻生效", Toast.LENGTH_SHORT).show();
                dialog.dismiss();

                // 若当前不在终局，询问是否立即重开
                if (brotatoView != null && brotatoView.isGameRunning()) {
                    startGame();
                }
            });

            listLayout.addView(card);
        }

        dialog.show();
    }

    private String formatHeroStats(BrotatoHero hero) {
        StringBuilder sb = new StringBuilder();
        int hp = BrotatoArena.BASE_MAX_HP + hero.getBonusMaxHp();
        sb.append("生命: ").append(hp);
        if (hero.getExtraShots() > 0) {
            sb.append(" · 双枪齐射(+").append(hero.getExtraShots()).append(")");
        }
        if (hero.getFireIntervalFactor() != 1.0f) {
            int fireBoost = Math.round((1.0f - hero.getFireIntervalFactor()) * 100);
            sb.append(" · 射速 ").append(fireBoost >= 0 ? "+" : "").append(fireBoost).append("%");
        }
        if (hero.getDamageFactor() != 1.0f) {
            int dmgBoost = Math.round((hero.getDamageFactor() - 1.0f) * 100);
            sb.append(" · 伤害 ").append(dmgBoost >= 0 ? "+" : "").append(dmgBoost).append("%");
        }
        if (hero.getSpeedFactor() != 1.0f) {
            int spdBoost = Math.round((hero.getSpeedFactor() - 1.0f) * 100);
            sb.append(" · 移速 ").append(spdBoost >= 0 ? "+" : "").append(spdBoost).append("%");
        }
        if (hero.getCritChance() > 0.15f) {
            sb.append(" · 高暴击 ").append((int) (hero.getCritChance() * 100)).append("%");
        }
        if (hero.getMagnetRangeFactor() > 1.0f) {
            sb.append(" · 磁吸 ").append((int) (hero.getMagnetRangeFactor() * 100)).append("%");
        }
        return sb.toString();
    }

    private void showRulesDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("🎖️ 特工手册 · 《蛋壳特工队》战术指南与超武配方")
                .setMessage("【游戏背景与玩法】\n"
                        + "操控全副武装的王牌特工，在废土都市中迎击源源不断的生化感染狂潮！搜集高能经验晶石，升级并突破合成终极超级武器（EVO）！\n\n"
                        + "【六大特战特工】\n"
                        + "🧣 科萌（战术特工）：看板主角！标志性飘逸红领巾，全能战术均衡大师；\n"
                        + "👑 金（暴击女王）：双枪开局，致命感知火力全开，攻速极快与高额暴击伤害；\n"
                        + "🌙 月咏（月影剑客）：神秘和风女特工，月影斩剑气单发威力惊人，魔刃贯穿群怪；\n"
                        + "🛡️ 沃尔默（重装教官）：退役重装特工教官，泰坦体魄极厚防弹血甲，近战减伤；\n"
                        + "🐱 凯蒂斯（机灵猫娘）：战术医疗猫娘特工，四叶草天眷，超大范围晶石磁吸与战地补给；\n"
                        + "⚡ 斯巴达（极速游侠）：特工穿插游侠，身手敏捷拥有超高机动性，风筝走位大师。\n\n"
                        + "【🔥 超级武器（EVO）经典合成法则】\n"
                        + "核心法则：主动武器升至 5 星满级 + 拥有对应 1 星被动补给 ➔ 升级或开宝箱时 100% 触发超武进化！\n"
                        + "1. 苦无(5星) + 甲贺忍法帖 ➔ ⭐ 幽灵手里剑 (零冷却无限极速追踪连射)\n"
                        + "2. 棒球棍(5星) + 健身指南 ➔ ⭐ 暴力美学 (360° 全方位粉碎冲击波)\n"
                        + "3. 守卫者(5星) + 外骨骼装甲 ➔ ⭐ 捍卫者 (永久高速旋转+格挡子弹)\n"
                        + "4. A型无人机(5星) + B型无人机(5星) ➔ ⭐ 毁灭者 (双机合体全屏大轰炸)\n"
                        + "5. 力场发生器(5星) + 功能饮料 ➔ ⭐ 压制领域 (范围减速翻倍高频融怪)\n"
                        + "6. 弹力足球(5星) + 跑鞋 ➔ ⭐ 量子球 (全场分裂疯狂几何折射)\n"
                        + "7. 火箭发射器(5星) + 高爆燃料 ➔ ⭐ 鲨嘴炮 (巨型鲨鱼核弹全屏爆破)\n"
                        + "8. 钻头弹(5星) + 弹药推进器 ➔ ⭐ 哨箭 (超音速巡航飞箭全图收割)\n"
                        + "9. 雷电发射器(5星) + 能量块 ➔ ⭐ 狂雷电池 (无缝连续链状落雷风暴)\n\n"
                        + "【战地补给生态】\n"
                        + "• 四阶经验晶石：绿(1EXP)、蓝(5EXP)、金(20EXP)、红(100EXP)；\n"
                        + "• 战术磁铁(🧲)：瞬间吸引全图所有晶石流星雨聚拢；\n"
                        + "• 战术核弹(💣)：全屏白光爆闪，瞬间清空普通小怪；\n"
                        + "• 香脆烤鸡腿(🍗)：立即回复特工 50% 生命值；\n"
                        + "• 三叶草幸运宝箱(🍀)：Boss/精英怪掉落，触发 1/3/5 连抽老虎机，必定保底合成超武！")
                .setPositiveButton("整装出击！", null)
                .show();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Context ctx = requireContext();
        usageStore = new GameUsageStore(ctx);
        highScore = usageStore.getHighScore(GAME_ID);
        if (tvBest != null) {
            tvBest.setText(getString(R.string.game_high_score_format, highScore));
        }
        updateDifficultyButtons();
        startGame();
    }

    private void startGame() {
        if (brotatoView == null) return;
        brotatoView.setHero(selectedHero);
        brotatoView.startGame();
        if (tvScore != null) tvScore.setText(getString(R.string.game_score_init));
        if (tvGems != null) tvGems.setText("💎 0");
        if (tvWave != null) tvWave.setText(getString(R.string.game_wave_init));
        handler.removeCallbacks(gameLoop);
        handler.post(gameLoop);
    }

    private void setDifficulty(float factor) {
        difficultyFactor = factor;
        applyDifficulty();
        updateDifficultyButtons();
        if (isAdded()) {
            Toast.makeText(requireContext(),
                    R.string.game_brotato_diff_next_round,
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void applyDifficulty() {
        if (brotatoView == null) return;
        brotatoView.setDifficultyScalar(difficultyScalar(difficultyFactor));
    }

    private static float difficultyScalar(float factor) {
        if (factor <= 0.3f) return BrotatoArena.DIFFICULTY_EASY;
        if (factor >= 0.8f) return BrotatoArena.DIFFICULTY_HARD;
        return BrotatoArena.DIFFICULTY_NORMAL;
    }

    private void finishGame(int score, boolean won) {
        handler.removeCallbacks(gameLoop);
        totalGames++;
        boolean isNewRecord = score > highScore;
        if (isNewRecord) {
            highScore = score;
        }
        if (usageStore != null) {
            usageStore.recordScore(GAME_ID, highScore);
            if (won) {
                usageStore.recordWin(GAME_ID);
            } else {
                usageStore.recordLoss(GAME_ID);
            }
        }
        if (tvBest != null) {
            tvBest.setText(getString(R.string.game_high_score_format, highScore));
        }

        int gems = (brotatoView != null) ? brotatoView.getCollectedMaterials() : 0;
        int level = (brotatoView != null) ? brotatoView.getCurrentLevel() : 1;
        if (isAdded() && getActivity() != null && !getActivity().isFinishing()) {
            new AlertDialog.Builder(requireContext())
                    .setTitle(won ? "🏆 旷世凯旋！肃清全波次" : "💥 特工战陨")
                    .setMessage((won ? "恭喜特工成功荡平二十波生化异变狂潮！" : "你倒在了冲锋的路上！")
                            + "\n\n出战特工：" + selectedHero.getEmoji() + " " + selectedHero.getNameCn()
                            + "\n特工战功：" + score
                            + "\n特工等级：LV." + level
                            + "\n高能晶石：💎 " + gems + " 颗"
                            + (isNewRecord ? "\n\n🎉 创造了特工全新历史记录！" : "\n历史最佳：" + highScore))
                    .setPositiveButton("重整旗鼓", (dialog, which) -> startGame())
                    .setNeutralButton("更换特工", (dialog, which) -> showHeroSelectionDialog())
                    .setNegativeButton("返回大厅", (dialog, which) -> exitModule())
                    .show();
        }
    }

    private void updateDifficultyButtons() {
        if (btnEasy == null || btnNormal == null || btnHard == null) return;
        float dp = getResources().getDisplayMetrics().density;
        styleDiffTab(btnEasy, difficultyFactor == 0.3f, dp);
        styleDiffTab(btnNormal, difficultyFactor == 0.5f, dp);
        styleDiffTab(btnHard, difficultyFactor == 0.8f, dp);
    }

    private void styleDiffTab(Button btn, boolean active, float dp) {
        if (active) {
            btn.setBackground(createCapsuleDrawable(0xFF2563EB, (int) (10 * dp)));
            btn.setTextColor(Color.WHITE);
            btn.setTypeface(null, android.graphics.Typeface.BOLD);
        } else {
            int inactiveBg = isNightMode() ? 0xFF1E293B : 0xFFE2E8F0;
            int inactiveText = isNightMode() ? 0xFF94A3B8 : 0xFF475569;
            btn.setBackground(createCapsuleDrawable(inactiveBg, (int) (10 * dp)));
            btn.setTextColor(inactiveText);
            btn.setTypeface(null, android.graphics.Typeface.NORMAL);
        }
    }

    private boolean isNightMode() {
        if (!isAdded()) return false;
        int nightMode = requireContext().getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == Configuration.UI_MODE_NIGHT_YES;
    }

    @Override
    public void onPause() {
        super.onPause();
        handler.removeCallbacks(gameLoop);
        if (brotatoView != null) {
            brotatoView.pauseGame();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (brotatoView != null && brotatoView.isGameRunning()) {
            brotatoView.resumeGame();
            handler.removeCallbacks(gameLoop);
            handler.post(gameLoop);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacksAndMessages(null);
        if (brotatoView != null) {
            brotatoView.stopGame();
        }
    }
}
