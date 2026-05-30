package com.gamecenter.app.games.minesweeper

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration

@SuppressLint("ViewConstructor")
class MineSweeperView(context: Context, private val rows: Int, private val cols: Int, val mineCount: Int) : View(context) {

    enum class CellState { HIDDEN, REVEALED, FLAGGED }
    enum class GameStatus { PLAYING, WON, LOST }

    data class Cell(var isMine: Boolean = false, var adjacentMines: Int = 0, var state: CellState = CellState.HIDDEN)

    val grid = Array(rows) { Array(cols) { Cell() } }
    var gameStatus = GameStatus.PLAYING
    var flagCount = 0
    var revealedCount = 0
    var onGameStatusChanged: ((GameStatus) -> Unit)? = null

    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = Rect()

    private var cellSize = 0f
    private var offsetX = 0f
    private var offsetY = 0f
    private var longPressTimeout = ViewConfiguration.getLongPressTimeout()
    private var touchStartTime = 0L
    private var touchCell: Pair<Int, Int>? = null

    private val numberColors = intArrayOf(
        0xFF1976D2.toInt(),
        0xFF388E3C.toInt(),
        0xFFD32F2F.toInt(),
        0xFF7B1FA2.toInt(),
        0xFFFF8F00.toInt(),
        0xFF00838F.toInt(),
        0xFF424242.toInt(),
        0xFF757575.toInt()
    )

    init {
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textAlign = Paint.Align.CENTER
        placeMines()
        calculateAdjacent()
    }

    private fun placeMines() {
        var placed = 0
        while (placed < mineCount) {
            val r = (0 until rows).random()
            val c = (0 until cols).random()
            if (!grid[r][c].isMine) {
                grid[r][c].isMine = true
                placed++
            }
        }
    }

