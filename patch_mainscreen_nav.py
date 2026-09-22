import re

with open("app/src/main/java/com/example/ui/MainScreen.kt", "r") as f:
    content = f.read()

import_statement = "import androidx.compose.animation.ExperimentalSharedTransitionApi\nimport androidx.compose.animation.SharedTransitionLayout\nimport androidx.compose.runtime.CompositionLocalProvider\n"
if "import androidx.compose.animation.SharedTransitionLayout" not in content:
    content = content.replace("import androidx.compose.runtime.Composable", import_statement + "import androidx.compose.runtime.Composable")

# Wrap NavHost
target_navhost = "                    NavHost(\n                        navController = mainNavController,\n                        startDestination = \"home\",\n                        modifier = Modifier.fillMaxSize()\n                    ) {"
replacement_navhost = """                    @OptIn(ExperimentalSharedTransitionApi::class)
                    SharedTransitionLayout {
                        CompositionLocalProvider(LocalSharedTransitionScope provides this@SharedTransitionLayout) {
                            NavHost(
                                navController = mainNavController,
                                startDestination = "home",
                                modifier = Modifier.fillMaxSize()
                            ) {"""

if "SharedTransitionLayout" not in content and target_navhost in content:
    content = content.replace(target_navhost, replacement_navhost)
    
    # We need to close the two extra brackets after NavHost
    # NavHost ends around line 490.
    target_navhost_end = """                            }
                        }
                    }
                }
            }"""
    
    # Actually it's easier to find the end of NavHost and replace it. Let's find exactly the composable block for `sandbox/{botId}` or something at the end of NavHost.
    # We can just replace:
    #                             }
    #                         }
    #                     }
    #                 }
    #             }
    #         }
    #     }
    # }

with open("app/src/main/java/com/example/ui/MainScreen.kt", "w") as f:
    f.write(content)
