package com.gamecenter.app.chinesechess;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;

import java.util.List;

/**
 * 中国象棋棋盘自定义视图。
 * <p>
 * 职责：
 * <ul>
 *   <li>渲染棋盘网格线、九宫斜线、楚河汉界</li>
 *   <li>渲染棋子（圆形底色 + 中文字符），区分红黑双方</li>
 *   <li>处理触摸交互：点击选中棋子、显示合法走法高亮</li>
 *   <li>播放走棋动画（棋子从起点滑动到终点的过渡动画）</li>
 *   <li>显示上一步走棋标记和选中棋子高亮</li>
 * </ul>
 * <p>
 * 关键设计决策：
 * <ul>
 *   <li>棋盘尺寸自适应：根据View宽高计算格子大小，保持棋盘居中</li>
 *   <li>棋子使用中文字符渲染（非图片），通过 {@link Paint} 设置字体和颜色</li>
 *   <li>走棋动画使用 {@link ValueAnimator} + Overshoot插值器，产生弹性回弹效果</li>
 *   <li>触摸事件通过 {@link GestureDetector} 处理，将像素坐标转换为棋盘逻辑坐标</li>
 *   <li>锁定模式（locked）下忽略触摸事件，用于AI思考期间禁止玩家操作</li>
 * </ul>
 *
 * 【初学者指南】
 * 这个类是中国象棋的棋盘画板，比五子棋和围棋的视图复杂得多：
 * - 棋盘有"楚河汉界"（中间的空白区域）
 * - 棋盘有"九宫格"斜线（将帅活动区域的对角线）
 * - 棋子用中文字符显示（帥、將、車、馬、炮等），红黑双方颜色不同
 * - 走棋有动画效果：棋子从起点滑动到终点，带弹性回弹
 * - 选中棋子后会显示所有合法走法（绿色圆点或圆环）
 */
public class ChineseChessView extends View {

    /** 棋盘左右边距（像素），用于棋盘居中 */
    private float boardPadding = 24f;

    /** 每个格子的像素大小，根据View尺寸动态计算 */
    private float cellSize;

    /** 棋盘左上角起点的X像素坐标 */
    private float boardLeft;

    /** 棋盘左上角起点的Y像素坐标 */
    private float boardTop;

    /** 绑定的游戏逻辑对象 */
    private ChineseChessGame game;

    /** 是否锁定棋盘（锁定后忽略触摸事件） */
    private boolean locked = false;

    /** 选中棋子的列坐标，-1 表示未选中 */
    private int selectedX = -1;

    /** 选中棋子的行坐标，-1 表示未选中 */
    private int selectedY = -1;

    /** 选中棋子的合法走法列表，每个元素为 [toX, toY] */
    private List<int[]> validMoves;

    /** 上一步走棋的起始列，-1 表示无标记 */
    private int lastFromX = -1;

    /** 上一步走棋的起始行 */
    private int lastFromY = -1;

    /** 上一步走棋的目标列 */
    private int lastToX = -1;

    /** 上一步走棋的目标行 */
    private int lastToY = -1;

    /** 格子点击事件监听器 */
    public interface OnCellClickListener {
        /**
         * 当棋盘格子被点击时回调。
         *
         * @param col 列坐标（0~8）
         * @param row 行坐标（0~9）
         */
        void onCellClick(int col, int row);
    }

    /** 当前注册的格子点击监听器 */
    private OnCellClickListener cellClickListener;

    /** 手势检测器，用于识别点击事件 */
    private GestureDetector gestureDetector;

    /** 棋盘线条画笔 */
    private Paint linePaint;

    /** 棋盘粗线条画笔（边框） */
    private Paint thickLinePaint;

    /** 红方棋子文字画笔 */
    private Paint redTextPaint;

    /** 黑方棋子文字画笔 */
    private Paint blackTextPaint;

    /** 红方棋子底色画笔 */
    private Paint redPiecePaint;

    /** 黑方棋子底色画笔 */
    private Paint blackPiecePaint;

    /** 红方棋子边框画笔 */
    private Paint redPieceBorderPaint;

    /** 黑方棋子边框画笔 */
    private Paint blackPieceBorderPaint;

    /** 红方棋子内圈装饰画笔 */
    private Paint redInnerPaint;

    /** 黑方棋子内圈装饰画笔 */
    private Paint blackInnerPaint;

    /** 棋子投影画笔 */
    private Paint pieceShadowPaint;

