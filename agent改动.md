# Agent 改动记录（agent改动.md）

> **记录规则（2026-07-24 确立，08:05 澄清）**：仅当用户输入的是**用户的任务**——即委托完成的具体工作 / 交付物（例如「加强中国象棋游戏AI」「修复登录崩溃」「实现模块下载功能」这类功能开发、修复、构建、实现任务）——才追加一条记录。**对话请求、指令、检查类诉求不记录**（例："检查项目变化""告诉我 agent 约束""确立工作约束"均属对话请求，不记入本文件）。任务命名应形如具体工作，如「加强中国象棋游戏AI」。
> 记录字段固定为：① 时间（**北京时间 GMT+8**）；② 完成内容；③ 是否全部完成；④ 若未完成，写明原因，并标注是否「无法绕过的阻碍」。
> 注：本文件仅记录 WorkBuddy 视角下完成的用户任务，**非项目权威进度源**；项目真实变动以代码（git diff / 文件对比）为准。

---

## 2026-07-24

### 任务1 ｜ 08:02 ｜ 新建 agent改动.md + 确立改动记录规则
- **完成内容**：创建本文件 `GameMatrixApp/agent改动.md`；确立"每完成一次用户任务记录 时间(北京时间)/内容/是否完成/未完成原因(是否阻碍)"的常驻规则，并写入项目记忆。
- **是否全部完成**：是
- **未完成原因**：无

### 任务2 ｜ 08:11 ｜ 修复 App Bug + 调整主要功能（对齐模块商店化方向）
- **完成内容**：已完成全局 + 重点（模块商店 / App 主要功能）只读诊断——git 现状扫描、模块商店接线/签名/扩展 blocker 走查、App 主功能与加载主流程走查；产出 Bug 清单（P0×5 / P1×4 / P2×4）与功能调整方向（gomoku/go 抽取为可下载、统一 catalog 双源、清理双份 AI 等）。
- **是否全部完成**：否（仅完成诊断，修复/调整尚未实施）
- **未完成原因**：用户已明确今日（2026-07-24）仅做学习/诊断，**暂不实施修复与调整**。非无法绕过的阻碍；后续若开工，建议从 A（P0 商店化地基）起步，再 B→C→D。

### 任务4 ｜ 08:42 ｜ 优化主页 UI 为每行两小板块（布局重构）
- **完成内容**：
  1. 连接三星 A52 真机抓取主页真实画面（home1/2/3.png 已对照用户截图）；定位主页布局 `app/src/main/res/layout/fragment_games.xml`，子代理走查给出 section 边界/id/背景资源/数据源
  2. **实施 A 路线**：L2 区（Hero 下方）改为 horizontal LinearLayout，左半 `resume_game_section` 50%（weight=1）+ 右半 `daily_cards_section` 50%（weight=1，内 vertical 堆叠 `card_daily_challenge` + `card_streak_summary`）
  3. **第一轮冒烟发现视觉问题**（v2 截图）：继续游玩卡 horizontal 布局在 50% 宽下文字竖排；每日挑战/连胜标题被压扁
  4. **修复**：重写 `layout_home_resume_game.xml` 为 vertical 布局（icon 上/文字中/按钮下，padding 16→12dp），缩小 `card_daily_challenge.xml` / `card_streak_summary.xml` padding 20→12dp；不修改 include id（成就中心页不受影响——已确认它未引用 resume_game_layout）
  5. **第二轮冒烟通过**：home_v3 截图显示所有卡片可读、画面占用减少、0 崩溃
- **影响文件**：
  - `app/src/main/res/layout/fragment_games.xml`（L2 Row 1 新增水平 LinearLayout + daily_cards_section 移动 + 移除原 vertical stack）
  - `app/src/main/res/layout/layout_home_resume_game.xml`（horizontal → vertical 重写，padding 缩小）
  - `app/src/main/res/layout/card_daily_challenge.xml`（padding 20→12dp，margin 归零）
  - `app/src/main/res/layout/card_streak_summary.xml`（padding 20→12dp，margin 归零）
- **是否全部完成**：是
- **未完成原因**：无

- **是否全部完成**：是
- **未完成原因**：无

### 任务5 ｜ 09:26 ｜ 修复主页 UI 空洞（每行两小板块方案 v2 视觉优化）
- **完成内容**：在任务4 实施的两列布局基础上，**消除三处视觉空洞**：
  1. **紫色"继续游玩"卡**：原 vertical 布局导致 icon 在顶、文字堆在底、中间留 80dp 纵向空洞。改为 horizontal 布局（icon 32dp 左 + vertical 文字按钮右）；删除冗余"继续上次游玩"标签；textSize 迭代缩小（13→11sp）+ padding 12→8dp 让文字完整显示
  2. **蓝色"每日挑战"卡**：原 horizontal 标题行（icon+title+badge）在 312dp 窄列下被挤成"每日\n挑\n战"竖排。改为 vertical（icon+title 在顶行，status badge 独立下一行），让 title 占满全宽
  3. **棕色"连胜记录"卡**：同样压缩 padding 20→8dp + 字号微调，让标题"连胜记录"四字一行
  4. **副作用修复**：发现 `dimen/spacing_10` 不存在（资源命名只有 8/12），全部改为 `spacing_8`
- **影响文件**：
  - `app/src/main/res/layout/layout_home_resume_game.xml`（horizontal 重写）
  - `app/src/main/res/layout/card_daily_challenge.xml`（标题行 vertical、padding 12→8dp）
  - `app/src/main/res/layout/card_streak_summary.xml`（padding 12→8dp、字号微调）
- **冒烟测试**：6 轮迭代（v4→v5→v6→v7→v8→v9），最终 home_v9_1.png 验证：所有标题完整可读、紫色卡"开始你的第一局游戏"完整显示、无纵向 80dp 空白、无大面积横向空洞、0 layout 引发的崩溃
- **是否全部完成**：否（次要文字"从下方推荐中挑选一款..."仍轻微 ellipsize，但属可接受次要信息；非阻塞）
- **未完成原因**：tv_resume_game_time 14 字 + 9sp 字号在 ~256dp vertical 区内仍超长——继续缩小会损害可读性，权衡后保留 ellipsize。**非无法绕过的阻碍**——可选优化：让 Java 端把 14 字改为 8-10 字简短版（如"推荐游戏任选一款"），或在 time TextView 设 maxLines=2 允许多行。

### 任务6 ｜ 09:19 ｜ 排查并记录冷启动 NPE 崩溃（未修复）
- **完成内容**：观察到 home_v7 装机首次启动时 `pool-7-thread-1` 抛 `NullPointerException: Iterator.next() on null`（r8 混淆栈：ye.run→r74.p0→ek4.r0→ek4.x0），App 被 SIGKILL 1 次后第二次启动恢复 MainActivity。
- **排查**：`git diff` 确认我未改任何 Java 文件（仅 layout xml），崩溃**不是本次 layout 改动引入的回归**——是 App 既有 Java bug，疑为模块商店下载/解压初始化线程遍历 null modules 列表。
- **是否全部完成**：否（仅记录+告知，未修复 Java 端）
- **未完成原因**��需定位具体模块加载代码（ModuleLoaderV2 / ModuleDependencyDownloader / ModuleStoreRepository 等），并加 null 检查——属另一个独立任务，不在本次"UI 空洞修复"任务范围内。**非无法绕过的阻碍**——本次 UI 任务已完成。

