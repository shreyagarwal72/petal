package com.petal.browser.account

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.petal.browser.compose.home.PetalShortcut

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChromeAccountSyncScreen(
    onBack: () -> Unit,
    onOpenOAuth: (PetalShortcut) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val profile = GoogleAccountManager.currentProfile


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Account & Sync", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Profile Card
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(if (profile.isSignedIn) Color(0xFF4285F4) else MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        if (profile.isSignedIn && !profile.avatarUrl.isNullOrEmpty()) {
                            // Show the real Google account photo, same as the home screen.
                            AsyncImage(
                                model = profile.avatarUrl,
                                contentDescription = "Profile",
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else if (profile.isSignedIn) {
                            val initial = profile.displayName.trim().take(1).ifEmpty { "G" }.uppercase()
                            Text(
                                text = initial,
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        } else {
                            Icon(
                                Icons.Rounded.PersonAdd,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (profile.isSignedIn) profile.displayName else "Google Account",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (profile.isSignedIn) profile.email else "Sign in to sync your browser data",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (!profile.isSignedIn) {
                        Button(
                            onClick = {
                                onOpenOAuth(PetalShortcut("Google OAuth", "https://accounts.google.com/ServiceLogin", "google", Color(0xFF4285F4)))
                            },
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Sign In")
                        }
                    }
                }
            }

            // Account Actions Card
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    ListItem(
                        headlineContent = { Text("Add Another Account", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)) },
                        supportingContent = { Text("Sign in with a different Google account", style = MaterialTheme.typography.bodySmall) },
                        leadingContent = { Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.clickable {
                            onOpenOAuth(PetalShortcut("Google OAuth", "https://accounts.google.com/AddSession", "google", Color(0xFF4285F4)))
                        }
                    )

                    if (profile.isSignedIn) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        ListItem(
                            headlineContent = { Text("Sign Out", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.error) },
                            supportingContent = { Text("Clear Google account session", style = MaterialTheme.typography.bodySmall) },
                            leadingContent = { Icon(Icons.Rounded.Logout, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            modifier = Modifier.clickable {
                                GoogleAccountManager.signOut(context)
                            }
                        )
                    }
                }
            }
        }
    }
}

