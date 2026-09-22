package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.di.Injector
import com.example.di.MessengerSandbox
import com.example.ui.botapi.BotRegistry
import com.example.ui.botapi.CustomBot
import com.example.ui.botapi.LogEntry
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SandboxScreen(
    viewModel: AppViewModel,
    botId: String,
    navController: NavController,
    sandboxEngine: MessengerSandbox = Injector.get().messengerSandbox
) {
    val bot = BotRegistry.getBot(botId) as? CustomBot

    if (bot == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Bot not found.", color = MaterialTheme.colorScheme.error)
        }
        return
    }

    var selectedTabIndex by remember { mutableStateOf(0) }
    var codeText by remember { mutableStateOf(bot.code) }
    
    // Sandbox chat state
    var chatMessages by remember { mutableStateOf(listOf<SandboxMessage>()) }
    var inputText by remember { mutableStateOf("") }
    val templates = remember { sandboxEngine.getAvailableTemplates() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sandbox: ${bot.name}", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    var menuExpanded by remember { mutableStateOf(false) }
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.LibraryBooks, contentDescription = "Templates")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        templates.forEach { template ->
                            DropdownMenuItem(
                                text = { Text(template.name) },
                                onClick = {
                                    codeText = template.code
                                    menuExpanded = false
                                }
                            )
                        }
                    }
                    IconButton(onClick = {
                        bot.code = codeText
                        com.example.ui.botapi.BotRegistry.saveCustomBots()
                        val result = sandboxEngine.runCustomApp(codeText)
                        bot.logs.add(LogEntry(message = "Build check: ${result.output}"))
                    }) {
                        Icon(Icons.Filled.Save, contentDescription = "Save Code")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(selectedTabIndex = selectedTabIndex, edgePadding = 8.dp) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = { Text("Code") },
                    icon = { Icon(Icons.Filled.Code, contentDescription = null) }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = { Text("Chat") },
                    icon = { Icon(Icons.Filled.Chat, contentDescription = null) }
                )
                Tab(
                    selected = selectedTabIndex == 2,
                    onClick = { selectedTabIndex = 2 },
                    text = { Text("Logs") },
                    icon = { Icon(Icons.Filled.List, contentDescription = null) }
                )
                Tab(
                    selected = selectedTabIndex == 3,
                    onClick = { selectedTabIndex = 3 },
                    text = { Text("Alerts") },
                    icon = { Icon(Icons.Filled.Notifications, contentDescription = null) }
                )
                Tab(
                    selected = selectedTabIndex == 4,
                    onClick = { selectedTabIndex = 4 },
                    text = { Text("Settings") },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) }
                )
                Tab(
                    selected = selectedTabIndex == 5,
                    onClick = { selectedTabIndex = 5 },
                    text = { Text("Console") },
                    icon = { Icon(Icons.Filled.BugReport, contentDescription = null) }
                )
                Tab(
                    selected = selectedTabIndex == 6,
                    onClick = { selectedTabIndex = 6 },
                    text = { Text("Payments") },
                    icon = { Icon(Icons.Filled.ShoppingCart, contentDescription = null) }
                )
            }

            if (selectedTabIndex == 0) {
                // Code Editor
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Text("main.kt", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = codeText,
                        onValueChange = { codeText = it },
                        modifier = Modifier.fillMaxSize().weight(1f),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = Color(0xFF1E1E1E),
                            focusedContainerColor = Color(0xFF1E1E1E),
                            unfocusedTextColor = Color(0xFFD4D4D4),
                            focusedTextColor = Color(0xFFD4D4D4)
                        )
                    )
                }
            } else if (selectedTabIndex == 1) {
                // Test Chat
                Column(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp),
                        reverseLayout = true
                    ) {
                        items(chatMessages.reversed()) { msg ->
                            SandboxMessageBubble(msg)
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Test message...") },
                            shape = RoundedCornerShape(24.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        FloatingActionButton(
                            onClick = {
                                if (inputText.isNotBlank()) {
                                    val userMsg = SandboxMessage(inputText, isMe = true)
                                    chatMessages = chatMessages + userMsg
                                    val toSend = inputText
                                    inputText = ""
                                    
                                    bot.logs.add(LogEntry(message = "Sandbox input: $toSend"))
                                    
                                    // Evaluate logic via Dependency Injection Sandbox Engine
                                    val execResult = sandboxEngine.runCustomApp(codeText)
                                    val replyText = if (execResult.success) {
                                        simulateBotExecution(toSend, bot.name, codeText)
                                    } else {
                                        "⚠️ Sandbox Engine Error:\n" + execResult.errors.joinToString("\n")
                                    }
                                    
                                    bot.logs.add(LogEntry(message = "Sandbox output: $replyText"))
                                    
                                    val botMsg = SandboxMessage(replyText, isMe = false)
                                    chatMessages = chatMessages + botMsg
                                }
                            },
                            shape = CircleShape
                        ) {
                            Icon(Icons.Filled.Send, contentDescription = "Send")
                        }
                    }
                }
            } else if (selectedTabIndex == 2) {
                // Activity Log
                Column(modifier = Modifier.fillMaxSize().background(Color(0xFF1E1E1E)).padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Server Logs", color = Color.White, style = MaterialTheme.typography.titleMedium)
                        Button(onClick = { /* Simulate Download CSV */ bot.logs.add(LogEntry(message = "Logs downloaded as CSV.")) }) {
                            Icon(Icons.Filled.Download, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Download")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    ) {
                        items(bot.logs.reversed()) { log ->
                            val color = if (log.level == "ERROR") Color.Red else if (log.level == "SUCCESS") Color.Green else Color(0xFFD4D4D4)
                            Text(
                                text = "[${log.timestamp}] ${log.level}: ${log.message}",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = color,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        if (bot.logs.isEmpty()) {
                            item {
                                Text("No activity logs yet.", color = Color.Gray)
                            }
                        }
                    }
                }
            } else if (selectedTabIndex == 3) {
                // Alerts / Notifications
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("System Alerts", style = MaterialTheme.typography.titleLarge)
                        Button(onClick = { bot.logs.add(LogEntry(level = "INFO", message = "All notifications marked as read. Summary: Bot is running smoothly.")) }) {
                            Text("Read & Summarize")
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    
                    val trafficAlertCount = remember { (1..5).random() }
                    
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                                Spacer(Modifier.width(8.dp))
                                Text("⚠️ Traffic Limit Alert", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text("Your bot exceeded the traffic limit of ${bot.rateLimit} msgs/min. This occurred $trafficAlertCount times recently.", color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            } else if (selectedTabIndex == 4) {
                // Settings (Versioning & Collaborators)
                var newCollabUsername by remember { mutableStateOf("") }
                var newCollabRole by remember { mutableStateOf("READ_ONLY") }
                
                LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    item {
                        Text("Bot Versioning", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = {
                            val newVersion = (bot.snapshots.maxOfOrNull { it.version } ?: 0) + 1
                            bot.snapshots.add(com.example.ui.botapi.CodeSnapshot(newVersion, codeText)); com.example.ui.botapi.BotRegistry.saveCustomBots()
                        }) {
                            Text("Save Current Code as Snapshot")
                        }
                        Spacer(Modifier.height(8.dp))
                        if (bot.snapshots.isEmpty()) {
                            Text("No snapshots saved.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    items(bot.snapshots.reversed()) { snapshot ->
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text("v${snapshot.version}", fontWeight = FontWeight.Bold)
                                    Text("Timestamp: ${snapshot.timestamp}", style = MaterialTheme.typography.bodySmall)
                                }
                                Button(onClick = { codeText = snapshot.code; bot.code = snapshot.code }) {
                                    Text("Rollback")
                                }
                            }
                        }
                    }
                    
                    item {
                        Spacer(Modifier.height(24.dp))
                        Text("Collaborators", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = newCollabUsername,
                                onValueChange = { newCollabUsername = it },
                                placeholder = { Text("@username") },
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = { newCollabRole = if (newCollabRole == "READ_ONLY") "EDIT" else "READ_ONLY" }) {
                                Text(newCollabRole)
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = {
                                if (newCollabUsername.isNotBlank()) {
                                    bot.collaborators.add(com.example.ui.botapi.Collaborator(newCollabUsername, newCollabRole))
                                    newCollabUsername = ""
                                }
                            }) {
                                Text("Invite")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        if (bot.collaborators.isEmpty()) {
                            Text("No collaborators.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    items(bot.collaborators) { collab ->
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(collab.username, fontWeight = FontWeight.Bold)
                                Text(collab.role, color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                    }
                }
            } else if (selectedTabIndex == 5) {
                // Test Console
                var consoleInput by remember { mutableStateOf("") }
                var consoleOutput by remember { mutableStateOf("Ready to send test payload...") }
                
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Text("API Test Console", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text("Send a raw text message and view the simulated JSON payload response.", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    Spacer(Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = consoleInput,
                        onValueChange = { consoleInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Enter test message...") },
                        label = { Text("Message Payload") }
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (consoleInput.isNotBlank()) {
                                val simulatedResponseText = simulateBotExecution(consoleInput, bot.name, codeText)
                                val jsonResponse = """
{
  "status": 200,
  "request": {
    "message": "$consoleInput",
    "bot_id": "${bot.id}",
    "timestamp": ${System.currentTimeMillis()}
  },
  "response": {
    "text": "$simulatedResponseText",
    "type": "text/plain"
  }
}
""".trimIndent()
                                consoleOutput = jsonResponse
                            }
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Send Request")
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    Text("Raw Response:", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(8.dp))
                    Box(modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color(0xFF1E1E1E), RoundedCornerShape(8.dp))
                        .padding(16.dp)
                    ) {
                        LazyColumn {
                            item {
                                Text(
                                    text = consoleOutput,
                                    color = Color(0xFF4CAF50),
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            } else if (selectedTabIndex == 6) {
                // Payments & Invoices Tab
                var invoiceTitle by remember { mutableStateOf("") }
                var invoiceDesc by remember { mutableStateOf("") }
                var invoicePrice by remember { mutableStateOf("") }
                var invoiceCurrency by remember { mutableStateOf("USD") }
                
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Text("Payment Configuration", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text("Create a mock invoice to test your bot's payment flows.", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    Spacer(Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = invoiceTitle,
                        onValueChange = { invoiceTitle = it },
                        label = { Text("Invoice Title") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = invoiceDesc,
                        onValueChange = { invoiceDesc = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = invoicePrice,
                            onValueChange = { invoicePrice = it },
                            label = { Text("Price (e.g. 1000 for $10.00)") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = invoiceCurrency,
                            onValueChange = { invoiceCurrency = it },
                            label = { Text("Currency") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            val msg = "🧾 Invoice Generated: $invoiceTitle ($invoiceCurrency $invoicePrice)\n$invoiceDesc"
                            chatMessages = chatMessages + SandboxMessage(msg, isMe = false)
                            selectedTabIndex = 1 // Switch to chat to see the invoice
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Payment, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Generate Test Invoice")
                    }
                }
            }
        }
    }
}

data class SandboxMessage(val text: String, val isMe: Boolean)

@Composable
fun SandboxMessageBubble(message: SandboxMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isMe) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .background(
                    color = if (message.isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(12.dp)
        ) {
            Text(
                text = message.text,
                color = if (message.isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

fun simulateBotExecution(input: String, botName: String, code: String): String {
    // A very simple mock evaluator for demonstration purposes
    return if (code.contains("Echo")) {
        "Echo from $botName: $input"
    } else {
        "Bot $botName processed: $input"
    }
}
