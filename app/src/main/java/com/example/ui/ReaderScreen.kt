package com.example.ui

import android.content.Context
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.MarkdownDocument
import com.example.parser.MarkdownBlock
import com.example.parser.MarkdownParser
import com.example.parser.TaskItem
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.*

// Reader Specific Theme configurations
data class ReaderTheme(
    val id: String,
    val name: String,
    val background: Color,
    val surface: Color,
    val text: Color,
    val textSecondary: Color,
    val primary: Color,
    val blockquoteBg: Color,
    val blockquoteBorder: Color,
    val codeBg: Color,
    val codeText: Color,
    val divider: Color
)

val ReaderThemes = listOf(
    ReaderTheme(
        id = "nord_light",
        name = "Nord Light",
        background = Color(0xFFF3F4F6),
        surface = Color(0xFFE5E7EB),
        text = Color(0xFF1F2937),
        textSecondary = Color(0xFF4B5563),
        primary = Color(0xFF2563EB),
        blockquoteBg = Color(0xFFE5E7EB),
        blockquoteBorder = Color(0xFF3B82F6),
        codeBg = Color(0xFFE5E7EB),
        codeText = Color(0xFFDC2626),
        divider = Color(0xFFD1D5DB)
    ),
    ReaderTheme(
        id = "warm_sepia",
        name = "Warm Sepia",
        background = Color(0xFFFBF0D9),
        surface = Color(0xFFF3E4C1),
        text = Color(0xFF433422),
        textSecondary = Color(0xFF705B44),
        primary = Color(0xFF8C6239),
        blockquoteBg = Color(0xFFEBDAB3),
        blockquoteBorder = Color(0xFFB08968),
        codeBg = Color(0xFFEBDAB3),
        codeText = Color(0xFF7F4F24),
        divider = Color(0xFFE1CFA1)
    ),
    ReaderTheme(
        id = "deep_slate",
        name = "Deep Slate",
        background = Color(0xFF1E293B),
        surface = Color(0xFF0F172A),
        text = Color(0xFFF1F5F9),
        textSecondary = Color(0xFF94A3B8),
        primary = Color(0xFF38BDF8),
        blockquoteBg = Color(0xFF0F172A),
        blockquoteBorder = Color(0xFF38BDF8),
        codeBg = Color(0xFF0F172A),
        codeText = Color(0xFFF43F5E),
        divider = Color(0xFF334155)
    ),
    ReaderTheme(
        id = "amoled_black",
        name = "Amoled Dark",
        background = Color(0xFF000000),
        surface = Color(0xFF121212),
        text = Color(0xFFE0E0E0),
        textSecondary = Color(0xFF888888),
        primary = Color(0xFFBB86FC),
        blockquoteBg = Color(0xFF121212),
        blockquoteBorder = Color(0xFFBB86FC),
        codeBg = Color(0xFF121212),
        codeText = Color(0xFFCF6679),
        divider = Color(0xFF222222)
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    viewModel: MarkdownViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val doc by viewModel.activeDocument.collectAsStateWithLifecycle()
    val blocks by viewModel.parsedBlocks.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoadingContent.collectAsStateWithLifecycle()
    val errorMsg by viewModel.errorLoading.collectAsStateWithLifecycle()

    val currentThemeId by viewModel.readerTheme.collectAsStateWithLifecycle()
    val activeTheme = remember(currentThemeId) {
        ReaderThemes.find { it.id == currentThemeId } ?: ReaderThemes[1] // Default Sepia
    }

    val fontSize by viewModel.fontSize.collectAsStateWithLifecycle()
    val fontFamilyType by viewModel.fontFamilyType.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Screen Sub-controls
    var showPreferences by remember { mutableStateOf(false) }
    var showOutline by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }
    var showSearchBar by remember { mutableStateOf(false) }

    // TTS state
    var isTtsActive by remember { mutableStateOf(false) }
    var isTtsPlaying by remember { mutableStateOf(false) }
    var ttsSpeed by remember { mutableStateOf(1.0f) }
    var ttsInstance by remember { mutableStateOf<TextToSpeech?>(null) }

    // Initialize Text-To-Speech
    DisposableEffect(context) {
        val tts = TextToSpeech(context) { status ->
            if (status != TextToSpeech.SUCCESS) {
                Toast.makeText(context, "TTS Initialization failed.", Toast.LENGTH_SHORT).show()
            }
        }
        ttsInstance = tts
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }

    // TTS Speech triggers
    val speakFullDocument = {
        val fullTextBuilder = StringBuilder()
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> fullTextBuilder.append(block.text).append(". ")
                is MarkdownBlock.Paragraph -> fullTextBuilder.append(block.text).append(". ")
                is MarkdownBlock.BlockQuote -> fullTextBuilder.append(block.text).append(". ")
                is MarkdownBlock.UnorderedList -> block.items.forEach { fullTextBuilder.append(it).append(". ") }
                is MarkdownBlock.OrderedList -> block.items.forEach { fullTextBuilder.append(it).append(". ") }
                is MarkdownBlock.TaskList -> block.items.forEach { fullTextBuilder.append(it.text).append(". ") }
                else -> {}
            }
        }
        val textToSpeak = fullTextBuilder.toString()
        if (textToSpeak.isNotBlank() && ttsInstance != null) {
            ttsInstance?.setSpeechRate(ttsSpeed)
            ttsInstance?.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, "MarkdownReaderTTS")
            isTtsPlaying = true
        }
    }

    val pauseTts = {
        ttsInstance?.stop()
        isTtsPlaying = false
    }

    // Restore Reading Progress on file open
    LaunchedEffect(doc?.uri, blocks.isNotEmpty()) {
        val activeDoc = doc
        if (activeDoc != null && blocks.isNotEmpty()) {
            if (activeDoc.scrollPosition > 0 && activeDoc.scrollPosition < blocks.size) {
                listState.scrollToItem(activeDoc.scrollPosition)
            }
        }
    }

    // Capture reading progress reactively during scrolling
    LaunchedEffect(listState, doc?.uri) {
        val activeDoc = doc
        if (activeDoc != null) {
            snapshotFlow {
                val layoutInfo = listState.layoutInfo
                val totalItemsCount = layoutInfo.totalItemsCount
                if (totalItemsCount > 0) {
                    val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    val percent = lastVisibleItemIndex.toFloat() / totalItemsCount
                    percent.coerceIn(0f, 1f)
                } else {
                    0f
                }
            }
            .distinctUntilChanged()
            .collect { percent ->
                viewModel.updateReadingProgress(activeDoc.uri, listState.firstVisibleItemIndex, percent)
            }
        }
    }

    // Search matches calculation
    val matches = remember(blocks, searchQuery) {
        if (searchQuery.isBlank()) emptyList<Int>()
        else {
            blocks.mapIndexedNotNull { index, block ->
                val textToSearch = when (block) {
                    is MarkdownBlock.Heading -> block.text
                    is MarkdownBlock.Paragraph -> block.text
                    is MarkdownBlock.BlockQuote -> block.text
                    is MarkdownBlock.CodeBlock -> block.code
                    is MarkdownBlock.UnorderedList -> block.items.joinToString(" ")
                    is MarkdownBlock.OrderedList -> block.items.joinToString(" ")
                    is MarkdownBlock.TaskList -> block.items.joinToString(" ") { it.text }
                    is MarkdownBlock.Table -> block.headers.joinToString(" ") + " " + block.rows.flatten().joinToString(" ")
                    else -> ""
                }
                if (textToSearch.contains(searchQuery, ignoreCase = true)) index else null
            }
        }
    }
    var currentMatchPointer by remember { mutableIntStateOf(0) }

    LaunchedEffect(searchQuery) {
        currentMatchPointer = 0
    }

    val scrollToMatch = { pointer: Int ->
        if (matches.isNotEmpty() && pointer in matches.indices) {
            coroutineScope.launch {
                listState.animateScrollToItem(matches[pointer])
            }
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = doc?.title ?: "Reading...",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            fontFamily = FontFamily.Serif
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.navigateTo(AppScreen.Library) }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        // Bookmark Star
                        doc?.let { activeDoc ->
                            IconButton(onClick = { viewModel.toggleBookmark(activeDoc) }) {
                                Icon(
                                    imageVector = if (activeDoc.isBookmarked) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                    contentDescription = "Bookmark",
                                    tint = if (activeDoc.isBookmarked) Color(0xFFFFB300) else LocalContentColor.current
                                )
                            }
                        }

                        // Text Settings
                        IconButton(onClick = { showPreferences = true }) {
                            Icon(Icons.Outlined.TextFormat, contentDescription = "Text Settings")
                        }

                        // Search
                        IconButton(onClick = { showSearchBar = !showSearchBar }) {
                            Icon(Icons.Outlined.Search, contentDescription = "Search Page")
                        }

                        // Outline
                        IconButton(onClick = { showOutline = true }) {
                            Icon(Icons.Outlined.MenuOpen, contentDescription = "Outline Index")
                        }

                        // Info / Stats
                        IconButton(onClick = { showStats = true }) {
                            Icon(Icons.Outlined.Info, contentDescription = "Document Stats")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = activeTheme.surface,
                        titleContentColor = activeTheme.text,
                        navigationIconContentColor = activeTheme.text,
                        actionIconContentColor = activeTheme.text
                    )
                )

                // Inline Search Bar
                AnimatedVisibility(
                    visible = showSearchBar,
                    enter = slideInVertically() + fadeIn(),
                    exit = slideOutVertically() + fadeOut()
                ) {
                    Surface(
                        color = activeTheme.surface,
                        contentColor = activeTheme.text,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { viewModel.setSearchQuery(it) },
                                placeholder = { Text("Find in page...", color = activeTheme.textSecondary) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = activeTheme.textSecondary) },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = activeTheme.textSecondary)
                                        }
                                    }
                                },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = activeTheme.primary,
                                    unfocusedBorderColor = activeTheme.divider,
                                    focusedTextColor = activeTheme.text,
                                    unfocusedTextColor = activeTheme.text,
                                    cursorColor = activeTheme.primary
                                ),
                                modifier = Modifier.weight(1f)
                            )

                            if (matches.isNotEmpty()) {
                                Text(
                                    text = "${currentMatchPointer + 1}/${matches.size}",
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                )

                                IconButton(
                                    onClick = {
                                        if (currentMatchPointer > 0) {
                                            currentMatchPointer--
                                            scrollToMatch(currentMatchPointer)
                                        }
                                    },
                                    enabled = currentMatchPointer > 0
                                ) {
                                    Icon(Icons.Default.ChevronLeft, contentDescription = "Prev match")
                                }

                                IconButton(
                                    onClick = {
                                        if (currentMatchPointer < matches.size - 1) {
                                            currentMatchPointer++
                                            scrollToMatch(currentMatchPointer)
                                        }
                                    },
                                    enabled = currentMatchPointer < matches.size - 1
                                ) {
                                    Icon(Icons.Default.ChevronRight, contentDescription = "Next match")
                                }
                            } else if (searchQuery.isNotEmpty()) {
                                Text(
                                    text = "0/0",
                                    fontSize = 12.sp,
                                    color = Color.Red,
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            // TTS bottom play bar (animated)
            AnimatedVisibility(
                visible = isTtsActive,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                Surface(
                    color = activeTheme.surface,
                    contentColor = activeTheme.text,
                    tonalElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(16.dp)
                            .fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                isTtsActive = false
                                pauseTts()
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Close TTS", tint = activeTheme.textSecondary)
                            }
                            Text(
                                text = "Reading Aloud",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Speech speed control
                            TextButton(onClick = {
                                ttsSpeed = when (ttsSpeed) {
                                    1.0f -> 1.25f
                                    1.25f -> 1.5f
                                    1.5f -> 2.0f
                                    2.0f -> 0.75f
                                    else -> 1.0f
                                }
                                if (isTtsPlaying) speakFullDocument()
                            }) {
                                Text("${ttsSpeed}x", fontWeight = FontWeight.Bold, color = activeTheme.primary)
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            FilledIconButton(
                                onClick = {
                                    if (isTtsPlaying) pauseTts() else speakFullDocument()
                                },
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = activeTheme.primary)
                            ) {
                                Icon(
                                    imageVector = if (isTtsPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play/Pause Speech",
                                    tint = activeTheme.background
                                )
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (!isTtsActive && !isLoading && blocks.isNotEmpty()) {
                FloatingActionButton(
                    onClick = {
                        isTtsActive = true
                        speakFullDocument()
                    },
                    containerColor = activeTheme.primary,
                    contentColor = activeTheme.background,
                    modifier = Modifier.navigationBarsPadding()
                ) {
                    Icon(Icons.Filled.VolumeUp, contentDescription = "Read Aloud")
                }
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(activeTheme.background)
                .padding(innerPadding)
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = activeTheme.primary)
                }
            } else if (errorMsg != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Icon(Icons.Outlined.Error, contentDescription = null, tint = Color.Red, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = errorMsg ?: "Error", color = activeTheme.text, textAlign = TextAlign.Center)
                    }
                }
            } else {
                val chosenFontFamily = when (fontFamilyType) {
                    "Sans-Serif" -> FontFamily.SansSerif
                    "Serif" -> FontFamily.Serif
                    "Monospace" -> FontFamily.Monospace
                    else -> FontFamily.Default
                }

                // Main Markdown Render Loop
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(blocks) { blockIndex, block ->
                        val isHighlighted = blockIndex in matches
                        val blockModifier = if (isHighlighted && searchQuery.isNotEmpty()) {
                            Modifier
                                .background(Color.Yellow.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                .padding(vertical = 4.dp, horizontal = 8.dp)
                        } else {
                            Modifier
                        }

                        Box(modifier = blockModifier.fillMaxWidth()) {
                            RenderBlock(
                                block = block,
                                theme = activeTheme,
                                fontSize = fontSize,
                                fontFamily = chosenFontFamily,
                                searchQuery = searchQuery
                            )
                        }
                    }
                }
            }
        }
    }

    // --- Bottom Sheet Customizer (Font size, families, themes) ---
    if (showPreferences) {
        ModalBottomSheet(
            onDismissRequest = { showPreferences = false },
            containerColor = activeTheme.background,
            contentColor = activeTheme.text
        ) {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .fillMaxWidth()
            ) {
                Text("Reader Settings", fontWeight = FontWeight.Bold, fontSize = 18.sp, fontFamily = FontFamily.Serif)
                Spacer(modifier = Modifier.height(16.dp))

                // Theme selector
                Text("Theme Canvas", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = activeTheme.textSecondary)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ReaderThemes.forEach { t ->
                        val isSelected = t.id == currentThemeId
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(t.background)
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) activeTheme.primary else t.divider,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { viewModel.setReaderTheme(t.id) }
                        ) {
                            Text(
                                text = t.name,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = t.text
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Font Family selector
                Text("Typography Family", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = activeTheme.textSecondary)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf("Sans-Serif", "Serif", "Monospace").forEach { family ->
                        val isSelected = family == fontFamilyType
                        Button(
                            onClick = { viewModel.setFontFamilyType(family) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) activeTheme.primary else activeTheme.surface,
                                contentColor = if (isSelected) activeTheme.background else activeTheme.text
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(family, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Font size slider
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Text Sizing", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = activeTheme.textSecondary)
                    Text("${fontSize.toInt()} sp", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("A", fontSize = 14.sp, fontWeight = FontWeight.Light)
                    Slider(
                        value = fontSize,
                        onValueChange = { viewModel.setFontSize(it) },
                        valueRange = 12f..32f,
                        colors = SliderDefaults.colors(
                            thumbColor = activeTheme.primary,
                            activeTrackColor = activeTheme.primary,
                            inactiveTrackColor = activeTheme.divider
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp)
                    )
                    Text("A", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // --- Outline Side Panel / Index Bottom Sheet ---
    if (showOutline) {
        val headings = remember(blocks) {
            blocks.mapIndexedNotNull { index, block ->
                if (block is MarkdownBlock.Heading) index to block else null
            }
        }

        ModalBottomSheet(
            onDismissRequest = { showOutline = false },
            containerColor = activeTheme.background,
            contentColor = activeTheme.text
        ) {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .fillMaxHeight(0.6f)
                    .fillMaxWidth()
            ) {
                Text("Document Index", fontWeight = FontWeight.Bold, fontSize = 18.sp, fontFamily = FontFamily.Serif)
                Spacer(modifier = Modifier.height(16.dp))

                if (headings.isEmpty()) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text("No headings found in this document.", color = activeTheme.textSecondary)
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(headings.size) { i ->
                            val (originalIndex, heading) = headings[i]
                            val indent = (heading.level - 1) * 16
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        showOutline = false
                                        coroutineScope.launch {
                                            listState.animateScrollToItem(originalIndex)
                                        }
                                    }
                                    .padding(vertical = 10.dp, horizontal = 12.dp)
                                    .padding(start = indent.dp)
                            ) {
                                Text(
                                    text = "#".repeat(heading.level) + " " + heading.text,
                                    fontSize = (15 - heading.level.coerceAtMost(3)).sp,
                                    fontWeight = if (heading.level == 1) FontWeight.Bold else FontWeight.Medium,
                                    color = if (heading.level == 1) activeTheme.primary else activeTheme.text,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // --- Stats Dialog ---
    if (showStats) {
        AlertDialog(
            onDismissRequest = { showStats = false },
            containerColor = activeTheme.background,
            titleContentColor = activeTheme.text,
            textContentColor = activeTheme.text,
            title = { Text("Document Intelligence", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold) },
            text = {
                val wc = doc?.wordCount ?: 0
                val charCount = activeTheme.id.length * 100 // Approximation if content null
                val estReadTime = kotlin.math.max(1, kotlin.math.ceil(wc / 200.0).toInt())
                val sizeKb = (doc?.fileSize ?: 0L) / 1024.0

                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Total Words", color = activeTheme.textSecondary)
                        Text("$wc", fontWeight = FontWeight.Bold)
                    }
                    Divider(color = activeTheme.divider)
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Est. Read Time", color = activeTheme.textSecondary)
                        Text("$estReadTime min", fontWeight = FontWeight.Bold)
                    }
                    Divider(color = activeTheme.divider)
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("File Weight", color = activeTheme.textSecondary)
                        Text(String.format(Locale.getDefault(), "%.2f KB", sizeKb), fontWeight = FontWeight.Bold)
                    }
                    Divider(color = activeTheme.divider)
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Status", color = activeTheme.textSecondary)
                        Text(if (doc?.isSample == true) "System Guide" else "External File", fontWeight = FontWeight.Bold, color = activeTheme.primary)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showStats = false }) {
                    Text("Acknowledge", color = activeTheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
fun RenderBlock(
    block: MarkdownBlock,
    theme: ReaderTheme,
    fontSize: Float,
    fontFamily: FontFamily,
    searchQuery: String
) {
    val uriHandler = LocalUriHandler.current

    when (block) {
        is MarkdownBlock.Heading -> {
            val scale = when (block.level) {
                1 -> 1.55f
                2 -> 1.40f
                3 -> 1.25f
                4 -> 1.15f
                else -> 1.0f
            }
            val headingStyle = TextStyle(
                fontFamily = fontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = (fontSize * scale).sp,
                color = if (block.level == 1) theme.primary else theme.text,
                lineHeight = (fontSize * scale * 1.3).sp
            )

            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                Text(
                    text = MarkdownParser.parseInline(block.text, theme.primary, theme.primary, theme.codeBg),
                    style = headingStyle
                )
                if (block.level == 1) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(theme.primary.copy(alpha = 0.5f))
                    )
                } else if (block.level == 2) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .width(60.dp)
                            .height(1.5.dp)
                            .background(theme.textSecondary.copy(alpha = 0.4f))
                    )
                }
            }
        }

        is MarkdownBlock.Paragraph -> {
            val style = TextStyle(
                fontFamily = fontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = fontSize.sp,
                color = theme.text,
                lineHeight = (fontSize * 1.55f).sp
            )

            val parsedString = remember(block.text, searchQuery) {
                MarkdownParser.parseInline(block.text, theme.primary, theme.primary, theme.codeBg)
            }

            ClickableText(
                text = parsedString,
                style = style,
                onClick = { offset ->
                    parsedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                        .firstOrNull()?.let { annotation ->
                            uriHandler.openUri(annotation.item)
                        }
                }
            )
        }

        is MarkdownBlock.BlockQuote -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp))
                    .background(theme.blockquoteBg.copy(alpha = 0.4f))
                    .drawBehind {
                        // Left vertical thick line with round corners
                        drawRect(
                            color = theme.blockquoteBorder,
                            topLeft = Offset(0f, 0f),
                            size = androidx.compose.ui.geometry.Size(6.dp.toPx(), size.height)
                        )
                    }
                    .padding(vertical = 12.dp, horizontal = 16.dp)
            ) {
                Text(
                    text = block.text,
                    fontFamily = fontFamily,
                    fontStyle = FontStyle.Italic,
                    fontSize = (fontSize * 0.95f).sp,
                    color = theme.text,
                    lineHeight = (fontSize * 1.45f).sp,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }

        is MarkdownBlock.CodeBlock -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(theme.codeBg.copy(alpha = 0.5f))
                    .border(0.5.dp, theme.divider, RoundedCornerShape(8.dp))
            ) {
                // Code block header/tab showing language
                if (block.language.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(theme.surface.copy(alpha = 0.5f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = block.language.uppercase(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = theme.textSecondary
                        )
                        Icon(
                            imageVector = Icons.Outlined.Code,
                            contentDescription = null,
                            tint = theme.textSecondary.copy(alpha = 0.5f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                // Code contents
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(14.dp)
                ) {
                    Text(
                        text = block.code,
                        fontFamily = FontFamily.Monospace,
                        fontSize = (fontSize * 0.85f).sp,
                        color = theme.codeText,
                        lineHeight = (fontSize * 1.3f).sp
                    )
                }
            }
        }

        is MarkdownBlock.UnorderedList -> {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp)
            ) {
                block.items.forEach { item ->
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            text = "•",
                            fontSize = (fontSize * 1.2f).sp,
                            color = theme.primary,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                        Text(
                            text = MarkdownParser.parseInline(item, theme.primary, theme.primary, theme.codeBg),
                            fontFamily = fontFamily,
                            fontSize = fontSize.sp,
                            color = theme.text,
                            lineHeight = (fontSize * 1.45f).sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        is MarkdownBlock.OrderedList -> {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp)
            ) {
                block.items.forEachIndexed { i, item ->
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            text = "${i + 1}.",
                            fontWeight = FontWeight.Bold,
                            fontSize = fontSize.sp,
                            color = theme.primary,
                            modifier = Modifier.padding(end = 10.dp)
                        )
                        Text(
                            text = MarkdownParser.parseInline(item, theme.primary, theme.primary, theme.codeBg),
                            fontFamily = fontFamily,
                            fontSize = fontSize.sp,
                            color = theme.text,
                            lineHeight = (fontSize * 1.45f).sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        is MarkdownBlock.TaskList -> {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp)
            ) {
                block.items.forEach { task ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = if (task.checked) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                            contentDescription = if (task.checked) "Checked" else "Unchecked",
                            tint = if (task.checked) theme.primary else theme.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = MarkdownParser.parseInline(task.text, theme.primary, theme.primary, theme.codeBg),
                            fontFamily = fontFamily,
                            fontSize = fontSize.sp,
                            color = if (task.checked) theme.textSecondary else theme.text,
                            textDecoration = if (task.checked) TextDecoration.LineThrough else TextDecoration.None,
                            lineHeight = (fontSize * 1.45f).sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        is MarkdownBlock.Table -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .border(0.5.dp, theme.divider, RoundedCornerShape(8.dp))
                    .clip(RoundedCornerShape(8.dp))
                    .horizontalScroll(rememberScrollState())
            ) {
                // Table Header
                Row(
                    modifier = Modifier
                        .background(theme.surface.copy(alpha = 0.5f))
                        .padding(vertical = 8.dp, horizontal = 12.dp)
                ) {
                    block.headers.forEach { header ->
                        Text(
                            text = header,
                            fontWeight = FontWeight.Bold,
                            fontFamily = fontFamily,
                            fontSize = (fontSize * 0.9f).sp,
                            color = theme.text,
                            modifier = Modifier
                                .widthIn(min = 100.dp, max = 220.dp)
                                .padding(horizontal = 6.dp)
                        )
                    }
                }

                Divider(color = theme.divider, thickness = 0.5.dp)

                // Table Rows
                block.rows.forEachIndexed { rowIndex, row ->
                    Row(
                        modifier = Modifier
                            .background(if (rowIndex % 2 == 1) theme.blockquoteBg.copy(alpha = 0.15f) else Color.Transparent)
                            .padding(vertical = 8.dp, horizontal = 12.dp)
                    ) {
                        row.forEach { cell ->
                            Text(
                                text = MarkdownParser.parseInline(cell, theme.primary, theme.primary, theme.codeBg),
                                fontFamily = fontFamily,
                                fontSize = (fontSize * 0.9f).sp,
                                color = theme.text,
                                modifier = Modifier
                                    .widthIn(min = 100.dp, max = 220.dp)
                                    .padding(horizontal = 6.dp)
                            )
                        }
                    }
                    if (rowIndex < block.rows.size - 1) {
                        Divider(color = theme.divider.copy(alpha = 0.3f), thickness = 0.5.dp)
                    }
                }
            }
        }

        is MarkdownBlock.HorizontalRule -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                ) {
                    drawLine(
                        color = theme.divider,
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    )
                }
            }
        }
    }
}
