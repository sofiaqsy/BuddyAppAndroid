package com.buddy.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.buddy.app.core.designsystem.BuddyColor
import com.buddy.app.core.designsystem.BuddyType

/**
 * Tab bar propia — espejo del GlassTabBar de iOS.
 * Superficie clara cálida con hairline superior; tab activo en Buddy Brown,
 * inactivo en TabBarInactive. Ripple es la adaptación Android (feedback táctil).
 * Re-tap del tab activo dispara onReselect (scroll-to-top / reload, como iOS).
 */
@Composable
fun BuddyTabBar(
    selected: AppTab,
    unreadChats: Int,
    onSelect: (AppTab) -> Unit,
    onReselect: (AppTab) -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(BuddyColor.TabBarBg)) {
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(BuddyColor.Hairline))
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(top = 6.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            AppTab.entries.forEach { tab ->
                val active = tab == selected
                val badge = if (tab == AppTab.Conexiones) unreadChats else 0
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = false, color = BuddyColor.Brand),
                        ) { if (active) onReselect(tab) else onSelect(tab) }
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    BadgedBox(badge = {
                        if (badge > 0) Badge(containerColor = BuddyColor.ErrorRed) { Text("$badge") }
                    }) {
                        Icon(
                            imageVector = if (active) tab.activeIcon else tab.icon,
                            contentDescription = stringResource(tab.label),
                            tint = if (active) BuddyColor.Brand else BuddyColor.TabBarInactive,
                        )
                    }
                    Text(
                        text = stringResource(tab.label),
                        style = BuddyType.Caption1,
                        color = if (active) BuddyColor.Brand else BuddyColor.TabBarInactive,
                    )
                }
            }
        }
    }
}