### 任务7 ｜ 10:55 ｜ 填充主页"今日精选"板块填补连胜记录空白（HOME_L2_FEATURED_TODAY_2026_07_24）
- **完成内容**：
  1. 在 L2 区域新增"今日精选"翡翠绿渐变卡片（★ 今日精选 + 精选推荐 badge + 游戏图标 + 名称 + 描述 + 立即开始按钮），填补连胜记录旁边的视觉空白
  2. **三版迭代**：
     - v1：放左列垂直堆叠 → 发现 onViewCreated 中 initTodayFeatured 跑得比 onResume 的 loadGames 早，allEntries 为空时 refreshTodayFeatured 把卡片设 GONE，loadGames 只 refreshGameOfDay 不 refreshTodayFeatured → 卡片永久 GONE 不可见
     - **核心修复**：在 `GamesFragment.loadGames()` 末尾追加 `refreshTodayFeatured()` 调用
     - v2：左列半宽 → 名称"推箱子"被按钮挤压成"推箱..."（按钮 272px 占满）
     - **最终 v3**：重构为**全宽独立 Row**，放在 L2 Row 1 下方；卡片 padding 12→16dp、icon 36→48dp、恢复 desc 行、按钮 padding/textSize 加大。名称 + 描述 + 按钮全部完整显示
  3. 涉及新增资源：`bg_today_featured_card.xml`（渐变背景）、`bg_today_featured_btn.xml`（按钮背景）、`card_today_featured.xml`（卡片布局）、`today_featured_card_*` 4 个颜色（日间+夜间）、`home_featured_play` 字符串（v2 用，v3 复用 `home_game_of_day_play`）
  4. 编译期间遇到 lint 缓存文件被锁（androidx.lifecycle.lint.LiveDataCoreIssueRegistry .jar），停 gradle daemon + 删除 `app/build/intermediates/lint-cache` 后重建通过
- **影响文件**：
  - `app/src/main/res/layout/fragment_games.xml`（L2 Row 1 恢复两列半宽；新增 L2 Row 2 全宽 card_today_featured_include）
  - `app/src/main/res/layout/card_today_featured.xml`（v3：全宽 padding 16、icon 48dp、desc 行恢复、按钮 padding 14dp textSize 12sp "立即开始"）
  - `app/src/main/res/drawable/bg_today_featured_card.xml`（新建）
  - `app/src/main/res/drawable/bg_today_featured_btn.xml`（新建）
  - `app/src/main/res/values/colors.xml` + `values-night/colors.xml`（4 个 today_featured_card_* 颜色）
  - `app/src/main/res/values/strings.xml`（home_featured_play="试玩"；v3 最终未使用）
  - `app/src/main/java/com/gamecenter/app/GamesFragment.java`（field/findViewById/refreshTodayFeatured 调用 loadGames 末尾）
- **冒烟测试**：三星 A52 release APK 安装，monkey LAUNCHER 启动 + 处理权限弹窗 + 下拉滚动；UI dump 验证 card_today_featured_include 节点 bounds [64,1046][1376,1496]（1312×450 ≈ 656×225dp）；名称"推箱子"宽 592px 完整、描述"经典推箱子益智游戏"宽 592px 完整、按钮"立即开始"宽 304px；截图 featured_v10.png 视觉确认翡翠绿卡 + 完整内容；logcat 无 FATAL/AndroidRuntime
- **是否全部完成**：是
- **未完成原因**：无

### 任务8 ｜ 11:55 ｜ 重构 L2 为两行两列四象限（用户指出"放在连胜记录旁边"而非下方）
- **背景**：任务7 把今日精选做成了"L2 Row 1 下方独立全宽卡片"。用户截屏指出："你没修复，连胜记录旁边还是空白的，今日精选没填到这空白的位置"——实际用户要的是与连胜记录**同一行并列**，而非下方独立行。
- **完成内容**：
  1. **结构重构**：`fragment_games.xml` L2 区改为两行 × 两列 × 四象限：
     - Row 1：左 `resume_game_section` 50%（weight=1） + 右 `daily_cards_section` 50%（weight=1，仅含 card_daily_challenge 单卡；`daily_cards_section` id 保留作 VISIBLE 开关）
     - Row 2：左 `card_today_featured_include` 50%（weight=1） + 右 `card_streak_summary_include` 50%（weight=1）
     - **关键点**：今日精选紧贴连胜记录**左侧同行**，消除"连胜记录旁边的视觉空白"
  2. **半宽版 `card_today_featured.xml` v4**：padding 16→8dp、星标 18→14dp、icon 48→32dp、名称 16→13sp、按钮 padding 14→8dp + textSize 12→9sp；desc TextView 保留 id 但 visibility=gone（兼容 Java `tvTodayFeaturedDesc` 引用，编译不报错）；总高 ~110dp 与 streak 卡同高
  3. **编译错**：误用 `dimen/spacing_10`（该命名不存在，仅有 8/12）→ 改为 `spacing_8` 通过
- **影响文件**：
  - `app/src/main/res/layout/fragment_games.xml`（L2 Row 1 简化：daily_cards_section 仅含每日挑战单卡；新增 L2 Row 2：今日精选 | 连胜记录 横向 LinearLayout）
  - `app/src/main/res/layout/card_today_featured.xml`（v4 半宽紧凑版：padding 8、星标 14dp、icon 32dp、name 13sp、按钮 9sp "立即开始"；desc 节点 visibility=gone 但 id 保留）
- **冒烟测试**：三星 A52 release APK 安装，启动后向下滚动约 700px；UI dump 验证 Row 2 节点渲染：
  - `card_today_featured_include` bounds [64,1362][688,1648]（624×286px ≈ 312×143dp 左半）
  - `card_streak_summary_include` bounds [752,1362][1376,1728]（624×366px ≈ 312×183dp 右半）
  - 文字"今日精选"在 [168,1394][488,1464]；"连胜记录"在 [896,1394][1225,1482]；"立即开始"按钮可见；"推箱子"名称完整
  - 截图 featured_v17_l2.png 视觉确认四象限平衡：左下翡翠绿今日精选 | 右下金色连胜记录，**今日精选紧贴连胜记录左侧**，无视觉空洞
  - logcat 无 `FATAL EXCEPTION` / `AndroidRuntime com.gamecenter` 崩溃
- **是否全部完成**：是
- **未完成原因**：无

### 任务9 ｜ 14:25 ｜ L2 四象限紧凑方阵修复（消除割裂感）
- **背景**：任务8 把 L2 做成 Row1=resume|daily + Row2=today|streak 两行两列，但用户截屏指出"这么割裂，我要求的是紧密贴合在一起的"。UI dump 定位根因：Row1 左卡(resume 305px) 矮于右卡(daily 528px)、Row2 左卡(today 286px) 矮于右卡(streak 366px) → **高度不一造成视觉割裂**；且 Row1/Row2 间纵向间距大（实测约 82dp）。
- **完成内容**：
  1. 迭代实现"同行两卡等高"的 2×2 紧凑方阵（L2 段重写 `fragment_games.xml`）：
     - **GridLayout（v4）**：外层 wrap_content + 子项 0dp+rowWeight → 父级解析为 0 高，整块不渲染 ✗
     - **ConstraintLayout（v5）**：约束依赖默认 GONE 的 resume/daily → 约束目标失效、整块不渲染 ✗
     - **LinearLayout 固定 190dp 行高 + `<include>` 直接作子项且 layout_height=match_parent（v6）** → L2 整块不渲染 ✗（根因：include 直接作 ViewGroup 子项且 match_parent 解析异常）
     - **最终 v7**：LinearLayout 固定 190dp 行高 + 每个卡片用 **FrameLayout/LinearLayout 包装层**包 `<include>`（include 的 layout_height=match_parent 落在包装层内）→ 四卡等高渲染成功 ✓（复用任务4 验证过的 resume/daily 包装模式，给 today/streak 也加同样包装）
  2. **间距收紧（v7b）**：横向 4 卡片 margin spacing_6→spacing_4、纵向 Row 间距 spacing_6→spacing_4，让横纵一致紧凑
