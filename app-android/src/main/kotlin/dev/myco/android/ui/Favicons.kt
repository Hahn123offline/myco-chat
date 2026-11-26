package dev.myco.android.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/** 站点图标：直接抓 host/favicon.ico，内存缓存；失败退化为首字母圆标（不经过第三方图标服务）。 */
object SiteIcons {
    private val cache = android.util.LruCache<String, Bitmap>(64)

    fun hostOf(url: String): String? = runCatching { URI(url).host }.getOrNull()

    suspend fun load(host: String): Bitmap? {
        cache.get(host)?.let { return it }
        val bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val conn = (URL("https://$host/favicon.ico").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 2500
                    readTimeout = 2500
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "myco/0.1 favicon")
                }
                conn.inputStream.use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
        }
        if (bitmap != null) cache.put(host, bitmap)
        return bitmap
    }
}

@Composable
fun SiteIcon(site: String?, url: String, size: Dp = 18.dp) {
    val host = SiteIcons.hostOf(url)
    val label = (site?.trim()?.takeIf { it.isNotBlank() } ?: host ?: "?").first().uppercase()
    val bitmap by produceState<Bitmap?>(initialValue = host?.let { SiteIcons.let { _ -> null } }, key1 = host) {
        value = null
        if (host != null) value = SiteIcons.load(host)
    }
    Box(
        Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = site ?: host,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size).clip(CircleShape),
            )
        } else {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