    /** 可吃子标记画笔 */
    private Paint captureMovePaint;

    /** 选中高亮画笔 */
    private Paint selectedPaint;

    /** 合法走法高亮画笔 */
    private Paint validMovePaint;

    /** 上一步走棋标记画笔 */
    private Paint lastMovePaint;

    /** 提示起始位置画笔（蓝色脉冲光环） */
    private Paint hintFromPaint;

    /** 提示目标位置画笔（蓝色脉冲光环） */
    private Paint hintToPaint;

    /** 提示箭头画笔 */
    private Paint hintArrowPaint;

    /** 提示起始列，-1 表示无提示 */
    private int hintFromX = -1;

    /** 提示起始行 */
    private int hintFromY = -1;

    /** 提示目标列 */
    private int hintToX = -1;

    /** 提示目标行 */
    private int hintToY = -1;

    /** 提示动画：脉冲相位（0~1循环） */
    private float hintPulsePhase = 0f;

    /** 提示脉冲动画 */
    private ValueAnimator hintPulseAnimator;

    /** 楚河汉界文字画笔 */
    private Paint riverTextPaint;

    /** 走棋动画：当前动画中的棋子 */
    private ChineseChessGame.Piece animatingPiece;

    /** 走棋动画：当前动画X坐标（像素） */
    private float animCurrentX;

    /** 走棋动画：当前动画Y坐标（像素） */
    private float animCurrentY;

    /** 走棋动画：是否正在播放 */
    private boolean isAnimating = false;

    /** 走棋动画完成后的回调 */
    private Runnable onAnimationEnd;

    /** 当前走棋动画实例 */
    private ValueAnimator currentAnimator;

    /**
     * XML布局使用的构造函数。
     *
     * @param context 上下文
     * @param attrs   XML属性集
     */
    public ChineseChessView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    /**
     * 仅上下文的构造函数。
     *
     * @param context 上下文
     */
    public ChineseChessView(Context context) {
        super(context);
        init();
    }

