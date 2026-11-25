package com.xxxx.emby_tv

import Emby_tvTheme
import com.xxxx.emby_tv.ui.LibraryScreen
import com.xxxx.emby_tv.ui.detail.CollectionDetailPage
import com.xxxx.emby_tv.ui.detail.PersonDetailPage
import com.xxxx.emby_tv.ui.library.CollectionListPage
import com.xxxx.emby_tv.ui.library.FavoritesPage
import com.xxxx.emby_tv.ui.library.LibraryBrowseConnected
import com.xxxx.emby_tv.ui.LoginScreen
import com.xxxx.emby_tv.ui.PlayerScreen

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import com.xxxx.emby_tv.ui.motion.ScreenTransition
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.xxxx.emby_tv.ui.AccountScreen
import com.xxxx.emby_tv.ui.MediaDetailScreen
import com.xxxx.emby_tv.ui.HomeScreen
import com.xxxx.emby_tv.ui.UpdateScreen
import com.xxxx.emby_tv.ui.PlaybackSettingsScreen
import com.xxxx.emby_tv.ui.ProxySettingsScreen
import com.xxxx.emby_tv.ui.KeySettingsScreen
import com.xxxx.emby_tv.ui.SearchScreen
import com.xxxx.emby_tv.ui.components.BuildGradientBackground
import com.xxxx.emby_tv.ui.components.Loading
import com.xxxx.emby_tv.ui.theme.ThemeColorManager
import com.xxxx.emby_tv.ui.viewmodel.MainViewModel
import com.xxxx.emby_tv.ui.viewmodel.HomeViewModel
import com.xxxx.emby_tv.ui.viewmodel.LibraryBrowseViewModel
import com.xxxx.emby_tv.ui.viewmodel.LoginViewModel
import com.xxxx.emby_tv.ui.viewmodel.LibraryViewModel
import com.xxxx.emby_tv.ui.viewmodel.DetailViewModel
import com.xxxx.emby_tv.ui.viewmodel.PlayerViewModel
import com.xxxx.emby_tv.ui.viewmodel.SearchViewModel


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 强制设置为横屏模式
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        enableEdgeToEdge()
        setContent {
            val configuration = LocalConfiguration.current
            val system = LocalDensity.current
            val override = com.xxxx.emby_tv.ui.home.TvDensity.overrideDensity(
                widthPx = (configuration.screenWidthDp * system.density).toInt(),
                density = system.density,
            )
            if (override == null) {
                EmbyTvApp()
            } else {
                CompositionLocalProvider(
                    LocalDensity provides Density(override, system.fontScale),
                ) {
                    EmbyTvApp()
                }
            }
        }
    }
}

