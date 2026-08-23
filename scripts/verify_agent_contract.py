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
        require(online_fragment, r"syncedGame\.commitMove\s*\(",
                "network state replay must validate every move through commitMove")

        require(module_ai, re.escape("if (board[r][c] != getInitialPiece(r, c)) return false;"),
                "opening book must require the exact initial position")
        require(module_ai, r"getOpeningMove.*?isMoveLegal\s*\(",
                "opening-book candidates must be legality checked")
        require(module_ai, r"MAX_SEARCH_PLY",
                "AI search needs an absolute ply limit")
        require(module_ai, r"computePositionHash\(newBoard,\s*-aiSide\)",
                "AI repetition hash must include the next side to move")

        if normalize_ai(module_ai) != normalize_ai(app_ai):
            raise AssertionError("module-store and app ChineseChessAI implementations are out of sync")

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
