#!/usr/bin/env python3
"""
i18n 补全脚本：为 app 模块补全缺失的中英翻译。
- 中文有但英文缺失：根据 ZH_TO_EN 字典补到 values-en/strings.xml
- 英文有但中文缺失：根据 EN_TO_ZH 字典补到 values/strings.xml
保留已有翻译，只追加缺失项。
"""
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
APP_ZH = ROOT / "app/src/main/res/values/strings.xml"
APP_EN = ROOT / "app/src/main/res/values-en/strings.xml"

KEY_VAL_RE = re.compile(r'<string\s+name="([^"]+)"\s*>(.*?)</string>', re.DOTALL)
KEY_ONLY_RE = re.compile(r'<string\s+name="([^"]+)"')

def extract_keys(path):
    keys = set()
    with open(path, "r", encoding="utf-8") as f:
        for m in KEY_ONLY_RE.finditer(f.read()):
            keys.add(m.group(1))
    return keys

# ============================================================
# 中文 → 英文 翻译表（321 条，对应中文有但英文缺失的 key）
# 保留所有 %s/%d/%1$s/%1$d/%02d 等占位符
# ============================================================
ZH_TO_EN = {
    "nav_browser": "Browser",
    "nav_games": "Games",
    "nav_tools": "Tools",
    "nav_ai": "AI Assistant",
    "nav_vpn": "VPN",
    "ai_assistant": "AI Assistant",
    "ai_task_chat": "Chat",
    "ai_task_ocr_clean": "OCR Cleanup",
    "ai_task_summary": "Summarize",
    "ai_task_translate": "Translate",
    "ai_task_rewrite": "Rewrite",
    "ai_task_ocr": "OCR",
    "ai_task_qa_pairs": "QA Pairs",
    "ai_task_keywords": "Keywords",
    "ai_task_classify": "Classify",
    "ai_input_hint": "Enter text to process…",
    "ai_execute": "Run",
    "ai_open_full_page": "Open full AI page",
    "ai_status_ready": "Ready",
    "ai_status_processing": "Processing…",
    "ai_status_done": "Done",
    "ai_status_failed": "Failed",
    "ai_result_empty": "Please enter text to process",
    "ai_no_api_key": "No API key configured. Cloud AI features are unavailable.",
    "ai_quota_exceeded": "Today\'s free quota is used up",
    "ai_local_mode": "Local mode",
    "ai_cloud_mode": "Cloud mode",
    "gomoku": "Gomoku",
    "chinese_chess": "Chinese Chess",
    "gomoku_desc": "Classic Gomoku vs AI",
    "chinese_chess_desc": "Chinese Chess vs AI",
    "browser_loading": "Loading…",
    "browser_initial_url": "https://www.baidu.com",
    "btn_undo": "Undo",
    "btn_restart": "Restart",
    "difficulty_hell": "Hell",
    "game_entrance": "Enter game",
    "snake": "Snake",
    "snake_desc": "Classic Snake",
    "tetris": "Tetris",
    "tetris_desc": "Classic Tetris",
    "game_doudizhu": "Dou Dizhu",
    "game_doudizhu_desc": "Classic three-player card game",
    "brotato": "Survival Shooter",
    "brotato_desc": "Defeat enemies, collect coins, upgrade",
    "tools_title": "Tools",
    "ip_info": "IP Address",
    "wifi_ip": "Wi-Fi IP",
    "mobile_ip": "Mobile IP",
    "dns_info": "DNS Servers",
    "dns1": "DNS 1",
    "dns2": "DNS 2",
    "wifi_signal": "Wi-Fi Signal",
    "wifi_signal_strength": "Signal Strength",
    "battery_info": "Battery Info",
    "battery_level": "Level",
    "battery_status": "Status",
    "battery_temperature": "Temperature",
    "battery_voltage": "Voltage",
    "device_model": "Model",
    "device_brand": "Brand",
    "device_os_version": "OS Version",
    "network_speed_test": "Speed Test",
    "start_test": "Start Test",
    "upload_speed": "Upload Speed",
    "ping": "Ping",
    "port_scan": "Port Scan",
    "target_ip": "Target IP",
    "scan_ports": "Scan Ports",
    "qr_code": "QR Code Tool",
    "generate_qr": "Generate QR",
    "scan_qr": "Scan QR",
    "qrcode_text": "Enter text",
    "generate": "Generate",
    "category_classics": "Classics",
    "category_puzzle": "Puzzle",
    "category_casual": "Casual",
    "category_reaction": "Reaction",
    "category_other": "Other",
    "game_sudoku": "Sudoku",
    "game_sokoban": "Sokoban",
    "game_pipeline": "Pipeline",
    "game_klotski": "Klotski",
    "game_breakout": "Breakout",
    "game_whack": "Whack-a-Mole",
    "game_match": "Match-3",
    "game_blackjack": "Blackjack",
    "game_checkers": "Checkers",
    "game_flappy": "Flappy Bird",
    "game_tiles": "Don\'t Tap White Tile",
    "game_plane": "Air Combat",
    "game_brotato": "Survival Shooter",
    "game_rock": "Rock Paper Scissors",
    "game_tic": "Tic-Tac-Toe",
    "game_memory": "Memory Cards",
    "game_guess": "Guess Number",
    "game_dice": "Dice Game",
    "game_knife": "Knife Master",
    "game_knife_desc": "Throw knives precisely, hit the rotating target, avoid inserted knives",
    "game_reaction": "Reaction Challenge",
    "game_go": "Go",
    "chinesechess": "Chinese Chess",
    "chinesechess_desc": "Classic Chinese Chess game",
    "checkers": "Checkers",
    "checkers_desc": "Classic checkers",
    "blackjack": "Blackjack",
    "blackjack_desc": "Classic card game",
    "rock": "Rock Paper Scissors",
    "rock_desc": "Simple rock-paper-scissors",
    "feedback_default": "Open local mail client",
    "feedback_qq": "QQ Mail (web)",
    "feedback_163": "NetEase 163 Mail (web)",
    "feedback_gmail": "Gmail (web)",
    "feedback_outlook": "Outlook (web)",
    "feedback_toast": "Opening mail client…",
    "feedback_netease_toast": "Opening NetEase Mail Master…",
    "feedback_copy_hint": "Fallback recipient is not configured in the public repo. Prefer \"Submit feedback\".",
    "feedback_copy_recipient": "",
    "feedback_copied": "Recipient address copied to clipboard",
    "feedback_no_client": "No mail client found. Please choose a web client or send manually.",
    "update_new_version": "New version available",
    "update_changelog": "Changelog:",
    "update_download": "Download now",
    "update_force": "Force update",
    "update_downloading": "Downloading…",
    "update_verifying": "Verifying package…",
    "update_install": "Install now",
    "update_no_update": "Already up to date",
    "update_error": "Failed to check for updates",
    "update_network_error": "Network connection failed. Please check your network and retry.",
    "update_beta_only_title": "Only beta update available",
    "update_beta_only_enable": "Enable beta and re-check",
    "update_beta_only_wait": "Wait for stable release",
    "update_beta_only_toast": "The latest server version is a beta. Enable \"Accept beta builds\" to update.",
    "update_channel_label": "Channel: %s",
    "update_version_code": "Version code: %d",
    "update_last_stable_default": "Previous stable release",
    "update_beta_only_msg": "The latest server version is beta %1$s (version code %2$d).\n\nYou are currently on version code %3$d, the previous stable release is %4$s",
    "update_beta_only_stable_code": "(version code %d)",
    "update_beta_only_hint": " has fallen far behind.\n\nYou can either enable \"Accept beta builds\" to update now, or wait for the next stable release.",
    "update_auto_downloading": "Update package is downloading in the background",
    "update_auto_download_started": "New version found. Downloading package in background.",
    "update_auto_download_complete": "Update package downloaded. You can open the download directory in Settings.",
    "update_install_prompt": "Download complete. Install now?",
    "update_open_directory": "Open directory",
    "update_apk_lost": "Installation package lost. Please re-download.",
    "update_install_permission_needed": "Install permission is required to install updates. Please grant it in Settings.",
    "permission_dialog_title": "Permission usage",
    "permission_dialog_message": "Welcome to GameMatrix! To provide full functionality, we need the following permissions:\n\n• Location: for LAN game discovery and Wi-Fi address detection\n• Camera: for QR code scanning in Tools\n• Storage: for downloading and saving game update packages\n• Install unknown apps: for installing downloaded game updates\n\nYou can grant all permissions for the best experience, or grant them individually in system settings later.",
    "permission_grant_all": "Grant all",
    "permission_decline": "Not now",
    "permission_granted_toast": "Permissions granted. All features are available.",
    "permission_declined_toast": "Some features may be limited. You can grant permissions in Settings at any time.",
    "error_network_disconnected": "Network disconnected. Please check your network settings.",
    "error_dns_resolution": "Unable to resolve server address. Please check your network.",
    "error_server_5xx": "Internal server error. Please try again later.",
    "error_server_4xx": "Request rejected by server. Please check your input.",
    "error_io": "Network I/O error. Please retry.",
    "error_ssl": "SSL certificate verification failed. Please check network security.",
    "error_cancelled": "Request cancelled",
    "online_title": "Online Match",
    "online_create_room": "Create room",
    "online_join_room": "Join room",
    "online_input_message": "Type a message…",
    "online_send": "Send",
    "online_creating_cloud": "Creating cloud room…",
    "online_room_created": "Room created",
    "online_room_code_label": "Room code: ",
    "online_create_failed": "Failed to create room",
    "online_joining_room": "Joining room ",
    "online_waiting_opponent": "Waiting for opponent",
    "online_copy_room_code": "Copy room code",
    "online_room_code_copied": "Room code copied",
    "online_share_room_code_hint": "Share this room code with your opponent.\\n\\nThe game starts automatically once they join.",
    "online_cancel": "Cancel",
    "online_input_room_code_hint": "Enter the 6-digit room code",
    "online_join_room_title": "Join room",
    "online_join": "Join",
    "online_input_room_code_toast": "Please enter the 6-digit room code",
    "online_disconnected_title": "Disconnected",
    "online_waiting_reconnect": "Waiting to reconnect",
    "online_waiting_opponent_reconnect": "Waiting for opponent to reconnect…",
    "online_reconnect": "Reconnect",
    "online_reconnecting": "Reconnecting…",
    "online_reconnect_failed": "Unable to reconnect. Please rejoin.",
    "online_leave_room": "Leave room",
    "online_opponent_joined": "Opponent joined!",
    "online_opponent_disconnected": "Opponent disconnected: ",
    "online_connected_to_host": "Connected to host",
    "online_connection_lost": "Connection lost: ",
    "online_server_error": "Server error: ",
    "online_client_error": "Client error: ",
    "settings_title": "Settings",
    "settings_ok": "OK",
    "settings_cancel": "Cancel",
    "settings_save": "Save",
    "settings_current_version": "Current version: ",
    "settings_version_update": "Version update",
    "settings_select_color_scheme": "Choose color scheme",
    "settings_select_update_source": "Choose update source",
    "settings_source_auto": "Auto",
    "settings_source_auto_recommended": "Auto (recommended)",
    "settings_source_hk_vps": "Hong Kong VPS",
    "settings_source_us_vps": "US VPS (deprecated)",
    "settings_source_github": "GitHub Releases",
    "achievement_first_win_title": "First Win",
    "achievement_first_win_desc": "Win your first game",
    "achievement_win_streak_3_title": "3-Win Streak",
    "achievement_win_streak_3_desc": "Win 3 games in a row",
    "achievement_win_streak_5_title": "5-Win Streak",
    "achievement_win_streak_5_desc": "Win 5 games in a row",
    "achievement_win_streak_10_title": "10-Win Streak",
    "achievement_win_streak_10_desc": "Win 10 games in a row",
    "achievement_games_played_10_title": "Getting Started",
    "achievement_games_played_10_desc": "Complete 10 games",
    "achievement_games_played_50_title": "Veteran",
    "achievement_games_played_50_desc": "Complete 50 games",
    "achievement_games_played_100_title": "Seasoned Player",
    "achievement_games_played_100_desc": "Complete 100 games",
    "achievement_go_first_capture_title": "First Capture",
    "achievement_go_first_capture_desc": "Capture your first stone in Go",
    "achievement_chess_checkmate_title": "Checkmate!",
    "achievement_chess_checkmate_desc": "Deliver checkmate in Chinese Chess",
    "achievement_gomoku_perfect_title": "Perfect Game",
    "achievement_gomoku_perfect_desc": "Win a Gomoku game without losing a stone",
    "achievement_online_first_win_title": "Online First Win",
    "achievement_online_first_win_desc": "Win your first online game",
    "achievement_online_win_streak_3_title": "Online 3-Win Streak",
    "achievement_online_win_streak_3_desc": "Win 3 online games in a row",
    "achievement_ai_conversations_10_title": "AI New Friend",
    "achievement_ai_conversations_10_desc": "Chat with AI assistant 10 times",
    "achievement_ai_conversations_50_title": "AI Old Buddy",
    "achievement_ai_conversations_50_desc": "Chat with AI assistant 50 times",
    "achievement_daily_login_title": "Daily Check-in",
    "achievement_daily_login_desc": "Log in once a day",
    "achievement_daily_login_streak_7_title": "Persistence",
    "achievement_daily_login_streak_7_desc": "Log in for 7 consecutive days",
    "recovery_icon_desc": "Recovery mode icon",
    "recovery_title": "App Recovery Mode",
    "recovery_status_ready": "App issue detected. Download a stable build to repair.",
    "recovery_status_downloading": "Downloading stable build…",
    "recovery_status_download_complete": "Download complete. Tap install to repair.",
    "recovery_status_apk_ready": "A downloaded stable build is ready",
    "recovery_status_error": "Download failed",
    "recovery_status_cancelled": "Download cancelled",
    "recovery_btn_download": "Download stable build",
    "recovery_btn_install": "Install stable build",
    "recovery_btn_retry": "Retry download",
    "recovery_btn_cancel": "Cancel download",
    "recovery_hint": "This mode is triggered automatically when the app crashes repeatedly. Installing the stable build will replace the current version.",
    "recovery_source": "Download source",
    "recovery_install_failed": "Installation failed. Please retry.",
    "module_back": "Back",
    "module_refresh": "Refresh",
    "module_store_title": "Module Store",
    "module_empty": "No modules available",
    "module_icon_desc": "Module icon",
    "minesweeper": "Minesweeper",
    "minesweeper_desc": "Classic minesweeper. Long press to flag, short tap to reveal.",
    "minesweeper_title": "Minesweeper",
    "minesweeper_easy": "Easy 9×9",
    "minesweeper_medium": "Medium 16×16",
    "minesweeper_hard": "Hard 16×30",
    "minesweeper_reset": "Restart",
    "minesweeper_status": "Remaining: %1$d | Flags: %2$d",
    "minesweeper_win_title": "Congratulations",
    "minesweeper_win_msg": "You win!",
    "minesweeper_lose_title": "Game over",
    "minesweeper_lose_msg": "You hit a mine!",
    "minesweeper_play_again": "Play again",
    "module_close": "Close",
    "installed_modules_title": "Downloaded Modules",
    "installed_modules_empty": "No downloaded modules",
    "store_category_games": "Games",
    "store_category_browser": "Browser",
    "store_category_tools": "Tools",
    "store_category_ai": "AI Assistant",
    "store_category_vpn": "VPN",
    "store_base_framework": "Base Framework",
    "store_subcategory_puzzle": "Puzzle",
    "store_subcategory_casual": "Casual",
    "store_subcategory_classics": "Classics",
    "installed_update": "Update",
    "installed_uninstall": "Uninstall",
    "tool_icon_desc": "Tool icon",
    "navigate_next": "Next",
    "ai_typing": "AI is typing…",
    "sending": "Sending…",
    "icon_desc": "Icon",
    "dependencies": "Dependencies",
    "check_update": "Check for updates",
    "chinese_chess_red_player": "Red (You)",
    "chinese_chess_black_player": "Black (AI)",
    "chinese_chess_check_alert": "Check!",
    "chinese_chess_resign": "Resign",
    "chinese_chess_draw": "Draw",
    "chinese_chess_draw_offer": "Opponent offers a draw. Accept?",
    "chinese_chess_draw_accept": "Accept",
    "chinese_chess_draw_reject": "Decline",
    "chinese_chess_move_history": "Move history",
    "chinese_chess_no_moves": "No moves yet",
    "chinese_chess_game_over_title": "Game over",
    "chinese_chess_game_over_win": "Red wins",
    "chinese_chess_game_over_lose": "Black wins",
    "chinese_chess_game_over_draw": "Draw",
    "chinese_chess_stat_moves": "Total moves",
    "chinese_chess_stat_captures": "Captures",
    "chinese_chess_stat_duration": "Duration",
    "chinese_chess_stat_score": "Score",
    "chinese_chess_stat_red_captures": "Red captures",
    "chinese_chess_stat_black_captures": "Black captures",
    "chinese_chess_resign_confirm": "Are you sure you want to resign?",
    "chinese_chess_resigned": "You resigned",
    "chinese_chess_draw_confirm": "Draw offered. Waiting for opponent…",
    "chinese_chess_draw_rejected": "Opponent declined the draw",
    "chinese_chess_play_again": "Play again",
    "chinese_chess_back_home": "Back to menu",
    "chinese_chess_captured_label": "Captured",
    "chinese_chess_turn_red": "Red\'s turn",
    "chinese_chess_turn_black": "Black\'s turn",
    "chinese_chess_default_player_name": "Player",
}


