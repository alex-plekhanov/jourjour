package app.jourjour.core.text

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Resolves dictionary names so that they can exist in more than one language.
 *
 * Preloaded rows carry a `nameKey` — the name of a string resource (`currency_usd`,
 * `category_dining`). Their name comes from resources and therefore follows the device
 * language. Rows a person created have no `nameKey`, so their own `name` is shown; that
 * same `name` is the fallback when a resource has gone missing.
 */
fun resolveName(context: Context, name: String, nameKey: String?): String {
    if (nameKey.isNullOrEmpty()) return name
    val id = context.resources.getIdentifier(nameKey, "string", context.packageName)
    return if (id != 0) context.getString(id) else name
}

@Composable
fun localizedName(name: String, nameKey: String?): String {
    val context = LocalContext.current
    return remember(context, name, nameKey) { resolveName(context, name, nameKey) }
}
