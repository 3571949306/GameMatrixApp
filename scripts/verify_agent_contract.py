#!/usr/bin/env python3
"""Fast, side-effect-free checks for repository invariants that future agents must preserve."""

from __future__ import annotations

import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CHESS = ROOT / "module-store/feature/games/games/chinesechess/src/main/java/com/gamecenter/app/chinesechess"
APP_AI = ROOT / "app/src/main/java/com/gamecenter/app/games/chinesechess/ChineseChessAI.java"
MODULE_AI = CHESS / "ChineseChessAI.java"
GO_MODULE = ROOT / "module-store/feature/games/games/go/src/main/java/com/gamecenter/app/go"
GO_APP = ROOT / "app/src/main/java/com/gamecenter/app/games/go"


def read(path: Path) -> str:
    if not path.is_file():
        raise AssertionError(f"missing required file: {path.relative_to(ROOT)}")
    return path.read_text(encoding="utf-8")


def require(text: str, pattern: str, message: str) -> None:
    if re.search(pattern, text, flags=re.MULTILINE | re.DOTALL) is None:
        raise AssertionError(message)


def normalize_ai(text: str) -> str:
    """Ignore the documented sync header and the unavoidable package-name difference."""
    package_at = text.index("package ")
    body = text[package_at:]
    return re.sub(
        r"package com\.gamecenter\.app(?:\.games)?\.chinesechess;",
        "package <CHINESE_CHESS>;",
        body,
        count=1,
    ).replace("\r\n", "\n")


def normalize_go(text: str) -> str:
    """Ignore sync comments and the host/dynamic Go package-name difference."""
    package_at = text.index("package ")
    body = text[package_at:]
    return re.sub(
        r"package com\.gamecenter\.app(?:\.games)?\.go;",
        "package <GO>;",
        body,
        count=1,
    ).replace("\r\n", "\n")


