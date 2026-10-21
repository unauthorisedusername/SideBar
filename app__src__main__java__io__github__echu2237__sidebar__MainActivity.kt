package io.github.echu2237.sidebar

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import io.github.echu2237.sidebar.data.AppEntry
import io.github.echu2237.sidebar.data.AppRepository
import io.github.echu2237.sidebar.data.FavoritesRepository
import io.github.echu2237.sidebar.freeform.FreeformLauncher
import io.github.echu2237.sidebar.service.SidebarService
import io.github.echu2237.sidebar.shizuku.ShizukuManager
import io.github.echu2237.sidebar.ui.AkButton
import io.github.echu2237.sidebar.ui.AkCheckbox
import io.github.echu2237.sidebar.ui.AkColors
import io.github.echu2237.sidebar.ui.AkField
import io.github.echu2237.sidebar.ui.AkHeader
import io.github.echu2237.sidebar.ui.AkPanel
import io.github.echu2237.sidebar.ui.AkStatusRow
import io.github.echu2237.sidebar.ui.AkSwitch
import io.github.echu2237.sidebar.ui.AkText
import io.github.echu2237.sidebar.ui.akShape
import io.github.echu2237.sidebar.ui.AkCorner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    internal val hasOverlayPermission = mutableStateOf(false)
    internal val serviceRunning = mutableStateOf(false)
    internal val freeformState = mutableStateOf(Pair(false, false)) // freeform windows on, force-resizable on
    internal val shizukuState = mutableStateOf(Triple(false, false, false)) // installed, active, granted

    private val permissionListener =
        rikka.shizuku.Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            refreshShizukuState()
            if (grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                startService(Intent(this, SidebarService::class.java))
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Auto-recovery: MIUI's "Clear all" force-stops this app and the app cannot revive itself,
        // so recovery cost is minimized: just opening the app restarts the service.
        // Only in onCreate (not onResume): after the user stops the service inside the app, it is not restarted right away.
        if (Settings.canDrawOverlays(this) && !SidebarService.isRunning) {
            runCatching {
                startForegroundService(Intent(this, SidebarService::class.java))
            }
        }
        // No longer requesting POST_NOTIFICATIONS: the foreground service runs without it;
        // without the permission the system does not show the persistent "Sidebar running" notification
        try {
            rikka.shizuku.Shizuku.addRequestPermissionResultListener(permissionListener)
        } catch (_: Exception) {
        }
        setContent {
            SidebarConsole()
        }
    }

    override fun onDestroy() {
        try {
            rikka.shizuku.Shizuku.removeRequestPermissionResultListener(permissionListener)
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        hasOverlayPermission.value = Settings.canDrawOverlays(this)
        serviceRunning.value = SidebarService.isRunning
        refreshShizukuState()
    }

    internal fun refreshShizukuState() {
        freeformState.value = Pair(
            FreeformLauncher.isFreeformSettingOn(this),
            FreeformLauncher.isForceResizableOn(this),
        )
        shizukuState.value = Triple(
            ShizukuManager.isShizukuInstalled,
            ShizukuManager.isShizukuActive,
            ShizukuManager.isPermissionGranted,
        )
    }
}

@Composable
fun SidebarConsole() {
    Box(
        Modifier
            .fillMaxSize()
            .background(AkColors.Bg),
    ) {
        var showPicker by remember { mutableStateOf(false) }
        if (showPicker) {
            AppPickerScreen(onBack = { showPicker = false })
        } else {
            MainScreen(onManageApps = { showPicker = true })
        }
    }
}

