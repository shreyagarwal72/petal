package com.petal.browser.compose.ai

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petal.browser.unit.BrowserUnit
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PetalAiHubScreen(
    context: Context,
    onOpenUrl: (String) -> Unit,
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var aiServices by remember { mutableStateOf<List<AiService>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isSyncing by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var selectedFilterPricing by remember { mutableStateOf("All") }
    var selectedFilterPrivacy by remember { mutableStateOf("All") }
    var showFilterDialog by remember { mutableStateOf(false) }
    var showCustomScriptDialog by remember { mutableStateOf(false) }

    val settings = remember { PetalAiHubManager.getSettings(context) }
    var favoriteSet by remember { mutableStateOf(settings.favoriteServices) }

    LaunchedEffect(Unit) {
        aiServices = PetalAiHubManager.getAiServices(context)
        isLoading = false
    }

    val categories = remember(aiServices) {
        listOf("All", "Favorites") + aiServices.map { it.category }.distinct().sorted()
    }

    val filteredServices = remember(aiServices, searchQuery, selectedCategory, selectedFilterPricing, selectedFilterPrivacy, favoriteSet) {
        aiServices.filter { service ->
            val matchesCategory = when (selectedCategory) {
                "All" -> true
                "Favorites" -> favoriteSet.contains(service.name)
                else -> service.category.equals(selectedCategory, ignoreCase = true)
            }
            val matchesPricing = when (selectedFilterPricing) {
                "All" -> true
                else -> service.pricing.equals(selectedFilterPricing, ignoreCase = true)
            }
            val matchesPrivacy = when (selectedFilterPrivacy) {
                "All" -> true
                else -> service.privacy.equals(selectedFilterPrivacy, ignoreCase = true)
            }
            val matchesSearch = searchQuery.isBlank() ||
                    service.name.contains(searchQuery, ignoreCase = true) ||
                    service.url.contains(searchQuery, ignoreCase = true) ||
                    service.bestFor.any { it.contains(searchQuery, ignoreCase = true) }

            matchesCategory && matchesPricing && matchesPrivacy && matchesSearch
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "AI Hub Directory",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            "${filteredServices.size} AI Services",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                isSyncing = true
                                val success = PetalAiHubManager.syncCloudData(context)
                                if (success) {
                                    aiServices = PetalAiHubManager.getAiServices(context)
                                    Toast.makeText(context, "AI Services updated from Cloud!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Cloud sync check complete", Toast.LENGTH_SHORT).show()
                                }
                                isSyncing = false
                            }
                        }
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Rounded.CloudSync, contentDescription = "Sync Cloud")
                        }
                    }

                    IconButton(onClick = { showCustomScriptDialog = true }) {
                        Icon(Icons.Rounded.Code, contentDescription = "Custom JS/CSS")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search Bar & Filter Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search AI services, tools, or tasks...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = if (searchQuery.isNotBlank()) {
                        { IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Rounded.Close, contentDescription = "Clear") } }
                    } else null,
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = { showFilterDialog = true },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                ) {
                    Icon(
                        Icons.Rounded.FilterList,
                        contentDescription = "Filter",
                        tint = if (selectedFilterPricing != "All" || selectedFilterPrivacy != "All") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Category Chips Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat.capitalizeFirstLetter()) },
                            leadingIcon = if (cat == "Favorites") {
                                { Icon(Icons.Rounded.Star, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFFFFB300)) }
                            } else null
                        )
                    }
                }
            }

            // AI Services Directory List
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (filteredServices.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.SearchOff, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.height(12.dp))
                        Text("No AI services match your filters.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredServices, key = { it.name }) { service ->
                        val isFavorite = favoriteSet.contains(service.name)

                        AiServiceCard(
                            service = service,
                            isFavorite = isFavorite,
                            onFavoriteToggle = {
                                PetalAiHubManager.toggleFavoriteService(context, service.name)
                                favoriteSet = PetalAiHubManager.getSettings(context).favoriteServices
                            },
                            onOpen = {
                                onOpenUrl(service.url)
                            }
                        )
                    }
                }
            }
        }
    }

    // Filter Dialog
    if (showFilterDialog) {
        AlertDialog(
            onDismissRequest = { showFilterDialog = false },
            title = { Text("Filter AI Services", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Pricing Filter:", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("All", "free", "freemium", "paid").forEach { price ->
                            FilterChip(
                                selected = selectedFilterPricing == price,
                                onClick = { selectedFilterPricing = price },
                                label = { Text(price.capitalizeFirstLetter()) }
                            )
                        }
                    }

                    Text("Privacy Rating Filter:", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("All", "friendly", "avoid").forEach { priv ->
                            FilterChip(
                                selected = selectedFilterPrivacy == priv,
                                onClick = { selectedFilterPrivacy = priv },
                                label = { Text(priv.capitalizeFirstLetter()) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFilterDialog = false }) {
                    Text("Done")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        selectedFilterPricing = "All"
                        selectedFilterPrivacy = "All"
                    }
                ) {
                    Text("Reset Filters")
                }
            }
        )
    }

    // Custom CSS/JS Injection Dialog
    if (showCustomScriptDialog) {
        var customJs by remember { mutableStateOf(settings.customJs) }
        var customCss by remember { mutableStateOf(settings.customCss) }

        AlertDialog(
            onDismissRequest = { showCustomScriptDialog = false },
            title = { Text("Custom Script Injection", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Text(
                        "Inject custom CSS or JavaScript into AI web services upon load.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = customJs,
                        onValueChange = { customJs = it },
                        label = { Text("Custom JavaScript") },
                        placeholder = { Text("e.g. console.log('AI Hub loaded');") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 5,
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = customCss,
                        onValueChange = { customCss = it },
                        label = { Text("Custom CSS Styles") },
                        placeholder = { Text("e.g. body { font-family: sans-serif; }") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 5,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newSettings = settings.copy(customJs = customJs, customCss = customCss)
                        PetalAiHubManager.saveSettings(context, newSettings)
                        Toast.makeText(context, "Custom script saved", Toast.LENGTH_SHORT).show()
                        showCustomScriptDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomScriptDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AiServiceCard(
    service: AiService,
    isFavorite: Boolean,
    onFavoriteToggle: () -> Unit,
    onOpen: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(service.accentColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            service.name.take(1).uppercase(),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = service.accentColor
                        )
                    }

                    Column {
                        Text(
                            service.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            service.category.capitalizeFirstLetter(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onFavoriteToggle) {
                        Icon(
                            if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            contentDescription = "Favorite",
                            tint = if (isFavorite) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = onOpen,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text("Open", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                    }
                }
            }

            // Pricing & Privacy Badges
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pricing badge
                val pricingColor = when (service.pricing.lowercase()) {
                    "free" -> Color(0xFF4CAF50)
                    "freemium" -> Color(0xFF2196F3)
                    else -> Color(0xFFFF9800)
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = pricingColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        service.pricing.capitalizeFirstLetter(),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = pricingColor
                    )
                }

                // Privacy badge
                val privacyColor = when (service.privacy.lowercase()) {
                    "friendly" -> Color(0xFF4CAF50)
                    "avoid" -> Color(0xFFE53935)
                    else -> Color(0xFFFFB300)
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = privacyColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        "Privacy: ${service.privacy.capitalizeFirstLetter()}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = privacyColor
                    )
                }

                if (service.loginRequired) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    ) {
                        Text(
                            "Login Req",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Best for tags
            if (service.bestFor.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    service.bestFor.forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                "#$tag",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun String.capitalizeFirstLetter(): String {
    return this.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}
