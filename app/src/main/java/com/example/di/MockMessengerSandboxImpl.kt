package com.example.di

import androidx.compose.ui.graphics.Color
import com.example.ui.AppTheme

/**
 * Mock/Test Sandbox Implementation.
 * Useful for fast local testing or minimal memory/CPU constrained environments.
 */
class MockMessengerSandboxImpl : MessengerSandbox {
    override fun runCustomApp(appCode: String): SandboxExecutionResult {
        return SandboxExecutionResult(
            success = true,
            output = "Mock engine executed: 0ms",
            executionTimeMs = 0,
            errors = emptyList()
        )
    }

    override fun validateSyntax(code: String): List<String> = emptyList()

    override fun getAvailableTemplates(): List<SandboxTemplate> = listOf(
        SandboxTemplate(
            id = "mock_sample",
            name = "Quick Sample Bot",
            description = "Minimal memory sample bot",
            code = "fun handleMessage(msg: String): String = \"Fast Mock OK\""
        )
    )
}
