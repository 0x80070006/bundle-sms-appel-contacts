package bundle.core.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Corner radii for M3 Expressive grouped ("segmented") lists: big outer corners, tight inner
 * ones. Row [index] of [count] in one card.
 */
fun segmentShape(index: Int, count: Int, large: Dp = 24.dp, small: Dp = 6.dp): Shape = when {
    count == 1 -> RoundedCornerShape(large)
    index == 0 -> RoundedCornerShape(topStart = large, topEnd = large, bottomStart = small, bottomEnd = small)
    index == count - 1 -> RoundedCornerShape(topStart = small, topEnd = small, bottomStart = large, bottomEnd = large)
    else -> RoundedCornerShape(small)
}