@Composable
fun EmbyTvApp() {
    val navController = rememberNavController()

    // 使用新的 MainViewModel
    val mainViewModel: MainViewModel = viewModel()

    // 响应式监听状态变化
    val isLoaded = mainViewModel.isLoaded
    val isLoggedIn = mainViewModel.isLoggedIn

    // 初始化加载
    LaunchedEffect(Unit) {
        mainViewModel.initialize()
    }

    // 监听状态变化并自动导航
    LaunchedEffect(isLoaded, isLoggedIn) {
        when {
            !isLoaded -> {
                if (navController.currentDestination?.route != "loading") {
                    navController.navigate("loading") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }

            isLoggedIn -> {
                if (navController.currentDestination?.route != "home") {
                    navController.navigate("home") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }

            else -> {
                if (navController.currentDestination?.route != "login") {
                    navController.navigate("login") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
        }
    }

    // 获取当前主题色
    val context = LocalContext.current
    val currentThemeColor =
        ThemeColorManager.getThemeColorById(context, mainViewModel.currentThemeId)

    Emby_tvTheme(themeColor = currentThemeColor) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            BuildGradientBackground(context = context, themeColor = currentThemeColor) {
                NavHost(
                    navController = navController,
                    startDestination = "loading",
                    enterTransition = { ScreenTransition.enter() },
                    exitTransition = { ScreenTransition.exit() },
                    popEnterTransition = { ScreenTransition.popEnter() },
                    popExitTransition = { ScreenTransition.popExit() },
                ) {
                    // 加载页面
                    composable("loading") {
                        Loading()
                    }

                    // 登录页面
                    composable("login") {
                        val loginViewModel: LoginViewModel = viewModel()
                        LoginScreen(
                            loginViewModel = loginViewModel,
                            mainViewModel = mainViewModel,
                            navController = navController
                        )
                    }

                    // 主页面
                    composable("home") { backStackEntry ->
                        val homeViewModel: HomeViewModel = viewModel()

                        // 使用 rememberSaveable 保存是否已经初始加载过
                        var isInitialLoaded by rememberSaveable { mutableStateOf(false) }

                        // 监听路由返回，延迟刷新
                        LaunchedEffect(backStackEntry) {
                            // 如果不是首次加载（说明是从其他页面返回），则刷新数据
                            if (isInitialLoaded) {
                                homeViewModel.loadData()
                            } else {
                                // 首次加载，不刷新
                                isInitialLoaded = true
                            }
                        }

                        HomeScreen(
                            homeViewModel = homeViewModel,
                            mainViewModel = mainViewModel,
                            navController = navController,
                            onSwitchAccount = {
                                navController.navigate("account")
                            }
                        )
                    }

                    // 账号管理页面
                    composable("account") {
                        val loginViewModel: LoginViewModel = viewModel()
                        AccountScreen(
                            mainViewModel = mainViewModel,
                            savedAccounts = loginViewModel.savedAccounts,
                            currentAccountId = loginViewModel.currentAccountId,
                            onSwitchAccount = { accountId ->
                                loginViewModel.switchAccount(
                                    accountId = accountId,
                                    onSuccess = {
                                        // 切换成功后回到首页并刷新
                                        navController.navigate("home") {
                                            popUpTo(0) { inclusive = true }
                                        }
                                    },
                                    onError = { /* 可选：显示错误提示 */ }
                                )
                            },
                            onDeleteAccount = { accountId ->
                                loginViewModel.removeAccount(accountId)
                            },
                            onAddAccount = {
                                // 退出当前账号，进入登录页面添加新账号
                                mainViewModel.logout()
                            }
                        )
                    }

                    // 播放设置页面
                    composable("settings") {
                        com.xxxx.emby_tv.ui.SettingsScreen(
                            onBack = { navController.popBackStack() },
                            currentVersion = mainViewModel.currentVersion,
                            onCheckUpdate = { onResult -> mainViewModel.checkForNewer(onResult) },
                            onInstallUpdate = { navController.navigate("update") },
                        )
                    }

                    composable("playback_settings") {
                        PlaybackSettingsScreen(
                            onBack = { navController.popBackStack() }
                        )
                    }

                    // 代理设置页面
                    composable("proxy_settings") {
                        ProxySettingsScreen(
                            mainViewModel = mainViewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    // 按键设置页面
                    composable("key_settings") {
                        KeySettingsScreen(
                            onBack = { navController.popBackStack() }
                        )
                    }

                    // 更新页面
                    composable("update") {
                        UpdateScreen(
                            mainViewModel = mainViewModel,
                            onBack = { navController.popBackStack() },
                        )
                    }

                    // 搜索页面
                    composable(
                        "collections/{libraryId}?libraryName={libraryName}",
                        arguments = listOf(
                            navArgument("libraryId") { type = NavType.StringType },
                            navArgument("libraryName") {
                                type = NavType.StringType
                                defaultValue = ""
                            },
                        ),
                    ) { backStackEntry ->
                        val libraryId = backStackEntry.arguments?.getString("libraryId").orEmpty()
                        val libraryName = backStackEntry.arguments?.getString("libraryName").orEmpty()
                        val context = androidx.compose.ui.platform.LocalContext.current
                        val repository = remember {
                            com.xxxx.emby_tv.data.repository.EmbyRepository.getInstance(context)
                        }
                        var boxes by remember { mutableStateOf<List<com.xxxx.emby_tv.data.model.BaseItemDto>?>(null) }
                        LaunchedEffect(libraryId) {
                            boxes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                try {
                                    repository.getLibraryList(
                                        libraryId,
                                        "BoxSet",
                                        0,
                                        200,
                                        "SortName",
                                        "Ascending",
                                    ).first
                                } catch (_: Exception) {
                                    emptyList()
                                }
                            }
                        }
                        CollectionListPage(
                            title = libraryName,
                            serverUrl = repository.serverUrl ?: "",
                            items = boxes,
                            onOpenItem = { item ->
                                val route = com.xxxx.emby_tv.util.EmbyPresentation.detailRoute(item)
                                if (route != null) navController.navigate(route)
                            },
                        )
                    }

                    composable(
                        "collection/{boxId}",
                        arguments = listOf(navArgument("boxId") { type = NavType.StringType }),
                    ) { backStackEntry ->
                        val boxId = backStackEntry.arguments?.getString("boxId").orEmpty()
                        val context = androidx.compose.ui.platform.LocalContext.current
                        val repository = remember {
                            com.xxxx.emby_tv.data.repository.EmbyRepository.getInstance(context)
                        }
                        var box by remember { mutableStateOf<com.xxxx.emby_tv.data.model.BaseItemDto?>(null) }
                        var children by remember { mutableStateOf<List<com.xxxx.emby_tv.data.model.BaseItemDto>?>(null) }
                        LaunchedEffect(boxId) {
                            val loaded = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                try {
                                    val item = repository.getMovieItem(boxId)
                                    val kids = com.xxxx.emby_tv.util.EmbyPresentation.collectionChildrenForDisplay(
                                        repository.getBoxSetChildren(boxId),
                                    )
                                    item to kids
                                } catch (_: Exception) {
                                    null to emptyList()
                                }
                            }
                            box = loaded.first
                            children = loaded.second
                        }
                        CollectionDetailPage(
                            box = box,
                            children = children,
                            serverUrl = repository.serverUrl ?: "",
                            onOpenItem = { item ->
                                val route = com.xxxx.emby_tv.util.EmbyPresentation.detailRoute(item)
                                if (route != null) navController.navigate(route)
                            },
                        )
                    }

                    composable(
                        "person/{personId}",
                        arguments = listOf(navArgument("personId") { type = NavType.StringType }),
                    ) { backStackEntry ->
                        val personId = backStackEntry.arguments?.getString("personId").orEmpty()
                        val context = androidx.compose.ui.platform.LocalContext.current
                        val repository = remember {
                            com.xxxx.emby_tv.data.repository.EmbyRepository.getInstance(context)
                        }
                        var person by remember { mutableStateOf<com.xxxx.emby_tv.data.model.BaseItemDto?>(null) }
                        var movies by remember { mutableStateOf<List<com.xxxx.emby_tv.data.model.BaseItemDto>>(emptyList()) }
                        var series by remember { mutableStateOf<List<com.xxxx.emby_tv.data.model.BaseItemDto>>(emptyList()) }
                        LaunchedEffect(personId) {
                            val loaded = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                try {
                                    val item = repository.getMovieItem(personId)
                                    val works = com.xxxx.emby_tv.util.EmbyPresentation.personWorks(
                                        repository.getPersonWorks(personId),
                                    )
                                    Triple(item, works.first, works.second)
                                } catch (_: Exception) {
                                    Triple(null, emptyList(), emptyList())
                                }
                            }
                            person = loaded.first
                            movies = loaded.second
                            series = loaded.third
                        }
                        PersonDetailPage(
                            person = person,
                            movies = movies,
                            series = series,
                            serverUrl = repository.serverUrl ?: "",
                            onOpenItem = { item ->
                                val route = com.xxxx.emby_tv.util.EmbyPresentation.detailRoute(item)
                                if (route != null) navController.navigate(route)
                            },
                        )
                    }

                    composable("favorites") {
                        val context = androidx.compose.ui.platform.LocalContext.current
                        val repository = remember {
                            com.xxxx.emby_tv.data.repository.EmbyRepository.getInstance(context)
                        }
                        var favoriteItems by remember { mutableStateOf<List<com.xxxx.emby_tv.data.model.BaseItemDto>?>(null) }
                        LaunchedEffect(Unit) {
                            favoriteItems = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                try {
                                    com.xxxx.emby_tv.util.EmbyPresentation.favoritesForDisplay(
                                        repository.getFavoriteItems(),
                                    )
                                } catch (_: Exception) {
                                    emptyList()
                                }
                            }
                        }
                        FavoritesPage(
                            serverUrl = repository.serverUrl ?: "",
                            items = favoriteItems,
                            onOpenItem = { item ->
                                val id = item.id ?: return@FavoritesPage
                                if (item.type == "Series" || item.isSeries) {
                                    navController.navigate("series/$id")
                                } else {
                                    navController.navigate("media/$id")
                                }
                            },
                        )
                    }

                    composable("search") {
                        val searchViewModel: SearchViewModel = viewModel()
                        val loginViewModel: LoginViewModel = viewModel()
                        SearchScreen(
                            searchViewModel = searchViewModel,
                            loginViewModel = loginViewModel,
                            mainViewModel = mainViewModel,
                            onNavigateToSeries = { seriesId ->
                                navController.navigate("series/$seriesId")
                            }
                        )
                    }

                    composable(
                        "browse/{libraryId}?libraryName={libraryName}&type={type}",
                        arguments = listOf(
                            navArgument("libraryId") { type = NavType.StringType },
                            navArgument("libraryName") {
                                type = NavType.StringType
                                defaultValue = ""
                            },
                            navArgument("type") {
                                type = NavType.StringType
                                defaultValue = "Movie"
                            },
                        )
                    ) { backStackEntry ->
                        val libraryId = backStackEntry.arguments?.getString("libraryId") ?: ""
                        val libraryName = backStackEntry.arguments?.getString("libraryName") ?: ""
                        val type = backStackEntry.arguments?.getString("type") ?: "Movie"
                        val browseViewModel: LibraryBrowseViewModel = viewModel()
                        val repository = com.xxxx.emby_tv.data.repository.EmbyRepository.getInstance(
                            androidx.compose.ui.platform.LocalContext.current,
                        )
                        LibraryBrowseConnected(
                            parentId = libraryId,
                            libraryName = libraryName,
                            itemType = type,
                            serverUrl = repository.serverUrl ?: "",
                            viewModel = browseViewModel,
                            onOpenItem = { item ->
                                val id = item.id ?: return@LibraryBrowseConnected
                                if (item.type == "Series" || item.isSeries) {
                                    navController.navigate("series/$id")
                                } else {
                                    navController.navigate("media/$id")
                                }
                            },
                        )
                    }

                    // 媒体库页面
                    composable(
                        "library/{libraryId}?libraryName={libraryName}&type={type}&filter={filter}",
                        arguments = listOf(
                            navArgument("libraryId") { type = NavType.StringType },
                            navArgument("libraryName") {
                                type = NavType.StringType
                                defaultValue = ""
                            },
                            navArgument("type") {
                                type = NavType.StringType
                                defaultValue = ""
                            },
                            navArgument("filter") {
                                type = NavType.StringType
                                defaultValue = ""
                            }
                        )
                    ) { backStackEntry ->
                        val libraryId = backStackEntry.arguments?.getString("libraryId") ?: ""
                        val libraryName = backStackEntry.arguments?.getString("libraryName") ?: ""
                        val type = backStackEntry.arguments?.getString("type") ?: ""
                        val filter = backStackEntry.arguments?.getString("filter").orEmpty()
                        val libraryViewModel: LibraryViewModel = viewModel()

                        LibraryScreen(
                            parentId = libraryId,
                            title = libraryName,
                            type = type,
                            libraryViewModel = libraryViewModel,
                            onNavigateToSeries = { seriesId ->
                                navController.navigate("series/$seriesId")
                            },
                            initialFilter = filter.ifBlank { null },
                        )
                    }

                    // 剧集详情页面
                    composable(
                        "series/{seriesId}",
                        arguments = listOf(navArgument("seriesId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val seriesId = backStackEntry.arguments?.getString("seriesId") ?: ""
                        val detailViewModel: DetailViewModel = viewModel()
                        MediaDetailScreen(
                            seriesId = seriesId,
                            detailViewModel = detailViewModel,
                            onNavigateToSeries = { nestedSeriesId ->
                                navController.navigate("series/$nestedSeriesId")
                            },
                            onNavigateToPlayer = { mediaItem ->
                                val id = mediaItem.id ?: ""
                                val position = mediaItem.userData?.playbackPositionTicks ?: 0L
                                navController.navigate("player/$id?position=$position")
                            },
                            onPlaySeries = { mediaItem, sourceId, fromStart ->
                                val id = mediaItem.id ?: return@MediaDetailScreen
                                val position = com.xxxx.emby_tv.util.EmbyPresentation.playbackStartTicks(mediaItem, fromStart)
                                val source = if (sourceId.isNullOrBlank()) "" else "&source=$sourceId"
                                navController.navigate("player/$id?position=$position$source")
                            },
                            onOpenPerson = { personId ->
                                navController.navigate("person/$personId")
                            },
                        )
                    }

                    // 通用媒体详情页面
                    composable(
                        "media/{mediaId}",
                        arguments = listOf(navArgument("mediaId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val mediaId = backStackEntry.arguments?.getString("mediaId") ?: ""
                        val detailViewModel: DetailViewModel = viewModel()

                        MediaDetailScreen(
                            seriesId = mediaId,
                            detailViewModel = detailViewModel,
                            onNavigateToSeries = { nestedSeriesId ->
                                navController.navigate("series/$nestedSeriesId")
                            },
                            onNavigateToPlayer = { mediaItem ->
                                val id = mediaItem.id ?: ""
                                val position = mediaItem.userData?.playbackPositionTicks ?: 0L
                                navController.navigate("player/$id?position=$position")
                            },
                            onOpenMovie = { movieId ->
                                navController.navigate("media/$movieId")
                            },
                            onPlayMovie = { mediaItem, sourceId, fromStart ->
                                val id = mediaItem.id ?: return@MediaDetailScreen
                                val position = com.xxxx.emby_tv.util.EmbyPresentation.playbackStartTicks(mediaItem, fromStart)
                                val source = if (sourceId.isNullOrBlank()) "" else "&source=$sourceId"
                                navController.navigate("player/$id?position=$position$source")
                            },
                            onOpenPerson = { personId ->
                                navController.navigate("person/$personId")
                            },
                        )
                    }

                    // 播放器页面
                    composable(
                        "player/{mediaId}?position={position}&source={source}",
                        arguments = listOf(
                            navArgument("mediaId") { type = NavType.StringType },
                            navArgument("position") {
                                type = NavType.LongType
                                defaultValue = 0L
                            },
                            navArgument("source") {
                                type = NavType.StringType
                                defaultValue = ""
                            }
                        )
                    ) { backStackEntry ->
                        val mediaId = backStackEntry.arguments?.getString("mediaId") ?: ""
                        val playbackPositionTicks =
                            backStackEntry.arguments?.getLong("position") ?: 0L
                        val source = backStackEntry.arguments?.getString("source").orEmpty()
                        val playerViewModel: PlayerViewModel = viewModel()

                        PlayerScreen(
                            mediaId = mediaId,
                            playbackPositionTicks = playbackPositionTicks,
                            initialMediaSourceId = source.ifBlank { null },
                            playerViewModel = playerViewModel,
                            onNavigateToPlayer = { nextItem ->
                                val nextId = nextItem.id ?: ""
                                val nextPos = nextItem.userData?.playbackPositionTicks ?: 0L
                                navController.popBackStack()
                                navController.navigate("player/$nextId?position=$nextPos")
                            },
                            onRePlayer = { nextItem, newPosition ->
                                val nextId = nextItem.id ?: ""
                                val nextPos = newPosition * 10000
                                navController.popBackStack()
                                navController.navigate("player/$nextId?position=$nextPos")
                            },
                            onExit = {
                                com.xxxx.emby_tv.ui.components.BackGuard.arm(System.currentTimeMillis())
                                navController.popBackStack()
                            },
                        )
                    }
                }
            }
        }
    }
}
