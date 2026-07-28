package com.gamecenter.app.doudizhu;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;

import com.gamecenter.app.doudizhu.model.Card;
import com.gamecenter.app.doudizhu.model.CardType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 斗地主桌面视图 (DouDiZhu Table View)
 *
 * <p>核心自定义视图，负责游戏桌面的所有绘制工作。
 * 继承自 {@link View}，使用 {@link Canvas} 进行手动绘制，不依赖 XML 布局中的子 View。</p>
 *
 * <p>你可以把这个类想象成一个"画师"——它负责在屏幕上画出游戏桌面的所有内容：
 * 手牌、底牌、AI的牌背、出牌区的牌、记牌器、按钮提示等。
 * 它不用XML布局，而是像画家一样一笔一笔画出来。</p>
 *
 * <p><b>主要功能：</b></p>
 * <ul>
 *   <li>DPI 自适应 - 所有尺寸基于 View 宽高动态计算，适配不同屏幕</li>
 *   <li>分区绘制 - 底牌区（左上角）、AI信息区（左右两侧）、出牌区（中心）、玩家手牌区（底部）</li>
 *   <li>精细化点击检测 - 支持层叠卡牌的点击区域判定，从右向左遍历优先命中顶层牌</li>
 *   <li>出牌动画 - 基于 {@link ValueAnimator} 的平滑动画效果</li>
 *   <li>记牌器 - 顶部居中显示各牌面剩余数量</li>
 *   <li>拖动选择 - 支持滑动手势批量选中多张手牌</li>
 * </ul>
 *
 * <p><b>关键设计决策：</b></p>
 * <ul>
 *   <li>卡牌尺寸根据手牌数量反推，确保所有手牌在屏幕内完整显示</li>
 *   <li>桌面出牌区使用固定尺寸（tableCardWidth），不随玩家手牌数量变化</li>
 *   <li>卡牌绘制优先使用位图资源，找不到时回退到 Canvas 简易绘制</li>
 *   <li>动画期间屏蔽触摸事件，防止动画未完成时操作导致状态错乱</li>
 * </ul>
 */
public class DouDiZhuTableView extends View {

    // ============ 常量定义 ============

    // 卡牌宽高比（标准扑克比例约为 2.5:3.5，即 1:1.4）
    private static final float CARD_WIDTH_TO_HEIGHT_RATIO = 1.0f / 1.4f;
    // 卡牌层叠时，每张卡水平偏移量相对于卡宽的比例（基础值）
    private static final float CARD_OVERLAP_RATIO = 0.25f;
    // 手牌抬起高度相对于卡高的比例
    private static final float CARD_LIFT_HEIGHT_RATIO = 0.15f;
    // 卡牌圆角半径相对于卡宽的比例
    private static final float CARD_CORNER_RADIUS_RATIO = 0.08f;

    // 玩家手牌区域占视图高度的比例（增大到 0.35 以适应新卡牌尺寸）
    private static final float PLAYER_HAND_HEIGHT_RATIO = 0.35f;
    // 玩家手牌区域距离底部的边距比例（增大到 0.05 防止导航栏裁剪）
    private static final float PLAYER_HAND_BOTTOM_MARGIN_RATIO = 0.05f;
    // 出牌区域占视图高度的比例
    private static final float PLAY_AREA_HEIGHT_RATIO = 0.25f;
    // 底牌区域占视图高度的比例
    private static final float BOTTOM_CARDS_HEIGHT_RATIO = 0.12f;
    // AI信息区域占视图宽度的比例（缩小到 1/18 减少视觉压迫）
    private static final float AI_INFO_WIDTH_RATIO = 0.055f;
    // 手牌区最大宽度占比（限制在 90% 屏幕宽度内）
    private static final float HAND_MAX_WIDTH_RATIO = 0.90f;
    // 卡牌最大宽度占屏幕宽度的比例（防止牌少时牌过大）
    private static final float CARD_MAX_WIDTH_RATIO = 0.125f;
    // 卡牌最小宽度（像素，防止牌太小无法操作）
    private static final float CARD_MIN_WIDTH_DP = 36f;
    // 默认重叠比例（17张牌时使用）
    private static final float DEFAULT_OVERLAP_17 = 0.22f;
    // 20张牌时的重叠比例（地主+3底牌）
    private static final float DEFAULT_OVERLAP_20 = 0.42f;

    // 动画时长（毫秒）
    private static final long PLAY_CARD_ANIMATION_DURATION = 400L;
    private static final long DEAL_CARD_ANIMATION_DURATION = 400L;

    // ============ 绘制相关 ============

    // 画笔 - 卡牌背景
    private Paint cardPaint;
    // 画笔 - 卡牌边框
    private Paint cardBorderPaint;
    // 画笔 - 卡牌文字
    private Paint cardTextPaint;
    // 画笔 - 花色文字（红桃/黑桃等）
    private Paint suitTextPaint;
    // 画笔 - 玩家手牌区域背景
    private Paint tableBackgroundPaint;
    // 画笔 - AI信息文字
    private Paint aiInfoPaint;
    // 画笔 - 出牌提示文字
    private Paint hintPaint;
    // 画笔 - 选中高亮
    private Paint selectedHighlightPaint;
    // 画笔 - 金色装饰
    private Paint goldPaint;
    private Paint goldStrokePaint;
    // 画笔 - 阴影
    private Paint shadowPaint;
    // 画笔 - 地主皇冠
    private Paint crownPaint;
    // 画笔 - 按钮
    private Paint buttonPaint;
    private Paint buttonTextPaint;
    private Paint buttonDisabledPaint;

    // 卡牌位图缓存（资源名称 -> Bitmap）
    private Map<String, Bitmap> cardBitmapCache;
    // 背面位图（用于AI手牌和未翻牌）
    private Bitmap cardBackBitmap;

    // ============ 尺寸计算 ============

    // 基于视图尺寸动态计算的卡牌宽度
    private float calculatedCardWidth;
    // 基于视图尺寸动态计算的卡牌高度
    private float calculatedCardHeight;
    // 手牌区域卡牌水平间距
    private float cardSpacing;
    // 桌面其他牌固定尺寸，不随玩家手牌数量变化
    private float tableCardWidth;
    private float tableCardHeight;
    private float tableCardSpacing;
    // 手牌抬起偏移量
    private float cardLiftHeight;
    // 卡牌圆角半径
    private float cardCornerRadius;

    // ============ 状态数据 ============

    // 玩家手牌列表（已排序）
    private List<Card> playerHandCards;
    // 选中的手牌列表
    private List<Card> selectedCards;
    // 玩家已选手牌的索引（用于快速查找）
    private List<Integer> selectedIndices;
    // 底牌列表（3张）
    private List<Card> bottomCards;
    // 玩家上一轮出牌
    private List<Card> playerPlayedCards;
    // 左方AI（电脑1）手牌数量
    private int leftAICardCount;
    // 右方AI（电脑2）手牌数量
    private int rightAICardCount;
    // 左方AI上一轮出牌
    private List<Card> leftAIPlayedCards;
    // 右方AI上一轮出牌
    private List<Card> rightAIPlayedCards;
    // AI是否选择了"不出"
    private boolean leftAIPassed = false;
    private boolean rightAIPassed = false;
    private int[] cardCounterCounts;

    // 当前轮到哪个玩家（0=玩家, 1=左AI, 2=右AI, 3=等待）
    private int currentTurn;
    // 地主身份标记（0=农民, 1=地主）
    private int playerLandlordStatus; // 0: 未确定, 1: 农民, 2: 地主
    // 三个玩家的地主状态
    private int[] landlordStatus = {0, 0, 0}; // 0=未确定, 1=农民, 2=地主
    private String[] playerLabels;

    // 动画相关
    private ValueAnimator playCardAnimator;
    private List<Card> animatingCards;
    private float animationProgress = 1.0f;
    private boolean isAnimating = false;
    // 发牌动画相关
    private ValueAnimator dealCardAnimator;
    private int dealAnimatingCardIndex = 0;
    private int totalDealCards = 0;
    private float dealAnimationProgress = 0f;
    private boolean isDealAnimating = false;

    // 触摸相关
    private float touchStartX;
    private float touchStartY;
    private boolean isDragging = false;
    private int lastTouchedCardIndex = -1;

    // 回调接口
    private OnCardTouchListener cardTouchListener;
    private OnTableClickListener tableClickListener;
    private OnAnimationCompleteListener pendingDealListener;

    // ============ 构造方法 ============

    /**
     * 代码创建视图时调用。
     *
     * @param context 上下文
     */
    public DouDiZhuTableView(Context context) {
        super(context);
        init();
    }

    /**
     * XML 布局中引用时调用。
     *
     * @param context 上下文
     * @param attrs   XML 属性集
     */
    public DouDiZhuTableView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    /**
     * 带默认样式属性的构造方法。
     *
     * @param context      上下文
     * @param attrs        XML 属性集
     * @param defStyleAttr 默认样式属性
     */
    public DouDiZhuTableView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    /**
     * 初始化画笔、位图缓存和数据列表。
     *
     * <p>创建所有绘制所需的 {@link Paint} 对象，初始化卡牌位图缓存，
     * 以及各数据列表的空实例。</p>
     */
    private void init() {
        // 初始化卡牌位图缓存
        cardBitmapCache = new HashMap<>();

        // 创建画笔 - 卡牌背景（白色）
        cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cardPaint.setColor(Color.WHITE);
        cardPaint.setStyle(Paint.Style.FILL);

        // 创建画笔 - 卡牌边框
        cardBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cardBorderPaint.setColor(Color.parseColor("#BDBDBD"));
        cardBorderPaint.setStyle(Paint.Style.STROKE);
        cardBorderPaint.setStrokeWidth(2f);
        cardBorderPaint.setShadowLayer(1f, 1f, 1f, Color.parseColor("#40000000"));

        // 创建画笔 - 卡牌数字和花色
        cardTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cardTextPaint.setColor(Color.BLACK);
        cardTextPaint.setTextAlign(Paint.Align.CENTER);
        cardTextPaint.setFakeBoldText(true);

        // 创建画笔 - 花色文字
        suitTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        suitTextPaint.setTextAlign(Paint.Align.CENTER);

        // 创建画笔 - 桌面背景
        tableBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tableBackgroundPaint.setStyle(Paint.Style.FILL);

        // 创建画笔 - AI信息
        aiInfoPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        aiInfoPaint.setColor(Color.WHITE);
        aiInfoPaint.setTextAlign(Paint.Align.CENTER);
        aiInfoPaint.setTextSize(40f);

        // 创建画笔 - 提示文字
        hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        hintPaint.setColor(Color.parseColor("#4FC3F7")); // 浅蓝色提示
        hintPaint.setTextAlign(Paint.Align.CENTER);
        hintPaint.setTextSize(36f);

        // 创建画笔 - 选中高亮（金色发光外框）
        selectedHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        selectedHighlightPaint.setColor(Color.parseColor("#FFD700"));
        selectedHighlightPaint.setStyle(Paint.Style.STROKE);
        selectedHighlightPaint.setStrokeWidth(6f);
        selectedHighlightPaint.setShadowLayer(12f, 0f, 0f, Color.parseColor("#80FFD700"));

        // 金色装饰画笔
        goldPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        goldPaint.setColor(Color.parseColor("#FFD700"));
        goldPaint.setStyle(Paint.Style.FILL);

        goldStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        goldStrokePaint.setColor(Color.parseColor("#FFD700"));
        goldStrokePaint.setStyle(Paint.Style.STROKE);
        goldStrokePaint.setStrokeWidth(2f);

        // 阴影画笔
        shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPaint.setColor(Color.parseColor("#40000000"));
        shadowPaint.setStyle(Paint.Style.FILL);

        // 皇冠画笔
        crownPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        crownPaint.setColor(Color.parseColor("#FFD700"));
        crownPaint.setStyle(Paint.Style.FILL);

        // 按钮画笔
        buttonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        buttonPaint.setStyle(Paint.Style.FILL);

        buttonTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        buttonTextPaint.setColor(Color.WHITE);
        buttonTextPaint.setTextAlign(Paint.Align.CENTER);
        buttonTextPaint.setFakeBoldText(true);

        buttonDisabledPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        buttonDisabledPaint.setStyle(Paint.Style.FILL);

        // 初始化数据
        playerLabels = new String[]{
                "P1（待定）",
                "人机（待定）",
                "人机（待定）"
        };
        playerHandCards = new ArrayList<>();
        selectedCards = new ArrayList<>();
        selectedIndices = new ArrayList<>();
        bottomCards = new ArrayList<>();
        playerPlayedCards = new ArrayList<>();
        leftAIPlayedCards = new ArrayList<>();
        rightAIPlayedCards = new ArrayList<>();
        cardCounterCounts = createFullDeckCounter();
        leftAICardCount = 17;
        rightAICardCount = 17;
        currentTurn = 0;
        playerLandlordStatus = 0;
    }