    /**
     * 初始化画笔和手势检测器。
     * <p>创建所有绘制所需的Paint对象，设置颜色、线宽、字体等属性。
     */
    private void init() {
        linePaint = new Paint();
        linePaint.setColor(Color.parseColor("#5D4037"));
        linePaint.setStrokeWidth(2.5f);
        linePaint.setAntiAlias(true);

        thickLinePaint = new Paint();
        thickLinePaint.setColor(Color.parseColor("#3E2723"));
        thickLinePaint.setStrokeWidth(5f);
        thickLinePaint.setStyle(Paint.Style.STROKE);
        thickLinePaint.setAntiAlias(true);

        redPiecePaint = new Paint();
        redPiecePaint.setColor(Color.parseColor("#FFF3E0"));
        redPiecePaint.setAntiAlias(true);

        blackPiecePaint = new Paint();
        blackPiecePaint.setColor(Color.parseColor("#FAFAFA"));
        blackPiecePaint.setAntiAlias(true);

        redPieceBorderPaint = new Paint();
        redPieceBorderPaint.setColor(Color.parseColor("#C62828"));
        redPieceBorderPaint.setStyle(Paint.Style.STROKE);
        redPieceBorderPaint.setStrokeWidth(2.5f);
        redPieceBorderPaint.setAntiAlias(true);

        blackPieceBorderPaint = new Paint();
        blackPieceBorderPaint.setColor(Color.parseColor("#212121"));
        blackPieceBorderPaint.setStyle(Paint.Style.STROKE);
        blackPieceBorderPaint.setStrokeWidth(2.5f);
        blackPieceBorderPaint.setAntiAlias(true);

        redInnerPaint = new Paint();
        redInnerPaint.setColor(Color.parseColor("#C62828"));
        redInnerPaint.setStyle(Paint.Style.STROKE);
        redInnerPaint.setStrokeWidth(1f);
        redInnerPaint.setAntiAlias(true);

        blackInnerPaint = new Paint();
        blackInnerPaint.setColor(Color.parseColor("#212121"));
        blackInnerPaint.setStyle(Paint.Style.STROKE);
        blackInnerPaint.setStrokeWidth(1f);
        blackInnerPaint.setAntiAlias(true);

        pieceShadowPaint = new Paint();
        pieceShadowPaint.setColor(0x40000000);
        pieceShadowPaint.setAntiAlias(true);

        redTextPaint = new Paint();
        redTextPaint.setColor(Color.parseColor("#C62828"));
        redTextPaint.setAntiAlias(true);
        redTextPaint.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        redTextPaint.setTextAlign(Paint.Align.CENTER);

        blackTextPaint = new Paint();
        blackTextPaint.setColor(Color.parseColor("#1A1A1A"));
        blackTextPaint.setAntiAlias(true);
        blackTextPaint.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        blackTextPaint.setTextAlign(Paint.Align.CENTER);

        selectedPaint = new Paint();
        selectedPaint.setColor(Color.parseColor("#4CAF50"));
        selectedPaint.setStyle(Paint.Style.STROKE);
        selectedPaint.setStrokeWidth(6f);
        selectedPaint.setAntiAlias(true);
        selectedPaint.setShadowLayer(8f, 0f, 0f, 0x804CAF50);

        validMovePaint = new Paint();
        validMovePaint.setColor(0x804CAF50);
        validMovePaint.setAntiAlias(true);

        captureMovePaint = new Paint();
        captureMovePaint.setColor(Color.parseColor("#F44336"));
        captureMovePaint.setStyle(Paint.Style.STROKE);
        captureMovePaint.setStrokeWidth(3f);
        captureMovePaint.setAntiAlias(true);

        lastMovePaint = new Paint();
        lastMovePaint.setColor(Color.parseColor("#FFD700"));
        lastMovePaint.setStyle(Paint.Style.STROKE);
        lastMovePaint.setStrokeWidth(3f);
        lastMovePaint.setAntiAlias(true);

        hintFromPaint = new Paint();
        hintFromPaint.setColor(Color.parseColor("#2196F3"));
        hintFromPaint.setStyle(Paint.Style.STROKE);
        hintFromPaint.setStrokeWidth(5f);
        hintFromPaint.setAntiAlias(true);

        hintToPaint = new Paint();
        hintToPaint.setColor(Color.parseColor("#2196F3"));
        hintToPaint.setStyle(Paint.Style.STROKE);
        hintToPaint.setStrokeWidth(5f);
        hintToPaint.setAntiAlias(true);

        hintArrowPaint = new Paint();
        hintArrowPaint.setColor(Color.parseColor("#2196F3"));
        hintArrowPaint.setStyle(Paint.Style.STROKE);
        hintArrowPaint.setStrokeWidth(4f);
        hintArrowPaint.setAntiAlias(true);

        riverTextPaint = new Paint();
        riverTextPaint.setColor(Color.parseColor("#5D4037"));
        riverTextPaint.setAntiAlias(true);
        riverTextPaint.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        riverTextPaint.setTextAlign(Paint.Align.CENTER);

        gestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                if (locked || isAnimating) return false;
                int[] pos = pixelToBoard(e.getX(), e.getY());
                if (pos != null && cellClickListener != null) {
                    cellClickListener.onCellClick(pos[0], pos[1]);
                }
                return true;
            }
        });
    }

    /**
     * 绑定游戏逻辑对象。
     * <p>设置此视图关联的游戏状态，视图将根据该对象渲染棋盘。
     *
     * @param game 游戏逻辑对象
     */
    public void bindGame(ChineseChessGame game) {
        this.game = game;
        invalidate();
    }

    /**
     * 设置格子点击监听器。
     *
     * @param listener 点击事件监听器
     */
    public void setOnCellClickListener(OnCellClickListener listener) {
        this.cellClickListener = listener;
    }

    /**
     * 设置锁定状态。
     * <p>锁定后棋盘不响应触摸事件，用于AI思考期间。
     *
     * @param locked true 锁定，false 解锁
     */
    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    /**
     * 设置选中棋子的高亮和合法走法显示。
     *
     * @param x          选中棋子的列坐标
     * @param y          选中棋子的行坐标
     * @param validMoves 合法走法列表，每个元素为 [toX, toY]
     */
    public void setSelected(int x, int y, List<int[]> validMoves) {
        this.selectedX = x;
        this.selectedY = y;
        this.validMoves = validMoves;
        invalidate();
    }

    /**
     * 清除选中状态和高亮。
     */
    public void clearSelected() {
        this.selectedX = -1;
        this.selectedY = -1;
        this.validMoves = null;
        invalidate();
    }

    /**
     * 设置上一步走棋的标记。
     *
     * @param fromX 起始列
     * @param fromY 起始行
     * @param toX   目标列
     * @param toY   目标行
     */
    public void setLastMove(int fromX, int fromY, int toX, int toY) {
        this.lastFromX = fromX;
        this.lastFromY = fromY;
        this.lastToX = toX;
        this.lastToY = toY;
    }

    /**
     * 清除上一步走棋标记。
     */
    public void clearLastMove() {
        this.lastFromX = -1;
        this.lastFromY = -1;
        this.lastToX = -1;
        this.lastToY = -1;
    }

    public void setHintMove(int fromX, int fromY, int toX, int toY) {
        this.hintFromX = fromX;
        this.hintFromY = fromY;
        this.hintToX = toX;
        this.hintToY = toY;
        startHintPulse();
        invalidate();
    }

    public void clearHint() {
        this.hintFromX = -1;
        this.hintFromY = -1;
        this.hintToX = -1;
        this.hintToY = -1;
        stopHintPulse();
        invalidate();
    }

    private void startHintPulse() {
        stopHintPulse();
        hintPulseAnimator = ValueAnimator.ofFloat(0f, 1f);
        hintPulseAnimator.setDuration(1200);
        hintPulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        hintPulseAnimator.setRepeatMode(ValueAnimator.RESTART);
        hintPulseAnimator.addUpdateListener(animation -> {
            hintPulsePhase = (float) animation.getAnimatedValue();
            invalidate();
        });
        hintPulseAnimator.start();
    }

    private void stopHintPulse() {
        if (hintPulseAnimator != null && hintPulseAnimator.isRunning()) {
            hintPulseAnimator.cancel();
        }
        hintPulseAnimator = null;
        hintPulsePhase = 0f;
    }

    /**
     * 测量视图尺寸，计算棋盘布局参数。
     * <p>根据View的可用宽高，计算格子大小（cellSize）和棋盘起点坐标（boardLeft, boardTop），
     * 确保棋盘在View中居中显示。
     *
     * @param widthMeasureSpec  宽度测量规格
     * @param heightMeasureSpec 高度测量规格
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = MeasureSpec.getSize(heightMeasureSpec);

        // 棋盘宽度 = 8个格子（9个交叉点），高度 = 9个格子（10个交叉点）
        float cellW = (w - 2 * boardPadding) / 8f;
        float cellH = (h - 2 * boardPadding) / 9f;
        cellSize = Math.min(cellW, cellH);

        // 居中棋盘
        float boardWidth = cellSize * 8;
        float boardHeight = cellSize * 9;
        boardLeft = (w - boardWidth) / 2f;
        boardTop = (h - boardHeight) / 2f;

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    /**
     * 绘制棋盘和棋子。
     * <p>绘制顺序：
     * <ol>
     *   <li>棋盘背景</li>
     *   <li>网格线和九宫斜线</li>
     *   <li>楚河汉界文字</li>
     *   <li>上一步走棋标记</li>
     *   <li>选中棋子高亮和合法走法标记</li>
     *   <li>所有棋子（动画中的棋子除外）</li>
     *   <li>动画中的棋子（最上层）</li>
     * </ol>
     *
     * @param canvas 画布
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (game == null) return;

        drawBoardBackground(canvas);
        drawGridLines(canvas);
        drawRiverText(canvas);
        drawLastMove(canvas);
        drawSelected(canvas);
        drawHint(canvas);
        drawPieces(canvas);
        drawAnimatingPiece(canvas);
    }

    /**
     * 绘制棋盘背景。
     * <p>使用木纹色填充棋盘区域。
     *
     * @param canvas 画布
     */
    private void drawBoardBackground(Canvas canvas) {
        Paint outerPaint = new Paint();
        outerPaint.setColor(Color.parseColor("#3E2723"));
        outerPaint.setAntiAlias(true);
        canvas.drawRect(0, 0, getWidth(), getHeight(), outerPaint);

        RectF boardRect = new RectF(
                boardLeft - boardPadding, boardTop - boardPadding,
                boardLeft + cellSize * 8 + boardPadding, boardTop + cellSize * 9 + boardPadding);

        LinearGradient gradient = new LinearGradient(
                boardRect.left, boardRect.top, boardRect.left, boardRect.bottom,
                Color.parseColor("#DEB887"), Color.parseColor("#D2A679"),
                Shader.TileMode.CLAMP);
        Paint bgPaint = new Paint();
        bgPaint.setShader(gradient);
        bgPaint.setAntiAlias(true);
        canvas.drawRect(boardRect, bgPaint);
    }

    /**
     * 绘制棋盘网格线。
     * <p>包括：
     * <ul>
     *   <li>9条竖线（上半部和下半部分开，中间为楚河汉界）</li>
     *   <li>10条横线</li>
     *   <li>两个九宫斜线</li>
     *   <li>外边框加粗</li>
     * </ul>
     *
     * @param canvas 画布
     */
    private void drawGridLines(Canvas canvas) {
        // 确保线宽足够清晰
        linePaint.setStrokeWidth(Math.max(2.5f, cellSize * 0.04f));
        thickLinePaint.setStrokeWidth(Math.max(5f, cellSize * 0.07f));
        
        // 横线：10条
        for (int row = 0; row < ChineseChessGame.ROWS; row++) {
            float y = boardTop + row * cellSize;
            canvas.drawLine(boardLeft, y, boardLeft + 8 * cellSize, y, linePaint);
        }

        // 竖线：上半部（行0~4）和下半部（行5~9）分别绘制，中间为楚河汉界
        for (int col = 0; col < ChineseChessGame.COLS; col++) {
            float x = boardLeft + col * cellSize;
            // 上半部竖线
            canvas.drawLine(x, boardTop, x, boardTop + 4 * cellSize, linePaint);
            // 下半部竖线
            canvas.drawLine(x, boardTop + 5 * cellSize, x, boardTop + 9 * cellSize, linePaint);
        }
        // 左右两条边线贯穿全棋盘（楚河汉界区域也需要边线）
        canvas.drawLine(boardLeft, boardTop + 4 * cellSize, boardLeft, boardTop + 5 * cellSize, linePaint);
        canvas.drawLine(boardLeft + 8 * cellSize, boardTop + 4 * cellSize,
                boardLeft + 8 * cellSize, boardTop + 5 * cellSize, linePaint);

        // 九宫斜线：红方九宫（行7~9，列3~5）
        canvas.drawLine(boardLeft + 3 * cellSize, boardTop + 7 * cellSize,
                boardLeft + 5 * cellSize, boardTop + 9 * cellSize, linePaint);
        canvas.drawLine(boardLeft + 5 * cellSize, boardTop + 7 * cellSize,
                boardLeft + 3 * cellSize, boardTop + 9 * cellSize, linePaint);
        // 黑方九宫（行0~2，列3~5）
        canvas.drawLine(boardLeft + 3 * cellSize, boardTop + 0 * cellSize,
                boardLeft + 5 * cellSize, boardTop + 2 * cellSize, linePaint);
        canvas.drawLine(boardLeft + 5 * cellSize, boardTop + 0 * cellSize,
                boardLeft + 3 * cellSize, boardTop + 2 * cellSize, linePaint);

        // 外边框加粗
        canvas.drawRect(boardLeft, boardTop,
                boardLeft + 8 * cellSize, boardTop + 9 * cellSize, thickLinePaint);

        // L形角标装饰
        float cornerLen = cellSize * 0.3f;
        float cornerOffset = cellSize * 0.08f;
        drawCornerMark(canvas, boardLeft - cornerOffset, boardTop - cornerOffset, cornerLen, 1, 1);
        drawCornerMark(canvas, boardLeft + 8 * cellSize + cornerOffset, boardTop - cornerOffset, cornerLen, -1, 1);
        drawCornerMark(canvas, boardLeft - cornerOffset, boardTop + 9 * cellSize + cornerOffset, cornerLen, 1, -1);
        drawCornerMark(canvas, boardLeft + 8 * cellSize + cornerOffset, boardTop + 9 * cellSize + cornerOffset, cornerLen, -1, -1);
    }

    private void drawCornerMark(Canvas canvas, float cx, float cy, float len, float dirX, float dirY) {
        canvas.drawLine(cx, cy, cx + len * dirX, cy, thickLinePaint);
        canvas.drawLine(cx, cy, cx, cy + len * dirY, thickLinePaint);
    }

    /**
     * 绘制楚河汉界文字。
     * <p>在棋盘中间空白区域（行4~5之间）绘制"楚河"和"汉界"。
     *
     * @param canvas 画布
     */
    private void drawRiverText(Canvas canvas) {
        float riverY = boardTop + 4.5f * cellSize;
        riverTextPaint.setTextSize(cellSize * 0.45f);
        canvas.drawText("楚  河", boardLeft + 2 * cellSize, riverY + riverTextPaint.getTextSize() / 3, riverTextPaint);
        canvas.drawText("汉  界", boardLeft + 6 * cellSize, riverY + riverTextPaint.getTextSize() / 3, riverTextPaint);

        Paint wavePaint = new Paint();
        wavePaint.setColor(Color.parseColor("#5D4037"));
        wavePaint.setStrokeWidth(1.5f);
        wavePaint.setStyle(Paint.Style.STROKE);
        wavePaint.setAntiAlias(true);

        float waveAmplitude = cellSize * 0.06f;
        float waveLength = cellSize * 0.4f;
        float riverTop = boardTop + 4 * cellSize;
        float riverBottom = boardTop + 5 * cellSize;

        for (float baseY : new float[]{riverTop + cellSize * 0.3f, riverBottom - cellSize * 0.3f}) {
            android.graphics.Path wavePath = new android.graphics.Path();
            wavePath.moveTo(boardLeft + cellSize * 0.5f, baseY);
            float x = boardLeft + cellSize * 0.5f;
            while (x < boardLeft + 7.5f * cellSize) {
                wavePath.quadTo(x + waveLength * 0.25f, baseY - waveAmplitude,
                        x + waveLength * 0.5f, baseY);
                wavePath.quadTo(x + waveLength * 0.75f, baseY + waveAmplitude,
                        x + waveLength, baseY);
                x += waveLength;
            }
            canvas.drawPath(wavePath, wavePaint);
        }
    }

    /**
     * 绘制上一步走棋的标记。
     * <p>在起始位置和目标位置绘制橙色圆环标记。
     *
     * @param canvas 画布
     */
    private void drawLastMove(Canvas canvas) {
        if (lastFromX < 0 || lastToX < 0) return;
        float radius = cellSize * 0.38f;

        float fromPx = boardLeft + lastFromX * cellSize;
        float fromPy = boardTop + lastFromY * cellSize;
        canvas.drawCircle(fromPx, fromPy, radius, lastMovePaint);

        float toPx = boardLeft + lastToX * cellSize;
        float toPy = boardTop + lastToY * cellSize;
        canvas.drawCircle(toPx, toPy, radius, lastMovePaint);
    }

    /**
     * 绘制选中棋子的高亮和合法走法标记。
     * <p>选中棋子绘制绿色圆环，合法走法目标位置绘制绿色小圆点。
     *
     * @param canvas 画布
     */
    private void drawSelected(Canvas canvas) {
        if (selectedX < 0 || selectedY < 0) return;
        float radius = cellSize * 0.38f;

        float px = boardLeft + selectedX * cellSize;
        float py = boardTop + selectedY * cellSize;
        canvas.drawCircle(px, py, radius + 4, selectedPaint);

        if (validMoves != null) {
            for (int[] move : validMoves) {
                float mx = boardLeft + move[0] * cellSize;
                float my = boardTop + move[1] * cellSize;
                ChineseChessGame.Piece target = game.getBoard()[move[1]][move[0]];
                if (target != null) {
                    canvas.drawCircle(mx, my, radius, captureMovePaint);
                } else {
                    canvas.drawCircle(mx, my, cellSize * 0.12f, validMovePaint);
                }
            }
        }
    }

    private void drawHint(Canvas canvas) {
        if (hintFromX < 0 || hintToX < 0) return;

        float pulse = 0.5f + 0.5f * (float) Math.sin(hintPulsePhase * 2 * Math.PI);
        float radius = cellSize * 0.38f;

        float fromPx = boardLeft + hintFromX * cellSize;
        float fromPy = boardTop + hintFromY * cellSize;
        float toPx = boardLeft + hintToX * cellSize;
        float toPy = boardTop + hintToY * cellSize;

        Paint fromPulsePaint = new Paint(hintFromPaint);
        fromPulsePaint.setAlpha((int) (120 + 135 * pulse));
        fromPulsePaint.setStrokeWidth(4f + 3f * pulse);
        float fromRadius = radius + 6f + 4f * pulse;
        canvas.drawCircle(fromPx, fromPy, fromRadius, fromPulsePaint);

        Paint fromFillPaint = new Paint();
        fromFillPaint.setColor(Color.parseColor("#2196F3"));
        fromFillPaint.setAlpha((int) (30 + 40 * pulse));
        fromFillPaint.setAntiAlias(true);
        canvas.drawCircle(fromPx, fromPy, radius, fromFillPaint);

        Paint toPulsePaint = new Paint(hintToPaint);
        toPulsePaint.setAlpha((int) (120 + 135 * pulse));
        toPulsePaint.setStrokeWidth(4f + 3f * pulse);
        float toRadius = radius + 6f + 4f * pulse;
        canvas.drawCircle(toPx, toPy, toRadius, toPulsePaint);

        Paint toFillPaint = new Paint();
        toFillPaint.setColor(Color.parseColor("#2196F3"));
        toFillPaint.setAlpha((int) (40 + 50 * pulse));
        toFillPaint.setAntiAlias(true);
        canvas.drawCircle(toPx, toPy, radius, toFillPaint);

        float dx = toPx - fromPx;
        float dy = toPy - fromPy;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist > 0) {
            float nx = dx / dist;
            float ny = dy / dist;

            float arrowStartX = fromPx + nx * (radius + 8f);
            float arrowStartY = fromPy + ny * (radius + 8f);
            float arrowEndX = toPx - nx * (radius + 8f);
            float arrowEndY = toPy - ny * (radius + 8f);

            Paint arrowPaint = new Paint(hintArrowPaint);
            arrowPaint.setAlpha((int) (140 + 115 * pulse));
            arrowPaint.setStrokeWidth(3f + 2f * pulse);
            canvas.drawLine(arrowStartX, arrowStartY, arrowEndX, arrowEndY, arrowPaint);

            float arrowHeadLen = cellSize * 0.18f;
            float angle = (float) Math.atan2(dy, dx);
            float a1 = angle + (float) Math.toRadians(150);
            float a2 = angle - (float) Math.toRadians(150);
            canvas.drawLine(arrowEndX, arrowEndY,
                    arrowEndX + arrowHeadLen * (float) Math.cos(a1),
                    arrowEndY + arrowHeadLen * (float) Math.sin(a1), arrowPaint);
            canvas.drawLine(arrowEndX, arrowEndY,
                    arrowEndX + arrowHeadLen * (float) Math.cos(a2),
                    arrowEndY + arrowHeadLen * (float) Math.sin(a2), arrowPaint);
        }
    }

    /**
     * 绘制所有棋子（动画中的棋子除外）。
     * <p>遍历棋盘数组，为每个棋子绘制圆形底色、边框和中文名称。
     * 正在动画中的棋子跳过，由 {@link #drawAnimatingPiece} 单独绘制。
     *
     * @param canvas 画布
     */
    private void drawPieces(Canvas canvas) {
        ChineseChessGame.Piece[][] board = game.getBoard();
        float radius = cellSize * 0.38f;

        for (int y = 0; y < ChineseChessGame.ROWS; y++) {
            for (int x = 0; x < ChineseChessGame.COLS; x++) {
                ChineseChessGame.Piece piece = board[y][x];
                if (piece == null) continue;
                // 跳过正在动画中的棋子
                if (isAnimating && animatingPiece == piece) continue;

                float px = boardLeft + x * cellSize;
                float py = boardTop + y * cellSize;
                drawSinglePiece(canvas, piece, px, py, radius);
            }
        }
    }

    /**
     * 绘制动画中的棋子。
     * <p>使用动画计算出的当前位置（animCurrentX, animCurrentY）绘制棋子，
     * 使棋子看起来从起点滑动到终点。
     *
     * @param canvas 画布
     */
    private void drawAnimatingPiece(Canvas canvas) {
        if (!isAnimating || animatingPiece == null) return;
        float radius = cellSize * 0.38f;
        drawSinglePiece(canvas, animatingPiece, animCurrentX, animCurrentY, radius);
    }

    /**
     * 绘制单个棋子。
     * <p>绘制顺序：圆形底色 → 边框 → 中文名称文字。
     * 红方棋子使用浅红底色+红色文字，黑方棋子使用浅灰底色+黑色文字。
     *
     * @param canvas 画布
     * @param piece  要绘制的棋子
     * @param cx     棋子中心X像素坐标
     * @param cy     棋子中心Y像素坐标
     * @param radius 棋子半径（像素）
     */
    private void drawSinglePiece(Canvas canvas, ChineseChessGame.Piece piece, float cx, float cy, float radius) {
        canvas.drawCircle(cx + 2, cy + 2, radius, pieceShadowPaint);

        Paint bgPaint = piece.side == ChineseChessGame.Side.RED ? redPiecePaint : blackPiecePaint;
        canvas.drawCircle(cx, cy, radius, bgPaint);

        Paint borderPaint = piece.side == ChineseChessGame.Side.RED ? redPieceBorderPaint : blackPieceBorderPaint;
        canvas.drawCircle(cx, cy, radius, borderPaint);

        Paint innerPaint = piece.side == ChineseChessGame.Side.RED ? redInnerPaint : blackInnerPaint;
        canvas.drawCircle(cx, cy, radius * 0.85f, innerPaint);

        Paint textPaint = piece.side == ChineseChessGame.Side.RED ? redTextPaint : blackTextPaint;
        textPaint.setTextSize(radius * 1.2f);
        Rect textBounds = new Rect();
        textPaint.getTextBounds(piece.getName(), 0, piece.getName().length(), textBounds);
        float textY = cy + textBounds.height() / 2f;
        canvas.drawText(piece.getName(), cx, textY, textPaint);
    }

    /**
     * 播放走棋动画。
     * <p>棋子从起始位置滑动到目标位置，使用Overshoot插值器产生弹性效果。
     * 动画期间该棋子从正常绘制中排除，由动画层单独绘制。
     * 动画完成后执行回调。
     *
     * 【初学者提示】Overshoot插值器是什么？
     * 就像弹簧一样，棋子到达目标位置时会稍微"冲过头"再弹回来，
     * 这种弹性效果让走棋看起来更自然有趣，而不是生硬地瞬间移动。
     *
     * @param fromX       起始列
     * @param fromY       起始行
     * @param toX         目标列
     * @param toY         目标行
     * @param onEnd       动画完成后的回调
     */
    public void animateMove(int fromX, int fromY, int toX, int toY, Runnable onEnd) {
        ChineseChessGame.Piece piece = game.getBoard()[fromY][fromX];
        if (piece == null) {
            if (onEnd != null) onEnd.run();
            return;
        }

        animatingPiece = piece;
        isAnimating = true;
        onAnimationEnd = onEnd;

        float startX = boardLeft + fromX * cellSize;
        float startY = boardTop + fromY * cellSize;
        float endX = boardLeft + toX * cellSize;
        float endY = boardTop + toY * cellSize;

        currentAnimator = ValueAnimator.ofFloat(0f, 1f);
        currentAnimator.setDuration(350);
        currentAnimator.setInterpolator(new OvershootInterpolator(1.2f));
        currentAnimator.addUpdateListener(animation -> {
            float fraction = (float) animation.getAnimatedValue();
            animCurrentX = startX + (endX - startX) * fraction;
            animCurrentY = startY + (endY - startY) * fraction;
            invalidate();
        });
        currentAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                isAnimating = false;
                animatingPiece = null;
                invalidate();
                if (onAnimationEnd != null) onAnimationEnd.run();
            }
        });
        currentAnimator.start();
    }

    /**
     * 取消当前正在播放的动画。
     * <p>用于重新开始游戏时，立即终止未完成的走棋动画。
     */
    public void cancelAnimation() {
        if (currentAnimator != null && currentAnimator.isRunning()) {
            currentAnimator.cancel();
        }
        isAnimating = false;
        animatingPiece = null;
    }

    /**
     * 将像素坐标转换为棋盘逻辑坐标。
     * <p>计算触摸点最近的交叉点，若距离在棋子半径内则返回该坐标，
     * 否则返回null（触摸位置不在任何有效交叉点上）。
     *
     * @param px 像素X坐标
     * @param py 像素Y坐标
     * @return 棋盘坐标 [col, row]，若不在有效范围内返回null
     */
    private int[] pixelToBoard(float px, float py) {
        int col = Math.round((px - boardLeft) / cellSize);
        int row = Math.round((py - boardTop) / cellSize);
        if (col >= 0 && col < ChineseChessGame.COLS && row >= 0 && row < ChineseChessGame.ROWS) {
            // 检查触摸点与最近交叉点的距离是否在合理范围内
            float cx = boardLeft + col * cellSize;
            float cy = boardTop + row * cellSize;
            float dist = (float) Math.sqrt((px - cx) * (px - cx) + (py - cy) * (py - cy));
            if (dist <= cellSize * 0.5f) {
                return new int[]{col, row};
            }
        }
        return null;
    }

    /**
     * 处理触摸事件。
     * <p>委托给 {@link GestureDetector} 处理，识别点击手势。
     *
     * @param event 触摸事件
     * @return true 若事件被消费
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        gestureDetector.onTouchEvent(event);
        return true;
    }
}
