package app.dialtone.phone.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import bundle.core.design.BundleTheme

// Bundle: the theme now lives in core/design (BundleTheme), shared with Contacts and Messages.
// These names are kept so the rest of the app is unchanged from upstream.

/** The app's theme: the bundle's theme. */
@Composable
fun ContactsMatchTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) =
    BundleTheme(darkTheme, content)

/** Call-green for Call / Answer buttons, shared by the bundle. */
typealias CallColors = bundle.core.design.CallColors