- **影响文件**：
  - `app/src/main/res/layout/fragment_games.xml`（L2 重写为 vertical LinearLayout 包两 horizontal Row；每行固定 190dp 行高 + FrameLayout 包装 include；4 卡片横纵 margin 收紧到 spacing_4）
- **冒烟测试**：三星 A52 release APK 安装，冷启动 + 滚动；UI dump 验证四卡**全部等高 760px、等宽 640px、横向间距 32px、纵向行距 16px**，形成整齐紧凑 2×2 方阵；logcat 无 FATAL/AndroidRuntime 崩溃
- **是否全部完成**：是
- **未完成原因**：无
- **可复用经验**：`include` 直接作为 LinearLayout/ViewGroup 子项且设 `layout_height="match_parent"` 会导致整块不渲染——必须用一层容器（FrameLayout/LinearLayout）包装 include，include 放容器内并设 match_parent。已同步记入项目 MEMORY.md「布局坑」。

### 任务10 ｜ 14:55 ｜ L2 卡片内部空白修复（消除"太多空白"）
- **背景**：任务9 完成 L2 四象限等高方阵（每卡 760×640px）后，用户截屏反馈「填充好内容后太多空白了」——四张卡虽并排等高，但卡片**内部垂直方向大片空白**：跳棋卡上面 70% 是空紫色、今日精选下方 50% 是空绿色、连胜记录下方也有空白。根因：固定 190dp 行高 + 子 view match_parent 把所有卡撑到 760px 高，但 daily challenge 实测只需 ~528px，其他卡内容更少 → 被强制撑高后居中显示造成大量背景空白。
- **完成内容**：
  1. **v8 收紧 padding + 减行高**：4 张卡片内部 padding 8→6dp（daily 12→8dp）、子元素 marginTop 8→6dp 等；Row 高度 190dp→150dp。实测每卡 600×640px。
  2. **v9 试验 wrap_content + 0dp+weight 自适应行高**：`<include>` 直接作 LinearLayout 子项 + height=0dp → 整块 L2 不渲染（与任务9 v6 同根因）。
  3. **v10 跳棋卡改 vertical 布局**：把 layout_home_resume_game.xml 从 horizontal（图标 + 文字列）改为 vertical（48dp 图标居中 + name + time + 继续按钮），让原本内容少的跳棋卡自然撑满 150dp 行高，消除跳棋卡上方大片紫底空白。
  4. **v11 给今日精选 / 连胜记录补 desc 填充底部**：card_today_featured.xml 中 desc TextView 从 visibility=gone 改为显示（"经典推箱..."）+ 2dp marginTop；card_streak_summary.xml 底部新增一行"坚持每天挑战，赢取连胜成就"（9sp 居中）。
  5. **v12 Row 2 改 wrap_content**：让 streak 卡（带底部提示后约 130dp）决定 Row 2 高度，today 卡不再被 match_parent 强制撑到 150dp → today 紧凑，Row 2 内部无大片空白。
- **影响文件**：
  - `app/src/main/res/layout/fragment_games.xml`（Row 2 由 150dp 固定行高改为 wrap_content）
  - `app/src/main/res/layout/layout_home_resume_game.xml`（重写：vertical 布局，48dp icon + name 居中 + time 居中 + 继续按钮 padding 6dp）
  - `app/src/main/res/layout/card_today_featured.xml`（desc 字段改为显示 + 2dp marginTop）
  - `app/src/main/res/layout/card_streak_summary.xml`（底部新增"坚持每天挑战，赢取连胜成就"提示行）
  - `app/src/main/res/layout/card_daily_challenge.xml`（padding 12→8dp，desc marginTop 8→6dp，progress marginTop 10→6dp）
  - `app/src/main/res/layout/card_streak_summary.xml`（padding 8→6dp，三栏 marginTop 10→6dp）
  - `app/src/main/res/layout/card_today_featured.xml`（padding 8→6dp，marginTop 6→4dp）
- **冒烟测试**：三星 A52 release APK v8/v10/v11/v12 增量构建并安装；冷启动无 FATAL EXCEPTION 崩溃。v12 实测：跳棋卡 vertical 撑满 150dp（图标居中 + name + time + 按钮），每日挑战 4 段内容饱满，今日精选带 desc 行，连胜记录三栏 + 底部提示；Row 1 高度由每日挑战决定 ~150dp、Row 2 高度由连胜记录决定 ~130dp，每行内两卡等高无大片空白。
- **是否全部完成**：是
- **未完成原因**：无
- **可复用经验**：① 固定行高 + match_parent 会强制把内容少的卡片撑大居中显示，导致大片背景空白——若四张卡内容差异大，应改用 wrap_content + 包装层（让 LinearLayout wrap_content 取 max 子 view 高度）；② 卡片内容少的，可用 vertical 布局 + 居中堆叠 + 加大图标 + 增加底部 desc/提示行 来"主动撑高"而非"被强制撑高"。

### 任务11 ｜ 15:00 ｜ 移除主页"今日精选"模块（顶部大图卡 game_of_day_section）
- **背景**：用户在截屏中标记主页 L1 顶部的大黑卡（带"今日精选"徽章 + 游戏图标背景 + 跳棋名 + 立即开始按钮）说"请移除今日精选模块，并使下方子板块自动上移填充"。该模块对应 `fragment_games.xml` 中的 `game_of_day_section`（id）→ `@layout/layout_home_game_of_day`（"今日精选" 徽章 + 立即开始）。
- **完成内容**：
  1. **删除 `layout_home_game_of_day.xml` 整个文件**（带"今日精选"徽章的大图卡）；同时删除 `fragment_games.xml` 中包裹它的 `game_of_day_section` FrameLayout（含 layout_marginBottom=12dp 占位）。
  2. **清理 `GamesFragment.java` 编译期引用**（layout 删除后 R.id.iv_game_of_day_icon/tv_game_of_day_name/tv_game_of_day_desc/btn_game_of_day_play 等常量失效，Java 必须同步清理，否则 59 个编译错误）：
     - 删除字段：`gameOfDaySection / ivGameOfDayIcon / tvGameOfDayName / tvGameOfDayDesc / btnGameOfDayPlay / gameOfDayEntry`
     - 删除方法：`initGameOfDay(View)` 整段、`refreshGameOfDay()` 整段
     - 删除 `initViews()` 中 `if (BuildConfig.HOME_GAME_OF_DAY) initGameOfDay(v);` 整段（替换为注释行）
     - 删除 `loadGames()` 末尾 `if (BuildConfig.HOME_GAME_OF_DAY) refreshGameOfDay();` 整段（替换为注释行）
     - 简化 `refreshTodayFeatured()` 内与 `gameOfDayEntry` 的去重判断（无 L1 推荐后无需去重）
- **影响文件**：
  - `app/src/main/res/layout/fragment_games.xml`（删除 `game_of_day_section` FrameLayout 10 行 + 加说明注释）
  - `app/src/main/res/layout/layout_home_game_of_day.xml`（**D 整文件删除**）
  - `app/src/main/java/com/gamecenter/app/GamesFragment.java`（删除 gameOfDay 字段 5 个 + 方法 2 个 + 调用 2 处 + 简化 L2 去重 4 行）
