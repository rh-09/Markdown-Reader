package com.example.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.MarkdownDocument
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: MarkdownViewModel,
    onOpenFilePicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    val recents by viewModel.recentDocuments.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarkedDocuments.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Recents, 1 = Bookmarks, 2 = Samples }

    // Grouping sample documents vs external documents
    val sampleDocs = recents.filter { it.isSample }
    val realRecents = recents.filter { !it.isSample }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                        Text(
                            text = "Markdown Reader",
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onOpenFilePicker,
                icon = { Icon(Icons.Filled.FileOpen, contentDescription = null) },
                text = { Text("Open File", fontWeight = FontWeight.Bold) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .navigationBarsPadding()
                    .testTag("open_file_fab")
            )
        },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding() + 80.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. Beautiful Hero Banner Card
            item {
                HeroBanner()
            }

            // 2. Tab Navigation for different sections
            item {
                PrimaryTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    divider = {}
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Recents", fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Bookmarks", fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Samples", fontWeight = FontWeight.Bold) }
                    )
                }
            }

            // 3. Lists based on Active Tab
            when (selectedTab) {
                0 -> {
                    if (realRecents.isEmpty()) {
                        item {
                            EmptyState(
                                icon = Icons.Outlined.HistoryToggleOff,
                                title = "No Recent Files",
                                description = "Open any Markdown file (.md, .txt) from your device to begin reading."
                            )
                        }
                    } else {
                        items(realRecents, key = { it.uri }) { document ->
                            DocumentCard(
                                document = document,
                                onClick = { viewModel.navigateTo(AppScreen.Reader(document.uri)) },
                                onToggleBookmark = { viewModel.toggleBookmark(document) },
                                onDelete = { viewModel.deleteDocument(document.uri) }
                            )
                        }
                    }
                }
                1 -> {
                    val realBookmarks = bookmarks.filter { !it.isSample }
                    if (realBookmarks.isEmpty()) {
                        item {
                            EmptyState(
                                icon = Icons.Outlined.StarOutline,
                                title = "No Bookmarks",
                                description = "Star your favorite documents while reading to keep them bookmarked here."
                            )
                        }
                    } else {
                        items(realBookmarks, key = { it.uri }) { document ->
                            DocumentCard(
                                document = document,
                                onClick = { viewModel.navigateTo(AppScreen.Reader(document.uri)) },
                                onToggleBookmark = { viewModel.toggleBookmark(document) },
                                onDelete = { viewModel.deleteDocument(document.uri) }
                            )
                        }
                    }
                }
                2 -> {
                    if (sampleDocs.isEmpty()) {
                        item {
                            EmptyState(
                                icon = Icons.Outlined.LibraryBooks,
                                title = "No Samples Found",
                                description = "Reinstall or reset database to view the custom formatting guides."
                            )
                        }
                    } else {
                        items(sampleDocs, key = { it.uri }) { document ->
                            DocumentCard(
                                document = document,
                                onClick = { viewModel.navigateTo(AppScreen.Reader(document.uri)) },
                                onToggleBookmark = { viewModel.toggleBookmark(document) },
                                onDelete = null // Prevent deleting sample guides
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HeroBanner() {
    val primaryColor = MaterialTheme.colorScheme.primary
    val containerColor = MaterialTheme.colorScheme.primaryContainer

    Card(
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    // Modern Minimalistic code-drawn visual patterns (geometric lines/curves)
                    val width = size.width
                    val height = size.height

                    // Drawing subtle background minimalist circular lines
                    drawCircle(
                        color = primaryColor.copy(alpha = 0.05f),
                        radius = width * 0.4f,
                        center = Offset(width * 0.9f, height * 0.2f)
                    )
                    drawCircle(
                        color = primaryColor.copy(alpha = 0.08f),
                        radius = width * 0.25f,
                        center = Offset(width * 0.85f, height * 0.3f)
                    )

                    // Draw styled diagonal bars
                    val lineBrush = Brush.linearGradient(
                        colors = listOf(primaryColor.copy(alpha = 0.15f), Color.Transparent),
                        start = Offset(0f, height),
                        end = Offset(width * 0.4f, 0f)
                    )
                    drawRect(
                        brush = lineBrush,
                        topLeft = Offset(0f, 0f),
                        size = size
                    )
                }
                .padding(24.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Column(modifier = Modifier.fillMaxWidth(0.7f)) {
                Text(
                    text = "Pure Distraction-Free Reading",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "A beautiful canvas designed entirely for consuming Markdown ideas. No edits, no noise.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    lineHeight = 16.sp
                )
            }

            Icon(
                imageVector = Icons.Outlined.AutoStories,
                contentDescription = null,
                tint = primaryColor.copy(alpha = 0.2f),
                modifier = Modifier
                    .size(96.dp)
                    .align(Alignment.CenterEnd)
                    .offset(x = 8.dp)
            )
        }
    }
}

@Composable
fun DocumentCard(
    document: MarkdownDocument,
    onClick: () -> Unit,
    onToggleBookmark: () -> Unit,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val dateString = remember(document.lastOpened) {
        if (document.lastOpened == 0L) {
            "Never read"
        } else {
            val date = Date(document.lastOpened)
            val format = SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault())
            format.format(date)
        }
    }

    val formattedSize = remember(document.fileSize) {
        val sizeKb = document.fileSize / 1024.0
        if (sizeKb < 1) {
            "${document.fileSize} B"
        } else {
            String.format(Locale.getDefault(), "%.1f KB", sizeKb)
        }
    }

    val readTime = remember(document.wordCount) {
        val minutes = kotlin.math.max(1, kotlin.math.ceil(document.wordCount / 200.0).toInt())
        "$minutes min read"
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("document_card_${document.title.lowercase().replace(" ", "_")}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth()
            ) {
                // File Icon / Type Indicator
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (document.isSample) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (document.isSample) Icons.Outlined.AutoAwesome else Icons.Outlined.Description,
                        contentDescription = null,
                        tint = if (document.isSample) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // File Details
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = document.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = document.filePath,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Action Buttons (Bookmark & Options)
                IconButton(
                    onClick = onToggleBookmark,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (document.isBookmarked) Icons.Filled.Star else Icons.Outlined.StarOutline,
                        contentDescription = "Bookmark",
                        tint = if (document.isBookmarked) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (onDelete != null) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Remove recent",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Progress and Stats
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Secondary Stats (Size, Word Count, Est Read Time)
                Text(
                    text = "$formattedSize · $readTime · $dateString",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )

                if (document.scrollPercentage > 0f) {
                    val percentText = (document.scrollPercentage * 100).toInt()
                    Text(
                        text = if (percentText >= 95) "Finished" else "$percentText% read",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (percentText >= 95) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Reading Progress Bar
            if (document.scrollPercentage > 0f) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { document.scrollPercentage },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(CircleShape),
                    color = if (document.scrollPercentage >= 0.95f) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

@Composable
fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 24.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = description,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 18.sp
        )
    }
}
