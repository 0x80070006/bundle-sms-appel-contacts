package app.people.contacts.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.union
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The bottom bar, as in Dialtone: Material 3's 64dp short navigation bar in its default
 * colours. It handles the gesture-bar inset itself, and rides above the keyboard.
 */
@Composable
fun BottomBar(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    // Bundle: the link pastille (Contacts · Phone · Messages) above the bar.
    androidx.compose.foundation.layout.Column(modifier) {
        bundle.core.appearance.LinkChip(bundle.core.appearance.BundleApp.Contacts)
        ShortNavigationBar(
            windowInsets = ShortNavigationBarDefaults.windowInsets.union(WindowInsets.ime),
            content = content,
        )
    }
}