- **保留未动**：`bg_home_v2_hero_card` / `bg_home_v2_hero_card_overlay` / `bg_home_v2_badge` / `bg_home_v2_play_btn` 4 个 drawable 资源（仅 layout_home_game_of_day 引用，留作备份可恢复）；`home_v2_featured_today` / `home_game_of_day_*` 字符串保留（可能在其他文档/模块引用，且删除 AAPT 不报错）；`card_today_featured` 仍存在（L2 网格中的"今日精选"，与 L1 大图卡同名但不同模块，用户未要求移除）。
- **冒烟测试**：三星 A52 release APK v13 增量构建并安装；冷启动无 FATAL EXCEPTION 崩溃。UI dump 实测关键节点 y 坐标对比：
  - 移除前：quick_stats_section [80,1386] / resume_game_section [64,1755] / daily_cards_section [736,1755]
  - 移除后：quick_stats_section [80, 698] / resume_game_section [64,1067] / daily_cards_section [736,1067]
  - 即 quick_stats 自动上移 688px（≈ game_of_day_section 高度 640px + margin 12dp + padding 12dp + 其他 = 与移除占位完全吻合），L2 网格上移 688px；game_of_day_section 节点在 dump 中**完全消失**（被 R8 + Lint 一并剥除）。整体结构完整无空白无错位，截图确认：搜索框下紧接"今日还未游玩/连1天/0成就"统计行，再下是 L2 网格（跳棋 + 每日挑战），其下 L2 Row 2（今日精选 推箱子 + 连胜记录 1/1/总对局）现在也露出来了。
- **是否全部完成**：是
- **未完成原因**：无
- **可复用经验**：① **删除一个被 Java 业务代码 findViewById 使用的 layout 时，必须同步清理 Java 中的字段 + 方法 + 调用点**——R.id.* 是编译期常量，layout 删除后 AAPT2 不再生成对应常量，导致所有 `R.id.iv_xxx` 引用编译期找不到符号（不是 null check 能解决的）；② 仅设 visibility=gone 不够——layout 文件本身删除会触发 59 个编译错误，必须 XML+Java 联动清理；③ **绘制类资源（drawable + string + color）删除前先用 `grep -rln` 全工程检索引用方**，未引用时再删以免 AAPT 警告；本任务 4 个 drawable 仅 layout_home_game_of_day.xml 引用 → 暂保留作"快速恢复"备份。

### 任务12 ｜ 19:47 ｜ 修复下载游戏后游戏大厅不显示已安装游戏（编译错误 + UI 验证）
- **背景**：用户反馈"下载的游戏之后，app 压根没有显示已经安装的小游戏"。前序会话已定位根因（SwipeRefreshLayout 的 EXACTLY 测量模式导致 wrap_content 的 rv_games 高度为 0），并修改了 `fragment_games.xml`（移除 SwipeRefreshLayout 包裹、rv_games 改 wrap_content）和 `GamesFragment.java`（hideShimmer 添加 requestLayout + notifyDataSetChanged、filterAndRefresh 修复 filtered.clear() 数据丢失、临时禁用 shimmer）。本次接续完成编译修复 + 真机验证。
- **完成内容**：
  1. **修复编译错误**：前序会话从 `fragment_games.xml` 移除了 `swipe_refresh_games`（SwipeRefreshLayout），但 `GamesFragment.java:946` 的 `setupPullRefresh(View)` 方法仍 `v.findViewById(R.id.swipe_refresh_games)` 引用该 id，导致 `:app:assembleDebug` 编译失败（找不到符号 swipe_refresh_games）。将 `setupPullRefresh` 方法体改为空实现 + 日志说明（SwipeRefreshLayout 已从布局移除，下拉刷新暂不可用），保留方法签名以便未来恢复布局时快速接回。
  2. **真机验证（小米 ares f0363bc0）**：
     - `:app:assembleDebug` 构建成功，`bundlePreinstalledModules` 刷新 26 个游戏模块 catalog 元数据（wrongbook 预装 + 26 个游戏不预装）
     - `adb install -r -d` 安装成功，冷启动无 FATAL EXCEPTION
     - logcat 确认：ModuleManager 安装状态缓存 6 个已安装模块，动态注册 3 个游戏（2048→puzzle、Blackjack→classics、Breakout→puzzle）
     - GamesFragment 日志：总游戏数量 3，过滤后游戏数量 3（分类 all），`setupPullRefresh: SwipeRefreshLayout 已从布局移除，跳过初始化`
     - UI dump 实测：rv_games bounds=[0,2224][2136,2925]（高度 701px，正常），滚动后显示 3 张游戏卡片（Blackjack / 打砖块 / 2048），点击 Blackjack 正常打开游戏详情页（显示战绩 + 立即开始按钮）
- **影响文件**：
  - `app/src/main/java/com/gamecenter/app/GamesFragment.java`（`setupPullRefresh` 方法体改为空实现 + 注释说明，第 941-950 行）
- **回滚方法**：恢复 `setupPullRefresh` 方法体为原 SwipeRefreshLayout 逻辑（需同步在 `fragment_games.xml` 中恢复 `swipe_refresh_games` 布局包裹 rv_games）
- **是否全部完成**：是
- **未完成原因**：无
- **可复用经验**：① 移除布局中的带 id 视图后，必须全局搜索 Java/Kotlin 中对该 `R.id.*` 的引用并同步清理——否则编译期 `找不到符号`；② 临时禁用功能（如 shimmer、下拉刷新）时，保留方法签名 + 空实现 + 日志，比直接删除调用更易于未来恢复；③ rv_games 在 NestedScrollView 内使用 `nestedScrollingEnabled=false` + `wrap_content` 可正常测量出全部 item 高度，无需 SwipeRefreshLayout 包裹。

---

## 2026-07-25

### 任务13 ｜ 16:50 ｜ 全量中英文翻译 + 布局硬编码清理 + 真机中文显示验证
- **背景**：用户反馈"目前很多文本在中文环境下还是显示英文"，要求进行全量的中英文翻译。前序会话已完成主要翻译工作（values/strings.xml + values-en/strings.xml 各约 200+ 条新增、catalog.json/modules.json 模块名与描述翻译、ModuleManager 优先从 catalog.json 取中文名、VPS 同步）。本会话接续完成布局硬编码清理检查 + 真机验证。
- **完成内容**：
  1. **布局 XML 硬编码清理（检查）**：使用 Grep 全量扫描 `app/src/main/res/layout*` 与 `module-store/**/layout*` 下所有 XML：
     - `android:text=` 共 13 处全部为 `@string/` 资源引用（无硬编码）
     - `android:hint=` / `android:contentDescription=` 无硬编码文本
     - `tools:text=` 共 8 处全部为中文占位符（设计期预览文本，符合规范，保留）
     - 结论：布局层无需修改，硬编码清理任务实质已由前序会话完成
  2. **真机中文显示验证（小米 ares f0363bc0，已解锁）**：通过 uiautomator dump 逐页抓取文本节点验证：
     - **首页/游戏大厅 ✓**：早上好、启动耗时、搜索游戏、今日还未游玩、今日时长、连续登录、成就、开始你的第一局游戏、从下方推荐中挑选一款、继续、每日挑战、每日挑战 0/2、进度：0 / 2、今日精选、精选推荐、打砖块、经典打砖块游戏、立即开始、连胜记录、当前连胜/最佳连胜/总对局、坚持每天挑战赢取连胜成就、活动进行中、前往模块市场发现更多新游戏、去逛逛、全部/经典/益智类/休闲类、游戏大厅/浏览器/工具箱/我的
     - **工具箱 ✓**：暂无工具模块，请前往模块商店下载
     - **我的 ✓**：游戏玩家、查看你的战绩与收藏、我的战绩、当前连胜/最佳连胜/总对局、我的收藏、还没有收藏的游戏去游戏大厅点♥收藏吧、游戏统计/成就中心/模块商店/资料设置
     - **浏览器 ✓**：返回/前进/刷新/更多、搜索或输入网址、浏览器起始页、收藏/历史/阅读列表、首页/收藏/标签页/下载/菜单
     - **模块商店 ✓（catalog.json 翻译关键验证点）**：模块商店、精选推荐、中国象棋（模块名）、中国象棋5级AI难度支持提示复盘主题学习（模块描述）、立即下载、总模块34/已安装4/有更新0、↑共34个/11%已装、搜索模块/游戏、游戏/浏览器/工具箱/AI助手/VPN（分类）、全部/益智/休闲/经典（子分类）、游戏大厅/内置入口聚合宿主游戏与已下载的游戏模块/内置/已安装/打开/卸载、2048/内置2048益智游戏/未安装/下载、21点/内置21点游戏/未安装/下载、新品、模块图标/重试
     - **游戏详情页 ✓**：打砖块、益智类、经典打砖块游戏、游戏战绩、总对局：0 次、胜 0 / 负 0、总时长：0 分钟、尚未游玩、立即开始、为这款游戏评分、清除评分、你的评分将显示在卡片右上角、加入收藏/已收藏
     - **游戏启动 ✓**：点击立即开始成功启动 BreakoutActivity（mCurrentFocus=com.gamecenter.app/.games.breakout.BreakoutActivity），游戏为自定义 SurfaceView 渲染（uiautomator 无法抓取文本，但启动成功无崩溃）
  3. **logcat 检查 ✓**：全流程无 FATAL EXCEPTION、无 Resources$NotFoundException、无 InflateException、无 ClassNotFoundException；仅有 Google Play 服务（AuthPII/Finsky）无关错误
