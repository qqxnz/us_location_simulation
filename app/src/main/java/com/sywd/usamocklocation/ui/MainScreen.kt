package com.sywd.usamocklocation.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sywd.usamocklocation.EnvironmentStatus
import com.sywd.usamocklocation.data.StateCapital
import com.sywd.usamocklocation.data.StateCapitals
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    environmentStatus: EnvironmentStatus,
    onStart: (String) -> Unit,
    onStop: () -> Unit,
    onOpenDeveloperSettings: () -> Unit,
    onOpenLocationSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val selected = StateCapitals.find(state.selectedCode) ?: StateCapitals.all.first()
    val active = StateCapitals.find(state.activeCode)
    val filteredStates = remember(query) { StateCapitals.all.filter { it.matches(query) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("美国州府模拟定位", fontWeight = FontWeight.SemiBold)
                        Text(
                            "仅用于自有 App 定位测试",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { scaffoldPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                RunningStatusCard(active = active)
            }

            state.errorMessage?.let { message ->
                item {
                    ErrorCard(message = message, onDismiss = viewModel::clearError)
                }
            }

            item {
                SetupCard(
                    environmentStatus = environmentStatus,
                    onOpenDeveloperSettings = onOpenDeveloperSettings,
                    onOpenLocationSettings = onOpenLocationSettings,
                )
            }

            item {
                SelectedCapitalCard(
                    capital = selected,
                    isRunning = state.isRunning,
                    isActiveSelection = state.activeCode == selected.code,
                    onStart = { onStart(selected.code) },
                    onStop = onStop,
                )
            }

            item {
                Text(
                    "选择州或特区",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("搜索州、缩写或州府") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "清除搜索")
                            }
                        }
                    },
                )
            }

            if (filteredStates.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("没有匹配的州府", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(filteredStates, key = { it.code }) { capital ->
                    CapitalRow(
                        capital = capital,
                        selected = capital.code == selected.code,
                        active = capital.code == active?.code,
                        onClick = { viewModel.select(capital.code) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RunningStatusCard(active: StateCapital?) {
    val running = active != null
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (running) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = if (running) Icons.Default.LocationOn else Icons.Default.CheckCircle,
                contentDescription = null,
                tint = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column {
                Text(
                    if (running) "模拟定位运行中" else "当前未模拟定位",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    active?.let { "${it.stateNameZh} · ${it.capitalNameZh} (${it.code})" }
                        ?: "选择州府并点击开始模拟",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun SetupCard(
    environmentStatus: EnvironmentStatus,
    onOpenDeveloperSettings: () -> Unit,
    onOpenLocationSettings: () -> Unit,
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("运行条件", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            SetupRow("精确定位权限", environmentStatus.hasFineLocationPermission)
            SetupRow("系统定位开关", environmentStatus.isLocationEnabled)
            SetupRow("已设为模拟位置信息应用", environmentStatus.isSelectedMockApp)

            if (!environmentStatus.isLocationEnabled || !environmentStatus.isSelectedMockApp) {
                Divider()
                Text(
                    "首次使用：打开开发者选项 → 选择模拟位置信息应用 → 美国州府模拟定位。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!environmentStatus.isSelectedMockApp) {
                        FilledTonalButton(onClick = onOpenDeveloperSettings) {
                            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("开发者选项")
                        }
                    }
                    if (!environmentStatus.isLocationEnabled) {
                        FilledTonalButton(onClick = onOpenLocationSettings) {
                            Text("开启定位")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupRow(label: String, ready: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = if (ready) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (ready) Color(0xFF15803D) else MaterialTheme.colorScheme.error,
        )
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(
            if (ready) "已就绪" else "未就绪",
            style = MaterialTheme.typography.labelMedium,
            color = if (ready) Color(0xFF15803D) else MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun SelectedCapitalCard(
    capital: StateCapital,
    isRunning: Boolean,
    isActiveSelection: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("已选择", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(
                "${capital.stateNameZh} · ${capital.stateNameEn} (${capital.code})",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "州府：${capital.capitalNameZh} · ${capital.capitalNameEn}",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                String.format(Locale.US, "纬度 %.6f    经度 %.6f", capital.latitude, capital.longitude),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onStart, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text(if (isRunning && !isActiveSelection) "切换到此州府" else "开始模拟")
                }
                if (isRunning) {
                    FilledTonalButton(onClick = onStop) {
                        Icon(Icons.Default.Close, contentDescription = null)
                        Spacer(Modifier.size(4.dp))
                        Text("停止")
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Text(
                message,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "关闭错误")
            }
        }
    }
}

@Composable
private fun CapitalRow(
    capital: StateCapital,
    selected: Boolean,
    active: Boolean,
    onClick: () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${capital.stateNameZh} (${capital.code})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (active) {
                        Text(
                            "  · 运行中",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Text(
                    "${capital.stateNameEn} · ${capital.capitalNameZh} / ${capital.capitalNameEn}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
