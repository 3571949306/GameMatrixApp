package com.gamecenter.app.home

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gamecenter.app.R
import com.gamecenter.app.games.GameRegistry
import com.gamecenter.app.games.RecentGamesManager
import com.gamecenter.app.games.ui.GameLauncherHelper
import com.gamecenter.app.ui.theme.GameMatrixTheme
import java.util.Calendar

/**
 * Compose 版首页（首页重写试点）。
 *
 * 与原 [com.gamecenter.app.GamesFragment] 并存：
 * - 旧首页仍走 View + XML，保证存量功能不回归
 * - 新首页用 Compose + GameMatrixTheme 试点新栈
 *
 * 数据源 & 启动逻辑完全复用既有设施，零业务逻辑重写：
 * - 游戏列表：GameRegistry.getCategories(context)（静态 + 动态 + 插件合并）
 * - 游戏启动：GameLauncherHelper.launchGameWithDialog(context, id)（统一处理模块下载/难度选择）
 *
 * 覆盖原首页 3 层信息架构的核心层：
 * L1 Hero（问候 + 搜索）→ L2 分类筛选（FilterChip）→ L3 游戏网格（LazyVerticalGrid）
 */
class HomeComposeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GameMatrixTheme {
                HomeRoot(onBack = { finish() }, onGameClick = ::onGameClick)
            }
        }
    }

    private fun onGameClick(entry: GameRegistry.Entry) {
        val ctx: Context = this
        RecentGamesManager.getInstance(ctx).recordPlay(entry.id)
        val ok = GameLauncherHelper.launchGameWithDialog(ctx, entry.id)
        if (!ok) {
            Toast.makeText(
                ctx,
                ctx.getString(R.string.error_game_launch_failed_format, entry.name),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeRoot(onBack: () -> Unit, onGameClick: (GameRegistry.Entry) -> Unit) {
    val context = LocalContext.current
    val categories = remember(context) { GameRegistry.getCategories(context) }
    // 扁平化全部游戏（index 唯一性用于网格 key）
    val allGames = remember(categories) { GameRegistry.flatten(categories) }

    var query by remember { mutableStateOf("") }
    var categoryKey by remember { mutableStateOf<String?>(null) }

    val filtered = remember(query, categoryKey, allGames) {
        allGames.filter { e ->
            val catOk = categoryKey == null || e.categoryKey == categoryKey
            val q = query.trim()
            val searchOk = q.isEmpty() || e.name.contains(q, ignoreCase = true) || e.desc.contains(q, ignoreCase = true)
            catOk && searchOk
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                navigationIcon = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_back),
                        contentDescription = stringResource(R.string.back),
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .clickable(onClick = onBack)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // ===== L1 Hero: 问候语 + 搜索 =====
            HeroSection(
                query = query,
                onQueryChange = { query = it },
                modifier = Modifier.fillMaxWidth()
            )

            // ===== L2 分类筛选 =====
            CategoryFilter(
                categories = categories,
                selected = categoryKey,
                onSelect = { categoryKey = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )

            // ===== L3 游戏网格 =====
            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.no_results),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(items = filtered, key = { it.id }) { entry ->
                        GameCard(entry, onClick = { onGameClick(entry) })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HeroSection(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.primary)
            .padding(20.dp)
    ) {
        Text(
            text = greetingText(),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onPrimary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.home_top_bar_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(stringResource(R.string.search_hint), color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f))
            },
            leadingIcon = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_search),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            keyboardOptions = KeyboardOptions.Default,
            keyboardActions = KeyboardActions.Default,
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                unfocusedBorderColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f),
                focusedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                unfocusedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                focusedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                unfocusedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                cursorColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryFilter(
    categories: List<GameRegistry.Category>,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 「全部」
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.all)) }
        )
        for (category in categories) {
            FilterChip(
                selected = selected == category.categoryKey,
                onClick = { onSelect(category.categoryKey) },
                label = { Text(category.name) }
            )
        }
    }
}

@Composable
private fun GameCard(
    entry: GameRegistry.Entry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = painterResource(id = entry.iconRes),
                contentDescription = entry.name,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = entry.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = entry.desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** 依时段生成问候语（与原 GamesFragment.updateGreeting 逻辑一致）。 */
@Composable
private fun greetingText(): String {
    return when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> stringResource(R.string.home_top_bar_greeting_morning)
        in 12..17 -> stringResource(R.string.home_top_bar_greeting_afternoon)
        else -> stringResource(R.string.home_top_bar_greeting_evening)
    }
}
