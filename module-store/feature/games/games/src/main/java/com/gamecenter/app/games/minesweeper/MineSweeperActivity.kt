package com.gamecenter.app.games.minesweeper

import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.gamecenter.app.games.R

class MineSweeperActivity : AppCompatActivity() {

    private var gameView: MineSweeperView? = null
    private var statusText: TextView? = null
    private var container: LinearLayout? = null
    private var currentRows = 9
    private var currentCols = 9
    private var currentMines = 10

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        currentRows = intent.getIntExtra("rows", 9)
        currentCols = intent.getIntExtra("cols", 9)
        currentMines = intent.getIntExtra("mines", 10)

        title = getString(R.string.minesweeper_title)

        val status = TextView(this)
        status.text = formatStatus()
        status.textSize = 18f
        status.gravity = Gravity.CENTER
        status.setPadding(0, 24, 0, 8)
        statusText = status

        val view = MineSweeperView(this, currentRows, currentCols, currentMines)
        gameView = view

        val easyBtn = Button(this)
        easyBtn.text = getString(R.string.minesweeper_easy)
        easyBtn.setOnClickListener { restartGame(9, 9, 10) }

        val mediumBtn = Button(this)
        mediumBtn.text = getString(R.string.minesweeper_medium)
        mediumBtn.setOnClickListener { restartGame(16, 16, 40) }

        val hardBtn = Button(this)
        hardBtn.text = getString(R.string.minesweeper_hard)
        hardBtn.setOnClickListener { restartGame(16, 30, 99) }

        val difficultyLayout = LinearLayout(this)
        difficultyLayout.orientation = LinearLayout.HORIZONTAL
        difficultyLayout.gravity = Gravity.CENTER
        val btnParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
        btnParams.setMargins(4, 4, 4, 4)
        difficultyLayout.addView(easyBtn, btnParams)
        difficultyLayout.addView(mediumBtn, btnParams)
        difficultyLayout.addView(hardBtn, btnParams)

        val resetBtn = Button(this)
        resetBtn.text = getString(R.string.minesweeper_reset)
        resetBtn.setOnClickListener {
            gameView?.reset()
            updateStatus()
        }

        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        container.gravity = Gravity.CENTER_HORIZONTAL
        container.setPadding(16, 0, 16, 16)
        container.addView(status)
        container.addView(difficultyLayout, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        container.addView(view, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1.0f
        ))
        container.addView(resetBtn)

        this.container = container
        setContentView(container)

        view.onGameStatusChanged = { ms ->
            runOnUiThread {
                updateStatus()
                when (ms) {
                    MineSweeperView.GameStatus.WON -> {
                        AlertDialog.Builder(this)
                            .setTitle(getString(R.string.minesweeper_win_title))
                            .setMessage(getString(R.string.minesweeper_win_msg))
                            .setPositiveButton(getString(R.string.minesweeper_play_again)) { _, _ ->
                                gameView?.reset()
                                updateStatus()
                            }
                            .setCancelable(false)
                            .show()
                    }
                    MineSweeperView.GameStatus.LOST -> {
                        AlertDialog.Builder(this)
                            .setTitle(getString(R.string.minesweeper_lose_title))
                            .setMessage(getString(R.string.minesweeper_lose_msg))
                            .setPositiveButton(getString(R.string.minesweeper_play_again)) { _, _ ->
                                gameView?.reset()
                                updateStatus()
                            }
                            .setCancelable(false)
                            .show()
                    }
                    MineSweeperView.GameStatus.PLAYING -> {}
                }
            }
        }
    }

    private fun restartGame(rows: Int, cols: Int, mines: Int) {
        currentRows = rows
        currentCols = cols
        currentMines = mines
        val newView = MineSweeperView(this, rows, cols, mines)
        gameView = newView

        val c = container ?: return
        c.removeViewAt(2)
        c.addView(newView, 2, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1.0f
        ))

        newView.onGameStatusChanged = { ms ->
            runOnUiThread {
                updateStatus()
                when (ms) {
                    MineSweeperView.GameStatus.WON -> {
                        AlertDialog.Builder(this)
                            .setTitle(getString(R.string.minesweeper_win_title))
                            .setMessage(getString(R.string.minesweeper_win_msg))
                            .setPositiveButton(getString(R.string.minesweeper_play_again)) { _, _ ->
                                gameView?.reset()
                                updateStatus()
                            }
                            .setCancelable(false)
                            .show()
                    }
                    MineSweeperView.GameStatus.LOST -> {
                        AlertDialog.Builder(this)
                            .setTitle(getString(R.string.minesweeper_lose_title))
                            .setMessage(getString(R.string.minesweeper_lose_msg))
                            .setPositiveButton(getString(R.string.minesweeper_play_again)) { _, _ ->
                                gameView?.reset()
                                updateStatus()
                            }
                            .setCancelable(false)
                            .show()
                    }
                    MineSweeperView.GameStatus.PLAYING -> {}
                }
            }
        }
        updateStatus()
    }

    private fun updateStatus() {
        statusText?.text = formatStatus()
    }

    private fun formatStatus(): String {
        val g = gameView ?: return ""
        val remaining = g.mineCount - g.flagCount
        return getString(R.string.minesweeper_status, remaining, g.flagCount)
    }
}
