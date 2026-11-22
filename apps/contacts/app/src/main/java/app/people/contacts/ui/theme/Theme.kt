package app.people.contacts.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import bundle.core.design.BundleTheme

/**
 * The app's theme. Bundle: the theme now lives in core/design (BundleTheme), shared with
 * Phone and Messages; this name is kept so the rest of the app is unchanged from upstream.
 */
@Composable
fun ContactsTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) =
    BundleTheme(darkTheme, content)
