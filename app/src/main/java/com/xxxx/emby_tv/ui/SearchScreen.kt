package com.xxxx.emby_tv.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.xxxx.emby_tv.LocalServer
import com.xxxx.emby_tv.QrCodeUtils
import com.xxxx.emby_tv.R
import com.xxxx.emby_tv.R.*
import com.xxxx.emby_tv.data.repository.EmbyRepository
import com.xxxx.emby_tv.data.session.AccountInfo
import com.xxxx.emby_tv.ui.components.Loading
import com.xxxx.emby_tv.ui.home.ArtRole
import com.xxxx.emby_tv.ui.home.HomeImage
import com.xxxx.emby_tv.ui.home.HomeStyle
import com.xxxx.emby_tv.ui.home.HomeText
import com.xxxx.emby_tv.ui.motion.FocusCard
import com.xxxx.emby_tv.ui.components.TvInputDialog
import com.xxxx.emby_tv.ui.viewmodel.LoginViewModel
import com.xxxx.emby_tv.ui.viewmodel.MainViewModel
import com.xxxx.emby_tv.ui.focus.TvBringIntoView
import com.xxxx.emby_tv.ui.motion.ContentCrossfade
import com.xxxx.emby_tv.ui.motion.RestoreFocus
import com.xxxx.emby_tv.ui.motion.TvLazyGrid
import com.xxxx.emby_tv.ui.viewmodel.SearchViewModel
import com.xxxx.emby_tv.util.ErrorHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Search Screen with QR Code input and redesigned layout.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SearchScreen(
    searchViewModel: SearchViewModel,
    loginViewModel: LoginViewModel,
    mainViewModel: MainViewModel,
    onNavigateToSeries: (String) -> Unit,
) {
    val context = LocalContext.current
    val repository = remember { EmbyRepository.getInstance(context) }
    // State
    var showSearchDialog by remember { mutableStateOf(false) }
    val searchResults = searchViewModel.searchResults
    val isSearching = searchViewModel.isSearching
    // val isLoadingMore = searchViewModel.isLoadingMore // Removed in ViewModel
    // val hasMoreData = searchViewModel.hasMoreData // Removed in ViewModel
    val totalCount = searchViewModel.totalCount
    val currentQuery = searchViewModel.currentQuery
    val searchProgress = searchViewModel.searchProgress

    // Server & QR Code State
    var qrCodeBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var localServerAddress by remember { mutableStateOf("") }
    var localServer by remember { mutableStateOf<LocalServer?>(null) }

    // Account List
    val savedAccounts = loginViewModel.savedAccounts
    val currentAccountId = loginViewModel.currentAccountId

    // Sort accounts: Current account first
    val sortedAccounts = remember(savedAccounts, currentAccountId) {
        savedAccounts.sortedByDescending { it.id == currentAccountId }
    }

    // Focus
    val searchInputFocusRequester = remember { FocusRequester() }
    val returnFocus = remember { FocusRequester() }
    var returnId by rememberSaveable { mutableStateOf<String?>(null) }
    val gridState = rememberLazyGridState()
    val bringIntoView = remember { TvBringIntoView() }


    // Start Local Server for Search
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {

            val server = LocalServer.startSearchServer { query ->
                searchViewModel.search(query)
            }
            localServer = server
            if (server != null) {
                val ip = QrCodeUtils.getLocalIpAddress()
                if (ip != null) {
                    val address = "http://$ip:${server.listeningPort}"
                    localServerAddress = address
                    val bitmap = QrCodeUtils.generateQrCode(address, 200) // Smaller QR code
                    qrCodeBitmap = bitmap?.asImageBitmap()
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            localServer?.stop()
        }
    }

    // Error Handling
    LaunchedEffect(searchViewModel.errorMessage) {
        searchViewModel.errorMessage?.let { error ->
            android.widget.Toast.makeText(
                context,
                context.getString(R.string.search_failed, error),
                android.widget.Toast.LENGTH_SHORT
            ).show()
            searchViewModel.errorMessage = null
        }
    }

    val restoreIndex = searchResults?.indexOfFirst { it.item.id == returnId } ?: -1
    RestoreFocus(
        active = restoreIndex >= 0,
        index = restoreIndex,
        requester = returnFocus,
        scrollTo = { gridState.scrollToItem(it) },
    )
    LaunchedEffect(searchResults != null) {
        if (returnId != null) return@LaunchedEffect
        delay(100)
        try {
            searchInputFocusRequester.requestFocus()
        } catch (_: IllegalStateException) {
        }
    }
    LaunchedEffect(gridState, searchResults?.size) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last ->
                val results = searchResults.orEmpty()
                val urls = (last + 1 until (last + 10).coerceAtMost(results.size)).map { index ->
                    com.xxxx.emby_tv.Utils.getImageUrl(results[index].account.serverUrl, results[index].item, false)
                }
                com.xxxx.emby_tv.ui.home.ArtworkPrefetch.enqueue(
                    context,
                    urls,
                    com.xxxx.emby_tv.ui.home.ArtRole.Primary,
                )
            }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp)
    ) {
        // Left Sidebar: Account List
        if (true) {
            LazyColumn(
                modifier = Modifier
                    .width(250.dp)
                    .fillMaxHeight()
                    .padding(end = 20.dp),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {


                // QR Code (Small)
                if (qrCodeBitmap != null) {
                    item {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Image(
                                bitmap = qrCodeBitmap!!,
                                contentDescription = stringResource(R.string.scan_qr_search_hint),
                                modifier = Modifier
                                    .size(180.dp)
                                    .background(Color.White, RoundedCornerShape(12.dp))
                                    .padding(6.dp)
                            )
                            Text(
                                text = stringResource(R.string.scan_qr_search_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.padding(bottom = 10.dp, top = 10.dp),

                                )
                            Text(
                                text = "${stringResource(R.string.local_server_url)}: $localServerAddress",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }

                    }
                }

            }

            // Divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(Color.White.copy(alpha = 0.1f))
            )

            Spacer(modifier = Modifier.width(24.dp))
        }

        // Right Content: Search Input + Results
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            // Header: Search Input + QR Code
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .padding(bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Search Input Button
                Surface(
                    onClick = { showSearchDialog = true },
                    modifier = Modifier
                        .width(300.dp)
                        .height(56.dp)
                        .focusRequester(searchInputFocusRequester)
                        .onFocusChanged {
                            if (it.isFocused) {
                                // Reset filter when going back to search box
                                searchViewModel.setAccountFilter(null)
                            }
                        },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(28.dp)),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                    border = ClickableSurfaceDefaults.border(
                        border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))),
                        focusedBorder = Border(
                            BorderStroke(
                                2.dp,
                                MaterialTheme.colorScheme.primary
                            )
                        )
                    ),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.1f),
                        focusedContainerColor = Color.White.copy(alpha = 0.2f),
                        contentColor = Color.White
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = Color.White.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = if (currentQuery.isNotEmpty()) currentQuery else stringResource(
                                string.search_placeholder
                            ),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = if (currentQuery.isNotEmpty()) Color.White else Color.White.copy(
                                    alpha = 0.5f
                                )
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(24.dp))
                // Search Results Info
                if (searchResults != null) {
                    val progressText = if (isSearching) {
                        " | ${stringResource(R.string.searching)}: ${searchProgress.first}/${searchProgress.second}"
                    } else {
                        ""
                    }
                    Text(
                        text = "${searchResults.size} / $totalCount$progressText",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.5f),
                    )
                }
            }


            val resultsPhase = when {
                searchResults == null && isSearching -> "loading"
                searchResults?.isEmpty() == true -> "empty"
                searchResults != null -> "results"
                else -> "idle"
            }
            ContentCrossfade(targetState = resultsPhase, label = "search-results") { phase ->
            if (phase == "loading") {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Loading()
                }
            } else if (phase == "empty" || phase == "results") {
                if (phase == "empty") {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(string.no_search_results),
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                } else {
                    val items = searchResults.orEmpty()
                    val maxLength = 200.dp
                    // Calculate max aspect ratio based on items present
                    val maxAspectRatio = items.mapNotNull {
                        val ratio = it.item.primaryImageAspectRatio?.toFloat()
                        if (ratio == null || ratio == 1.0f) null else ratio
                    }.maxOrNull() ?: 0.666f

                    val imgWidth =
                        if (maxAspectRatio >= 1f) maxLength else (maxLength.value * maxAspectRatio).dp
                    val numStart = if (maxAspectRatio > 1) 4 else 5

                    TvLazyGrid(
                        columns = GridCells.Fixed(numStart),
                        state = gridState,
                        bringIntoView = bringIntoView,
                        contentPadding = PaddingValues(top = 10.dp, bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(items.size, key = { index ->
                            val item = items[index]
                            "${item.item.id}_${item.account.id}"
                        }, contentType = { "poster" }) { index ->
                            val resultModel = items[index]
                            val item = resultModel.item
                            val account = resultModel.account
                            val id = item.id ?: ""

                            if (id.isNotEmpty()) {
                                SearchPoster(
                                    item = item,
                                    serverUrl = account.serverUrl,
                                    modifier = if (item.id == returnId) {
                                        Modifier.fillMaxWidth().focusRequester(returnFocus)
                                    } else {
                                        Modifier.fillMaxWidth()
                                    },
                                    onClick = {
                                        returnId = id
                                        // Switch account if needed, then navigate
                                        if (account.id != currentAccountId) {
                                            loginViewModel.switchAccount(
                                                accountId = account.id,
                                                onSuccess = {
                                                    onNavigateToSeries(id)
                                                },
                                                onError = {
                                                    android.widget.Toast.makeText(
                                                        context,
                                                        "Switch account failed",
                                                        android.widget.Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            )
                                        } else {
                                            onNavigateToSeries(id)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
            }
        }
    }

    // Input Dialog
    if (showSearchDialog) {
        TvInputDialog(
            title = stringResource(string.search),
            initialValue = currentQuery,
            onConfirm = { query ->
                showSearchDialog = false
                searchViewModel.search(query)
            },
            onDismiss = { showSearchDialog = false },
            darkConfirm = true,
        )
    }
}

@Composable
private fun SearchPoster(
    item: com.xxxx.emby_tv.data.model.BaseItemDto,
    serverUrl: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        FocusCard(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .onFocusChanged { focused = it.isFocused }
                .focusable()
                .clickable(onClick = onClick),
            focused = focused,
            shape = shape,
            lift = true,
        ) {
            HomeImage(
                itemId = item.id,
                fallbackArtId = item.seriesId,
                url = if (serverUrl.isBlank()) "" else com.xxxx.emby_tv.Utils.getImageUrl(serverUrl, item, false),
                role = ArtRole.Primary,
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize(),
            )
        }
        HomeText(
            text = item.name.orEmpty(),
            fontSize = 14.sp,
            color = Color.White,
            maxLines = 1,
            modifier = Modifier.padding(top = 6.dp).fillMaxWidth(),
        )
        val year = item.productionYear?.toString().orEmpty()
        if (year.isNotBlank()) {
            HomeText(year, 12.sp, HomeStyle.meta, maxLines = 1, modifier = Modifier.fillMaxWidth())
        }
    }
}
