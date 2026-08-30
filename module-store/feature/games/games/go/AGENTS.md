# 围棋：规则、AI 与实机验收

运行时真源：`module-store/feature/games/games/go/src/main/`。宿主侧副本（`GoGame`/`GoAI`/`GoView`/`GoUiPreferences`）已删除，禁止向宿主拷回或只修一条启动路径。

## 规则与 AI 不变量

- 所有难度和实战必须共用同一个规则提交入口：落子模拟必须包含提子、自杀禁着、简单劫、坐标边界和轮次校验；搜索只能读取棋盘快照，禁止原地修改实战棋盘或 MCTS 树节点。UI 必须把 AI 返回值视为不可信输入，提交拒绝时记录 `GO_AI_CONTRACT_VIOLATION`，不得伪装成停一手。
- AI 只有在没有合法着法或满足可解释且已测试的终局停着策略时才可返回停一手；禁止用固定随机概率停着。普通（中等）难度至少应识别提子、救一气棋、避免自打吃、连接/切断与角边效率，困难档不得仅靠增加损坏算法的墙钟时间区分棋力。
- 9 路围棋统一采用中国面积计分：黑白各自的盘上棋子加仅被该方包围的空点，白方贴目保留 `6.5`；提子数只用于对局信息展示，不得在面积分中重复加入，胜负、UI、回放和测试必须读取同一个 `Score` 结果。
- 大厅的 `game_difficulty_index` 只可预选难度，默认无推荐时选择普通（2/4），难度面板必须保持可见且不得自动开局。默认使用增强棋盘，并以 `game_go_ui/board_style_v1` 持久化简洁模式。
- AI 思考期间玩家不得停一手或重复提交；返回菜单、重开、结束和销毁必须取消搜索并递增对局代次，回写前再次校验代次、白方回合和游戏未结束。程序化创建的动态模块 `Button` 必须调用 `setStateListAnimator(null)`，避免宿主主题资源 ID 冲突。

## 回归与验收

- 围棋专项回归优先运行 `python scripts/verify_go.py`。规则测试至少覆盖提子、自杀、越界、即时劫、隔手可回、两次停着和面积计分；AI 测试必须覆盖搜索不改输入、四档原始非法着法为 0、普通档无随机停着和旧回调隔离。
- 棋力或完整对局验收记录 `difficulty`、真实策略/预算、总 ply、`undoCount=0`、`restartCount`、raw illegal、rejected commit 与 fallback；围棋没有悔棋按钮，不得通过重开筛选有利对局。
- 模拟器自动验收可通过 `app/src/debug/AndroidManifest.xml` 直接启动围棋；该入口只能存在于 Debug source set，Release 的 `GoActivity` 必须保持 `android:exported="false"`（机器检查：`scripts/verify_security_clauses.py`）。
