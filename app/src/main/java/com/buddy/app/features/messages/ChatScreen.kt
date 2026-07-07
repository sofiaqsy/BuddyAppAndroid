package com.buddy.app.features.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.buddy.app.core.designsystem.BuddyColor
import com.buddy.app.core.designsystem.BuddyType
import com.buddy.app.core.designsystem.Radius
import com.buddy.app.core.designsystem.Spacing
import com.buddy.app.core.designsystem.components.BuddyLoading
import com.buddy.app.core.designsystem.components.BuddyTextField
import com.buddy.app.features.matching.data.ApiMessage

/**
 * Chat 1:1 — mismo modelo de interacción que BuddyChatView (iOS):
 * burbujas propias en brand a la derecha, del otro en surface a la izquierda.
 * Adaptaciones Android: imePadding para el teclado, back en el header.
 */
@Composable
fun ChatScreen(
    matchId: String,
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    LaunchedEffect(matchId) { viewModel.open(matchId) }
    val state by viewModel.state.collectAsState()
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.size - 1)
    }

    Column(modifier.fillMaxSize().background(BuddyColor.Canvas).imePadding()) {
        // Header
        Row(
            Modifier.fillMaxWidth().background(BuddyColor.TabBarBg).padding(horizontal = Spacing.xs, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = BuddyColor.Ink)
            }
            Text(title, style = BuddyType.Headline, color = BuddyColor.Ink)
        }

        if (state.isLoading) {
            BuddyLoading(Modifier.weight(1f))
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                items(state.messages, key = { it.id }) { msg ->
                    MessageBubble(msg, isMine = msg.senderId == state.myTravelerId)
                }
            }
        }

        // Input
        Row(
            Modifier.fillMaxWidth().background(BuddyColor.TabBarBg).padding(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            BuddyTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = "Escribe un mensaje…",
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = { viewModel.send(draft); draft = "" },
                enabled = draft.isNotBlank() && !state.isSending,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Enviar",
                    tint = if (draft.isNotBlank()) BuddyColor.Brand else BuddyColor.InkFaint,
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: ApiMessage, isMine: Boolean) {
    Box(Modifier.fillMaxWidth(), contentAlignment = if (isMine) Alignment.CenterEnd else Alignment.CenterStart) {
        Text(
            text = msg.content ?: "",
            style = BuddyType.Body,
            color = if (isMine) BuddyColor.InkInverse else BuddyColor.Ink,
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(
                    color = if (isMine) BuddyColor.Brand else BuddyColor.Surface,
                    shape = RoundedCornerShape(Radius.md),
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}