- **影响文件**：本会话无代码改动（仅检查 + 验证）
- **回滚方法**：不适用（无改动）
- **是否全部完成**：是
- **未完成原因**：无
- **可复用经验**：① `tools:text` 是设计期预览文本，仅 Android Studio 预览可见，运行时不生效，可保留中文占位符不影响多语言；② 真机 PIN 锁屏时 `mDreamingLockscreen=true`，`adb shell input keyevent KEYCODE_WAKEUP` 只能唤醒屏幕不能解锁 PIN，必须人工解锁；③ 检查布局硬编码应同时覆盖 `android:text/hint/contentDescription` 三个属性，缺一不可；④ **验证中文显示优先用 `adb shell uiautomator dump` + Grep 提取 text/content-desc 节点**，比截图更准确可靠，可一次性获取整个页面所有文本；自定义 SurfaceView 渲染的游戏（如打砖块）uiautomator 抓不到文本，需配合截图与 mCurrentFocus 确认启动成功；⑤ 模块商店的模块名/描述来自 catalog.json（ModuleManager 优先读取），是验证 catalog.json 翻译是否生效的关键页面。

### 任务14 ｜ 18:55 ｜ 修复工具箱模块下载后不显示 + AI助手tab动态添加
- **背景**：用户反馈"工具箱下载之后没有正确加载"。经调研发现根因：MainActivity.onCreate 的 `loadBuiltInCoreModules()` 只 load `games_hall` + `browser`，未 load `tools` 模块，导致 ToolsModuleEntryPoint 的 BOTTOM_NAV 贡献未被收集，BottomNavigationCatalog 兜底加入 `DestinationKind.TOOLS -> DynamicToolsFragment`（只显示 TOOLS_GRID 贡献，不显示内置 28 个工具卡片）。
- **完成内容**：
  1. **新增 feature flag**（`app/build.gradle`）：`PRELOAD_INSTALLED_TOOL_MODULES = true`，控制工具模块下载完成后是否立即 load 进内存
  2. **ModuleManager.downloadModule onComplete 增加工具模块 load**（`ModuleManager.kt` L268-277）：对 `manifest.category == "tool"` 的模块调用 `ModuleLoader.loadModule()`，使 AI/VPN 等模块下载后立即贡献 BOTTOM_NAV，动态添加到底部导航栏
  3. **MainActivity.loadBuiltInCoreModules 增加 "tools"**（`MainActivity.kt` L203）：将 `tools` 加入 `coreModules` 列表，使其 BOTTOM_NAV 贡献被收集，工具箱 tab 指向 ToolsFragment（内置 28 个工具卡片）而非 DynamicToolsFragment
  4. **DynamicToolsFragment 保留本地化和深色主题改进**：移除之前的 preload 逻辑（避免与产品意图冲突），保留 `dynamic_tools_empty_state` / `dynamic_tools_loading` 字符串资源和主题感知颜色（colorSurface/colorSurfaceStroke/colorOnSurface）
  5. **新增字符串资源**：`values/strings.xml` 和 `values-en/strings.xml` 各新增 2 条（dynamic_tools_empty_state / dynamic_tools_loading）
  6. **真机验证（小米 ares f0363bc0）**：
     - **工具箱 tab ✓**：显示内置工具卡片（一键网络体检、诊断报告导出、DNS查询等 28 个工具），不再显示"暂无工具模块"
     - **AI 助手 tab ✓**：下载 AI 助手模块后，底部导航栏动态添加"AI 助手" tab（第 4 个位置），点击可正常打开 AI 聊天界面（本地模型/切换到云端/就绪/聊天/输入内容等 UI 元素渲染正常）
     - **底部导航 5 个 tab**：游戏大厅、浏览器、工具箱、AI 助手、我的
     - **logcat ✓**：无 FATAL EXCEPTION、无 Resources$NotFoundException
- **影响文件**：
  - `app/build.gradle`（新增 feature flag）
  - `app/src/main/java/com/gamecenter/app/modules/ModuleManager.kt`（onComplete 增加工具模块 load）
  - `app/src/main/kotlin/com/gamecenter/app/MainActivity.kt`（loadBuiltInCoreModules 增加 tools）
  - `app/src/main/java/com/gamecenter/app/features/DynamicToolsFragment.kt`（回退 preload，保留本地化和深色主题）
  - `app/src/main/res/values/strings.xml`（新增 dynamic_tools_empty_state / dynamic_tools_loading）
  - `app/src/main/res/values-en/strings.xml`（同上英文翻译）
- **回滚方法**：
  1. 还原 `MainActivity.kt` L203：`val coreModules = listOf("games_hall", "browser")`（移除 "tools"）
  2. 还原 `ModuleManager.kt` L268-277：移除 `else if (BuildConfig.PRELOAD_INSTALLED_TOOL_MODULES && manifest.category == "tool")` 分支
  3. 关闭 feature flag：`PRELOAD_INSTALLED_TOOL_MODULES=false`（即时回退，无需重新编译）
  4. DynamicToolsFragment 的本地化/深色主题改进可保留（不影响功能，仅 UI 优化）
- **是否全部完成**：是
- **未完成原因**：无
- **可复用经验**：① **BottomNavigationCatalog 的兜底逻辑是"无贡献时才兜底"**：L136 `if (!items.containsKey("tools"))` -- 只要 tools 模块的 BOTTOM_NAV 贡献存在（通过 `items[id]` 收集），兜底逻辑不会触发；② **产品意图澄清**：工具箱 tab 应显示内置 28 个工具卡片（ToolsFragment），AI/VPN 等模块应通过 BOTTOM_NAV 贡献动态添加为独立 tab，而非显示在工具箱内；③ **MainActivity.loadBuiltInCoreModules 是内置模块 load 的唯一入口**，新增内置模块需在此列表中添加；④ **ModuleLoader.loadModule 是幂等的**（L39-43 有缓存），重复调用无副作用，可在 onComplete 和 onResume 多处调用；⑤ **DynamicToolsFragment 现在仅作为 DestinationKind.TOOLS 的兜底**，当 tools 模块正常 load 后不会被触发，但仍保留作为防御性设计。

