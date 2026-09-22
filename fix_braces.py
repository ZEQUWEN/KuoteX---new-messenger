import re

with open("app/src/main/java/com/example/ui/MainScreen.kt", "r") as f:
    content = f.read()

# Let's find the closing brace of NavHost.
# Look for:
#                            composable("profile/{chatId}") { backStackEntry ->
#                                ...
#                            }
#                        }
#                    }
#                }
#            }
target = """                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable"""
replacement = """                                    }
                                }
                            }
                        }
                        }
                        }
                    }
                }
            }
        }
    }
}

@Composable"""
# Actually, the best way to handle this is using regex or counting braces, or just doing it properly. Let's find where NavHost ends.
