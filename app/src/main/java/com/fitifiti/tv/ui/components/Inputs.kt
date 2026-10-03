package com.fitifiti.tv.ui.components

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

/** TV metin kutusu: kumandayla odaklanır, OK tuşu klavyeyi açar (odaklanınca kendiliğinden açılmaz). Odakta beyaz çerçeve. */
@Composable
fun TvTextField(
    value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier,
    placeholder: String = "", password: Boolean = false, keyboard: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next, onDone: () -> Unit = {}, icon: ImageVector? = null,
) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    Column(modifier) {
        if (label.isNotEmpty()) { Text(label, style = MaterialTheme.typography.labelMedium, color = C.muted); Spacer(Modifier.height(6.dp)) }
        BasicTextField(
            value = value, onValueChange = onValueChange, singleLine = true, interactionSource = src,
            textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
            cursorBrush = SolidColor(C.primary),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard, imeAction = imeAction, autoCorrectEnabled = false, showKeyboardOnFocus = false),
            keyboardActions = KeyboardActions(onDone = { onDone() }, onGo = { onDone() }, onSearch = { onDone() }),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (focused) C.fill3 else C.fill1)
                        .border(2.dp, if (focused) Color.White else C.line, RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (icon != null) { Icon(icon, null, Modifier.size(20.dp), tint = C.muted); Spacer(Modifier.width(10.dp)) }
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty() && placeholder.isNotEmpty()) Text(placeholder, color = C.faint, fontSize = 18.sp)
                        inner()
                    }
                }
            },
        )
    }
}

/** Ayarlar/menü satırı: solda başlık + açıklama, sağda değer */
@Composable
fun SettingRow(title: String, value: String? = null, hint: String? = null, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Surface(
        onClick = onClick, modifier = modifier.fillMaxWidth(),
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
