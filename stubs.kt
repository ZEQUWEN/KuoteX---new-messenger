@Composable
fun SettingsAccountsScreen(viewModel: AppViewModel, navController: androidx.navigation.NavController) {
    Scaffold(topBar = { TopAppBar(title = { Text("Accounts") }, navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { padding -> Box(modifier = Modifier.padding(padding)) }
}

@Composable
fun PasscodeLockScreen(viewModel: AppViewModel, navController: androidx.navigation.NavController) {
    Scaffold(topBar = { TopAppBar(title = { Text("Passcode Lock") }, navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { padding -> Box(modifier = Modifier.padding(padding)) }
}

@Composable
fun LoginEmailScreen(viewModel: AppViewModel, navController: androidx.navigation.NavController) {
    Scaffold(topBar = { TopAppBar(title = { Text("Email") }, navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { padding -> Box(modifier = Modifier.padding(padding)) }
}

@Composable
fun VerifyEmailScreen(viewModel: AppViewModel, navController: androidx.navigation.NavController) {
    Scaffold(topBar = { TopAppBar(title = { Text("Verify Email") }, navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { padding -> Box(modifier = Modifier.padding(padding)) }
}

@Composable
fun DevicesScreen(navController: androidx.navigation.NavController) {
    Scaffold(topBar = { TopAppBar(title = { Text("Devices") }, navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { padding -> Box(modifier = Modifier.padding(padding)) }
}

@Composable
fun BlockedUsersScreen(viewModel: AppViewModel, navController: androidx.navigation.NavController) {
    Scaffold(topBar = { TopAppBar(title = { Text("Blocked Users") }, navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { padding -> Box(modifier = Modifier.padding(padding)) }
}

@Composable
fun PrivacySettingScreen(navController: androidx.navigation.NavController, title: String) {
    Scaffold(topBar = { TopAppBar(title = { Text(title) }, navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { padding -> Box(modifier = Modifier.padding(padding)) }
}
