package com.gamecenter.app.games.klotski;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * 华容道游戏核心逻辑类
 *
 * <p>管理华容道的方块布局、移动规则、胜负判定和最优解搜索。
 * 棋盘为 4×5 的网格，包含 10 个不同大小的方块（曹操、关羽、将领和兵）。</p>
 *
 * <p>关键设计决策：
 * <ul>
 *   <li>使用 BFS（广度优先搜索）求解最优解，搜索节点上限 900000 以控制计算时间</li>
 *   <li>状态编码使用位压缩：x 占低 3 位，y 占高 3 位，一个 int 存储一个方块位置</li>
 *   <li>状态去重使用哈希键，同类型方块（竖将、兵）视为等价以大幅减少搜索空间</li>
 *   <li>打乱功能通过从初始状态执行 120 步随机合法移动实现，保证可解性</li>
 *   <li>获胜条件：曹操（2×2 方块）到达 (1,3) 位置，即棋盘底部中央出口</li>
 * </ul>
 * </p>
 */
public class KlotskiGame {
    /** 方块类型：曹操（2×2） */
    public static final int BLOCK_CAOCAO = 0;
    /** 方块类型：竖将（1×2） */
    public static final int BLOCK_VERTICAL = 1;
    /** 方块类型：横将（2×1） */
    public static final int BLOCK_HORIZONTAL = 2;
    /** 方块类型：兵（1×1） */
    public static final int BLOCK_SOLDIER = 3;

    /** 棋盘宽度（列数） */
    public static final int BOARD_WIDTH = 4;
    /** 棋盘高度（行数） */
    public static final int BOARD_HEIGHT = 5;

    /** 方块总数 */
    private static final int NUM_BLOCKS = 10;
    /** BFS 搜索节点上限，防止搜索时间过长 */
    private static final int MAX_SEARCH_NODES = 900000;
    /** 四个移动方向：右、下、左、上 */
    private static final int[][] DIRECTIONS = {{0, 1}, {1, 0}, {-1, 0}, {0, -1}};

    /** 所有方块的列表 */
    private final List<Block> blocks = new ArrayList<>();
    /** 当前步数 */
    private int moves = 0;

    /**
     * 方块数据类
     *
     * <p>表示棋盘上的一个方块，包含位置、尺寸、类型和名称。</p>
     */
    public static class Block {
        /** 方块唯一标识 */
        public final int id;
        /** 当前列坐标 */
        public int x;
        /** 当前行坐标 */
        public int y;
        /** 方块宽度（占列数） */
        public final int width;
        /** 方块高度（占行数） */
        public final int height;
        /** 方块类型 */
        public final int type;
        /** 方块显示名称 */
        public final String name;

