package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.ui.botapi.BotRegistry
import com.example.ui.botapi.Bot



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverBotsScreen(viewModel: AppViewModel, navController: NavController) {
    var selectedCategory by remember { mutableStateOf("All") }
    val availableBots = BotRegistry.getAllBots()
    val categories = listOf("All") + availableBots.map { it.category }.distinct()
    
    val filteredBots = if (selectedCategory == "All") availableBots else availableBots.filter { it.category == selectedCategory }
    
    var selectedBot by remember { mutableStateOf<Bot?>(null) }

    if (selectedBot != null) {
        AlertDialog(
            onDismissRequest = { selectedBot = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.SmartToy, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(selectedBot!!.name)
                }
            },
            text = {
                Column {
                    Text(selectedBot!!.description, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(selectedBot!!.longDescription)
                    Spacer(Modifier.height(16.dp))
                    Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(selectedBot!!.category, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(4.dp))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val bot = Chat(
                            id = selectedBot!!.id,
                            title = selectedBot!!.name,
                            isBot = true,
                            lastMessage = selectedBot!!.description,
                            unreadCount = 0
                        )
                        viewModel.addBot(bot)
                        selectedBot = null
                        navController.navigate("chat/${bot.id}")
                    }
                ) {
                    Text("Start Chat")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedBot = null }) {
                    Text("Close")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bot Catalog") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        label = { Text(category) }
                    )
                }
            }
            
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredBots) { bot ->
                    ListItem(
                        headlineContent = { Text(bot.name, fontWeight = FontWeight.Bold) },
                        supportingContent = { Text(bot.description) },
                        leadingContent = {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.SmartToy, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        },
                        trailingContent = {
                            IconButton(onClick = { selectedBot = bot }) {
                                Icon(Icons.Filled.Info, contentDescription = "Info", tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        modifier = Modifier.clickable {
                            selectedBot = bot
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
