sed -i '/visualTransformation = PasswordVisualTransformation(),\n                    modifier = Modifier.fillMaxWidth()\n                )/a\
                Spacer(Modifier.height(8.dp))\
                Row(\
                    modifier = Modifier.fillMaxWidth(),\
                    horizontalArrangement = Arrangement.SpaceBetween\
                ) {\
                    Text(\
                        text = "Password Strength",\
                        style = MaterialTheme.typography.bodySmall,\
                        color = MaterialTheme.colorScheme.onSurfaceVariant\
                    )\
                    Text(\
                        text = when (strength) {\
                            0 -> ""\
                            1, 2 -> "Weak"\
                            3 -> "Fair"\
                            4, 5 -> "Strong"\
                            else -> ""\
                        },\
                        style = MaterialTheme.typography.bodySmall,\
                        color = strengthColor\
                    )\
                }\
                Spacer(modifier = Modifier.height(8.dp))\
                Box(\
                    modifier = Modifier\
                        .fillMaxWidth()\
                        .height(6.dp)\
                        .background(androidx.compose.ui.graphics.Color.DarkGray, androidx.compose.foundation.shape.CircleShape)\
                ) {\
                    Box(\
                        modifier = Modifier\
                            .fillMaxWidth(fraction = animatedProgress)\
                            .height(6.dp)\
                            .background(strengthColor, androidx.compose.foundation.shape.CircleShape)\
                            .border(1.dp, strengthColor, androidx.compose.foundation.shape.CircleShape)\
                            .shadow(\
                                elevation = if (strength > 0) 8.dp else 0.dp,\
                                shape = androidx.compose.foundation.shape.CircleShape,\
                                ambientColor = strengthColor,\
                                spotColor = strengthColor\
                            )\
                    )\
                }' app/src/main/java/com/example/ui/AuthScreens.kt