def main() -> int:
    try:
        read(ROOT / "AGENTS.md")
        app_gradle = read(ROOT / "app/build.gradle")
        ci_workflow = read(ROOT / ".github/workflows/ci.yml")
        read(ROOT / "scripts/verify_protected_assets.py")
        game = read(CHESS / "ChineseChessGame.java")
        module_fragment = read(CHESS / "ChineseChessModuleFragment.java")
        online_fragment = read(CHESS / "ChineseChessOnlineFragment.java")
        module_ai = read(MODULE_AI)
        app_ai = read(APP_AI)
        module_view = read(CHESS / "ChineseChessView.java")
        ui_preferences = read(CHESS / "ChineseChessUiPreferences.java")
        chess_layout = read(
            ROOT / "module-store/feature/games/games/chinesechess/src/main/res/layout/activity_chinese_chess.xml"
        )
        go_module_game = read(GO_MODULE / "GoGame.java")
        go_module_ai = read(GO_MODULE / "GoAI.java")
        go_module_view = read(GO_MODULE / "GoView.java")
        go_module_prefs = read(GO_MODULE / "GoUiPreferences.java")
        go_fragment = read(GO_MODULE / "GoModuleFragment.java")
        go_app_game = read(GO_APP / "GoGame.java")
        go_app_ai = read(GO_APP / "GoAI.java")
        go_app_view = read(GO_APP / "GoView.java")
        go_app_prefs = read(GO_APP / "GoUiPreferences.java")
        go_activity = read(GO_APP / "GoActivity.java")
        coachmark_sequence = read(ROOT / "app/src/main/java/com/gamecenter/app/ui/onboarding/CoachmarkSequence.kt")
        go_debug_manifest = read(ROOT / "app/src/debug/AndroidManifest.xml")
        main_manifest = read(ROOT / "app/src/main/AndroidManifest.xml")
        read(ROOT / "scripts/verify_go.py")
        agent_rules = read(ROOT / "AGENTS.md")

        require(game, r"private\s+MoveRecord\s+makeMoveSafe\s*\(",
                "makeMoveSafe must remain private and simulation-only")
        require(game, r"commitMove\s*\([^)]*\)\s*\{.*?isMoveLegal.*?switchSide\(\).*?recordPosition.*?checkGameOver",
                "commitMove must atomically validate, switch side, record, and adjudicate")
        require(game, r"p\.type\.ordinal\(\)\s*\+\s*1",
                "game hash must use the AI-compatible 1..7 piece encoding")
        require(game, r"isContinuousUnilateralCheck",
                "repetition adjudication must distinguish unilateral continuous check")

        for name, source in (("ChineseChessModuleFragment", module_fragment),
                             ("ChineseChessOnlineFragment", online_fragment)):
            if re.search(r"\.(?:makeMoveSafe|recordPosition|switchSide)\s*\(", source):
                raise AssertionError(f"{name} bypasses the central commitMove gate")
        require(module_fragment, r"game\.commitMove\s*\(",
                "human/AI module paths must use commitMove")
        require(module_fragment, r"prefilledIndex.*?selectDifficulty\s*\(mappedDifficulty\)",
                "hall difficulty may preselect but must not auto-start Chinese chess")
        prefill_block = re.search(
            r"if\s*\(prefilledIndex\s*>=\s*0\)\s*\{(?P<body>.*?)\n\s*\}",
            module_fragment,
            flags=re.DOTALL,
        )
        if prefill_block is None or "beginGame(" in prefill_block.group("body"):
            raise AssertionError("prefilled hall difficulty must not bypass the visible selector")
        require(module_fragment, r"AI_CONTRACT_VIOLATION",
                "AI fallback must emit contract-violation telemetry")
        require(module_fragment, r"generation\s*!=\s*gameGeneration",
                "stale AI/animation callbacks must be rejected by game generation")
        require(online_fragment, r"syncedGame\.commitMove\s*\(",
                "network state replay must validate every move through commitMove")

        require(chess_layout, r"@\+id/check_simple_board",
                "Chinese chess must expose the simple-board setting before play")
        require(chess_layout, r"@\+id/btn_board_style",
                "Chinese chess must expose the board-style setting during play")
        button_tags = re.findall(r"<Button\b.*?/>", chess_layout, flags=re.DOTALL)
        if not button_tags or any('android:stateListAnimator="@null"' not in tag
                                  for tag in button_tags):
            raise AssertionError(
                "dynamic Chinese-chess Buttons must disable host-theme state animators"
            )
        require(module_view, r"setSimpleMode\s*\(",
                "ChineseChessView must support the persisted simple-board mode")
        require(ui_preferences, r"board_style_v1.*?enhanced.*?simple",
                "board style must use a versioned module-local preference with enhanced default")
        require(agent_rules, r"undoCount=0.*?AI_CONTRACT_VIOLATION",
                "agent rules must preserve no-undo and raw-AI telemetry acceptance requirements")
        require(agent_rules, r"stateListAnimator.*?@null.*?真机",
                "agent rules must require dynamic-resource compatibility and device acceptance")

        require(module_ai, re.escape("if (board[r][c] != getInitialPiece(r, c)) return false;"),
                "opening book must require the exact initial position")
        require(module_ai, r"getOpeningMove.*?isMoveLegal\s*\(",
                "opening-book candidates must be legality checked")
        require(module_ai, r"MAX_SEARCH_PLY",
                "AI search needs an absolute ply limit")
        require(module_ai, r"computePositionHash\(newBoard,\s*-(?:aiSide|normalizedSide)\)",
                "AI repetition hash must include the next side to move")

        if normalize_ai(module_ai) != normalize_ai(app_ai):
            raise AssertionError("module-store and app ChineseChessAI implementations are out of sync")

        # Go's host and dynamic implementations are both shipping paths. Keep their rules and
        # search engines byte-for-byte equivalent apart from the package declaration.
        if normalize_go(go_module_game) != normalize_go(go_app_game):
            raise AssertionError("module-store and app GoGame implementations are out of sync")
        if normalize_go(go_module_ai) != normalize_go(go_app_ai):
            raise AssertionError("module-store and app GoAI implementations are out of sync")
        if normalize_go(go_module_view) != normalize_go(go_app_view):
            raise AssertionError("module-store and app GoView implementations are out of sync")
        if normalize_go(go_module_prefs) != normalize_go(go_app_prefs):
            raise AssertionError("module-store and app GoUiPreferences implementations are out of sync")

        require(go_module_game,
                r"tryMove\s*\(int\[\]\[\]\s+state,.*?OUT_OF_BOUNDS.*?OCCUPIED.*?SUICIDE.*?KO",
                "GoGame.tryMove must remain the central bounds/occupied/suicide/ko legality gate")
        require(go_module_game, r"PositionSnapshot.*?previousBoard.*?consecutivePasses.*?gameOver",
                "Go snapshots must preserve ko, pass, and terminal state")
        require(go_module_game, r"KOMI\s*=\s*6\.5f.*?calculateScore\s*\(",
                "Go adjudication must use Chinese area scoring with 6.5 komi")
        require(go_module_ai, r"game\.snapshot\s*\(\).*?GoGame\.tryMove\s*\(",
                "Go AI must search an immutable snapshot through the shared legality gate")
        require(go_module_ai, r"whiteReward\s*=\s*playout.*?current\s*=\s*current\.parent",
                "Go MCTS must backpropagate a fixed white-perspective reward through parent links")
        if re.search(r"nextInt\s*\(\s*(?:5|10)\s*\).*?return\s+null", go_module_ai,
                     flags=re.DOTALL):
            raise AssertionError("Go AI must not randomly pass while legal moves exist")

        for name, controller in (("GoActivity", go_activity),
                                 ("GoModuleFragment", go_fragment)):
            require(controller, r"GO_AI_CONTRACT_VIOLATION",
                    f"{name} must log raw AI contract violations")
            require(controller, r"aiGeneration.*?GoGame\.WHITE.*?isGameOver",
                    f"{name} must reject stale AI callbacks and wrong-turn writes")
            require(controller, r"startAiTurn.*?createSearchSnapshot\s*\(",
                    f"{name} must give AI a snapshot rather than mutable live state")
            require(controller,
                    r"createSearchSnapshot\s*\(\).*?getBoardSnapshot\s*\(\).*?getPreviousBoardSnapshot\s*\(",
                    f"{name} search snapshot must include board and ko history")
            require(controller, r"game_difficulty_index.*?setDifficulty",
                    f"{name} must map hall difficulty only to a visible preselection")
        require(go_fragment, r"setStateListAnimator\s*\(\s*null\s*\)",
                "dynamic Go buttons must disable host-theme state animators")
        require(go_module_view, r"setSimpleMode\s*\(.*?drawCoordinates",
                "GoView must keep an enhanced/simple board mode")
        require(go_module_prefs, r"board_style_v1.*?isSimpleMode.*?setSimpleMode",
                "Go board style must persist under a versioned preference")
        require(agent_rules, r"GO_AI_CONTRACT_VIOLATION.*?verify_go\.py",
                "agent rules must require Go contract telemetry and regression verification")
        require(go_debug_manifest, r"GoActivity.*?android:exported=\"true\"",
                "Debug must expose a deterministic Go emulator acceptance entry point")
        require(main_manifest, r"GoActivity\"\s+android:exported=\"false\"",
                "Release GoActivity must remain non-exported")
        require(coachmark_sequence, r"decor\.addView\s*\(",
                "Compose coachmarks must attach to the Activity decor ViewTree")
        if "windowManager.addView(composeView" in coachmark_sequence:
            raise AssertionError("Compose coachmarks must not bypass ViewTreeLifecycleOwner")

        if re.search(r"merge(?:Debug|Release)Assets.*dependsOn.*bundlePreinstalledModules",
                     app_gradle, flags=re.DOTALL):
            raise AssertionError("ordinary asset merge must not trigger preinstalled-module packaging")
        require(app_gradle, r"assembleDebugWithPreinstalledModules",
                "explicit Debug preinstalled-module lifecycle task is missing")
        require(app_gradle, r"assembleReleaseWithPreinstalledModules",
                "explicit Release preinstalled-module lifecycle task is missing")
        require(ci_workflow, r"verify_protected_assets\.py snapshot.*?verify_protected_assets\.py verify",
                "CI must snapshot and verify protected release assets around tests")
    except (AssertionError, ValueError) as exc:
        print(f"AGENT CONTRACT: FAIL\n- {exc}", file=sys.stderr)
        return 1

    print("AGENT CONTRACT: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
