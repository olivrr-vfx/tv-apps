package dev.purpletv.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.purpletv.chat.ChatMessage
import dev.purpletv.chat.ChatPart

@Composable
fun ChatPanel(messages: List<ChatMessage>, fontSize: Int, overlay: Boolean, modifier: Modifier = Modifier) {
    // Newest message at the bottom without having to scroll.
    LazyColumn(modifier = modifier.fillMaxSize(), reverseLayout = true) {
        items(messages.asReversed(), key = { it.id }) { msg ->
            ChatLine(msg, fontSize, overlay)
        }
    }
}

@Composable
private fun ChatLine(msg: ChatMessage, fontSize: Int, overlay: Boolean) {
    val (text, inline) = remember(msg) {
        val inline = mutableMapOf<String, InlineTextContent>()
        val text = buildAnnotatedString {
            withStyle(SpanStyle(color = Color(msg.color), fontWeight = FontWeight.Bold)) { append(msg.userName) }
            append(if (msg.isAction) " " else ": ")
            if (msg.deleted) {
                withStyle(SpanStyle(color = Colors.TextDim, fontStyle = FontStyle.Italic)) { append("<Nachricht gelöscht>") }
                return@buildAnnotatedString
            }
            val style = if (msg.isAction) SpanStyle(color = Color(msg.color)) else SpanStyle()
            withStyle(style) {
                msg.parts.forEachIndexed { i, part ->
                    when (part) {
                        is ChatPart.Text -> append(part.text)
                        is ChatPart.EmotePart -> {
                            val id = "e$i"
                            appendInlineContent(id, part.emote.name)
                            val height = 1.7f
                            inline[id] = InlineTextContent(
                                Placeholder((height * part.emote.aspect.coerceIn(0.5f, 4f)).em, height.em, PlaceholderVerticalAlign.Center)
                            ) {
                                AsyncImage(model = part.emote.url, contentDescription = part.emote.name, modifier = Modifier.fillMaxSize())
                            }
                        }
                    }
                }
            }
        }
        text to inline
    }
    Text(
        text,
        inlineContent = inline,
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
        color = Colors.Text,
        style = if (overlay) {
            TextStyle(fontSize = fontSize.sp, shadow = Shadow(Color.Black, blurRadius = 4f))
        } else {
            TextStyle(fontSize = fontSize.sp)
        },
    )
}
