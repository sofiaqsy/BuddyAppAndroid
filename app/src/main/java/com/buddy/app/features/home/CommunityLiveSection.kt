package com.buddy.app.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonPinCircle
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.buddy.app.core.data.model.ApiPulseItem
import com.buddy.app.core.data.model.ApiRecentHelp
import com.buddy.app.core.designsystem.BuddyColor
import com.buddy.app.core.designsystem.BuddyType
import com.buddy.app.core.designsystem.Radius
import com.buddy.app.core.designsystem.Spacing

/**
 * Sección de comunidad viva (espejo de iOS).
 * Muestra: actividad local (recent help) o pulso global (fallback).
 */
@Composable
fun CommunityLiveSection(
    recentHelp: List<ApiRecentHelp>,
    communityPulse: List<ApiPulseItem>,
    isLoading: Boolean,
    formatTimeAgo: (String?) -> String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            "COMUNIDAD VIVA",
            style = BuddyType.Eyebrow,
            letterSpacing = 1.5.sp,
            color = BuddyColor.Ink,
            modifier = Modifier.padding(horizontal = Spacing.edge)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.edge, vertical = Spacing.md)
                .background(BuddyColor.Surface, RoundedCornerShape(Radius.md))
                .border(1.dp, BuddyColor.Border, RoundedCornerShape(Radius.md))
        ) {
            if (recentHelp.isNotEmpty()) {
                // Actividad local
                recentHelp.take(3).forEachIndexed { idx, help ->
                    if (idx > 0) {
                        Divider(
                            color = BuddyColor.Border,
                            modifier = Modifier.padding(start = 56.dp)
                        )
                    }
                    CommunityRow(
                        avatarUrl = help.buddy?.avatarUrl,
                        icon = "person.fill",
                        text = buildAnnotatedString {
                            val buddyName = help.buddy?.fullName?.split(" ")?.firstOrNull()?.replaceFirstChar { it.uppercaseChar() }
                                ?: "Un buddy"
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                append(buddyName)
                            }
                            append(" ayudó a un viajero")
                            withStyle(SpanStyle(fontSize = 12.sp, color = BuddyColor.InkMuted.copy(alpha = 0.7f))) {
                                append(" · ${formatTimeAgo(help.completedAt)}")
                            }
                        }
                    )
                }
            } else {
                // Pulso global (fallback)
                communityPulse.take(3).forEachIndexed { idx, item ->
                    if (idx > 0) {
                        Divider(
                            color = BuddyColor.Border,
                            modifier = Modifier.padding(start = 56.dp)
                        )
                    }
                    CommunityRow(
                        avatarUrl = null,
                        icon = pulseIcon(item.type),
                        text = pulseText(item, formatTimeAgo)
                    )
                }
            }
        }
    }
}

@Composable
private fun CommunityRow(
    avatarUrl: String?,
    icon: String,
    text: AnnotatedString,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Avatar o icono
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(BuddyColor.SurfaceRaised),
            contentAlignment = Alignment.Center,
        ) {
            if (avatarUrl != null) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape),
                )
            } else {
                Icon(
                    imageVector = when (icon) {
                        "person.2.fill" -> Icons.Default.PersonAdd
                        "figure.walk" -> Icons.Default.PersonPinCircle
                        else -> Icons.Default.Person
                    },
                    contentDescription = null,
                    tint = BuddyColor.InkMuted,
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        Text(text, maxLines = 1, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun pulseText(item: ApiPulseItem, formatTimeAgo: (String?) -> String): AnnotatedString {
    return when (item.type) {
        "traveling" -> {
            val n = item.count ?: 1
            val prefix = if (n == 1) "Un viajero está en " else "$n viajeros están en "
            buildAnnotatedString {
                append(prefix)
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(item.city)
                }
            }
        }
        "ready" -> {
            val n = item.count ?: 1
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(item.city)
                }
                append(if (n == 1) " · 1 buddy listo para ayudar" else " · $n buddies listos para ayudar")
            }
        }
        else -> { // helped
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(item.city)
                }
                append(" · un buddy ayudó a un viajero")
                withStyle(SpanStyle(fontSize = 12.sp, color = BuddyColor.InkMuted.copy(alpha = 0.7f))) {
                    append(" · ${formatTimeAgo(item.at)}")
                }
            }
        }
    }
}

private fun pulseIcon(type: String): String = when (type) {
    "traveling" -> "figure.walk"
    "ready" -> "person.2.fill"
    else -> "person.fill"
}
