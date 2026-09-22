package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.ui.botapi.BotRegistry
import com.example.ui.botapi.CustomBot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BotDashboardScreen(botId: String, navController: NavController) {
    val bot = BotRegistry.getBot(botId) as? CustomBot

    if (bot == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Bot not found.", color = MaterialTheme.colorScheme.error)
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dashboard: ${bot.name}", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    "Bot Usage Statistics",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            item {
                DashboardCard(
                    title = "Daily Active Users",
                    value = bot.stats.activeUsers.toString(),
                    icon = Icons.Filled.Group
                )
            }
            
            item {
                DashboardCard(
                    title = "Total Messages",
                    value = bot.stats.totalMessages.toString(),
                    icon = Icons.Filled.Message
                )
            }
            
            item {
                DashboardCard(
                    title = "Interaction Rate",
                    value = String.format("%.2f msg/user", bot.stats.interactionRate),
                    icon = Icons.Filled.TouchApp
                )
            }
            
            item {
                Text(
                    "Usage Graph (Real-time)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )
                Box(modifier = Modifier.fillMaxWidth().height(200.dp).padding(16.dp)) {
                    val simulatedData = remember { listOf(0f, 12f, 25f, 15f, 40f, 30f, bot.stats.totalMessages.toFloat()) }
                    NeonLineChart(dataPoints = simulatedData, color = MaterialTheme.colorScheme.primary)
                }
            }
            
            item {
                Spacer(Modifier.height(32.dp))
                
                Text(
                    "API Configuration",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            item {
                OutlinedTextField(
                    value = bot.oauthToken ?: "Not Authenticated",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("OAuth2 Token") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            item {
                OutlinedTextField(
                    value = bot.webhookUrl ?: "Not configured",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Webhook URL") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun DashboardCard(title: String, value: String, icon: ImageVector) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}
