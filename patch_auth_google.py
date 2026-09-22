import re

with open('app/src/main/java/com/example/ui/AuthScreens.kt', 'r') as f:
    content = f.read()

replacement = '''
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        isLoading = true
                                        val result = if (viewModel != null) {
                                            viewModel.signInWithGoogle(context)
                                        } else {
                                            FirebaseAuthManager.signInWithGoogle(context)
                                        }
                                        isLoading = false
                                        if (result is AuthResult.Success) {
                                            onLoginSuccess(result.data.uid)
                                        } else if (result is AuthResult.Error) {
                                            errorMessage = result.message
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.AccountCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Войти через Google")
                            }
'''

pattern = re.compile(r'                            OutlinedButton\(\s*onClick = \{\s*scope.launch \{\s*isLoading = true\s*val result = if \(viewModel != null\) \{\s*viewModel\.signInAnonymouslyWithFirebase\(\)\s*\} else \{\s*FirebaseAuthManager\.signInAnonymously\(\)\s*\}\s*isLoading = false\s*if \(result is AuthResult\.Success\) \{\s*onLoginSuccess\(result\.data\.uid\)\s*\} else if \(result is AuthResult\.Error\) \{\s*errorMessage = result\.message\s*\}\s*\}\s*\},\s*modifier = Modifier\.fillMaxWidth\(\)\s*\) \{\s*Icon\(Icons\.Filled\.PersonOutline, contentDescription = null, modifier = Modifier\.size\(18\.dp\)\)\s*Spacer\(Modifier\.width\(8\.dp\)\)\s*Text\("Войти как Гость \(Firebase\)"\)\s*\}', re.DOTALL)

content = pattern.sub(replacement.strip(), content)

with open('app/src/main/java/com/example/ui/AuthScreens.kt', 'w') as f:
    f.write(content)
