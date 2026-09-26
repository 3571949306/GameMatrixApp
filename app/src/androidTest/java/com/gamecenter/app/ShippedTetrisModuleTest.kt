package com.gamecenter.app

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.gamecenter.app.core.common.ModuleManifest
import com.gamecenter.app.core.common.ModuleScopedPreferences
import com.gamecenter.app.core.security.ModuleSignatureVerifier
import com.gamecenter.app.core.security.ModuleVerifier
import com.gamecenter.app.database.AppDatabase
import com.gamecenter.app.database.entity.GameUsageEntity
import com.gamecenter.app.modules.ModuleDownloader
import com.gamecenter.app.modules.ModuleLoader
import com.gamecenter.app.modules.ModuleManager
import com.gamecenter.app.modules.store.TransactionInstaller
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real dynamically shipped Tetris: production canvas controls, floating pause Button and
 * actual confirmation-dialog Buttons receive system UiAutomation touchscreen events.
 * Gameplay starts naturally without replacing a board, queue or seed, except that the
 * explicitly named legal-restored-fixture tests call public restoreSnapshot
 * with a reviewed legal saved position and NEXT queue, then uses real touchscreen input.
 * Reflection never writes private state; it reads public state getters and the actual
 * control rectangles produced by a committed Window frame. PixelCopy records those real
 * Window pixels; no synthetic View.draw is used.
 * The explicit normal-difficulty preference fixture is restored in finally together with
 * all known/existing flat and scoped Tetris preferences, legacy saves and the Tetris Room row.
 * Caller owns device selection. This test does not choose or connect to an emulator.
 */
