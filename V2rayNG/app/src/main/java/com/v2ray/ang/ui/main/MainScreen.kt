package com.v2ray.ang.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.ui.compose.LocalDarkTheme
import com.v2ray.ang.ui.compose.QRCodeDialog
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

enum class MainBottomTab {
    Home,
    Subscriptions,
    Settings
}

@Composable
fun MainScreen(
    mainViewModel: MainViewModel,
    onAction: (MainAction) -> Unit,
    onNavigate: (MainDestination) -> Unit,
) {
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val groups = uiState.groups
    val isLoading by mainViewModel.isLoading.collectAsStateWithLifecycle()
    val isRunning = uiState.isRunning
    val displayText = mainViewModel.formatStatus(uiState.status)
    val selectedGuid = uiState.selectedGuid
    val doubleColumnDisplay = uiState.doubleColumnDisplay
    val confirmRemove = uiState.confirmRemove
    val shareQRCodeBitmap = uiState.shareQRCodeBitmap

    val isDarkTheme = LocalDarkTheme.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(MainBottomTab.Home) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showDelAllConfirm by remember { mutableStateOf(false) }
    var showDelDuplicateConfirm by remember { mutableStateOf(false) }
    var showDelInvalidConfirm by remember { mutableStateOf(false) }
    var showRemoveConfirm by remember { mutableStateOf<String?>(null) }

    var shareTarget by remember { mutableStateOf<Triple<String, ProfileItem, Boolean>?>(null) }
    val removeServer: (String) -> Unit = { guid ->
        if (confirmRemove) showRemoveConfirm = guid else onAction(MainAction.RemoveServer(guid))
    }

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { groups.size.coerceAtLeast(1) }
    )

    val lazyListStates = remember { mutableStateMapOf<String, LazyListState>() }
    val lazyGridStates = remember { mutableStateMapOf<String, LazyGridState>() }

    LaunchedEffect(groups) {
        val validGroupIds = groups.map { it.id }.toSet()
        lazyListStates.keys.retainAll(validGroupIds)
        lazyGridStates.keys.retainAll(validGroupIds)
    }

    LaunchedEffect(groups, uiState.selectedGroupId) {
        if (groups.isEmpty()) return@LaunchedEffect
        val selectedIndex = groups.indexOfFirst { it.id == uiState.selectedGroupId }
            .takeIf { it >= 0 } ?: 0
        if (!pagerState.isScrollInProgress && pagerState.settledPage != selectedIndex) {
            pagerState.scrollToPage(selectedIndex)
        }
    }

    val latestGroups by rememberUpdatedState(groups)

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                val currentGroups = latestGroups
                if (page in currentGroups.indices) {
                    onAction(MainAction.SelectGroup(currentGroups[page].id))
                }
            }
    }

    MainDialogs(
        showDelAllConfirm = showDelAllConfirm,
        onDismissDelAll = { showDelAllConfirm = false },
        onConfirmDelAll = { showDelAllConfirm = false; onAction(MainAction.RemoveAllServers) },
        showDelDuplicateConfirm = showDelDuplicateConfirm,
        onDismissDelDuplicate = { showDelDuplicateConfirm = false },
        onConfirmDelDuplicate = { showDelDuplicateConfirm = false; onAction(MainAction.RemoveDuplicateServers) },
        showDelInvalidConfirm = showDelInvalidConfirm,
        onDismissDelInvalid = { showDelInvalidConfirm = false },
        onConfirmDelInvalid = { showDelInvalidConfirm = false; onAction(MainAction.RemoveInvalidServers) },
        showRemoveConfirm = showRemoveConfirm,
        onDismissRemove = { showRemoveConfirm = null },
        onConfirmRemove = { guid -> showRemoveConfirm = null; onAction(MainAction.RemoveServer(guid)) }
    )

    if (shareTarget != null) {
        val (guid, profile, more) = shareTarget!!
        ShareMethodDialog(
            guid = guid,
            profile = profile,
            more = more,
            onDismiss = { shareTarget = null },
            onAction = onAction,
            onRemove = removeServer,
        )
    }
    if (shareQRCodeBitmap != null) {
        QRCodeDialog(bitmap = shareQRCodeBitmap, onDismiss = { onAction(MainAction.DismissQRCodeDialog) })
    }

    Scaffold(
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
        topBar = {
            if (selectedTab == MainBottomTab.Home) {
                MainTopBar(
                    isLoading = isLoading,
                    showSearch = showSearch,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { query: String ->
                        searchQuery = query
                        onAction(MainAction.Search(query))
                    },
                    onSearchClose = {
                        searchQuery = ""
                        onAction(MainAction.Search(""))
                        showSearch = false
                    },
                    onSearchToggle = { show: Boolean -> showSearch = show },
                    onAction = onAction,
                    onMoreMenuAction = { action ->
                        when (action) {
                            MainMoreMenuAction.RestartService -> onAction(MainAction.RestartService)
                            MainMoreMenuAction.DeleteAll -> showDelAllConfirm = true
                            MainMoreMenuAction.DeleteDuplicate -> showDelDuplicateConfirm = true
                            MainMoreMenuAction.DeleteInvalid -> showDelInvalidConfirm = true
                            MainMoreMenuAction.ExportAll -> onAction(MainAction.ExportAll)
                            MainMoreMenuAction.LocateSelected -> onAction(MainAction.LocateSelectedServer)
                            MainMoreMenuAction.SortByTestResults -> onAction(MainAction.SortByTestResults)
                            MainMoreMenuAction.TestAll -> onAction(MainAction.TestAllServers)
                            MainMoreMenuAction.TestAllRealPing -> onAction(MainAction.TestRealAllServers)
                            MainMoreMenuAction.UpdateSubscriptions -> onAction(MainAction.UpdateSubscriptions)
                        }
                    }
                )
            }
        },
        bottomBar = {
            Column(modifier = Modifier.navigationBarsPadding()) {
                if (selectedTab == MainBottomTab.Home) {
                    MainBottomBar(
                        displayText = displayText,
                        isRunning = isRunning,
                        isDarkTheme = isDarkTheme,
                        onAction = onAction
                    )
                }
                NavigationBar(
                    windowInsets = WindowInsets(0),
                    modifier = Modifier.height(56.dp)
                ) {
                    NavigationBarItem(
                        selected = selectedTab == MainBottomTab.Home,
                        onClick = { selectedTab = MainBottomTab.Home },
                        icon = { Icon(painterResource(R.drawable.ic_home_24dp), contentDescription = null) },
                        label = { Text(stringResource(R.string.title_home)) }
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = { onNavigate(MainDestination.Subscriptions) },
                        icon = { Icon(painterResource(R.drawable.ic_subscriptions_24dp), contentDescription = null) },
                        label = { Text(stringResource(R.string.title_sub_setting)) }
                    )
                    NavigationBarItem(
                        selected = selectedTab == MainBottomTab.Settings,
                        onClick = { selectedTab = MainBottomTab.Settings },
                        icon = { Icon(painterResource(R.drawable.ic_settings_24dp), contentDescription = null) },
                        label = { Text(stringResource(R.string.title_settings)) }
                    )
                }
            }
        },
        floatingActionButton = {},
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current

        when (selectedTab) {
            MainBottomTab.Settings -> {
                SettingsTabContent(
                    modifier = Modifier.padding(innerPadding),
                    onNavigate = onNavigate
                )
            }
            else -> {
                if (groups.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (groups.size > 1) {
                            GroupTabBar(
                                groups = groups,
                                selectedTabIndex = pagerState.currentPage.coerceIn(0, groups.lastIndex),
                                mainViewModel = mainViewModel,
                                onTabClick = { targetIndex ->
                                    scope.launch {
                                        pagerState.navigateToPageOptimized(
                                            targetPage = targetIndex,
                                            animateAdjacentPage = true
                                        )
                                    }
                                }
                            )
                        }

                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                            userScrollEnabled = true,
                            beyondViewportPageCount = 1,
                            key = { page -> groups.getOrNull(page)?.id ?: "group-page-$page" }
                        ) { page ->
                            val group = groups.getOrNull(page) ?: return@HorizontalPager

                            GroupPagerPage(
                                groupId = group.id,
                                mainViewModel = mainViewModel,
                                selectedGuid = selectedGuid,
                                locateTarget = uiState.locateTarget,
                                doubleColumnDisplay = doubleColumnDisplay,
                                searchQuery = searchQuery,
                                lazyListStates = lazyListStates,
                                lazyGridStates = lazyGridStates,
                                onSelectServer = { guid -> onAction(MainAction.SelectServer(guid)) },
                                onEditServer = { guid, profile -> onAction(MainAction.EditServer(guid, profile)) },
                                onShareServer = { guid, profile ->
                                    shareTarget = Triple(guid, profile, false)
                                },
                                onMoreServer = { guid, profile ->
                                    shareTarget = Triple(guid, profile, true)
                                },
                                onRemoveServer = removeServer,
                                contentPadding = PaddingValues(
                                    start = 0.dp,
                                    top = 0.dp,
                                    end = 0.dp,
                                    bottom = 80.dp
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
