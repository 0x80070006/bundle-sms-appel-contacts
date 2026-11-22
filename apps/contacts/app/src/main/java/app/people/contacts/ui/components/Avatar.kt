package app.people.contacts.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.Dp

// Bundle: avatars now live in core/design, shared with Phone and Messages, so one person looks
// the same in every app. These names are kept so the rest of the app is unchanged from upstream.

/** A contact's avatar colours: container and content, picked from [seed]. */
@Composable
fun avatarColors(seed: Long): Pair<Color, Color> = bundle.core.design.avatarColors(seed)

/** Loads an image from a content URI off the main thread; null while loading or if it fails. */
@Composable
fun rememberPhoto(uri: String?): ImageBitmap? = bundle.core.design.rememberPhoto(uri)

/** A contact's avatar: the photo, or the first initial on a colour picked from [seed]. */
@Composable
fun Avatar(
    name: String?,
    photoUri: String?,
    seed: Long,
    size: Dp,
    modifier: Modifier = Modifier,
    expressive: Boolean = false,
) = bundle.core.design.Avatar(name, photoUri, seed, size, modifier, expressive)
