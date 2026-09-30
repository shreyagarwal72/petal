package com.petal.browser.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderShared
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.petal.browser.account.GoogleAccountManager
import com.petal.browser.account.ProfileAvatarDisplay

/** Shows the account-page avatar for the default profile and the saved gallery avatar for others. */
@Composable
fun PetalProfileAvatar(
    profile: PetalProfile,
    size: Dp,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = CircleShape,
) {
    if (profile.isDefault) {
        ProfileAvatarDisplay(
            profile = GoogleAccountManager.currentProfile,
            sizeDp = size.value.toInt().coerceAtLeast(1),
            modifier = modifier,
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(profile.getComposeColor().copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) {
            val avatarUri = profile.customAvatarUri
            if (avatarUri != null) {
                AsyncImage(
                    model = avatarUri,
                    contentDescription = null,
                    modifier = Modifier.size(size).clip(shape),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.FolderShared,
                    contentDescription = null,
                    tint = profile.getComposeColor(),
                    modifier = Modifier.size(size * 0.55f),
                )
            }
        }
    }
}