    // ============ 尺寸计算 ============

    /**
     * 视图尺寸变化时回调。
     *
     * <p>在视图首次布局或尺寸改变时触发，重新计算所有卡牌尺寸并加载卡牌图片。</p>
     *
     * @param w     新宽度
     * @param h     新高度
     * @param oldw  旧宽度
     * @param oldh  旧高度
     */
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        calculateDimensions();
        loadCardImages();
    }

    /**
     * 根据视图尺寸和手牌数量，反推卡牌的最大可显示尺寸。
     *
     * <p>核心公式：cardWidth * (1 + (N-1)*(1-overlap)) ≤ viewWidth * 90%，
     * 从中反推出刚好能放下所有手牌的最大卡牌宽度。
     * 同时计算桌面出牌区卡牌尺寸（固定比例，不随手牌数量变化）。</p>
     *
     * <p>所有尺寸均基于 viewWidth/viewHeight 动态计算，确保 DPI 自适应。</p>
     */
    private void calculateDimensions() {
        int viewWidth = getWidth();
        int viewHeight = getHeight();

        if (viewWidth == 0 || viewHeight == 0) {
            return;
        }

        int actualCardCount = (playerHandCards != null && !playerHandCards.isEmpty())
                ? playerHandCards.size()
                : 20;
        int cardCount = Math.max(actualCardCount, 15);

        float overlap;
        if (cardCount <= 17) {
            overlap = DEFAULT_OVERLAP_17;
        } else {
            overlap = DEFAULT_OVERLAP_20 - (cardCount - 17) * 0.02f;
        }

        float denominator = 1.0f + (cardCount - 1) * (1.0f - overlap);
        float safeWidth = viewWidth * HAND_MAX_WIDTH_RATIO;
        calculatedCardWidth = safeWidth / denominator;

        float maxCardWidth = viewWidth * CARD_MAX_WIDTH_RATIO;
        if (calculatedCardWidth > maxCardWidth) {
            calculatedCardWidth = maxCardWidth;
        }

        float density = getResources().getDisplayMetrics().density;
        float minCardWidth = CARD_MIN_WIDTH_DP * density;
        if (calculatedCardWidth < minCardWidth) {
            calculatedCardWidth = minCardWidth;
        }

        calculatedCardHeight = calculatedCardWidth / CARD_WIDTH_TO_HEIGHT_RATIO;

        cardSpacing = calculatedCardWidth * (1.0f - overlap);

        float tableDenominator = 1.0f + (17 - 1) * (1.0f - DEFAULT_OVERLAP_17);
        tableCardWidth = (viewWidth * HAND_MAX_WIDTH_RATIO) / tableDenominator;
        tableCardWidth = Math.min(tableCardWidth, viewWidth * CARD_MAX_WIDTH_RATIO);
        if (tableCardWidth < minCardWidth) {
            tableCardWidth = minCardWidth;
        }
        tableCardHeight = tableCardWidth / CARD_WIDTH_TO_HEIGHT_RATIO;
        tableCardSpacing = tableCardWidth * 0.70f;

        cardLiftHeight = calculatedCardHeight * CARD_LIFT_HEIGHT_RATIO;
        cardCornerRadius = calculatedCardWidth * CARD_CORNER_RADIUS_RATIO;

        cardTextPaint.setTextSize(calculatedCardWidth * 0.22f);
        suitTextPaint.setTextSize(calculatedCardWidth * 0.18f);
        aiInfoPaint.setTextSize(tableCardWidth * 0.14f);
        hintPaint.setTextSize(calculatedCardHeight * 0.12f);
    }

    /**
     * 加载卡牌图片资源
     * 从 res/drawable 或 assets 中加载已准备好的卡牌图片
     */
    private void loadCardImages() {
        // 由于我们还不知道实际的图片资源名称，
        // 这里先加载背面图片作为占位
        // 实际项目中应该从 drawable 资源加载
        loadCardBackImage();

        // 提示：实际项目中应该预加载所有54张卡牌图片
        // for (Card card : Card.createFullDeck()) {
        //     loadCardBitmap(card.getResName());
        // }
    }

    /**
     * 加载卡牌背面图片
     */
    private void loadCardBackImage() {
        // 尝试从资源加载背面图片
        // int backResId = getResources().getIdentifier("poker_back", "drawable", getContext().getPackageName());
        // if (backResId != 0) {
        //     cardBackBitmap = BitmapFactory.decodeResource(getResources(), backResId);
        // }

        // 如果没有找到图片，创建一个简单的位图作为占位
        if (cardBackBitmap == null) {
            int backWidth = (int) calculatedCardWidth;
            int backHeight = (int) calculatedCardHeight;
            cardBackBitmap = Bitmap.createBitmap(backWidth, backHeight, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(cardBackBitmap);
            float radius = cardCornerRadius;

            Paint backPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            backPaint.setColor(Color.parseColor("#0D47A1"));
            backPaint.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(new RectF(0, 0, backWidth, backHeight),
                    radius, radius, backPaint);

            float inset = Math.max(backWidth * 0.06f, backHeight * 0.04f);
            RectF innerRect = new RectF(inset, inset, backWidth - inset, backHeight - inset);
            Paint innerBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            innerBorderPaint.setColor(Color.parseColor("#FFD54F"));
            innerBorderPaint.setStyle(Paint.Style.STROKE);
            innerBorderPaint.setStrokeWidth(Math.max(1.5f, backWidth * 0.02f));
            canvas.drawRoundRect(innerRect, radius * 0.6f, radius * 0.6f, innerBorderPaint);

            Paint diamondPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            diamondPaint.setColor(Color.parseColor("#33FFD54F"));
            diamondPaint.setStyle(Paint.Style.FILL);
            Paint diamondStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            diamondStrokePaint.setColor(Color.parseColor("#80FFD54F"));
            diamondStrokePaint.setStyle(Paint.Style.STROKE);
            diamondStrokePaint.setStrokeWidth(Math.max(0.8f, backWidth * 0.01f));

            float gridW = backWidth * 0.18f;
            float gridH = backHeight * 0.14f;
            float innerLeft = inset + backWidth * 0.03f;
            float innerTop = inset + backHeight * 0.03f;
            float innerRight = backWidth - inset - backWidth * 0.03f;
            float innerBottom = backHeight - inset - backHeight * 0.03f;

            for (float dy = innerTop; dy < innerBottom; dy += gridH) {
                for (float dx = innerLeft; dx < innerRight; dx += gridW) {
                    float cx = dx + gridW / 2f;
                    float cy = dy + gridH / 2f;
                    float dw = gridW * 0.38f;
                    float dh = gridH * 0.38f;
                    canvas.drawLine(cx, cy - dh, cx + dw, cy, diamondStrokePaint);
                    canvas.drawLine(cx + dw, cy, cx, cy + dh, diamondStrokePaint);
                    canvas.drawLine(cx, cy + dh, cx - dw, cy, diamondStrokePaint);
                    canvas.drawLine(cx - dw, cy, cx, cy - dh, diamondStrokePaint);
                }
            }

            float centerCx = backWidth / 2f;
            float centerCy = backHeight / 2f;
            float centerSize = Math.min(backWidth, backHeight) * 0.18f;
            Paint centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            centerPaint.setColor(Color.parseColor("#55FFD54F"));
            centerPaint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(centerCx, centerCy, centerSize, centerPaint);
            Paint centerStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            centerStrokePaint.setColor(Color.parseColor("#CCFFD54F"));
            centerStrokePaint.setStyle(Paint.Style.STROKE);
            centerStrokePaint.setStrokeWidth(Math.max(1f, backWidth * 0.015f));
            canvas.drawCircle(centerCx, centerCy, centerSize, centerStrokePaint);
            canvas.drawCircle(centerCx, centerCy, centerSize * 0.55f, centerStrokePaint);

            Paint symbolPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            symbolPaint.setColor(Color.parseColor("#EEFFD54F"));
            symbolPaint.setTextAlign(Paint.Align.CENTER);
            symbolPaint.setTextSize(centerSize * 1.1f);
            symbolPaint.setFakeBoldText(true);
            canvas.drawText("♦", centerCx, centerCy + centerSize * 0.38f, symbolPaint);
        }
    }

    /**
     * 加载指定卡牌的位图，带缓存机制。
     *
     * <p>先从内存缓存 {@link #cardBitmapCache} 中查找，命中则直接返回。
     * 未命中时从 drawable 资源加载，缩放到当前卡牌尺寸后缓存。
     * 如果资源不存在，返回 null（调用方会回退到 Canvas 简易绘制）。</p>
     *
     * @param resName 卡牌资源名称（如 "card_heart_ace"）
     * @return 卡牌位图，加载失败返回 null
     */
    private Bitmap loadCardBitmap(String resName) {
        if (cardBitmapCache.containsKey(resName)) {
            return cardBitmapCache.get(resName);
        }

        // 尝试从 drawable 资源加载
        int resId = getResources().getIdentifier(resName, "drawable", getContext().getPackageName());
        if (resId != 0) {
            Bitmap bitmap = BitmapFactory.decodeResource(getResources(), resId);
            if (bitmap != null) {
                // 缩放到位图到计算出的卡牌尺寸
                Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap,
                        (int) calculatedCardWidth, (int) calculatedCardHeight, true);
                // P0 内存泄漏修复：回收原始 Bitmap（createScaledBitmap 已创建新实例时）。
                if (scaledBitmap != bitmap && !bitmap.isRecycled()) {
                    bitmap.recycle();
                }
                cardBitmapCache.put(resName, scaledBitmap);
                return scaledBitmap;
            }
        }

        return null;
    }

    // ============ 绘制方法 ============

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int viewWidth = getWidth();
        int viewHeight = getHeight();

        if (viewWidth == 0 || viewHeight == 0) {
            return;
        }

        // 1. 绘制桌面背景
        drawTableBackground(canvas, viewWidth, viewHeight);

        // 2. 绘制各个区域
        drawBottomCards(canvas);       // 顶部底牌区域
        drawLeftAIInfo(canvas);        // 左侧AI信息
        drawRightAIInfo(canvas);       // 右侧AI信息
        drawCenterPlayedCards(canvas); // 中心出牌区域
        drawPlayerHand(canvas);        // 底部玩家手牌
        // 按钮由XML布局中的蓝色按钮提供，不在Canvas上绘制
        // drawButtons(canvas);
    }

    /**
     * 绘制桌面背景 - 欢乐斗地主风格
     * 深绿色毛毡质感 + 金色边框装饰线 + 四角金色花纹装饰
     */
    private void drawTableBackground(Canvas canvas, int viewWidth, int viewHeight) {
        // 1. 深绿色径向渐变背景（#1B5E20 到 #0D3310）
        float centerX = viewWidth / 2f;
        float centerY = viewHeight / 2f;
        float radius = (float) Math.sqrt(centerX * centerX + centerY * centerY);
        RadialGradient gradient = new RadialGradient(
                centerX, centerY, radius,
                new int[]{Color.parseColor("#1B5E20"), Color.parseColor("#145A16"), Color.parseColor("#0D3310")},
                new float[]{0f, 0.6f, 1f},
                Shader.TileMode.CLAMP);
        tableBackgroundPaint.setShader(gradient);
        canvas.drawRect(0, 0, viewWidth, viewHeight, tableBackgroundPaint);
        tableBackgroundPaint.setShader(null);

        // 2. 绘制毛毡质感纹理（细点状噪声模拟）
        drawFeltTexture(canvas, viewWidth, viewHeight);

        // 3. 金色外边框装饰线（缩小内边距，避免遮挡顶部内容）
        float borderInset = Math.max(4f, viewWidth * 0.006f);
        Paint outerBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        outerBorderPaint.setColor(Color.parseColor("#FFD700"));
        outerBorderPaint.setStyle(Paint.Style.STROKE);
        outerBorderPaint.setStrokeWidth(Math.max(2f, viewWidth * 0.003f));
        RectF outerBorderRect = new RectF(borderInset, borderInset,
                viewWidth - borderInset, viewHeight - borderInset);
        canvas.drawRoundRect(outerBorderRect, borderInset * 1.5f, borderInset * 1.5f, outerBorderPaint);

        // 4. 金色内边框装饰线
        float innerInset = borderInset + Math.max(3f, viewWidth * 0.004f);
        Paint innerBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        innerBorderPaint.setColor(Color.parseColor("#D4AF37"));
        innerBorderPaint.setStyle(Paint.Style.STROKE);
        innerBorderPaint.setStrokeWidth(Math.max(1f, viewWidth * 0.0015f));
        RectF innerBorderRect = new RectF(innerInset, innerInset,
                viewWidth - innerInset, viewHeight - innerInset);
        canvas.drawRoundRect(innerBorderRect, innerInset, innerInset, innerBorderPaint);

        // 5. 四角金色花纹装饰
        drawCornerDecorations(canvas, viewWidth, viewHeight, borderInset);
    }

    /**
     * 绘制毛毡质感纹理
     */
    private void drawFeltTexture(Canvas canvas, int viewWidth, int viewHeight) {
        Paint texturePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        texturePaint.setColor(Color.parseColor("#0A280C"));
        texturePaint.setAlpha(30);

        float step = Math.max(4f, viewWidth * 0.005f);
        for (float x = 0; x < viewWidth; x += step * 2) {
            for (float y = 0; y < viewHeight; y += step * 2) {
                float offsetX = (float) (Math.sin(y * 0.1) * step * 0.5);
                canvas.drawCircle(x + offsetX, y, step * 0.15f, texturePaint);
            }
        }
    }

    /**
     * 绘制四角金色花纹装饰
     */
    private void drawCornerDecorations(Canvas canvas, int viewWidth, int viewHeight, float borderInset) {
        float cornerSize = Math.max(30f, viewWidth * 0.04f);
        float strokeWidth = Math.max(2f, viewWidth * 0.003f);

        Paint cornerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cornerPaint.setColor(Color.parseColor("#FFD700"));
        cornerPaint.setStyle(Paint.Style.STROKE);
        cornerPaint.setStrokeWidth(strokeWidth);

        // 左上角
        drawCornerPattern(canvas, borderInset + cornerSize * 0.5f, borderInset + cornerSize * 0.5f,
                cornerSize, cornerPaint, 0);
        // 右上角
        drawCornerPattern(canvas, viewWidth - borderInset - cornerSize * 0.5f, borderInset + cornerSize * 0.5f,
                cornerSize, cornerPaint, 90);
        // 左下角
        drawCornerPattern(canvas, borderInset + cornerSize * 0.5f, viewHeight - borderInset - cornerSize * 0.5f,
                cornerSize, cornerPaint, 270);
        // 右下角
        drawCornerPattern(canvas, viewWidth - borderInset - cornerSize * 0.5f, viewHeight - borderInset - cornerSize * 0.5f,
                cornerSize, cornerPaint, 180);
    }

    /**
     * 绘制单个角的花纹图案
     */
    private void drawCornerPattern(Canvas canvas, float cx, float cy, float size, Paint paint, float rotation) {
        canvas.save();
        canvas.rotate(rotation, cx, cy);

        Path path = new Path();
        // 绘制L形花纹
        float halfSize = size * 0.5f;
        path.moveTo(cx - halfSize, cy - halfSize * 0.3f);
        path.lineTo(cx - halfSize, cy - halfSize);
        path.lineTo(cx - halfSize * 0.3f, cy - halfSize);

        path.moveTo(cx + halfSize * 0.3f, cy - halfSize);
        path.lineTo(cx + halfSize, cy - halfSize);
        path.lineTo(cx + halfSize, cy - halfSize * 0.3f);

        // 添加装饰弧线
        path.addCircle(cx - halfSize * 0.6f, cy - halfSize * 0.6f, size * 0.08f, Path.Direction.CW);
        path.addCircle(cx + halfSize * 0.6f, cy - halfSize * 0.6f, size * 0.08f, Path.Direction.CW);

        canvas.drawPath(path, paint);

        // 填充小圆点
        Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dotPaint.setColor(Color.parseColor("#FFD700"));
        dotPaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx - halfSize * 0.6f, cy - halfSize * 0.6f, size * 0.06f, dotPaint);
        canvas.drawCircle(cx + halfSize * 0.6f, cy - halfSize * 0.6f, size * 0.06f, dotPaint);

        canvas.restore();
    }

    /**
     * 绘制顶部底牌区域（3张地主牌）
     * 缩小50%，左上角，左距6%，每张牌只重叠20%
     */
    private void drawBottomCards(Canvas canvas) {
        if (bottomCards == null || bottomCards.isEmpty()) {
            return;
        }

        int viewWidth = getWidth();
        float smallCardWidth = tableCardWidth * 0.5f;
        float smallCardHeight = tableCardHeight * 0.5f;

        // 左上角，左边距6%，顶部距4%（下移避免被金色边框遮挡）
        float startX = viewWidth * 0.06f;
        float startY = getHeight() * 0.04f;

        // 每张牌露出80%，重叠20%（原30%重叠改为20%）
        float spacing = smallCardWidth * 0.80f;
        for (int i = 0; i < bottomCards.size(); i++) {
            Card card = bottomCards.get(i);
            float cardX = startX + i * spacing;
            drawScaledCard(canvas, card, cardX, startY, smallCardWidth, smallCardHeight);
        }

        // 身份文本：最右侧地主牌的右边，距离3个大写字母宽度
        float lastCardRight = startX + (bottomCards.size() - 1) * spacing + smallCardWidth;
        float letterWidth = smallCardWidth * 0.28f; // 一个大写字母约卡宽28%
        float textX = lastCardRight + letterWidth * 3f;
        float textY = startY + smallCardHeight * 0.6f;

        String myRole = playerLabels[0] != null ? playerLabels[0] : ((landlordStatus[0] == 2) ? "P1（地主）" : "P1（农民）");
        int myRoleColor = (landlordStatus[0] == 2) ? Color.parseColor("#FF6B35") : Color.parseColor("#4FC3F7");
        Paint rolePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        rolePaint.setColor(myRoleColor);
        rolePaint.setTextSize(smallCardWidth * 0.28f);
        rolePaint.setFakeBoldText(true);
        canvas.drawText(myRole, textX, textY, rolePaint);
    }

    /**
     * 绘制缩放的卡牌（用于底牌等需要缩小显示的场景）
     */
    private void drawScaledCard(Canvas canvas, Card card, float x, float y,
                                 float targetWidth, float targetHeight) {
        if (card == null) return;

        // 绘制缩小的简易卡牌
        RectF cardRect = new RectF(x, y, x + targetWidth, y + targetHeight);
        float smallRadius = targetWidth * 0.08f;

        cardPaint.setColor(Color.WHITE);
        cardPaint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(cardRect, smallRadius, smallRadius, cardPaint);
        canvas.drawRoundRect(cardRect, smallRadius, smallRadius, cardBorderPaint);

        boolean isJoker = card.getRank().isJoker();
        int cardColor;
        if (card.getRank() == com.gamecenter.app.doudizhu.model.Rank.SMALL_JOKER) {
            cardColor = Color.parseColor("#9C27B0");
        } else if (card.getRank() == com.gamecenter.app.doudizhu.model.Rank.BIG_JOKER) {
            cardColor = Color.parseColor("#D32F2F");
        } else {
            boolean isRed = card.getSuit() == com.gamecenter.app.doudizhu.model.Suit.HEART
                         || card.getSuit() == com.gamecenter.app.doudizhu.model.Suit.DIAMOND;
            cardColor = isRed ? Color.parseColor("#D32F2F") : Color.BLACK;
        }

        Paint smallPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        smallPaint.setColor(cardColor);
        smallPaint.setTextSize(targetWidth * 0.35f);
        smallPaint.setFakeBoldText(true);

        String rankSymbol = isJoker ? (card.getRank() == com.gamecenter.app.doudizhu.model.Rank.SMALL_JOKER ? getContext().getString(R.string.game_doudizhu_joker_small) : getContext().getString(R.string.game_doudizhu_joker_big))
                                    : card.getRank().getSymbol();
        float textX = x + targetWidth * 0.15f;
        float textY = y + targetHeight * 0.35f;
        canvas.drawText(rankSymbol, textX, textY, smallPaint);

        if (!isJoker) {
            smallPaint.setTextSize(targetWidth * 0.25f);
            String suitSymbol = getSuitSymbol(card.getSuit());
            canvas.drawText(suitSymbol, textX, textY + targetHeight * 0.25f, smallPaint);
        } else {
            smallPaint.setTextSize(targetWidth * 0.2f);
            canvas.drawText("王", textX, textY + targetHeight * 0.25f, smallPaint);
        }
    }

    /**
     * 绘制卡牌背面（蓝色底+交叉线花纹）。
     *
     * <p>用于 AI 手牌堆叠和未翻牌的显示。</p>
     *
     * @param canvas 画布
     * @param x      左上角 X 坐标
     * @param y      左上角 Y 坐标
     * @param width  卡牌宽度
     * @param height 卡牌高度
     */
    private void drawCardBack(Canvas canvas, float x, float y, float width, float height) {
        RectF cardRect = new RectF(x, y, x + width, y + height);
        float radius = width * 0.08f;
        Paint backPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        backPaint.setColor(Color.parseColor("#1976D2"));
        backPaint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(cardRect, radius, radius, backPaint);
        canvas.drawRoundRect(cardRect, radius, radius, cardBorderPaint);
        Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(Color.parseColor("#90CAF9"));
        linePaint.setStrokeWidth(Math.max(1f, width * 0.025f));
        canvas.drawLine(x + width * 0.2f, y + height * 0.2f,
                x + width * 0.8f, y + height * 0.8f, linePaint);
        canvas.drawLine(x + width * 0.8f, y + height * 0.2f,
                x + width * 0.2f, y + height * 0.8f, linePaint);
    }

    /**
     * 绘制记牌器面板。
     *
     * <p>在桌面顶部居中位置显示一个半透明面板，列出 3~K、A、2、小王、大王
     * 各牌面的剩余张数。已出完的牌显示为灰色，剩余 0-1 张时用黄色高亮提醒。</p>
     *
     * @param canvas 画布
     */
    private void drawCardCounter(Canvas canvas) {
        if (cardCounterCounts == null || cardCounterCounts.length < 15) return;
        int viewWidth = getWidth();
        float cellW = Math.min(tableCardWidth * 0.46f, viewWidth * 0.55f / 15f);
        float cellH = Math.max(tableCardWidth * 0.28f, 16f);
        float labelW = cellW * 1.65f;
        float panelW = labelW + cellW * 15f + cellW * 0.4f;
        float panelH = cellH * 2.15f;
        float x = (viewWidth - panelW) / 2f;
        float y = getHeight() * 0.065f;

        Paint panel = new Paint(Paint.ANTI_ALIAS_FLAG);
        panel.setColor(Color.parseColor("#7A08111C"));
        panel.setStyle(Paint.Style.FILL);
        RectF rect = new RectF(x, y, x + panelW, y + panelH);
        canvas.drawRoundRect(rect, cellH * 0.45f, cellH * 0.45f, panel);

        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setColor(Color.parseColor("#55FFFFFF"));
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(Math.max(1f, cellW * 0.04f));
        canvas.drawRoundRect(rect, cellH * 0.45f, cellH * 0.45f, stroke);

        Paint title = new Paint(Paint.ANTI_ALIAS_FLAG);
        title.setColor(Color.parseColor("#FFE082"));
        title.setFakeBoldText(true);
        title.setTextAlign(Paint.Align.CENTER);
        title.setTextSize(cellH * 0.58f);
        canvas.drawText("记牌", x + labelW * 0.48f, y + panelH * 0.58f, title);

        String[] labels = {"3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A", "2", "小", "大"};
        Paint rankPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        rankPaint.setTextAlign(Paint.Align.CENTER);
        rankPaint.setFakeBoldText(true);
        rankPaint.setTextSize(cellH * 0.48f);
        Paint countPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        countPaint.setTextAlign(Paint.Align.CENTER);
        countPaint.setTextSize(cellH * 0.52f);
        countPaint.setFakeBoldText(true);

        float startX = x + labelW;
        for (int i = 0; i < labels.length; i++) {
            float cx = startX + cellW * i + cellW * 0.5f;
            int count = Math.max(0, cardCounterCounts[i]);
            if (count == 0) {
                rankPaint.setColor(Color.parseColor("#78909C"));
                countPaint.setColor(Color.parseColor("#607D8B"));
            } else {
                rankPaint.setColor(i >= 12 ? Color.parseColor("#FFCC80") : Color.parseColor("#E3F2FD"));
                countPaint.setColor(count <= 1 ? Color.parseColor("#FFE082") : Color.WHITE);
            }
            canvas.drawText(labels[i], cx, y + cellH * 0.78f, rankPaint);
            canvas.drawText(String.valueOf(count), cx, y + cellH * 1.62f, countPaint);
        }
    }

    /**
     * 绘制 AI 手牌剩余数量徽章（蓝色圆角矩形 + "剩 N" 文字）。
     *
     * @param canvas  画布
     * @param centerX 徽章中心 X 坐标
     * @param y       徽章参考 Y 坐标
     * @param count   剩余手牌数
     */
    private void drawCardCountBadge(Canvas canvas, float centerX, float y, int count) {
        String text = "剩 " + Math.max(0, count);
        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(tableCardWidth * 0.18f);
        textPaint.setFakeBoldText(true);
        float padX = tableCardWidth * 0.18f;
        float padY = tableCardWidth * 0.10f;
        float textW = textPaint.measureText(text);
        RectF badge = new RectF(centerX - textW / 2f - padX, y - tableCardWidth * 0.22f,
                centerX + textW / 2f + padX, y + tableCardWidth * 0.18f + padY);
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(Color.parseColor("#BB0D47A1"));
        bg.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(badge, tableCardWidth * 0.13f, tableCardWidth * 0.13f, bg);
        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setColor(Color.parseColor("#88E3F2FD"));
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(Math.max(1f, tableCardWidth * 0.018f));
        canvas.drawRoundRect(badge, tableCardWidth * 0.13f, tableCardWidth * 0.13f, stroke);
        textPaint.setColor(Color.WHITE);
        canvas.drawText(text, centerX, y + tableCardWidth * 0.08f, textPaint);
    }

    /**
     * 绘制"不出"标签（带描边和阴影的金色文字）。
     *
     * @param canvas 画布
     * @param text   显示文本（通常为"不出"）
     * @param x      文字 X 坐标
     * @param y      文字基线 Y 坐标
     */
    private void drawPassLabel(Canvas canvas, String text, float x, float y) {
        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setTextAlign(Paint.Align.CENTER);
        stroke.setTextSize(tableCardWidth * 0.25f);
        stroke.setFakeBoldText(true);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(Math.max(2f, tableCardWidth * 0.025f));
        stroke.setColor(Color.parseColor("#AA3E2723"));
        canvas.drawText(text, x, y, stroke);

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setTextAlign(Paint.Align.CENTER);
        fill.setTextSize(tableCardWidth * 0.25f);
        fill.setFakeBoldText(true);
        fill.setColor(Color.parseColor("#FFE082"));
        fill.setShadowLayer(tableCardWidth * 0.04f, 0f, tableCardWidth * 0.02f, Color.parseColor("#CC000000"));
        canvas.drawText(text, x, y, fill);
    }

    /**
     * 绘制左侧 AI 信息区域。
     *
     * <p>包含：记牌器面板、蓝色手牌背面堆叠、剩余牌数徽章、
     * 身份标签（地主/农民）、出牌区或"不出"标签。</p>
     * <p>布局：蓝色牌堆紧贴地主牌下方，AI 出牌显示在牌堆内侧（面向中心）。</p>
     *
     * @param canvas 画布
     */
    private void drawLeftAIInfo(Canvas canvas) {
        drawCardCounter(canvas);
        int viewHeight = getHeight();
        float areaWidth = getWidth() * AI_INFO_WIDTH_RATIO;
        float centerX = areaWidth / 2f;

        float bottomCardsEndY = getHeight() * 0.02f + tableCardHeight * 0.5f;
        float letterAHeight = tableCardWidth * 0.16f;
        float blueStackTopY = bottomCardsEndY + letterAHeight * 2f + tableCardWidth * 0.16f;
        float stackCenterY = blueStackTopY + tableCardHeight * 0.4f;

        drawPlayerInfoPanel(canvas, centerX, bottomCardsEndY,
                blueStackTopY + tableCardHeight + tableCardWidth * 0.6f, areaWidth);

        drawAvatarFrame(canvas, centerX, bottomCardsEndY + tableCardWidth * 0.35f, tableCardWidth * 0.28f);

        drawStackedCards(canvas, centerX - tableCardWidth * 0.3f,
                blueStackTopY, leftAICardCount);

        drawRedCardCountBadge(canvas, centerX,
                blueStackTopY + tableCardHeight + tableCardWidth * 0.32f, leftAICardCount);

        String role = playerLabels[1] != null ? playerLabels[1] : ((landlordStatus[1] == 2) ? "P2（地主）" : "P2（农民）");
        int roleColor = (landlordStatus[1] == 2) ? Color.parseColor("#FFD700") : Color.parseColor("#B0BEC5");
        Paint rolePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        rolePaint.setColor(roleColor);
        rolePaint.setTextSize(tableCardWidth * 0.13f);
        rolePaint.setTextAlign(Paint.Align.CENTER);
        rolePaint.setFakeBoldText(true);
        canvas.drawText(role, centerX, bottomCardsEndY + letterAHeight * 2f, rolePaint);

        if (landlordStatus[1] == 2) {
            drawLandlordCrown(canvas, centerX, bottomCardsEndY + letterAHeight * 2f - tableCardWidth * 0.15f,
                    tableCardWidth * 0.12f);
        }

        float playedX = centerX + tableCardWidth * 0.5f + tableCardWidth * 0.3f;
        float playedY = stackCenterY - tableCardHeight * 0.2f;
        if (leftAIPassed) {
            drawPassLabel(canvas, "不出", playedX + tableCardWidth * 0.2f,
                    playedY + tableCardHeight * 0.5f);
        } else if (leftAIPlayedCards != null && !leftAIPlayedCards.isEmpty()) {
            drawPlayedCardsRow(canvas, leftAIPlayedCards, playedX, playedY);
        }
    }

    private void drawRightAIInfo(Canvas canvas) {
        int viewWidth = getWidth();
        int viewHeight = getHeight();
        float areaWidth = getWidth() * AI_INFO_WIDTH_RATIO;
        float centerX = viewWidth - areaWidth / 2f;

        float bottomCardsEndY = getHeight() * 0.02f + tableCardHeight * 0.5f;
        float letterAHeight = tableCardWidth * 0.16f;
        float blueStackTopY = bottomCardsEndY + letterAHeight * 2f + tableCardWidth * 0.16f;
        float stackCenterY = blueStackTopY + tableCardHeight * 0.4f;

        drawPlayerInfoPanel(canvas, centerX, bottomCardsEndY,
                blueStackTopY + tableCardHeight + tableCardWidth * 0.6f, areaWidth);

        drawAvatarFrame(canvas, centerX, bottomCardsEndY + tableCardWidth * 0.35f, tableCardWidth * 0.28f);

        drawStackedCards(canvas, centerX - tableCardWidth * 0.3f,
                blueStackTopY, rightAICardCount);

        drawRedCardCountBadge(canvas, centerX,
                blueStackTopY + tableCardHeight + tableCardWidth * 0.32f, rightAICardCount);

        String role = playerLabels[2] != null ? playerLabels[2] : ((landlordStatus[2] == 2) ? "P3（地主）" : "P3（农民）");
        int roleColor = (landlordStatus[2] == 2) ? Color.parseColor("#FFD700") : Color.parseColor("#B0BEC5");
        Paint rolePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        rolePaint.setColor(roleColor);
        rolePaint.setTextSize(tableCardWidth * 0.13f);
        rolePaint.setTextAlign(Paint.Align.CENTER);
        rolePaint.setFakeBoldText(true);
        canvas.drawText(role, centerX, bottomCardsEndY + letterAHeight * 2f, rolePaint);

        if (landlordStatus[2] == 2) {
            drawLandlordCrown(canvas, centerX, bottomCardsEndY + letterAHeight * 2f - tableCardWidth * 0.15f,
                    tableCardWidth * 0.12f);
        }

        float playedX = centerX - tableCardWidth * 0.5f - tableCardWidth * 0.3f;
        if (rightAIPlayedCards != null && !rightAIPlayedCards.isEmpty()) {
            playedX -= rightAIPlayedCards.size() * tableCardSpacing;
        }
        float playedY = stackCenterY - tableCardHeight * 0.2f;
        if (rightAIPassed) {
            drawPassLabel(canvas, "不出", playedX, playedY + tableCardHeight * 0.5f);
        } else if (rightAIPlayedCards != null && !rightAIPlayedCards.isEmpty()) {
            drawPlayedCardsRow(canvas, rightAIPlayedCards, playedX, playedY);
        }
    }

    /**
     * 绘制堆叠的卡牌背面
     * @param canvas 画布
     * @param startX 起始X坐标
     * @param startY 起始Y坐标
     * @param count 堆叠数量
     */
    private void drawStackedCards(Canvas canvas, float startX, float startY, int count) {
        // 最多显示5张堆叠效果
        int displayCount = Math.min(count, 5);
        float offset = tableCardWidth * 0.05f;

        for (int i = 0; i < displayCount; i++) {
            float x = startX + i * offset;
            float y = startY + i * offset;
            drawCardBack(canvas, x, y, tableCardWidth, tableCardHeight);
        }

    }

    /**
     * 绘制玩家信息面板
     * 半透明深色（#1A1A2E，alpha 180）+ 圆角 + 金色边框装饰
     */
    private void drawPlayerInfoPanel(Canvas canvas, float centerX, float topY, float bottomY, float areaWidth) {
        float panelPadding = tableCardWidth * 0.15f;
        RectF panelRect = new RectF(
                centerX - areaWidth / 2f - panelPadding,
                topY,
                centerX + areaWidth / 2f + panelPadding,
                bottomY);

        Paint panelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        panelPaint.setColor(Color.parseColor("#B31A1A2E"));
        panelPaint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(panelRect, tableCardWidth * 0.12f, tableCardWidth * 0.12f, panelPaint);

        Paint goldBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        goldBorderPaint.setColor(Color.parseColor("#FFD700"));
        goldBorderPaint.setStyle(Paint.Style.STROKE);
        goldBorderPaint.setStrokeWidth(2f);
        canvas.drawRoundRect(panelRect, tableCardWidth * 0.12f, tableCardWidth * 0.12f, goldBorderPaint);
    }

    /**
     * 绘制圆形头像框
     * 金色边框2px，内部深绿色
     */
    private void drawAvatarFrame(Canvas canvas, float cx, float cy, float radius) {
        Paint goldBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        goldBorderPaint.setColor(Color.parseColor("#FFD700"));
        goldBorderPaint.setStyle(Paint.Style.STROKE);
        goldBorderPaint.setStrokeWidth(2f);
        canvas.drawCircle(cx, cy, radius, goldBorderPaint);

        Paint innerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        innerPaint.setColor(Color.parseColor("#1B5E20"));
        innerPaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx, cy, radius - 1f, innerPaint);

        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.parseColor("#FFD700"));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(radius * 0.8f);
        textPaint.setFakeBoldText(true);
        canvas.drawText("AI", cx, cy + radius * 0.3f, textPaint);
    }

    /**
     * 绘制地主金色皇冠图标（Canvas绘制）
     */
    private void drawLandlordCrown(Canvas canvas, float cx, float cy, float size) {
        Paint crownFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        crownFillPaint.setColor(Color.parseColor("#FFD700"));
        crownFillPaint.setStyle(Paint.Style.FILL);

        Paint crownStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        crownStrokePaint.setColor(Color.parseColor("#B8860B"));
        crownStrokePaint.setStyle(Paint.Style.STROKE);
        crownStrokePaint.setStrokeWidth(1.5f);

        Path crownPath = new Path();
        float halfSize = size * 0.5f;
        float baseY = cy + halfSize * 0.3f;
        float topY = cy - halfSize * 0.5f;

        crownPath.moveTo(cx - halfSize, baseY);
        crownPath.lineTo(cx + halfSize, baseY);
        crownPath.lineTo(cx + halfSize * 0.8f, cy - halfSize * 0.1f);
        crownPath.lineTo(cx + halfSize * 0.4f, topY);
        crownPath.lineTo(cx, cy - halfSize * 0.1f);
        crownPath.lineTo(cx - halfSize * 0.4f, topY);
        crownPath.lineTo(cx - halfSize * 0.8f, cy - halfSize * 0.1f);
        crownPath.close();

        canvas.drawPath(crownPath, crownFillPaint);
        canvas.drawPath(crownPath, crownStrokePaint);

        Paint gemPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gemPaint.setColor(Color.parseColor("#D32F2F"));
        gemPaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx - halfSize * 0.4f, topY + size * 0.05f, size * 0.08f, gemPaint);
        canvas.drawCircle(cx + halfSize * 0.4f, topY + size * 0.05f, size * 0.08f, gemPaint);
        canvas.drawCircle(cx, topY - size * 0.05f, size * 0.1f, gemPaint);
    }

    /**
     * 绘制红色圆形徽章显示剩余牌数
     */
    private void drawRedCardCountBadge(Canvas canvas, float centerX, float y, int count) {
        String text = String.valueOf(Math.max(0, count));
        float badgeRadius = tableCardWidth * 0.18f;

        Paint redPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        redPaint.setColor(Color.parseColor("#D32F2F"));
        redPaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(centerX, y, badgeRadius, redPaint);

        Paint whiteBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        whiteBorderPaint.setColor(Color.WHITE);
        whiteBorderPaint.setStyle(Paint.Style.STROKE);
        whiteBorderPaint.setStrokeWidth(1.5f);
        canvas.drawCircle(centerX, y, badgeRadius, whiteBorderPaint);

        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(badgeRadius * 0.9f);
        textPaint.setFakeBoldText(true);
        canvas.drawText(text, centerX, y + badgeRadius * 0.35f, textPaint);
    }

    /**
     * 绘制中心出牌区域 - 欢乐斗地主风格
     * 半透明深色背景面板（#1A1A2E，alpha 120）+ 圆角 + 出牌高亮金色边框脉冲
     */
    private void drawCenterPlayedCards(Canvas canvas) {
        int viewWidth = getWidth();
        int viewHeight = getHeight();
        float areaHeight = getHeight() * PLAY_AREA_HEIGHT_RATIO;
        float areaTop = viewHeight * 0.35f;

        float panelPadX = viewWidth * 0.08f;
        float panelPadY = tableCardHeight * 0.15f;
        RectF playedAreaRect = new RectF(
                panelPadX,
                areaTop - panelPadY,
                viewWidth - panelPadX,
                areaTop + areaHeight + panelPadY);

        // 半透明深色背景面板
        Paint playedAreaPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        playedAreaPaint.setColor(Color.parseColor("#781A1A2E"));
        playedAreaPaint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(playedAreaRect, tableCardWidth * 0.15f, tableCardWidth * 0.15f, playedAreaPaint);

        // 金色边框
        Paint goldBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        goldBorderPaint.setColor(Color.parseColor("#FFD700"));
        goldBorderPaint.setStyle(Paint.Style.STROKE);
        goldBorderPaint.setStrokeWidth(2f);
        goldBorderPaint.setAlpha(180);
        canvas.drawRoundRect(playedAreaRect, tableCardWidth * 0.15f, tableCardWidth * 0.15f, goldBorderPaint);
        goldBorderPaint.setAlpha(255);

        // 出牌高亮：金色边框脉冲动画效果
        if (playerPlayedCards != null && !playerPlayedCards.isEmpty()) {
            drawPlayHighlightPulse(canvas, playedAreaRect);
        }

        if (playerPlayedCards != null && !playerPlayedCards.isEmpty()) {
            float playerAreaWidth = viewWidth * 0.4f;
            float playerStartX = (viewWidth - playerAreaWidth) / 2f;
            float playerY = areaTop + areaHeight * 0.3f;
            drawPlayedCardsRow(canvas, playerPlayedCards, playerStartX, playerY);
        }
    }

    /**
     * 绘制出牌高亮金色边框脉冲效果
     */
    private void drawPlayHighlightPulse(Canvas canvas, RectF rect) {
        long time = System.currentTimeMillis() % 2000;
        float pulse = (float) Math.sin(time * Math.PI / 1000.0) * 0.5f + 0.5f;

        Paint pulsePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pulsePaint.setColor(Color.parseColor("#FFD700"));
        pulsePaint.setStyle(Paint.Style.STROKE);
        pulsePaint.setStrokeWidth(3f + pulse * 2f);
        pulsePaint.setAlpha((int) (100 + pulse * 80));
        canvas.drawRoundRect(rect, tableCardWidth * 0.15f, tableCardWidth * 0.15f, pulsePaint);
    }

    /**
     * 绘制一行出牌
     */
    private void drawPlayedCardsRow(Canvas canvas, List<Card> cards, float startX, float startY) {
        if (cards == null || cards.isEmpty()) {
            return;
        }

        float spacing = tableCardSpacing;
        for (int i = 0; i < cards.size(); i++) {
            Card card = cards.get(i);
            float cardX = startX + i * spacing;
            drawScaledCard(canvas, card, cardX, startY, tableCardWidth, tableCardHeight);
        }
    }

    /**
     * 绘制按钮区域 - 欢乐斗地主风格金色渐变按钮
     * 金色渐变按钮（#FFD700 到 #FFA000）+ 圆角（radius=20）+ 阴影
     * 文字白色粗体，禁用状态灰色渐变
     */
    private void drawButtons(Canvas canvas) {
        int viewWidth = getWidth();
        int viewHeight = getHeight();

        // 按钮区域在出牌区下方、手牌区上方（出牌区35%-60%，按钮在52%-58%区域）
        float buttonAreaY = viewHeight * 0.52f;
        float buttonWidth = calculatedCardWidth * 1.6f;
        float buttonHeight = calculatedCardHeight * 0.30f;
        float buttonSpacing = buttonWidth * 0.25f;

        // 绘制"出牌"按钮（金色渐变）
        float chupaiX = viewWidth / 2f - buttonWidth - buttonSpacing / 2f;
        drawGradientButton(canvas, chupaiX, buttonAreaY, buttonWidth, buttonHeight,
                "出牌", true);

        // 绘制"不出"按钮（金色渐变）
        float buchuX = viewWidth / 2f + buttonSpacing / 2f;
        drawGradientButton(canvas, buchuX, buttonAreaY, buttonWidth, buttonHeight,
                "不出", true);

        // 绘制"提示"按钮（较小，在左侧）
        float tishiX = chupaiX - buttonWidth * 0.8f - buttonSpacing;
        drawGradientButton(canvas, tishiX, buttonAreaY, buttonWidth * 0.8f, buttonHeight,
                "提示", true);
    }

    /**
     * 绘制单个金色渐变按钮
     */
    private void drawGradientButton(Canvas canvas, float x, float y, float width, float height,
                                     String text, boolean enabled) {
        float radius = 20f;
        RectF buttonRect = new RectF(x, y, x + width, y + height);

        if (enabled) {
            // 金色渐变（#FFD700 到 #FFA000）
            LinearGradient gradient = new LinearGradient(
                    x, y, x, y + height,
                    new int[]{Color.parseColor("#FFD700"), Color.parseColor("#FFA000")},
                    new float[]{0f, 1f},
                    Shader.TileMode.CLAMP);
            buttonPaint.setShader(gradient);
        } else {
            // 禁用状态灰色渐变
            LinearGradient gradient = new LinearGradient(
                    x, y, x, y + height,
                    new int[]{Color.parseColor("#9E9E9E"), Color.parseColor("#616161")},
                    new float[]{0f, 1f},
                    Shader.TileMode.CLAMP);
            buttonPaint.setShader(gradient);
        }

        // 按钮阴影
        Paint buttonShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        buttonShadowPaint.setColor(Color.parseColor("#40000000"));
        buttonShadowPaint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(new RectF(x + 3, y + 4, x + width + 3, y + height + 5),
                radius, radius, buttonShadowPaint);

        // 按钮主体
        canvas.drawRoundRect(buttonRect, radius, radius, buttonPaint);
        buttonPaint.setShader(null);

        // 按钮边框（金色更亮）
        if (enabled) {
            Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            borderPaint.setColor(Color.parseColor("#FFECB3"));
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(1.5f);
            canvas.drawRoundRect(buttonRect, radius, radius, borderPaint);
        }

        // 白色粗体文字
        buttonTextPaint.setTextSize(height * 0.38f);
        canvas.drawText(text, x + width / 2f, y + height * 0.62f, buttonTextPaint);
    }

    /**
     * 绘制玩家手牌（底部区域，扇形展开）
     * 选中状态：金色发光外框 + 卡牌上移10px
     */
    private void drawPlayerHand(Canvas canvas) {
        if (playerHandCards == null || playerHandCards.isEmpty()) {
            return;
        }

        int viewWidth = getWidth();
        float areaBottom = getHeight() * (1 - PLAYER_HAND_BOTTOM_MARGIN_RATIO);
        float areaHeight = getHeight() * PLAYER_HAND_HEIGHT_RATIO;
        float areaTop = areaBottom - areaHeight;

        // 计算手牌起始位置（居中）
        int cardCount = playerHandCards.size();
        float totalWidth = cardSpacing * (cardCount - 1) + calculatedCardWidth;
        float startX = (viewWidth - totalWidth) / 2f;

        // 绘制每张手牌
        for (int i = 0; i < cardCount; i++) {
            Card card = playerHandCards.get(i);
            float baseX = startX + i * cardSpacing;
            float baseY = areaBottom - calculatedCardHeight;

            // 检查是否选中
            boolean isSelected = selectedIndices.contains(i);
            // 选中状态卡牌上移10px
            float cardY = isSelected ? baseY - 10f : baseY;

            // 绘制选中状态金色发光外框
            if (isSelected) {
                drawSelectedCardHighlight(canvas, baseX, cardY);
            }

            // 绘制动画中的卡牌
            if (isAnimating && animatingCards != null && animatingCards.contains(card)) {
                drawAnimatedCard(canvas, card, baseX, cardY);
            } else {
                drawCard(canvas, card, baseX, cardY, false, false);
            }
        }

        // "请选择要出的牌" 提示
        if (currentTurn == 0 && (playerPlayedCards == null || playerPlayedCards.isEmpty())) {
            float promptY = areaTop - calculatedCardHeight * 0.15f;
            hintPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("请选择要出的牌", viewWidth / 2f, promptY, hintPaint);
        }

        // 如果自己是地主，在手牌上方绘制金色皇冠标记
        if (landlordStatus[0] == 2) {
            drawLandlordCrown(canvas, viewWidth / 2f, areaTop - calculatedCardHeight * 0.4f,
                    calculatedCardWidth * 0.25f);
        }
    }

    /**
     * 绘制选中卡牌的金色发光外框
     * 金色发光外框（#FFD700，3px，带阴影）
     */
    private void drawSelectedCardHighlight(Canvas canvas, float x, float y) {
        float padding = 8f;
        RectF highlightRect = new RectF(
                x - padding, y - padding,
                x + calculatedCardWidth + padding, y + calculatedCardHeight + padding);

        // 外发光阴影
        Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glowPaint.setColor(Color.parseColor("#80FFD700"));
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(8f);
        glowPaint.setMaskFilter(new android.graphics.BlurMaskFilter(12f, android.graphics.BlurMaskFilter.Blur.NORMAL));
        canvas.drawRoundRect(highlightRect, 12f, 12f, glowPaint);

        // 金色边框
        Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setColor(Color.parseColor("#FFD700"));
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(3f);
        canvas.drawRoundRect(highlightRect, 12f, 12f, borderPaint);
    }

    /**
     * 绘制单张卡牌 - 欢乐斗地主风格
     * @param canvas 画布
     * @param card 卡牌实体
     * @param x 卡牌左上角X坐标
     * @param y 卡牌左上角Y坐标
     * @param showBack 是否显示背面
     * @param isPlayable 是否可出的牌（用于提示）
     */
    private void drawCard(Canvas canvas, Card card, float x, float y,
                          boolean showBack, boolean isPlayable) {
        if (showBack || card == null) {
            drawCardBackDetailed(canvas, x, y, calculatedCardWidth, calculatedCardHeight);
            return;
        }

        Bitmap bitmap = loadCardBitmap(card.getResName());
        if (bitmap != null) {
            canvas.drawBitmap(bitmap, x, y, null);
        } else {
            drawSimpleCard(canvas, card, x, y);
        }
    }

    /**
     * 绘制欢乐斗地主风格卡牌背面
     * 深蓝色（#1A237E）+ 金色菱形花纹图案
     */
    private void drawCardBackDetailed(Canvas canvas, float x, float y, float width, float height) {
        float radius = width * 0.08f;
        RectF cardRect = new RectF(x, y, x + width, y + height);

        // 1. 底部投影
        Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPaint.setColor(Color.parseColor("#60000000"));
        shadowPaint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(new RectF(x + 2, y + 2, x + width + 2, y + height + 4),
                radius, radius, shadowPaint);

        // 2. 深蓝色底色
        Paint backPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        backPaint.setColor(Color.parseColor("#1A237E"));
        backPaint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(cardRect, radius, radius, backPaint);

        // 3. 金色边框
        Paint goldBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        goldBorderPaint.setColor(Color.parseColor("#FFD700"));
        goldBorderPaint.setStyle(Paint.Style.STROKE);
        goldBorderPaint.setStrokeWidth(Math.max(1.5f, width * 0.02f));
        canvas.drawRoundRect(cardRect, radius, radius, goldBorderPaint);

        // 4. 内边框
        float inset = Math.max(width * 0.06f, height * 0.04f);
        RectF innerRect = new RectF(x + inset, y + inset, x + width - inset, y + height - inset);
        Paint innerBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        innerBorderPaint.setColor(Color.parseColor("#D4AF37"));
        innerBorderPaint.setStyle(Paint.Style.STROKE);
        innerBorderPaint.setStrokeWidth(Math.max(1f, width * 0.015f));
        canvas.drawRoundRect(innerRect, radius * 0.6f, radius * 0.6f, innerBorderPaint);

        // 5. 金色菱形花纹图案
        drawDiamondPattern(canvas, x + inset, y + inset, x + width - inset, y + height - inset, width, height);

        // 6. 中心装饰圆
        float centerCx = x + width / 2f;
        float centerCy = y + height / 2f;
        float centerSize = Math.min(width, height) * 0.18f;

        Paint centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        centerPaint.setColor(Color.parseColor("#55FFD700"));
        centerPaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(centerCx, centerCy, centerSize, centerPaint);

        Paint centerStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        centerStrokePaint.setColor(Color.parseColor("#CCFFD700"));
        centerStrokePaint.setStyle(Paint.Style.STROKE);
        centerStrokePaint.setStrokeWidth(Math.max(1f, width * 0.015f));
        canvas.drawCircle(centerCx, centerCy, centerSize, centerStrokePaint);
        canvas.drawCircle(centerCx, centerCy, centerSize * 0.55f, centerStrokePaint);

        Paint symbolPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        symbolPaint.setColor(Color.parseColor("#EEFFD700"));
        symbolPaint.setTextAlign(Paint.Align.CENTER);
        symbolPaint.setTextSize(centerSize * 1.1f);
        symbolPaint.setFakeBoldText(true);
        canvas.drawText("♦", centerCx, centerCy + centerSize * 0.38f, symbolPaint);
    }

    /**
     * 绘制菱形花纹图案
     */
    private void drawDiamondPattern(Canvas canvas, float left, float top, float right, float bottom,
                                     float cardWidth, float cardHeight) {
        float gridW = cardWidth * 0.18f;
        float gridH = cardHeight * 0.14f;
        float innerLeft = left + cardWidth * 0.03f;
        float innerTop = top + cardHeight * 0.03f;
        float innerRight = right - cardWidth * 0.03f;
        float innerBottom = bottom - cardHeight * 0.03f;

        Paint diamondStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        diamondStrokePaint.setColor(Color.parseColor("#80FFD700"));
        diamondStrokePaint.setStyle(Paint.Style.STROKE);
        diamondStrokePaint.setStrokeWidth(Math.max(0.8f, cardWidth * 0.01f));

        for (float dy = innerTop; dy < innerBottom; dy += gridH) {
            for (float dx = innerLeft; dx < innerRight; dx += gridW) {
                float cx = dx + gridW / 2f;
                float cy = dy + gridH / 2f;
                float dw = gridW * 0.38f;
                float dh = gridH * 0.38f;
                canvas.drawLine(cx, cy - dh, cx + dw, cy, diamondStrokePaint);
                canvas.drawLine(cx + dw, cy, cx, cy + dh, diamondStrokePaint);
                canvas.drawLine(cx, cy + dh, cx - dw, cy, diamondStrokePaint);
                canvas.drawLine(cx - dw, cy, cx, cy - dh, diamondStrokePaint);
            }
        }
    }

    /**
     * 绘制欢乐斗地主风格简易卡牌（当没有图片资源时的备选方案）
     * 白色圆角矩形底色 + 金色细边框 + 底部投影 + 红/黑花色和点数
     */
    private void drawSimpleCard(Canvas canvas, Card card, float x, float y) {
        if (card == null) return;

        float radius = 8f;
        RectF cardRect = new RectF(x, y, x + calculatedCardWidth, y + calculatedCardHeight);

        // 1. 底部投影（深灰色，偏移2px，模糊4px）
        Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPaint.setColor(Color.parseColor("#60000000"));
        shadowPaint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(new RectF(x + 2, y + 2, x + calculatedCardWidth + 2, y + calculatedCardHeight + 4),
                radius, radius, shadowPaint);

        // 2. 白色圆角矩形底色
        cardPaint.setColor(Color.WHITE);
        cardPaint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(cardRect, radius, radius, cardPaint);

        // 3. 金色细边框（#FFD700，1.5px）
        Paint goldBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        goldBorderPaint.setColor(Color.parseColor("#FFD700"));
        goldBorderPaint.setStyle(Paint.Style.STROKE);
        goldBorderPaint.setStrokeWidth(1.5f);
        canvas.drawRoundRect(cardRect, radius, radius, goldBorderPaint);

        boolean isJoker = card.getRank().isJoker();
        String rankSymbol = card.getRank().getSymbol();

        // 确定卡牌颜色
        int cardColor;
        String jokerText;
        if (card.getRank() == com.gamecenter.app.doudizhu.model.Rank.SMALL_JOKER) {
            cardColor = Color.parseColor("#9C27B0");
            jokerText = "小";
        } else if (card.getRank() == com.gamecenter.app.doudizhu.model.Rank.BIG_JOKER) {
            cardColor = Color.parseColor("#D32F2F");
            jokerText = "大";
        } else {
            boolean isRed = card.getSuit() == com.gamecenter.app.doudizhu.model.Suit.HEART
                    || card.getSuit() == com.gamecenter.app.doudizhu.model.Suit.DIAMOND;
            cardColor = isRed ? Color.parseColor("#D32F2F") : Color.parseColor("#212121");
            jokerText = null;
        }

        if (isJoker) {
            drawJokerCard(canvas, card, x, y, cardColor, jokerText);
        } else {
            drawNormalCard(canvas, card, x, y, cardColor, rankSymbol);
        }
    }

    /**
     * 绘制普通牌（非王牌）
     */
    private void drawNormalCard(Canvas canvas, Card card, float x, float y, int cardColor, String rankSymbol) {
        String suitSymbol = getSuitSymbol(card.getSuit());

        // 左上角牌值和花色
        Paint rankPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        rankPaint.setColor(cardColor);
        rankPaint.setTextSize(calculatedCardWidth * 0.22f);
        rankPaint.setFakeBoldText(true);

        Paint suitPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        suitPaint.setColor(cardColor);
        suitPaint.setTextSize(calculatedCardWidth * 0.18f);

        float textX = x + calculatedCardWidth * 0.15f;
        float textY = y + calculatedCardHeight * 0.22f;
        canvas.drawText(rankSymbol, textX, textY, rankPaint);
        canvas.drawText(suitSymbol, textX, textY + calculatedCardHeight * 0.14f, suitPaint);

        // 右上角（镜像）
        canvas.save();
        canvas.rotate(180, x + calculatedCardWidth * 0.85f, y + calculatedCardHeight * 0.78f);
        canvas.drawText(rankSymbol, x + calculatedCardWidth * 0.85f,
                y + calculatedCardHeight * 0.78f, rankPaint);
        canvas.drawText(suitSymbol, x + calculatedCardWidth * 0.85f,
                y + calculatedCardHeight * 0.64f, suitPaint);
        canvas.restore();

        // 中间大花色符号
        Paint centerSuitPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        centerSuitPaint.setColor(cardColor);
        centerSuitPaint.setTextSize(calculatedCardWidth * 0.35f);
        centerSuitPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(suitSymbol, x + calculatedCardWidth / 2f,
                y + calculatedCardHeight / 2f + calculatedCardWidth * 0.1f, centerSuitPaint);
    }

    /**
     * 绘制王牌（小王/大王）
     */
    private void drawJokerCard(Canvas canvas, Card card, float x, float y, int cardColor, String jokerText) {
        float cx = x + calculatedCardWidth / 2f;
        float cy = y + calculatedCardHeight / 2f;

        // 左上角 "JOKER" 标记
        Paint smallPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        smallPaint.setColor(cardColor);
        smallPaint.setTextSize(calculatedCardWidth * 0.14f);
        smallPaint.setFakeBoldText(true);
        canvas.drawText("JOKER", x + calculatedCardWidth * 0.15f,
                y + calculatedCardHeight * 0.16f, smallPaint);

        // 右上角（镜像）
        canvas.save();
        canvas.rotate(180, x + calculatedCardWidth * 0.85f, y + calculatedCardHeight * 0.84f);
        canvas.drawText("JOKER", x + calculatedCardWidth * 0.85f,
                y + calculatedCardHeight * 0.84f, smallPaint);
        canvas.restore();

        // 中间大号中文文字
        Paint jokerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        jokerPaint.setColor(cardColor);
        jokerPaint.setTextSize(calculatedCardWidth * 0.32f);
        jokerPaint.setTextAlign(Paint.Align.CENTER);
        jokerPaint.setFakeBoldText(true);
        canvas.drawText(jokerText + "王", cx, cy + calculatedCardWidth * 0.1f, jokerPaint);

        // 中间星形装饰
        Paint symbolPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        symbolPaint.setColor(cardColor);
        symbolPaint.setTextSize(calculatedCardWidth * 0.22f);
        symbolPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("★", cx, cy - calculatedCardHeight * 0.12f, symbolPaint);
    }

    /**
     * 获取花色符号
     */
    private String getSuitSymbol(com.gamecenter.app.doudizhu.model.Suit suit) {
        switch (suit) {
            case SPADE: return "♠";
            case HEART: return "♥";
            case CLUB: return "♣";
            case DIAMOND: return "♦";
            default: return "";
        }
    }

    /**
     * 绘制动画中的卡牌（从手牌移动到出牌区）
     * 带路径曲线、旋转和缩放效果
     */
    private void drawAnimatedCard(Canvas canvas, Card card, float startX, float startY) {
        if (!isAnimating || animatingCards == null) return;

        // 计算动画目标位置（桌面中心出牌区）
        float targetX = getWidth() / 2f - calculatedCardWidth / 2f;
        float targetY = getHeight() * 0.35f;

        float progress = animationProgress;

        // 使用贝塞尔曲线路径：手牌 -> 控制点（上方弧线） -> 出牌区
        float controlX = (startX + targetX) / 2f;
        float controlY = Math.min(startY, targetY) - getHeight() * 0.15f;

        // 二次贝塞尔曲线计算当前位置
        float t = progress;
        float oneMinusT = 1 - t;
        float currentX = oneMinusT * oneMinusT * startX + 2 * oneMinusT * t * controlX + t * t * targetX;
        float currentY = oneMinusT * oneMinusT * startY + 2 * oneMinusT * t * controlY + t * t * targetY;

        // 旋转效果：从0度旋转到360度
        float rotation = progress * 360f;

        // 缩放效果：先放大再恢复正常
        float scale = 1f + (float) Math.sin(progress * Math.PI) * 0.2f;

        canvas.save();
        canvas.translate(currentX + calculatedCardWidth / 2f, currentY + calculatedCardHeight / 2f);
        canvas.rotate(rotation);
        canvas.scale(scale, scale);
        canvas.translate(-calculatedCardWidth / 2f, -calculatedCardHeight / 2f);

        // 绘制卡牌
        drawCard(canvas, card, 0, 0, false, false);

        canvas.restore();
    }

    // ============ 触摸事件处理 ============

    /**
     * 处理触摸事件，支持单击选牌和拖动批量选牌。
     *
     * <p>触摸逻辑：</p>
     * <ul>
     *   <li>ACTION_DOWN：记录起始位置和触摸的卡牌索引</li>
     *   <li>ACTION_MOVE：移动超过 15px 判定为拖动，拖动时批量选中经过的卡牌</li>
     *   <li>ACTION_UP：非拖动时切换卡牌选中状态，通知监听器</li>
     * </ul>
     * <p>动画期间屏蔽所有触摸事件。</p>
     *
     * @param event 触摸事件
     * @return true 表示事件已消费
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (isAnimating) {
            return true;
        }

        float touchX = event.getX();
        float touchY = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                touchStartX = touchX;
                touchStartY = touchY;
                isDragging = false;
                lastTouchedCardIndex = getTouchedCardIndex(touchX, touchY);
                return true;

            case MotionEvent.ACTION_MOVE:
                float dx = Math.abs(touchX - touchStartX);
                float dy = Math.abs(touchY - touchStartY);
                if (dx > 15 || dy > 15) {
                    isDragging = true;
                }

                if (isDragging) {
                    int hoveredIndex = getTouchedCardIndex(touchX, touchY);
                    if (hoveredIndex != -1 && hoveredIndex != lastTouchedCardIndex) {
                        selectCardsInRange(lastTouchedCardIndex, hoveredIndex);
                        lastTouchedCardIndex = hoveredIndex;
                    }
                }
                return true;

            case MotionEvent.ACTION_UP:
                if (!isDragging && lastTouchedCardIndex != -1) {
                    toggleCardSelection(lastTouchedCardIndex);
                }

                if (cardTouchListener != null) {
                    cardTouchListener.onCardsSelected(new ArrayList<>(selectedCards));
                }

                isDragging = false;
                lastTouchedCardIndex = -1;
                return true;

            default:
                return super.onTouchEvent(event);
        }
    }

    /**
     * 获取触摸位置对应的卡牌索引。
     *
     * <p>从右向左遍历手牌（右边的牌在上层），优先命中顶层牌。
     * 每张牌的有效点击区域为：自身可见宽度 + 最右侧牌的完整宽度。</p>
     *
     * @param touchX 触摸 X 坐标
     * @param touchY 触摸 Y 坐标
     * @return 被点击的卡牌索引，-1 表示未命中
     */
    private int getTouchedCardIndex(float touchX, float touchY) {
        if (playerHandCards == null || playerHandCards.isEmpty()) {
            return -1;
        }

        int viewWidth = getWidth();
        float areaBottom = getHeight() * (1 - PLAYER_HAND_BOTTOM_MARGIN_RATIO);
        int cardCount = playerHandCards.size();
        float totalWidth = cardSpacing * (cardCount - 1) + calculatedCardWidth;
        float startX = (viewWidth - totalWidth) / 2f;

        for (int i = cardCount - 1; i >= 0; i--) {
            float cardX = startX + i * cardSpacing;
            float baseY = areaBottom - calculatedCardHeight;
            float cardY = selectedIndices.contains(i) ? baseY - cardLiftHeight : baseY;

            // 可见区域：[cardX, cardX + cardSpacing]，最末牌：[cardX, cardX + cardWidth]
            float exposedRight = (i == cardCount - 1)
                    ? cardX + calculatedCardWidth
                    : cardX + cardSpacing;

            float yTop = cardY - calculatedCardHeight * 0.4f;
            float yBottom = cardY + calculatedCardHeight;

            if (touchX >= cardX && touchX <= exposedRight
                    && touchY >= yTop && touchY <= yBottom) {
                return i;
            }
        }

        return -1;
    }

    /**
     * 切换卡牌选中状态
     */
    private void toggleCardSelection(int index) {
        if (index < 0 || index >= playerHandCards.size()) {
            return;
        }

        if (selectedIndices.contains(index)) {
            // 取消选中
            selectedIndices.remove(Integer.valueOf(index));
            selectedCards.remove(playerHandCards.get(index));
        } else {
            // 选中
            selectedIndices.add(index);
            selectedCards.add(playerHandCards.get(index));
        }

        // 重绘
        invalidate();
    }

    /**
     * 选择范围内所有卡牌（拖动选择时使用）。
     *
     * <p>将 fromIndex 到 toIndex 之间的所有卡牌设为选中状态，
     * 范围外的卡牌取消选中。</p>
     *
     * @param fromIndex 起始卡牌索引
     * @param toIndex   结束卡牌索引
     */
    private void selectCardsInRange(int fromIndex, int toIndex) {
        int start = Math.min(fromIndex, toIndex);
        int end = Math.max(fromIndex, toIndex);

        for (int i = start; i <= end; i++) {
            if (!selectedIndices.contains(i)) {
                selectedIndices.add(i);
                selectedCards.add(playerHandCards.get(i));
            }
        }

        invalidate();
    }

    // ============ 动画方法 ============

    /**
     * 出牌动画：将选中的卡牌从手牌区平滑移动到桌面中心。
     *
     * <p>使用 {@link ValueAnimator} 在 400ms 内将卡牌从手牌位置移动到出牌区，
     * 使用 OvershootInterpolator 实现出牌时的弹性效果，
     * 动画期间屏蔽触摸事件，动画完成后回调 {@link OnAnimationCompleteListener}。</p>
     *
     * @param cards    要出的卡牌列表
     * @param listener 动画完成回调
     */
    public void playCardAnim(List<Card> cards, OnAnimationCompleteListener listener) {
        if (cards == null || cards.isEmpty()) {
            if (listener != null) {
                listener.onAnimationComplete();
            }
            return;
        }

        animatingCards = new ArrayList<>(cards);
        isAnimating = true;

        playCardAnimator = ValueAnimator.ofFloat(0f, 1f);
        playCardAnimator.setDuration(PLAY_CARD_ANIMATION_DURATION);
        playCardAnimator.setInterpolator(new OvershootInterpolator(1.5f));

        playCardAnimator.addUpdateListener(animation -> {
            animationProgress = (float) animation.getAnimatedValue();
            invalidate();
        });

        playCardAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                isAnimating = false;
                animatingCards = null;
                animationProgress = 1.0f;
                if (listener != null) {
                    listener.onAnimationComplete();
                }
            }
        });

        playCardAnimator.start();
    }

    /**
     * 发牌动画：卡牌从牌堆飞到手牌区，带旋转和缩放
     * 使用 OvershootInterpolator + 400ms
     * @param cardCount 发牌数量
     * @param listener  动画完成回调
     */
    public void dealCardsAnim(int cardCount, OnAnimationCompleteListener listener) {
        if (cardCount <= 0) {
            if (listener != null) {
                listener.onAnimationComplete();
            }
            return;
        }

        totalDealCards = cardCount;
        dealAnimatingCardIndex = 0;
        isDealAnimating = true;
        pendingDealListener = listener;

        startNextDealAnimation();
    }

    /**
     * 开始下一张牌的发牌动画
     */
    private void startNextDealAnimation() {
        if (dealAnimatingCardIndex >= totalDealCards) {
            isDealAnimating = false;
            dealAnimationProgress = 0f;
            if (pendingDealListener != null) {
                pendingDealListener.onAnimationComplete();
                pendingDealListener = null;
            }
            return;
        }

        dealCardAnimator = ValueAnimator.ofFloat(0f, 1f);
        dealCardAnimator.setDuration(DEAL_CARD_ANIMATION_DURATION);
        dealCardAnimator.setInterpolator(new OvershootInterpolator(1.2f));

        dealCardAnimator.addUpdateListener(animation -> {
            dealAnimationProgress = (float) animation.getAnimatedValue();
            invalidate();
        });

        dealCardAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                dealAnimatingCardIndex++;
                startNextDealAnimation();
            }
        });

        dealCardAnimator.start();
    }

    // ============ 数据更新方法 ============

    /**
     * 设置玩家手牌
     * @param cards 手牌列表
     */
    public void setPlayerHandCards(List<Card> cards) {
        float oldCardWidth = calculatedCardWidth;
        this.playerHandCards = cards != null ? new ArrayList<>(cards) : new ArrayList<>();
        this.selectedCards.clear();
        this.selectedIndices.clear();
        calculateDimensions(); // 根据新牌数重新计算卡牌尺寸
        if (Math.abs(oldCardWidth - calculatedCardWidth) > 0.5f && cardBitmapCache != null) {
            cardBitmapCache.clear();
            loadCardBackImage();
        }
        invalidate();
    }

    /**
     * 获取选中的卡牌
     * @return 选中的卡牌列表
     */
    public List<Card> getSelectedCards() {
        return new ArrayList<>(selectedCards);
    }

    /**
     * 清空选中的卡牌
     */
    public void clearSelection() {
        selectedCards.clear();
        selectedIndices.clear();
        invalidate();
    }

    public void selectCards(List<Card> cards) {
        if (cards == null || playerHandCards == null) return;
        selectedCards.clear();
        selectedIndices.clear();
        for (Card card : cards) {
            int index = playerHandCards.indexOf(card);
            if (index >= 0 && !selectedIndices.contains(index)) {
                selectedIndices.add(index);
                selectedCards.add(playerHandCards.get(index));
            }
        }
        invalidate();
    }

    /**
     * 设置底牌
     * @param cards 底牌列表（3张）
     */
    public void setBottomCards(List<Card> cards) {
        this.bottomCards = cards != null ? new ArrayList<>(cards) : new ArrayList<>();
        invalidate();
    }

    /**
     * 设置玩家出牌
     * @param cards 出的牌列表
     */
    public void setPlayerPlayedCards(List<Card> cards) {
        this.playerPlayedCards = cards != null ? new ArrayList<>(cards) : new ArrayList<>();
        invalidate();
    }

    /**
     * 设置左AI出牌
     */
    public void setLeftAIPlayedCards(List<Card> cards) {
        this.leftAIPlayedCards = cards != null ? new ArrayList<>(cards) : new ArrayList<>();
        this.leftAIPassed = (cards == null);
        invalidate();
    }

    /**
     * 设置右 AI 出牌。
     *
     * @param cards 出的牌列表，null 表示不出
     */
    public void setRightAIPlayedCards(List<Card> cards) {
        this.rightAIPlayedCards = cards != null ? new ArrayList<>(cards) : new ArrayList<>();
        this.rightAIPassed = (cards == null);
        invalidate();
    }

    /**
     * 设置 AI 是否选择"不出"的状态。
     *
     * @param leftPassed  左 AI 是否不出
     * @param rightPassed 右 AI 是否不出
     */
    public void setPassStates(boolean leftPassed, boolean rightPassed) {
        this.leftAIPassed = leftPassed;
        this.rightAIPassed = rightPassed;
        invalidate();
    }

    /**
     * 清空所有出牌
     */
    public void clearAllPlayedCards() {
        this.playerPlayedCards.clear();
        this.leftAIPlayedCards.clear();
        this.rightAIPlayedCards.clear();
        this.leftAIPassed = false;
        this.rightAIPassed = false;
        invalidate();
    }

    /**
     * 设置AI手牌数量
     * @param leftCount 左边AI剩余牌数
     * @param rightCount 右边AI剩余牌数
     */
    public void setAICardCounts(int leftCount, int rightCount) {
        this.leftAICardCount = leftCount;
        this.rightAICardCount = rightCount;
        invalidate();
    }

    /**
     * 设置当前回合
     * @param turn 0=玩家, 1=左AI, 2=右AI
     */
    public void setCurrentTurn(int turn) {
        this.currentTurn = turn;
        invalidate();
    }

    /**
     * 设置玩家地主身份
     * @param status 0=未确定, 1=农民, 2=地主
     */
    public void setPlayerLandlordStatus(int status) {
        this.playerLandlordStatus = status;
        this.landlordStatus[0] = status;
        invalidate();
    }

    /**
     * 设置所有玩家地主身份
     * @param statuses 地主状态数组 [玩家, 左AI, 右AI]
     */
    public void setAllLandlordStatus(int[] statuses) {
        if (statuses != null && statuses.length >= 3) {
            this.landlordStatus = statuses;
            this.playerLandlordStatus = statuses[0];
        }
        invalidate();
    }

    /**
     * 设置三个玩家的身份标签。
     *
     * @param labels 标签数组 [玩家, 左AI, 右AI]，如 ["你(地主)", "左AI(农民)", "右AI(农民)"]
     */
    public void setPlayerLabels(String[] labels) {
        if (labels != null && labels.length >= 3) {
            this.playerLabels = new String[]{labels[0], labels[1], labels[2]};
        }
        invalidate();
    }

    private int[] createFullDeckCounter() {
        int[] counts = new int[15];
        for (int i = 0; i < 13; i++) {
            counts[i] = 4;
        }
        counts[13] = 1;
        counts[14] = 1;
        return counts;
    }

    /**
     * 设置记牌器的计数数组。
     *
     * <p>数组长度为 15，索引 0-12 对应 3~K，索引 13 对应小王，索引 14 对应大王。</p>
     *
     * @param counts 计数数组
     */
    public void setCardCounterCounts(int[] counts) {
        if (counts == null || counts.length < 15) {
            this.cardCounterCounts = createFullDeckCounter();
        } else {
            this.cardCounterCounts = new int[15];
            System.arraycopy(counts, 0, this.cardCounterCounts, 0, 15);
        }
        invalidate();
    }

    /**
     * 获取AI手牌数量
     */
    public int getLeftAICardCount() {
        return leftAICardCount;
    }

    /**
     * 获取右 AI 的手牌剩余数量。
     *
     * @return 右 AI 剩余手牌数
     */
    public int getRightAICardCount() {
        return rightAICardCount;
    }

    /**
     * 获取当前轮到哪个玩家
     */
    public int getCurrentTurn() {
        return currentTurn;
    }

    /**
     * 获取玩家地主状态
     */
    public int getPlayerLandlordStatus() {
        return playerLandlordStatus;
    }

    // ============ 回调接口 ============

    /**
     * 卡牌触摸监听器
     */
    public interface OnCardTouchListener {
        void onCardsSelected(List<Card> selectedCards);
    }

    /**
     * 设置卡牌触摸监听器，在玩家选中/取消选中卡牌时回调。
     *
     * @param listener 卡牌触摸监听器
     */
    public void setOnCardTouchListener(OnCardTouchListener listener) {
        this.cardTouchListener = listener;
    }

    /**
     * 桌面点击监听器（用于不出按钮区域）
     */
    public interface OnTableClickListener {
        void onTableClicked();
    }

    /**
     * 设置桌面点击监听器，在玩家点击桌面空白区域时回调。
     *
     * @param listener 桌面点击监听器
     */
    public void setOnTableClickListener(OnTableClickListener listener) {
        this.tableClickListener = listener;
    }

    /**
     * 动画完成监听器
     */
    public interface OnAnimationCompleteListener {
        void onAnimationComplete();
    }

    /**
     * 取消所有动画
     */
    public void cancelAnimations() {
        if (playCardAnimator != null && playCardAnimator.isRunning()) {
            playCardAnimator.cancel();
        }
        if (dealCardAnimator != null && dealCardAnimator.isRunning()) {
            dealCardAnimator.cancel();
        }
        isAnimating = false;
        isDealAnimating = false;
        animatingCards = null;
        dealAnimatingCardIndex = 0;
        dealAnimationProgress = 0f;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        cancelAnimations();
        // P0 内存泄漏修复：清理位图缓存并回收所有 Bitmap。
        if (cardBitmapCache != null) {
            for (Bitmap bmp : cardBitmapCache.values()) {
                if (bmp != null && !bmp.isRecycled()) {
                    bmp.recycle();
                }
            }
            cardBitmapCache.clear();
        }
        if (cardBackBitmap != null && !cardBackBitmap.isRecycled()) {
            cardBackBitmap.recycle();
        }
        cardBackBitmap = null;
    }
}
