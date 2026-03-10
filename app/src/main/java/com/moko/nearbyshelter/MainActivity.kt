package com.moko.nearbyshelter

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    companion object {
        const val PREFS = "alert_prefs"
        const val MODE_KEY = "mode"

        const val MODE_OFF = 0
        const val MODE_ALERT_ONLY = 1
        const val MODE_WARNING_AND_ALERT = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val savedMode = prefs.getInt(MODE_KEY, MODE_OFF)

        setContent {
            var selectedMode by remember { mutableStateOf(savedMode) }
            val context = LocalContext.current
            
            // Permission states
            var isNotificationServiceGranted by remember { mutableStateOf(isNotificationServiceEnabled(context)) }
            var isLocationGranted by remember { 
                mutableStateOf(
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                )
            }
            var isBackgroundLocationGranted by remember {
                mutableStateOf(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
                    } else true
                )
            }
            var isOverlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
            var isBatteryOptIgnored by remember {
                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                mutableStateOf(pm.isIgnoringBatteryOptimizations(context.packageName))
            }
            var isPostNotificationGranted by remember {
                mutableStateOf(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                    } else true
                )
            }

            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                isLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                                   permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    isBackgroundLocationGranted = permissions[Manifest.permission.ACCESS_BACKGROUND_LOCATION] == true
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    isPostNotificationGranted = permissions[Manifest.permission.POST_NOTIFICATIONS] == true
                }
            }

            // Auto-prompt for permissions on launch
            LaunchedEffect(Unit) {
                val perms = mutableListOf<String>()
                if (!isLocationGranted) {
                    perms.add(Manifest.permission.ACCESS_FINE_LOCATION)
                    perms.add(Manifest.permission.ACCESS_COARSE_LOCATION)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !isPostNotificationGranted) {
                    perms.add(Manifest.permission.POST_NOTIFICATIONS)
                }
                if (perms.isNotEmpty()) {
                    permissionLauncher.launch(perms.toTypedArray())
                }
            }

            // Re-check permissions when user returns from settings
            DisposableEffect(Unit) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                        isNotificationServiceGranted = isNotificationServiceEnabled(context)
                        isLocationGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                                           ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            isBackgroundLocationGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
                        }
                        isOverlayGranted = Settings.canDrawOverlays(context)
                        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                        isBatteryOptIgnored = pm.isIgnoringBatteryOptimizations(context.packageName)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            isPostNotificationGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                        }
                    }
                }
                lifecycle.addObserver(observer)
                onDispose {
                    lifecycle.removeObserver(observer)
                }
            }

            NearbyShelterTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color.Transparent
                ) { innerPadding ->
                    MainScreen(
                        modifier = Modifier.padding(innerPadding),
                        selectedMode = selectedMode,
                        isNotificationServiceGranted = isNotificationServiceGranted,
                        isLocationGranted = isLocationGranted,
                        isBackgroundLocationGranted = isBackgroundLocationGranted,
                        isOverlayGranted = isOverlayGranted,
                        isBatteryOptIgnored = isBatteryOptIgnored,
                        isPostNotificationGranted = isPostNotificationGranted,
                        onModeSelected = { mode ->
                            selectedMode = mode
                            prefs.edit().putInt(MODE_KEY, mode).apply()
                        },
                        onRequestNotificationService = {
                            context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                        },
                        onRequestLocationPermission = {
                            if (!isLocationGranted) {
                                permissionLauncher.launch(arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                ))
                            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !isBackgroundLocationGranted) {
                                permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
                            }
                        },
                        onRequestOverlayPermission = {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        },
                        onRequestBatteryOptimization = {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        },
                        onRequestPostNotifications = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                            }
                        }
                    )
                }
            }
        }
    }

    private fun isNotificationServiceEnabled(context: Context): Boolean {
        val pkgName = context.packageName
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        if (!TextUtils.isEmpty(flat)) {
            val names = flat.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            for (name in names) {
                val cn = ComponentName.unflattenFromString(name)
                if (cn != null) {
                    if (TextUtils.equals(pkgName, cn.packageName)) {
                        return true
                    }
                }
            }
        }
        return false
    }
}

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    selectedMode: Int,
    isNotificationServiceGranted: Boolean,
    isLocationGranted: Boolean,
    isBackgroundLocationGranted: Boolean,
    isOverlayGranted: Boolean,
    isBatteryOptIgnored: Boolean,
    isPostNotificationGranted: Boolean,
    onModeSelected: (Int) -> Unit,
    onRequestNotificationService: () -> Unit,
    onRequestLocationPermission: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onRequestBatteryOptimization: () -> Unit,
    onRequestPostNotifications: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F172A), 
                        Color(0xFF020617)  
                    )
                )
            )
            .then(modifier)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(56.dp))
            
            Text(
                text = "Nearby Shelter",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = (-1).sp
                )
            )
            
            Text(
                text = "Stay safe and informed",
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = Color(0xFF94A3B8),
                    fontWeight = FontWeight.Medium
                )
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Permission Section
            Column(modifier = Modifier.fillMaxWidth()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !isPostNotificationGranted) {
                    PermissionWarning(
                        title = "Notifications Required",
                        description = "Needed to show you information when alerts happen.",
                        icon = Icons.Default.Notifications,
                        onClick = onRequestPostNotifications
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (!isNotificationServiceGranted) {
                    PermissionWarning(
                        title = "Alert Detection Required",
                        description = "Tap to enable 'Notification Access' so we can see incoming alerts.",
                        icon = Icons.Default.Settings,
                        onClick = onRequestNotificationService
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (!isLocationGranted) {
                    PermissionWarning(
                        title = "Location Permission Required",
                        description = "Needed to find the closest shelter accurately.",
                        icon = Icons.Default.LocationOn,
                        onClick = onRequestLocationPermission
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (!isOverlayGranted) {
                    PermissionWarning(
                        title = "Display Over Other Apps",
                        description = "Needed to wake up screen and show map when locked.",
                        icon = Icons.Default.Layers,
                        onClick = onRequestOverlayPermission
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (!isBatteryOptIgnored) {
                    PermissionWarning(
                        title = "Disable Battery Optimization",
                        description = "Ensures the app isn't closed by the system in the background.",
                        icon = Icons.Default.BatteryAlert,
                        onClick = onRequestBatteryOptimization
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && isLocationGranted && !isBackgroundLocationGranted) {
                Button(
                    onClick = onRequestLocationPermission,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(12.dp))
                    Text("Grant Background Location (Allow Always)", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            ModeCard(
                title = "Silent Mode",
                description = "Mute all shelter alerts and notifications.",
                icon = Icons.Default.NotificationsOff,
                isSelected = selectedMode == MainActivity.MODE_OFF,
                accentColor = Color(0xFF64748B),
                onClick = { onModeSelected(MainActivity.MODE_OFF) }
            )

            Spacer(modifier = Modifier.height(20.dp))

            ModeCard(
                title = "Critical Only",
                description = "Notify only for immediate emergency shelter alerts.",
                icon = Icons.Default.NotificationsActive,
                isSelected = selectedMode == MainActivity.MODE_ALERT_ONLY,
                accentColor = Color(0xFFF59E0B),
                onClick = { onModeSelected(MainActivity.MODE_ALERT_ONLY) }
            )

            Spacer(modifier = Modifier.height(20.dp))

            ModeCard(
                title = "All Alerts",
                description = "Full protection. Receive both warnings and alerts.",
                icon = Icons.Default.Warning,
                isSelected = selectedMode == MainActivity.MODE_WARNING_AND_ALERT,
                accentColor = Color(0xFFEF4444),
                onClick = { onModeSelected(MainActivity.MODE_WARNING_AND_ALERT) }
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = "Settings are saved automatically",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF475569),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = "Made with ❤️ by Amit Mokady",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8)
                ),
                modifier = Modifier.padding(bottom = 32.dp)
            )
        }
    }
}

@Composable
fun PermissionWarning(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
    isCritical: Boolean = false
) {
    Surface(
        onClick = onClick,
        color = if (isCritical) Color(0xFF7F1D1D) else Color(0xFF450A0A),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isCritical) Color.White else Color(0xFFFCA5A5)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isCritical) Color(0xFFFECACA) else Color(0xFFFCA5A5)
                )
            }
        }
    }
}

@Composable
fun ModeCard(
    title: String,
    description: String,
    icon: ImageVector,
    isSelected: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    val animatedElevation by animateDpAsState(
        targetValue = if (isSelected) 8.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "elevation"
    )

    Card(
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = animatedElevation),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF1E293B) else Color(0xFF111827)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isSelected) {
                    Modifier.shadow(
                        elevation = 16.dp,
                        shape = RoundedCornerShape(28.dp),
                        ambientColor = accentColor.copy(alpha = 0.5f),
                        spotColor = accentColor.copy(alpha = 0.5f)
                    )
                } else Modifier
            )
            .clip(RoundedCornerShape(28.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.width(20.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color(0xFF94A3B8),
                        lineHeight = 20.sp
                    )
                )
            }
            
            RadioButton(
                selected = isSelected,
                onClick = null,
                colors = RadioButtonDefaults.colors(
                    selectedColor = accentColor,
                    unselectedColor = Color(0xFF334155)
                )
            )
        }
    }
}

@Composable
fun NearbyShelterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF3B82F6),
            onPrimary = Color.White,
            surface = Color(0xFF0F172A),
            onSurface = Color.White,
            background = Color(0xFF020617)
        ),
        typography = Typography(),
        content = content
    )
}
