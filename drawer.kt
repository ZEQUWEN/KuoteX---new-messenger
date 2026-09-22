@Composable
fun AccountDrawerContent(viewModel: AppViewModel, onCloseDrawer: () -> Unit, navController: NavController, onCreateGroupClick: () -> Unit, onCreateChannelClick: () -> Unit, onCreateSecretChatClick: () -> Unit) {
    val accounts by viewModel.accounts.collectAsState()
    val activeAccount = LocalActiveAccount.current
    var isAccountsExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isAccountsExpanded = !isAccountsExpanded }
                .padding(16.dp)
                .padding(top = 24.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    AsyncImage(
                        model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                            .data(activeAccount?.profilePicUrl ?: "")
                            .crossfade(true)
                            .build(),
                        contentDescription = "Profile Picture",
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color.DarkGray),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.weight(1f))
                    Icon(
                        if (isAccountsExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Expand Accounts"
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(activeAccount?.displayName ?: "", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(activeAccount?.username ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

        LazyColumn(modifier = Modifier.weight(1f)) {
            if (isAccountsExpanded) {
                items(accounts) { account ->
                    NavigationDrawerItem(
                        label = { 
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(
                                    model = account.profilePicUrl,
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp).clip(CircleShape).background(Color.Gray),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(account.displayName, fontWeight = if(account.isActive) FontWeight.Bold else FontWeight.Normal)
                            }
                        },
                        selected = account.isActive,
                        onClick = {
                            viewModel.switchAccount(account.id)
                            onCloseDrawer()
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
                if (accounts.size < 5) {
                    item {
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Filled.Add, "Add Account") },
                            label = { Text("Add Account") },
                            selected = false,
                            onClick = { 
                                viewModel.startAddAccount()
                                onCloseDrawer() 
                            }
                        )
                    }
                }
                item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)) }
            }

            item {
                NavigationDrawerItem(
                    icon = { Icon(Icons.Filled.Person, "Profile") },
                    label = { Text("Profile") },
                    selected = false,
                    onClick = { 
                        navController.navigate("settings/profile")
                        onCloseDrawer() 
                    }
                )
            }
            item {
                NavigationDrawerItem(
                    icon = { Icon(Icons.Filled.Group, "New Group") },
                    label = { Text("New Group") },
                    selected = false,
                    onClick = { onCreateGroupClick(); onCloseDrawer() }
                )
            }
            item {
                NavigationDrawerItem(
                    icon = { Icon(Icons.Filled.PersonOutline, "Contacts") },
                    label = { Text("Contacts") },
                    selected = false,
                    onClick = { navController.navigate("contacts"); onCloseDrawer() }
                )
            }
            item {
                NavigationDrawerItem(
                    icon = { Icon(Icons.Filled.Call, "Calls") },
                    label = { Text("Calls") },
                    selected = false,
                    onClick = { navController.navigate("calls"); onCloseDrawer() }
                )
            }
            item {
                NavigationDrawerItem(
                    icon = { Icon(Icons.Filled.Bookmark, "Saved Messages") },
                    label = { Text("Saved Messages") },
                    selected = false,
                    onClick = { onCloseDrawer() }
                )
            }
            item {
                NavigationDrawerItem(
                    icon = { Icon(Icons.Filled.Settings, "Settings") },
                    label = { Text("Settings") },
                    selected = false,
                    onClick = { 
                        navController.navigate("settings")
                        onCloseDrawer() 
                    }
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)) }
            item {
                NavigationDrawerItem(
                    icon = { Icon(Icons.Filled.PersonAdd, "Invite Friends") },
                    label = { Text("Invite Friends") },
                    selected = false,
                    onClick = { onCloseDrawer() }
                )
            }
            item {
                NavigationDrawerItem(
                    icon = { Icon(Icons.Filled.Help, "Neon Messenger Features") },
                    label = { Text("Neon Messenger Features") },
                    selected = false,
                    onClick = { onCloseDrawer() }
                )
            }
        }
    }
}
