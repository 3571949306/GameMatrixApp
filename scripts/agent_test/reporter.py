"""
测试报告生成器。

功能：
- 管理截图文件命名与存储
- 记录每步操作（Activity、操作描述、截图路径、崩溃日志）
- 生成 HTML 报告（含操作时间线、截图缩略图、问题列表）
- 生成 JSON 摘要（供 CI 集成）

输出目录结构：
    reports/
    └── {timestamp}/
        ├── report.html      # 可视化报告
        ├── summary.json     # 机器可读摘要
        └── screenshots/
            ├── step_001.png
            ├── step_002.png
            └── ...
"""
from __future__ import annotations

import html
import json
import logging
import os
import time
from dataclasses import asdict
from typing import List, Optional

from explorer import StepRecord

logger = logging.getLogger(__name__)


class Reporter:
    """报告生成器。

    用法：
        reporter = Reporter(output_dir="reports")
        reporter.start()
        # ... 记录步骤 ...
        reporter.finalize(...)
    """

    def __init__(self, output_dir: str = "reports"):
        self.base_dir = output_dir
        # 每次运行用时间戳建子目录，避免覆盖历史报告
        self.run_dir = os.path.join(
            output_dir, time.strftime("%Y%m%d_%H%M%S")
        )
        self.screenshot_dir = os.path.join(self.run_dir, "screenshots")
        os.makedirs(self.screenshot_dir, exist_ok=True)

        self.steps: List[StepRecord] = []
        self.crashes: List[StepRecord] = []  # 崩溃记录单独存放
        self.start_time = 0.0

    def start(self) -> None:
        """标记测试开始。"""
        self.start_time = time.time()
        logger.info("报告目录: %s", self.run_dir)

    def screenshot_path(self, step: int) -> str:
        """生成第 N 步的截图文件路径。"""
        return os.path.join(self.screenshot_dir, f"step_{step:03d}.png")

    def record_step(self, record: StepRecord) -> None:
        """记录一步操作。"""
        self.steps.append(record)
        if record.crash_log:
            self.crashes.append(record)
        logger.info(
            "[step %03d] %s | %s",
            record.step, record.activity, record.action_desc,
        )

    def finalize(
        self,
        total_steps: int,
        visited_states: int,
        acted_elements: int,
        duration: float,
    ) -> None:
        """生成最终报告（HTML + JSON）。"""
        summary = {
            "run_dir": self.run_dir,
            "start_time": self.start_time,
            "duration_seconds": round(duration, 1),
            "total_steps": total_steps,
            "visited_states": visited_states,
            "acted_elements": acted_elements,
            "crash_count": len(self.crashes),
            "crashes": [
                {
                    "step": c.step,
                    "activity": c.activity,
                    "crash_log": c.crash_log,
                    "screenshot": self._rel_screenshot(c.screenshot_path),
                }
                for c in self.crashes
            ],
            "steps": [asdict(s) for s in self.steps],
        }

        # JSON 摘要
        json_path = os.path.join(self.run_dir, "summary.json")
        with open(json_path, "w", encoding="utf-8") as f:
            json.dump(summary, f, ensure_ascii=False, indent=2)
        logger.info("JSON 摘要: %s", json_path)

        # HTML 报告
        html_path = self._generate_html(summary)
        logger.info("HTML 报告: %s", html_path)

        # 控制台打印摘要
        print("\n" + "=" * 60)
        print(f"探索完成 - 报告目录: {self.run_dir}")
        print(f"  总步数: {total_steps}")
        print(f"  访问状态数: {visited_states}")
        print(f"  操作元素数: {acted_elements}")
        print(f"  时长: {duration:.1f}s")
        print(f"  崩溃数: {len(self.crashes)}")
        print(f"  HTML 报告: {html_path}")
        print("=" * 60 + "\n")

    def _rel_screenshot(self, abs_path: Optional[str]) -> str:
        """返回相对于 run_dir 的截图路径（用于 HTML 引用）。"""
        if not abs_path:
            return ""
        try:
            return os.path.relpath(abs_path, self.run_dir)
        except ValueError:
            return abs_path

    def _generate_html(self, summary: dict) -> str:
        """生成 HTML 报告。"""
        html_path = os.path.join(self.run_dir, "report.html")

        crash_section = self._html_crash_section(summary["crashes"])
        timeline = self._html_timeline(summary["steps"])

        crash_badge = (
            f'<span class="badge crash">{summary["crash_count"]} 崩溃</span>'
            if summary["crash_count"] > 0
            else '<span class="badge ok">无崩溃</span>'
        )

        html_content = f"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<title>智能体探索报告 - {time.strftime("%Y-%m-%d %H:%M:%S")}</title>