@RunWith(AndroidJUnit4::class)
class ShippedTetrisModuleTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun shippedTetrisPackagePassesIntegrityAndPinnedSignature() {
        withShippedPackage { manifest, apk -> verifyPackage(manifest, apk) }
    }

    @Test
    fun shippedTetrisRealHoldAndHardDropAllowOnlyOneHoldPerPiece() {
        withInstalledGame { scenario ->
            val initial = readState(scenario)
            assertEquals(2, initial.difficulty)
            assertEquals(-1, initial.hold)
            assertFalse(initial.holdUsed)
            assertEquals(0, initial.grid.count { it != 0 })
            assertEquals(5, initial.next.size)
            assertFalse(initial.paused || initial.gameOver)

            touchControl(scenario, HOLD)
            val firstHold = readState(scenario)
            assertEquals("First HOLD must store the naturally generated current piece", initial.piece, firstHold.hold)
            assertEquals("Empty HOLD must use the first real NEXT piece", initial.next.first(), firstHold.piece)
            assertEquals(initial.next.drop(1), firstHold.next.take(4))
            assertEquals(initial.grid, firstHold.grid)
            assertEquals(initial.score, firstHold.score)
            assertTrue("Filling an empty HOLD slot must consume this piece's one HOLD", firstHold.holdUsed)
            touchControl(scenario, HOLD)
            assertSameRoundApartFromNaturalFall("A second HOLD before locking must do nothing", firstHold, readState(scenario))
            captureScreen("tetris-empty-slot-hold-used-visible", scenario, ::boardView)

            touchControl(scenario, DROP)
            val locked = readState(scenario)
            assertEquals("A real hard drop must lock exactly four cells", 4, locked.grid.count { it != 0 })
            assertTrue("Real hard drop must earn positive score", locked.score > firstHold.score)
            assertEquals(firstHold.hold, locked.hold)
            assertEquals(firstHold.next.first(), locked.piece)
            assertFalse("Only locking a piece makes HOLD available again", locked.holdUsed)

            touchControl(scenario, HOLD)
            val occupiedHold = readState(scenario)
            assertEquals("Occupied HOLD must swap the stored piece into play", locked.hold, occupiedHold.piece)
            assertEquals(locked.piece, occupiedHold.hold)
            assertEquals("Occupied HOLD must not consume NEXT", locked.next, occupiedHold.next)
            assertEquals(locked.grid, occupiedHold.grid)
            assertEquals(locked.score, occupiedHold.score)
            assertTrue("An occupied-slot swap must also consume this piece's HOLD", occupiedHold.holdUsed)
            touchControl(scenario, HOLD)
            assertSameRoundApartFromNaturalFall("A second occupied-slot HOLD must do nothing", occupiedHold, readState(scenario))
            captureScreen("tetris-occupied-slot-hold-used-visible", scenario, ::boardView)
        }
    }

    @Test
    fun shippedTetrisRealDifficultyCancelPreservesRoundAndConfirmStartsSelectedRound() {
        withInstalledGame { scenario ->
            touchControl(scenario, DROP)
            touchPause(scenario)
            val before = readState(scenario)
            assertEquals("One real hard drop must precede the confirmation", 4, before.grid.count { it != 0 })
            assertTrue(before.score > 0)
            assertTrue("Use the real pause control to freeze gravity during exact round assertions", before.paused)
            val colorsBefore = difficultyColors(scenario)
            val prefsBefore = preferenceValues(tetrisPreferences())
            captureScreen("tetris-normal-paused-progress", scenario, ::boardView)

            touchDifficulty(scenario, 4)
            // Cancel first: verify the end-user cancellation path independently of any
            // assumptions about temporary visuals while the dialog is open.
            touchDialogButton(android.R.id.button2)
            assertEquals("Cancelling master difficulty must retain the complete paused normal round", before, readState(scenario))
            assertEquals("Cancelling must retain every original preference value", prefsBefore, preferenceValues(tetrisPreferences()))
            assertEquals("Cancelling must keep the same selected difficulty style", colorsBefore, difficultyColors(scenario))
            captureScreen("tetris-cancel-keeps-normal-round", scenario, ::boardView)

            touchDifficulty(scenario, 4)
            awaitDialogButton(android.R.id.button1)
            assertEquals("Opening a confirmation must not alter the live paused round", before, readState(scenario))
            assertEquals("Opening a confirmation must not persist unconfirmed difficulty", prefsBefore, preferenceValues(tetrisPreferences()))
            assertEquals(colorsBefore, difficultyColors(scenario))
            touchDialogButton(android.R.id.button1)
            val restarted = readState(scenario)
            assertEquals(4, restarted.difficulty)
            assertEquals(4, tetrisPreferences().getInt("last_difficulty", -1))
            assertEquals("Confirmation must clear the previous locked piece", 0, restarted.grid.count { it != 0 })
            // Master gravity may tick while UiAutomator waits for the dialog to vanish.
            // Passive movement may change its row, but must never award drop points.
            assertTrue("The fresh piece must still be near its spawn row", restarted.y in 0..4)
            assertEquals("A fresh round must stay at zero points during passive gravity", 0, restarted.score)
            assertEquals(0, restarted.lines)
            assertEquals(1, restarted.level)
            assertEquals(-1, restarted.hold)
            assertFalse(restarted.holdUsed || restarted.paused || restarted.gameOver)
            assertTrue(restarted.piece in 0..6)
            assertEquals(5, restarted.next.size)
            val colorsAfter = difficultyColors(scenario)
            assertEquals("Master must take the former active normal style", colorsBefore[1], colorsAfter[3])
            assertEquals("Normal must take the former inactive master style", colorsBefore[3], colorsAfter[1])
            captureScreen("tetris-confirm-master-fresh-round", scenario, ::boardView)
        }
    }

    @Test
    fun shippedTetrisActiveConfirmationFreezesGravityAndCancelResumesNormalRound() {
        withInstalledGame { scenario ->
            val initial = readState(scenario)
            assertEquals(2, initial.difficulty)
            assertEquals("A naturally started round has no input-earned points", 0, initial.score)
            assertFalse("Start from the actual active round, without pressing pause", initial.paused || initial.gameOver)
            val prefsBefore = preferenceValues(tetrisPreferences())
            val colorsBefore = difficultyColors(scenario)

            touchDifficulty(scenario, 4)
            awaitDialogButton(android.R.id.button2)
            val opened = readState(scenario)
            val observationStart = SystemClock.uptimeMillis()
            // A normal level-one piece falls every 1,000 ms. This real elapsed window
            // exceeds that interval; no virtual clock or engine state is advanced.
            SystemClock.sleep(1_600L)
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            val afterWait = readState(scenario)
            val observedMillis = SystemClock.uptimeMillis() - observationStart
            Log.i(TAG, "Active confirmation observation ${observedMillis}ms: " +
                "beforeY=${opened.y} afterY=${afterWait.y} beforeScore=${opened.score} " +
                "afterScore=${afterWait.score} paused=${afterWait.paused}")
            captureScreen("tetris-active-confirmation-gravity-frozen", scenario, ::boardView)
            assertTrue("Observe the actual dialog for at least 1,500 ms", observedMillis >= 1_500L)
            assertEquals("An open confirmation must freeze the complete actual round", opened, afterWait)
            assertTrue("The real active round must be paused while confirmation is open", afterWait.paused)
            assertEquals("An unconfirmed master request must retain normal difficulty", 2, afterWait.difficulty)
            assertEquals(prefsBefore, preferenceValues(tetrisPreferences()))
            assertEquals(colorsBefore, difficultyColors(scenario))

            touchDialogButton(android.R.id.button2)
            val resumed = readState(scenario)
            assertFalse("The real negative button must resume the previously active round", resumed.paused || resumed.gameOver)
            assertEquals(2, resumed.difficulty)
            assertTrue(resumed.y >= afterWait.y)
            assertEquals("Natural gravity after dismissal must not award points", afterWait.score, resumed.score)
            assertEquals("Cancellation must preserve the actual piece, queue, board and progress",
                afterWait.copy(paused = false, y = resumed.y), resumed)

            val resumeDeadline = SystemClock.uptimeMillis() + 2_500L
            var fallen = resumed
            while (fallen.y == resumed.y && SystemClock.uptimeMillis() < resumeDeadline) {
                SystemClock.sleep(50L)
                fallen = readState(scenario)
            }
            assertTrue("Gravity must actually move the same piece after cancelling the dialog", fallen.y > resumed.y)
            assertEquals("At least one real automatic fall must leave score at zero", 0, fallen.score)
            assertEquals("Resuming must preserve everything except the natural fall",
                resumed.copy(y = fallen.y), fallen)
            assertEquals(prefsBefore, preferenceValues(tetrisPreferences()))
            assertEquals(colorsBefore, difficultyColors(scenario))
            Log.i(TAG, "Real cancellation resumed normal gravity: y=${resumed.y}->${fallen.y}, " +
                "score=${resumed.score}->${fallen.score}, difficulty=${fallen.difficulty}")
            captureScreen("tetris-cancel-active-dialog-gravity-resumed", scenario, ::boardView)
        }
    }

    @Test
    fun shippedTetrisPauseOverlayResumeSurvivesRealActivityPauseAndResume() {
        withInstalledGame { scenario ->
            val prefsBefore = preferenceValues(tetrisPreferences())
            assertFalse(readState(scenario).paused)
            touchPause(scenario)
            val manuallyPaused = readState(scenario)
            assertTrue("The real pause button must pause the current round", manuallyPaused.paused)
            captureScreen("tetris-manual-pause-before-overlay-resume", scenario, ::boardView)

            touchPausedOverlay(scenario)
            val overlayResumed = readState(scenario)
            assertFalse("Tapping the actual paused board overlay must resume gameplay", overlayResumed.paused)
            assertEquals("Overlay resume must preserve the live round without rotating the piece",
                manuallyPaused.copy(paused = false, y = overlayResumed.y), overlayResumed)

            // Actual Activity lifecycle callbacks, not reflective calls into the Fragment.
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            captureScreen("tetris-overlay-resume-after-activity-return", scenario, ::boardView)
            val returned = readState(scenario)
            assertFalse("Returning to the Activity must not reinstate an obsolete manual-pause flag", returned.paused)
            assertEquals("Activity pause/resume must preserve the same round",
                overlayResumed.copy(y = returned.y), returned)
            observeResumedGravity(scenario, returned, "overlay-resume-after-lifecycle")
            assertEquals(prefsBefore, preferenceValues(tetrisPreferences()))
            captureScreen("tetris-overlay-resume-lifecycle-gravity-running", scenario, ::boardView)
        }
    }

    @Test
    fun shippedTetrisSettingsAndRulesFreezeRoundThenRestoreItsPriorPauseState() {
        withInstalledGame { scenario ->
            val prefsBefore = preferenceValues(tetrisPreferences())
            val colorsBefore = difficultyColors(scenario)
            assertFalse("Enter settings from an actual active normal round", readState(scenario).paused)

            touchSettings(scenario)
            awaitDialogButton(android.R.id.button2)
            val settingsFrozen = assertDialogFreezesRound(scenario, "tetris-active-settings-frozen")
            touchVisibleDialogText(context.getString(R.string.tetris_rules_title))
            awaitDialogButton(android.R.id.button1)
            val rulesFrozen = assertDialogFreezesRound(scenario, "tetris-active-rules-frozen")
            assertEquals("Opening rules must preserve the settings dialog's frozen round", settingsFrozen, rulesFrozen)
            touchDialogButton(android.R.id.button1)
            val resumed = readState(scenario)
            assertFalse("Closing rules must resume the round that was active before settings", resumed.paused)
            assertEquals("Closing rules must keep the existing round",
                rulesFrozen.copy(paused = false, y = resumed.y), resumed)
            observeResumedGravity(scenario, resumed, "settings-rules-active-dismissal")

            // A distinct entry that was already manually paused must stay paused after
            // the settings-to-rules transition and an actual system Back dismissal.
            touchPause(scenario)
            val manuallyPaused = readState(scenario)
            assertTrue(manuallyPaused.paused)
            touchSettings(scenario)
            awaitDialogButton(android.R.id.button2)
            assertEquals(manuallyPaused, assertDialogFreezesRound(scenario, "tetris-paused-settings-frozen"))
            touchVisibleDialogText(context.getString(R.string.tetris_rules_title))
            awaitDialogButton(android.R.id.button1)
            assertEquals(manuallyPaused, assertDialogFreezesRound(scenario, "tetris-paused-rules-frozen"))
            dismissDialogThroughSystemBack(android.R.id.button1)
            assertEquals("Back from rules must preserve the pre-existing manual pause and full round",
                manuallyPaused, readState(scenario))
            assertEquals(prefsBefore, preferenceValues(tetrisPreferences()))
            assertEquals(colorsBefore, difficultyColors(scenario))
            captureScreen("tetris-rules-back-retains-manual-pause", scenario, ::boardView)
        }
    }

    @Test
    fun shippedTetrisLegalRestoredFixtureClearsOneLinePreservingEveryOtherCell() {
        withInstalledGame { scenario ->
            // Explicit saved-position fixture, NOT evidence of reaching this layout
            // through natural play. The I piece occupies column 4 and can legally
            // descend 16 rows into the only bottom-row gap without touching anchors.
            val savedGrid = Array(20) { IntArray(10) }
            savedGrid[19].fill(7)
            savedGrid[19][4] = 0
            savedGrid[3][1] = 5
            savedGrid[10][8] = 2
            savedGrid[18][0] = 4
            val savedNext = intArrayOf(1, 2, 3, 4, 5) // O, T, L, J, S.
            fun restoreLegalFixture() {
                scenario.onActivity { activity ->
                    val board = boardView(activity)
                    val restore = board.javaClass.getMethod("restoreSnapshot",
                        Array<IntArray>::class.java,
                        Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                        IntArray::class.java,
                        Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                        Boolean::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                        Boolean::class.javaPrimitiveType)
                    assertEquals("The public saved-game API must accept this legal fixture", true,
                        restore.invoke(board, savedGrid.map { it.clone() }.toTypedArray(),
                            0, 1, 2, 0, savedNext.clone(), -1, 0, 0, 1, false, 0, false))
                    val restored = state(board)
                    assertEquals(savedGrid.flatMap { it.toList() }, restored.grid)
                    assertEquals(savedNext.toList(), restored.next)
                    assertEquals(0, restored.piece)
                    assertEquals(1, restored.rotation)
                    assertEquals(2, restored.x)
                    assertEquals(0, restored.y)
                    assertEquals(0, restored.score)
                    assertFalse(restored.paused || restored.gameOver)
                }
            }
            Log.i(TAG, "EXPLICIT LEGAL SAVED-POSITION FIXTURE: public restoreSnapshot; " +
                "vertical I, bottom gap column 4, three colored anchors; not natural-play completion")
            restoreLegalFixture()
            captureScreen("tetris-legal-restored-fixture-vertical-ghost-before-clear", scenario, ::boardView)
            // Replaying exactly the same public save immediately before input keeps
            // PNG encoding time from altering the fixture's expected hard-drop distance.
            // The saved board and control geometry are unchanged, and no private state
            // or synthetic draw is used by either replay.
            restoreLegalFixture()
            touchControl(scenario, DROP)
            val scored = readState(scenario)
            assertEquals("One actual DROP must score 16x2 hard-drop points plus a 100-point Single, without false Perfect Clear",
                132, scored.score)
            assertEquals("The actual lock must record exactly one cleared line", 1, scored.lines)

            // Observe the production frame-driven animation via the public current
            // piece. No animation callback, virtual clock or private method is invoked.
            val animationDeadline = SystemClock.uptimeMillis() + 3_000L
            var animated = scored
            while (animated.piece != 1 && SystemClock.uptimeMillis() < animationDeadline) {
                SystemClock.sleep(20L)
                animated = readState(scenario)
            }
            assertEquals("The actual clear animation must finish by spawning the queued O piece", 1, animated.piece)
            captureScreen("tetris-legal-restored-fixture-after-clear-animation", scenario, ::boardView)
            val completed = readState(scenario)

            // Independent full-board expectation: delete only row 19 and move every
            // previous row down one. Three I cells remain after its fourth cell clears.
            val expectedGrid = Array(20) { IntArray(10) }
            expectedGrid[4][1] = 5
            expectedGrid[11][8] = 2
            expectedGrid[19][0] = 4
            for (row in 17..19) expectedGrid[row][4] = 1
            assertEquals("Single clear must preserve all 200 expected cells, including anchor colors and remaining I cells",
                expectedGrid.flatMap { it.toList() }, completed.grid)
            assertEquals(6, completed.grid.count { it != 0 })
            assertEquals(1, completed.piece)
            assertEquals(0, completed.rotation)
            assertEquals("NEXT must be consumed exactly once after the animation", listOf(2, 3, 4, 5), completed.next.take(4))
            assertEquals(5, completed.next.size)
            assertEquals(1, completed.lines)
            assertEquals(1, completed.level)
            assertEquals(1, completed.combo)
            assertFalse(completed.backToBack || completed.holdUsed || completed.paused || completed.gameOver)
            assertEquals(-1, completed.hold)
            assertTrue("The queued piece must still be near spawn", completed.y in 0..4)
            assertEquals("The next piece's natural gravity must preserve the exact Single score",
                132, completed.score)
            Log.i(TAG, "Legal saved-fixture actual animation PASS: initialScore=132, lines=1, " +
                "six exact colored cells preserved, O spawned once, NEXT=${completed.next}, score=${completed.score}")
        }
    }

    @Test
    fun shippedTetrisLegalRestoredFixtureAwardsSingleLinePerfectClear() {
        withInstalledGame { scenario ->
            // Explicit legal saved-position fixture, not a naturally played full game.
            // Rotation-zero I has four occupied cells at local row 1, columns 0..3.
            // With x=3 those cells fill the sole bottom-row opening at columns 3..6.
            val savedGrid = Array(20) { IntArray(10) }
            savedGrid[19].fill(7)
            for (column in 3..6) savedGrid[19][column] = 0
            val savedNext = intArrayOf(1, 2, 3, 4, 5)
            val occupiedOffsets = (0..3).map { column -> 1 to column }
            var independentLandingY = 0
            while (occupiedOffsets.all { (row, column) ->
                val candidateRow = independentLandingY + 1 + row
                candidateRow in 0..19 && savedGrid[candidateRow][3 + column] == 0
            }) {
                independentLandingY++
            }
            assertEquals("Independent horizontal-I geometry must yield exactly 18 drop rows", 18, independentLandingY)
            val expectedScore = independentLandingY * 2 + 100 + 800
            assertEquals(936, expectedScore)

            fun restoreLegalFixture() {
                scenario.onActivity { activity ->
                    val board = boardView(activity)
                    val restore = board.javaClass.getMethod("restoreSnapshot",
                        Array<IntArray>::class.java,
                        Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                        IntArray::class.java,
                        Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                        Boolean::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                        Boolean::class.javaPrimitiveType)
                    assertEquals("Public restoreSnapshot must accept the legal Perfect Clear fixture", true,
                        restore.invoke(board, savedGrid.map { it.clone() }.toTypedArray(),
                            0, 0, 3, 0, savedNext.clone(), -1, 0, 0, 1, false, 0, false))
                    val restored = state(board)
                    assertEquals(savedGrid.flatMap { it.toList() }, restored.grid)
                    assertEquals(savedNext.toList(), restored.next)
                    assertEquals(0, restored.piece)
                    assertEquals(0, restored.rotation)
                    assertEquals(3, restored.x)
                    assertEquals(0, restored.y)
                    assertEquals(0, restored.score)
                    assertEquals(0, restored.lines)
                    assertFalse(restored.paused || restored.gameOver)
                }
            }
            Log.i(TAG, "EXPLICIT LEGAL PERFECT-CLEAR SAVED FIXTURE: public restoreSnapshot; " +
                "horizontal I, bottom gap columns 3..6, independent drop distance=$independentLandingY; " +
                "not natural-play completion")
            restoreLegalFixture()
            captureScreen("tetris-legal-restored-perfect-clear-before-drop", scenario, ::boardView)
            // Replay the same public save after image encoding so timing cannot change
            // the exact 18-row manual hard drop used for this scoring assertion.
            restoreLegalFixture()
            touchControl(scenario, DROP)
            val scored = readState(scenario)
            assertEquals("Actual hard drop must award exactly 36 drop + 100 Single + 800 Perfect Clear points",
                expectedScore, scored.score)
            assertEquals(1, scored.lines)

            val animationDeadline = SystemClock.uptimeMillis() + 3_000L
            var animated = scored
            while (animated.piece != 1 && SystemClock.uptimeMillis() < animationDeadline) {
                SystemClock.sleep(20L)
                animated = readState(scenario)
            }
            assertEquals("The real Perfect Clear animation must finish by spawning O", 1, animated.piece)
            captureScreen("tetris-legal-restored-perfect-clear-after-animation", scenario, ::boardView)
            val completed = readState(scenario)
            assertEquals("The actual animation must leave all 200 locked-board cells empty",
                List(200) { 0 }, completed.grid)
            assertEquals("NEXT must advance exactly once after Perfect Clear", listOf(2, 3, 4, 5), completed.next.take(4))
            assertEquals(5, completed.next.size)
            assertEquals(1, completed.piece)
            assertEquals(0, completed.rotation)
            assertEquals(1, completed.lines)
            assertEquals(1, completed.level)
            assertEquals(1, completed.combo)
            assertEquals(-1, completed.hold)
            assertFalse(completed.backToBack || completed.holdUsed || completed.paused || completed.gameOver)
            assertEquals("Perfect Clear and its animation must award exactly once; passive gravity adds nothing",
                expectedScore, completed.score)
            Log.i(TAG, "Legal Perfect Clear fixture PASS: dropRows=18, score=936, lines=1, " +
                "all 200 locked cells empty, O spawned once, NEXT=${completed.next}")
        }
    }

    @Test
    fun shippedTetrisLegalRestoredFixtureClockwiseFloorKickUsesFirstValidLocalCandidate() {
        withInstalledGame { scenario ->
            // Explicit legal saved position: the spawn-orientation T at x7,y18
            // occupies (18,8), (19,7), (19,8), (19,9) on an otherwise empty board.
            // It touches both the floor and right wall. No natural-play claim is made.
            val savedGrid = Array(20) { IntArray(10) }
            val savedNext = intArrayOf(1, 0, 3, 4, 5)
            val rotatedOffsets = listOf(0 to 1, 1 to 1, 1 to 2, 2 to 1)
            val localCandidates = listOf(0 to 0, -1 to 0, -1 to 1, 0 to -2, -1 to -2)
            val firstValid = localCandidates.first { (dx, dy) ->
                rotatedOffsets.all { (row, column) ->
                    val x = 7 + dx + column
                    val y = 18 + dy + row
                    x in 0..9 && y in 0..19 && savedGrid[y][x] == 0
                }
            }
            assertEquals("Independent geometry must select the first legal candidate from the local 0-to-1 table",
                0 to -2, firstValid)

            fun restoreLegalFixture(): RoundState {
                lateinit var restored: RoundState
                scenario.onActivity { activity ->
                    val board = boardView(activity)
                    val restore = board.javaClass.getMethod("restoreSnapshot",
                        Array<IntArray>::class.java,
                        Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                        IntArray::class.java,
                        Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                        Boolean::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                        Boolean::class.javaPrimitiveType)
                    assertEquals("Public restoreSnapshot must accept the legal floor/right-wall T fixture", true,
                        restore.invoke(board, savedGrid.map { it.clone() }.toTypedArray(),
                            2, 0, 7, 18, savedNext.clone(), -1, 0, 0, 1, false, 0, false))
                    restored = state(board)
                    assertEquals(List(200) { 0 }, restored.grid)
                    assertEquals(savedNext.toList(), restored.next)
                    assertEquals(2, restored.piece)
                    assertEquals(0, restored.rotation)
                    assertEquals(7, restored.x)
                    assertEquals(18, restored.y)
                    assertEquals(0, restored.score)
                    assertFalse(restored.paused || restored.gameOver)
                }
                return restored
            }
            Log.i(TAG, "EXPLICIT LEGAL ROTATION SAVED FIXTURE: T rotation0 x7 y18, empty locked board; " +
                "independent first valid local clockwise kick=(0,-2); not natural-play setup")
            restoreLegalFixture()
            captureScreen("tetris-legal-restored-clockwise-floor-kick-before", scenario, ::boardView)
            // Reset the same public save immediately before input so image encoding
            // cannot let the already grounded piece lock on its next gravity tick.
            val before = restoreLegalFixture()
            touchControl(scenario, ROTATE)
            val rotated = readState(scenario)
            // Save the old package's resulting position before the strict assertion.
            captureScreen("tetris-legal-restored-clockwise-floor-kick-after", scenario, ::boardView)
            assertEquals("Actual clockwise control must use (0,-2), preserve x7, and leave board/score/NEXT/HOLD unchanged",
                before.copy(rotation = 1, x = 7, y = 16), rotated)
            Log.i(TAG, "Actual clockwise floor kick PASS: T rotation1 x7 y16, score0, empty locked board, NEXT unchanged")
        }
    }

    @Test
    fun shippedTetrisDifficultyButtonsAreUnclippedAndAtLeast48Dp() {
        withInstalledGame { scenario ->
            scenario.onActivity { activity ->
                val buttons = difficultyButtons(gameFragment(activity))
                assertEquals(listOf("简单", "普通", "困难", "大师"), buttons.map { it.text.toString() })
                for (button in buttons) {
                    fullyVisibleScreenBounds(button)
                    val minimum = kotlin.math.ceil(48.0 * button.resources.displayMetrics.density).toInt()
                    assertTrue("${button.text} touch target ${button.width}x${button.height}px must be at least ${minimum}px (48dp)",
                        button.width >= minimum && button.height >= minimum)
                    val layout = requireNotNull(button.layout)
                    assertEquals("Difficulty label must fit on one visible line", 1, layout.lineCount)
                    assertEquals("Difficulty label must not be ellipsized", 0, layout.getEllipsisCount(0))
                    assertTrue("Difficulty label must fit its available content width", layout.getLineWidth(0) <=
                        button.width - button.compoundPaddingLeft - button.compoundPaddingRight + 1)
                    assertTrue("Difficulty label must fit its available content height", layout.height <=
                        button.height - button.compoundPaddingTop - button.compoundPaddingBottom)
                }
            }
            captureScreen("tetris-four-difficulties-visible", scenario, ::boardView)
        }
    }

    @Test
    fun shippedTetrisCanvasControlsDoNotOverlapFloatingButtons() {
        withInstalledGame { scenario ->
            // Save the actual committed Window before asserting geometry, so the old
            // package leaves visual evidence even when its overlapping targets fail.
            captureScreen("tetris-canvas-controls-and-floating-buttons", scenario, ::boardView)
            scenario.onActivity { activity ->
                val board = boardView(activity)
                val boardBounds = fullyVisibleScreenBounds(board)
                val rectangles = boardField(board, "ctrlBtnRects") as Array<*>
                val labels = listOf("HOLD", "LEFT", "ROTATE", "RIGHT", "DROP")
                assertEquals(labels.size, rectangles.size)
                val overlays = descendants(gameFragment(activity).requireView())
                    .filterIsInstance<ImageButton>().filter { it.isShown }.toList()
                assertEquals("Check all real pause, restart and settings floating controls", 3, overlays.size)
                val conflicts = mutableListOf<String>()
                val overlayRectangles = overlays.mapIndexed { index, overlay ->
                    val minimum = kotlin.math.ceil(48.0 * overlay.resources.displayMetrics.density).toInt()
                    assertTrue("Floating control[$index] ${overlay.width}x${overlay.height}px must be at least ${minimum}px (48dp)",
                        overlay.width >= minimum && overlay.height >= minimum)
                    RectF(fullyVisibleScreenBounds(overlay))
                }
                for (first in overlayRectangles.indices) {
                    for (second in first + 1 until overlayRectangles.size) {
                        val intersection = RectF(overlayRectangles[first])
                        if (intersection.intersect(overlayRectangles[second])) {
                            conflicts += "floating[$first] ${overlayRectangles[first]} intersects " +
                                "floating[$second] ${overlayRectangles[second]} by " +
                                "${intersection.width()}x${intersection.height()}px"
                        }
                    }
                }
                for (index in rectangles.indices) {
                    val local = RectF(requireNotNull(rectangles[index]) as RectF)
                    assertTrue("${labels[index]} must have a positive actually drawn touch rectangle",
                        local.width() > 0 && local.height() > 0)
                    assertTrue("${labels[index]} must fit fully in the drawn board View",
                        RectF(0f, 0f, board.width.toFloat(), board.height.toFloat()).contains(local))
                    val screen = RectF(local).apply {
                        offset(boardBounds.left.toFloat(), boardBounds.top.toFloat())
                    }
                    overlayRectangles.forEachIndexed { overlayIndex, overlayBounds ->
                        val intersection = RectF(screen)
                        if (intersection.intersect(overlayBounds)) {
                            conflicts += "${labels[index]} screen=$screen intersects floating[$overlayIndex] " +
                                "$overlayBounds by ${intersection.width()}x${intersection.height()}px"
                        }
                    }
                }
                Log.i(TAG, "Committed canvas/floating control overlap evidence: " +
                    if (conflicts.isEmpty()) "none" else conflicts.joinToString("; "))
                assertTrue("All floating targets must be mutually separate and leave every canvas target uncovered: " +
                    conflicts.joinToString("; "), conflicts.isEmpty())
            }
        }
    }

    private data class RoundState(
        val difficulty: Int, val grid: List<Int>, val piece: Int, val rotation: Int,
        val x: Int, val y: Int, val hold: Int, val holdUsed: Boolean, val next: List<Int>,
        val score: Int, val lines: Int, val level: Int, val combo: Int, val backToBack: Boolean,
        val paused: Boolean, val gameOver: Boolean
    )
    private data class TouchPoint(val x: Float, val y: Float)

    private fun withInstalledGame(action: (ActivityScenario<DynamicGameActivity>) -> Unit) {
        withShippedPackage { manifest, apk ->
            val previousCatalog = ModuleManager.getModuleManifest(MODULE_ID)
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            val knownNames = listOf("tetris_module", "tetris", "tetris_save", "tetris_settings")
            val existingNames = File(context.applicationInfo.dataDir, "shared_prefs").listFiles().orEmpty()
                .filter { it.isFile && it.extension == "xml" }.map { it.nameWithoutExtension }
                .filter { it == "tetris" || it.startsWith("tetris_") || it.startsWith("mod_tetris__") }
            // Current Fragment has no SaveManager/snapshot integration. Preserve legacy
            // SaveManager contents as well so an existing older Tetris save stays untouched.
            val preferenceNames = knownNames + knownNames.map { ModuleScopedPreferences.scopedName(MODULE_ID, it) } +
                existingNames + listOf("GameMatrix_saves", ModuleScopedPreferences.scopedName(MODULE_ID, "GameMatrix_saves"),
                    "game_usage", ModuleScopedPreferences.scopedName(MODULE_ID, "game_usage"))
            val prefsBefore = preferenceNames.distinct().map(::capturePreferences)
            val database = AppDatabase.getDatabase(context.applicationContext)
            val usageBefore = database.gameUsageDao().getByIdSync(GAME_ID)?.copy()
            var scenario: ActivityScenario<DynamicGameActivity>? = null
            try {
                ensureShipped(manifest, apk)
                // Explicit reversible preference fixture only; never alter engine data.
                assertTrue(tetrisPreferences().edit().putBoolean("__migrated__", true)
                    .putInt("last_difficulty", 2).commit())
                val launched = ActivityScenario.launch<DynamicGameActivity>(
                    Intent(context, DynamicGameActivity::class.java)
                        .putExtra(DynamicGameActivity.EXTRA_GAME_ID, GAME_ID)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                scenario = launched
                captureScreen("tetris-opened-natural-round", launched, ::boardView)
                action(launched)
            } finally {
                try {
                    scenario?.close()
                } finally {
                    try {
                        ModuleManager.unloadModule(context, MODULE_ID)
                    } finally {
                        try {
                            restoreUserState(database, usageBefore, prefsBefore)
                        } finally {
                            if (previousCatalog != null) ModuleManager.registerAvailableManifests(listOf(previousCatalog))
                        }
                    }
                }
            }
        }
    }

    private fun tetrisPreferences(): SharedPreferences = ModuleScopedPreferences.get(context, MODULE_ID, "tetris_module")

    private fun gameFragment(activity: DynamicGameActivity): Fragment {
        activity.supportFragmentManager.executePendingTransactions()
        val fragment = activity.supportFragmentManager.findFragmentById(R.id.fragment_container)
        assertNotNull("DynamicGameActivity must attach the real external Tetris Fragment", fragment)
        return requireNotNull(fragment).also {
            assertEquals("com.gamecenter.app.tetris.TetrisModuleFragment", it.javaClass.name)
            assertFalse("Tetris must not come from host classes", it.javaClass.classLoader === context.classLoader)
            assertSame("Tetris must use the verified module ClassLoader", ModuleLoader.getClassLoader(MODULE_ID), it.javaClass.classLoader)
            assertTrue(it.requireView().isShown)
        }
    }

    private fun fragmentField(fragment: Fragment, name: String): Any? =
        fragment.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(fragment)

    private fun boardView(activity: DynamicGameActivity): View {
        val fragment = gameFragment(activity)
        return (fragmentField(fragment, "tetrisView") as View).also {
            assertEquals("com.gamecenter.app.tetris.TetrisView", it.javaClass.name)
            assertSame(fragment.javaClass.classLoader, it.javaClass.classLoader)
        }
    }

    private fun boardField(board: View, name: String): Any? =
        board.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(board)

    private fun state(board: View): RoundState {
        fun int(name: String) = board.javaClass.getMethod(name).invoke(board) as Int
        fun bool(name: String) = board.javaClass.getMethod(name).invoke(board) as Boolean
        val grid = (board.javaClass.getMethod("getGrid").invoke(board) as Array<*>).flatMap { (it as IntArray).toList() }
        val next = (board.javaClass.getMethod("getNextQueue").invoke(board) as Collection<*>).map { it as Int }
        return RoundState(int("getDifficultyLevel"), grid, int("getCurrentPiece"), int("getCurrentRotation"),
            int("getPieceX"), int("getPieceY"), int("getHoldPiece"), bool("isHoldUsedThisTurn"), next,
            int("getScore"), int("getLines"), int("getLevel"), int("getCombo"), bool("isBackToBack"),
            bool("isPaused"), bool("isGameOver"))
    }

    private fun readState(scenario: ActivityScenario<DynamicGameActivity>): RoundState {
        lateinit var result: RoundState
        scenario.onActivity { result = state(boardView(it)) }
        return result
    }

    private fun assertSameRoundApartFromNaturalFall(label: String, expected: RoundState, actual: RoundState) {
        assertTrue("Gravity may move down but cannot move up during an ignored HOLD", actual.y >= expected.y)
        assertEquals("Neither an ignored HOLD nor passive gravity may award points", expected.score, actual.score)
        assertEquals(label, expected.copy(y = actual.y), actual)
    }

    private fun difficultyButtons(fragment: Fragment): List<Button> {
        val bar = (fragment.requireView() as LinearLayout).getChildAt(1) as LinearLayout
        assertEquals(4, bar.childCount)
        return (0 until bar.childCount).map { bar.getChildAt(it) as Button }
    }

    private fun difficultyColors(scenario: ActivityScenario<DynamicGameActivity>): List<Pair<Int, Int>> {
        lateinit var result: List<Pair<Int, Int>>
        scenario.onActivity { activity ->
            result = difficultyButtons(gameFragment(activity)).map {
                assertTrue(it.background is ColorDrawable)
                (it.background as ColorDrawable).color to it.currentTextColor
            }
        }
        return result
    }

    private fun touchControl(scenario: ActivityScenario<DynamicGameActivity>, control: Int) {
        lateinit var point: TouchPoint
        scenario.onActivity { activity ->
            val board = boardView(activity)
            val bounds = fullyVisibleScreenBounds(board)
            // captureScreen has already awaited a committed real Window frame.
            val rectangles = boardField(board, "ctrlBtnRects") as Array<*>
            val controlBounds = RectF(requireNotNull(rectangles[control]) as RectF)
            assertTrue("Canvas control must be fully inside the actually drawn board",
                RectF(0f, 0f, board.width.toFloat(), board.height.toFloat()).contains(controlBounds))
            val minimum = 48f * board.resources.displayMetrics.density
            assertTrue("Real HOLD/DROP controls must offer at least 48dp targets", controlBounds.width() >= minimum && controlBounds.height() >= minimum)
            // The center previously hit the floating settings control. Test that exact
            // real center now; the separate geometry regression checks the whole target.
            point = TouchPoint(bounds.left + controlBounds.centerX(), bounds.top + controlBounds.centerY())
            for (overlay in descendants(gameFragment(activity).requireView()).filterIsInstance<ImageButton>()) {
                val overlayBounds = fullyVisibleScreenBounds(overlay)
                assertFalse("The injected canvas point must not hit a floating ImageButton",
                    overlayBounds.contains(point.x.toInt(), point.y.toInt()))
            }
        }
        gesture(point) { downTime -> injectTouch(downTime, MotionEvent.ACTION_UP, point) }
    }

    private fun touchPause(scenario: ActivityScenario<DynamicGameActivity>) {
        lateinit var point: TouchPoint
        scenario.onActivity { activity ->
            val imageButtons = descendants(gameFragment(activity).requireView()).filterIsInstance<ImageButton>().toList()
            assertEquals("The real module exposes pause, restart and settings controls", 3, imageButtons.size)
            val pause = imageButtons.first()
            assertTrue(pause.isEnabled && pause.isClickable)
            val bounds = fullyVisibleScreenBounds(pause)
            point = TouchPoint(bounds.exactCenterX(), bounds.exactCenterY())
        }
        gesture(point) { downTime -> injectTouch(downTime, MotionEvent.ACTION_UP, point) }
    }

    private fun touchPausedOverlay(scenario: ActivityScenario<DynamicGameActivity>) {
        lateinit var point: TouchPoint
        scenario.onActivity { activity ->
            val board = boardView(activity)
            assertTrue("Use the actual paused overlay", state(board).paused)
            val bounds = fullyVisibleScreenBounds(board)
            val localX = board.width / 2f
            val localY = board.height / 2f
            val rectangles = boardField(board, "ctrlBtnRects") as Array<*>
            rectangles.forEach {
                assertFalse("The overlay tap must not hit a canvas control", (it as RectF).contains(localX, localY))
            }
            point = TouchPoint(bounds.left + localX, bounds.top + localY)
            descendants(gameFragment(activity).requireView()).filterIsInstance<ImageButton>().forEach {
                assertFalse("The overlay tap must not hit a floating button",
                    fullyVisibleScreenBounds(it).contains(point.x.toInt(), point.y.toInt()))
            }
        }
        gesture(point) { downTime -> injectTouch(downTime, MotionEvent.ACTION_UP, point) }
    }

    private fun touchSettings(scenario: ActivityScenario<DynamicGameActivity>) {
        lateinit var point: TouchPoint
        scenario.onActivity { activity ->
            val buttons = descendants(gameFragment(activity).requireView()).filterIsInstance<ImageButton>().toList()
            assertEquals(3, buttons.size)
            val settings = buttons[2]
            assertEquals("游戏设置与规则", settings.contentDescription.toString())
            assertTrue(settings.isEnabled && settings.isClickable)
            val bounds = fullyVisibleScreenBounds(settings)
            point = TouchPoint(bounds.exactCenterX(), bounds.exactCenterY())
        }
        gesture(point) { downTime -> injectTouch(downTime, MotionEvent.ACTION_UP, point) }
    }

    private fun touchVisibleDialogText(label: String) {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val item = device.wait(Until.findObject(By.text(label)), 3_000L)
        assertNotNull("The actual dialog must contain the localized item: $label", item)
        assertTrue(requireNotNull(item).isEnabled)
        val bounds = Rect(item.visibleBounds)
        assertTrue(bounds.width() > 0 && bounds.height() > 0)
        val point = TouchPoint(bounds.exactCenterX(), bounds.exactCenterY())
        gesture(point) { downTime -> injectTouch(downTime, MotionEvent.ACTION_UP, point) }
    }

    private fun assertDialogFreezesRound(scenario: ActivityScenario<DynamicGameActivity>, label: String): RoundState {
        val before = readState(scenario)
        val start = SystemClock.uptimeMillis()
        SystemClock.sleep(1_600L)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        val after = readState(scenario)
        val elapsed = SystemClock.uptimeMillis() - start
        Log.i(TAG, "$label observed ${elapsed}ms: y=${before.y}->${after.y}, paused=${after.paused}")
        captureScreen(label, scenario, ::boardView)
        assertTrue("Observe the actual settings/rules dialog for at least 1,500 ms", elapsed >= 1_500L)
        assertEquals("The complete round must remain frozen while $label is visible", before, after)
        assertTrue("The visible settings/rules dialog must own a real gameplay pause", after.paused)
        assertEquals(2, after.difficulty)
        return after
    }

    private fun observeResumedGravity(
        scenario: ActivityScenario<DynamicGameActivity>, before: RoundState, label: String
    ): RoundState {
        assertFalse("The $label observation must begin with active gameplay", before.paused || before.gameOver)
        val deadline = SystemClock.uptimeMillis() + 2_500L
        var after = before
        while (after.y == before.y && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(50L)
            after = readState(scenario)
        }
        assertTrue("The same falling piece must actually move after $label", after.y > before.y)
        assertEquals("Actual passive movement after $label must not award points", before.score, after.score)
        assertEquals("Resuming $label must keep the piece, board, queue and round",
            before.copy(y = after.y), after)
        Log.i(TAG, "$label actually resumed gravity: y=${before.y}->${after.y}, score=${before.score}->${after.score}")
        return after
    }

    private fun dismissDialogThroughSystemBack(resourceId: Int) {
        awaitDialogButton(resourceId)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val automation = instrumentation.uiAutomation
        val downTime = SystemClock.uptimeMillis()
        fun backEvent(action: Int, flags: Int = 0) = KeyEvent(
            downTime, SystemClock.uptimeMillis(), action, KeyEvent.KEYCODE_BACK, 0, 0,
            KeyCharacterMap.VIRTUAL_KEYBOARD, 0, flags, InputDevice.SOURCE_KEYBOARD)
        var downAccepted = false
        var upAccepted = false
        try {
            // UiAutomator 2.3.0 pressBack returns whether a specific accessibility
            // content-change event arrived within 1s, not whether key injection worked.
            // Check both real system injections directly and the actual UI result below.
            downAccepted = automation.injectInputEvent(backEvent(KeyEvent.ACTION_DOWN), true)
            assertTrue("The system must accept the actual BACK key DOWN", downAccepted)
            upAccepted = automation.injectInputEvent(backEvent(KeyEvent.ACTION_UP), true)
            assertTrue("The system must accept the actual BACK key UP", upAccepted)
            Log.i(TAG, "Actual BACK system key injection: DOWN=$downAccepted UP=$upAccepted")
        } finally {
            if (downAccepted && !upAccepted) {
                // Release a partially sent key without performing another Back action.
                automation.injectInputEvent(backEvent(KeyEvent.ACTION_UP, KeyEvent.FLAG_CANCELED), true)
            }
        }
        assertTrue("Actual Back must dismiss the rules dialog", device.wait(
            Until.gone(By.res("android", context.resources.getResourceEntryName(resourceId))), 3_000L))
        instrumentation.waitForIdleSync()
    }

    private fun touchDifficulty(scenario: ActivityScenario<DynamicGameActivity>, level: Int) {
        lateinit var point: TouchPoint
        scenario.onActivity { activity ->
            val button = difficultyButtons(gameFragment(activity))[level - 1]
            assertTrue(button.isEnabled && button.isClickable)
            val bounds = fullyVisibleScreenBounds(button)
            point = TouchPoint(bounds.exactCenterX(), bounds.exactCenterY())
        }
        gesture(point) { downTime -> injectTouch(downTime, MotionEvent.ACTION_UP, point) }
    }

    private fun awaitDialogButton(resourceId: Int): Rect {
        val idName = context.resources.getResourceEntryName(resourceId)
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val button = device.wait(Until.findObject(By.res("android", idName)), 3_000L)
        assertNotNull("The real confirmation dialog must show android:id/$idName", button)
        assertTrue(requireNotNull(button).isEnabled && button.isClickable)
        return Rect(button.visibleBounds).also { assertTrue(it.width() > 0 && it.height() > 0) }
    }

    private fun touchDialogButton(resourceId: Int) {
        val bounds = awaitDialogButton(resourceId)
        val point = TouchPoint(bounds.exactCenterX(), bounds.exactCenterY())
        gesture(point) { downTime -> injectTouch(downTime, MotionEvent.ACTION_UP, point) }
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        assertTrue("The actual confirmation action must close the dialog", device.wait(
            Until.gone(By.res("android", context.resources.getResourceEntryName(resourceId))), 3_000L))
    }

    private fun gesture(start: TouchPoint, finish: (Long) -> Unit) {
        assertFalse("Touch injection must not block the Activity main thread", Looper.myLooper() === Looper.getMainLooper())
        val downTime = SystemClock.uptimeMillis()
        var finished = false
        try {
            injectTouch(downTime, MotionEvent.ACTION_DOWN, start)
            finish(downTime)
            finished = true
        } finally {
            // A failed assertion must not leave a held pointer or pending long press behind.
            if (!finished) injectTouch(downTime, MotionEvent.ACTION_CANCEL, start)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private fun injectTouch(downTime: Long, action: Int, point: TouchPoint) {
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, point.x, point.y, 0)
        try {
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            assertTrue("The system must accept real touchscreen event $action",
                InstrumentationRegistry.getInstrumentation().uiAutomation.injectInputEvent(event, true))
        } finally {
            event.recycle()
        }
    }
    private fun fullyVisibleScreenBounds(view: View): Rect {
        assertTrue("Input target must be attached, laid out and shown",
            view.isAttachedToWindow && view.isLaidOut && view.isShown && view.width > 0 && view.height > 0)
        var ancestor: View? = view
        while (ancestor != null) {
            val current = ancestor
            assertTrue("Input target cannot have an invisible or transparent ancestor",
                current.visibility == View.VISIBLE && current.alpha > 0f)
            ancestor = current.parent as? View
        }
        val local = Rect()
        assertTrue(view.getLocalVisibleRect(local))
        assertEquals("Real input target must not be clipped", Rect(0, 0, view.width, view.height), local)
        val location = IntArray(2).also(view::getLocationOnScreen)
        val bounds = Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
        val window = Rect().also(view::getWindowVisibleDisplayFrame)
        assertTrue("Real input target must fit in the visible window", window.contains(bounds))
        return bounds
    }

    private fun descendants(root: View): Sequence<View> = sequence {
        yield(root)
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) yieldAll(descendants(root.getChildAt(index)))
        }
    }

    private fun restoreUserState(
        database: AppDatabase, rowBefore: GameUsageEntity?, prefsBefore: List<PreferenceSnapshot>
    ) {
        // GameUsageStore writes Room synchronously; the UI callbacks have returned before
        // teardown. Drain UI work, commit over previous preference apply writes, and await
        // the existing suspend DAO delete when this game had no pre-test row.
        val failures = listOf(
            runCatching { InstrumentationRegistry.getInstrumentation().waitForIdleSync() }.exceptionOrNull(),
            runCatching { restoreAllPreferences(prefsBefore) }.exceptionOrNull(),
            runCatching {
                if (rowBefore != null) database.gameUsageDao().upsertSync(rowBefore)
                else runBlocking { database.gameUsageDao().delete(GAME_ID) }
                assertEquals("tetris Room usage must match the exact original row or absence",
                    rowBefore, database.gameUsageDao().getByIdSync(GAME_ID))
            }.exceptionOrNull()
        ).filterNotNull()
        if (failures.isNotEmpty()) {
            throw AssertionError("Failed to restore tetris user data after device verification").apply {
                failures.forEach(::addSuppressed)
            }
        }
    }

    private fun ensureShipped(manifest: ModuleManifest, apk: File) {
        verifyPackage(manifest, apk)
        val previousCatalog = ModuleManager.getModuleManifest(MODULE_ID)
        val installedVersion = ModuleManager.getInstalledVersionCode(context, MODULE_ID)
        assertTrue("This test must not downgrade a newer installed tetris package",
            installedVersion <= manifest.versionCode)
        val installedSnapshot = context.getSharedPreferences("module_manager_prefs", Context.MODE_PRIVATE)
            .getString("module_current_manifest_$MODULE_ID", null)?.let { raw ->
                runCatching { ModuleManifest.fromJson(JSONObject(raw)) }.getOrNull()
            }?.takeIf {
                it.id == MODULE_ID && it.fileName.isNotBlank() && File(it.fileName).name == it.fileName
            }
        val priorFile = (installedSnapshot ?: previousCatalog ?: manifest).let {
            ModuleDownloader.getModuleFileCompat(context, it)
        }.takeIf(File::isFile)
        val priorSha = priorFile?.let(::sha256)
        val alreadyCurrent = priorFile != null && priorSha == manifest.sha256
        val installBranch = when {
            alreadyCurrent -> "SHIPPED_BYTES_ALREADY_CURRENT_SKIP_UPDATE"
            priorFile == null || installedVersion <= 0 -> "FIRST_INSTALL_OR_RECOVERY"
            installedVersion == manifest.versionCode -> "SAME_VERSION_PACKAGE_REPLACEMENT"
            else -> "VERSION_UPGRADE"
        }
        Log.i(TAG, "Install branch=$installBranch beforeVersion=$installedVersion " +
            "beforeSha256=${priorSha ?: "unavailable"} shippedVersion=${manifest.versionCode} " +
            "shippedSha256=${manifest.sha256}")
        ModuleManager.registerAvailableManifests(listOf(manifest))
        val current = if (alreadyCurrent) {
            // Reusing verified bytes must not consume the previous package in last_good.
            // A stale installed snapshot would also affect the real loader; fail explicitly
            // rather than silently reinstalling identical bytes to repair test prerequisites.
            installedSnapshot?.let {
                assertEquals("Installed SHA metadata must match existing shipped bytes", manifest.sha256, it.sha256)
                assertEquals(manifest.fileSize, it.fileSize)
                assertEquals(manifest.versionCode, it.versionCode)
                assertEquals(manifest.entryClass, it.entryClass)
            }
            requireNotNull(priorFile)
        } else {
            // One necessary update is allowed. A fresh device demonstrates first install;
            // only an existing equal-version package with different bytes demonstrates replacement.
            assertTrue("Shipped tetris must install through the production transaction",
                ModuleManager.applyExternalUpdate(context, MODULE_ID, apk, manifest.versionCode))
            TransactionInstaller.getCurrentFile(context, manifest)
        }
        assertEquals(manifest.versionCode, ModuleManager.getInstalledVersionCode(context, MODULE_ID))
        verifyPackage(manifest, current)
        assertEquals("Current must contain exactly the shipped APK bytes", sha256(apk), sha256(current))
        Log.i(TAG, "Verified current ${current.name} v${manifest.versionCode} sha256=${manifest.sha256}")
    }

    private fun captureScreen(
        label: String,
        scenario: ActivityScenario<DynamicGameActivity>,
        targetView: (DynamicGameActivity) -> View
    ) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assertTrue("Screen evidence requires the API 29+ frame-commit callback", Build.VERSION.SDK_INT >= 29)
        val deadline = SystemClock.uptimeMillis() + 5_000L
        fun remainingMillis() = (deadline - SystemClock.uptimeMillis()).coerceAtLeast(0L)
        val frameReady = CountDownLatch(1)
        val cancelled = AtomicBoolean(false)
        var target: View? = null
        var root: View? = null
        var observer: ViewTreeObserver? = null
        var preDraw: ViewTreeObserver.OnPreDrawListener? = null
        var frameCommit: Runnable? = null
        try {
            scenario.onActivity { activity ->
                val expected = targetView(activity)
                val windowRoot = expected.rootView
                target = expected
                root = windowRoot
                assertTrue("Screenshot target must be attached: $label", expected.isAttachedToWindow)
                assertTrue("Frame submission requires a hardware-rendered window", windowRoot.isHardwareAccelerated)
                val tree = windowRoot.viewTreeObserver
                observer = tree
                val committed = Runnable {
                    if (!cancelled.get()) frameReady.countDown()
                }
                frameCommit = committed
                val listener = object : ViewTreeObserver.OnPreDrawListener {
                    override fun onPreDraw(): Boolean {
                        if (!cancelled.get() && expected.isAttachedToWindow && expected.isShown
                            && expected.isLaidOut && expected.width > 0 && expected.height > 0
                            && !expected.isLayoutRequested && !windowRoot.isLayoutRequested) {
                            tree.removeOnPreDrawListener(this)
                            tree.registerFrameCommitCallback(committed)
                        }
                        return true
                    }
                }
                preDraw = listener
                tree.addOnPreDrawListener(listener)
                expected.postInvalidateOnAnimation()
                windowRoot.postInvalidateOnAnimation()
            }
            assertTrue("Timed out waiting for the $label target layout and committed draw",
                frameReady.await(remainingMillis(), TimeUnit.MILLISECONDS))
            scenario.onActivity { activity ->
                assertTrue("The same screenshot target must still be attached and visible: $label",
                    target?.let { it.isAttachedToWindow && it.isShown && !it.isLayoutRequested
                        && it.rootView === root } == true)
                fullyVisibleScreenBounds(requireNotNull(target))
            }
        } finally {
            cancelled.set(true)
            instrumentation.runOnMainSync {
                observer?.takeIf { it.isAlive }?.let { tree ->
                    preDraw?.let(tree::removeOnPreDrawListener)
                    frameCommit?.let(tree::unregisterFrameCommitCallback)
                }
            }
        }

        // Frame commit promises a rendered Surface buffer, not its presentation on the
        // display. PixelCopy reads that actual window buffer without racing the compositor
        // as a full-screen UiAutomation capture can. No View.draw or synthetic pixels are used.
        val copyReady = CountDownLatch(1)
        val copyResult = AtomicInteger(PixelCopy.ERROR_UNKNOWN)
        val copyCancelled = AtomicBoolean(false)
        val ownedBitmap = AtomicReference<Bitmap?>()
        var copyRequested = false
        try {
            scenario.onActivity { activity ->
                val windowRoot = requireNotNull(root)
                val bitmap = Bitmap.createBitmap(windowRoot.width, windowRoot.height, Bitmap.Config.ARGB_8888)
                ownedBitmap.set(bitmap)
                PixelCopy.request(activity.window, bitmap, { result ->
                    copyResult.set(result)
                    copyReady.countDown()
                    if (copyCancelled.get()) ownedBitmap.getAndSet(null)?.recycle()
                }, Handler(Looper.getMainLooper()))
                copyRequested = true
            }
            assertTrue("Timed out copying the committed $label window pixels",
                copyReady.await(remainingMillis(), TimeUnit.MILLISECONDS))
            assertEquals("PixelCopy must read the rendered $label window", PixelCopy.SUCCESS, copyResult.get())
            val bitmap = requireNotNull(ownedBitmap.get())
            val directory = File(context.filesDir, "gamematrix-qa")
            assertTrue("Create screenshot evidence directory", directory.isDirectory || directory.mkdirs())
            val destination = File(directory, "$label-${System.currentTimeMillis()}.png")
            destination.outputStream().use { output ->
                assertTrue("Save actual window pixels as PNG", bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
            assertTrue("Saved screenshot must not be empty", destination.length() > 0)
            Log.i(TAG, "Window pixel screenshot evidence (PixelCopy, not full screen) " +
                "${destination.absolutePath} ${bitmap.width}x${bitmap.height}")
        } finally {
            copyCancelled.set(true)
            // PixelCopy can complete after timeout. Its callback owns recycling while
            // the request is in flight; atomic ownership prevents a concurrent double recycle.
            if (!copyRequested || copyReady.count == 0L) ownedBitmap.getAndSet(null)?.recycle()
        }
    }

    private fun withShippedPackage(action: (ModuleManifest, File) -> Unit) {
        // Instrumentation enters directly, without Splash's context-aware catalog bootstrap.
        // Do this before any context-free manifest/version lookup can seed a VPN-only cache.
        ModuleManager.registerLocalFallbackIfNeeded(context)
        val modules = context.assets.open("modules.json").bufferedReader(Charsets.UTF_8).use {
            JSONObject(it.readText()).getJSONArray("modules")
        }
        val records = (0 until modules.length()).map { modules.getJSONObject(it) }
            .filter { it.optString("id") == MODULE_ID }
        assertEquals("The shipped catalog must identify tetris exactly once", 1, records.size)
        val manifest = ModuleManifest.fromJson(records.single())
        assertEquals("com.gamecenter.app.tetris.TetrisModuleEntryPoint", manifest.entryClass)
        assertTrue("Shipped tetris must remain an external APK", manifest.fileName.endsWith(".apk"))
        assertEquals(File(manifest.fileName).name, manifest.fileName)
        assertTrue("Shipped tetris must specify SHA-256 and a positive size",
            manifest.sha256.length == 64 && manifest.fileSize > 0L)
        val apk = File.createTempFile("shipped-tetris-instrumentation-", ".apk", context.cacheDir)
        try {
            context.assets.open("modules/${manifest.fileName}").use { input ->
                apk.outputStream().use { output -> input.copyTo(output) }
            }
            action(manifest, apk)
        } finally {
            apk.delete()
        }
    }

    private fun verifyPackage(manifest: ModuleManifest, apk: File) {
        // The existing checked release has catalog version 103 while Android's archive
        // version remains 102. Preserve and check both contracts independently.
        assertEquals("Existing catalog version must stay unchanged for this local QA package", 103, manifest.versionCode)
        assertEquals("1.0.2", manifest.versionName)
        val archive = context.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)
        assertNotNull("The shipped Tetris bytes must be a readable Android APK", archive)
        assertEquals("Android archive version must preserve the existing v102 manifest", 102L,
            requireNotNull(archive).longVersionCode)
        assertEquals("1.0.2", archive.versionName)
        assertEquals("tetris APK size must match its shipped manifest", manifest.fileSize, apk.length())
        assertTrue("tetris APK must pass the production SHA/size verifier",
            ModuleVerifier.verify(apk, manifest.sha256, manifest.fileSize).isSuccess)
        assertTrue("tetris APK must pass production publisher-certificate pinning",
            ModuleSignatureVerifier.verify(apk, context) is ModuleSignatureVerifier.Result.Success)
    }

    private fun sha256(file: File): String = com.gamecenter.app.modules.ModuleVerifier.computeSha256(file)

    private data class PreferenceSnapshot(val name: String, val values: Map<String, Any?>)

    private fun preferenceValues(prefs: SharedPreferences): Map<String, Any?> =
        prefs.all.mapValues { (_, value) -> if (value is Set<*>) value.toSet() else value }

    private fun capturePreferences(name: String) = PreferenceSnapshot(
        name, preferenceValues(context.getSharedPreferences(name, Context.MODE_PRIVATE))
    )

    private fun restoreAllPreferences(snapshots: List<PreferenceSnapshot>) {
        val failures = snapshots.mapNotNull { snapshot ->
            runCatching { restorePreferences(snapshot) }.exceptionOrNull()
        }
        if (failures.isNotEmpty()) {
            throw AssertionError("Failed to restore tetris preferences after device verification").apply {
                failures.forEach(::addSuppressed)
            }
        }
    }

    private fun restorePreferences(snapshot: PreferenceSnapshot) {
        val prefs = context.getSharedPreferences(snapshot.name, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        prefs.all.keys.filterNot(snapshot.values::containsKey).forEach(editor::remove)
        snapshot.values.forEach { (key, value) ->
            when (value) {
                null -> editor.remove(key)
                is String -> editor.putString(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Set<*> -> editor.putStringSet(key, value.map { it as String }.toSet())
                else -> error("Unsupported preference type for ${snapshot.name}/$key")
            }
        }
        assertTrue("Restore Tetris usage preferences: ${snapshot.name}", editor.commit())
        assertEquals("Tetris usage preferences must match their pre-test state: ${snapshot.name}",
            snapshot.values, preferenceValues(prefs))
    }

    companion object {
        private const val MODULE_ID = "tetris"
        private const val GAME_ID = "tetris"
        private const val TAG = "ShippedTetrisTest"
        private const val HOLD = 0
        private const val ROTATE = 2
        private const val DROP = 4
    }
}
