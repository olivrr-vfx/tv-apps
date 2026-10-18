package dev.purpletv.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction

/** Single-line text field in the design system look. Opens the TV keyboard on OK. */
@Composable
fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    label: String? = null,
    icon: ImageVector? = null,
    imeAction: ImeAction = ImeAction.Done,
    onSubmit: () -> Unit = {},
) {
    val accent = LocalAccent.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        placeholder = { Text(placeholder, style = Type.body) },
        label = label?.let { { Text(it) } },
        leadingIcon = icon?.let { { Icon(it, null, tint = Palette.textDim) } },
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }, onSearch = { onSubmit() }, onGo = { onSubmit() }),
        shape = RoundedCornerShape(Tokens.Radius.pill),
        textStyle = Type.body,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Palette.focus,
            unfocusedBorderColor = Palette.surfaceHigh,
            focusedContainerColor = Palette.surfaceRaised,
            unfocusedContainerColor = Palette.surface,
            focusedTextColor = Palette.text,
            unfocusedTextColor = Palette.text,
            cursorColor = accent.light,
            focusedLabelColor = accent.light,
            unfocusedLabelColor = Palette.textDim,
            focusedPlaceholderColor = Palette.textFaint,
            unfocusedPlaceholderColor = Palette.textFaint,
        ),
        modifier = modifier,
    )
}
