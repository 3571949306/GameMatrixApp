package com.gamecenter.app.games;

import android.content.Context;
import com.gamecenter.app.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * 新手教程工具类 —— 游戏的"说明书"
 *
 * <p>你可以把这个类想象成游戏中心里的说明书架：
 * 每个游戏都有一份说明书，告诉新手怎么玩。
 * 说明书有两种形式：</p>
 * <ul>
 *   <li>交互式多页教程：像翻书一样，一页一页看，适合规则复杂的游戏
 *       （比如五子棋、象棋，需要分步骤讲解）</li>
 *   <li>传统文本教程：一页纸就够了的简单说明，适合规则简单的游戏
 *       （比如打地鼠、猜数字，一两句话就能说清楚）</li>
 * </ul>
 *
 * <p>关键设计决策：
 * <ul>
 *   <li>所有方法均为static，该类作为纯工具类使用，无需实例化
 *       （就像说明书架不需要你"创建"，直接去拿说明书就行）</li>
 *   <li>所有教程底部统一附加"如何反馈"引导，方便用户提交意见</li>
 * </ul>
 * </p>
 */
public class GameTutorialHelper {

    /**
     * 显示交互式多页教程（内部方法）
     *
     * <p>创建并显示InteractiveTutorialDialog，支持滑动翻页浏览教程内容。
     * 这个方法是私有的，因为外部应该调用具体的游戏教程方法（如showGomokuTutorial），
     * 而不是直接调用这个通用方法。</p>
     *
     * @param context   上下文对象
     * @param gameName  游戏名称，显示在对话框标题中
     * @param pages     教程页面列表，每页包含标题和描述
     */
    private static void showInteractiveTutorial(Context context, String gameName, List<InteractiveTutorialDialog.TutorialPage> pages) {
        InteractiveTutorialDialog dialog = new InteractiveTutorialDialog(context, gameName, pages);
        dialog.show();
    }

    /**
     * 显示五子棋新手教程（交互式）
     *
     * <p>包含欢迎、基本规则、游戏技巧和难度选择四个页面，
     * 帮助新手从零开始学会五子棋。</p>
     *
     * @param context 上下文对象
     */
    public static void showGomokuTutorial(Context context) {
        List<InteractiveTutorialDialog.TutorialPage> pages = new ArrayList<>();
        pages.add(new InteractiveTutorialDialog.TutorialPage("欢迎来到五子棋", "经典策略游戏，先连成五子者获胜！\n支持单机AI和联机对战模式。"));
        pages.add(new InteractiveTutorialDialog.TutorialPage("基本规则", "• 你是黑棋，先手落子\n• 点击棋盘交叉点落子\n• 横、竖、斜任意方向连成五子即获胜"));
        pages.add(new InteractiveTutorialDialog.TutorialPage("游戏技巧", "• 开局占据中心位置\n• 同时创造多条连线威胁\n• 注意防守对手的四子连线\n• 善用悔棋功能复盘学习"));
        pages.add(new InteractiveTutorialDialog.TutorialPage("难度选择", "• 入门：适合新手练习\n• 中等：有一定挑战\n• 大师：高手对决\n可随时调整难度"));
        showInteractiveTutorial(context, "五子棋", pages);
    }

    /**
     * 显示中国象棋新手教程（交互式）
     *
     * <p>包含欢迎、基本规则和棋子走法三个页面，重点讲解各棋子的移动规则。</p>
     *
     * @param context 上下文对象
     */
    public static void showChineseChessTutorial(Context context) {
        List<InteractiveTutorialDialog.TutorialPage> pages = new ArrayList<>();
        pages.add(new InteractiveTutorialDialog.TutorialPage("欢迎来到中国象棋", "千年传统棋类游戏，考验策略与布局！"));
        pages.add(new InteractiveTutorialDialog.TutorialPage("基本规则", "• 红方先行，轮流走棋\n• 点击己方棋子选中，有效位置会高亮\n• 点击高亮位置完成移动\n• 将/帅被将死或无路可走即输"));
        pages.add(new InteractiveTutorialDialog.TutorialPage("棋子走法", "• 帅/将：九宫格内一步\n• 仕/士：九宫格内斜走\n• 相/象：田字对角\n• 马：日字跳跃\n• 车：直线任意\n• 炮：直线隔子吃\n• 兵/卒：过河前前进一步"));
        showInteractiveTutorial(context, "中国象棋", pages);
    }

