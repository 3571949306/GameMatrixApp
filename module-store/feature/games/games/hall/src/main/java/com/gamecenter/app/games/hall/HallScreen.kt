package com.gamecenter.app.games.hall

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

/**
 * Phase 2.4: Compose 样板 — 游戏大厅 grid 屏
 *
 * 这是把 hall 屏从 View 系统迁到 Compose 的样板. 实际迁移:
 * 1. hall/build.gradle 加 Compose 依赖 (见 docs/COMPOSE_MIGRATION.md)
 * 2. 把 HallActivity 的 RecyclerView 换成这个 Composable
 * 3. 删 setContentView(R.layout.activity_hall) 那套
 *
 * Composable 设计原则:
 * - 无状态: 数据来自 ViewModel (collectAsState)
 * - 单一职责: HallScreen 负责布局, HallViewModel 负责数据
 * - 主题统一: 用 MaterialTheme (项目已经迁移到 Material 3)
 * - Preview 友好: 抽离静态数据让 @Preview 能跑
 */

data class GameInfo(
    val id: String,
    val name: String,
    val description: String,
    val iconEmoji: String = "🎮"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HallScreen(
    viewModel: HallViewModel,
    onGameClick: (String) -> Unit = {}
) {
    val games by viewModel.games.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("游戏大厅") }
            )
        }
    ) { padding ->
        HallGrid(
            games = games,
            contentPadding = padding,
            onGameClick = onGameClick
        )
    }
}

@Composable
fun HallGrid(
    games: List<GameInfo>,
    contentPadding: PaddingValues,
    onGameClick: (String) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 120.dp),
        contentPadding = contentPadding,
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(games, key = { it.id }) { game ->
            GameCard(game = game, onClick = { onGameClick(game.id) })
        }
    }
}

@Composable
fun GameCard(game: GameInfo, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.9f)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 占位 - 实际用 Glide/Coil 加载
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(game.iconEmoji, fontSize = 28.sp)
            }
            Text(
                text = game.name,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                text = game.description,
                fontSize = 11.sp,
                color = Color.Gray,
                maxLines = 2,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

// ============================================
// ViewModel
// ============================================

/**
 * Phase 2.4: 用 StateFlow 暴露给 Compose
 * 实际项目用 Hilt 注入 + @HiltViewModel
 */
class HallViewModel {
    val games = kotlinx.coroutines.flow.MutableStateFlow<List<GameInfo>>(emptyList())

    init {
        // Phase 2.4: 实际从 module store API 拉
        games.value = sampleGames()
    }

    private fun sampleGames() = listOf(
        GameInfo("gomoku", "五子棋", "经典对战"),
        GameInfo("snake", "贪吃蛇", "怀旧经典"),
        GameInfo("doudizhu", "斗地主", "三人对抗"),
        GameInfo("chinesechess", "中国象棋", "传统策略"),
        GameInfo("klotski", "华容道", "益智解谜"),
        GameInfo("go", "围棋", "策略巅峰")
    )
}

// ============================================
// Previews
// ============================================

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun HallGridPreview() {
    MaterialTheme {
        HallGrid(
            games = listOf(
                GameInfo("gomoku", "五子棋", "经典对战", "♟"),
                GameInfo("snake", "贪吃蛇", "怀旧经典", "🐍"),
                GameInfo("doudizhu", "斗地主", "三人对抗", "🃏")
            ),
            contentPadding = PaddingValues(8.dp),
            onGameClick = {}
        )
    }
}
