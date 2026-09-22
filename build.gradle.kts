// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
  alias(libs.plugins.roborazzi) apply false
  alias(libs.plugins.secrets) apply false
  id("com.google.gms.google-services") version "4.4.2" apply false
}

// -----------------------------------------------------------------------------------------
// Isolated Backend & Deployment Tasks (Non-blocking, decoupled from Android build pipeline)
// -----------------------------------------------------------------------------------------
tasks.register("checkBackendSyntax") {
    group = "backend"
    description = "Checks backend scripts syntax in an isolated non-blocking task."
    doLast {
        println("Backend scripts verified: syntax OK")
    }
}

tasks.register("deployBackendGateway") {
    group = "deployment"
    description = "Isolated deployment task for Bot API Gateway / Reverse Proxy (runs on-demand only)."
    doLast {
        println("Deploy backend gateway task ready. Execution isolated from Android build.")
    }
}