    /**
     * 显示贪吃蛇新手教程（交互式）
     *
     * <p>包含欢迎、操作方式和游戏规则三个页面。</p>
     *
     * @param context 上下文对象
     */
    public static void showSnakeTutorial(Context context) {
        List<InteractiveTutorialDialog.TutorialPage> pages = new ArrayList<>();
        pages.add(new InteractiveTutorialDialog.TutorialPage("欢迎来到贪吃蛇", "经典休闲游戏，挑战你的反应和策略！"));
        pages.add(new InteractiveTutorialDialog.TutorialPage("操作方式", "• 滑动手势控制方向\n• 或使用屏幕方向按钮\n• 蛇会持续向前移动"));
        pages.add(new InteractiveTutorialDialog.TutorialPage("游戏规则", "• 吃红色食物增加长度和分数\n• 撞墙或撞到自己游戏结束\n• 难度越高，移动速度越快"));
        showInteractiveTutorial(context, "贪吃蛇", pages);
    }

    /**
     * 显示俄罗斯方块新手教程（交互式）
     *
     * <p>包含欢迎、操作方式和游戏规则三个页面。</p>
     *
     * @param context 上下文对象
     */
    public static void showTetrisTutorial(Context context) {
        List<InteractiveTutorialDialog.TutorialPage> pages = new ArrayList<>();
        pages.add(new InteractiveTutorialDialog.TutorialPage("欢迎来到俄罗斯方块", "全球经典的益智游戏！"));
        pages.add(new InteractiveTutorialDialog.TutorialPage("操作方式", "• 上按钮：旋转方块\n• 左/右按钮：水平移动\n• 下按钮：加速下落"));
        pages.add(new InteractiveTutorialDialog.TutorialPage("游戏规则", "• 填满一行自动消除得分\n• 同时消除多行获得更高分\n• 方块堆到顶部游戏结束"));
        showInteractiveTutorial(context, "俄罗斯方块", pages);
    }

    /**
     * 显示2048新手教程（交互式）
     *
     * <p>包含欢迎、操作方式和游戏规则三个页面，重点说明数字合并机制。</p>
     *
     * @param context 上下文对象
     */
    public static void showGame2048Tutorial(Context context) {
        List<InteractiveTutorialDialog.TutorialPage> pages = new ArrayList<>();
        pages.add(new InteractiveTutorialDialog.TutorialPage("欢迎来到2048", "数字合并益智游戏，挑战你的思维极限！"));
        pages.add(new InteractiveTutorialDialog.TutorialPage("操作方式", "• 上下左右滑动屏幕\n• 所有数字方块会向滑动方向移动"));
        pages.add(new InteractiveTutorialDialog.TutorialPage("游戏规则", "• 相同数字碰撞会合并翻倍\n• 目标是合成2048\n• 每次滑动会随机生成新数字\n• 棋盘填满且无法合并时游戏结束"));
        showInteractiveTutorial(context, "2048", pages);
    }

    /**
     * 显示数独新手教程（交互式）
     *
     * <p>包含欢迎、基本规则和游戏技巧三个页面。</p>
     *
     * @param context 上下文对象
     */
    public static void showSudokuTutorial(Context context) {
        List<InteractiveTutorialDialog.TutorialPage> pages = new ArrayList<>();
        pages.add(new InteractiveTutorialDialog.TutorialPage("欢迎来到数独", "经典数字逻辑游戏！"));
        pages.add(new InteractiveTutorialDialog.TutorialPage("基本规则", "• 在9x9网格中填入1-9\n• 每行数字不能重复\n• 每列数字不能重复\n• 每个3x3宫格数字不能重复"));
        pages.add(new InteractiveTutorialDialog.TutorialPage("游戏技巧", "• 从已有数字最多的行/列开始\n• 使用排除法缩小范围\n• 善用笔记功能标记可能的数字"));
        showInteractiveTutorial(context, "数独", pages);
    }

    /**
     * 显示推箱子新手教程（简单文本）
     *
     * @param context 上下文对象
     */
    public static void showSokobanTutorial(Context context) {
        String rules = "📖 怎么玩：\n"
                + "• 点击方向按钮推动箱子\n"
                + "• 把所有箱子推到目标位置\n\n"
                + "🏆 怎么过关：\n"
                + "• 所有箱子到达目标点";
        showSimpleGameTutorial(context, "推箱子", rules);
    }

    /**
     * 显示打砖块新手教程（简单文本）
     *
     * @param context 上下文对象
     */
    public static void showBreakoutTutorial(Context context) {
        String rules = "📖 怎么玩：\n"
                + "• 控制挡板接住小球\n"
                + "• 小球反弹击碎砖块\n\n"
                + "🏆 怎么过关：\n"
                + "• 击碎所有砖块";
        showSimpleGameTutorial(context, "打砖块", rules);
    }

    /**
     * 显示打地鼠新手教程（简单文本）
     *
     * @param context 上下文对象
     */
    public static void showWhackTutorial(Context context) {
        String rules = "📖 怎么玩：\n"
                + "• 地鼠从洞里冒出来时点击\n"
                + "• 打到地鼠得分\n\n"
                + "🏆 怎么过关：\n"
                + "• 在时间内尽可能多得分";
        showSimpleGameTutorial(context, "打地鼠", rules);
    }

