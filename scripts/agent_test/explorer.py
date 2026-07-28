"""
自主探索智能体。

核心算法：深度优先 + 状态记忆 + 回溯。

工作流程：
1. 感知当前页面状态（Activity + 可交互元素）
2. 检测崩溃 → 记录并终止
3. 检测对话框 → 自动关闭
4. 判断是否新状态 → 更新状态记忆
5. 选择下一个操作（优先未访问的元素）
6. 执行操作 → 记录步骤
7. 若当前页面无可操作元素 → 返回键回溯

终止条件：最大步数 / 最大时长 / 连续无新发现 / 应用崩溃
"""
from __future__ import annotations

import logging
import time
from dataclasses import dataclass
from typing import List, Optional

from appium_driver import AppiumDriver, AppState, ElementInfo

logger = logging.getLogger(__name__)


@dataclass
class StepRecord:
    """单步操作记录，供报告器使用。"""

    step: int
    timestamp: float
    activity: str
    action_desc: str
    element_sig: str
    is_new_state: bool
    screenshot_path: Optional[str]
    crash_log: Optional[str] = None


class Explorer:
    """自主探索智能体。

    用法：
        explorer = Explorer(driver, reporter, config)
        explorer.explore()
    """

    # 输入框类名特征
    EDIT_TEXT_CLASS = "EditText"

    def __init__(self, driver: AppiumDriver, reporter, config: dict):
        self.driver = driver
        self.reporter = reporter
        self.config = config["explore"]
        self.test_inputs = self.config.get("test_input_texts", {})

        self.visited_states: set[str] = set()
        # 已操作元素签名（全局，防止重复点击同一元素）
        self.acted_elements: set[str] = set()
        self.action_stack: List[str] = []  # 已执行操作描述栈，用于回溯

        self.step = 0
        self.start_time = 0.0
        self.no_new_state_count = 0
        self.backtrack_depth = 0

    def explore(self) -> None:
        """主探索循环。"""
        self.start_time = time.time()
        self.driver.clear_crash_buffer()
        max_steps = self.config.get("max_steps", 200)
        max_duration = self.config.get("max_duration_seconds", 1800)
        max_no_new = self.config.get("max_no_new_state_count", 15)

        logger.info(
            "开始自主探索：max_steps=%d max_duration=%ds", max_steps, max_duration
        )

        while self._should_continue(max_steps, max_duration):
            self.step += 1
            screenshot_path = self.reporter.screenshot_path(self.step)
            state = self.driver.get_state(screenshot_path=screenshot_path)

            # 1. 崩溃检测（最高优先级）
            crash = self.driver.check_crash()
            if crash:
                logger.error("检测到崩溃！step=%d\n%s", self.step, crash)
                self.reporter.record_step(
                    StepRecord(
                        step=self.step,
                        timestamp=time.time(),
                        activity=state.activity,
                        action_desc="CRASH_DETECTED",
                        element_sig="",
                        is_new_state=False,
                        screenshot_path=screenshot_path,
                        crash_log=crash,
                    )
                )
                break

            # 2. 对话框处理
            if self.driver.find_and_dismiss_dialog():
                self.reporter.record_step(
                    StepRecord(
                        step=self.step,
                        timestamp=time.time(),
                        activity=state.activity,
                        action_desc="dismiss_dialog",
                        element_sig="",
                        is_new_state=False,
                        screenshot_path=screenshot_path,
                    )
                )
                time.sleep(0.5)
                continue

            # 3. 状态判断
            fp = state.fingerprint()
            is_new_state = fp not in self.visited_states
            self.visited_states.add(fp)
            if is_new_state:
                self.no_new_state_count = 0
                logger.info(
                    "新状态 step=%d activity=%s 元素数=%d",
                    self.step, state.activity, len(state.elements),
                )
            else:
                self.no_new_state_count += 1
                logger.debug(
                    "已访问状态 step=%d no_new=%d", self.step, self.no_new_state_count
                )

            # 4. 连续无新发现过多 → 终止
            if self.no_new_state_count >= max_no_new:
                logger.info("连续 %d 步无新发现，终止探索", self.no_new_state_count)
                self.reporter.record_step(
                    StepRecord(
                        step=self.step,
                        timestamp=time.time(),
                        activity=state.activity,
                        action_desc="STOP_NO_NEW_STATE",
                        element_sig="",
                        is_new_state=False,
                        screenshot_path=screenshot_path,
                    )
                )
                break

            # 5. 选择操作
            action = self._select_action(state)

            if action is None:
                # 无可操作元素，尝试滑动看是否有更多内容
                if self._should_try_swipe(state):
                    self.driver.swipe_up()
                    self.reporter.record_step(
                        StepRecord(
                            step=self.step,
                            timestamp=time.time(),
                            activity=state.activity,
                            action_desc="swipe_up",
                            element_sig="",
                            is_new_state=is_new_state,
                            screenshot_path=screenshot_path,
                        )
                    )
                    time.sleep(1)
                    continue

                # 滑动无效 → 回溯
                if self.backtrack_depth < self.config.get("max_backtrack_depth", 8):
                    if self.driver.back():
                        self.backtrack_depth += 1
                        self.reporter.record_step(
                            StepRecord(
                                step=self.step,
                                timestamp=time.time(),
                                activity=state.activity,
                                action_desc=f"back (depth={self.backtrack_depth})",
                                element_sig="",
                                is_new_state=is_new_state,
                                screenshot_path=screenshot_path,
                            )
                        )
                        time.sleep(1)
                        continue
                # 回溯也失败 → 终止
                logger.info("无法回溯，终止探索")
                self.reporter.record_step(
                    StepRecord(
                        step=self.step,
                        timestamp=time.time(),
                        activity=state.activity,
                        action_desc="STOP_CANNOT_BACKTRACK",
                        element_sig="",
                        is_new_state=is_new_state,
                        screenshot_path=screenshot_path,
                    )
                )
                break

            # 6. 执行操作
            self._execute_action(action)
            self.acted_elements.add(action.signature)
            self.action_stack.append(action.signature)
            self.backtrack_depth = 0  # 成功操作后重置回溯深度

            self.reporter.record_step(
                StepRecord(
                    step=self.step,
                    timestamp=time.time(),
                    activity=state.activity,
                    action_desc=action.action_desc,
                    element_sig=action.signature,
                    is_new_state=is_new_state,
                    screenshot_path=screenshot_path,
                )
            )

            # 7. 等待页面稳定（动画、加载）
            time.sleep(self.config.get("step_delay_seconds", 1.0))

        self.reporter.finalize(
            total_steps=self.step,
            visited_states=len(self.visited_states),
            acted_elements=len(self.acted_elements),
            duration=time.time() - self.start_time,
        )
        logger.info(
            "探索完成：steps=%d states=%d elements=%d duration=%.1fs",
            self.step,
            len(self.visited_states),
            len(self.acted_elements),
            time.time() - self.start_time,
        )

    def _should_continue(self, max_steps: int, max_duration: int) -> bool:
        if self.step >= max_steps:
            logger.info("达到最大步数 %d，终止", max_steps)
            return False
        if time.time() - self.start_time >= max_duration:
            logger.info("达到最大时长 %ds，终止", max_duration)
            return False
        return True

    def _select_action(self, state: AppState) -> Optional[_Action]:
        """从当前状态选择下一个操作。

        策略：
        1. 优先操作未访问的可点击元素
        2. 输入框 → 输入测试数据
        3. 已访问元素 → 跳过（除非需要重新触发）
        4. 无标识元素 → 用 bounds 兜底签名，仍然可被操作
        """
        candidates: List[ElementInfo] = []
        edit_text_candidates: List[ElementInfo] = []

        for el in state.elements:
            if not el.enabled:
                continue
            sig = el.signature
            if not sig:
                # 完全无标识且无 bounds 的元素才跳过（理论上不会出现）
                continue
            if sig in self.acted_elements:
                continue

            # 区分输入框和普通可点击元素
            if self.EDIT_TEXT_CLASS in el.class_name:
                edit_text_candidates.append(el)
            elif el.clickable:
                candidates.append(el)

        # 优先处理输入框（输入测试数据后标记为已访问）
        if edit_text_candidates:
            el = edit_text_candidates[0]
            text = self._pick_input_text(el)
            return _Action(
                element=el,
                action_desc=f"input {text!r} into {el.signature}",
                action_type="input",
                text=text,
            )

        if not candidates:
            return None

        # 按优先级排序可点击元素：
        # 0) 有明显"开始/进入/确认"语义的文本优先（促进探索推进）
        # 1) 有 resource-id（最稳定）
        # 2) 有 content-desc
        # 3) 有 text
        # 4) 仅 bounds 兜底
        high_priority_keywords = (
            "开始", "进入", "确认", "确定", "继续", "下一步", "next", "start",
            "play", "begin", "ok", "yes", "确认", "进入游戏", "开始游戏",
        )

        def priority_key(e: ElementInfo):
            text_lower = (e.text or "").lower()
            is_high = any(kw in text_lower for kw in high_priority_keywords)
            if is_high:
                prio = -1
            elif e.resource_id:
                prio = 0
            elif e.content_desc:
                prio = 1
            elif e.text:
                prio = 2
            else:
                prio = 3
            return (prio, e.signature)

        candidates.sort(key=priority_key)

        el = candidates[0]
        return _Action(
            element=el,
            action_desc=f"tap {el.signature} text={el.text!r}",
            action_type="tap",
        )

    def _pick_input_text(self, el: ElementInfo) -> str:
        """根据输入框特征选择测试文本。"""
        sig = el.signature.lower()
        text_attr = (el.text + el.content_desc).lower()
        if "search" in sig or "搜索" in text_attr:
            return self.test_inputs.get("search", "中国象棋")
        if "chat" in sig or "输入" in text_attr or "消息" in text_attr:
            return self.test_inputs.get("ai_chat", "你好")
        return self.test_inputs.get("default", "测试输入")

    def _execute_action(self, action: "_Action") -> None:
        """执行单个操作。"""
        if action.action_type == "tap":
            self.driver.tap_element(action.element)
        elif action.action_type == "input":
            self.driver.input_text(action.element, action.text or "")

    def _should_try_swipe(self, state: AppState) -> bool:
        """判断是否值得尝试滑动。

        简单策略：当前页面有元素但全部已访问，尝试滑动一次看是否有新内容。
        通过限制滑动频率避免无限滑动。
        """
        if not state.elements:
            return False
        # 所有元素都已访问过才滑动
        return all(
            e.signature in self.acted_elements or not e.signature
            for e in state.elements
        )


@dataclass
class _Action:
    """内部操作描述。"""

    element: ElementInfo
    action_desc: str
    action_type: str  # "tap" | "input"
    text: Optional[str] = None
    signature: str = ""

    def __post_init__(self):
        if not self.signature:
            self.signature = self.element.signature