@Composable
fun MainScreen(onManageApps: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? MainActivity
    val hasOverlayPermission = activity?.hasOverlayPermission?.value ?: false
    val serviceRunning = activity?.serviceRunning?.value ?: false
    val shizuku = activity?.shizukuState?.value ?: Triple(false, false, false)
    val (freeformOn, forceResizable) = activity?.freeformState?.value ?: Pair(false, false)
    val preferShizuku by remember { FavoritesRepository.preferShizukuFlow(context) }
        .collectAsState(initial = false)

    val autostart by remember { FavoritesRepository.autostartEnabledFlow(context) }
        .collectAsState(initial = true)
    val selected by remember { FavoritesRepository.selectedAppsFlow(context) }
        .collectAsState(initial = emptySet())
    val scope = rememberCoroutineScope()

    val powerManager = remember {
        context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
    }
    var ignoringBattery by remember {
        mutableStateOf(powerManager.isIgnoringBatteryOptimizations(context.packageName))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AkHeader(crumb = "// SIDEBAR SYSTEM", title = "SIDEBAR CONSOLE", right = "HYPEROS")

        // 01 System status
        AkPanel("01", "SYSTEM") {
            AkStatusRow(
                "Overlay permission",
                if (hasOverlayPermission) "Granted" else "Not granted",
                if (hasOverlayPermission) AkColors.Ok else AkColors.Danger,
            )
            AkStatusRow(
                "Service status",
                if (serviceRunning) "Running" else "Stopped",
                if (serviceRunning) AkColors.Ok else AkColors.TextDim,
            )
            when {
                !hasOverlayPermission -> AkButton("Grant overlay permission", primary = true) {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}"),
                        ),
                    )
                }
                !serviceRunning -> AkButton("Start sidebar service", primary = true) {
                    context.startForegroundService(Intent(context, SidebarService::class.java))
                }
                else -> AkButton("Stop service") {
                    context.startService(
                        Intent(context, SidebarService::class.java)
                            .setAction(SidebarService.ACTION_STOP),
                    )
                }
            }
        }

        // 02 Sidebar content
        AkPanel("02", "CONTENT") {
            AkStatusRow(
                "Selected apps",
                if (selected.isEmpty()) "Show all" else "${selected.size} selected",
                if (selected.isEmpty()) AkColors.TextDim else AkColors.Accent,
            )
            AkText(
                "With no apps selected, the sidebar shows all apps",
                color = AkColors.TextDim,
                size = 10,
            )
            AkButton("Manage apps", primary = true, onClick = onManageApps)
        }

        // 03 Floating window mode
        AkPanel("03", "FREEFORM WINDOWS") {
            AkStatusRow(
                "Freeform windows (system)",
                if (freeformOn) "On" else "Off",
                if (freeformOn) AkColors.Ok else AkColors.Warn,
            )
            AkStatusRow(
                "Force resizable apps",
                if (forceResizable) "On" else "Off",
                if (forceResizable) AkColors.Ok else AkColors.TextDim,
            )
            if (!freeformOn || !forceResizable) {
                AkText(
                    "No Shizuku needed: open Developer options and turn on \"Enable freeform windows\" " +
                        "(and \"Force activities to be resizable\" so every app can float). Then tap an icon in the sidebar.",
                    color = if (freeformOn) AkColors.TextDim else AkColors.Warn,
                    size = 10,
                )
                AkButton("Open developer options", primary = !freeformOn) {
                    FreeformLauncher.openDeveloperOptions(context)
                }
            } else {
                AkText(
                    "Ready — tapping a sidebar icon opens the app in a floating window. Falls back to fullscreen on failure.",
                    color = AkColors.Ok,
                    size = 10,
                )
            }

            Spacer(Modifier.height(4.dp))
            val (installed, active, granted) = shizuku
            AkStatusRow(
                "SHIZUKU (optional fallback)",
                when {
                    !installed -> "Not installed"
                    !active -> "Not active"
                    !granted -> "Needs permission"
                    else -> "Ready"
                },
                when {
                    !installed -> AkColors.TextDim
                    !active -> AkColors.Warn
                    !granted -> AkColors.Warn
                    else -> AkColors.Ok
                },
            )
            if (installed && active && !granted) {
                AkButton("AUTHORIZE SHIZUKU") {
                    try {
                        ShizukuManager.requestPermission(1)
                    } catch (_: Exception) {
                        activity?.refreshShizukuState()
                    }
                }
            }
            if (granted) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        AkText("Prefer Shizuku", size = 12)
                        Spacer(Modifier.height(2.dp))
                        AkText(
                            "Use Shizuku before the direct method. Turn on if the direct method opens apps fullscreen.",
                            color = AkColors.TextDim,
                            size = 10,
                        )
                    }
                    AkSwitch(checked = preferShizuku) { checked ->
                        scope.launch { FavoritesRepository.setPreferShizuku(context, checked) }
                    }
                }
            }
        }

        // 04 Background survival
        AkPanel("04", "BACKGROUND") {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    AkText("Start on boot", size = 12)
                    Spacer(Modifier.height(2.dp))
                    AkText("Restores the sidebar after the tablet restarts", color = AkColors.TextDim, size = 10)
                }
                AkSwitch(checked = autostart) { checked ->
                    scope.launch { FavoritesRepository.setAutostartEnabled(context, checked) }
                }
            }
            AkStatusRow(
                "Battery optimization",
                if (ignoringBattery) "Whitelisted" else "Not whitelisted",
                if (ignoringBattery) AkColors.Ok else AkColors.Warn,
            )
            if (!ignoringBattery) {
                AkButton("Add to battery optimization whitelist") {
                    try {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:${context.packageName}"),
                            ),
                        )
                    } catch (_: Exception) {
                        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    }
                    ignoringBattery =
                        powerManager.isIgnoringBatteryOptimizations(context.packageName)
                }
            }
            Spacer(Modifier.height(2.dp))
            AkText(
                "After being cleared by the system's \"Clear all\": open this app, or pull down the shade and tap the \"Sidebar\" tile to restore",
                color = AkColors.TextDim,
                size = 10,
            )
        }

        Spacer(Modifier.height(4.dp))
        AkText(
            "//  ONLY FOR THIS DEVICE  ·  NO ROOT REQUIRED",
            color = AkColors.TextDim,
            size = 9,
            spacing = 1.5f,
        )
    }
}

