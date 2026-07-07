package com.buddy.app.core.designsystem.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.buddy.app.core.designsystem.BuddyColor
import com.buddy.app.core.designsystem.BuddyType
import com.buddy.app.core.designsystem.Radius

/**
 * Input estándar — superficie blanca, borde hairline → brand al enfocar,
 * placeholder en inkFaint. Radius sm (14) como los campos de iOS.
 */
@Composable
fun BuddyTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = placeholder?.let { { Text(it, style = BuddyType.Body, color = BuddyColor.InkFaint) } },
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        textStyle = BuddyType.Body,
        shape = RoundedCornerShape(Radius.sm),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = BuddyColor.Surface,
            unfocusedContainerColor = BuddyColor.Surface,
            focusedBorderColor = BuddyColor.Brand,
            unfocusedBorderColor = BuddyColor.Border,
            cursorColor = BuddyColor.Brand,
            focusedTextColor = BuddyColor.Ink,
            unfocusedTextColor = BuddyColor.Ink,
        ),
    )
}