def xml_escape(s: str) -> str:
    """转义 XML 字符（保留已存在的占位符）。"""
    # 不转义 &（占位符里没有 &），但需转义 < > &
    s = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    # 回退已转义的 &amp; 等
    s = s.replace("&amp;lt;", "&lt;").replace("&amp;gt;", "&gt;").replace("&amp;amp;", "&amp;")
    return s


def append_translations(target_path: Path, existing_keys: set, translations: dict, header_comment: str):
    """在 </resources> 之前追加缺失的翻译。保留原文件行尾（LF/CRLF）。"""
    missing = {k: v for k, v in translations.items() if k not in existing_keys}
    if not missing:
        print(f"  [{header_comment}] 无需追加")
        return 0
    # 用 newline='' 读取以保留原始行尾
    with open(target_path, "r", encoding="utf-8", newline="") as f:
        content = f.read()
    # 检测行尾
    if "\r\n" in content:
        nl = "\r\n"
    else:
        nl = "\n"
    lines = []
    for key in sorted(missing.keys()):
        val = xml_escape(missing[key])
        lines.append(f'    <string name="{key}">{val}</string>')
    block = f'{nl}    <!-- {header_comment}: auto-supplemented by scripts/sync_i18n.py -->{nl}' + nl.join(lines) + nl
    new_content = content.replace("</resources>", block + "</resources>")
    # 用 newline="" 写入，不进行行尾转换
    with open(target_path, "w", encoding="utf-8", newline="") as f:
        f.write(new_content)
    print(f"  [{header_comment}] 追加 {len(missing)} 条翻译到 {target_path.name}")
    return len(missing)


