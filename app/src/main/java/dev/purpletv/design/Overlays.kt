package dev.purpletv.design

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/** Modal card over a dimmed screen. Back closes it. */
@Composable
fun TvDialog(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 560.dp,
    body: String? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    BackHandler(onBack = onDismiss)
    Box(Modifier.fillMaxSize().background(Palette.scrim), contentAlignment = Alignment.Center) {
        Column(
            modifier
                .width(width)
                .clip(RoundedCornerShape(Tokens.Radius.xl))
                .background(Palette.surfaceRaised)
                .padding(Tokens.Space.xxl),
        ) {
            Text(title, color = Palette.text, style = Type.title)
            if (body != null) {
                Spacer(Modifier.height(Tokens.Space.sm))
                Text(body, color = Palette.textDim, style = Type.body)
            }
            Spacer(Modifier.height(Tokens.Space.xl))
            content()
        }
    }
}

@Composable
fun DialogButtons(content: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.md)) { content() }
}

/** Small notice in the bottom right corner (updates, "copied", ...). */
@Composable
fun CornerToast(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier.fillMaxSize().padding(Tokens.Space.xl), contentAlignment = Alignment.BottomEnd) {
        Column(
            Modifier
                .clip(RoundedCornerShape(Tokens.Radius.md))
                .background(LocalAccent.current.primary.copy(alpha = 0.95f))
                .padding(horizontal = Tokens.Space.lg, vertical = 10.dp),
            content = content,
        )
    }
}

/** White-framed QR code. */
@Composable
fun QrCode(text: String, size: Dp, modifier: Modifier = Modifier) {
    val bitmap = remember(text) { qrBitmap(text, 512) }
    Image(
        bitmap,
        contentDescription = "QR-Code",
        modifier = modifier.size(size).clip(RoundedCornerShape(Tokens.Radius.md)).background(Color.White).padding(10.dp),
    )
}

fun qrBitmap(text: String, size: Int): ImageBitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
    val pixels = IntArray(size * size) { i -> if (matrix.get(i % size, i / size)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
}
