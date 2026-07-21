package com.hillel.studyzone.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hillel.studyzone.model.AdminUser
import com.hillel.studyzone.model.GeminiServerKey
import com.hillel.studyzone.model.SystemPasswordStatus
import com.hillel.studyzone.model.UiState
import com.hillel.studyzone.ui.components.GlassSurface
import com.hillel.studyzone.ui.components.Pressable
import com.hillel.studyzone.ui.components.ProfileAvatar
import com.hillel.studyzone.ui.components.RoundActionButton
import com.hillel.studyzone.ui.theme.StudyBlue
import java.text.DateFormat
import java.util.Date

@Composable
fun AdminScreen(
    state: UiState,
    onClose: () -> Unit,
    onReload: () -> Unit,
    onSelectUser: (String) -> Unit,
    onToggleBlock: (String) -> Unit,
    onDeleteUser: (String) -> Unit,
    onSendMessage: (String, String, String, Boolean, Boolean) -> Unit,
    onLoadRequests: (String) -> Unit,
    onAccess: (String, String) -> Unit,
    onTogglePublic: (String) -> Unit,
    onClearPublic: () -> Unit,
    onSettings: (Boolean, Boolean, Boolean, String) -> Unit,
    onCreateKey: (String, String) -> Unit,
    onUpdateKey: (String, String, String?, Boolean) -> Unit,
    onClearCooldown: (String) -> Unit,
    onDeleteKey: (String) -> Unit,
    onExportKeys: () -> Unit,
    onPassword: (String, String) -> Unit
) {
    var tab by remember { mutableStateOf("overview") }
    val tabs = listOf(
        "overview" to "סקירה",
        "users" to "משתמשים",
        "requests" to "בקשות גישה",
        "public" to "קורסים ציבוריים",
        "settings" to "הגדרות",
        "gemini" to "Gemini Keys",
        "passwords" to "סיסמאות"
    )
    var messageTarget by remember { mutableStateOf<String?>(null) }
    var messageTargetLabel by remember { mutableStateOf("") }
    var deleteUserId by remember { mutableStateOf<String?>(null) }
    var clearPublicConfirm by remember { mutableStateOf(false) }
    var editKey by remember { mutableStateOf<GeminiServerKey?>(null) }
    var deleteKey by remember { mutableStateOf<GeminiServerKey?>(null) }
    var editPassword by remember { mutableStateOf<SystemPasswordStatus?>(null) }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .statusBarsPadding().navigationBarsPadding()
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundActionButton(Icons.AutoMirrored.Rounded.ArrowBack, "סגירה", onClose, size = 46.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("מרכז ניהול", style = MaterialTheme.typography.headlineMedium)
                Text("כל יכולות הניהול של האתר", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RoundActionButton(Icons.Rounded.Refresh, "רענון", onReload, size = 46.dp)
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(tabs, key = { it.first }) { item ->
                Pressable(
                    onClick = { tab = item.first },
                    selected = tab == item.first,
                    shape = CircleShape,
                    contentPadding = 11.dp
                ) {
                    Text(item.second, color = if (tab == item.first) StudyBlue else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        AnimatedVisibility(state.adminLoading, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("מסנכרנים מול השרת…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        when (tab) {
            "overview" -> FullAdminOverview(state)
            "users" -> FullAdminUsers(
                state = state,
                onSelect = onSelectUser,
                onToggleBlock = onToggleBlock,
                onMessage = { id, label -> messageTarget = id; messageTargetLabel = label },
                onGlobalMessage = { messageTarget = "all"; messageTargetLabel = "כל המשתמשים" },
                onDelete = { deleteUserId = it }
            )
            "requests" -> FullAdminRequests(state, onLoadRequests, onAccess)
            "public" -> FullAdminPublicCourses(state, onTogglePublic) { clearPublicConfirm = true }
            "settings" -> FullAdminSettings(state, onSettings)
            "gemini" -> FullAdminGemini(
                state = state,
                onCreate = onCreateKey,
                onEdit = { editKey = it },
                onToggle = { key -> onUpdateKey(key.id, key.label, null, !key.enabled) },
                onCooldown = onClearCooldown,
                onDelete = { deleteKey = it },
                onExport = onExportKeys
            )
            "passwords" -> FullAdminPasswords(state.adminPasswords) { editPassword = it }
        }
    }

    messageTarget?.let { target ->
        AdminMessageDialog(
            targetLabel = messageTargetLabel,
            onDismiss = { messageTarget = null },
            onSend = { subject, content, email, inApp ->
                onSendMessage(target, subject, content, email, inApp)
                messageTarget = null
            }
        )
    }
    deleteUserId?.let { userId ->
        ConfirmAdminDialog(
            title = "מחיקת משתמש",
            message = "המשתמש יימחק גם ממסד הנתונים וגם מ־Firebase Auth. אי אפשר לבטל את הפעולה.",
            confirmLabel = "מחיקה לצמיתות",
            onDismiss = { deleteUserId = null },
            onConfirm = { onDeleteUser(userId); deleteUserId = null }
        )
    }
    if (clearPublicConfirm) {
        ConfirmAdminDialog(
            title = "סגירת כל הקורסים הציבוריים",
            message = "כל הקורסים יחזרו לדרוש הרשאה.",
            confirmLabel = "סגירת הכול",
            onDismiss = { clearPublicConfirm = false },
            onConfirm = { onClearPublic(); clearPublicConfirm = false }
        )
    }
    editKey?.let { key ->
        GeminiKeyDialog(key, onDismiss = { editKey = null }) { label, apiKey, enabled ->
            onUpdateKey(key.id, label, apiKey.takeIf(String::isNotBlank), enabled)
            editKey = null
        }
    }
    deleteKey?.let { key ->
        ConfirmAdminDialog(
            title = "מחיקת מפתח Gemini",
            message = "${key.label.ifBlank { key.maskedKey }} יימחק מהשרת.",
            confirmLabel = "מחיקת מפתח",
            onDismiss = { deleteKey = null },
            onConfirm = { onDeleteKey(key.id); deleteKey = null }
        )
    }
    editPassword?.let { item ->
        PasswordAdminDialog(item, onDismiss = { editPassword = null }) { password ->
            onPassword(item.type, password)
            editPassword = null
        }
    }
}

@Composable
private fun FullAdminOverview(state: UiState) {
    val overview = state.adminOverview
    val metrics = listOf(
        Triple("סה״כ משתמשים", overview?.totalUsers ?: 0, Icons.Rounded.Group),
        Triple("משתמשים חסומים", overview?.blockedUsers ?: 0, Icons.Rounded.Lock),
        Triple("בקשות ממתינות", overview?.pendingRequests ?: 0, Icons.Rounded.Shield),
        Triple("קורסים ציבוריים", overview?.publicCoursesCount ?: 0, Icons.Rounded.Public),
        Triple("מפתחות Gemini", overview?.geminiServerKeysTotal ?: 0, Icons.Rounded.Key),
        Triple("מפתחות פעילים", overview?.geminiServerKeysActive ?: 0, Icons.Rounded.Check),
        Triple("מפתחות ב־cooldown", overview?.geminiServerKeysCooldown ?: 0, Icons.Rounded.Refresh)
    )
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 18.dp, 16.dp, 64.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            GlassSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(30.dp), selected = true) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AdminPanelSettings, null, tint = StudyBlue, modifier = Modifier.size(34.dp))
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("מצב מערכת", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Pythi: ${overview?.pythiChatModel.orEmpty().ifBlank { "לא הוגדר" }}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        items(metrics) { metric -> AdminMetric(metric.first, metric.second, metric.third) }
    }
}

@Composable
private fun AdminMetric(title: String, value: Int, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    GlassSurface(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(CircleShape).background(StudyBlue.copy(.14f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = StudyBlue)
            }
            Spacer(Modifier.width(14.dp))
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Text(value.toString(), style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Composable
private fun FullAdminUsers(
    state: UiState,
    onSelect: (String) -> Unit,
    onToggleBlock: (String) -> Unit,
    onMessage: (String, String) -> Unit,
    onGlobalMessage: () -> Unit,
    onDelete: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val users = remember(query, state.adminUsers) {
        state.adminUsers.filter { query.isBlank() || it.email.contains(query, true) || it.displayName.contains(query, true) }
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 14.dp, 16.dp, 64.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("שם או אימייל") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    shape = RoundedCornerShape(22.dp),
                    singleLine = true
                )
                Spacer(Modifier.width(8.dp))
                RoundActionButton(Icons.Rounded.Email, "הודעה גורפת", onGlobalMessage, size = 50.dp, active = true)
            }
        }
        state.adminSelectedUser?.let { user ->
            item(key = "selected-${user.userId}") {
                GlassSurface(Modifier.fillMaxWidth(), selected = true, shape = RoundedCornerShape(28.dp)) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ProfileAvatar(user.photoUrl, user.displayName.ifBlank { user.email }, size = 56.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(user.displayName.ifBlank { "ללא שם" }, style = MaterialTheme.typography.titleLarge)
                                Text(user.email, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    "${if (user.isVerified) "מאומת" else "לא מאומת"} · ${if (user.isGoogle) "Google" else "Password"}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Text("נוצר: ${formatAdminDate(user.createdAt)} · כניסה: ${formatAdminDate(user.lastLoginAt)}", style = MaterialTheme.typography.bodyMedium)
                        Text("הישגים: ${user.achievementsCount} · API Keys: ${user.apiKeysCount}", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(10.dp))
                        user.metrics.entries.chunked(3).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                row.forEach { metric ->
                                    Box(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(9.dp)) {
                                        Column {
                                            Text(metric.value.toString(), fontWeight = FontWeight.Bold)
                                            Text(adminMetricLabel(metric.key), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                            Spacer(Modifier.height(7.dp))
                        }
                        if (user.allowedCourseIds.isNotEmpty() || user.pendingCourseIds.isNotEmpty() || user.deniedCourseIds.isNotEmpty()) {
                            Text("גישה: ${user.allowedCourseIds.size} מאושרים · ${user.pendingCourseIds.size} ממתינים · ${user.deniedCourseIds.size} נדחו", style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Pressable(onClick = { onToggleBlock(user.userId) }, modifier = Modifier.weight(1f), selected = user.isBlocked) {
                                Icon(if (user.isBlocked) Icons.Rounded.LockOpen else Icons.Rounded.Lock, null)
                                Spacer(Modifier.width(5.dp))
                                Text(if (user.isBlocked) "שחרור" else "חסימה")
                            }
                            Pressable(onClick = { onMessage(user.userId, user.displayName.ifBlank { user.email }) }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Rounded.Email, null)
                                Spacer(Modifier.width(5.dp))
                                Text("הודעה")
                            }
                            RoundActionButton(Icons.Rounded.DeleteForever, "מחיקת משתמש", { onDelete(user.userId) }, size = 50.dp)
                        }
                    }
                }
            }
        }
        item { Text("נמצאו ${users.size} משתמשים", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium) }
        items(users, key = AdminUser::id) { user ->
            Pressable(onClick = { onSelect(user.id) }, modifier = Modifier.fillMaxWidth(), selected = state.adminSelectedUser?.userId == user.id) {
                ProfileAvatar(user.photoUrl, user.displayName.ifBlank { user.email }, size = 44.dp)
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(user.displayName.ifBlank { "ללא שם" }, fontWeight = FontWeight.Bold)
                    Text(user.email, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${if (user.isBlocked) "חסום" else "פעיל"} · ${if (user.isVerified) "מאומת" else "לא מאומת"} · ${if (user.isGoogle) "Google" else "Password"}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                Icon(if (user.isBlocked) Icons.Rounded.Lock else Icons.Rounded.Edit, null, tint = if (user.isBlocked) MaterialTheme.colorScheme.error else StudyBlue)
            }
        }
    }
}

@Composable
private fun FullAdminRequests(state: UiState, onLoad: (String) -> Unit, onAccess: (String, String) -> Unit) {
    var filter by remember { mutableStateOf("pending") }
    val filters = listOf("pending" to "ממתינות", "all" to "הכול", "approved" to "אושרו", "rejected" to "נדחו", "revoked" to "בוטלו")
    LaunchedEffect(filter) { onLoad(filter) }
    Column(Modifier.fillMaxSize()) {
        LazyRow(contentPadding = PaddingValues(16.dp, 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filters, key = { it.first }) { item ->
                Pressable(onClick = { filter = item.first }, selected = filter == item.first, shape = CircleShape, contentPadding = 10.dp) {
                    Text(item.second, color = if (filter == item.first) StudyBlue else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 64.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (state.accessRequests.isEmpty() && !state.adminLoading) item { AdminEmpty("אין בקשות במסנן הזה") }
            items(state.accessRequests, key = { it.id }) { request ->
                GlassSurface(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(request.userDisplayName.ifBlank { request.userEmail }, fontWeight = FontWeight.Bold)
                        Text(request.userEmail, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(request.courseTitle.ifBlank { request.courseId }, color = StudyBlue, modifier = Modifier.padding(vertical = 7.dp))
                        Text("סטטוס: ${request.status} · ${formatAdminDate(request.updatedAt ?: request.createdAt)}", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (request.status == "pending") {
                                Pressable(onClick = { onAccess(request.id, "approve") }, selected = true, modifier = Modifier.weight(1f)) { Text("אישור", color = StudyBlue) }
                                Pressable(onClick = { onAccess(request.id, "reject") }, modifier = Modifier.weight(1f)) { Text("דחייה") }
                            } else if (request.status == "approved") {
                                Pressable(onClick = { onAccess(request.id, "revoke") }, modifier = Modifier.fillMaxWidth()) { Text("ביטול גישה") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FullAdminPublicCourses(state: UiState, onToggle: (String) -> Unit, onClear: () -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 14.dp, 16.dp, 64.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Pressable(onClick = onClear, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Close, null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                Text("סגירת כל הקורסים הציבוריים", color = MaterialTheme.colorScheme.error)
            }
        }
        items(state.courses, key = { it.id }) { course ->
            Pressable(onClick = { onToggle(course.id) }, modifier = Modifier.fillMaxWidth(), selected = course.id in state.publicCourseIds) {
                Icon(if (course.id in state.publicCourseIds) Icons.Rounded.Public else Icons.Rounded.Lock, null, tint = if (course.id in state.publicCourseIds) StudyBlue else MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Text(course.title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Switch(checked = course.id in state.publicCourseIds, onCheckedChange = null)
            }
        }
    }
}

@Composable
private fun FullAdminSettings(state: UiState, onSave: (Boolean, Boolean, Boolean, String) -> Unit) {
    val overview = state.adminOverview ?: return
    var requirePassword by remember(overview) { mutableStateOf(overview.requireCoursePassword) }
    var gemini by remember(overview) { mutableStateOf(overview.geminiServerKeysEnabled) }
    var explain by remember(overview) { mutableStateOf(overview.askPopoverShortExplainEnabled) }
    var model by remember(overview) { mutableStateOf(overview.pythiChatModel.ifBlank { "gemini-2.5-flash" }) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 14.dp, 16.dp, 64.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            GlassSurface(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    AdminSwitch("דרישת סיסמת קורסים", requirePassword) { requirePassword = it }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    AdminSwitch("מפתחות Gemini של השרת", gemini) { gemini = it }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    AdminSwitch("הסבר קצר מעל Pythi", explain) { explain = it }
                }
            }
        }
        item {
            Text("מודל הצ׳אט", style = MaterialTheme.typography.titleMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("gemini-2.5-flash" to "Gemini 2.5 Flash", "gemini-3-flash-preview" to "Gemini 3 Flash")) { item ->
                    Pressable(onClick = { model = item.first }, selected = model == item.first, shape = CircleShape) {
                        Text(item.second, color = if (model == item.first) StudyBlue else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
        item {
            Pressable(onClick = { onSave(requirePassword, gemini, explain, model) }, modifier = Modifier.fillMaxWidth(), selected = true) {
                Text("שמירת הגדרות המערכת", color = StudyBlue, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun FullAdminGemini(
    state: UiState,
    onCreate: (String, String) -> Unit,
    onEdit: (GeminiServerKey) -> Unit,
    onToggle: (GeminiServerKey) -> Unit,
    onCooldown: (String) -> Unit,
    onDelete: (GeminiServerKey) -> Unit,
    onExport: () -> Unit
) {
    var label by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 14.dp, 16.dp, 64.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            GlassSurface(Modifier.fillMaxWidth(), selected = true) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("הוספת מפתח שרת", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(label, { label = it }, Modifier.fillMaxWidth(), label = { Text("שם תצוגה") }, singleLine = true, shape = RoundedCornerShape(20.dp))
                    OutlinedTextField(apiKey, { apiKey = it }, Modifier.fillMaxWidth(), label = { Text("API Key") }, singleLine = true, shape = RoundedCornerShape(20.dp))
                    Pressable(
                        onClick = { onCreate(label, apiKey); label = ""; apiKey = "" },
                        modifier = Modifier.fillMaxWidth(), selected = true, enabled = apiKey.isNotBlank()
                    ) { Text("הוספת מפתח", color = StudyBlue) }
                }
            }
        }
        item {
            Pressable(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.CloudDownload, null, tint = StudyBlue)
                Spacer(Modifier.width(8.dp))
                Text("ייצוא JSON מלא")
            }
        }
        if (state.adminGeminiKeys.isEmpty() && !state.adminLoading) item { AdminEmpty("אין מפתחות Gemini שמורים") }
        items(state.adminGeminiKeys, key = { it.id }) { key ->
            GlassSurface(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(key.label.ifBlank { "ללא שם" }, fontWeight = FontWeight.Bold)
                            Text(key.maskedKey, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Box(
                            Modifier.clip(CircleShape).background(
                                when (key.state) {
                                    "active" -> Color(0xFF31C76A).copy(alpha = .16f)
                                    "cooldown" -> Color(0xFFFFA000).copy(alpha = .17f)
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }
                            ).padding(horizontal = 10.dp, vertical = 6.dp)
                        ) { Text(key.state.ifBlank { "unknown" }, style = MaterialTheme.typography.labelMedium) }
                    }
                    if (key.lastFailureMessage.isNotBlank()) {
                        Text("כשל אחרון: ${key.lastFailureMessage}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                        Text("${key.lastFailureRoute} · ${key.lastFailureModel} · ${key.lastFailureStatus ?: "-"}", style = MaterialTheme.typography.labelMedium)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        RoundActionButton(Icons.Rounded.Edit, "עריכה", { onEdit(key) }, size = 44.dp)
                        RoundActionButton(if (key.enabled) Icons.Rounded.Lock else Icons.Rounded.LockOpen, if (key.enabled) "השבתה" else "הפעלה", { onToggle(key) }, size = 44.dp, active = key.enabled)
                        if (key.state == "cooldown") RoundActionButton(Icons.Rounded.Refresh, "ניקוי cooldown", { onCooldown(key.id) }, size = 44.dp)
                        Spacer(Modifier.weight(1f))
                        RoundActionButton(Icons.Rounded.DeleteForever, "מחיקה", { onDelete(key) }, size = 44.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun FullAdminPasswords(passwords: List<SystemPasswordStatus>, onEdit: (SystemPasswordStatus) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 14.dp, 16.dp, 64.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        items(passwords, key = { it.type }) { item ->
            GlassSurface(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Key, null, tint = StudyBlue)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.label, fontWeight = FontWeight.Bold)
                        Text(
                            if (item.configured) "מוגדרת${if (item.hashed) " ומוצפנת bcrypt" else ""}" else "לא מוגדרת",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    RoundActionButton(Icons.Rounded.Edit, "שינוי סיסמה", { onEdit(item) }, size = 44.dp)
                }
            }
        }
    }
}

@Composable
private fun AdminSwitch(title: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun AdminMessageDialog(targetLabel: String, onDismiss: () -> Unit, onSend: (String, String, Boolean, Boolean) -> Unit) {
    var subject by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var viaEmail by remember { mutableStateOf(true) }
    var inApp by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("שליחת הודעה אל $targetLabel") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(subject, { subject = it }, Modifier.fillMaxWidth(), label = { Text("נושא") }, singleLine = true)
                OutlinedTextField(content, { content = it }, Modifier.fillMaxWidth().height(150.dp), label = { Text("תוכן") })
                AdminSwitch("אימייל", viaEmail) { viaEmail = it }
                AdminSwitch("התראה בתוך המערכת", inApp) { inApp = it }
            }
        },
        confirmButton = { TextButton(onClick = { onSend(subject, content, viaEmail, inApp) }, enabled = content.isNotBlank() && (viaEmail || inApp)) { Text("שליחה") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } }
    )
}

@Composable
private fun GeminiKeyDialog(key: GeminiServerKey, onDismiss: () -> Unit, onSave: (String, String, Boolean) -> Unit) {
    var label by remember(key.id) { mutableStateOf(key.label) }
    var apiKey by remember(key.id) { mutableStateOf("") }
    var enabled by remember(key.id) { mutableStateOf(key.enabled) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("עריכת מפתח Gemini") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(label, { label = it }, Modifier.fillMaxWidth(), label = { Text("שם תצוגה") }, singleLine = true)
                OutlinedTextField(apiKey, { apiKey = it }, Modifier.fillMaxWidth(), label = { Text("מפתח חדש — לא חובה") }, singleLine = true)
                AdminSwitch("פעיל", enabled) { enabled = it }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(label, apiKey, enabled) }) { Text("שמירה") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } }
    )
}

@Composable
private fun PasswordAdminDialog(item: SystemPasswordStatus, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var password by remember(item.type) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("שינוי ${item.label}") },
        text = {
            OutlinedTextField(
                password,
                { password = it },
                Modifier.fillMaxWidth(),
                label = { Text("סיסמה חדשה") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )
        },
        confirmButton = { TextButton(onClick = { onSave(password) }, enabled = password.isNotBlank()) { Text("עדכון") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } }
    )
}

@Composable
private fun ConfirmAdminDialog(title: String, message: String, confirmLabel: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } }
    )
}

@Composable
private fun AdminEmpty(message: String) {
    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatAdminDate(value: Long?): String = value?.takeIf { it > 0L }?.let {
    runCatching { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it)) }.getOrNull()
} ?: "-"

private fun adminMetricLabel(key: String): String = when (key) {
    "memories" -> "זיכרונות"
    "visitHistory" -> "ביקורים"
    "visitedSections" -> "שיעורים"
    "bookmarks" -> "סימניות"
    "searchHistory" -> "חיפושים"
    "exercises" -> "תרגילים"
    "savedItems" -> "שמורים"
    "completedSections" -> "הושלמו"
    "quizProgress" -> "בחנים"
    else -> key
}
