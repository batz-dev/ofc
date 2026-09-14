package com.example.ui.navigation

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.repository.MovieRepository
import com.example.data.repository.SearchHistoryManager
import com.example.ui.screens.*
import com.example.ui.theme.*

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Search : Screen("search")
    object Downloads : Screen("downloads")
    object Library : Screen("library")
    object Detail : Screen("detail/{subjectId}") {
        fun createRoute(subjectId: String) = "detail/$subjectId"
    }
    object Player : Screen("player/{subjectId}/{se}/{ep}?title={title}&epTitle={epTitle}&filePath={filePath}&audioTrack={audioTrack}") {
        fun createRoute(
            subjectId: String,
            se: Int,
            ep: Int,
            title: String,
            epTitle: String = "",
            filePath: String = "",
            audioTrack: String = ""
        ): String {
            val encTitle = Uri.encode(title)
            val encEpTitle = Uri.encode(epTitle)
            val encFilePath = Uri.encode(filePath)
            val encAudio = Uri.encode(audioTrack)
            return "player/$subjectId/$se/$ep?title=$encTitle&epTitle=$encEpTitle&filePath=$encFilePath&audioTrack=$encAudio"
        }
    }
}

@Composable
fun MainAppNavigation(
    repository: MovieRepository,
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val isPlayerScreen = currentRoute?.startsWith("player/") == true

    val context = LocalContext.current
    val homeViewModel = remember { HomeViewModel(repository) }
    val searchHistoryManager = remember { SearchHistoryManager.getInstance(context) }
    val searchViewModel = remember { SearchViewModel(repository, searchHistoryManager) }
    val downloadViewModel = remember { DownloadViewModel(repository) }
    val libraryViewModel = remember { LibraryViewModel(repository) }

    val watchHistory by repository.watchHistory.collectAsState(initial = emptyList())

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (!isPlayerScreen) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    tonalElevation = 3.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    NavigationBar(
                        containerColor = Color.Transparent,
                        tonalElevation = 0.dp,
                        windowInsets = WindowInsets.navigationBars,
                        modifier = Modifier.testTag("main_bottom_nav")
                    ) {
                        val items = listOf(
                            Triple(Screen.Home.route, "Discover", Icons.Filled.Home to Icons.Outlined.Home),
                            Triple(Screen.Search.route, "Search", Icons.Filled.Search to Icons.Outlined.Search),
                            Triple(Screen.Downloads.route, "Downloads", Icons.Filled.Download to Icons.Outlined.Download),
                            Triple(Screen.Library.route, "Library", Icons.Filled.VideoLibrary to Icons.Outlined.VideoLibrary)
                        )

                        items.forEach { (route, label, icons) ->
                            val isSelected = currentRoute == route
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = {
                                    if (currentRoute != route) {
                                        navController.navigate(route) {
                                            popUpTo(Screen.Home.route) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (isSelected) icons.first else icons.second,
                                        contentDescription = "$label navigation tab",
                                        modifier = Modifier.size(24.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                modifier = Modifier.testTag("nav_${label.lowercase()}")
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(if (isPlayerScreen) PaddingValues(0.dp) else PaddingValues(bottom = innerPadding.calculateBottomPadding()))
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    viewModel = homeViewModel,
                    watchHistoryList = watchHistory,
                    onMediaClick = { media ->
                        navController.navigate(Screen.Detail.createRoute(media.id))
                    },
                    onBannerClick = { banner ->
                        navController.navigate(Screen.Detail.createRoute(banner.subjectId))
                    },
                    onResumeWatching = { item ->
                        navController.navigate(
                            Screen.Player.createRoute(
                                subjectId = item.subjectId,
                                se = item.se,
                                ep = item.ep,
                                title = item.title,
                                epTitle = item.episodeTitle
                            )
                        )
                    },
                    onSearchClick = {
                        navController.navigate(Screen.Search.route) {
                            popUpTo(Screen.Home.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }

            composable(Screen.Search.route) {
                SearchScreen(
                    viewModel = searchViewModel,
                    onMediaClick = { media ->
                        navController.navigate(Screen.Detail.createRoute(media.id))
                    }
                )
            }

            composable(Screen.Downloads.route) {
                DownloadScreen(
                    viewModel = downloadViewModel,
                    onPlayDownload = { downloadItem ->
                        navController.navigate(
                            Screen.Player.createRoute(
                                subjectId = downloadItem.subjectId,
                                se = downloadItem.se,
                                ep = downloadItem.ep,
                                title = downloadItem.title,
                                epTitle = downloadItem.episodeTitle,
                                filePath = downloadItem.filePath,
                                audioTrack = downloadItem.audioTrack
                            )
                        )
                    },
                    onExploreClick = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Home.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onOpenDetail = { subjectId ->
                        navController.navigate(Screen.Detail.createRoute(subjectId))
                    }
                )
            }

            composable(Screen.Library.route) {
                LibraryScreen(
                    viewModel = libraryViewModel,
                    onMediaClick = { media ->
                        navController.navigate(Screen.Detail.createRoute(media.id))
                    },
                    onResumeWatching = { item ->
                        navController.navigate(
                            Screen.Player.createRoute(
                                subjectId = item.subjectId,
                                se = item.se,
                                ep = item.ep,
                                title = item.title,
                                epTitle = item.episodeTitle
                            )
                        )
                    }
                )
            }

            composable(
                route = Screen.Detail.route,
                arguments = listOf(navArgument("subjectId") { type = NavType.StringType })
            ) { backStackEntry ->
                val subjectId = backStackEntry.arguments?.getString("subjectId") ?: ""
                val detailViewModel = remember(subjectId) { DetailViewModel(repository, subjectId) }

                DetailScreen(
                    viewModel = detailViewModel,
                    onBackClick = { navController.popBackStack() },
                    onPlayClick = { sid, se, ep, title, epTitle, audioTrack ->
                        navController.navigate(Screen.Player.createRoute(sid, se, ep, title, epTitle, audioTrack = audioTrack))
                    },
                    onMediaClick = { media ->
                        navController.navigate(Screen.Detail.createRoute(media.id))
                    },
                    onGoToDownloads = {
                        navController.navigate(Screen.Downloads.route) {
                            popUpTo(Screen.Home.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }

            composable(
                route = Screen.Player.route,
                arguments = listOf(
                    navArgument("subjectId") { type = NavType.StringType },
                    navArgument("se") { type = NavType.IntType; defaultValue = 0 },
                    navArgument("ep") { type = NavType.IntType; defaultValue = 0 },
                    navArgument("title") { type = NavType.StringType; defaultValue = "" },
                    navArgument("epTitle") { type = NavType.StringType; defaultValue = "" },
                    navArgument("filePath") { type = NavType.StringType; defaultValue = "" },
                    navArgument("audioTrack") { type = NavType.StringType; defaultValue = "" }
                )
            ) { backStackEntry ->
                val subjectId = backStackEntry.arguments?.getString("subjectId") ?: ""
                val se = backStackEntry.arguments?.getInt("se") ?: 0
                val ep = backStackEntry.arguments?.getInt("ep") ?: 0
                val title = Uri.decode(backStackEntry.arguments?.getString("title") ?: "")
                val epTitle = Uri.decode(backStackEntry.arguments?.getString("epTitle") ?: "")
                val filePath = Uri.decode(backStackEntry.arguments?.getString("filePath") ?: "")
                val audioTrack = Uri.decode(backStackEntry.arguments?.getString("audioTrack") ?: "")

                val playerViewModel = remember(subjectId, se, ep, filePath, audioTrack) {
                    PlayerViewModel(
                        repository = repository,
                        subjectId = subjectId,
                        se = se,
                        ep = ep,
                        title = title,
                        episodeTitle = epTitle,
                        filePath = filePath,
                        initialAudioTrack = audioTrack
                    )
                }

                PlayerScreen(
                    viewModel = playerViewModel,
                    onBackClick = { navController.popBackStack() }
                )
            }
        }
    }
}
