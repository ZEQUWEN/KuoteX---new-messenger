package com.example.ui.contacts

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.AppViewModel
import com.example.ui.Contact
import com.example.utils.CountryInfo
import com.example.utils.CountryPhoneHelper

/**
 * Telegram-style "Новый контакт" Screen / Sheet with country code auto-detection,
 * device contact synchronization, and authentic Telegram aesthetics.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewContactScreen(
    viewModel: AppViewModel,
    onDismiss: () -> Unit,
    onContactCreated: ((Contact) -> Unit)? = null
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var phoneNumberDigits by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var syncWithDevice by remember { mutableStateOf(true) }

    var selectedCountry by remember { mutableStateOf(CountryPhoneHelper.defaultCountry) }
    var showCountryPicker by remember { mutableStateOf(false) }

    val nameFocusRequester = remember { FocusRequester() }

    // Permission launcher for writing to device contacts
    val writeContactsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    // Check write contacts permission when sync is turned on
    LaunchedEffect(syncWithDevice) {
        if (syncWithDevice) {
            val hasWritePermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasWritePermission) {
                writeContactsLauncher.launch(Manifest.permission.WRITE_CONTACTS)
            }
        }
    }

    LaunchedEffect(Unit) {
        try {
            nameFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    // Auto-detect country if digits contain country dialing code
    fun onPhoneDigitsChanged(newDigits: String) {
        val clean = newDigits.filter { it.isDigit() || it == '+' || it == ' ' }
        if (clean.startsWith("+")) {
            val (detectedCountry, remaining) = CountryPhoneHelper.detectCountry(clean)
            selectedCountry = detectedCountry
            phoneNumberDigits = remaining
        } else if (clean.startsWith("89") || clean.startsWith("79")) {
            selectedCountry = CountryPhoneHelper.defaultCountry
            phoneNumberDigits = clean.drop(1)
        } else {
            phoneNumberDigits = clean.filter { it.isDigit() }
        }
    }

    val fullFormattedPhone = remember(selectedCountry, phoneNumberDigits) {
        val formattedNational = CountryPhoneHelper.formatNationalNumber(phoneNumberDigits, selectedCountry.mask)
        "${selectedCountry.dialCode} $formattedNational".trim()
    }

    val isFormValid = firstName.isNotBlank() && phoneNumberDigits.isNotBlank()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF141318))
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Header: Back button + Title
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Назад",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Новый контакт",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                // First Name (Required)
                TelegramInputField(
                    value = firstName,
                    onValueChange = { firstName = it },
                    placeholder = "Имя (обязательно)",
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(nameFocusRequester),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Last Name (Optional)
                TelegramInputField(
                    value = lastName,
                    onValueChange = { lastName = it },
                    placeholder = "Фамилия (необязательно)",
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Phone Input with Country Code & Flag
                TelegramPhoneField(
                    country = selectedCountry,
                    digits = phoneNumberDigits,
                    onDigitsChange = { onPhoneDigitsChanged(it) },
                    onCountryClick = {
                        focusManager.clearFocus()
                        showCountryPicker = true
                    }
                )

                Spacer(modifier = Modifier.height(18.dp))

                // "Синхронизировать с телефоном" Checkbox (Telegram style)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { syncWithDevice = !syncWithDevice }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Telegram round checkbox
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(
                                if (syncWithDevice) Color(0xFFA855F7) else Color.Transparent
                            )
                            .border(
                                width = if (syncWithDevice) 0.dp else 2.dp,
                                color = if (syncWithDevice) Color.Transparent else Color(0xFF6B7280),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (syncWithDevice) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Text(
                        text = "Синхронизировать с телефоном",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Notes Field
                TelegramInputField(
                    value = notes,
                    onValueChange = { notes = it },
                    placeholder = "Notes",
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                )

                Spacer(modifier = Modifier.height(28.dp))

                // "Создать контакт" Button (Telegram full-width purple pill button)
                Button(
                    onClick = {
                        if (isFormValid) {
                            val fullPhoneToSave = fullFormattedPhone
                            viewModel.addContact(
                                firstName = firstName,
                                lastName = lastName,
                                phoneNumber = fullPhoneToSave,
                                notes = notes,
                                syncWithDevice = syncWithDevice,
                                context = context
                            ) { created ->
                                Toast.makeText(
                                    context,
                                    "Контакт ${created.name} сохранён!",
                                    Toast.LENGTH_SHORT
                                ).show()
                                onContactCreated?.invoke(created)
                                onDismiss()
                            }
                        }
                    },
                    enabled = isFormValid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFA855F7),
                        disabledContainerColor = Color(0xFFA855F7).copy(alpha = 0.4f),
                        contentColor = Color.White,
                        disabledContentColor = Color.White.copy(alpha = 0.5f)
                    )
                ) {
                    Text(
                        text = "Создать контакт",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Country Picker Bottom Sheet
    if (showCountryPicker) {
        CountryPickerBottomSheet(
            selectedCountry = selectedCountry,
            onSelectCountry = { country ->
                selectedCountry = country
                showCountryPicker = false
            },
            onDismiss = { showCountryPicker = false }
        )
    }
}

/**
 * Telegram-style unified Dark Input Field
 */
