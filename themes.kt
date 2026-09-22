@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingsThemesScreen(viewModel: AppViewModel, navController: androidx.navigation.NavController) {
    val currentTheme by viewModel.theme.collectAsState()
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = AppTheme.values().indexOf(currentTheme).takeIf { it >= 0 } ?: 0,
        pageCount = { AppTheme.values().size }
    )
    
    androidx.compose.material3.Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { androidx.compose.material3.Text("Themes", color = androidx.compose.ui.graphics.Color.White) },
                navigationIcon = { androidx.compose.material3.IconButton(onClick = { navController.popBackStack() }) { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = androidx.compose.ui.graphics.Color.White) } },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
            )
        },
        containerColor = androidx.compose.ui.graphics.Color.Black
    ) { padding ->
        androidx.compose.foundation.layout.Box(modifier = androidx.compose.foundation.layout.Modifier.fillMaxSize()) {
            androidx.compose.foundation.pager.HorizontalPager(
                state = pagerState,
                modifier = androidx.compose.foundation.layout.Modifier.fillMaxSize()
            ) { page ->
                val theme = AppTheme.values()[page]
                androidx.compose.foundation.layout.Box(modifier = androidx.compose.foundation.layout.Modifier.fillMaxSize()) {
                    // Preview Canvas
                    when (theme) {
                        AppTheme.NEON_SNOWFLAKES -> NeonSnowflakesBackground(isBatterySaver = false, opacity = 1f)
                        AppTheme.NEON_CHERRY_BLOSSOM -> NeonCherryBlossomBackground(isBatterySaver = false, opacity = 1f)
                        AppTheme.NEON_CONFETTI -> NeonConfettiBackground(isBatterySaver = false, opacity = 1f)
                        AppTheme.NEON_MOON -> NeonMoonBackground(opacity = 1f)
                        AppTheme.NEON_ROOM_FOG -> NeonRoomFogBackground(opacity = 1f)
                        AppTheme.DEFAULT -> ElegantDarkBackground(opacity = 1f)
                    }
                    
                    // Theme Name Label
                    androidx.compose.foundation.layout.Column(
                        modifier = androidx.compose.foundation.layout.Modifier
                            .align(androidx.compose.ui.Alignment.Center)
                            .padding(32.dp),
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                    ) {
                        androidx.compose.material3.Text(
                            text = theme.name.replace("_", " "),
                            style = androidx.compose.material3.MaterialTheme.typography.displayMedium,
                            color = androidx.compose.ui.graphics.Color.White,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        androidx.compose.foundation.layout.Spacer(androidx.compose.foundation.layout.Modifier.height(16.dp))
                        if (currentTheme == theme) {
                            androidx.compose.material3.Text("Current Theme", color = androidx.compose.material3.MaterialTheme.colorScheme.primary, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                        } else {
                            androidx.compose.material3.Button(onClick = { viewModel.switchTheme(theme) }) {
                                androidx.compose.material3.Text("Apply Theme")
                            }
                        }
                    }
                }
            }
            
            // Pager Indicators
            androidx.compose.foundation.layout.Row(
                androidx.compose.foundation.layout.Modifier
                    .height(50.dp)
                    .fillMaxWidth()
                    .align(androidx.compose.ui.Alignment.BottomCenter)
                    .padding(bottom = 16.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center
            ) {
                repeat(AppTheme.values().size) { iteration ->
                    val color = if (pagerState.currentPage == iteration) androidx.compose.material3.MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.5f)
                    androidx.compose.foundation.layout.Box(
                        modifier = androidx.compose.foundation.layout.Modifier
                            .padding(4.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(color)
                            .height(8.dp)
                            .width(if (pagerState.currentPage == iteration) 24.dp else 8.dp)
                    )
                }
            }
        }
    }
}