### 任务15 ｜ 19:20 ｜ 完善工具箱内容（删除冗余 + 改进现有 + 新增 6 个工具）
- **背景**：用户要求"完善工具箱的内容，需要自行思考哪些功能需要删除、改进、添加"。基于 ToolSectionStore 的 25 个原工具集审计，识别 5 个冗余工具（与 text_codec 或 qr_plus 功能重叠）+ 6 个新增工具（单位换算/进制转换/密码生成/UUID/加密/JWT），并修复多个工具的功能问题。
- **完成内容**：
  1. **删除冗余工具（5 个 Binder + 1 个布局）**：
     - `UrlEncodeToolBinder` / `Base64ToolBinder` / `JsonFormatToolBinder`：与 `text_codec` 功能重叠，统一由 TextCodecToolBinder 提供
     - `QrToolBinder`：与 `qr_plus` 重叠，qr_plus 已包含扫码+生成+识别+文件导入
     - `DeviceToolBinder` + `item_tool_device.xml`：与 `sysinfo` 重叠，sysinfo 已合并 device 详情（弹窗展示完整信息+复制按钮）
  2. **修复工具功能问题**：
     - **文件选择无响应**：FileHashToolBinder / QrPlusToolBinder / ColorPlusToolBinder 的"选择文件"按钮无响应（`pickFileListener == null`）。修复：在 ToolsFragment 注册 `ActivityResultLauncher.OpenDocument`，通过 `contentView.setTag(R.id.tag_tools_fragment, this)` 传递 Fragment 引用，Binder 从 tag 获取并调用 `requestPickFile` 回调
     - **clipboard 分类错误**：在 `ToolSectionStore.defaultSections()` 中将 `clipboard` 从 `network` 修正为 `tool`
     - **正则测试工具 UX 优化**：将单输入框两行格式改为双输入框（正则+测试串），移除硬编码中文
     - **端口扫描并发优化**：使用 `ThreadPoolExecutor` 实现并发扫描，支持超时和并发数配置
     - **系统信息工具合并 device**：完整详情改为弹窗展示，新增复制按钮
     - **CryptoToolBinder 修复**：① 修复变量作用域错误（统一使用 `ciphertext` 变量）；② 修复 `spAlgorithm` 重复 "AES" 项
  3. **新增 6 个工具（受 `ENABLE_TOOLS_ENHANCEMENT` flag 控制）**：
     - `UnitConverterToolBinder`：长度/重量/温度单位换算
     - `RadixConverterToolBinder`：二进制/八进制/十进制/十六进制互转（动态设置 Spinner，移除 `@array/radix_bases` 依赖）
     - `PasswordGeneratorToolBinder`：自定义长度和字符集的随机密码生成
     - `UuidGeneratorToolBinder`：生成 UUID v4 和 v7
     - `CryptoToolBinder`：AES 加密解密（CBC/ECB）+ 消息摘要（MD5/SHA-1/SHA-256）
     - `JwtParserToolBinder`：JWT 三段式解码（Base64 URL-safe + padding 补齐），支持复制 Header/Payload/Signature
  4. **新增 feature flag**：`app/build.gradle` 新增 `ENABLE_TOOLS_ENHANCEMENT = true`，关闭后回退到原 25 个工具集（去除冗余后为 20 个）
  5. **新增字符串资源（中英文本地化）**：`values/strings.xml` 和 `values-en/strings.xml` 各新增 50+ 条工具名称、描述、提示字符串
  6. **新增布局文件（6 个）**：`item_tool_unit_converter.xml` / `item_tool_radix_converter.xml` / `item_tool_password_generator.xml` / `item_tool_uuid_generator.xml` / `item_tool_crypto_tool.xml` / `item_tool_jwt_parser.xml`，全部使用主题属性（`?attr/colorSurface` / `?attr/colorOnSurface`）支持浅色/深色主题
  7. **模块清单更新**：`modules.json` 中 `tools` 模块 sha256/fileSize/version 同步更新（version 29, sha256 452d24465f9499e601322ab657b44b4ce3de79227c684290b1dce8946327894a, fileSize 695909），APK 重新签名并推送到设备
  8. **真机验证（小米 ares f0363bc0）**：
     - **UUID 生成器 ✓**：搜索 "uuid" 显示 UUID 生成器卡片，含"生成 UUID v4"/"生成 UUID v7"/"复制"按钮
     - **JWT 解析功能 ✓**：输入标准 JWT（`eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c.`），点击"解码"成功输出：
       - Header: `{"alg":"HS256","typ":"JWT"}`
       - Payload: `{"sub":"1234567890","name":"John Doe","iat":1516239022}`
       - Signature: 完整 JWT 第三段
     - **工具箱标题 ✓**：tv_tools_title 显示 "夹层"（app_name），副标题"多功能工具箱"（符合品牌命名）
     - **logcat ✓**：无 FATAL EXCEPTION、无 Resources$NotFoundException、无 InflateException、无 ClassNotFoundException（仅 Google Play 服务无关网络错误）
- **影响文件**：
  - `app/build.gradle`（新增 ENABLE_TOOLS_ENHANCEMENT feature flag）
  - `app/src/main/assets/modules.json`（tools 模块 sha256/fileSize/version 更新）
  - `app/src/main/res/values/strings.xml`（新增 50+ 条工具名称/描述/提示）
  - `app/src/main/res/values-en/strings.xml`（同上英文翻译）
  - `module-store/feature/tools/tools/src/main/java/com/gamecenter/app/tools/ToolSectionStore.java`（移除 5 个冗余条目，新增 6 个工具条目受 flag 控制，修复 clipboard 分类）
  - `module-store/feature/tools/tools/src/main/java/com/gamecenter/app/fragments/ToolsFragment.java`（移除 DeviceToolBinder 注册，新增 ActivityResultLauncher + tag_tools_fragment 机制）
  - `module-store/feature/tools/tools/src/main/java/com/gamecenter/app/tools/UnitConverterToolBinder.java`（新增）
  - `module-store/feature/tools/tools/src/main/java/com/gamecenter/app/tools/RadixConverterToolBinder.java`（新增）
  - `module-store/feature/tools/tools/src/main/java/com/gamecenter/app/tools/PasswordGeneratorToolBinder.java`（新增）
  - `module-store/feature/tools/tools/src/main/java/com/gamecenter/app/tools/UuidGeneratorToolBinder.java`（新增）
  - `module-store/feature/tools/tools/src/main/java/com/gamecenter/app/tools/CryptoToolBinder.java`（新增 + 修复变量作用域）
  - `module-store/feature/tools/tools/src/main/java/com/gamecenter/app/tools/JwtParserToolBinder.java`（新增）
  - `module-store/feature/tools/tools/src/main/res/layout/item_tool_unit_converter.xml`（新增）
  - `module-store/feature/tools/tools/src/main/res/layout/item_tool_radix_converter.xml`（新增）
  - `module-store/feature/tools/tools/src/main/res/layout/item_tool_password_generator.xml`（新增）
  - `module-store/feature/tools/tools/src/main/res/layout/item_tool_uuid_generator.xml`（新增）
  - `module-store/feature/tools/tools/src/main/res/layout/item_tool_crypto_tool.xml`（新增）
  - `module-store/feature/tools/tools/src/main/res/layout/item_tool_jwt_parser.xml`（新增）
  - 其他改动文件：IpToolBinder（清理 import）、SystemInfoToolBinder（合并 device + 弹窗展示）、RegexToolBinder（双输入框优化）、PortScanToolBinder（并发优化）、TextCodecToolBinder（吸收 url_encode/base64/json_format）、QrPlusToolBinder（接入 pickFile）、ColorPlusToolBinder（接入 pickFile）、FileHashToolBinder（接入 pickFile）
  - `to_delete_files.txt`（新增，记录待删除的 6 个冗余文件路径）
- **回滚方法**：
  1. **即时回退（无需重编译）**：在 `app/build.gradle` 中将 `ENABLE_TOOLS_ENHANCEMENT` 改为 `false`，重新编译即可关闭 6 个新增工具
  2. **完整回退**：
     - 还原 `ToolSectionStore.java`：恢复 5 个冗余工具条目（url_encode/base64/json_format/qr/device）+ clipboard 的 `network` 分类，移除 6 个新增工具的 `if (BuildConfig.ENABLE_TOOLS_ENHANCEMENT)` 块
     - 还原 `ToolsFragment.java`：恢复 `DeviceToolBinder` 注册，移除 `pickFileLauncher` 和 `tag_tools_fragment` 设置
     - 还原 `modules.json` 中 `tools` 模块的 sha256/fileSize/version 到上一版本
     - 从 `app/build.gradle` 移除 `ENABLE_TOOLS_ENHANCEMENT` buildConfigField
     - 移除新增的 6 个 Binder Java 文件和 6 个布局 XML 文件
     - 移除 `to_delete_files.txt`，恢复 6 个被删除的冗余文件（UrlEncodeToolBinder / JsonFormatToolBinder / Base64ToolBinder / QrToolBinder / DeviceToolBinder / item_tool_device.xml）
