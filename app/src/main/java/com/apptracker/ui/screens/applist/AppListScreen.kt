package com.apptracker.ui.screens.applist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.apptracker.data.model.AppInfo
import com.apptracker.data.model.AppCategory
import com.apptracker.data.db.entity.TrustLabel
import com.apptracker.ui.components.AppActionsBottomSheet
import com.apptracker.ui.components.AppIcon
import com.apptracker.ui.components.RiskBadge
import com.apptracker.ui.components.RiskScoreIndicator
import com.apptracker.ui.components.riskLevelFromScore
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(
    onAppClick: (String) -> Unit,
    viewModel: AppListViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val quickSearchKeywords = listOf(
        "high",
        "critical",
        "sideload",
        "trusted",
        "suspicious",
        "camera",
        "microphone",
        "location"
    )
    var showSortMenu by remember { mutableStateOf(false) }
    var selectedApp by remember { mutableStateOf<AppInfo?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.isLoading) {
        if (!state.isLoading) isRefreshing = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("All Apps") },
            actions = {
                // System apps toggle
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "System",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Switch(
                        checked = state.showSystemApps,
                        onCheckedChange = { viewModel.toggleSystemApps() }
                    )
                }

                // Sort menu
                Box {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.AutoMirrored.Filled.Sort, "Sort")
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        SortOption.entries.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        option.label,
                                        color = if (state.sortOption == option)
                                            MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    viewModel.onSortChange(option)
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }
            }
        )

        // Search bar
        SearchBar(
            inputField = {
                SearchBarDefaults.InputField(
                    query = state.searchQuery,
                    onQueryChange = { viewModel.onSearchQueryChange(it) },
                    onSearch = { },
                    expanded = false,
                    onExpandedChange = { },
                    placeholder = { Text("Search apps, permissions, trust labels...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") }
                )
            },
            expanded = false,
            onExpandedChange = { },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) { }

        if (!state.rememberSearchFilters) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Search filter memory is off. Filters reset after closing the app.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        viewModel.setSearchFilterMemoryEnabled(true)
                        scope.launch { snackbarHostState.showSnackbar("Search memory enabled") }
                    }) {
                        Text("Enable memory")
                    }
                }
            }
        }

        LazyRow(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(quickSearchKeywords) { keyword ->
                val selected = queryTokens(state.searchQuery).contains(keyword)
                FilterChip(
                    selected = selected,
                    onClick = {
                        viewModel.onSearchQueryChange(toggleQueryToken(state.searchQuery, keyword, selected))
                    },
                    label = { Text(keyword.replaceFirstChar { it.uppercase() }) }
                )
            }
        }

        val activeTokens = queryTokens(state.searchQuery)
        if (activeTokens.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Active filters: ${activeTokens.joinToString(" • ")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { viewModel.onSearchQueryChange("") }) {
                    Text("Clear all")
                }
            }
            LazyRow(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(activeTokens.toList()) { token ->
                    FilterChip(
                        selected = true,
                        onClick = {
                            viewModel.onSearchQueryChange(toggleQueryToken(state.searchQuery, token, true))
                        },
                        label = { Text("$token ×") }
                    )
                }
            }
        }

        if (state.isBeginnerMode) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                )
            ) {
                Text(
                    text = "Beginner Tip: Start with 'High Risk' filter (${state.highRiskThreshold}+). " +
                            "Risk score combines permissions, app behavior, battery, and network signals.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        // Filter chips
        LazyRow(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(FilterOption.entries.toList()) { filter ->
                FilterChip(
                    selected = state.filterOption == filter,
                    onClick = { viewModel.onFilterChange(filter) },
                    label = { Text(filter.label) },
                    leadingIcon = if (state.filterOption == filter) {
                        { Icon(Icons.Default.FilterList, null) }
                    } else null
                )
            }
        }

        val categories = remember(state.allApps) {
            state.allApps.map { it.category }.distinct().sortedBy { it.label }
        }
        LazyRow(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = state.selectedCategory == null,
                    onClick = { viewModel.onCategoryChange(null) },
                    label = { Text("All Categories") }
                )
            }
            items(categories) { category ->
                FilterChip(
                    selected = state.selectedCategory == category,
                    onClick = { viewModel.onCategoryChange(category) },
                    label = { Text(category.label) }
                )
            }
        }

        // App count
        val resultTokenCount = queryTokens(state.searchQuery).size
        Text(
            text = if (resultTokenCount > 0) {
                "${state.filteredApps.size} apps · $resultTokenCount active filter${if (resultTokenCount == 1) "" else "s"}"
            } else {
                "${state.filteredApps.size} apps"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        if (state.isLoading && !isRefreshing) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { isRefreshing = true; viewModel.loadApps() },
                state = pullToRefreshState,
                modifier = Modifier.fillMaxSize()
            ) {
                if (state.filteredApps.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.outlineVariant
                            )
                            Text(
                                "No apps found",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Try adjusting your search or filters",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(
                            items = state.filteredApps,
                            key = { it.packageName }
                        ) { app ->
                            AppListItem(
                                app = app,
                                trustLabel = state.trustLabels[app.packageName] ?: TrustLabel.UNKNOWN,
                                onClick = { onAppClick(app.packageName) },
                                onLongClick = { selectedApp = app }
                            )
                        }
                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }
                }
            }
        }

        SnackbarHost(hostState = snackbarHostState)
    }

    // Bottom sheet for quick actions
    selectedApp?.let { app ->
        val isWatched by viewModel.isWatched(app.packageName).collectAsState()
        AppActionsBottomSheet(
            app = app,
            isWatched = isWatched,
            onDismiss = { selectedApp = null },
            onViewDetails = { onAppClick(app.packageName) },
            onToggleWatch = {
                if (isWatched) viewModel.unwatchApp(app.packageName)
                else viewModel.watchApp(app)
            },
            onShareReport = { /* share handled in detail screen */ }
        )
    }
}

private fun queryTokens(query: String): Set<String> {
    return query
        .lowercase()
        .split(Regex("[\\s,]+"))
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toSet()
}

private fun toggleQueryToken(query: String, token: String, isSelected: Boolean): String {
    val mutable = queryTokens(query).toMutableSet()
    if (isSelected) mutable.remove(token) else mutable.add(token)
    return mutable.joinToString(" ")
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppListItem(
    app: AppInfo,
    trustLabel: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(icon = app.icon, contentDescription = app.appName, size = 44.dp)

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    val dangerousCount = app.permissions.count { it.isDangerous && it.isGranted }
                    if (dangerousCount > 0) {
                        Text(
                            text = "$dangerousCount dangerous",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    val totalPerms = app.permissions.size
                    Text(
                        text = "$totalPerms total perms",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = app.category.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Text(
                        text = trustLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = when (trustLabel) {
                            TrustLabel.TRUSTED -> MaterialTheme.colorScheme.primary
                            TrustLabel.SUSPICIOUS -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                RiskScoreIndicator(score = app.riskScore)
                RiskBadge(riskLevel = riskLevelFromScore(app.riskScore))
            }
        }
    }
}
