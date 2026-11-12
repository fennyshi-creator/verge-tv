package com.xxxx.emby_tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.xxxx.emby_tv.data.local.PreferencesManager
import com.xxxx.emby_tv.data.model.BaseItemDto
import com.xxxx.emby_tv.data.repository.EmbyRepository
import com.xxxx.emby_tv.ui.components.DoubleBackExitHandler
import com.xxxx.emby_tv.ui.components.MenuDialog
import com.xxxx.emby_tv.ui.home.HomeStyle
import com.xxxx.emby_tv.ui.home.OfficialHome
import com.xxxx.emby_tv.ui.viewmodel.HomeViewModel
import com.xxxx.emby_tv.util.EmbyPresentation
import com.xxxx.emby_tv.ui.viewmodel.MainViewModel

@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel,
    mainViewModel: MainViewModel,
    navController: NavController,
    onSwitchAccount: () -> Unit = {},
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    val doubleBackToExit = PreferencesManager(context).doubleBackToExit
    val repository = remember { EmbyRepository.getInstance(context) }
    val serverUrl = repository.serverUrl ?: ""

    val resumeItems = homeViewModel.resumeItems
    val libraryLatestItems = homeViewModel.libraryLatestItems
    val errorMessage = homeViewModel.errorMessage

    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            android.widget.Toast.makeText(context, errorMessage, android.widget.Toast.LENGTH_LONG).show()
            homeViewModel.clearError()
        }
    }

    LaunchedEffect(Unit) {
        mainViewModel.checkUpdate()
    }

    if (showMenu) {
        MenuDialog(
            needUpdate = mainViewModel.needUpdate,
            onDismiss = { showMenu = false },
            onLogout = {
                mainViewModel.logout()
                showMenu = false
            },
            onUpdate = {
                mainViewModel.checkUpdate()
                showMenu = false
                navController.navigate("update")
            },
            onThemeChange = { themeColor ->
                mainViewModel.saveThemeId(themeColor.id)
            },
            onSwitchAccount = {
                showMenu = false
                onSwitchAccount()
            },
            onSearch = {
                showMenu = false
                navController.navigate("search")
            },
            onPlaybackSettings = {
                showMenu = false
                navController.navigate("playback_settings")
            },
            onProxySettings = {
                showMenu = false
                navController.navigate("proxy_settings")
            },
            onKeySettings = {
                showMenu = false
                navController.navigate("key_settings")
            }
        )
    }

    fun goPlay(item: BaseItemDto) {
        val id = item.id ?: return
        val position = item.userData?.playbackPositionTicks ?: 0L
        navController.navigate("player/$id?position=$position")
    }

    fun libraryType(item: BaseItemDto): String {
        return EmbyPresentation.latestIncludeTypes(item)
            ?: item.latestItems?.firstOrNull()?.type
            ?: ""
    }

    fun openLibrary(item: BaseItemDto) {
        val id = item.id ?: return
        val title = item.name ?: ""
        navController.navigate("library/$id?libraryName=$title&type=${libraryType(item)}")
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (libraryLatestItems == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(HomeStyle.background),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
            }
        } else {
            OfficialHome(
                resumeItems = resumeItems.orEmpty(),
                libraries = libraryLatestItems,
                serverUrl = serverUrl,
                onResume = { goPlay(it) },
                onOpenLibrary = { openLibrary(it) },
                onOpenItem = { item ->
                    val id = item.id ?: return@OfficialHome
                    if (item.type == "Series" || item.isSeries) {
                        navController.navigate("series/$id")
                    } else {
                        navController.navigate("media/$id")
                    }
                },
                onSearch = { navController.navigate("search") },
                onFavorites = {
                    val library = libraryLatestItems.firstOrNull() ?: return@OfficialHome
                    val id = library.id ?: return@OfficialHome
                    val title = library.name ?: ""
                    navController.navigate(
                        "library/$id?libraryName=$title&type=${libraryType(library)}&filter=IsFavorite"
                    )
                },
                onSettings = { showMenu = true },
            )
        }
        DoubleBackExitHandler(doubleBackToExit)
    }
}
