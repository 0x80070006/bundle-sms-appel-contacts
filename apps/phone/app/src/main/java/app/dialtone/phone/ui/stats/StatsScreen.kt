package app.dialtone.phone.ui.stats

import android.Manifest
import android.content.Context
import android.provider.CallLog.Calls
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.dialtone.phone.telecom.CallerLookup
import app.dialtone.phone.util.formatDuration
import app.dialtone.phone.util.formatNumber
import app.dialtone.phone.util.hasPermission
import app.dialtone.phone.util.numberKey
import app.dialtone.phone.util.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Bundle: call statistics for the last 30 days, computed on the phone from the call log. */
data class CallStats(
    val incoming: Int = 0,
    val outgoing: Int = 0,
    val missed: Int = 0,
    val totalSec: Long = 0,
    val weekSec: Long = 0,
    /** Seconds per day for the last 7 days, oldest first. */
    val perDay: List<Long> = List(7) { 0L },
    val top: List<Pair<String, Long>> = emptyList(),
) {
    val answered get() = incoming + outgoing
    val averageSec get() = if (answered == 0) 0 else totalSec / answered
}

internal data class LogRow(val number: String, val type: Int, val date: Long, val duration: Long)

internal fun computeStats(rows: List<LogRow>, now: Long, nameOf: (String) -> String): CallStats {
    val day = 86_400_000L
    val since = now - 30 * day
    val recent = rows.filter { it.date >= since }
    val perDay = LongArray(7)
    recent.forEach { r ->
        val ago = ((now - r.date) / day).toInt()
        if (ago in 0..6) perDay[6 - ago] += r.duration
    }
    val top = recent.filter { it.duration > 0 }.groupBy { numberKey(it.number) }
        .map { (_, calls) -> nameOf(calls.first().number) to calls.sumOf { it.duration } }
        .sortedByDescending { it.second }.take(5)
    return CallStats(
        incoming = recent.count { it.type == Calls.INCOMING_TYPE && it.duration > 0 },
        outgoing = recent.count { it.type == Calls.OUTGOING_TYPE },
        missed = recent.count { it.type == Calls.MISSED_TYPE },
        totalSec = recent.sumOf { it.duration },
        weekSec = perDay.sum(),
        perDay = perDay.toList(),
        top = top,
    )
}

private suspend fun loadStats(context: Context): CallStats = withContext(Dispatchers.IO) {
    if (!context.hasPermission(Manifest.permission.READ_CALL_LOG)) return@withContext CallStats()
    val since = System.currentTimeMillis() - 30 * 86_400_000L
    val rows = mutableListOf<LogRow>()
    runCatching {
        context.contentResolver.query(
            Calls.CONTENT_URI, arrayOf(Calls.NUMBER, Calls.TYPE, Calls.DATE, Calls.DURATION),
            "${Calls.DATE} >= ?", arrayOf(since.toString()), null,
        )?.use { c -> while (c.moveToNext()) rows += LogRow(c.getString(0).orEmpty(), c.getInt(1), c.getLong(2), c.getLong(3)) }
    }
    computeStats(rows, System.currentTimeMillis()) { n -> CallerLookup.lookup(context, n).name ?: formatNumber(n) }
}

@Composable
fun StatsScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    var stats by remember { mutableStateOf<CallStats?>(null) }
    LaunchedEffect(Unit) { stats = loadStats(context) }

    Surface(color = scheme.background, contentColor = scheme.onBackground, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Back")) }
                Text(tr("Call statistics"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 8.dp))
            }
            val s = stats ?: return@Column
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Tile(tr("This week"), formatDuration(s.weekSec), Modifier.weight(1f))
                        Tile(tr("Average call"), formatDuration(s.averageSec), Modifier.weight(1f))
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Tile(tr("Incoming"), s.incoming.toString(), Modifier.weight(1f))
                        Tile(tr("Outgoing"), s.outgoing.toString(), Modifier.weight(1f))
                        Tile(tr("Missed"), s.missed.toString(), Modifier.weight(1f))
                    }
                }
                item {
                    Surface(shape = RoundedCornerShape(24.dp), color = scheme.surfaceBright) {
                        Column(Modifier.padding(16.dp)) {
                            Text(tr("Last 7 days"), style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(12.dp))
                            val max = (s.perDay.maxOrNull() ?: 0L).coerceAtLeast(1L)
                            Row(Modifier.fillMaxWidth().height(96.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                                s.perDay.forEach { sec ->
                                    Box(Modifier.weight(1f).fillMaxHeight((sec.toFloat() / max).coerceAtLeast(0.03f))
                                        .clip(RoundedCornerShape(8.dp)).background(scheme.primary))
                                }
                            }
                        }
                    }
                }
                item { Text(tr("Most called (30 days)"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp)) }
                if (s.top.isEmpty()) item { Text(tr("No calls yet"), color = scheme.onSurfaceVariant) }
                items(s.top) { (name, sec) ->
                    Surface(shape = RoundedCornerShape(16.dp), color = scheme.surfaceBright, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp)) {
                            Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            Text(formatDuration(sec), color = scheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Tile(label: String, value: String, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceBright, modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Suppress("unused") private val keepWidth = Modifier.width(0.dp)
