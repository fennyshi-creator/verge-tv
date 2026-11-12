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
    val favoriteItems = homeViewModel.favoriteItems
    val becauseYouWatched = homeViewModel.becauseYouWatched
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

    fun openDetails(item: BaseItemDto) {
        when (item.type) {
            "Series" -> {
                val id = item.id ?: return
                navController.navigate("series/$id")
            }
            "Episode" -> {
                val id = item.seriesId ?: item.id ?: return
                navController.navigate("series/$id")
            }
            else -> {
                val id = item.id ?: return
                navController.navigate("media/$id")
            }
        }
    }

    fun openLibrary(item: BaseItemDto) {
        val id = item.id ?: return
        val title = item.name ?: ""
        val type = item.latestItems?.firstOrNull()?.type ?: ""
        navController.navigate("library/$id?libraryName=$title&type=$type")
    }

    fun openBrowse(item: BaseItemDto) {
        if (item.isSeries) {
            val seriesId = item.id
            if (!seriesId.isNullOrEmpty()) {
                homeViewModel.playNextUp(seriesId) { next -> goPlay(next) }
            } else {
                goPlay(item)
            }
        } else {
            goPlay(item)
        }
    }

    val hero = resumeItems?.firstOrNull()
        ?: libraryLatestItems?.firstNotNullOfOrNull { it.latestItems?.firstOrNull() }

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
                hero = hero,
                resumeItems = resumeItems.orEmpty(),
                libraries = libraryLatestItems,
                favorites = favoriteItems.orEmpty(),
                recommendations = becauseYouWatched,
                serverUrl = serverUrl,
                onResume = { goPlay(it) },
                onOpenDetails = { openDetails(it) },
                onOpenLibrary = { openLibrary(it) },
                onOpenItem = { openBrowse(it) },
                onMenu = { showMenu = true },
            )
        }
        DoubleBackExitHandler(doubleBackToExit)
    }
}