        Block(int id, int x, int y, int width, int height, int type, String name) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.type = type;
            this.name = name;
        }
    }

    /**
     * 提示结果数据类
     *
     * <p>包含建议移动的方块 ID、移动方向和到目标的总步数。</p>
     */
    public static class HintResult {
        /** 建议移动的方块 ID */
        public final int blockId;
        /** 水平移动量 */
        public final int dx;
        /** 垂直移动量 */
        public final int dy;
        /** 从当前状态到获胜的最少步数 */
        public final int totalSteps;

        HintResult(int blockId, int dx, int dy, int totalSteps) {
            this.blockId = blockId;
            this.dx = dx;
            this.dy = dy;
            this.totalSteps = totalSteps;
        }
    }

    /**
     * 内部移动记录，用于 BFS 搜索路径追踪
     */
    private static class Move {
        /** 移动的方块 ID */
        final int blockId;
        /** 水平移动量 */
        final int dx;
        /** 垂直移动量 */
        final int dy;

        Move(int blockId, int dx, int dy) {
            this.blockId = blockId;
            this.dx = dx;
            this.dy = dy;
        }
    }

    /**
     * BFS 搜索节点，包含位置编码、父节点、移动记录和搜索深度
     */
    private static class Node {
        /** 所有方块位置的编码数组 */
        final int[] positions;
        /** 父节点（用于路径回溯） */
        final Node parent;
        /** 从父节点到本节点的移动 */
        final Move move;
        /** 搜索深度（即步数） */
        final int depth;

        Node(int[] positions, Node parent, Move move, int depth) {
            this.positions = positions;
            this.parent = parent;
            this.move = move;
            this.depth = depth;
        }
    }

    /**
     * 构造方法，初始化并重置棋盘
     */
    public KlotskiGame() {
        reset();
    }

    /**
     * 重置棋盘到经典华容道初始布局
     *
     * <p>经典"横刀立马"布局：
     * <pre>
     * 张 曹曹 赵
     * 飞 曹曹 云
     * 马 关关 黄
     * 超 兵兵 忠
     * 兵 空 空 兵
     * </pre></p>
     */
    public void reset() {
        moves = 0;
        blocks.clear();
        blocks.add(new Block(0, 1, 0, 2, 2, BLOCK_CAOCAO, "曹操"));
        blocks.add(new Block(1, 0, 0, 1, 2, BLOCK_VERTICAL, "张飞"));
        blocks.add(new Block(2, 3, 0, 1, 2, BLOCK_VERTICAL, "赵云"));
        blocks.add(new Block(3, 0, 2, 1, 2, BLOCK_VERTICAL, "马超"));
        blocks.add(new Block(4, 3, 2, 1, 2, BLOCK_VERTICAL, "黄忠"));
        blocks.add(new Block(5, 1, 2, 2, 1, BLOCK_HORIZONTAL, "关羽"));
        blocks.add(new Block(6, 1, 3, 1, 1, BLOCK_SOLDIER, "兵"));
        blocks.add(new Block(7, 2, 3, 1, 1, BLOCK_SOLDIER, "兵"));
        blocks.add(new Block(8, 0, 4, 1, 1, BLOCK_SOLDIER, "兵"));
        blocks.add(new Block(9, 3, 4, 1, 1, BLOCK_SOLDIER, "兵"));
    }

    /**
     * 获取所有方块列表
     *
     * @return 方块列表
     */
    public List<Block> getBlocks() {
        return blocks;
    }

    /**
     * 获取指定坐标上的方块
     *
     * @param x 列坐标
     * @param y 行坐标
     * @return 该坐标上的方块，如果为空则返回 null
     */
    public Block getBlockAt(int x, int y) {
        for (Block block : blocks) {
            if (x >= block.x && x < block.x + block.width
                    && y >= block.y && y < block.y + block.height) {
                return block;
            }
        }
        return null;
    }

    /**
     * 获取当前步数
     *
     * @return 已走步数
     */
    public int getMoves() {
        return moves;
    }

    /**
     * 移动指定方块
     *
     * <p>仅允许单步移动（dx + dy 的绝对值之和必须为 1），
     * 移动前检查目标位置是否可用。</p>
     *
     * @param block 要移动的方块
     * @param dx    水平移动量（-1/0/1）
     * @param dy    垂直移动量（-1/0/1）
     * @return 移动是否成功
     */
    public boolean moveBlock(Block block, int dx, int dy) {
        if (block == null || Math.abs(dx) + Math.abs(dy) != 1) {
            return false;
        }
        if (!canMove(block, dx, dy)) {
            return false;
        }
        // 2026-06-23: 记录走法历史（撤销用）
        moveHistory.push(new UndoRecord(block.id, block.x, block.y, dx, dy));
        block.x += dx;
        block.y += dy;
        moves++;
        return true;
    }

    // 2026-06-23: 撤销历史栈
    private final java.util.Deque<UndoRecord> moveHistory = new java.util.ArrayDeque<>();

    private static class UndoRecord {
        final int blockId;
        final int fromX;
        final int fromY;
        final int dx;
        final int dy;
        UndoRecord(int blockId, int fromX, int fromY, int dx, int dy) {
            this.blockId = blockId;
            this.fromX = fromX;
            this.fromY = fromY;
            this.dx = dx;
            this.dy = dy;
        }
    }

    /**
     * 2026-06-23: 撤销上一步。返回 true 表示成功撤销，false 无可撤销。
     */
    public boolean undoMove() {
        if (moveHistory.isEmpty()) return false;
        UndoRecord record = moveHistory.pop();
        Block block = findBlockById(record.blockId);
        if (block == null) return false;
        block.x = record.fromX;
        block.y = record.fromY;
        moves = Math.max(0, moves - 1);
        return true;
    }

    /**
     * 查找指定 id 的方块。
     */
    private Block findBlockById(int id) {
        for (Block b : blocks) {
            if (b.id == id) return b;
        }
        return null;
    }

    /**
     * 检查方块是否可以向指定方向移动
     *
     * @param block 要检查的方块
     * @param dx    水平移动量
     * @param dy    垂直移动量
     * @return 是否可以移动
     */
    public boolean canMove(Block block, int dx, int dy) {
        if (block == null || Math.abs(dx) + Math.abs(dy) != 1) {
            return false;
        }
        int[] positions = encodePositions();
        return canPlace(positions, block.id, block.x + dx, block.y + dy);
    }

    /**
     * 判断是否获胜
     *
     * <p>获胜条件：曹操（方块 0）位于 (1, 3) 位置，
     * 即棋盘底部中央的出口位置。</p>
     *
     * @return 是否获胜
     */
    public boolean isWon() {
        return isGoalPosition(encodePositions());
    }

    /**
     * 随机打乱棋盘（保证可解）
     *
     * <p>从初始状态出发，执行 120 步随机合法移动来打乱棋盘。
     * 这种方式保证打乱后的局面一定可以通过合法移动回到初始状态（从而可解）。
     * 如果打乱后恰好是获胜状态，则重置重来。</p>
     */
    public void shuffle() {
        reset();
        Random random = new Random();
        for (int step = 0; step < 120; step++) {
            List<Move> legalMoves = getLegalMoves(encodePositions());
            if (legalMoves.isEmpty()) break;
            Move chosen = legalMoves.get(random.nextInt(legalMoves.size()));
            moveBlock(blocks.get(chosen.blockId), chosen.dx, chosen.dy);
        }
        // 防止打乱后恰好是获胜状态
        if (isWon()) {
            reset();
        }
        moves = 0;
    }

    /**
     * 将游戏状态序列化为字符串
     *
     * <p>格式：步数,方块0的x,方块0的y,方块1的x,方块1的y,...</p>
     *
     * @return 序列化的状态字符串
     */
    public String serializeState() {
        StringBuilder sb = new StringBuilder();
        sb.append(moves);
        for (Block block : blocks) {
            sb.append(',').append(block.x).append(',').append(block.y);
        }
        return sb.toString();
    }

    /**
     * 从序列化字符串恢复游戏状态
     *
     * <p>解析状态数据并验证棋盘合法性。如果数据无效或棋盘不合法，
     * 则回滚到恢复前的状态。</p>
     *
     * @param data 序列化的状态字符串
     * @return 恢复是否成功
     */
    public boolean restoreState(String data) {
        if (data == null || data.trim().isEmpty()) return false;
        // 保存当前状态以便回滚
        int[] originalPositions = encodePositions();
        int originalMoves = moves;
        try {
            String[] parts = data.split(",");
            if (parts.length != 1 + blocks.size() * 2) return false;
            moves = Integer.parseInt(parts[0]);
            int[] restored = new int[NUM_BLOCKS];
            for (int i = 0; i < blocks.size(); i++) {
                int x = Integer.parseInt(parts[1 + i * 2]);
                int y = Integer.parseInt(parts[1 + i * 2 + 1]);
                restored[i] = packPosition(x, y);
            }
            // 验证恢复后的棋盘是否合法（无重叠、无越界）
            if (!isValidBoard(restored)) {
                restorePositions(originalPositions, originalMoves);
                return false;
            }
            applyPositions(restored);
            return true;
        } catch (Exception e) {
            restorePositions(originalPositions, originalMoves);
            return false;
        }
    }

    /**
     * 序列化棋盘状态（不含步数），用于比对棋盘是否变化
     *
     * @return 仅包含方块位置的序列化字符串
     */
    public String serializeBoardState() {
        StringBuilder sb = new StringBuilder();
        for (Block block : blocks) {
            if (sb.length() > 0) sb.append(',');
            sb.append(block.x).append(',').append(block.y);
        }
        return sb.toString();
    }

    /**
     * 获取当前局面的最优解提示
     *
     * <p>使用 BFS 搜索从当前状态到获胜状态的最短路径，
     * 返回第一步的移动建议和总步数。</p>
     *
     * @return 提示结果，如果未找到解法则返回 null
     */
    public HintResult getHint() {
        List<Move> path = solveFromCurrent();
        if (path == null || path.isEmpty()) {
            return null;
        }
        Move first = path.get(0);
        return new HintResult(first.blockId, first.dx, first.dy, path.size());
    }

    /**
     * 获取从当前状态到获胜的完整解法路径
     *
     * @return 移动序列（每项为 [blockId, dx, dy]），如果无解则返回 null
     */
    public List<int[]> getSolutionPath() {
        List<Move> moves = solveFromCurrent();
        if (moves == null) return null;
        List<int[]> result = new ArrayList<>(moves.size());
        for (Move move : moves) {
            result.add(new int[]{move.blockId, move.dx, move.dy});
        }
        return result;
    }

    /**
     * BFS 求解从当前状态到获胜的最短路径
     *
     * <p>搜索策略：
     * <ol>
     *   <li>将当前状态编码为位置数组作为起始节点</li>
     *   <li>如果当前已是获胜状态，返回空路径</li>
     *   <li>使用广度优先搜索遍历所有可达状态</li>
     *   <li>使用 stateKey 进行去重，同类型方块视为等价</li>
     *   <li>搜索节点数达到上限时停止，返回 null</li>
     *   <li>找到获胜状态后回溯路径</li>
     * </ol>
     * </p>
     *
     * @return 最短路径的移动列表，如果未找到则返回 null
     */
    private List<Move> solveFromCurrent() {
        int[] start = encodePositions();
        if (isGoalPosition(start)) return new ArrayList<>();

        ArrayDeque<Node> queue = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        queue.add(new Node(start, null, null, 0));
        visited.add(stateKey(start));

        int searched = 0;
        while (!queue.isEmpty() && searched < MAX_SEARCH_NODES) {
            Node current = queue.removeFirst();
            searched++;

            for (Move move : getLegalMoves(current.positions)) {
                // 计算移动后的新位置
                int[] nextPositions = current.positions.clone();
                int nextX = unpackX(nextPositions[move.blockId]) + move.dx;
                int nextY = unpackY(nextPositions[move.blockId]) + move.dy;
                nextPositions[move.blockId] = packPosition(nextX, nextY);

                long key = stateKey(nextPositions);
                // 去重：已访问过的状态跳过
                if (!visited.add(key)) continue;

                Node next = new Node(nextPositions, current, move, current.depth + 1);
                // 检查是否达到获胜状态
                if (isGoalPosition(nextPositions)) {
                    return reconstructPath(next);
                }
                queue.addLast(next);
            }
        }
        // 搜索超限，未找到解法
        return null;
    }

    /**
     * 从目标节点回溯到起始节点，重建完整移动路径
     *
     * @param goalNode 目标节点（获胜状态）
     * @return 从起始状态到目标的移动列表
     */
    private List<Move> reconstructPath(Node goalNode) {
        List<Move> path = new ArrayList<>();
        Node node = goalNode;
        while (node != null && node.move != null) {
            path.add(0, node.move);
            node = node.parent;
        }
        return path;
    }

    /**
     * 获取指定状态下所有合法移动
     *
     * <p>遍历所有方块和四个方向，检查每个移动是否合法。</p>
     *
     * @param positions 方块位置编码数组
     * @return 合法移动列表
     */
    private List<Move> getLegalMoves(int[] positions) {
        List<Move> moves = new ArrayList<>();
        for (int blockId = 0; blockId < NUM_BLOCKS; blockId++) {
            int x = unpackX(positions[blockId]);
            int y = unpackY(positions[blockId]);
            for (int[] direction : DIRECTIONS) {
                int nx = x + direction[0];
                int ny = y + direction[1];
                if (canPlace(positions, blockId, nx, ny)) {
                    moves.add(new Move(blockId, direction[0], direction[1]));
                }
            }
        }
        return moves;
    }

    /**
     * 检查指定方块是否可以放置到新位置
     *
     * <p>验证条件：
     * <ol>
     *   <li>新位置不超出棋盘边界</li>
     *   <li>新位置不与其他方块重叠</li>
     * </ol>
     * </p>
     *
     * @param positions      当前方块位置编码数组
     * @param movingBlockId  正在移动的方块 ID
     * @param x              新位置的列坐标
     * @param y              新位置的行坐标
     * @return 是否可以放置
     */
    private boolean canPlace(int[] positions, int movingBlockId, int x, int y) {
        int width = getBlockWidth(movingBlockId);
        int height = getBlockHeight(movingBlockId);
        // 边界检查
        if (x < 0 || x + width > BOARD_WIDTH || y < 0 || y + height > BOARD_HEIGHT) {
            return false;
        }
        // 重叠检查：与其他方块比较
        for (int otherId = 0; otherId < NUM_BLOCKS; otherId++) {
            if (otherId == movingBlockId) continue;
            int otherX = unpackX(positions[otherId]);
            int otherY = unpackY(positions[otherId]);
            if (overlaps(x, y, width, height,
                    otherX, otherY, getBlockWidth(otherId), getBlockHeight(otherId))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 验证整个棋盘的合法性（所有方块都在合法位置且互不重叠）
     *
     * @param positions 方块位置编码数组
     * @return 棋盘是否合法
     */
    private boolean isValidBoard(int[] positions) {
        for (int i = 0; i < NUM_BLOCKS; i++) {
            if (!canPlaceIgnoringSelf(positions, i)) return false;
        }
        return true;
    }

    /**
     * 检查指定方块在当前位置是否合法（忽略自身与其他方块的重叠检查）
     *
     * <p>与 canPlace 类似，但不需要排除自身，因为此方法用于验证
     * 每个方块是否与其他方块重叠。</p>
     *
     * @param positions 方块位置编码数组
     * @param blockId   要检查的方块 ID
     * @return 该方块位置是否合法
     */
    private boolean canPlaceIgnoringSelf(int[] positions, int blockId) {
        int x = unpackX(positions[blockId]);
        int y = unpackY(positions[blockId]);
        int width = getBlockWidth(blockId);
        int height = getBlockHeight(blockId);
        if (x < 0 || x + width > BOARD_WIDTH || y < 0 || y + height > BOARD_HEIGHT) {
            return false;
        }
        for (int otherId = 0; otherId < NUM_BLOCKS; otherId++) {
            if (otherId == blockId) continue;
            int otherX = unpackX(positions[otherId]);
            int otherY = unpackY(positions[otherId]);
            if (overlaps(x, y, width, height,
                    otherX, otherY, getBlockWidth(otherId), getBlockHeight(otherId))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断两个矩形是否重叠
     *
     * @param x1 第一个矩形的左上角 x
     * @param y1 第一个矩形的左上角 y
     * @param w1 第一个矩形的宽度
     * @param h1 第一个矩形的高度
     * @param x2 第二个矩形的左上角 x
     * @param y2 第二个矩形的左上角 y
     * @param w2 第二个矩形的宽度
     * @param h2 第二个矩形的高度
     * @return 是否重叠
     */
    private boolean overlaps(int x1, int y1, int w1, int h1,
                             int x2, int y2, int w2, int h2) {
        return x1 < x2 + w2 && x1 + w1 > x2 && y1 < y2 + h2 && y1 + h1 > y2;
    }

    /**
     * 将当前所有方块的位置编码为 int 数组
     *
     * <p>每个方块的位置使用 packPosition 编码为一个 int。</p>
     *
     * @return 位置编码数组
     */
    private int[] encodePositions() {
        int[] positions = new int[NUM_BLOCKS];
        for (int i = 0; i < NUM_BLOCKS; i++) {
            Block block = blocks.get(i);
            positions[i] = packPosition(block.x, block.y);
        }
        return positions;
    }

    /**
     * 将位置编码数组应用到方块列表
     *
     * @param positions 位置编码数组
     */
    private void applyPositions(int[] positions) {
        for (int i = 0; i < NUM_BLOCKS; i++) {
            blocks.get(i).x = unpackX(positions[i]);
            blocks.get(i).y = unpackY(positions[i]);
        }
    }

    /**
     * 恢复到指定的位置和步数（用于回滚）
     *
     * @param positions      要恢复的位置编码数组
     * @param restoredMoves  要恢复的步数
     */
    private void restorePositions(int[] positions, int restoredMoves) {
        applyPositions(positions);
        moves = restoredMoves;
    }

    /**
     * 判断是否为获胜位置
     *
     * <p>获胜条件：曹操（方块 0）位于 (1, 3)。</p>
     *
     * @param positions 位置编码数组
     * @return 是否获胜
     */
    private boolean isGoalPosition(int[] positions) {
        return unpackX(positions[0]) == 1 && unpackY(positions[0]) == 3;
    }

    /**
     * 获取指定方块的宽度
     *
     * @param blockId 方块 ID
     * @return 宽度（列数）
     */
    private int getBlockWidth(int blockId) {
        return blocks.get(blockId).width;
    }

    /**
     * 获取指定方块的高度
     *
     * @param blockId 方块 ID
     * @return 高度（行数）
     */
    private int getBlockHeight(int blockId) {
        return blocks.get(blockId).height;
    }

    /**
     * 将坐标打包为一个 int
     *
     * <p>x 占低 3 位，y 占第 3-5 位。由于棋盘最大 4×5，
     * 坐标值不会超过 3，3 位足够存储。</p>
     *
     * @param x 列坐标
     * @param y 行坐标
     * @return 打包后的位置编码
     */
    private int packPosition(int x, int y) {
        return x | (y << 3);
    }

    /**
     * 从打包的位置编码中解包列坐标
     *
     * @param packed 打包的位置编码
     * @return 列坐标
     */
    private int unpackX(int packed) {
        return packed & 0x7;
    }

    /**
     * 从打包的位置编码中解包行坐标
     *
     * @param packed 打包的位置编码
     * @return 行坐标
     */
    private int unpackY(int packed) {
        return (packed >> 3) & 0x7;
    }

    /**
     * 计算状态的哈希键，用于 BFS 去重
     *
     * <p>关键优化：同类型方块视为等价。
     * 四个竖将（1-4）的位置排序后编码，四个兵（6-9）的位置也排序后编码。
     * 这大幅减少了搜索空间，因为交换同类型方块的位置不产生新的有效状态。</p>
     *
     * <p>编码方式：将各部分的位置索引按位组合成一个 long 值。
     * 每个位置索引占 5 位（最大值 19 = 4*5-1）。</p>
     *
     * @param positions 位置编码数组
     * @return 状态的哈希键
     */
    private long stateKey(int[] positions) {
        // 竖将位置排序（等价类处理）
        int[] verticals = {
                cellIndex(positions[1]),
                cellIndex(positions[2]),
                cellIndex(positions[3]),
                cellIndex(positions[4])
        };
        // 兵位置排序（等价类处理）
        int[] soldiers = {
                cellIndex(positions[6]),
                cellIndex(positions[7]),
                cellIndex(positions[8]),
                cellIndex(positions[9])
        };
        Arrays.sort(verticals);
        Arrays.sort(soldiers);

        // 将各部分编码到一个 long 中
        long key = cellIndex(positions[0]);  // 曹操：bit 0-4
        key |= ((long) cellIndex(positions[5])) << 5;  // 关羽：bit 5-9
        for (int i = 0; i < verticals.length; i++) {
            key |= ((long) verticals[i]) << (10 + i * 5);  // 竖将：bit 10-29
        }
        for (int i = 0; i < soldiers.length; i++) {
            key |= ((long) soldiers[i]) << (30 + i * 5);  // 兵：bit 30-49
        }
        return key;
    }

    /**
     * 将打包的位置编码转换为一维单元格索引
     *
     * <p>索引 = y * BOARD_WIDTH + x，用于 stateKey 中的紧凑编码。</p>
     *
     * @param packedPosition 打包的位置编码
     * @return 一维单元格索引
     */
    private int cellIndex(int packedPosition) {
        return unpackY(packedPosition) * BOARD_WIDTH + unpackX(packedPosition);
    }
}
