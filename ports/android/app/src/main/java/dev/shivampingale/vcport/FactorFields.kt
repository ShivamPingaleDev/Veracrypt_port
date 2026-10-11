package dev.shivampingale.vcport

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType

/**
 * One password field and one PIM field. Open, Create, Tools, and the nested
 * password all use this. The secret still lives in the session that called it.
 */
@Composable
fun PasswordAndPim(
    password: String,
    onPassword: (String) -> Unit,
    passwordLabel: String,
    passwordTag: String,
    pim: String,
    onPim: (String) -> Unit,
    pimLabel: String,
    pimTag: String,
    enabled: Boolean,
    between: @Composable () -> Unit = {},
) {
    SecretField(
        password,
        onPassword,
        passwordLabel,
        modifier = Modifier.testTag(passwordTag),
        enabled = enabled
    )
    between()
    PimField(pim, onPim, pimLabel, pimTag, enabled)
}

@Composable
fun PimField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    tag: String,
    enabled: Boolean,
) {
    OutlinedTextField(
        value,
        onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth().testTag(tag),
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

@Composable
fun KeyfileList(
    labels: List<String>,
    onRemove: (Int) -> Unit,
    onAdd: () -> Unit,
    enabled: Boolean,
    addLabel: String = "Add keyfiles",
    addTag: String? = null,
    emptyText: String? = null,
) {
    if (labels.isEmpty() && emptyText != null) {
        Text(emptyText, style = MaterialTheme.typography.bodySmall)
    }
    labels.forEachIndexed { index, label ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            TextButton(onClick = { onRemove(index) }) { Text("Remove") }
        }
    }
    val addModifier = if (addTag == null) {
        Modifier.fillMaxWidth()
    } else {
        Modifier.fillMaxWidth().testTag(addTag)
    }
    OutlinedButton(
        onClick = onAdd,
        enabled = enabled,
        modifier = addModifier
    ) { Text(addLabel) }
}
