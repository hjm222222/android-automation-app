package com.example.myapplication

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.delay
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myapplication.script.model.SavedScript
import com.example.myapplication.ui.theme.MyApplicationTheme

@Composable
fun HomeRoute(
    viewModel: MainViewModel,
    onRequestOverlay: () -> Unit,
    onRequestScreenCapture: () -> Unit,
    onRequestAccessibility: () -> Unit,
    onOpenFloatingWorkspace: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var permissionShakeTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                MainEvent.ShakePermissionCard -> permissionShakeTrigger++
                is MainEvent.OpenFloatingWorkspace -> onOpenFloatingWorkspace(event.scriptId)
            }
        }
    }

    HomeScreen(
        state = state,
        onAddClick = viewModel::onAddClicked,
        onScriptClick = viewModel::onScriptClicked,
        onDeleteScript = viewModel::onScriptDeleted,
        onRequestOverlay = onRequestOverlay,
        onRequestScreenCapture = onRequestScreenCapture,
        onRequestAccessibility = onRequestAccessibility,
        permissionShakeTrigger = permissionShakeTrigger,
        modifier = modifier
    )
}

@Composable
fun HomeScreen(
    state: MainUiState,
    onAddClick: () -> Unit,
    onScriptClick: (String) -> Unit,
    onDeleteScript: (String) -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestScreenCapture: () -> Unit,
    onRequestAccessibility: () -> Unit,
    permissionShakeTrigger: Int = 0,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color(0xFFFFFBF5),
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddClick,
                containerColor = Color(0xFFFFE08A),
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Text(text = "+", fontSize = 32.sp, textAlign = TextAlign.Center)
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "自动化小精灵",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF345247),
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = "让重复的小事交给我吧",
                modifier = Modifier.padding(top = 4.dp),
                fontSize = 15.sp,
                color = Color(0xFF759087)
            )
            Spacer(modifier = Modifier.height(28.dp))
            WelcomeCard()
            Spacer(modifier = Modifier.height(30.dp))
            MainContent(
                state = state,
                onScriptClick = onScriptClick,
                onDeleteScript = onDeleteScript,
                onRequestOverlay = onRequestOverlay,
                onRequestAccessibility = onRequestAccessibility,
                onRequestScreenCapture = onRequestScreenCapture,
                permissionShakeTrigger = permissionShakeTrigger
            )
        }
    }
}

@Composable
private fun MainContent(
    state: MainUiState,
    onScriptClick: (String) -> Unit,
    onDeleteScript: (String) -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestScreenCapture: () -> Unit,
    onRequestAccessibility: () -> Unit,
    permissionShakeTrigger: Int
) {
    var scriptToDelete by remember { mutableStateOf<SavedScript?>(null) }

    if (scriptToDelete != null) {
        val script = scriptToDelete!!
        AlertDialog(
            onDismissRequest = { scriptToDelete = null },
            title = { Text("删除脚本") },
            text = { Text("确定删除“${script.name}”吗？") },
            confirmButton = {
                Text(
                    text = "删除",
                    modifier = Modifier
                        .padding(12.dp)
                        .clickable {
                            onDeleteScript(script.id)
                            scriptToDelete = null
                        },
                    color = Color(0xFFC83737)
                )
            },
            dismissButton = {
                Text(
                    text = "取消",
                    modifier = Modifier
                        .padding(12.dp)
                        .clickable { scriptToDelete = null }
                )
            }
        )
    }

    when (state.workspaceStatus) {
        WorkspaceStatus.READY -> {
            SectionTitle(text = "我的小任务")
            Spacer(modifier = Modifier.height(14.dp))
            if (state.scripts.isEmpty()) {
                EmptyTaskCard()
            } else {
                state.scripts.forEach { script ->
                    TaskCard(
                        script = script,
                        onClick = { onScriptClick(script.id) },
                        onDelete = { scriptToDelete = script }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }

        WorkspaceStatus.PREPARING -> WorkspaceLoadingCard()

        WorkspaceStatus.NEEDS_PERMISSION -> {
            SectionTitle(text = "权限准备")
            Spacer(modifier = Modifier.height(14.dp))
            PermissionCard(
                state = state,
                permissionShakeTrigger = permissionShakeTrigger,
                onRequestOverlay = onRequestOverlay,
                onRequestScreenCapture = onRequestScreenCapture,
                onRequestAccessibility = onRequestAccessibility
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF345247)
    )
}

@Composable
private fun WelcomeCard(modifier: Modifier = Modifier) {
    val title = "你好呀！"
    val subtitle = "我是你的自动小精灵，点击下面的加号，创建第一个小任务吧！"
    var typedTitle by remember { mutableStateOf("") }
    var typedSubtitle by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        delay(700)
        title.forEachIndexed { index, _ ->
            typedTitle = title.substring(0, index + 1)
            delay(110)
        }
        delay(220)
        subtitle.forEachIndexed { index, _ ->
            typedSubtitle = subtitle.substring(0, index + 1)
            delay(75)
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xFFFFE9A8),
        shape = RoundedCornerShape(28.dp),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SpriteFace()
            Spacer(modifier = Modifier.width(18.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = typedTitle,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF8A6420)
                )
                Text(
                    text = typedSubtitle,
                    modifier = Modifier.padding(top = 6.dp),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = Color(0xFF8B713C),
                    minLines = 2
                )
            }
        }
    }
}

@Composable
private fun SpriteFace() {
    Image(
        painter = painterResource(R.drawable.sprite_catgirl_circle),
        contentDescription = "小精灵头像",
        modifier = Modifier
            .size(84.dp)
            .clip(CircleShape)
    )
}

@Composable
private fun WorkspaceLoadingCard(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(22.dp),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 34.dp, horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(42.dp),
                color = Color(0xFFE3B84E),
                strokeWidth = 4.dp
            )
            Text(
                text = "小精灵正在准备工作区...",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF79551B)
            )
        }
    }
}

