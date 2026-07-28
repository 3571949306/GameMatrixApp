$games = @(
    @{name="gomoku"; activity=".games.gomoku.GomokuActivity"},
    @{name="doudizhu"; activity=".games.doudizhu.DouDiZhuMenuActivity"},
    @{name="blackjack"; activity=".games.blackjack.BlackjackActivity"},
    @{name="breakout"; activity=".games.breakout.BreakoutActivity"},
    @{name="brotato"; activity=".games.brotato.BrotatoActivity"},
    @{name="checkers"; activity=".games.checkers.CheckersActivity"},
    @{name="dice"; activity=".games.dice.DiceActivity"},
    @{name="flappy"; activity=".games.flappy.FlappyActivity"},
    @{name="go"; activity=".games.go.GoActivity"},
    @{name="guess"; activity=".games.guess.GuessActivity"},
    @{name="match"; activity=".games.match.MatchActivity"},
    @{name="memory"; activity=".games.memory.MemoryActivity"},
    @{name="minesweeper"; activity=".games.minesweeper.MinesweeperActivity"},
    @{name="pipeline"; activity=".games.pipeline.PipelineActivity"},
    @{name="plane"; activity=".games.plane.PlaneActivity"},
    @{name="reaction"; activity=".games.reaction.ReactionActivity"},
    @{name="rock"; activity=".games.rock.RockActivity"},
    @{name="snake"; activity=".games.snake.SnakeActivity"},
    @{name="sokoban"; activity=".games.sokoban.SokobanActivity"},
    @{name="sudoku"; activity=".games.sudoku.SudokuActivity"},
    @{name="tetris"; activity=".games.tetris.TetrisActivity"},
    @{name="tic"; activity=".games.tic.TicTacToeActivity"},
    @{name="tiles"; activity=".games.tiles.TilesActivity"},
    @{name="whack"; activity=".games.whack.WhackActivity"},
    @{name="klotski"; activity=".games.klotski.KlotskiActivity"},
    @{name="chinesechess"; activity=".games.chinesechess.ChineseChessActivity"},
    @{name="game2048"; activity=".games.game2048.Game2048Activity"}
)

$results = @()
$serial = "emulator-5554"

foreach ($game in $games) {
    $n = $game.name
    $act = $game.activity
    Write-Host "=== Testing $n ($act) ===" -ForegroundColor Cyan

    # Start activity
    $startOut = adb -s $serial shell am start -n "com.gamecenter.app/$act" 2>&1
    $startStr = $startOut -join " "
    Write-Host "  Start: $startStr"

    # Check if start failed
    $startFailed = $false
    if ($startStr -match "Error|Exception|not found|does not exist") {
        $startFailed = $true
        Write-Host "  START FAILED!" -ForegroundColor Red
    }

    # Wait 2 seconds
    Start-Sleep -Seconds 2

    # Screenshot
    $ssPath = "/sdcard/test_${n}.png"
    $localPath = "d:\Developmment\GameMatrixApp\test_${n}.png"
    adb -s $serial shell screencap -p $ssPath 2>&1 | Out-Null
    adb -s $serial pull $ssPath $localPath 2>&1 | Out-Null
    $ssOk = Test-Path $localPath
    if ($ssOk) {
        $sz = (Get-Item $localPath).Length
        Write-Host "  Screenshot: OK ($sz bytes)" -ForegroundColor Green
    } else {
        Write-Host "  Screenshot: FAILED" -ForegroundColor Red
    }

    # Press back
    adb -s $serial shell input keyevent KEYCODE_BACK 2>&1 | Out-Null
    Start-Sleep -Milliseconds 500

    $results += [PSCustomObject]@{
        Game = $n
        Activity = $act
        StartFailed = $startFailed
        StartOutput = $startStr
        ScreenshotOK = $ssOk
    }
}

Write-Host ""
Write-Host "========== SUMMARY ==========" -ForegroundColor Yellow
foreach ($r in $results) {
    $status = if ($r.StartFailed) { "FAIL" } else { "OK" }
    $ss = if ($r.ScreenshotOK) { "SS:OK" } else { "SS:FAIL" }
    Write-Host ("  {0,-16} {1,-8} {2}" -f $r.Game, $status, $ss)
}
Write-Host "=============================" -ForegroundColor Yellow