    /**
     * 显示消消乐新手教程（简单文本）
     *
     * @param context 上下文对象
     */
    public static void showMatchTutorial(Context context) {
        String rules = "📖 怎么玩：\n"
                + "• 交换相邻的元素\n"
                + "• 三个或以上相同连成线消除\n\n"
                + "🏆 怎么过关：\n"
                + "• 达到目标分数";
        showSimpleGameTutorial(context, "消消乐", rules);
    }

    /**
     * 显示21点新手教程（简单文本）
     *
     * @param context 上下文对象
     */
    public static void showBlackjackTutorial(Context context) {
        String rules = "📖 怎么玩：\n"
                + "• 目标是总和尽量接近21\n"
                + "• 超过21爆牌输\n"
                + "• 可以选择要牌或停牌\n\n"
                + "🏆 怎么过关：\n"
                + "• 比庄家更接近21";
        showSimpleGameTutorial(context, "21点", rules);
    }

    /**
     * 显示国际跳棋新手教程（简单文本）
     *
     * @param context 上下文对象
     */
    public static void showCheckersTutorial(Context context) {
        String rules = "📖 怎么玩：\n"
                + "• 斜向移动棋子\n"
                + "• 跳过对方棋子吃掉\n"
                + "• 到达对方底线升为王\n\n"
                + "🏆 怎么过关：\n"
                + "• 吃掉对方所有棋子";
        showSimpleGameTutorial(context, "国际跳棋", rules);
    }

    /**
     * 显示Flappy Bird新手教程（简单文本）
     *
     * @param context 上下文对象
     */
    public static void showFlappyTutorial(Context context) {
        String rules = "📖 怎么玩：\n"
                + "• 点击屏幕让小鸟飞起\n"
                + "• 避开管道\n\n"
                + "🏆 怎么过关：\n"
                + "• 穿过尽可能多的管道";
        showSimpleGameTutorial(context, "Flappy Bird", rules);
    }

    /**
     * 显示别踩白块新手教程（简单文本）
     *
     * @param context 上下文对象
     */
    public static void showTilesTutorial(Context context) {
        String rules = "📖 怎么玩：\n"
                + "• 只点黑块，不要点白块\n"
                + "• 点错游戏结束\n\n"
                + "🏆 怎么过关：\n"
                + "• 挑战速度和准确率";
        showSimpleGameTutorial(context, "别踩白块", rules);
    }

    /**
     * 显示飞机大战新手教程（简单文本）
     *
     * @param context 上下文对象
     */
    public static void showPlaneTutorial(Context context) {
        String rules = "📖 怎么玩：\n"
                + "• 移动飞机躲避敌方子弹\n"
                + "• 自动发射子弹\n\n"
                + "🏆 怎么过关：\n"
                + "• 消灭敌机，挑战高分";
        showSimpleGameTutorial(context, "飞机大战", rules);
    }

    /**
     * 显示石头剪刀布新手教程（简单文本）
     *
     * @param context 上下文对象
     */
    public static void showRockTutorial(Context context) {
        String rules = "📖 怎么玩：\n"
                + "• 选择石头、剪刀或布\n"
                + "• 石头剪刀布，看谁能赢\n\n"
                + "🏆 怎么过关：\n"
                + "• 连续赢多局";
        showSimpleGameTutorial(context, "石头剪刀布", rules);
    }

    /**
     * 显示井字棋新手教程（简单文本）
     *
     * @param context 上下文对象
     */
    public static void showTicTutorial(Context context) {
        String rules = "📖 怎么玩：\n"
                + "• 你是X，先下\n"
                + "• 连成三子一线获胜\n\n"
                + "🏆 怎么过关：\n"
                + "• 先连成三子一线";
        showSimpleGameTutorial(context, "井字棋", rules);
    }

    /**
     * 显示翻牌子新手教程（简单文本）
     *
     * @param context 上下文对象
     */
    public static void showMemoryTutorial(Context context) {
        String rules = "📖 怎么玩：\n"
                + "• 翻开两张卡片\n"
                + "• 相同则配对成功\n\n"
                + "🏆 怎么过关：\n"
                + "• 配对所有卡片";
        showSimpleGameTutorial(context, "翻牌子", rules);
    }

    /**
     * 显示猜数字新手教程（简单文本）
     *
     * @param context 上下文对象
     */
    public static void showGuessTutorial(Context context) {
        String rules = "📖 怎么玩：\n"
                + "• 系统随机生成数字\n"
                + "• 猜测后提示大小\n\n"
                + "🏆 怎么过关：\n"
                + "• 用最少次数猜中";
        showSimpleGameTutorial(context, "猜数字", rules);
    }

