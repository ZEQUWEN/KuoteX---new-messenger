package com.example.ui

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.data.CryptoManager
import net.sqlcipher.database.SQLiteDatabase
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatabaseDiagnosticScreen() {
    val context = LocalContext.current
    var schemaVersion by remember { mutableStateOf<Int?>(null) }
    var tables by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(Unit) {
        try {
            val dbName = "messenger_database_encrypted"
            val passphrase = CryptoManager.getDatabasePassphrase(context)
            val dbFile = context.getDatabasePath(dbName)
            
            if (dbFile.exists()) {
                SQLiteDatabase.loadLibs(context.applicationContext)
                val db = SQLiteDatabase.openDatabase(
                    dbFile.absolutePath,
                    passphrase,
                    null,
                    SQLiteDatabase.OPEN_READWRITE
                )
                
                val schemaCursor = db.rawQuery("PRAGMA user_version;", null)
                if (schemaCursor.moveToFirst()) {
                    schemaVersion = schemaCursor.getInt(0)
                }
                schemaCursor.close()
                
                val tableCursor = db.rawQuery("SELECT name, sql FROM sqlite_master WHERE type='table'", null)
                val tableList = mutableListOf<Pair<String, String>>()
                while (tableCursor.moveToNext()) {
                    tableList.add(tableCursor.getString(0) to (tableCursor.getString(1) ?: "No SQL"))
                }
                tableCursor.close()
                
                tables = tableList
                db.close()
            } else {
                errorMessage = "Database file does not exist."
            }
        } catch (e: Exception) {
            errorMessage = "Error loading database: ${e.message}"
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Database Diagnostics") }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            if (errorMessage != null) {
                Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
            } else {
                Text("Schema Version: ${schemaVersion ?: "Loading..."}", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Tables (${tables.size}):", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(tables) { (name, sql) ->
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("Table: $name", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(sql, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
