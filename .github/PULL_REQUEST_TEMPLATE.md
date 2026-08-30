## Description / 描述
<!-- What does this PR do? / 这个 PR 做了什么？ -->

## Related Issues / 关联 Issue
<!-- Link related issues: Fixes #123 / 关联 Issue：Fixes #123 -->

## Changes / 变更内容
- 

## Testing / 测试
- [ ] Unit tests pass / 单元测试通过
- [ ] UI tests pass / UI 测试通过
- [ ] Manual testing completed / 手动测试完成

## Screenshots / 截图
<!-- If UI changes, add screenshots / 如果有 UI 变更，请添加截图 -->

## Checklist / 检查清单
- [ ] Code follows project style / 代码符合项目规范
- [ ] Self-review completed / 已完成自查
- [ ] Documentation updated / 已更新文档（如需要）

## 规范检查单（AGENTS.md，必选）
<!-- 勾选前实际运行，不要凭印象勾选。全绿 CI 不免除本地检查。 -->
- [ ] 修 bug / 改行为处已加回归测试（修复前失败、修复后通过）
- [ ] 相关 verify 脚本已本地跑过并通过：`verify_agent_contract.py` + 本次改动对应域脚本（象棋 `verify_chinese_chess.py` / 围棋 `verify_go.py` / 隔离 `verify_isolation.py` / 安全 `verify_security_clauses.py` …）
- [ ] `python scripts/verify_ratchet.py` 通过（未引入新的空 catch / boolean flag / SOFT 项）
- [ ] 未触碰受保护发布资产（catalog.json / modules.json / modules/*.apk / version.properties），或任务确属发布
- [ ] scoped `git diff --check` 通过；工作区原有改动未混入本 PR