- **是否全部完成**：是
- **未完成原因**：无
- **可复用经验**：① **Fragment 内 ActivityResultLauncher 必须在 onCreate/onAttach 阶段注册**，不能在 bindContent 中按需注册（生命周期约束）；通过 `contentView.setTag(R.id.tag_tools_fragment, this)` 传递 Fragment 引用给 Binder 是安全可靠的，避免 Binder 持有 Fragment 强引用导致泄漏；② **ADB shell input text 不支持中文字符**，会抛 `NullPointerException: Attempt to get length of null array`；测试中文搜索需通过其他方式（如先复制到剪贴板或使用 Instrumentation）；ASCII 字符（如 JWT）可正常输入；③ **JWT Base64 URL-safe 解码需补齐 padding**：`int rem = part.length() % 4; if (rem != 0) part += "====".substring(0, 4 - rem);` 然后用 `Base64.getUrlDecoder()`，标准 Base64.getDecoder() 会因 `-_` 字符抛异常；④ **新增工具受 feature flag 控制是必要的工程实践**：`ENABLE_TOOLS_ENHANCEMENT` 允许在发现新工具有问题时一键回退，避免影响原 25 个工具的稳定性；⑤ **冗余工具清理需考虑功能包含关系**：text_codec 工具已通过单 Binder 提供多种编码（URL/Base64/JSON/Hex/Unicode），保留单独的 url_encode/base64/json_format 工具是冗余；qr_plus 已包含文件扫码，保留单独的 qr 工具是冗余；这种"合并工具集"比"细分工具"更符合工具箱的轻量定位；⑥ **modules.json 的 sha256 必须与设备上 APK 实际哈希一致**，否则 ModuleLoader 校验失败会提示"暂无工具模块"；每次 APK 重新签名后必须同步更新 modules.json；⑦ **tools 工具标题"夹层"非 bug**：`tv_tools_title.setText(getString(R.string.app_name))` 是设计意图，使用宿主 App 名作为工具箱标题（"夹层"是 App 品牌名），副标题"多功能工具箱"说明功能性质。

### 任务16 ｜ 20:15 ｜ 发布 Beta 安装包 vc602 + 工具模块 APK 到 VPS
- **背景**：用户要求"发布新的安装包"。前序任务15 完成工具箱完善（删除 5 个冗余 + 新增 6 个工具），需要发布 beta 渠道安装包到 VPS 让用户真机更新。
- **完成内容**：
  1. **版本号 bump**：`version.properties` versionCode 601 → 602（versionName 1.4.1 不变，beta 通道）
  2. **构建 tools 模块 APK**（`:module-store:feature:tools:tools:assembleRelease`，25s）：
     - 产物：`module-store/feature/tools/tools/build/outputs/apk/release/feature_tools_v100.apk`
     - 大小：692923 bytes
     - sha256：`658f8bfa932e53ed66f2a4209d7c372f8d4d61388777d1d7adf9c9b8900ea9dc`
  3. **同步 modules.json**：tools 模块的 `sha256` / `fileSize` / `package.sha256` / `package.fileSize` 全部更新为新 APK 的实际哈希
  4. **构建主 APK**（`:app:assembleRelease :app:generateVersionJson -PupdateChannel=beta`，2m22s）：
     - 产物：`app/build/outputs/apk/release/app-release.apk` (120061803 bytes, sha256=c4ab6053fdee7857fa1bddc06758c469a37899d8f837a5f65755f6d748e1c287)
     - version.json: vc=602, channel=beta, isBeta=true, apkName=app-beta.apk
  5. **VPS 上传**（`tools/upload_to_vps.py --channel beta`，SSH 到 149.104.29.181:2222）：
     - `app-beta.apk` (120MB) → /var/www/update/app/app-beta.apk ✓
     - `version-beta.json` → /var/www/update/app/version-beta.json ✓
     - `version.json` (兼容 beta) → /var/www/update/app/version.json ✓
     - `modules.json` (47KB) → /var/www/modules/modules.json ✓
     - `feature_tools_v100.apk` (693KB) → /var/www/modules/feature_tools_v100.apk ✓
  6. **公开 HTTPS 验证全部通过**：
     - https://hk-update.tcp0053.shop/app-beta.apk?v=602
     - https://hk-update.tcp0053.shop/version-beta.json
     - https://hk-update.tcp0053.shop/version.json?acceptBeta=true
     - https://hk-update.tcp0053.shop/modules.json
     - https://hk-update.tcp0053.shop/modules/feature_tools_v100.apk
- **影响文件**：
  - `version.properties`（versionCode 601 → 602）
  - `app/src/main/assets/modules.json`（tools 模块 sha256/fileSize 同步）
  - 产物：`app/build/outputs/apk/release/app-release.apk`、`app/build/outputs/apk/release/version.json`、`module-store/feature/tools/tools/build/outputs/apk/release/feature_tools_v100.apk`
- **回滚方法**：
  1. 还原 `version.properties` versionCode=601
  2. 还原 `modules.json` 中 tools 模块 sha256 到上一版本（452d24465f9499e601322ab657b44b4ce3de79227c684290b1dce8946327894a / 695909）
  3. 重新构建并上传上一版 APK 覆盖 VPS 上的 app-beta.apk / version.json / modules.json / feature_tools_v100.apk
- **是否全部完成**：是
- **未完成原因**：无
- **可复用经验**：① **Beta 渠道发布流程**：`assembleRelease + generateVersionJson -PupdateChannel=beta` → `upload_to_vps.py --channel beta`；upload_to_vps.py 会自动将 app-release.apk 重命名为 app-beta.apk 并同时生成 version-beta.json 和兼容的 version.json?acceptBeta=true；② **每次工具模块改动后必须同步 modules.json 的 sha256/fileSize**，否则设备下载后会因校验失败而无法加载；③ **upload_to_vps.py 上传 120MB APK 约需 2-3 分钟**，期间无进度输出（SSH 静默传输），需耐心等待；上传完成后会自动做 HTTPS 公开访问验证，5 个文件逐一 VERIFY OK；④ **Beta 模式下 validateReleaseNotes 不严格校验版本号**（不传 --version-file），RELEASE_NOTES.md 内容可以滞后于 versionCode，但仍建议正式版发布前同步更新；⑤ **tools 模块 APK 构建命令**：`:module-store:feature:tools:tools:assembleRelease`，产物在 `module-store/feature/tools/tools/build/outputs/apk/release/tools-release.apk`，需手动复制重命名为 `feature_tools_v100.apk` 以匹配 modules.json 中的 fileName。

