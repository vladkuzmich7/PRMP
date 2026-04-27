package com.example.lab_mobile3

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.lab_mobile3.ui.theme.Lab_mobileTheme
import com.google.firebase.installations.FirebaseInstallations
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : FragmentActivity() {
    private lateinit var securityManager: SecurityManager
    private val TAG = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        securityManager = SecurityManager(this)

        FirebaseMessaging.getInstance().token.addOnCompleteListener { regTokenTask ->
            if (regTokenTask.isSuccessful) {
                Log.d(TAG, "FCM registration token: ${regTokenTask.result}")
            } else {
                Log.e(TAG, "Unable to retrieve registration token", regTokenTask.exception)
            }
        }
        FirebaseInstallations.getInstance().id.addOnCompleteListener { installationIdTask ->
            if (installationIdTask.isSuccessful) {
                Log.d(TAG, "Firebase Installations ID: ${installationIdTask.result}")
            } else {
                Log.e(TAG, "Unable to retrieve installations ID", installationIdTask.exception)
            }
        }
        
        setContent {
            val viewModel: CalculatorViewModel = viewModel()
            val currentTheme by viewModel.appTheme
            
            var isAuthorized by remember { mutableStateOf(!securityManager.isPassKeySet()) }
            var showAuthDialog by remember { mutableStateOf(securityManager.isPassKeySet()) }

            Lab_mobileTheme(appTheme = currentTheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (isAuthorized) {
                        CalculatorScreen(viewModel, securityManager)
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    if (showAuthDialog) {
                        AuthDialog(
                            securityManager = securityManager,
                            onAuthorized = {
                                isAuthorized = true
                                showAuthDialog = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AuthDialog(
    securityManager: SecurityManager,
    onAuthorized: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as FragmentActivity
    var passKeyInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showRecoveryDialog by remember { mutableStateOf(false) }

    val onBiometricSuccess = {
        onAuthorized()
    }

    val onBiometricError = { error: String ->
        errorMessage = error
    }

    LaunchedEffect(Unit) {
        if (securityManager.canUseBiometric()) {
            securityManager.showBiometricPrompt(activity, onBiometricSuccess, onBiometricError)
        }
    }

    AlertDialog(
        onDismissRequest = { },
        title = { Text("Unlock Calculator") },
        text = {
            Column {
                Text("Enter Pass Key to access history and scientific features.")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = passKeyInput,
                    onValueChange = { passKeyInput = it },
                    label = { Text("Pass Key") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = errorMessage != null
                )
                if (errorMessage != null) {
                    Text(errorMessage!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
                
                TextButton(
                    onClick = { showRecoveryDialog = true },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Forgot Pass Key?", fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (passKeyInput == securityManager.getPassKey()) {
                        onAuthorized()
                    } else {
                        errorMessage = "Incorrect Pass Key"
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Unlock")
            }
        },
        dismissButton = {
            if (securityManager.canUseBiometric()) {
                TextButton(onClick = {
                    securityManager.showBiometricPrompt(activity, onBiometricSuccess, onBiometricError)
                }) {
                    Text("Use Biometric")
                }
            }
        }
    )

    if (showRecoveryDialog) {
        RecoveryDialog(
            securityManager = securityManager,
            onDismiss = { showRecoveryDialog = false },
            onRecovered = {
                showRecoveryDialog = false
                onAuthorized()
            }
        )
    }
}

@Composable
fun RecoveryDialog(
    securityManager: SecurityManager,
    onDismiss: () -> Unit,
    onRecovered: () -> Unit
) {
    var animalInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Recover Access") },
        text = {
            Column {
                Text("Enter your favorite animal to reset access.")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = animalInput,
                    onValueChange = { animalInput = it },
                    label = { Text("Favorite Animal") },
                    isError = errorMessage != null
                )
                if (errorMessage != null) {
                    Text(errorMessage!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val savedAnimal = securityManager.getAnimalName()
                    if (animalInput.lowercase().trim() == savedAnimal) {
                        securityManager.clearPassKey()
                        onRecovered()
                    } else {
                        errorMessage = "Incorrect Answer"
                    }
                }
            ) {
                Text("Reset & Unlock")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen(viewModel: CalculatorViewModel = viewModel(), securityManager: SecurityManager) {
    val displayValue = viewModel.display.value
    val isScientificExpanded = viewModel.isScientificExpanded.value
    val history = viewModel.history.value
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    
    val haptic = LocalHapticFeedback.current
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    var showHistory by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showThemePicker by remember { mutableStateOf(false) }

    // Permission request for notifications (Android 13+)
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (!isGranted) {
                Toast.makeText(context, "Notifications disabled", Toast.LENGTH_SHORT).show()
            }
        }
    )

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val buttonAspectRatio = if (isLandscape) 3.2f else 1.2f
    val buttonFontSize = if (isLandscape) 20.sp else 28.sp
    val displayFontSize = if (isLandscape) 21.sp else 36.sp
    val rowSpacing = if (isLandscape) 8.dp else 16.dp
    val outerPadding = if (isLandscape) 8.dp else 24.dp

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Calculator", fontSize = 18.sp) },
                actions = {
                    IconButton(onClick = { showThemePicker = true }) {
                        Icon(Icons.Default.Palette, contentDescription = "Choose Theme")
                    }
                    IconButton(onClick = { showHistory = true }) {
                        Icon(Icons.Default.History, contentDescription = "History")
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Lock, contentDescription = "Security Settings")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = outerPadding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!isLandscape) {
                Spacer(modifier = Modifier.weight(1f))
            }
            
            Column(
                modifier = Modifier
                    .fillMaxWidth(if (isLandscape) 0.8f else 1f)
                    .padding(bottom = if (isLandscape) 8.dp else 32.dp),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = displayValue,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = displayFontSize,
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { },
                            onLongClick = {
                                clipboardManager.setText(AnnotatedString(displayValue))
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                            }
                        ),
                    maxLines = Int.MAX_VALUE,
                    softWrap = true,
                    lineHeight = displayFontSize * 1.1
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(if (isLandscape) 0.8f else 1f),
                verticalArrangement = Arrangement.spacedBy(rowSpacing)
            ) {
                AnimatedVisibility(
                    visible = isScientificExpanded,
                    enter = expandVertically(animationSpec = tween(300)),
                    exit = shrinkVertically(animationSpec = tween(300))
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(rowSpacing)) {
                        CalculatorRow(rowSpacing) {
                            CalculatorButton("π", MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.primary, buttonAspectRatio, buttonFontSize) { viewModel.onPiClick() }
                            CalculatorButton("xⁿ", MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.primary, buttonAspectRatio, buttonFontSize) { viewModel.onOperatorClick("^") }
                            CalculatorButton("√", MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.primary, buttonAspectRatio, buttonFontSize) { viewModel.onSqrtClick() }
                            CalculatorButton("!", MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.primary, buttonAspectRatio, buttonFontSize) { viewModel.onFactorialClick() }
                        }
                    }
                }

                CalculatorRow(rowSpacing) {
                    CalculatorButton("AC", MaterialTheme.colorScheme.primary, Color.White, buttonAspectRatio, buttonFontSize) { viewModel.onClearClick() }
                    CalculatorButton("+/-", MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.onBackground, buttonAspectRatio, buttonFontSize) { viewModel.onSignClick() }
                    CalculatorButton("%", MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.onBackground, buttonAspectRatio, buttonFontSize) { viewModel.onPercentClick() }
                    CalculatorButton("÷", MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.primary, buttonAspectRatio, buttonFontSize) { viewModel.onOperatorClick("/") }
                }

                CalculatorRow(rowSpacing) {
                    CalculatorButton("7", MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onBackground, buttonAspectRatio, buttonFontSize) { viewModel.onDigitClick("7") }
                    CalculatorButton("8", MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onBackground, buttonAspectRatio, buttonFontSize) { viewModel.onDigitClick("8") }
                    CalculatorButton("9", MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onBackground, buttonAspectRatio, buttonFontSize) { viewModel.onDigitClick("9") }
                    CalculatorButton("×", MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.primary, buttonAspectRatio, buttonFontSize) { viewModel.onOperatorClick("*") }
                }

                CalculatorRow(rowSpacing) {
                    CalculatorButton("4", MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onBackground, buttonAspectRatio, buttonFontSize) { viewModel.onDigitClick("4") }
                    CalculatorButton("5", MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onBackground, buttonAspectRatio, buttonFontSize) { viewModel.onDigitClick("5") }
                    CalculatorButton("6", MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onBackground, buttonAspectRatio, buttonFontSize) { viewModel.onDigitClick("6") }
                    CalculatorButton("-", MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.primary, buttonAspectRatio, buttonFontSize) { viewModel.onOperatorClick("-") }
                }

                CalculatorRow(rowSpacing) {
                    CalculatorButton("1", MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onBackground, buttonAspectRatio, buttonFontSize) { viewModel.onDigitClick("1") }
                    CalculatorButton("2", MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onBackground, buttonAspectRatio, buttonFontSize) { viewModel.onDigitClick("2") }
                    CalculatorButton("3", MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onBackground, buttonAspectRatio, buttonFontSize) { viewModel.onDigitClick("3") }
                    CalculatorButton("+", MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.primary, buttonAspectRatio, buttonFontSize) { viewModel.onOperatorClick("+") }
                }

                CalculatorRow(rowSpacing) {
                    CalculatorButton("...", MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.primary, buttonAspectRatio, buttonFontSize) { viewModel.toggleScientific() }
                    CalculatorButton("0", MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onBackground, buttonAspectRatio, buttonFontSize) { viewModel.onDigitClick("0") }
                    CalculatorButton(".", MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onBackground, buttonAspectRatio, buttonFontSize) { viewModel.onDecimalClick() }
                    CalculatorButton("=", MaterialTheme.colorScheme.primary, Color.White, buttonAspectRatio, buttonFontSize) { viewModel.onEqualClick() }
                }
            }
        }
    }

    if (showHistory) {
        AlertDialog(
            onDismissRequest = { showHistory = false },
            title = { Text("Cloud History") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(history) { entry ->
                        Text(entry, modifier = Modifier.padding(vertical = 4.dp))
                        HorizontalDivider()
                    }
                    if (history.isEmpty()) {
                        item { Text("No history found in cloud.") }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHistory = false }) { Text("Close") }
            }
        )
    }

    if (showSettings) {
        SecuritySettingsDialog(
            securityManager = securityManager,
            onDismiss = { showSettings = false }
        )
    }

    if (showThemePicker) {
        ThemePickerDialog(
            currentTheme = viewModel.appTheme.value,
            onThemeSelected = {
                viewModel.setTheme(it)
                showThemePicker = false
            },
            onDismiss = { showThemePicker = false }
        )
    }
}

@Composable
fun ThemePickerDialog(
    currentTheme: AppTheme,
    onThemeSelected: (AppTheme) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose Theme") },
        text = {
            Column {
                AppTheme.entries.forEach { theme ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onThemeSelected(theme) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = theme == currentTheme,
                            onClick = { onThemeSelected(theme) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(theme.displayName)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun SecuritySettingsDialog(securityManager: SecurityManager, onDismiss: () -> Unit) {
    var passKeyInput by remember { mutableStateOf("") }
    var animalNameInput by remember { mutableStateOf("") }
    val isSet = securityManager.isPassKeySet()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isSet) "Change Pass Key" else "Set Pass Key") },
        text = {
            Column {
                Text("Set a numeric Pass Key for extra security.")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = passKeyInput,
                    onValueChange = { if (it.length <= 8) passKeyInput = it },
                    label = { Text("New Pass Key") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = animalNameInput,
                    onValueChange = { animalNameInput = it },
                    label = { Text("Favorite Animal (Recovery)") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (passKeyInput.isNotEmpty() && animalNameInput.isNotEmpty()) {
                        securityManager.savePassKey(passKeyInput, animalNameInput)
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            if (isSet) {
                TextButton(onClick = {
                    securityManager.clearPassKey()
                    onDismiss()
                }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    )
}

@Composable
fun CalculatorRow(spacing: androidx.compose.ui.unit.Dp, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        content = content
    )
}

@Composable
fun RowScope.CalculatorButton(
    text: String,
    backgroundColor: Color,
    contentColor: Color,
    aspectRatio: Float,
    fontSize: androidx.compose.ui.unit.TextUnit,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .weight(1f)
            .aspectRatio(aspectRatio)
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
    ) {
        Text(
            text = text,
            color = contentColor,
            fontSize = fontSize,
            fontWeight = FontWeight.Normal
        )
    }
}