<style>
  body {{ font-family: -apple-system, "Segoe UI", Roboto, sans-serif; margin: 24px; background: #f5f5f5; }}
  h1 {{ color: #333; }}
  .summary {{ display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin: 20px 0; }}
  .card {{ background: white; padding: 16px; border-radius: 8px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }}
  .card .label {{ color: #666; font-size: 13px; }}
  .card .value {{ font-size: 24px; font-weight: 600; color: #1a73e8; }}
  .badge {{ display: inline-block; padding: 4px 10px; border-radius: 12px; font-size: 13px; font-weight: 600; }}
  .badge.ok {{ background: #e6f4ea; color: #137333; }}
  .badge.crash {{ background: #fce8e6; color: #c5221f; }}
  .crash-list {{ background: #fce8e6; border-left: 4px solid #c5221f; padding: 12px; margin: 12px 0; border-radius: 4px; }}
  .crash-list pre {{ background: #fff; padding: 8px; overflow-x: auto; font-size: 12px; border-radius: 4px; }}
  .timeline {{ margin-top: 24px; }}
  .step {{ display: flex; gap: 12px; padding: 8px 0; border-bottom: 1px solid #eee; }}
  .step img {{ width: 120px; height: 213px; object-fit: cover; border-radius: 4px; cursor: pointer; border: 1px solid #ddd; }}
  .step .info {{ flex: 1; }}
  .step .step-num {{ font-weight: 600; color: #1a73e8; }}
  .step .activity {{ color: #666; font-size: 13px; }}
  .step .action {{ color: #333; margin-top: 4px; }}
  .step .new-state {{ color: #137333; font-size: 12px; }}
  img.full {{ max-width: 100%; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.2); margin: 20px 0; }}
</style>
</head>
<body>
<h1>智能体探索报告</h1>
<div class="summary">
  <div class="card"><div class="label">总步数</div><div class="value">{summary["total_steps"]}</div></div>
  <div class="card"><div class="label">访问状态</div><div class="value">{summary["visited_states"]}</div></div>
  <div class="card"><div class="label">操作元素</div><div class="value">{summary["acted_elements"]}</div></div>
  <div class="card"><div class="label">时长 (秒)</div><div class="value">{summary["duration_seconds"]}</div></div>
</div>
<p>{crash_badge}</p>
{crash_section}
<h2>操作时间线</h2>
<div class="timeline">
{timeline}
</div>
<script>
document.querySelectorAll('.step img').forEach(img => {{
  img.addEventListener('click', () => {{
    const full = document.createElement('img');
    full.src = img.src;
    full.className = 'full';
    full.onclick = () => full.remove();
    document.body.appendChild(full);
  }});
}});
</script>
</body>
</html>"""

        with open(html_path, "w", encoding="utf-8") as f:
            f.write(html_content)
        return html_path

    def _html_crash_section(self, crashes: list) -> str:
        if not crashes:
            return ""
        items = []
        for c in crashes:
            log = html.escape(c.get("crash_log", "") or "")
            items.append(
                f'<div class="crash-list"><strong>Step {c["step"]} - {html.escape(c["activity"])}</strong>'
                f"<pre>{log}</pre></div>"
            )
        return f'<h2>崩溃记录</h2>{"".join(items)}'

    def _html_timeline(self, steps: list) -> str:
        if not steps:
            return "<p>无操作记录</p>"
        rows = []
        for s in steps:
            shot = s.get("screenshot_path") or ""
            rel_shot = self._rel_screenshot(shot) if shot else ""
            img_tag = (
                f'<img src="{html.escape(rel_shot)}" alt="step {s["step"]}">'
                if rel_shot and os.path.exists(os.path.join(self.run_dir, rel_shot))
                else '<div style="width:120px;height:213px;background:#eee;display:flex;align-items:center;justify-content:center;color:#999;border-radius:4px;">无截图</div>'
            )
            new_badge = '<span class="new-state">● 新状态</span>' if s.get("is_new_state") else ""
            rows.append(
                f'<div class="step">'
                f"{img_tag}"
                f'<div class="info">'
                f'<div><span class="step-num">Step {s["step"]}</span> {new_badge}</div>'
                f'<div class="activity">{html.escape(s.get("activity", ""))}</div>'
                f'<div class="action">{html.escape(s.get("action_desc", ""))}</div>'
                f"</div></div>"
            )
        return "".join(rows)