### 任务17 ｜ 15:15 ｜ 重写 6 款小游戏 AI 人机程序（难度分级 + 开局随机性）
- **背景**：用户反馈"目前人机程序难度分级实际体验不明显，都很难，同时人机每次开局走的步骤几乎一样，没有新意"。需重写五子棋/中国象棋/围棋/井字棋/跳棋/斗地主 6 款游戏的 AI，实现平滑难度梯度 + 开局多样性，同步 app 内嵌版和 module-store 版代码。
- **完成内容**：
  1. **五子棋 AI（GomokuAI.java）**：
     - 难度梯度：4 档（low/medium/high/master），搜索时间 200/800/2500/5000ms，搜索深度 2/4/6/8
     - 随机走子：低难度从评分前 5 名中随机选（RANDOM_TOP_N_LOW=5），中难度前 3 名，高难度前 2 名
     - 开局随机性：13 个候选位置加权随机（天元权重4、一线4个各权重3、二线4个各权重2、三线4个各权重1）
     - 算法：Minimax + α-β剪枝 + 迭代加深 + VCF算杀
  2. **中国象棋 AI（ChineseChessAI.java）**：
     - 难度梯度：4 档（UI 映射 {1,2,3,4}，跳过大师档5），修复原 UI 档位跳过 bug（原为 {1,2,3,5}）
     - 开局库：14 种开局走法（炮二平五/马二进三/车一进一/兵七进一/相三进五/仕四进五/马八进七/车九平八/兵三进一/士四进五/炮八平六/马二进一/车一平二/炮二平六），加权随机选择
     - 算法：Minimax + 静态搜索 + 将军延伸 + MVV-LVA 走法排序
  3. **围棋 AI（GoAI.java）**：
     - 难度梯度：4 级（随机/贪心/Minimax/MCTS）
     - MCTS 后台线程：将 MCTS 算法迁移到 aiExecutor 后台线程，避免主线程卡顿
     - 评估函数：子数 + 领地估算 + 目数
     - 开局位置：星位、边星、天元、小目等多位置随机
  4. **井字棋 AI（TicTacToeGame.java / TicTacToeActivity.java）**：
     - 难度梯度：3 档（简单/中等/困难），新增中等难度按钮
     - 简单：80% 随机 + 20% 必胜/必堵手
     - 中等：Minimax depth=2 + 启发式评估
     - 困难：完整 Minimax + 开局库加权随机
     - 开局随机性：9 位置加权随机（4角权重3、中心权重2、4边权重1）
     - 成就触发：仅困难档胜利触发"击败困难AI"成就
  5. **跳棋 AI（CheckersGame.java / CheckersActivity.java）**：
     - 难度梯度：3 档（简单/中等/困难），新增中等难度按钮
     - 简单：随机走子
     - 中等：Minimax depth=2 + α-β剪枝
     - 困难：Minimax depth=4 + α-β剪枝 + 评估函数（棋子差+王棋加权+位置加权）
     - 开局随机性：困难档多等价走法随机选择
  6. **斗地主 AI（AIBot.java）**：
     - 修复 difficultyFactor bug：原难度因子仅对接牌生效，首发出牌无随机性，导致低难度 AI 首发同样强
     - 新增 applyLeadPlayVariety 方法：低难度首发出牌按概率选非最优牌（概率 = (1.0 - factor) * 0.5）
     - 原决策是单牌时，从手牌中其他单牌候选里加权随机选一张略大的牌替换
     - 原决策是对子时，从手牌其他对子里选略大的替换
     - 不替换王炸/炸弹（保留保护机制）
  7. **代码同步**：app 内嵌版和 module-store 版 AI 代码逻辑保持一致
  8. **编译验证**：`:app:assembleDebug -PautoBumpVersion=false` BUILD SUCCESSFUL（1m 38s），所有 6 个游戏模块 APK 编译通过
  9. **真机验证（小米 ares f0363bc0）**：
     - 推送 6 个游戏模块 APK 到 `files/modules/` 目录
     - **跳棋 ✓**：启动成功，显示 3 档难度（简单/中等/困难），选择简单档后游戏正常进行，AI 响应玩家走子，无 FATAL EXCEPTION
     - **代码审查 ✓**：6 款游戏的 AI 改进均已到位（开局随机+难度梯度），通过 Grep 验证关键代码模式
- **影响文件**：
  - `app/src/main/java/com/gamecenter/app/games/gomoku/GomokuAI.java`（难度梯度+开局随机性）
  - `app/src/main/java/com/gamecenter/app/games/chinesechess/ChineseChessAI.java`（开局库扩展+难度梯度）
  - `app/src/main/java/com/gamecenter/app/games/chinesechess/ChineseChessActivity.java`（UI 档位映射修复 {1,2,3,5}→{1,2,3,4}）
  - `app/src/main/java/com/gamecenter/app/games/go/GoAI.java`（MCTS 后台线程+评估函数+开局位置）
  - `app/src/main/java/com/gamecenter/app/games/tic/TicTacToeActivity.java`（3 档难度+开局库加权随机+中等难度按钮）
  - `app/src/main/java/com/gamecenter/app/games/checkers/CheckersActivity.java`（3 档难度+Minimax+中等难度按钮）
  - `app/src/main/java/com/gamecenter/app/games/doudizhu/AIBot.java`（修复 difficultyFactor bug+首发出牌随机化）
  - `module-store/feature/games/games/gomoku/src/main/java/com/gamecenter/app/gomoku/GomokuAI.java`（同步 app 版）
  - `module-store/feature/games/games/chinesechess/src/main/java/com/gamecenter/app/chinesechess/ChineseChessAI.java`（同步 app 版）
  - `module-store/feature/games/games/go/src/main/java/com/gamecenter/app/go/GoAI.java`（同步 app 版）
  - `module-store/feature/games/games/tic/src/main/java/com/gamecenter/app/tic/TicTacToeGame.java`（同步 app 版，3 档难度+开局库）
  - `module-store/feature/games/games/checkers/src/main/java/com/gamecenter/app/checkers/CheckersGame.java`（同步 app 版，3 档难度+Minimax）
  - `module-store/feature/games/games/doudizhu/src/main/java/com/gamecenter/app/doudizhu/AIBot.java`（同步 app 版，applyLeadPlayVariety）
  - 字符串资源文件（新增中等难度按钮文本 game_diff_medium 等中英文翻译）
- **回滚方法**：
  1. 五子棋/中国象棋/围棋：还原 GomokuAI.java/ChineseChessAI.java/GoAI.java 的 DIFFICULTY_PROFILES 和开局库到上一版本
  2. 井字棋/跳棋：还原 TicTacToeActivity.java/CheckersActivity.java，移除中等难度按钮和 aiLevel=1 分支
  3. 斗地主：还原 AIBot.java decidePlay 方法，移除 applyLeadPlayVariety 调用和方法
  4. 中国象棋 UI：还原 ChineseChessActivity.java 档位映射 {1,2,3,4}→{1,2,3,5}
  5. module-store 版本同步还原
- **是否全部完成**：是
- **未完成原因**：无
- **可复用经验**：① **难度梯度设计的核心三要素**：搜索深度（控制算力）、搜索时间（控制强度）、随机走子概率/范围（控制失误率）；三者配合才能实现平滑难度曲线，单一维度调整效果不明显；② **开局随机性的两种实现**：a) 加权随机选择（五子棋13位置/井字棋9位置，权重递减）；b) 开局库+随机选择（中国象棋14种开局）；权重设计应让"中心/最佳位置"权重高、"边角/冷门"权重低，既保证开局合理性又增加多样性；③ **斗地主 difficultyFactor bug 的根因**：原代码只对接牌（previousCards非空）应用随机性，首发出牌（previousCards为空）走 decideLeadPlay 最优决策，导致低难度 AI 首发和普通难度一样强；修复需在首发出牌后也调用 applyLeadPlayVariety；④ **module-store 模块测试方法**：通过 `adb push` + `run-as com.gamecenter.app tee files/modules/xxx.apk` 将模块 APK 推送到设备模块目录，避免通过模块商店 UI 下载的复杂流程；⑤ **模块化游戏的 Activity 不在宿主 manifest 中注册**，需通过模块商店 UI 或 GameRegistry 动态注册后才能启动；⑥ **Minimax 搜索深度对难度的影响远大于时间限制**：depth=2 只能看1步，depth=4 能看2步，玩家感受差异明显；低难度应同时降低深度和增加随机性，不能只降深度不增随机性（否则 AI 仍然"死板"）。
