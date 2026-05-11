package com.gamecenter.app.games.klotski;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class KlotskiGame {
    public static final int BLOCK_CAOCAO = 0;
    public static final int BLOCK_VERTICAL = 1;
    public static final int BLOCK_HORIZONTAL = 2;
    public static final int BLOCK_SOLDIER = 3;

    public static final int BOARD_WIDTH = 4;
    public static final int BOARD_HEIGHT = 5;

    private static final int NUM_BLOCKS = 10;
    private static final int MAX_SEARCH_NODES = 900000;
    private static final int[][] DIRECTIONS = {{0, 1}, {1, 0}, {-1, 0}, {0, -1}};

    private final List<Block> blocks = new ArrayList<>();
    private int moves = 0;

    public static class Block {
        public final int id;
        public int x;
        public int y;
        public final int width;
        public final int height;
        public final int type;
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

    public static class HintResult {
        public final int blockId;
        public final int dx;
        public final int dy;
        public final int totalSteps;

        HintResult(int blockId, int dx, int dy, int totalSteps) {
            this.blockId = blockId;
            this.dx = dx;
            this.dy = dy;
            this.totalSteps = totalSteps;
        }
    }

    private static class Move {
        final int blockId;
        final int dx;
        final int dy;

        Move(int blockId, int dx, int dy) {
            this.blockId = blockId;
            this.dx = dx;
            this.dy = dy;
        }
    }

    private static class Node {
        final int[] positions;
        final Node parent;
        final Move move;
        final int depth;

        Node(int[] positions, Node parent, Move move, int depth) {
            this.positions = positions;
            this.parent = parent;
            this.move = move;
            this.depth = depth;
        }
    }

    public KlotskiGame() {
        reset();
    }

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

    public List<Block> getBlocks() {
        return blocks;
    }

    public Block getBlockAt(int x, int y) {
        for (Block block : blocks) {
            if (x >= block.x && x < block.x + block.width
                    && y >= block.y && y < block.y + block.height) {
                return block;
            }
        }
        return null;
    }

    public int getMoves() {
        return moves;
    }

    public boolean moveBlock(Block block, int dx, int dy) {
        if (block == null || Math.abs(dx) + Math.abs(dy) != 1) {
            return false;
        }
        if (!canMove(block, dx, dy)) {
            return false;
        }
        block.x += dx;
        block.y += dy;
        moves++;
        return true;
    }

    public boolean canMove(Block block, int dx, int dy) {
        if (block == null || Math.abs(dx) + Math.abs(dy) != 1) {
            return false;
        }
        int[] positions = encodePositions();
        return canPlace(positions, block.id, block.x + dx, block.y + dy);
    }

    public boolean isWon() {
        return isGoalPosition(encodePositions());
    }

    public void shuffle() {
        reset();
        Random random = new Random();
        for (int step = 0; step < 120; step++) {
            List<Move> legalMoves = getLegalMoves(encodePositions());
            if (legalMoves.isEmpty()) break;
            Move chosen = legalMoves.get(random.nextInt(legalMoves.size()));
            moveBlock(blocks.get(chosen.blockId), chosen.dx, chosen.dy);
        }
        if (isWon()) {
            reset();
        }
        moves = 0;
    }

    public String serializeState() {
        StringBuilder sb = new StringBuilder();
        sb.append(moves);
        for (Block block : blocks) {
            sb.append(',').append(block.x).append(',').append(block.y);
        }
        return sb.toString();
    }

    public boolean restoreState(String data) {
        if (data == null || data.trim().isEmpty()) return false;
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

    public String serializeBoardState() {
        StringBuilder sb = new StringBuilder();
        for (Block block : blocks) {
            if (sb.length() > 0) sb.append(',');
            sb.append(block.x).append(',').append(block.y);
        }
        return sb.toString();
    }

    public HintResult getHint() {
        List<Move> path = solveFromCurrent();
        if (path == null || path.isEmpty()) {
            return null;
        }
        Move first = path.get(0);
        return new HintResult(first.blockId, first.dx, first.dy, path.size());
    }

    public List<int[]> getSolutionPath() {
        List<Move> moves = solveFromCurrent();
        if (moves == null) return null;
        List<int[]> result = new ArrayList<>(moves.size());
        for (Move move : moves) {
            result.add(new int[]{move.blockId, move.dx, move.dy});
        }
        return result;
    }

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
                int[] nextPositions = current.positions.clone();
                int nextX = unpackX(nextPositions[move.blockId]) + move.dx;
                int nextY = unpackY(nextPositions[move.blockId]) + move.dy;
                nextPositions[move.blockId] = packPosition(nextX, nextY);

                long key = stateKey(nextPositions);
                if (!visited.add(key)) continue;

                Node next = new Node(nextPositions, current, move, current.depth + 1);
                if (isGoalPosition(nextPositions)) {
                    return reconstructPath(next);
                }
                queue.addLast(next);
            }
        }
        return null;
    }

    private List<Move> reconstructPath(Node goalNode) {
        List<Move> path = new ArrayList<>();
        Node node = goalNode;
        while (node != null && node.move != null) {
            path.add(0, node.move);
            node = node.parent;
        }
        return path;
    }

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

    private boolean canPlace(int[] positions, int movingBlockId, int x, int y) {
        int width = getBlockWidth(movingBlockId);
        int height = getBlockHeight(movingBlockId);
        if (x < 0 || x + width > BOARD_WIDTH || y < 0 || y + height > BOARD_HEIGHT) {
            return false;
        }
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

    private boolean isValidBoard(int[] positions) {
        for (int i = 0; i < NUM_BLOCKS; i++) {
            if (!canPlaceIgnoringSelf(positions, i)) return false;
        }
        return true;
    }

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

    private boolean overlaps(int x1, int y1, int w1, int h1,
                             int x2, int y2, int w2, int h2) {
        return x1 < x2 + w2 && x1 + w1 > x2 && y1 < y2 + h2 && y1 + h1 > y2;
    }

    private int[] encodePositions() {
        int[] positions = new int[NUM_BLOCKS];
        for (int i = 0; i < NUM_BLOCKS; i++) {
            Block block = blocks.get(i);
            positions[i] = packPosition(block.x, block.y);
        }
        return positions;
    }

    private void applyPositions(int[] positions) {
        for (int i = 0; i < NUM_BLOCKS; i++) {
            blocks.get(i).x = unpackX(positions[i]);
            blocks.get(i).y = unpackY(positions[i]);
        }
    }

    private void restorePositions(int[] positions, int restoredMoves) {
        applyPositions(positions);
        moves = restoredMoves;
    }

    private boolean isGoalPosition(int[] positions) {
        return unpackX(positions[0]) == 1 && unpackY(positions[0]) == 3;
    }

    private int getBlockWidth(int blockId) {
        return blocks.get(blockId).width;
    }

    private int getBlockHeight(int blockId) {
        return blocks.get(blockId).height;
    }

    private int packPosition(int x, int y) {
        return x | (y << 3);
    }

    private int unpackX(int packed) {
        return packed & 0x7;
    }

    private int unpackY(int packed) {
        return (packed >> 3) & 0x7;
    }

    private long stateKey(int[] positions) {
        int[] verticals = {
                cellIndex(positions[1]),
                cellIndex(positions[2]),
                cellIndex(positions[3]),
                cellIndex(positions[4])
        };
        int[] soldiers = {
                cellIndex(positions[6]),
                cellIndex(positions[7]),
                cellIndex(positions[8]),
                cellIndex(positions[9])
        };
        Arrays.sort(verticals);
        Arrays.sort(soldiers);

        long key = cellIndex(positions[0]);
        key |= ((long) cellIndex(positions[5])) << 5;
        for (int i = 0; i < verticals.length; i++) {
            key |= ((long) verticals[i]) << (10 + i * 5);
        }
        for (int i = 0; i < soldiers.length; i++) {
            key |= ((long) soldiers[i]) << (30 + i * 5);
        }
        return key;
    }

    private int cellIndex(int packedPosition) {
        return unpackY(packedPosition) * BOARD_WIDTH + unpackX(packedPosition);
    }
}