def main():
    zh_keys = extract_keys(APP_ZH)
    en_keys = extract_keys(APP_EN)
    print(f"现状：中文 {len(zh_keys)} 条 / 英文 {len(en_keys)} 条")

    print("\n[1/2] 补全 app 模块英文缺失（中文→英文）：")
    n_en = append_translations(APP_EN, en_keys, ZH_TO_EN, "i18n sync: zh→en")

    # 重新读取 en keys
    en_keys = extract_keys(APP_EN)
    print(f"\n[2/2] 补全 app 模块中文缺失（英文→中文）：")
    # EN_TO_ZH 由 _gen_en_to_zh.py 生成的字典
    from _en_to_zh_dict import EN_TO_ZH
    n_zh = append_translations(APP_ZH, zh_keys, EN_TO_ZH, "i18n sync: en→zh")

    # 重新统计
    zh_keys2 = extract_keys(APP_ZH)
    en_keys2 = extract_keys(APP_EN)
    print(f"\n结果：中文 {len(zh_keys)} → {len(zh_keys2)} 条 / 英文 {len(en_keys)} → {len(en_keys2)} 条")
    print(f"本次补全：中→英 {n_en} 条 / 英→中 {n_zh} 条")
    still_missing_en = zh_keys2 - en_keys2
    still_missing_zh = en_keys2 - zh_keys2
    if still_missing_en:
        print(f"⚠ 仍有 {len(still_missing_en)} 条中文缺英文：{sorted(still_missing_en)[:10]}…")
    if still_missing_zh:
        print(f"⚠ 仍有 {len(still_missing_zh)} 条英文缺中文：{sorted(still_missing_zh)[:10]}…")
    if not still_missing_en and not still_missing_zh:
        print("✓ app 模块中英 key 完全对齐")


if __name__ == "__main__":
    main()