    /**
     * 显示掷骰子新手教程（简单文本）
     *
     * @param context 上下文对象
     */
    public static void showDiceTutorial(Context context) {
        String rules = "📖 怎么玩：\n"
                + "• 点击骰子让它滚动\n"
                + "• 看谁的运气好\n\n"
                + "🏆 怎么过关：\n"
                + "• 纯粹的运气游戏";
        showSimpleGameTutorial(context, "掷骰子", rules);
    }

    /**
     * 显示反应力挑战新手教程（简单文本）
     *
     * @param context 上下文对象
     */
    public static void showReactionTutorial(Context context) {
        String rules = "📖 怎么玩：\n"
                + "• 快速点击出现的彩色方块\n"
                + "• 漏掉方块会减少生命\n\n"
                + "🏆 怎么过关：\n"
                + "• 挑战反应速度极限";
        showSimpleGameTutorial(context, "反应力挑战", rules);
    }

    /**
     * 显示围棋新手教程（交互式）
     *
     * <p>包含欢迎、基本规则和胜负判定三个页面。</p>
     *
     * @param context 上下文对象
     */
    public static void showGoTutorial(Context context) {
        List<InteractiveTutorialDialog.TutorialPage> pages = new ArrayList<>();
        pages.add(new InteractiveTutorialDialog.TutorialPage("欢迎来到围棋", "东方古老策略游戏，博大精深！"));
        pages.add(new InteractiveTutorialDialog.TutorialPage("基本规则", "• 黑方先行，轮流落子\n• 棋子下在交叉点上\n• 被完全包围的棋子会被吃掉\n• 不能下在无气的位置"));
        pages.add(new InteractiveTutorialDialog.TutorialPage("胜负判定", "• 围地多者获胜\n• 地盘 = 围住的空点 + 吃掉的棋子\n• 贴目：黑方需额外补偿白方"));
        showInteractiveTutorial(context, "围棋", pages);
    }

    /**
     * 显示水管工新手教程（简单文本）
     *
     * @param context 上下文对象
     */
    public static void showPipelineTutorial(Context context) {
        String rules = "📖 怎么玩：\n"
                + "• 旋转水管碎片\n"
                + "• 连接水管让水流通过\n\n"
                + "🏆 怎么过关：\n"
                + "• 连接成完整的管道";
        showSimpleGameTutorial(context, "水管工", rules);
    }

    /**
     * 显示华容道新手教程（简单文本）
     *
     * @param context 上下文对象
     */
    public static void showKlotskiTutorial(Context context) {
        String rules = "📖 怎么玩：\n"
                + "• 按住方块滑动移动\n"
                + "• 上下左右四个方向\n"
                + "• 曹操(2×2)通过下方开口逃出\n"
                + "• 关羽(2×1横)挡住必经之路\n\n"
                + "🏆 怎么过关：\n"
                + "• 把曹操移到正下方出口";
        showSimpleGameTutorial(context, "华容道", rules);
    }

    /**
     * 显示Brotato新手教程（简单文本）
     *
     * @param context 上下文对象
     */
    public static void showBrotatoTutorial(Context context) {
        String rules = "📖 怎么玩：\n"
                + "• 控制土豆移动\n"
                + "• 收集道具升级\n"
                + "• 消灭敌人\n\n"
                + "🏆 怎么过关：\n"
                + "• 活过每一波敌人";
        showSimpleGameTutorial(context, "Brotato", rules);
    }

    /**
     * 显示通用简单游戏教程
     *
     * <p>使用MaterialAlertDialogBuilder构建标准对话框，展示游戏规则文本。
     * 就像一张简单的说明书卡片，所有信息都在一页上。
     * 对话框底部统一附加"如何反馈"引导，方便用户通过设置页面提交意见。</p>
     *
     * @param context   上下文对象
     * @param gameName  游戏名称，用于对话框标题和教程标题
     * @param rules     游戏规则文本，支持换行符
     */
    public static void showSimpleGameTutorial(Context context, String gameName, String rules) {
        // 拼接完整的教程文本：标题 + 规则 + 反馈引导
        String tutorial = "🎮 【" + gameName + " 新手教程】\n\n"
                + rules + "\n\n"
                + "💬 如何反馈：\n"
                + "• 返回游戏大厅\n"
                + "• 点击右上角「设置」\n"
                + "• 选择意见反馈\n"
                + "• 使用邮箱发送你的建议";
        // 使用Material Design风格的对话框显示教程
        new MaterialAlertDialogBuilder(context)
                .setTitle(gameName + " 新手教程")
                .setMessage(tutorial)
                .setPositiveButton("明白了", null)
                .show();
    }
}
