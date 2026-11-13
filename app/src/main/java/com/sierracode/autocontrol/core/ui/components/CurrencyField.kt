package com.sierracode.autocontrol.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sierracode.autocontrol.core.ui.theme.AutoControlTheme

/**
 * Campo de moneda.
 * - Normaliza coma → punto.
 * - Trunca a 2 decimales.
 * - Muestra el código de divisa como sufijo.
 * - No formatea con separador de miles mientras se escribe (rompe el cursor);
 *   la formatación queda al perder el foco para no interrumpir la captura.
 */
@Composable
fun CurrencyField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    currencyCode: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    errorMessage: String? = null,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {}
) {
    OutlinedTextField(
        value = value,
        onValueChange = { raw ->
            val normalized = raw.replace(',', '.')
            val filtered = buildString {
                var dotSeen = false
                var decimals = 0
                for (c in normalized) {
                    when {
                        c.isDigit() -> {
                            if (dotSeen) {
                                if (decimals < 2) { append(c); decimals++ }
                            } else append(c)
                        }
                        c == '.' && !dotSeen -> { append(c); dotSeen = true }
                    }
                }
            }
            onValueChange(filtered)
        },
        label = { Text(label) },
        suffix = { Text(currencyCode) },
        isError = isError,
        supportingText = if (isError && errorMessage != null) {
            { Text(errorMessage) }
        } else null,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = imeAction
        ),
        keyboardActions = KeyboardActions(
            onNext = { onImeAction() },
            onDone = { onImeAction() }
        ),
        modifier = modifier
    )
}

@Preview(uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO)
@Preview(uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CurrencyFieldPreview() {
    AutoControlTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CurrencyField(
                value = "1250.50",
                onValueChange = {},
                label = "Monto total",
                currencyCode = "MXN"
            )
            CurrencyField(
                value = "",
                onValueChange = {},
                label = "Monto total",
                currencyCode = "USD",
                isError = true,
                errorMessage = "Ingresa un monto válido"
            )
        }
    }
}