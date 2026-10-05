#!/usr/bin/env python3
"""Guard: positioning cuts (coin / game weekly report / daily reminder) must not resurface in product UI."""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="replace")


def main() -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")

    failures: list[str] = []

    profile = ROOT / "app/src/main/kotlin/com/gamecenter/app/ProfileFragment.kt"
    app = ROOT / "app/src/main/kotlin/com/gamecenter/app/App.kt"
    layout = ROOT / "app/src/main/res/layout/fragment_profile.xml"

    for path in (profile, app, layout):
        if not path.is_file():
            failures.append(f"missing {path.relative_to(ROOT)}")

    if failures:
        print("\n".join(failures), file=sys.stderr)
        return 2

    profile_t = read(profile)
    app_t = read(app)
    layout_t = read(layout)

    # 1) Profile must not launch cut-domain activities
    if "CoinWalletActivity.launch" in profile_t:
        failures.append("ProfileFragment: CoinWalletActivity.launch must not return")
    if "WeeklyReportActivity.launch" in profile_t:
        failures.append("ProfileFragment: WeeklyReportActivity.launch must not return")
    if re.search(r"import\s+com\.gamecenter\.app\.games\.coin\.CoinWallet", profile_t):
        failures.append("ProfileFragment: CoinWallet import must not return")

    # 2) App must not schedule daily reminder
    if re.search(r"^\s*com\.gamecenter\.app\.games\.reminder\.DailyReminderScheduler\.ensureScheduled",
                 app_t, re.M):
        failures.append("App.kt: DailyReminderScheduler.ensureScheduled must stay commented")

    # 3) Layout entries stay hidden
    if not re.search(r'android:id="@\+id/tv_profile_coins_value"[\s\S]{0,400}?android:visibility="gone"'
                     r'|android:visibility="gone"[\s\S]{0,200}?android:id="@\+id/tv_profile_coins_value"',
                     layout_t):
        # coin block parent has visibility=gone — accept either order near coins id
        if 'android:id="@+id/tv_profile_coins_value"' in layout_t and "visibility=\"gone\"" not in layout_t:
            failures.append("fragment_profile.xml: coin block must be visibility=gone")
    if re.search(r'android:id="@\+id/btn_profile_weekly_report"', layout_t) and \
            not re.search(r'android:visibility="gone"\s*\n\s*android:id="@\+id/btn_profile_weekly_report"'
                          r'|android:id="@\+id/btn_profile_weekly_report"[\s\S]{0,80}?android:visibility="gone"',
                          layout_t):
        # check a gone flag exists on the weekly report block
        block = layout_t.split('btn_profile_weekly_report', 1)
        if len(block) == 2 and 'android:visibility="gone"' not in block[0][-200:]:
            # look at the LinearLayout opening just before id
            idx = layout_t.find('btn_profile_weekly_report')
            window = layout_t[max(0, idx - 250):idx + 80]
            if 'android:visibility="gone"' not in window:
                failures.append("fragment_profile.xml: btn_profile_weekly_report must be visibility=gone")

    # 4) Codebook must be framed as level-code book, not password book
    if "密码本" in profile_t or "password book" in profile_t.lower():
        failures.append("ProfileFragment: do not call codebook a password book")

    if failures:
        print("POSITIONING_GUARD: FAIL")
        for f in failures:
            print(f"  - {f}", file=sys.stderr)
        return 1

    print("POSITIONING_GUARD=PASS (coin/weekly/reminder product surface offline)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
