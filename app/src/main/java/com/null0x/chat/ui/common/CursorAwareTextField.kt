@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.null0x.chat.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@Composable
internal fun CursorAwareOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = Int.MAX_VALUE,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    shape: Shape = RoundedCornerShape(14.dp),
    colors: TextFieldColors = primalisBareOutlinedTextFieldColors(),
    textStyle: TextStyle = TextStyle.Default
) {
    var fieldValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(value))
    }

    LaunchedEffect(value) {
        if (fieldValue.text != value) {
            fieldValue = TextFieldValue(value, selection = TextRange(value.length))
        }
    }

    CursorAwareOutlinedTextField(
        value = fieldValue,
        onValueChange = {
            fieldValue = it
            onValueChange(it.text)
        },
        modifier = modifier,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        supportingText = supportingText,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        enabled = enabled,
        readOnly = readOnly,
        isError = isError,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        shape = shape,
        colors = colors,
        textStyle = textStyle
    )
}

@Composable
internal fun CursorAwareOutlinedTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = Int.MAX_VALUE,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    shape: Shape = RoundedCornerShape(14.dp),
    colors: TextFieldColors = primalisBareOutlinedTextFieldColors(),
    textStyle: TextStyle = TextStyle.Default
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth(),
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        supportingText = supportingText,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        enabled = enabled,
        readOnly = readOnly,
        isError = isError,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        shape = shape,
        colors = colors,
        textStyle = textStyle
    )
}

@Composable
internal fun primalisBareOutlinedTextFieldColors(
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    placeholderColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f),
    supportingTextColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    cursorColor: Color = textColor,
    errorColor: Color = MaterialTheme.colorScheme.error
): TextFieldColors {
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = textColor,
        unfocusedTextColor = textColor,
        disabledTextColor = textColor.copy(alpha = 0.48f),
        errorTextColor = textColor,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        errorContainerColor = Color.Transparent,
        cursorColor = cursorColor,
        errorCursorColor = errorColor,
        focusedBorderColor = Color.Transparent,
        unfocusedBorderColor = Color.Transparent,
        disabledBorderColor = Color.Transparent,
        errorBorderColor = Color.Transparent,
        focusedPlaceholderColor = placeholderColor,
        unfocusedPlaceholderColor = placeholderColor,
        disabledPlaceholderColor = placeholderColor.copy(alpha = 0.52f),
        errorPlaceholderColor = placeholderColor,
        focusedLabelColor = placeholderColor,
        unfocusedLabelColor = placeholderColor,
        disabledLabelColor = placeholderColor.copy(alpha = 0.52f),
        errorLabelColor = errorColor,
        focusedSupportingTextColor = supportingTextColor,
        unfocusedSupportingTextColor = supportingTextColor,
        disabledSupportingTextColor = supportingTextColor.copy(alpha = 0.52f),
        errorSupportingTextColor = errorColor
    )
}