    private fun calculateAdjacent() {
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (grid[r][c].isMine) continue
                var count = 0
                for (dr in -1..1) {
                    for (dc in -1..1) {
                        if (dr == 0 && dc == 0) continue
                        val nr = r + dr
                        val nc = c + dc
                        if (nr in 0 until rows && nc in 0 until cols && grid[nr][nc].isMine) {
                            count++
                        }
                    }
                }
                grid[r][c].adjacentMines = count
            }
        }
    }

    fun reset() {
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                grid[r][c] = Cell()
            }
        }
        gameStatus = GameStatus.PLAYING
        flagCount = 0
        revealedCount = 0
        placeMines()
        calculateAdjacent()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        cellSize = (minOf(w, h) / maxOf(rows, cols)).toFloat()
        val needW = cellSize * cols
        val needH = cellSize * rows
        offsetX = (w - needW) / 2f
        offsetY = (h - needH) / 2f
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val cell = grid[r][c]
                val left = offsetX + c * cellSize
                val top = offsetY + r * cellSize
                rect.set(left.toInt(), top.toInt(), (left + cellSize).toInt(), (top + cellSize).toInt())

                when (cell.state) {
                    CellState.HIDDEN -> {
                        cellPaint.color = 0xFFB0BEC5.toInt()
                        canvas.drawRect(rect, cellPaint)
                        cellPaint.color = 0xFF90A4AE.toInt()
                        canvas.drawRect(
                            rect.left.toFloat(), rect.top.toFloat(),
                            (rect.right - 1).toFloat(), (rect.bottom - 1).toFloat(), cellPaint
                        )
                    }
                    CellState.REVEALED -> {
                        cellPaint.color = if (cell.isMine) 0xFFFFCDD2.toInt() else 0xFFECEFF1.toInt()
                        canvas.drawRect(rect, cellPaint)
                        cellPaint.color = 0xFFCFD8DC.toInt()
                        canvas.drawLine(rect.left.toFloat(), rect.bottom.toFloat(), rect.right.toFloat(), rect.bottom.toFloat(), cellPaint)
                        canvas.drawLine(rect.right.toFloat(), rect.top.toFloat(), rect.right.toFloat(), rect.bottom.toFloat(), cellPaint)

                        if (cell.isMine) {
                            textPaint.textSize = cellSize * 0.6f
                            textPaint.color = 0xFFD32F2F.toInt()
                            canvas.drawText("*", rect.exactCenterX(), rect.exactCenterY() + textPaint.textSize / 3, textPaint)
                        } else if (cell.adjacentMines > 0) {
                            textPaint.textSize = cellSize * 0.55f
                            textPaint.color = numberColors[minOf(cell.adjacentMines - 1, numberColors.size - 1)]
                            canvas.drawText(cell.adjacentMines.toString(), rect.exactCenterX(), rect.exactCenterY() + textPaint.textSize / 3, textPaint)
                        }
                    }
                    CellState.FLAGGED -> {
                        cellPaint.color = 0xFFB0BEC5.toInt()
                        canvas.drawRect(rect, cellPaint)
                        cellPaint.color = 0xFF90A4AE.toInt()
                        canvas.drawRect(
                            rect.left.toFloat(), rect.top.toFloat(),
                            (rect.right - 1).toFloat(), (rect.bottom - 1).toFloat(), cellPaint
                        )
                        textPaint.textSize = cellSize * 0.55f
                        textPaint.color = 0xFFD32F2F.toInt()
                        canvas.drawText("P", rect.exactCenterX(), rect.exactCenterY() + textPaint.textSize / 3, textPaint)
                    }
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (gameStatus != GameStatus.PLAYING) return false

        val col = ((event.x - offsetX) / cellSize).toInt()
        val row = ((event.y - offsetY) / cellSize).toInt()
        if (row !in 0 until rows || col !in 0 until cols) return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchStartTime = System.currentTimeMillis()
                touchCell = Pair(row, col)
                return true
            }
            MotionEvent.ACTION_UP -> {
                val elapsed = System.currentTimeMillis() - touchStartTime
                val cell = touchCell
                if (cell != null && cell == Pair(row, col)) {
                    if (elapsed > longPressTimeout) {
                        toggleFlag(row, col)
                    } else {
                        reveal(row, col)
                    }
                }
                touchCell = null
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun toggleFlag(r: Int, c: Int) {
        val cell = grid[r][c]
        when (cell.state) {
            CellState.HIDDEN -> {
                cell.state = CellState.FLAGGED
                flagCount++
            }
            CellState.FLAGGED -> {
                cell.state = CellState.HIDDEN
                flagCount--
            }
            CellState.REVEALED -> {}
        }
        invalidate()
    }

    private fun reveal(r: Int, c: Int) {
        val cell = grid[r][c]
        if (cell.state != CellState.HIDDEN) return

        if (cell.isMine) {
            cell.state = CellState.REVEALED
            gameStatus = GameStatus.LOST
            revealAllMines()
            invalidate()
            onGameStatusChanged?.invoke(GameStatus.LOST)
            return
        }

        floodReveal(r, c)
        checkWin()
        invalidate()
    }

    private fun floodReveal(r: Int, c: Int) {
        if (r !in 0 until rows || c !in 0 until cols) return
        val cell = grid[r][c]
        if (cell.state != CellState.HIDDEN || cell.isMine) return

        cell.state = CellState.REVEALED
        revealedCount++

        if (cell.adjacentMines == 0) {
            for (dr in -1..1) {
                for (dc in -1..1) {
                    if (dr == 0 && dc == 0) continue
                    floodReveal(r + dr, c + dc)
                }
            }
        }
    }

    private fun revealAllMines() {
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (grid[r][c].isMine) {
                    grid[r][c].state = CellState.REVEALED
                }
            }
        }
    }

    private fun checkWin() {
        if (revealedCount == rows * cols - mineCount) {
            gameStatus = GameStatus.WON
            onGameStatusChanged?.invoke(GameStatus.WON)
        }
    }
}
