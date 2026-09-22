import re

with open("app/src/main/java/com/example/ui/AccountScreen.kt", "r") as f:
    content = f.read()

target = """                        // Mock Wheel Picker representation
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            Text("21", style = MaterialTheme.typography.headlineMedium)
                            Spacer(Modifier.width(16.dp))
                            Text("Июнь", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(16.dp))
                            Text("2005", style = MaterialTheme.typography.headlineMedium)
                        }
                        
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { 
                                viewModel.updateBirthDate("21 июн. 2005")
                                showDatePicker = false 
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {"""

replacement = """                        var selectedDate by remember { mutableStateOf(state.birthDate) }
                        
                        WheelDatePicker(
                            initialDate = state.birthDate,
                            onDateSelected = { selectedDate = it }
                        )
                        
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { 
                                viewModel.updateBirthDate(selectedDate)
                                showDatePicker = false 
                            },
                            modifier = Modifier.fillMaxWidth()
                        )"""

if target in content:
    content = content.replace(target, replacement)
    with open("app/src/main/java/com/example/ui/AccountScreen.kt", "w") as f:
        f.write(content)
    print("Replaced!")
else:
    print("Target not found.")