@Composable
private fun EmptyTaskCard(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(22.dp),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 34.dp, horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFFE9B7)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "+", fontSize = 28.sp, color = Color(0xFFE9A53B))
            }
            Text(
                text = "还没有小任务",
                modifier = Modifier.padding(top = 14.dp),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF53685F)
            )
            Text(
                text = "准备好时，从右下角开始创建吧",
                modifier = Modifier.padding(top = 5.dp),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                color = Color(0xFF91A39B)
            )
        }
    }
}

@Composable
private fun TaskCard(
    script: SavedScript,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = Color.White,
        shape = RoundedCornerShape(22.dp),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = script.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF53685F)
                )
                Text(
                    text = "${script.actions.size} 个动作",
                    modifier = Modifier.padding(top = 4.dp),
                    fontSize = 13.sp,
                    color = Color(0xFF91A39B)
                )
            }
            Text(
                text = "删除",
                modifier = Modifier
                    .padding(start = 12.dp)
                    .clickable(onClick = onDelete)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                color = Color(0xFFC83737),
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun PermissionCard(
    state: MainUiState,
    permissionShakeTrigger: Int,
    onRequestOverlay: () -> Unit,
    onRequestAccessibility: () -> Unit,
    onRequestScreenCapture: () -> Unit,
    modifier: Modifier = Modifier
) {
    var shakeOffset by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(permissionShakeTrigger) {
        if (permissionShakeTrigger > 0) {
            repeat(2) {
                shakeOffset = -7f
                delay(70)
                shakeOffset = 7f
                delay(70)
                shakeOffset = 0f
                delay(70)
            }
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { translationX = shakeOffset },
        color = Color.White,
        shape = RoundedCornerShape(22.dp),
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "先准备好这三项权限，就可以开始工作啦！",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF53685F)
            )
            Spacer(modifier = Modifier.height(10.dp))
            PermissionRow(
                icon = "⌁",
                title = "悬浮窗",
                description = "让小精灵陪伴在屏幕上",
                granted = state.permissions.overlayGranted,
                onClick = onRequestOverlay
            )
            PermissionRow(
                icon = "✦",
                title = "无障碍",
                description = "帮助小精灵完成自动化",
                granted = state.permissions.accessibilityGranted,
                onClick = onRequestAccessibility
            )
            PermissionRow(
                icon = "▣",
                title = "屏幕录制",
                description = "支持截图、找图和文字识别",
                granted = state.permissions.screenCaptureGranted,
                onClick = onRequestScreenCapture
            )
        }
    }
}

@Composable
private fun PermissionRow(
    icon: String,
    title: String,
    description: String,
    granted: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(if (granted) Color(0xFFFFD96A) else Color(0xFFFFF1C9)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = icon, fontSize = 20.sp, color = Color(0xFFD49B32))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF53685F)
            )
            Text(
                text = description,
                fontSize = 12.sp,
                color = Color(0xFF9AA8A1)
            )
        }
        Text(
            text = if (granted) "已授权" else "未授权",
            fontSize = 12.sp,
            color = if (granted) Color(0xFF79551B) else Color(0xFFD39A32),
            modifier = Modifier
                .clickable(onClick = onClick)
                .background(
                    if (granted) Color(0xFFFFD96A) else Color(0xFFFFF1C9),
                    RoundedCornerShape(20.dp)
                )
                .padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun HomeScreenPreview() {
    MyApplicationTheme(darkTheme = false, dynamicColor = false) {
        HomeScreen(
            state = MainUiState(workspaceStatus = WorkspaceStatus.READY),
            onAddClick = {},
            onScriptClick = {},
            onDeleteScript = {},
            onRequestOverlay = {},
            onRequestAccessibility = {},
            onRequestScreenCapture = {},
            permissionShakeTrigger = 0,
            modifier = Modifier
        )
    }
}