@Composable
fun AppPickerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    val selected by remember { FavoritesRepository.selectedAppsFlow(context) }
        .collectAsState(initial = emptySet())

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { AppRepository.loadLaunchableApps(context) }
        loading = false
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 20.dp),
    ) {
        // Back
        Row(
            Modifier
                .clickable { onBack() }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AkText("◀", color = AkColors.Accent, size = 12, mono = false)
            Spacer(Modifier.width(8.dp))
            AkText("BACK", color = AkColors.Accent, size = 11, bold = true, spacing = 2f)
        }
        Spacer(Modifier.height(10.dp))

        AkHeader(
            crumb = "// SIDEBAR / APPS",
            title = "SELECT APPS",
            right = if (loading) "LOADING" else "${apps.size} APPS",
        )
        Spacer(Modifier.height(14.dp))

        // Search box
        AkField {
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(
                    color = AkColors.Text,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                ),
                cursorBrush = SolidColor(AkColors.Accent),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        AkText("Search apps…", color = AkColors.TextDim, size = 13)
                    }
                    inner()
                },
            )
        }
        Spacer(Modifier.height(10.dp))

        AkStatusRow(
            "Selected",
            if (selected.isEmpty()) "None (show all)" else "${selected.size} selected",
            if (selected.isEmpty()) AkColors.TextDim else AkColors.Accent,
        )
        Spacer(Modifier.height(10.dp))

        if (loading) {
            AkText("Loading app list…", color = AkColors.TextDim, size = 11)
        } else {
            val filtered = if (query.isBlank()) apps else apps.filter {
                it.label.contains(query, ignoreCase = true)
            }
            LazyColumn(Modifier.fillMaxWidth()) {
                itemsIndexed(filtered, key = { _, app -> app.packageName }) { idx, app ->
                    AppPickRow(
                        index = idx + 1,
                        app = app,
                        checked = app.packageName in selected,
                        onToggle = { isChecked ->
                            scope.launch {
                                val next = selected.toMutableSet()
                                if (isChecked) next.add(app.packageName) else next.remove(app.packageName)
                                FavoritesRepository.setSelectedApps(context, next)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun AppPickRow(index: Int, app: AppEntry, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle(!checked) }
                .background(if (checked) AkColors.PanelAlt else Color.Transparent)
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AkText(
                index.toString().padStart(2, '0'),
                color = if (checked) AkColors.Accent else AkColors.TextDim,
                size = 10,
            )
            Spacer(Modifier.width(12.dp))
            AppIcon(app.icon, Modifier.size(34.dp))
            Spacer(Modifier.width(12.dp))
            AkText(
                app.label,
                modifier = Modifier.weight(1f),
                size = 13,
                mono = false,
            )
            AkCheckbox(checked = checked) { onToggle(!checked) }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(AkColors.Line),
        )
    }
}

@Composable
fun AppIcon(drawable: Drawable, modifier: Modifier = Modifier) {
    val bitmap = remember(drawable) { drawable.toBitmap(96, 96).asImageBitmap() }
    Image(bitmap = bitmap, contentDescription = null, modifier = modifier)
}
