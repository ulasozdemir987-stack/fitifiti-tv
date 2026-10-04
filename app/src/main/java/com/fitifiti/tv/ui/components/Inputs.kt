package com.fitifiti.tv.ui.components

import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.*
import com.fitifiti.tv.ui.rememberFocus
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.fitifiti.tv.ui.theme.C

/**
 * TV metin kutusu. Normalde DÜĞME gibi davranır (yön tuşlarıyla üzerinden geçilir, klavye açılmaz); OK'e basınca
 * yazma moduna geçer ve klavye açılır, klavyede Tamam/İleri ya da geri → düğmeye döner.
 * Neden: bazı cihazlarda (Nova, Android 14) `showKeyboardOnFocus = false`'a rağmen odak gelince klavye açılıyor ve
 * yön tuşlarını kapıyordu — Ayarlar'da TMDB kutusunda aşağı inilemiyordu (gerçek kutuda görüldü).
 */
@Composable
fun TvTextField(
    value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier,
    placeholder: String = "", password: Boolean = false, keyboard: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next, onDone: () -> Unit = {}, icon: ImageVector? = null, startEditing: Int = 0,
) {
    var editing by remember { mutableStateOf(false) }
    // Dışarıdan "yazmaya başla" (ör. Ara simgesine basınca): kutuya odak + klavye
    LaunchedEffect(startEditing) { if (startEditing > 0) editing = true }
    var fieldFocused by remember { mutableStateOf(false) }
    val boxFr = remember { FocusRequester() }
    val editFr = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardCtl = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focused = fieldFocused || editing
    // Telefon kumandası: odaktaki kutu telefona bildirilir, telefonda yazılan metin buraya gelir
    val latest by rememberUpdatedState(onValueChange)
    val me = remember { Any() }
    val info = com.fitifiti.tv.data.remote.RemoteInput(label.ifEmpty { placeholder }, value, password)
    LaunchedEffect(focused, info) { if (focused) { com.fitifiti.tv.data.remote.RemoteBus.input.value = info; RemoteOwner.owner = me } }
    LaunchedEffect(focused) {
        if (!focused) { if (RemoteOwner.owner === me) { com.fitifiti.tv.data.remote.RemoteBus.input.value = null; RemoteOwner.owner = null }; return@LaunchedEffect }
        com.fitifiti.tv.data.remote.RemoteBus.text.collect { latest(it) }
    }
    DisposableEffect(Unit) { onDispose { if (RemoteOwner.owner === me) { com.fitifiti.tv.data.remote.RemoteBus.input.value = null; RemoteOwner.owner = null } } }

    fun finish(next: Boolean) {
        editing = false
        keyboardCtl?.hide()
        if (next) focusManager.moveFocus(FocusDirection.Down) else runCatching { boxFr.requestFocus() }
    }
    // Yazı alanı ilk eklendiğinde onFocusChanged "odakta değil" diye de çağrılır: odağı bir kez almadan bunu
    // "odak kaybedildi" sayıp yazmayı bitirmek, Ara simgesinden gelen isteği anında iptal ediyordu
    var editHadFocus by remember { mutableStateOf(false) }
    LaunchedEffect(editing) {
        if (!editing) return@LaunchedEffect
        editHadFocus = false
        repeat(10) { kotlinx.coroutines.delay(30); runCatching { editFr.requestFocus() }; if (editHadFocus) { keyboardCtl?.show(); return@LaunchedEffect } }
        editing = false
    }

    val shape = RoundedCornerShape(12.dp)
    @Composable fun Frame(content: @Composable RowScope.() -> Unit) = Row(
        Modifier.fillMaxWidth().clip(shape).background(if (focused) C.fill3 else C.fill1)
            .border(2.dp, if (focused) Color.White else C.line, shape).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(20.dp), tint = C.muted); Spacer(Modifier.width(10.dp)) }
        content()
    }

    Column(modifier) {
        if (label.isNotEmpty()) { Text(label, style = MaterialTheme.typography.labelMedium, color = C.muted); Spacer(Modifier.height(6.dp)) }
        if (!editing) {
            Surface(
                onClick = { editing = true },
                modifier = Modifier.fillMaxWidth().focusRequester(boxFr).rememberFocus().onFocusChanged { fieldFocused = it.isFocused },
                shape = ClickableSurfaceDefaults.shape(shape),
                colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            ) {
                Frame {
                    val shown = if (password) "•".repeat(value.length) else value
                    Text(shown.ifEmpty { placeholder }, color = if (shown.isEmpty()) C.faint else Color.White, fontSize = 18.sp, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
            }
        } else {
            BasicTextField(
                value = value, onValueChange = onValueChange, singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
                cursorBrush = SolidColor(C.primary),
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard, imeAction = imeAction, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(
                    onDone = { finish(false); onDone() }, onGo = { finish(false); onDone() }, onSearch = { finish(false); onDone() },
                    onNext = { finish(true) },
                ),
                modifier = Modifier.fillMaxWidth().focusRequester(editFr)
                    .onFocusChanged { if (it.isFocused) editHadFocus = true else if (editHadFocus && editing) editing = false }
                    // yazarken ↑/↓ yazmayı bitirip odağı taşır
                    .onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (e.key) {
                            Key.DirectionDown -> { finish(true); true }
                            Key.DirectionUp -> { editing = false; keyboardCtl?.hide(); focusManager.moveFocus(FocusDirection.Up); true }
                            else -> false
                        }
                    },
                decorationBox = { inner ->
                    Frame {
                        Box(Modifier.weight(1f)) {
                            if (value.isEmpty() && placeholder.isNotEmpty()) Text(placeholder, color = C.faint, fontSize = 18.sp)
                            inner()
                        }
                    }
                },
            )
        }
    }
}

private object RemoteOwner { var owner: Any? = null }

/** Ayarlar/menü satırı: solda başlık + açıklama, sağda değer */
@Composable
fun SettingRow(title: String, value: String? = null, hint: String? = null, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Surface(
        onClick = onClick, modifier = modifier.fillMaxWidth().rememberFocus(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = C.fill1, focusedContainerColor = C.fill3, contentColor = Color.White, focusedContentColor = Color.White),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(androidx.compose.foundation.BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(14.dp))),
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, Modifier.size(22.dp), tint = Color(0xCCFFFFFF)); Spacer(Modifier.width(14.dp)) }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = C.muted)
            }
            if (value != null) Text(value, style = MaterialTheme.typography.bodyLarge, color = Color(0xCCFFFFFF))
        }
    }
}
