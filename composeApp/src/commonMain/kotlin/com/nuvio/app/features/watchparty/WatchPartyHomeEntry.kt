package com.nuvio.app.features.watchparty

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.compose_nav_watch_party
import org.jetbrains.compose.resources.stringResource

/**
 * Floating watch-party entry on the Home tab (phones have no persistent top
 * bar). Hidden entirely when the feature is not configured; shows an active
 * dot while a session is running.
 */
@Composable
fun WatchPartyHomeEntry(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!WatchPartyCoordinator.isConfigured) return
    val sessionState by WatchPartyCoordinator.sessionState.collectAsStateWithLifecycle()
    Box(modifier = modifier) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Icon(
                imageVector = Icons.Rounded.Groups,
                contentDescription = stringResource(Res.string.compose_nav_watch_party),
                modifier = Modifier.padding(10.dp).size(22.dp),
            )
        }
        if (sessionState.isActive) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(10.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
    }
}