@Composable
private fun TelegramInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(
                text = placeholder,
                color = Color(0xFF9CA3AF),
                fontSize = 16.sp
            )
        },
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = Color.White,
            fontSize = 16.sp
        ),
        singleLine = true,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        modifier = modifier.height(58.dp),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color(0xFF201F25),
            unfocusedContainerColor = Color(0xFF201F25),
            focusedBorderColor = Color(0xFFA855F7),
            unfocusedBorderColor = Color(0xFF383740),
            cursorColor = Color(0xFFA855F7)
        )
    )
}

/**
 * Telegram-style Phone Number Field with clickable country flag + code and national number entry
 */
@Composable
private fun TelegramPhoneField(
    country: CountryInfo,
    digits: String,
    onDigitsChange: (String) -> Unit,
    onCountryClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF201F25))
                .border(1.dp, Color(0xFFA855F7), RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Clickable Country code button with Flag emoji
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onCountryClick)
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = country.flagEmoji,
                        fontSize = 20.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = country.dialCode,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Vertical Divider
                Box(
                    modifier = Modifier
                        .padding(horizontal = 10.dp)
                        .width(1.dp)
                        .height(26.dp)
                        .background(Color(0xFF4B4A54))
                )

                // Phone Digits Input
                val formattedDigits = remember(digits, country.mask) {
                    CountryPhoneHelper.formatNationalNumber(digits, country.mask)
                }

                BasicTextField(
                    value = formattedDigits,
                    onValueChange = { input ->
                        onDigitsChange(input)
                    },
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = Color.White,
                        fontSize = 16.sp
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Next
                    ),
                    cursorBrush = SolidColor(Color(0xFFA855F7)),
                    decorationBox = { innerTextField ->
                        if (digits.isEmpty()) {
                            Text(
                                text = country.mask,
                                color = Color(0xFF6B7280),
                                fontSize = 16.sp
                            )
                        }
                        innerTextField()
                    }
                )
            }
        }
    }
}

/**
 * Country Selector Bottom Sheet with real-time search
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountryPickerBottomSheet(
    selectedCountry: CountryInfo,
    onSelectCountry: (CountryInfo) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredCountries = remember(searchQuery) {
        val query = searchQuery.trim().lowercase()
        if (query.isBlank()) {
            CountryPhoneHelper.countries
        } else {
            CountryPhoneHelper.countries.filter {
                it.nameRu.lowercase().contains(query) ||
                it.nameEn.lowercase().contains(query) ||
                it.dialCode.contains(query) ||
                it.id.lowercase().contains(query)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF1E1B2E),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.Gray.copy(alpha = 0.5f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 20.dp)
        ) {
            Text(
                text = "Выбор страны",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Поиск страны или кода (+7, +1...)", color = Color.Gray, fontSize = 14.sp) },
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = Color(0xFFA855F7))
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Очистить", tint = Color.Gray)
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF262533),
                    unfocusedContainerColor = Color(0xFF262533),
                    focusedBorderColor = Color(0xFFA855F7),
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Countries List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(filteredCountries, key = { "${it.id}_${it.dialCode}" }) { country ->
                    val isSelected = country.id == selectedCountry.id && country.dialCode == selectedCountry.dialCode

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelectCountry(country) }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = country.flagEmoji,
                            fontSize = 24.sp
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = country.nameRu,
                                color = if (isSelected) Color(0xFFA855F7) else Color.White,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 16.sp
                            )
                            Text(
                                text = country.nameEn,
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                        Text(
                            text = country.dialCode,
                            color = Color(0xFFA855F7),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                    }
                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
