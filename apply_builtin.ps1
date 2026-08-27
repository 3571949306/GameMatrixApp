# Module layering refactor: mark 12 curated games as builtIn (playable out of box)
$ErrorActionPreference = "Stop"

$assetsDir = "d:\Developmment\GameMatrixApp\app\src\main\assets"

# 11 curated games (breakout already builtIn=true)
$games = [ordered]@{
    "gomoku"      = "com.gamecenter.app.games.gomoku.GomokuActivity"
    "go"          = "com.gamecenter.app.games.go.GoActivity"
    "doudizhu"    = "com.gamecenter.app.games.doudizhu.DouDiZhuMenuActivity"
    "blackjack"   = "com.gamecenter.app.games.blackjack.BlackjackActivity"
    "checkers"    = "com.gamecenter.app.games.checkers.CheckersActivity"
    "game_2048"   = "com.gamecenter.app.games.game2048.Game2048Activity"
    "sudoku"      = "com.gamecenter.app.games.sudoku.SudokuActivity"
    "klotski"     = "com.gamecenter.app.games.klotski.KlotskiActivity"
    "minesweeper" = "com.gamecenter.app.games.minesweeper.MinesweeperActivity"
    "tetris"      = "com.gamecenter.app.games.tetris.TetrisActivity"
    "snake"       = "com.gamecenter.app.games.snake.SnakeActivity"
}

$path = Join-Path $assetsDir "modules.json"
$text = [System.IO.File]::ReadAllText($path)

$changed = 0
foreach ($gid in $games.Keys) {
    $cls = $games[$gid]

    # 1) activityClass: "" -> host Activity (anchored by following gameId line)
    $pat1 = '"activityClass": "",(\s*\r?\n\s*"gameId": "' + $gid + '",)'
    $rep1 = '"activityClass": "' + $cls + '",$1'
    $new1 = [regex]::Replace($text, $pat1, $rep1)
    if ($new1 -ne $text) { $text = $new1; $changed++ } else { Write-Warning "activityClass NOT matched: $gid" }

    # 2) builtIn: false -> true (anchored by gameId in same entry)
    $pat2 = '("gameId": "' + $gid + '",[\s\S]*?"builtIn": )false'
    $new2 = [regex]::Replace($text, $pat2, '${1}true')
    if ($new2 -ne $text) { $text = $new2 } else { Write-Warning "builtIn NOT matched: $gid" }

    # 3) builtInVersionCode: 0 -> 100 (equal to store version; future v200+ on server enables update)
    $pat3 = '("gameId": "' + $gid + '",[\s\S]*?"builtInVersionCode": )0'
    $new3 = [regex]::Replace($text, $pat3, '${1}100')
    if ($new3 -ne $text) { $text = $new3 } else { Write-Warning "builtInVersionCode NOT matched: $gid" }
}

# 4) catalog header: catalogVersion 10->11, version 31->32, timestamp
$text = $text.Replace('"catalogVersion": 10,', '"catalogVersion": 11,')
$text = $text.Replace('"version": 31,', '"version": 32,')
$text = $text.Replace('"generatedAt": "2026-07-27T13:00:00Z"', '"generatedAt": "2026-08-23T10:30:00Z"')

# Validate JSON before writing
try {
    $null = $text | ConvertFrom-Json
} catch {
    throw "Generated JSON is INVALID, aborting write: $_"
}
[System.IO.File]::WriteAllText($path, $text, (New-Object System.Text.UTF8Encoding($false)))

# Sync catalog.json (files were byte-identical)
Copy-Item $path (Join-Path $assetsDir "catalog.json") -Force

Write-Host "DONE: $changed/11 games marked builtIn; modules.json and catalog.json synced"
