package com.example.myapplication

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private enum class CreationMode {
    MANUAL,
    RECORD
}

@Composable
fun ScriptCreatorScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var scriptName by rememberSaveable { mutableStateOf("") }
    var selectedMode by rememberSaveable { mutableStateOf(CreationMode.MANUAL) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFFFFBF5))
            .padding(horizontal = 24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onBackClick,
                modifier = Modifier.size(width = 78.dp, height = 42.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(text = "返回")
            }
            Text(
                text = "创建脚本",
                modifier = Modifier
                    .padding(start = 16.dp)
                    .semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF345247)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "先给小任务取个名字吧",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF345247)
        )
        Text(
            text = "后面可以继续添加和整理自动化步骤",
            modifier = Modifier.padding(top = 6.dp),
            color = Color(0xFF759087),
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(22.dp))

        OutlinedTextField(
            value = scriptName,
            onValueChange = { scriptName = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("脚本名称") },
            placeholder = { Text("例如：每日签到") },
            shape = RoundedCornerShape(16.dp)
        )

        Spacer(modifier = Modifier.height(26.dp))
        Text(
            text = "制作方式",
            fontWeight = FontWeight.Bold,
            color = Color(0xFF345247)
        )
        Spacer(modifier = Modifier.height(12.dp))

        CreationModeOption(
            title = "手动添加步骤",
            description = "逐步设计小任务的执行流程",
            selected = selectedMode == CreationMode.MANUAL,
            onClick = { selectedMode = CreationMode.MANUAL }
        )
        Spacer(modifier = Modifier.height(10.dp))
        CreationModeOption(
            title = "录制操作",
            description = "功能准备中，后续支持录制操作步骤",
            selected = selectedMode == CreationMode.RECORD,
            onClick = { selectedMode = CreationMode.RECORD }
        )

        Spacer(modifier = Modifier.height(26.dp))
        Text(
            text = "步骤",
            fontWeight = FontWeight.Bold,
            color = Color(0xFF345247)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            color = Color(0xFFFFF1C9),
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(text = "还没有步骤", fontWeight = FontWeight.Bold, color = Color(0xFF6A5940))
                Text(
                    text = "脚本步骤编辑功能将在下一阶段接入",
                    modifier = Modifier.padding(top = 8.dp),
                    color = Color(0xFF907C5C),
                    fontSize = 14.sp
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onBackClick,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(text = "先返回")
            }
            Button(
                onClick = {},
                enabled = false,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(text = "保存脚本")
            }
        }
    }
}

@Composable
private fun CreationModeOption(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = if (selected) Color(0xFFFFE08A) else Color.White,
        shape = RoundedCornerShape(18.dp),
        tonalElevation = if (selected) 2.dp else 0.dp,
        shadowElevation = if (selected) 2.dp else 0.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (selected) "●" else "○",
                color = if (selected) Color(0xFF876A1D) else Color(0xFFA99B7E),
                fontSize = 22.sp
            )
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(text = title, fontWeight = FontWeight.Bold, color = Color(0xFF345247))
                Text(
                    text = description,
                    modifier = Modifier.padding(top = 3.dp),
                    color = Color(0xFF759087),
                    fontSize = 13.sp
                )
            }
        }
    }
}
