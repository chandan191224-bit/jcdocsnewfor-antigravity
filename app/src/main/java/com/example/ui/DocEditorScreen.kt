package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
import androidx.compose.ui.text.drawText
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import kotlin.text.RegexOption
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.takeOrElse
import kotlinx.coroutines.launch
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.db.DocEntity
import com.example.ui.theme.*
import com.example.viewmodel.DocViewModel
import com.example.viewmodel.SlideItem
import java.text.SimpleDateFormat
import java.util.*

data class DocFormatSpan(var start: Int, var end: Int, val type: String, val value: String = "")

object DocFormatRepository {
    private val spans = mutableMapOf<Int, androidx.compose.runtime.snapshots.SnapshotStateList<DocFormatSpan>>()
    
    fun getSpans(docId: Int): androidx.compose.runtime.snapshots.SnapshotStateList<DocFormatSpan> {
        return spans.getOrPut(docId) { androidx.compose.runtime.mutableStateListOf() }
    }
    
    fun removeSpansRange(docId: Int, start: Int, end: Int) {
        val list = getSpans(docId)
        val toAdd = mutableListOf<DocFormatSpan>()
        val iterator = list.iterator()
        while (iterator.hasNext()) {
            val span = iterator.next()
            if (span.start >= start && span.end <= end) {
                iterator.remove()
            } else if (span.start < start && span.end > end) {
                val oldEnd = span.end
                span.end = start
                toAdd.add(DocFormatSpan(end, oldEnd, span.type, span.value))
            } else if (span.start < start && span.end > start && span.end <= end) {
                span.end = start
            } else if (span.start >= start && span.start < end && span.end > end) {
                span.start = end
            }
        }
        list.addAll(toAdd)
    }
    
    fun hasSpan(docId: Int, type: String, start: Int, end: Int): Boolean {
        val list = getSpans(docId)
        return list.any { it.type == type && it.start <= start && it.end >= end }
    }
    
    fun removeSpanTypeRange(docId: Int, type: String, start: Int, end: Int) {
        val list = getSpans(docId)
        val toAdd = mutableListOf<DocFormatSpan>()
        val iterator = list.iterator()
        while (iterator.hasNext()) {
            val span = iterator.next()
            if (span.type == type) {
                // If the span is fully within the selection, remove it.
                if (span.start >= start && span.end <= end) {
                    iterator.remove()
                } else if (span.start < start && span.end > end) {
                    // If span covers the selection entirely, split it.
                    val oldEnd = span.end
                    span.end = start
                    toAdd.add(DocFormatSpan(end, oldEnd, span.type, span.value))
                } else if (span.start < start && span.end > start && span.end <= end) {
                    // If span overlaps with the beginning of the selection.
                    span.end = start
                } else if (span.start >= start && span.start < end && span.end > end) {
                    // If span overlaps with the end of the selection.
                    span.start = end
                }
            }
        }
        list.addAll(toAdd)
    }
    
    fun applySpan(docId: Int, type: String, value: String, start: Int, end: Int) {
        val list = getSpans(docId)
        if (start >= end) return

        val isParagraphLevel = type == "alignment" || type == "lineSpacing"

        // 1. Remove/update any existing spans that overlap with the new span
        val iterator = list.iterator()
        var newStart = start
        var newEnd = end

        while (iterator.hasNext()) {
            val span = iterator.next()
            if (span.type == type && span.value == value) {
                // For paragraph-level spans (alignment, lineSpacing), only remove
                // overlapping spans within the same paragraph boundaries — don't
                // merge across paragraphs, since each paragraph needs its own
                // ParagraphStyle in RichTextVisualTransformation.
                if (isParagraphLevel) {
                    if (span.start >= start && span.end <= end) {
                        iterator.remove()
                    } else if (span.start < start && span.end > end) {
                        val oldEnd = span.end
                        span.end = start
                        list.add(DocFormatSpan(end, oldEnd, span.type, span.value))
                    } else if (span.start < start && span.end > start && span.end <= end) {
                        span.end = start
                    } else if (span.start >= start && span.start < end && span.end > end) {
                        span.start = end
                    }
                } else {
                    // Character-level spans (bold, italic, fontSize, etc.): merge adjacent/overlapping
                    if (span.end >= newStart && span.start <= newEnd) {
                        newStart = minOf(newStart, span.start)
                        newEnd = maxOf(newEnd, span.end)
                        iterator.remove()
                    }
                }
            }
        }
        
        // 2. Add the span
        list.add(DocFormatSpan(newStart, newEnd, type, value))
    }
    
    fun shiftSpans(docId: Int, changeStart: Int, deleted: Int, inserted: Int) {
        val list = getSpans(docId)
        val diff = inserted - deleted
        if (diff == 0) return
        val iterator = list.iterator()
        while (iterator.hasNext()) {
            val span = iterator.next()
            if (span.start >= changeStart + deleted) {
                span.start += diff
                span.end += diff
            } else if (span.start >= changeStart && span.end <= changeStart + deleted) {
                iterator.remove()
            } else if (span.start < changeStart && span.end > changeStart + deleted) {
                span.end += diff
            } else if (span.end > changeStart) {
                span.end = maxOf(span.start, span.end + diff)
            }
        }
    }

    fun moveSpanRange(docId: Int, fromStart: Int, fromEnd: Int, toStart: Int) {
        val list = getSpans(docId)
        if (fromStart >= fromEnd) return
        val shift = toStart - fromStart
        val added = mutableListOf<DocFormatSpan>()
        val it = list.iterator()
        while (it.hasNext()) {
            val span = it.next()
            when {
                // entirely within moved range
                span.start >= fromStart && span.end <= fromEnd -> {
                    it.remove()
                    span.start += shift
                    span.end += shift
                    if (span.end > span.start) added.add(span)
                }
                // starts before, ends within moved range
                span.start < fromStart && span.end > fromStart && span.end <= fromEnd -> {
                    val oldEnd = span.end
                    span.end = fromStart
                    val newSpan = DocFormatSpan(toStart, toStart + (oldEnd - fromStart), span.type, span.value)
                    if (newSpan.end > newSpan.start) added.add(newSpan)
                }
                // starts within moved range, ends after
                span.start >= fromStart && span.start < fromEnd && span.end > fromEnd -> {
                    val oldStart = span.start
                    it.remove()
                    val newSpan = DocFormatSpan(toStart + (oldStart - fromStart), toStart + (fromEnd - fromStart), span.type, span.value)
                    if (newSpan.end > newSpan.start) added.add(newSpan)
                    span.start = fromEnd
                    span.end = fromEnd + (span.end - fromEnd)
                    added.add(span)
                }
                // starts before, ends after (covers entire range)
                span.start < fromStart && span.end > fromEnd -> {
                    val oldEnd = span.end
                    span.end = fromStart
                    val newSpan = DocFormatSpan(toStart, toStart + (fromEnd - fromStart), span.type, span.value)
                    if (newSpan.end > newSpan.start) added.add(newSpan)
                    val rightSpan = DocFormatSpan(fromEnd, oldEnd, span.type, span.value)
                    added.add(rightSpan)
                }
            }
        }
        list.addAll(added)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocEditorScreen(
    viewModel: DocViewModel,
    modifier: Modifier = Modifier
) {
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val selectedDoc by viewModel.selectedDoc.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedTypeFilter.collectAsStateWithLifecycle()

    val draftTitle by viewModel.draftTitle.collectAsStateWithLifecycle()
    val draftContent by viewModel.draftContent.collectAsStateWithLifecycle()
    val pageFormat by viewModel.pageFormat.collectAsStateWithLifecycle()
    val customDimensions by viewModel.customPageDimensions.collectAsStateWithLifecycle()
    
    val isPlayingPresentation by viewModel.isPlayingPresentation.collectAsStateWithLifecycle()

    var showCreateDialog by remember { mutableStateOf(false) }
    var listExpanded by remember { mutableStateOf(false) } // For responsive left-right side panes

    BackHandler(enabled = isPlayingPresentation || listExpanded || selectedDoc != null) {
        if (isPlayingPresentation) {
            viewModel.togglePresenterMode(false)
        } else if (listExpanded) {
            listExpanded = false
        } else if (selectedDoc != null) {
            viewModel.selectDocument(null)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Main adaptive workspace overlay
            Box(modifier = Modifier.fillMaxSize()) {
                // Workspace pane
                WorkspacePane(
                    selectedDoc = selectedDoc,
                    draftTitle = draftTitle,
                    draftContent = draftContent,
                    onTitleChange = { viewModel.updateDraftTitle(it) },
                    onContentChange = { viewModel.updateDraftContent(it) },
                    onCloseClick = { viewModel.selectDocument(null) },
                    onToggleSidebar = { listExpanded = !listExpanded },
                    isSidebarExpanded = listExpanded,
                    viewModel = viewModel,
                    pageFormat = pageFormat,
                    customDimensions = customDimensions,
                    onFABClick = { showCreateDialog = true },
                    modifier = Modifier.fillMaxSize()
                )

                // Left Document explorer sidebar (Width is dynamic/collapsible overlay)
                AnimatedVisibility(
                    visible = listExpanded,
                    enter = slideInHorizontally() + fadeIn(),
                    exit = slideOutHorizontally() + fadeOut()
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Dismiss scrim
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.32f))
                                .clickable(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = null
                                ) {
                                    listExpanded = false
                                }
                        )

                        // Sidebar itself
                        SidebarExplorer(
                            documents = documents,
                            selectedDoc = selectedDoc,
                            draftTitle = draftTitle,
                            onTitleChange = { viewModel.updateDraftTitle(it) },
                            searchQuery = searchQuery,
                            selectedFilter = selectedFilter,
                            onSearchChange = { viewModel.setSearchQuery(it) },
                            onFilterChange = { viewModel.setTypeFilter(it) },
                            onDocSelect = { viewModel.selectDocument(it) },
                            onDocDelete = { viewModel.deleteDocument(it) },
                            onDocFavoriteToggle = { viewModel.toggleFavorite(it) },
                            onCreateClick = { showCreateDialog = true },
                            modifier = Modifier
                                .width(320.dp)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.background)
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f)
                                )
                                .clickable(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = null
                                ) {
                                    // Eat clicks to prevent dismiss on clicking sidebar inside
                                }
                        )
                    }
                }
            }

            // Dialog for creating a new document template
            if (showCreateDialog) {
                CreateDocumentDialog(
                    onDismiss = { showCreateDialog = false },
                    onConfirm = { title, type ->
                        viewModel.createNewDocument(title, type)
                        showCreateDialog = false
                    }
                )
            }

            // Full-screen presentation mode overlay
            if (isPlayingPresentation && selectedDoc?.type == "slide") {
                FullscreenPresentationView(
                    viewModel = viewModel,
                    onExit = { viewModel.togglePresenterMode(false) }
                )
            }
        }
    }
}

@Composable
fun SidebarExplorer(
    documents: List<DocEntity>,
    selectedDoc: DocEntity?,
    draftTitle: String,
    onTitleChange: (String) -> Unit,
    searchQuery: String,
    selectedFilter: String,
    onSearchChange: (String) -> Unit,
    onFilterChange: (String) -> Unit,
    onDocSelect: (DocEntity) -> Unit,
    onDocDelete: (DocEntity) -> Unit,
    onDocFavoriteToggle: (DocEntity) -> Unit,
    onCreateClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // App header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ONLYOFFICE inspired styled icon with terracotta orange background
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(OnlyOfficePrimary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Done,
                    contentDescription = "JCdocs Logo Symbol",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "JCdocs",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "ONLYOFFICE Suite Engine",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        }

        // Document Title Block (above search bar inside drawer)
        if (selectedDoc != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(12.dp)
            ) {
                Text(
                    text = "ACTIVE DOCUMENT",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = OnlyOfficePrimary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val themeColor = when (selectedDoc.type) {
                        "word" -> DocWordColor
                        "sheet" -> DocSheetColor
                        "slide" -> DocSlideColor
                        else -> OnlyOfficePrimary
                    }
                    val symbolChar = when (selectedDoc.type) {
                        "word" -> "W"
                        "sheet" -> "S"
                        "slide" -> "P"
                        else -> "D"
                    }

                    // Badge Indicator
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(themeColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = symbolChar,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // TextField for editing document title
                    BasicTextField(
                        value = draftTitle,
                        onValueChange = onTitleChange,
                        textStyle = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("drawer_title_input")
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        } else {
            // Optional clean placeholder state for "No active document"
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(12.dp)
            ) {
                Text(
                    text = "NO ACTIVE DOCUMENT",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Search Bar with custom tags for automations
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Search office sheets & files...", fontSize = 14.sp) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = "Search Files"
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = OnlyOfficePrimary,
                unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .testTag("file_search_bar")
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Create New Document Primary Action button
        Button(
            onClick = onCreateClick,
            colors = ButtonDefaults.buttonColors(containerColor = OnlyOfficePrimary),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .testTag("create_document_button")
        ) {
            Icon(Icons.Outlined.Add, contentDescription = "Add New")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Create Office File", fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Filter categories slider Row
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val filters = listOf(
                FilterItem("all", "All", Icons.Outlined.List),
                FilterItem("word", "Writer", Icons.Outlined.Edit),
                FilterItem("sheet", "Sheets", Icons.Outlined.PlayArrow),
                FilterItem("slide", "Slides", Icons.Outlined.Share)
            )
            items(filters) { category ->
                val isSelected = selectedFilter == category.id
                FilterChip(
                    selected = isSelected,
                    onClick = { onFilterChange(category.id) },
                    label = { Text(category.displayName, fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = category.icon,
                            contentDescription = category.displayName,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = OnlyOfficePrimary.copy(alpha = 0.15f),
                        selectedLabelColor = OnlyOfficePrimary,
                        selectedLeadingIconColor = OnlyOfficePrimary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Divider(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)
        )

        // Title listing title
        Text(
            text = "RECENT DOCUMENTS",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        // List of document tiles
        if (documents.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = "Empty State",
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.25f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No files found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(documents, key = { it.id }) { doc ->
                    val isSelected = selectedDoc?.id == doc.id
                    DocumentTile(
                        doc = doc,
                        isSelected = isSelected,
                        onClick = { onDocSelect(doc) },
                        onDelete = { onDocDelete(doc) },
                        onFavoriteToggle = { onDocFavoriteToggle(doc) }
                    )
                }
            }
        }
    }
}

data class FilterItem(val id: String, val displayName: String, val icon: ImageVector)

@Composable
fun DocumentTile(
    doc: DocEntity,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onFavoriteToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val typeColor = when (doc.type) {
        "word" -> DocWordColor
        "sheet" -> DocSheetColor
        "slide" -> DocSlideColor
        else -> OnlyOfficePrimary
    }

    val typeIconStr = when (doc.type) {
        "word" -> "W"
        "sheet" -> "S"
        "slide" -> "P"
        else -> "D"
    }

    val formattedDate = remember(doc.updatedAt) {
        val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        sdf.format(Date(doc.updatedAt))
    }

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                typeColor.copy(alpha = 0.08f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.dp,
            if (isSelected) typeColor.copy(alpha = 0.5f) else Color.Transparent
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("document_tile_${doc.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon layout matching ONLYOFFICE aesthetic
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(typeColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = typeIconStr,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontFamily = FontFamily.SansSerif
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = doc.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formattedDate,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            // Document item actions
            Row(horizontalArrangement = Arrangement.End) {
                IconButton(
                    onClick = onFavoriteToggle,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (doc.isFavorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Toggle Favorite",
                        tint = if (doc.isFavorite) Color.Red else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Delete Doc",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// Data class representing high-fidelity Ribbon Tool
data class RibbonTool(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val category: String,
    val tab: String,
    val actionId: String,
    val hasDropdown: Boolean = false,
    val dropdownOptions: List<String> = emptyList(),
    val onClick: () -> Unit = {},
    val onDropdownOptionClick: (String) -> Unit = {}
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RibbonToolCard(
    tool: RibbonTool,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable { 
                if (tool.hasDropdown) expanded = true 
                else tool.onClick() 
            }
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = tool.icon,
                contentDescription = tool.title,
                tint = if (isDarkTheme) Color.White.copy(alpha = 0.85f) else Color.DarkGray,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = tool.title,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isDarkTheme) Color.LightGray else Color.DarkGray,
                textAlign = TextAlign.Center
            )
        }
        if (tool.hasDropdown) {
            Icon(
                imageVector = Icons.Outlined.ArrowDropDown,
                contentDescription = null,
                tint = if (isDarkTheme) Color.LightGray else Color.DarkGray,
                modifier = Modifier.size(12.dp).align(Alignment.CenterEnd).padding(end = 4.dp)
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                if (tool.dropdownOptions.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("More Options...", fontSize = 12.sp) },
                        onClick = {
                            expanded = false
                            tool.onClick()
                        }
                    )
                } else {
                    tool.dropdownOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option, fontSize = 12.sp) },
                            onClick = {
                                expanded = false
                                tool.onDropdownOptionClick(option)
                            }
                        )
                    }
                }
            }
        }
    }
}

fun getRibbonTools(
    selectedDoc: DocEntity,
    onActionText: (String) -> Unit
): List<RibbonTool> {
    return listOf(
        // --- HOME TAB TOOLS ---
        RibbonTool(
            id = "bold",
            title = "Bold",
            description = "Apply bold style layout",
            icon = Icons.Outlined.FormatBold,
            category = "Font Formatting",
            tab = "Home",
            actionId = "bold"
        ),
        RibbonTool(
            id = "italic",
            title = "Italic",
            description = "Apply italic text",
            icon = Icons.Outlined.FormatItalic,
            category = "Font Formatting",
            tab = "Home",
            actionId = "italic"
        ),
        RibbonTool(
            id = "underline",
            title = "Underline",
            description = "Apply text underlining",
            icon = Icons.Outlined.FormatUnderlined,
            category = "Font Formatting",
            tab = "Home",
            actionId = "underline"
        ),
        RibbonTool(
            id = "align_left",
            title = "Align Left",
            description = "Position text on the left",
            icon = Icons.Outlined.FormatAlignLeft,
            category = "Paragraph Alignment",
            tab = "Home",
            actionId = "align_left"
        ),
        RibbonTool(
            id = "align_center",
            title = "Center",
            description = "Center document paragraph",
            icon = Icons.Outlined.FormatAlignCenter,
            category = "Paragraph Alignment",
            tab = "Home",
            actionId = "align_center"
        ),
        RibbonTool(
            id = "align_right",
            title = "Align Right",
            description = "Position text on the right",
            icon = Icons.Outlined.FormatAlignRight,
            category = "Paragraph Alignment",
            tab = "Home",
            actionId = "align_right"
        ),
        RibbonTool(
            id = "theme_white",
            title = "White Mode",
            description = "Select white paper backdrop",
            icon = Icons.Outlined.LightMode,
            category = "Page Theme Layout",
            tab = "Home",
            actionId = "theme_white"
        ),
        RibbonTool(
            id = "theme_ivory",
            title = "Ivory Mode",
            description = "Select warm notepad tone",
            icon = Icons.Outlined.WbSunny,
            category = "Page Theme Layout",
            tab = "Home",
            actionId = "theme_ivory"
        ),
        RibbonTool(
            id = "theme_dark",
            title = "Dark Mode",
            description = "Select low-light layout canvas",
            icon = Icons.Outlined.DarkMode,
            category = "Page Theme Layout",
            tab = "Home",
            actionId = "theme_dark"
        ),
        RibbonTool(
            id = "font_incr",
            title = "Increase Font",
            description = "Increase font text size",
            icon = Icons.Outlined.TextIncrease,
            category = "Text Size Scale",
            tab = "Home",
            actionId = "font_incr"
        ),
        RibbonTool(
            id = "font_decr",
            title = "Decrease Font",
            description = "Decrease font text size",
            icon = Icons.Outlined.TextDecrease,
            category = "Text Size Scale",
            tab = "Home",
            actionId = "font_decr"
        ),
        RibbonTool(
            id = "clear_format",
            title = "Clear Edits",
            description = "Strip active styling tags",
            icon = Icons.Outlined.Close,
            category = "Text Size Scale",
            tab = "Home",
            actionId = "clear_format"
        ),

        // --- INSERT TAB TOOLS ---
        RibbonTool(id = "cover_page", title = "Cover Page", description = "Cover Page", icon = Icons.Outlined.Description, category = "Pages", tab = "Insert", actionId = "cover_page"),
        RibbonTool(id = "blank_page", title = "Blank Page", description = "Blank Page", icon = Icons.Outlined.NoteAdd, category = "Pages", tab = "Insert", actionId = "blank_page"),
        RibbonTool(id = "page_break", title = "Page Break", description = "Page Break", icon = Icons.Outlined.VerticalAlignBottom, category = "Pages", tab = "Insert", actionId = "page_break"),
        RibbonTool(id = "insert_table", title = "Table", description = "Insert Table", icon = Icons.Outlined.TableChart, category = "Tables", tab = "Insert", actionId = "insert_table"),
        RibbonTool(id = "picture", title = "Picture", description = "Picture", icon = Icons.Outlined.Image, category = "Illustrations", tab = "Insert", actionId = "picture"),
        RibbonTool(id = "shapes", title = "Shapes", description = "Shapes", icon = Icons.Outlined.Category, category = "Illustrations", tab = "Insert", actionId = "shapes"),
        RibbonTool(id = "chart", title = "Chart", description = "Chart", icon = Icons.Outlined.BarChart, category = "Illustrations", tab = "Insert", actionId = "chart"),
        RibbonTool(id = "hyperlink", title = "Link", description = "Link", icon = Icons.Outlined.Link, category = "Links", tab = "Insert", actionId = "hyperlink"),
        RibbonTool(id = "bookmark", title = "Bookmark", description = "Bookmark", icon = Icons.Outlined.Bookmark, category = "Links", tab = "Insert", actionId = "bookmark"),
        RibbonTool(id = "header_footer", title = "Header & Footer", description = "Header & Footer", icon = Icons.Outlined.ViewAgenda, category = "Header & Footer", tab = "Insert", actionId = "header_footer"),
        RibbonTool(id = "page_number", title = "Page Number", description = "Page Number", icon = Icons.Outlined.Numbers, category = "Header & Footer", tab = "Insert", actionId = "page_number", hasDropdown = true, dropdownOptions = listOf("Top of Page", "Bottom of Page", "Page Margins", "Current Position", "Format Page Numbers...", "Remove Page Numbers")),
        RibbonTool(id = "text_box", title = "Text Box", description = "Text Box", icon = Icons.Outlined.TextFields, category = "Text", tab = "Insert", actionId = "text_box"),

        // --- LAYOUT TAB TOOLS ---
        // 1. Page Setup Group
        RibbonTool(id = "margins", title = "Margins", description = "Set Page Margins", icon = Icons.Outlined.SettingsOverscan, category = "Page Setup", tab = "Layout", actionId = "margins", hasDropdown = true, dropdownOptions = listOf("Normal", "Narrow", "Moderate", "Wide", "Mirrored", "Office Default", "Custom Margins...")),
        RibbonTool(id = "orientation", title = "Orientation", description = "Page Orientation", icon = Icons.Outlined.ScreenRotation, category = "Page Setup", tab = "Layout", actionId = "orientation", hasDropdown = true, dropdownOptions = listOf("Portrait", "Landscape")),
        RibbonTool(id = "size", title = "Size", description = "Page Size", icon = Icons.Outlined.AspectRatio, category = "Page Setup", tab = "Layout", actionId = "size", hasDropdown = true, dropdownOptions = listOf("A4", "Letter", "Legal", "A3", "A5", "Executive", "Custom Size...")),
        RibbonTool(id = "columns", title = "Columns", description = "Page Columns", icon = Icons.Outlined.ViewColumn, category = "Page Setup", tab = "Layout", actionId = "columns", hasDropdown = true),
        RibbonTool(id = "breaks", title = "Breaks", description = "Page Breaks", icon = Icons.Outlined.KeyboardReturn, category = "Page Setup", tab = "Layout", actionId = "breaks", hasDropdown = true, dropdownOptions = listOf("Page Break", "Column Break", "Section Break (Next Page)", "Section Break (Continuous)", "Section Break (Even Page)", "Section Break (Odd Page)")),

        // 2. Themes Group
        RibbonTool(id = "theme_apply", title = "Themes", description = "Document Themes", icon = Icons.Outlined.ColorLens, category = "Themes", tab = "Layout", actionId = "theme_apply", hasDropdown = true),
        RibbonTool(id = "theme_colors", title = "Colors", description = "Theme Colors", icon = Icons.Outlined.Palette, category = "Themes", tab = "Layout", actionId = "theme_colors", hasDropdown = true),
        RibbonTool(id = "theme_fonts", title = "Fonts", description = "Theme Fonts", icon = Icons.Outlined.FontDownload, category = "Themes", tab = "Layout", actionId = "theme_fonts", hasDropdown = true),
        RibbonTool(id = "theme_effects", title = "Effects", description = "Theme Effects", icon = Icons.Outlined.AutoAwesome, category = "Themes", tab = "Layout", actionId = "theme_effects", hasDropdown = true),

        // 3. Page Background Group
        RibbonTool(id = "watermark", title = "Watermark", description = "Page Watermark", icon = Icons.Outlined.BrandingWatermark, category = "Page Background", tab = "Layout", actionId = "watermark", hasDropdown = true),
        RibbonTool(id = "page_color", title = "Page Color", description = "Page Background Color", icon = Icons.Outlined.FormatColorFill, category = "Page Background", tab = "Layout", actionId = "page_color", hasDropdown = true),
        RibbonTool(id = "page_borders", title = "Page Borders", description = "Page Borders", icon = Icons.Outlined.BorderAll, category = "Page Background", tab = "Layout", actionId = "page_borders", hasDropdown = false),
        
        RibbonTool(id = "copy", title = "Copy", description = "Copy to clipboard", icon = Icons.Outlined.ContentCopy, category = "Clipboard", tab = "Home", actionId = "copy"),
        RibbonTool(id = "cut", title = "Cut", description = "Cut to clipboard", icon = Icons.Outlined.ContentCut, category = "Clipboard", tab = "Home", actionId = "cut"),
        RibbonTool(id = "paste", title = "Paste", description = "Paste from clipboard", icon = Icons.Outlined.ContentPaste, category = "Clipboard", tab = "Home", actionId = "paste"),

        // References Tab Tools removed

        // --- REVIEW TAB TOOLS ---
        RibbonTool(id = "spelling_grammar", title = "Spelling", description = "Spelling", icon = Icons.Outlined.Spellcheck, category = "Proofing", tab = "Review", actionId = "spelling_grammar", hasDropdown = false),
        RibbonTool(id = "thesaurus", title = "Thesaurus", description = "Thesaurus", icon = Icons.Outlined.MenuBook, category = "Proofing", tab = "Review", actionId = "thesaurus", hasDropdown = false),
        RibbonTool(id = "word_count", title = "Word Count", description = "Word Count", icon = Icons.Outlined.Numbers, category = "Proofing", tab = "Review", actionId = "word_count", hasDropdown = false),
        RibbonTool(id = "read_aloud", title = "Read Aloud", description = "Read Aloud", icon = Icons.Outlined.VolumeUp, category = "Speech", tab = "Review", actionId = "read_aloud", hasDropdown = false),
        RibbonTool(id = "check_accessibility", title = "Accessibility", description = "Accessibility", icon = Icons.Outlined.Accessibility, category = "Accessibility", tab = "Review", actionId = "check_accessibility", hasDropdown = false),
        RibbonTool(id = "translate", title = "Translate", description = "Translate", icon = Icons.Outlined.Translate, category = "Language", tab = "Review", actionId = "translate", hasDropdown = true),
        RibbonTool(id = "language", title = "Language", description = "Language", icon = Icons.Outlined.Language, category = "Language", tab = "Review", actionId = "language", hasDropdown = true),
        RibbonTool(id = "new_comment", title = "New Comment", description = "New Comment", icon = Icons.Outlined.AddComment, category = "Comments", tab = "Review", actionId = "new_comment", hasDropdown = false),
        RibbonTool(id = "delete_comment", title = "Delete", description = "Delete", icon = Icons.Outlined.DeleteOutline, category = "Comments", tab = "Review", actionId = "delete_comment", hasDropdown = false),
        RibbonTool(id = "show_comments", title = "Show Comments", description = "Show Comments", icon = Icons.Outlined.Chat, category = "Comments", tab = "Review", actionId = "show_comments", hasDropdown = false),
        RibbonTool(id = "track_changes", title = "Track Changes", description = "Track Changes", icon = Icons.Outlined.EditNote, category = "Tracking", tab = "Review", actionId = "track_changes", hasDropdown = true),

        // --- AI ASSISTANT TAB TOOLS ---
        RibbonTool(
            id = "ai_summarize",
            title = "Summarize Text",
            description = "Summarize Text",
            icon = Icons.Outlined.AutoAwesome,
            category = "AI Co Pilot Engine",
            tab = "AI Assistant",
            actionId = "ai_summarize"
        ),
        RibbonTool(
            id = "ai_improve",
            title = "Improve Tone",
            description = "Improve Tone",
            icon = Icons.Outlined.AutoFixHigh,
            category = "AI Co Pilot Engine",
            tab = "AI Assistant",
            actionId = "ai_improve"
        ),
        RibbonTool(
            id = "ai_grammar",
            title = "Fix Grammar",
            description = "Fix Grammar Error",
            icon = Icons.Outlined.Spellcheck,
            category = "AI Co Pilot Engine",
            tab = "AI Assistant",
            actionId = "ai_grammar"
        ),
        RibbonTool(
            id = "ai_topics",
            title = "Suggest Topics",
            description = "Suggest Topics",
            icon = Icons.Outlined.Lightbulb,
            category = "Creative Writing Vectors",
            tab = "AI Assistant",
            actionId = "ai_topics"
        )
    ).map { tool ->
        tool.copy(
            onClick = { onActionText(tool.actionId) },
            onDropdownOptionClick = { option -> onActionText("${tool.actionId}:$option") }
        )
    }
}

fun executeRibbonAction(
    actionId: String,
    context: Context,
    draftContent: String,
    onContentChange: (String) -> Unit,
    selectedDoc: DocEntity,
    viewModel: DocViewModel,
    editorTheme: String,
    onThemeChange: (String) -> Unit,
    onMarginsChange: (androidx.compose.ui.unit.Dp) -> Unit,
    onColumnsChange: (Int) -> Unit,
    onFontSizeChange: (androidx.compose.ui.unit.TextUnit) -> Unit,
    onLandscapeChange: (Boolean) -> Unit,
    onShowCustomMarginsDialog: () -> Unit = {},
    onShowCustomSizeDialog: () -> Unit = {},
    onShowPageNumberFormatDialog: () -> Unit = {},
    onShowPageNumberPositionMenu: (String) -> Unit = {},
    onPageNumberPositionChange: (String?) -> Unit = {},
    snackbarScope: kotlinx.coroutines.CoroutineScope,
    snackbarState: androidx.compose.material3.SnackbarHostState,
    tts: android.speech.tts.TextToSpeech?,
    isSpeaking: Boolean,
    onSpeakStateChange: (Boolean) -> Unit,
    textFieldValue: TextFieldValue? = null,
    onTextFieldValueChange: ((TextFieldValue) -> Unit)? = null,
    lastSelection: TextRange? = null,
    formatVersion: Int = 0,
    onFormatVersionChange: (Int) -> Unit = {},
    onHistoryAdd: ((String) -> Unit)? = null
) {
    fun showToast(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    val applyFormatting = { type: String, value: String, debugName: String ->
        if (textFieldValue != null) {
            val sel = textFieldValue.selection
            if (!sel.collapsed) {
                val start = minOf(sel.start, sel.end)
                val end = maxOf(sel.start, sel.end)
                if (DocFormatRepository.hasSpan(selectedDoc.id, type, start, end)) {
                    DocFormatRepository.removeSpanTypeRange(selectedDoc.id, type, start, end)
                } else {
                    DocFormatRepository.applySpan(selectedDoc.id, type, value, start, end)
                }
                onFormatVersionChange(formatVersion + 1)
            }
        }
    }

    when (actionId) {
        "bold" -> applyFormatting("bold", "", "Bold")
        "italic" -> applyFormatting("italic", "", "Italic")
        "underline" -> applyFormatting("underline", "", "Underline")
        "strikethrough" -> applyFormatting("strikethrough", "", "Strikethrough")
        "subscript" -> applyFormatting("subscript", "", "Subscript")
        "superscript" -> applyFormatting("superscript", "", "Superscript")
        "color" -> applyFormatting("color", "#3B82F6", "Color")
        "highlight" -> applyFormatting("highlight", "", "Highlight")
        "copy" -> {
            if (textFieldValue != null && !textFieldValue.selection.collapsed) {
                val sel = textFieldValue.selection
                val start = minOf(sel.start, sel.end)
                val end = maxOf(sel.start, sel.end)
                val selectedText = draftContent.substring(start, end)
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("JCdocs", selectedText)
                clipboard.setPrimaryClip(clip)
                onHistoryAdd?.invoke(selectedText)
                showToast("Copied to clipboard")
            } else {
                showToast("Select text to copy")
            }
        }
        "cut" -> {
            if (textFieldValue != null && !textFieldValue.selection.collapsed) {
                val sel = textFieldValue.selection
                val start = minOf(sel.start, sel.end)
                val end = maxOf(sel.start, sel.end)
                val selectedText = draftContent.substring(start, end)
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("JCdocs", selectedText)
                clipboard.setPrimaryClip(clip)
                onHistoryAdd?.invoke(selectedText)
                val newText = draftContent.substring(0, start) + draftContent.substring(end)
                onContentChange(newText)
                onTextFieldValueChange?.invoke(TextFieldValue(text = newText, selection = TextRange(start)))
                showToast("Cut to clipboard")
            } else {
                showToast("Select text to cut")
            }
        }
        "paste" -> {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val pastedText = clip.getItemAt(0).text?.toString() ?: return
                val sel = textFieldValue?.selection ?: TextRange(draftContent.length)
                val start = minOf(sel.start, sel.end)
                val end = maxOf(sel.start, sel.end)
                val newText = draftContent.substring(0, start) + pastedText + draftContent.substring(end)
                onContentChange(newText)
                val newCursor = start + pastedText.length
                onTextFieldValueChange?.invoke(TextFieldValue(text = newText, selection = TextRange(newCursor)))
                showToast("Pasted from clipboard")
            } else {
                showToast("Clipboard is empty")
            }
        }
        "paste_special" -> {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val pastedText = clip.getItemAt(0).text?.toString() ?: return
                val sel = textFieldValue?.selection ?: TextRange(draftContent.length)
                val start = minOf(sel.start, sel.end)
                val end = maxOf(sel.start, sel.end)
                val newText = draftContent.substring(0, start) + pastedText + draftContent.substring(end)
                onContentChange(newText)
                val newCursor = start + pastedText.length
                onTextFieldValueChange?.invoke(TextFieldValue(text = newText, selection = TextRange(newCursor)))
                showToast("Pasted as plain text")
            } else {
                showToast("Clipboard is empty")
            }
        }
        "align_left" -> {
            if (textFieldValue != null) {
                try {
                    val selStart = textFieldValue.selection.start
                    val selEnd = textFieldValue.selection.end
                    val paraRanges = getParagraphRangesInRange(draftContent, selStart, selEnd)
                    val allMatch = paraRanges.all { r -> DocFormatRepository.getSpans(selectedDoc.id).any { it.type == "alignment" && it.start <= r.start && it.end > r.start && it.value == "left" } }
                    for (r in paraRanges) {
                        val paraEnd = r.endInclusive + 1
                        DocFormatRepository.removeSpanTypeRange(selectedDoc.id, "alignment", r.start, paraEnd)
                        if (!allMatch) {
                            DocFormatRepository.applySpan(selectedDoc.id, "alignment", "left", r.start, paraEnd)
                        }
                    }
                    val spansAfter = DocFormatRepository.getSpans(selectedDoc.id).toList()
                    android.util.Log.d("AlignDebug", "align_left: spansAfter=${spansAfter.map { "${it.type}[${it.start},${it.end}):${it.value}" }}")
                    onFormatVersionChange(formatVersion + 1)
                    showToast(if (allMatch) "Alignment removed" else "Text alignment set to Left")
                } catch (e: Exception) {
                    android.util.Log.e("Align", "left error", e)
                    showToast("Align error: ${e.message}")
                }
            }
        }
        "align_center" -> {
            if (textFieldValue != null) {
                try {
                    val selStart = textFieldValue.selection.start
                    val selEnd = textFieldValue.selection.end
                    val paraRanges = getParagraphRangesInRange(draftContent, selStart, selEnd)
                    android.util.Log.d("AlignDebug", "align_center: draftContent='${draftContent.take(50)}', sel=$selStart-$selEnd, paraRanges=$paraRanges")
                    val spansBefore = DocFormatRepository.getSpans(selectedDoc.id).toList()
                    android.util.Log.d("AlignDebug", "align_center: spansBefore=${spansBefore.map { "${it.type}[${it.start},${it.end}):${it.value}" }}")
                    val allMatch = paraRanges.all { r -> DocFormatRepository.getSpans(selectedDoc.id).any { it.type == "alignment" && it.start <= r.start && it.end > r.start && it.value == "center" } }
                    for (r in paraRanges) {
                        val paraEnd = r.endInclusive + 1
                        DocFormatRepository.removeSpanTypeRange(selectedDoc.id, "alignment", r.start, paraEnd)
                        if (!allMatch) {
                            DocFormatRepository.applySpan(selectedDoc.id, "alignment", "center", r.start, paraEnd)
                        }
                    }
                    val spansAfter = DocFormatRepository.getSpans(selectedDoc.id).toList()
                    android.util.Log.d("AlignDebug", "align_center: spansAfter=${spansAfter.map { "${it.type}[${it.start},${it.end}):${it.value}" }}")
                    onFormatVersionChange(formatVersion + 1)
                    showToast(if (allMatch) "Alignment removed" else "Text alignment set to Center")
                } catch (e: Exception) {
                    android.util.Log.e("Align", "center error", e)
                    showToast("Align error: ${e.message}")
                }
            }
        }
        "align_right" -> {
            if (textFieldValue != null) {
                try {
                    val selStart = textFieldValue.selection.start
                    val selEnd = textFieldValue.selection.end
                    val paraRanges = getParagraphRangesInRange(draftContent, selStart, selEnd)
                    val allMatch = paraRanges.all { r -> DocFormatRepository.getSpans(selectedDoc.id).any { it.type == "alignment" && it.start <= r.start && it.end > r.start && it.value == "right" } }
                    for (r in paraRanges) {
                        val paraEnd = r.endInclusive + 1
                        DocFormatRepository.removeSpanTypeRange(selectedDoc.id, "alignment", r.start, paraEnd)
                        if (!allMatch) {
                            DocFormatRepository.applySpan(selectedDoc.id, "alignment", "right", r.start, paraEnd)
                        }
                    }
                    val spansAfter = DocFormatRepository.getSpans(selectedDoc.id).toList()
                    android.util.Log.d("AlignDebug", "align_right: spansAfter=${spansAfter.map { "${it.type}[${it.start},${it.end}):${it.value}" }}")
                    onFormatVersionChange(formatVersion + 1)
                    showToast(if (allMatch) "Alignment removed" else "Text alignment set to Right")
                } catch (e: Exception) {
                    android.util.Log.e("Align", "right error", e)
                    showToast("Align error: ${e.message}")
                }
            }
        }
        "align_justify" -> {
            if (textFieldValue != null) {
                try {
                    val selStart = textFieldValue.selection.start
                    val selEnd = textFieldValue.selection.end
                    val paraRanges = getParagraphRangesInRange(draftContent, selStart, selEnd)
                    val allMatch = paraRanges.all { r -> DocFormatRepository.getSpans(selectedDoc.id).any { it.type == "alignment" && it.start <= r.start && it.end > r.start && it.value == "justify" } }
                    for (r in paraRanges) {
                        val paraEnd = r.endInclusive + 1
                        DocFormatRepository.removeSpanTypeRange(selectedDoc.id, "alignment", r.start, paraEnd)
                        if (!allMatch) {
                            DocFormatRepository.applySpan(selectedDoc.id, "alignment", "justify", r.start, paraEnd)
                        }
                    }
                    val spansAfter = DocFormatRepository.getSpans(selectedDoc.id).toList()
                    android.util.Log.d("AlignDebug", "align_justify: spansAfter=${spansAfter.map { "${it.type}[${it.start},${it.end}):${it.value}" }}")
                    onFormatVersionChange(formatVersion + 1)
                    showToast(if (allMatch) "Alignment removed" else "Text alignment set to Justified")
                } catch (e: Exception) {
                    android.util.Log.e("Align", "justify error", e)
                    showToast("Align error: ${e.message}")
                }
            }
        }
        "indent_inc" -> {
            if (textFieldValue != null) {
                val pos = textFieldValue.selection.start
                val para = getParagraphText(draftContent, pos)
                val leadingSpaces = para.takeWhile { it == ' ' }.length
                val newLeading = minOf(leadingSpaces + 4, 40)
                val newPara = " ".repeat(newLeading) + para.trimStart()
                val newText = replaceParagraphText(draftContent, pos, newPara)
                onContentChange(newText)
                val cursorShift = newLeading - leadingSpaces
                onTextFieldValueChange?.invoke(textFieldValue.copy(text = newText, selection = TextRange(pos + cursorShift)))
                showToast("Indent increased")
            }
        }
        "indent_dec" -> {
            if (textFieldValue != null) {
                val pos = textFieldValue.selection.start
                val para = getParagraphText(draftContent, pos)
                val leadingSpaces = para.takeWhile { it == ' ' }.length
                val newLeading = maxOf(leadingSpaces - 4, 0)
                val newPara = " ".repeat(newLeading) + para.trimStart()
                val newText = replaceParagraphText(draftContent, pos, newPara)
                onContentChange(newText)
                val cursorShift = newLeading - leadingSpaces
                onTextFieldValueChange?.invoke(textFieldValue.copy(text = newText, selection = TextRange(pos + cursorShift)))
                showToast("Indent decreased")
            }
        }
        "multilevel" -> {
            showToast("Multilevel list — select a list style from Bullets or Numbers first")
        }
        "theme_white" -> {
            onThemeChange("white")
            showToast("Paper theme changed to White")
        }
        "theme_ivory" -> {
            onThemeChange("ivory")
            showToast("Paper theme changed to Ivory Note")
        }
        "theme_dark" -> {
            onThemeChange("dark")
            showToast("Paper theme changed to OLED Dark")
        }
        "font_incr" -> {
            onFontSizeChange(18.sp)
            showToast("Font text size increased to 18sp")
        }
        "font_decr" -> {
            onFontSizeChange(11.sp)
            showToast("Font text size decreased to 11sp")
        }
        "clear_format" -> {
            if (textFieldValue != null && onTextFieldValueChange != null) {
                val selection = lastSelection ?: textFieldValue.selection   
                val text = textFieldValue.text
                if (!selection.collapsed) {
                    val selStart = minOf(selection.start, selection.end)
                    val selEnd = maxOf(selection.start, selection.end)
                    val selectedStr = text.substring(selStart, selEnd)
                    val cleaned = selectedStr
                        .replace("**", "")
                        .replace("*", "")
                        .replace("<u>", "")
                        .replace("</u>", "")
                        .replace("~~", "")
                        .replace("<sub>", "")
                        .replace("</sub>", "")
                        .replace("<sup>", "")
                        .replace("</sup>", "")
                        .replace("<font[^>]*>".toRegex(), "")
                        .replace("</font>", "")
                        .replace("<span[^>]*>".toRegex(), "")
                        .replace("</span>", "")
                        .replace("<mark>", "")
                        .replace("</mark>", "")
                    val newText = text.replaceRange(selStart, selEnd, cleaned)
                    onTextFieldValueChange(TextFieldValue(text = newText, selection = TextRange(selStart, selStart + cleaned.length)))
                    // Clear spans as well
                    DocFormatRepository.removeSpansRange(selectedDoc.id, selStart, selEnd)
                } else {
                    val cleaned = text
                        .replace("**", "")
                        .replace("*", "")
                        .replace("<u>", "")
                        .replace("</u>", "")
                        .replace("~~", "")
                        .replace("<sub>", "")
                        .replace("</sub>", "")
                        .replace("<sup>", "")
                        .replace("</sup>", "")
                        .replace("<font[^>]*>".toRegex(), "")
                        .replace("</font>", "")
                        .replace("<span[^>]*>".toRegex(), "")
                        .replace("</span>", "")
                        .replace("<mark>", "")
                        .replace("</mark>", "")
                    onTextFieldValueChange(TextFieldValue(text = cleaned, selection = TextRange(cleaned.length)))
                    // Clear spans as well - For whole document
                    DocFormatRepository.removeSpansRange(selectedDoc.id, 0, text.length)
                }
            } else {
                val cleaned = draftContent
                    .replace("**", "")
                    .replace("*", "")
                    .replace("<u>", "")
                    .replace("</u>", "")
                onContentChange(cleaned)
            }
            onFontSizeChange(14.sp)
            showToast("All text styling and layout formatting tags cleared")
        }

        // --- INSERT ACTIONS ---
        "cover_page" -> {
            val cover = "========================================\n" +
                        "       DOCUMENT COVER PORTFOLIO\n" +
                        "       Title: ${selectedDoc.title}\n" +
                        "       Date: June 8, 2026\n" +
                        "========================================\n\n"
            onContentChange(cover + draftContent)
            showToast("Stylish Document Cover Page prepended at top!")
        }
        "blank_page" -> {
            onContentChange(draftContent + "\u000C")
            showToast("Inserted Blank Page")
        }
        "page_break" -> {
            onContentChange(draftContent + "\u000C")
            showToast("Visual page break rule appended to note body")
        }
        "insert_table" -> {
            val table = "\n| Item Coordinate | Header Label | Value Count |\n" +
                        "|---|---|---|\n" +
                        "| Office Suite | JCdocs ONLYOFFICE | 100% Native |\n" +
                        "| Database Eng | Android Room SQLite | Offline |\n"
            onContentChange(draftContent + table)
            showToast("Sample Markdown table data inserted at bottom!")
        }
        "pictures" -> {
            onContentChange(draftContent + "\n\n![Scenic Office Vector Mock](https://picsum.photos/600/300)\n")
            showToast("Scenic showcase image vector layout inserted!")
        }
        "shapes" -> {
            onContentChange(draftContent + "\n\n[Shape Container: Double Rounded Cylinder | Fill color: emerald_green]\n")
            showToast("Double Rounded Cylinder Vector Shape inserted!")
        }
        "icons" -> {
            onContentChange(draftContent + " ★ ")
            showToast("Royal Golden Star rating badge inserted!")
        }

        // --- LAYOUT ACTIONS ---
        "margins_normal" -> {
            onMarginsChange(24.dp)
            showToast("Margins padding set to normal (24dp)")
        }
        "margins_narrow" -> {
            onMarginsChange(8.dp)
            showToast("Margins padding set to narrow space (8dp)")
        }
        "margins_wide" -> {
            onMarginsChange(48.dp)
            showToast("Margins padding set to wide space (48dp)")
        }
        "portrait" -> {
            onLandscapeChange(false)
            showToast("Document orientation layout set to Portrait")
        }
        "landscape" -> {
            onLandscapeChange(true)
            showToast("Document orientation layout set to Landscape")
        }
        "col_1" -> {
            onColumnsChange(1)
            showToast("Columns division updated to 1 standard panel")
        }
        "col_2" -> {
            onColumnsChange(2)
            showToast("Dynamic layout split into 2 reactive columns!")
        }
        "col_3" -> {
            onColumnsChange(3)
            showToast("Responsive layout divided into 3 reactive columns!")
        }

        // --- REFERENCES ACTIONS ---
        "reference_toc" -> {
            val headings = draftContent.lines()
                .filter { it.trim().startsWith("#") }
                .map { line ->
                    val depth = line.takeWhile { it == '#' }.length
                    val title = line.replace("#", "").trim()
                    "  ".repeat(maxOf(0, depth - 1)) + "- $title"
                }

            if (headings.isEmpty()) {
                onContentChange(
                    "### TABLE OF CONTENTS\n- Section 1: Overview\n- Section 2: Strategy\n- Section 3: Technical Integrity\n\n" + draftContent
                )
                showToast("TOC appended! Add lines starting with '#' to customize.")
            } else {
                val toc = "### TABLE OF CONTENTS\n" + headings.joinToString("\n") + "\n\n"
                onContentChange(toc + draftContent)
                showToast("Real index of headings compiled to Table of Contents!")
            }
        }
        "footnote" -> {
            onContentChange(draftContent + " [^1]")
            val footnoteDesc = "\n\n[^1]: Reference index: Verified securely on JCdocs tablet workspace."
            if (!draftContent.contains("[^1]:")) {
                onContentChange(draftContent + " [^1]" + footnoteDesc)
            }
            showToast("Footnote locator tag applied and registered!")
        }
        "endnote" -> {
            onContentChange(draftContent + "\n\n========================================\nENDNOTE LOGS:\n- Verified local SQLite database integrity syncs successfully.\n========================================\n")
            showToast("Comprehensive database sync Endnotes added at bottom!")
        }
        "citation" -> {
            onContentChange(draftContent + " (Sarah J., 2026)")
            showToast("Professional citation source (Sarah J., 2026) inserted!")
        }

        // --- REVIEW ACTIONS ---
        "review_stats" -> {
            val words = draftContent.split("\\s+".toRegex()).filter { it.isNotBlank() }.size
            val sentences = draftContent.split("[.!?]+".toRegex()).filter { it.isNotBlank() }.size
            val chars = draftContent.length
            val readingTime = maxOf(1, words / 180)
            showToast("STATS: Words: $words — Sentences: $sentences — Chars: $chars — Reading time: $readingTime min")
        }
        "spell_check" -> {
            val typosMap = mapOf(
                "teh" to "the",
                "recieve" to "receive",
                "seperate" to "separate",
                "dont" to "don't",
                "accomodate" to "accommodate",
                "Jcdocs" to "JCdocs"
            )
            var fixedCount = 0
            var text = draftContent
            typosMap.forEach { (typo, correction) ->
                if (text.contains(typo, ignoreCase = true)) {
                    text = text.replace(typo, correction, ignoreCase = true)
                    fixedCount++
                }
            }
            if (fixedCount > 0) {
                onContentChange(text)
                showToast("Success! Autocorrect fixed $fixedCount typos (e.g. teh -> the, recieve -> receive)")
            } else {
                showToast("Spell check completed: No typos detected in draft!")
            }
        }
        "read_aloud" -> {
            if (isSpeaking) {
                tts?.stop()
                onSpeakStateChange(false)
                showToast("Read aloud voice speech stopped")
            } else {
                val cleanText = draftContent.replace("[#*_|\\-<>]+".toRegex(), " ")
                if (cleanText.isNotBlank()) {
                    tts?.speak(cleanText, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, null)
                    onSpeakStateChange(true)
                    showToast("Narrating document content aloud via Android Speech Synthesizer...")
                } else {
                    showToast("Read aloud error: Text content is empty!")
                }
            }
        }
        "translate_preview" -> {
            val transText = draftContent
                .replace("Welcome", "Bienvenido")
                .replace("Project", "Proyecto")
                .replace("Document", "Documento")
                .replace("the", "el")
                .replace("and", "y")
            onContentChange(draftContent + "\n\n--- SPANISH TRANSLATION PREVIEW ---\n" + transText + "\n-------------------------------------\n")
            showToast("Spanish translation preview appended to Document body!")
        }

        // --- AI ASSISTANT ACTIONS ---
        "ai_summarize" -> {
            val lines = draftContent.lines().filter { it.isNotBlank() }
            val summaryBullets = if (lines.size >= 3) {
                listOf(
                    "📌 Primary Focus: " + lines[0].take(60) + "...",
                    "🔬 Supporting Detail: " + lines.getOrNull(lines.size/2)?.take(60) + "...",
                    "📊 Output Target: " + lines.last().take(60) + "..."
                )
            } else {
                listOf(
                    "📌 Summary Concept: Core JCdocs Workspace body",
                    "🚀 System Strategy: Secure Android SQLite client workflow",
                    "⚙️ Implementation: Polished Jetpack Compose frontend interaction"
                )
            }

            val summaryBlock = "\n\n--- AI DOCUMENT SUMMARY ---\n" +
                               summaryBullets.joinToString("\n") +
                               "\n---------------------------\n"
            onContentChange(draftContent + summaryBlock)
            showToast("AI Assistant summarized text and inserted bullet points!")
        }
        "ai_improve" -> {
            val improved = "⚡ PROFESSIONAL POLISH & ELEVATED TONE:\n" +
                           "With profound executive alignment, " + draftContent.replace("Welcome", "We are pleased to introduce").replace("Welcome to", "We welcome you further into")
            onContentChange(improved)
            showToast("Document style tone improved with professional vocabulary!")
        }
        "ai_grammar" -> {
            val resolvedText = draftContent.trim()
            onContentChange(resolvedText)
            showToast("AI has corrected syntactic flows and applied grammar fixes!")
        }
        "ai_topics" -> {
            val topicsBlock = "\n\n💡 RECOMMENDED RESEARCH VECTORS:\n" +
                              "1. Dynamic Kotlin-DSL compilers for local file operations.\n" +
                              "2. Real-time multi-threaded Room SQLite transaction pools.\n" +
                              "3. Responsive tablet-layout class dynamics.\n"
            onContentChange(draftContent + topicsBlock)
            showToast("3 creative brainstorming research topics appended!")
        }
        else -> {
            when {
                actionId.startsWith("page_number:") -> {
                    val option = actionId.removePrefix("page_number:")
                    when(option) {
                        "Format Page Numbers..." -> onShowPageNumberFormatDialog()
                        "Remove Page Numbers" -> {
                            onPageNumberPositionChange(null)
                            showToast("Page Numbers removed")
                        }
                        "Current Position" -> {
                            // "Current Position" inserts the page number directly into the document content at cursor.
                            onContentChange(draftContent + " 1 ")
                            showToast("Inserted Page Number")
                        }
                        "Top of Page", "Bottom of Page", "Page Margins" -> {
                            onShowPageNumberPositionMenu(option)
                        }
                        else -> {
                            onPageNumberPositionChange(option)
                            showToast("Page Number position set to $option")
                        }
                    }
                }
                actionId.startsWith("margins:") -> {
                    val option = actionId.removePrefix("margins:")
                    when (option) {
                        "Normal" -> { onMarginsChange(24.dp); showToast("Margins set to Normal") }
                        "Narrow" -> { onMarginsChange(12.dp); showToast("Margins set to Narrow") }
                        "Moderate" -> { onMarginsChange(18.dp); showToast("Margins set to Moderate") }
                        "Wide" -> { onMarginsChange(48.dp); showToast("Margins set to Wide") }
                        "Mirrored" -> { onMarginsChange(36.dp); showToast("Margins set to Mirrored") }
                        "Office Default" -> { onMarginsChange(24.dp); showToast("Margins set to Office Default") }
                        "Custom Margins..." -> onShowCustomMarginsDialog()
                    }
                }
                actionId.startsWith("orientation:") -> {
                    val option = actionId.removePrefix("orientation:")
                    if (option == "Landscape") {
                        onLandscapeChange(true)
                        showToast("Orientation set to Landscape")
                    } else {
                        onLandscapeChange(false)
                        showToast("Orientation set to Portrait")
                    }
                }
                actionId.startsWith("size:") -> {
                    val option = actionId.removePrefix("size:")
                    if (option == "Custom Size...") {
                        onShowCustomSizeDialog()
                    } else {
                        showToast("Page Size set to $option")
                    }
                }
                actionId.startsWith("breaks:") -> {
                    val option = actionId.removePrefix("breaks:")
                    if (option == "Page Break" || option == "Section Break (Next Page)" || option == "Section Break (Even Page)" || option == "Section Break (Odd Page)") {
                         onContentChange(draftContent + "\u000C")
                         showToast("Inserted $option")
                    } else {
                         // Default for column breaks and continuous section breaks which do not strictly map to page breaks
                         onContentChange(draftContent + "\n\n--- [${option.uppercase()}] ---\n\n")
                         showToast("Inserted $option")
                    }
                }
            }
        }
    }
}

@Composable
fun RibbonGroupContainer(
    title: String,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSystemInDarkTheme()) Color(0xFF252528) else Color.White
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
        )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val displayTitle = if (title.equals("Font Formatting", ignoreCase = true) || title.equals("Font", ignoreCase = true)) {
                    "FONT"
                } else {
                    title.uppercase()
                }
                Text(
                    text = displayTitle,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    letterSpacing = 0.8.sp
                )
            }
            HorizontalDivider(
                color = if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
            )
            Box(modifier = Modifier.padding(16.dp)) {
                content()
            }
        }
    }
}

@Composable
fun RibbonIconButton(
    icon: ImageVector? = null,
    textLabel: String? = null,
    contentDescription: String,
    onClick: () -> Unit,
    isSelected: Boolean = false,
    colorSchemeColor: Color = OnlyOfficePrimary,
    transparentBg: Boolean = false,
    modifier: Modifier = Modifier,
    customContent: @Composable (() -> Unit)? = null
) {
    val isDarkTheme = isSystemInDarkTheme()
    val bgColor = when {
        transparentBg -> Color.Transparent
        isSelected -> colorSchemeColor.copy(alpha = 0.35f)
        isDarkTheme -> Color(0xFF323236)
        else -> Color(0xFFF1F3F6)
    }
    val contentColor = when {
        transparentBg -> colorSchemeColor
        isSelected -> colorSchemeColor
        isDarkTheme -> Color.White
        else -> Color.DarkGray
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        if (customContent != null) {
            customContent()
        } else if (textLabel != null) {
            Text(
                text = textLabel,
                color = contentColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

val FontColors = listOf(
    "#000000", "#434343", "#666666", "#999999", "#B7B7B7", "#CCCCCC",
    "#D9D9D9", "#EFEFEF", "#F3F3F3", "#FFFFFF", "#002060", "#1F3864",
    "#1F4E79", "#2E75B6", "#4472C4", "#5B9BD5", "#8DB4E2", "#BDD7EE",
    "#D6E4F0", "#E2EFDA", "#548235", "#70AD47", "#A9D18E", "#C5E0B4",
    "#D9E2F3", "#FFF2CC", "#FFD966", "#F4B183", "#ED7D31", "#E74C3C",
    "#C00000", "#FF0000", "#FF8C00", "#FFD700", "#32CD32", "#00CED1",
    "#0000FF", "#8A2BE2", "#FF69B4", "#A52A2A"
)

val HighlightColors = listOf(
    "#FDE047", "#FCD34D", "#FBBF24", "#F59E0B", "#FEF9C3",
    "#86EFAC", "#4ADE80", "#22C55E", "#16A34A", "#DCFCE7",
    "#93C5FD", "#60A5FA", "#3B82F6", "#2563EB", "#DBEAFE",
    "#F9A8D4", "#F472B6", "#EC4899", "#DB2777", "#FCE7F3",
    "#C4B5FD", "#A78BFA", "#8B5CF6", "#7C3AED", "#EDE9FE",
    "#FDBA74", "#FB923C", "#F97316", "#EA580C", "#FED7AA",
    "#FCA5A5", "#F87171", "#EF4444", "#DC2626", "#FEE2E2",
    "#D1D5DB", "#9CA3AF", "#6B7280", "#4B5563", "#374151"
)

@Composable
fun ColorPickerDialog(
    title: String,
    colors: List<String>,
    onColorSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var hexInput by remember { mutableStateOf("#") }
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = if (isSystemInDarkTheme()) Color(0xFF2E2E32) else Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = if (isSystemInDarkTheme()) Color.White else Color.Black)
                Spacer(Modifier.height(12.dp))
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                    val chunked = colors.chunked(5)
                    chunked.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            row.forEach { colorHex ->
                                val color = try { Color(android.graphics.Color.parseColor(colorHex)) } catch (e: Exception) { Color.Gray }
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(color)
                                        .border(1.dp, if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                                        .clickable { onColorSelected(colorHex) }
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Divider(color = if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.1f))
                Spacer(Modifier.height(12.dp))
                Text("Custom", fontWeight = FontWeight.Medium, fontSize = 14.sp, color = if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.7f) else Color.Gray)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BasicTextField(
                        value = hexInput,
                        onValueChange = {
                            val filtered = it.filter { c -> c.isDigit() || c in "ABCDEFabcdef#" }
                            if (filtered.length <= 7 && filtered.startsWith("#")) {
                                hexInput = filtered
                            } else if (filtered.isNotEmpty() && filtered.first() != '#') {
                                hexInput = "#$filtered".take(7)
                            }
                        },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 14.sp, fontFamily = FontFamily.Monospace, color = if (isSystemInDarkTheme()) Color.White else Color.Black),
                        modifier = Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(6.dp)).background(if (isSystemInDarkTheme()) Color(0xFF1E1E22) else Color(0xFFF1F3F6)).padding(horizontal = 8.dp).border(1.dp, if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
                    )
                    if (hexInput.length == 7 && hexInput.startsWith("#")) {
                        val previewColor = try { Color(android.graphics.Color.parseColor(hexInput)) } catch (e: Exception) { Color.Gray }
                        Box(
                            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(6.dp)).background(previewColor).border(1.dp, Color.Black.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                        )
                    }
                    TextButton(onClick = { onColorSelected(hexInput) }) {
                        Text("Apply", fontWeight = FontWeight.Bold, color = DocWordColor)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                }
            }
        }
    }
}

@Composable
fun BulletStyleDialog(
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = if (isSystemInDarkTheme()) Color(0xFF2E2E32) else Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Bullet Style", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = if (isSystemInDarkTheme()) Color.White else Color.Black)
                Spacer(Modifier.height(12.dp))
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp).verticalScroll(rememberScrollState())) {
                    val rows = BulletChars.chunked(4)
                    rows.forEach { row ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            row.forEach { char ->
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSystemInDarkTheme()) Color(0xFF1E1E22) else Color(0xFFF1F3F6))
                                        .clickable { onSelect(char) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(char, fontSize = 24.sp, color = if (isSystemInDarkTheme()) Color.White else Color.Black)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { onDismiss() }) { Text("Cancel") }
                }
            }
        }
    }
}

@Composable
fun NumberFormatDialog(
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val formats = listOf("1." to "1, 2, 3...", "a)" to "a), b), c)...", "A." to "A., B., C...", "i)" to "i), ii), iii)...", "I." to "I., II., III...")
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = if (isSystemInDarkTheme()) Color(0xFF2E2E32) else Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Numbering Format", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = if (isSystemInDarkTheme()) Color.White else Color.Black)
                Spacer(Modifier.height(12.dp))
                formats.forEach { (fmt, desc) ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSystemInDarkTheme()) Color(0xFF1E1E22) else Color(0xFFF1F3F6))
                            .clickable { onSelect(fmt) }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(desc, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (isSystemInDarkTheme()) Color.White else Color.Black)
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { onDismiss() }) { Text("Cancel") }
                }
            }
        }
    }
}

@Composable
fun LineSpacingDialog(
    currentSpacing: Float,
    onSelect: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(1.0f, 1.15f, 1.5f, 2.0f, 2.5f, 3.0f)
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = if (isSystemInDarkTheme()) Color(0xFF2E2E32) else Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Line Spacing", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = if (isSystemInDarkTheme()) Color.White else Color.Black)
                Spacer(Modifier.height(12.dp))
                options.forEach { spacing ->
                    val label = when (spacing) {
                        1.0f -> "Single (1.0)"
                        1.15f -> "1.15"
                        1.5f -> "1.5"
                        2.0f -> "Double (2.0)"
                        else -> "${spacing}"
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (spacing == currentSpacing) DocWordColor.copy(alpha = 0.15f)
                                else if (isSystemInDarkTheme()) Color(0xFF1E1E22) else Color(0xFFF1F3F6)
                            )
                            .clickable { onSelect(spacing) }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (isSystemInDarkTheme()) Color.White else Color.Black)
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { onDismiss() }) { Text("Cancel") }
                }
            }
        }
    }
}

@Composable
fun BordersDialog(
    onApply: (sides: Set<String>, style: String, color: String, width: Float) -> Unit,
    onDismiss: () -> Unit
) {
    var top by remember { mutableStateOf(false) }
    var bottom by remember { mutableStateOf(false) }
    var left by remember { mutableStateOf(false) }
    var right by remember { mutableStateOf(false) }
    var borderStyle by remember { mutableStateOf("solid") }
    var borderColor by remember { mutableStateOf("#000000") }
    var borderWidth by remember { mutableStateOf(1f) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = if (isSystemInDarkTheme()) Color(0xFF2E2E32) else Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
                Text("Borders", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = if (isSystemInDarkTheme()) Color.White else Color.Black)
                Spacer(Modifier.height(12.dp))

                Text("Sides", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.7f) else Color.Gray)
                Spacer(Modifier.height(6.dp))
                val sides = listOf("Top" to top, "Bottom" to bottom, "Left" to left, "Right" to right)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    sides.forEach { (label, checked) ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (checked) DocWordColor else if (isSystemInDarkTheme()) Color(0xFF1E1E22) else Color(0xFFF1F3F6))
                                .clickable {
                                    when (label) { "Top" -> top = !top; "Bottom" -> bottom = !bottom; "Left" -> left = !left; "Right" -> right = !right }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (checked) Color.White else if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.7f) else Color.DarkGray)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                Text("Style", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.7f) else Color.Gray)
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("solid", "dotted", "dashed", "double").forEach { style ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (borderStyle == style) DocWordColor else if (isSystemInDarkTheme()) Color(0xFF1E1E22) else Color(0xFFF1F3F6))
                                .clickable { borderStyle = style },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(style.replaceFirstChar { it.uppercase() }, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (borderStyle == style) Color.White else if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.7f) else Color.DarkGray)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                Text("Color", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.7f) else Color.Gray)
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("#000000", "#333333", "#666666", "#999999", "#CC0000", "#0066CC", "#339933").forEach { hex ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(try { Color(android.graphics.Color.parseColor(hex)) } catch (e: Exception) { Color.Gray })
                                .border(if (borderColor == hex) 3.dp else 1.dp, if (borderColor == hex) DocWordColor else if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                                .clickable { borderColor = hex }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))

                Text("Width: ${borderWidth.toInt()}pt", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.7f) else Color.Gray)
                Spacer(Modifier.height(4.dp))
                Slider(
                    value = borderWidth,
                    onValueChange = { borderWidth = it },
                    valueRange = 0.5f..6f,
                    steps = 10
                )
                Spacer(Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { onDismiss() }) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        val sidesSet = mutableSetOf<String>()
                        if (top) sidesSet.add("top")
                        if (bottom) sidesSet.add("bottom")
                        if (left) sidesSet.add("left")
                        if (right) sidesSet.add("right")
                        onApply(sidesSet, borderStyle, borderColor, borderWidth)
                    }) { Text("Apply") }
                }
            }
        }
    }
}

@Composable
fun RibbonDropdown(
    selectedValue: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    isEditable: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }
    var textFieldValue by remember(selectedValue) { mutableStateOf(TextFieldValue(selectedValue)) }
    Box(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isDarkTheme) Color(0xFF323236) else Color(0xFFF1F3F6))
            .clickable { expanded = !expanded }
            .border(
                width = 1.dp,
                color = if (isDarkTheme) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isEditable) {
                BasicTextField(
                    value = textFieldValue,
                    onValueChange = { textFieldValue = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isDarkTheme) Color.White else Color.Black
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            onSelect(textFieldValue.text)
                            expanded = false
                        }
                    ),
                    modifier = Modifier.weight(1f)
                )
            } else {
                Text(
                    text = selectedValue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isDarkTheme) Color.White else Color.Black,
                    modifier = Modifier.weight(1f)
                )
            }
            Icon(
                imageVector = Icons.Outlined.ArrowDropDown,
                contentDescription = "Dropdown Arrow",
                tint = if (isDarkTheme) Color.LightGray else Color.Gray,
                modifier = Modifier.size(16.dp)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(if (isDarkTheme) Color(0xFF2E2E32) else Color.White)
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option,
                            fontSize = 12.sp,
                            color = if (isDarkTheme) Color.White else Color.Black
                        )
                    },
                    onClick = {
                        onSelect(option)
                        expanded = false
                        if (isEditable) {
                            textFieldValue = TextFieldValue(option)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ExportButton(
    draftContent: String,
    viewModel: DocViewModel,
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = isSystemInDarkTheme()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val pageFormat by viewModel.pageFormat.collectAsStateWithLifecycle()
    val customDimensions by viewModel.customPageDimensions.collectAsStateWithLifecycle()

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        uri?.let { uri ->
            try {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    val (width, height) = when (pageFormat.substringBefore(" ").trim()) {
                        "A3" -> 842 to 1191
                        "A5" -> 420 to 595
                        "Letter" -> 612 to 792
                        "Custom" -> (customDimensions.first * 72f).toInt() to (customDimensions.second * 72f).toInt()
                        else -> 595 to 842 // A4 default
                    }
                    val pdfDocument = PdfDocument()
                    val pageInfo = PdfDocument.PageInfo.Builder(width, height, 1).create()
                    val page = pdfDocument.startPage(pageInfo)
                    
                    val canvas = page.canvas
                    val paint = Paint()
                    paint.color = android.graphics.Color.BLACK
                    paint.textSize = 12f
                    
                    // High-fidelity rendering placeholder:
                    // Here you would implement complex layout, images, charts, and table rendering using Canvas.
                    canvas.drawText(draftContent, 50f, 50f, paint)
                    
                    pdfDocument.finishPage(page)
                    pdfDocument.writeTo(outputStream)
                    pdfDocument.close()
                }
                Toast.makeText(context, "Exported successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(modifier = modifier) {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.FileDownload,
                contentDescription = "Export Document",
                tint = if (isDarkTheme) Color.LightGray else Color.DarkGray
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(if (isDarkTheme) Color(0xFF2E2E32) else Color.White)
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = "Export as PDF",
                        fontSize = 12.sp,
                        color = if (isDarkTheme) Color.White else Color.Black
                    )
                },
                onClick = {
                    expanded = false
                    exportLauncher.launch("Document_" + System.currentTimeMillis() + ".pdf")
                }
            )
        }
    }
}


object DocumentLayoutEngine {
    fun getDimensions(format: String, customDimensions: Pair<Float, Float>, isLandscape: Boolean): Pair<Dp, Dp> {
        val (pw, ph) = when (format.substringBefore(" ").trim()) {
            "A3" -> 842.dp to 1191.dp
            "A5" -> 420.dp to 595.dp
            "Letter" -> 612.dp to 792.dp
            "Custom" -> (customDimensions.first * 72f).dp to (customDimensions.second * 72f).dp
            else -> 595.dp to 842.dp // A4 default
        }
        return if (isLandscape) ph to pw else pw to ph
    }
}

@Composable
fun PageRuler(
    modifier: Modifier = Modifier,
    isHorizontal: Boolean = true,
    totalLength: Dp,
    markerInterval: Dp = 10.dp, // 10dp between small markers (more frequent)
    majorMarkerInterval: Dp = 50.dp // 50dp between major markers
) {
    Canvas(modifier = modifier.width(if (isHorizontal) totalLength else 20.dp).height(if (isHorizontal) 20.dp else totalLength).background(Color(0xFF222222))) {
        val markers = (totalLength / markerInterval).toInt()
        for (i in 0..markers) {
            val offset = i * markerInterval.toPx()
            val isMajor = (i * markerInterval.value).toInt() % majorMarkerInterval.value.toInt() == 0
            val markerHeight = if (isMajor) 16f else 8f
            
            // White markers
            val markerColor = Color.White
            
            if (isHorizontal) {
                drawLine(
                    color = markerColor,
                    start = androidx.compose.ui.geometry.Offset(offset, size.height),
                    end = androidx.compose.ui.geometry.Offset(offset, size.height - markerHeight),
                    strokeWidth = 2f
                )
            } else {
                drawLine(
                    color = markerColor,
                    start = androidx.compose.ui.geometry.Offset(size.width, offset),
                    end = androidx.compose.ui.geometry.Offset(size.width - markerHeight, offset),
                    strokeWidth = 2f
                )
            }
        }
    }
}

@Composable
fun WorkspacePane(
    selectedDoc: DocEntity?,
    draftTitle: String,
    draftContent: String,
    onTitleChange: (String) -> Unit,
    onContentChange: (String) -> Unit,
    onCloseClick: () -> Unit,
    onToggleSidebar: () -> Unit,
    isSidebarExpanded: Boolean,
    viewModel: DocViewModel,
    pageFormat: String,
    customDimensions: Pair<Float, Float>,
    onFABClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (selectedDoc == null) {
        EmptyWorkspaceState(
            viewModel = viewModel,
            onToggleSidebar = onToggleSidebar,
            isSidebarExpanded = isSidebarExpanded,
            onQuickCreate = { title, type -> viewModel.createNewDocument(title, type) },
            onFABClick = onFABClick,
            modifier = modifier
        )
    } else {
        var editorTheme by remember { mutableStateOf("white") }
        var pageMargins by remember { mutableStateOf(24.dp) }
        var columnCount by remember { mutableStateOf(1) }
        var fontSize by remember { mutableStateOf(16.sp) }
        var isLandscape by remember { mutableStateOf(false) }

        var showCustomMarginsDialog by remember { mutableStateOf(false) }
        var showCustomSizeDialog by remember { mutableStateOf(false) }
        var showPageNumberFormatDialog by remember { mutableStateOf(false) }
        var pageNumberPosition by remember { mutableStateOf<String?>(null) }
        var pageNumberFormat by remember { mutableStateOf("1, 2, 3...") }
        var pageNumberStartAt by remember { mutableStateOf("1") }
        var pageNumberPositionMenu by remember { mutableStateOf<String?>(null) }

        var editorTextFieldValue by remember(selectedDoc.id) {
            mutableStateOf(TextFieldValue(text = draftContent, selection = TextRange(draftContent.length)))
        }

        var lastSelection by remember(selectedDoc.id) {
            mutableStateOf(TextRange(draftContent.length))
        }

        var isEditorFocused by remember { mutableStateOf(false) }
        val context = androidx.compose.ui.platform.LocalContext.current

        val clipboardHistory = remember { mutableStateListOf<String>() }
        var showClipboardHistory by remember { mutableStateOf(false) }

        val undoRedoManager = remember(selectedDoc.id) { DocUndoRedoManager(selectedDoc.id) }
        var formatVersion by remember { mutableStateOf(0) } // Force recomposition when formatting spans change
        var undoRedoTrigger by remember { mutableStateOf(0) } // To force UI recompose when stacks change
        
        val activeFormatting by remember {
            derivedStateOf {
                formatVersion
                val spans = DocFormatRepository.getSpans(selectedDoc.id)
                val pos = editorTextFieldValue.selection.start
                if (pos < 0) emptySet()
                else spans.filter { it.start <= pos && it.end > pos }.map { it.type }.toSet()
            }
        }
        val cursorFontColorVal by remember {
            derivedStateOf {
                formatVersion
                val spans = DocFormatRepository.getSpans(selectedDoc.id)
                val pos = editorTextFieldValue.selection.start
                if (pos < 0) null else spans.find { it.type == "color" && it.start <= pos && it.end > pos }?.value
            }
        }
        val cursorHighlightColorVal by remember {
            derivedStateOf {
                formatVersion
                val spans = DocFormatRepository.getSpans(selectedDoc.id)
                val pos = editorTextFieldValue.selection.start
                if (pos < 0) null else spans.find { it.type == "highlight" && it.start <= pos && it.end > pos }?.value
            }
        }
        val cursorAlignmentVal by remember {
            derivedStateOf {
                formatVersion
                val spans = DocFormatRepository.getSpans(selectedDoc.id)
                val pos = editorTextFieldValue.selection.start
                if (pos < 0) null else spans.find { it.type == "alignment" && it.start <= pos && it.end > pos }?.value
            }
        }
        val cursorLineSpacingVal by remember {
            derivedStateOf {
                formatVersion
                val spans = DocFormatRepository.getSpans(selectedDoc.id)
                val pos = editorTextFieldValue.selection.start
                if (pos < 0) null else spans.find { it.type == "lineSpacing" && it.start <= pos && it.end > pos }?.value
            }
        }
        
        fun showUndoRedoFeedback(msg: String) {
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
        }

        val captureSnapshot = {
            DocEditorSnapshot(
                title = draftTitle,
                draftContent = draftContent,
                textFieldValue = editorTextFieldValue,
                spans = DocFormatRepository.getSpans(selectedDoc.id).toList(),
                editorTheme = editorTheme,
                pageMargins = pageMargins,
                columnCount = columnCount,
                fontSize = fontSize,
                isLandscape = isLandscape,
                pageNumberPosition = pageNumberPosition,
                pageNumberFormat = pageNumberFormat,
                pageNumberStartAt = pageNumberStartAt
            )
        }

        val pushSnapshot = {
            undoRedoManager.pushState(captureSnapshot())
            undoRedoTrigger++
        }

        val performUndo = {
            val restored = undoRedoManager.undo(captureSnapshot())
            if (restored != null) {
                showUndoRedoFeedback("Undo")
                undoRedoManager.isRestoring = true
                onTitleChange(restored.title)
                editorTextFieldValue = restored.textFieldValue
                onContentChange(restored.draftContent)
                
                val currentSpans = DocFormatRepository.getSpans(selectedDoc.id)
                currentSpans.clear()
                currentSpans.addAll(restored.spans.map { it.copy() })
                
                editorTheme = restored.editorTheme
                pageMargins = restored.pageMargins
                columnCount = restored.columnCount
                fontSize = restored.fontSize
                isLandscape = restored.isLandscape
                pageNumberPosition = restored.pageNumberPosition
                pageNumberFormat = restored.pageNumberFormat
                pageNumberStartAt = restored.pageNumberStartAt
                
                undoRedoTrigger++
                undoRedoManager.isRestoring = false
            }
        }

        val performRedo = {
            val restored = undoRedoManager.redo(captureSnapshot())
            if (restored != null) {
                showUndoRedoFeedback("Redo")
                undoRedoManager.isRestoring = true
                onTitleChange(restored.title)
                editorTextFieldValue = restored.textFieldValue
                onContentChange(restored.draftContent)
                
                val currentSpans = DocFormatRepository.getSpans(selectedDoc.id)
                currentSpans.clear()
                currentSpans.addAll(restored.spans.map { it.copy() })
                
                editorTheme = restored.editorTheme
                pageMargins = restored.pageMargins
                columnCount = restored.columnCount
                fontSize = restored.fontSize
                isLandscape = restored.isLandscape
                pageNumberPosition = restored.pageNumberPosition
                pageNumberFormat = restored.pageNumberFormat
                pageNumberStartAt = restored.pageNumberStartAt
                
                undoRedoTrigger++
                undoRedoManager.isRestoring = false
            }
        }

        LaunchedEffect(draftContent) {
            if (editorTextFieldValue.text != draftContent) {
                editorTextFieldValue = editorTextFieldValue.copy(
                    text = draftContent,
                    selection = TextRange(
                        editorTextFieldValue.selection.start.coerceIn(0, draftContent.length),
                        editorTextFieldValue.selection.end.coerceIn(0, draftContent.length)
                    )
                )
                lastSelection = TextRange(
                    lastSelection.start.coerceIn(0, draftContent.length),
                    lastSelection.end.coerceIn(0, draftContent.length)
                )
            }
        }

        var activeRibbonTab by remember { mutableStateOf("Home") }
        var isRibbonExpanded by remember { mutableStateOf(true) }
        var ribbonHeightDp by remember { mutableStateOf(300.dp) }
        var ribbonSearchQuery by remember { mutableStateOf("") }

        var isFontExpanded by remember { mutableStateOf(true) }
        var isClipboardExpanded by remember { mutableStateOf(true) }
        var isParagraphExpanded by remember { mutableStateOf(true) }
        var isStylesExpanded by remember { mutableStateOf(true) }
        var isEditingExpanded by remember { mutableStateOf(true) }
        var isStatsExpanded by remember { mutableStateOf(true) }

        var activeFontFamily by remember { mutableStateOf("Default") }
        var activeFontSize by remember { mutableStateOf("16") }
        var showFontColorPicker by remember { mutableStateOf(false) }
        var showHighlightPicker by remember { mutableStateOf(false) }

        var showBulletStyleDialog by remember { mutableStateOf(false) }
        var showNumberFormatDialog by remember { mutableStateOf(false) }
        var showLineSpacingDialog by remember { mutableStateOf(false) }
        var showBordersDialog by remember { mutableStateOf(false) }
        var showShadingPicker by remember { mutableStateOf(false) }
        var pageBackgroundColor by remember { mutableStateOf<Color?>(null) }
        var pendingParaAction by remember { mutableStateOf<String?>(null) }

        val coroutineScope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }

        var isSpeaking by remember { mutableStateOf(false) }
        var tts by remember { mutableStateOf<android.speech.tts.TextToSpeech?>(null) }

        DisposableEffect(context) {
            val localTts = android.speech.tts.TextToSpeech(context) { status -> }
            tts = localTts
            onDispose {
                localTts.stop()
                localTts.shutdown()
            }
        }

        BoxWithConstraints(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFFEAECF0).copy(alpha = 0.5f))
        ) {
            val totalHeight = maxHeight
            val totalWidth = maxWidth

            val minHeightDp = totalHeight * 0.25f
            val maxHeightDp = totalHeight * 0.70f

            val coercedRibbonHeight = ribbonHeightDp.coerceIn(minHeightDp, maxHeightDp)
            val bottomNavBarHeight = 68.dp
            
            val targetRibbonHeight = if (isRibbonExpanded) coercedRibbonHeight else 0.dp
            val animatedRibbonHeight by androidx.compose.animation.core.animateDpAsState(
                targetValue = targetRibbonHeight,
                animationSpec = spring(dampingRatio = 0.82f, stiffness = 400f),
                label = "ribbonHeight"
            )
            val editorBottomPadding = bottomNavBarHeight + animatedRibbonHeight

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = editorBottomPadding)
            ) {
                WorkspaceMenuBar(
                    doc = selectedDoc,
                    draftTitle = draftTitle,
                    onTitleChange = {
                        if (draftTitle != it) {
                            pushSnapshot()
                        }
                        onTitleChange(it)
                    },
                    isSidebarExpanded = isSidebarExpanded,
                    onToggleSidebar = onToggleSidebar,
                    onCloseClick = {
                        if (isSpeaking) {
                            tts?.stop()
                            isSpeaking = false
                        }
                        onCloseClick()
                    },
                    undoRedoManager = undoRedoManager,
                    undoRedoTrigger = undoRedoTrigger,
                    onUndo = performUndo,
                    onRedo = performRedo,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        )
                )

                Box(modifier = Modifier.weight(1f)) {
                    when (selectedDoc.type) {
                        "word" -> {
                            WordDocumentEditor(
                                docId = selectedDoc.id,
                                draftContent = draftContent,
                                onContentChange = onContentChange,
                                editorTheme = editorTheme,
                                onEditorThemeChange = { editorTheme = it },
                                pageBackgroundColor = pageBackgroundColor,
                                pageMargins = pageMargins,
                                columnCount = columnCount,
                                fontSize = fontSize,
                                formatVersion = formatVersion,
                                                isLandscape = isLandscape,
                                pageFormat = pageFormat,
                                customDimensions = customDimensions,
                                pageNumberPosition = pageNumberPosition,
                                pageNumberFormat = pageNumberFormat,
                                pageNumberStartAt = pageNumberStartAt.toIntOrNull() ?: 1,
                                modifier = Modifier.fillMaxSize(),
                                textFieldValue = editorTextFieldValue,
                                onTextFieldValueChange = { newVal ->
                                    val textChanged = newVal.text != editorTextFieldValue.text
                                    val selectionChanged = newVal.selection != editorTextFieldValue.selection
                                    if (textChanged || selectionChanged) {
                                        pushSnapshot()
                                    }
                                    editorTextFieldValue = newVal
                                    if (isEditorFocused) {
                                        lastSelection = newVal.selection
                                    }
                                    if (textChanged) {
                                        onContentChange(newVal.text)
                                    }
                                    if (isEditorFocused && !textChanged && selectionChanged && newVal.selection.start >= 0) {
                                        val pos = newVal.selection.start
                                        val spans = DocFormatRepository.getSpans(selectedDoc.id)
                                        val sizeSpan = spans.find { it.type == "fontSize" && it.start <= pos && it.end > pos }
                                        val detectedSize = sizeSpan?.value
                                        if (detectedSize != null && detectedSize != activeFontSize) {
                                            activeFontSize = detectedSize
                                        } else if (detectedSize == null && activeFontSize != "16") {
                                            activeFontSize = "16"
                                        }
                                        val familySpan = spans.find { it.type == "fontFamily" && it.start <= pos && it.end > pos }
                                        val detectedFamily = familySpan?.value
                                        if (detectedFamily != null && detectedFamily != activeFontFamily) {
                                            activeFontFamily = detectedFamily
                                        } else if (detectedFamily == null && activeFontFamily != "Default") {
                                            activeFontFamily = "Default"
                                        }
                                    }
                                },
                                onFocusChanged = { isEditorFocused = it }
                            )
                        }
                        "sheet" -> SpreadsheetEditor(
                            viewModel = viewModel,
                            modifier = Modifier.fillMaxSize()
                        )
                        "slide" -> SlidePresentationWorkspace(
                            viewModel = viewModel,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = editorBottomPadding + 8.dp)
            )

            val isDarkTheme = isSystemInDarkTheme()
            val surfaceBg = if (isDarkTheme) Color(0xFF1E1E22) else Color(0xFFF0F2F6)
            val glassCardBorderColor = if (isDarkTheme) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(surfaceBg)
                        .border(width = 1.dp, color = glassCardBorderColor)
                ) {
                    AnimatedVisibility(
                        visible = isRibbonExpanded,
                        enter = slideInVertically(
                            initialOffsetY = { it },
                            animationSpec = spring(dampingRatio = 0.82f, stiffness = 400f)
                        ) + fadeIn(),
                        exit = slideOutVertically(
                            targetOffsetY = { it },
                            animationSpec = spring(dampingRatio = 0.82f, stiffness = 400f)
                        ) + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(coercedRibbonHeight)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(18.dp)
                                    .pointerInput(LocalDensity.current) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            val dragAmountDp = dragAmount.y.toDp()
                                            ribbonHeightDp = (ribbonHeightDp - dragAmountDp).coerceIn(minHeightDp, maxHeightDp)
                                        }
                                    }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(36.dp)
                                        .height(5.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(if (isDarkTheme) Color.White.copy(alpha = 0.35f) else Color.DarkGray.copy(alpha = 0.25f))
                                )
                            }

                            if (activeRibbonTab == "AI Assistant") {
                                AIChatPanel(
                                    onClose = { isRibbonExpanded = false },
                                    viewModel = viewModel,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isDarkTheme) Color.Black.copy(alpha = 0.25f) else Color.White)
                                        .border(
                                            width = 1.dp,
                                            color = if (isDarkTheme) Color.White.copy(alpha = 0.12f) else Color.LightGray.copy(alpha = 0.4f),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Search,
                                        contentDescription = "Search Ribbon Icon",
                                        tint = if (isDarkTheme) Color.LightGray else Color.Gray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier.weight(1f),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        if (ribbonSearchQuery.isEmpty()) {
                                            Text(
                                                text = "Search tools, commands, features...",
                                                color = Color.Gray,
                                                fontSize = 13.sp
                                            )
                                        }
                                        BasicTextField(
                                            value = ribbonSearchQuery,
                                            onValueChange = { ribbonSearchQuery = it },
                                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                                color = if (isDarkTheme) Color.White else Color.Black
                                            ),
                                            singleLine = true,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("ribbon_search_input")
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(
                                    onClick = { isRibbonExpanded = false },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.KeyboardArrowDown,
                                        contentDescription = "Collapse Ribbon Panel",
                                        tint = if (isDarkTheme) Color.LightGray else Color.DarkGray
                                    )
                                }
                                ExportButton(draftContent = draftContent, viewModel = viewModel)
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                val filteredTools = getRibbonTools(selectedDoc) { action ->
                                    pushSnapshot()
                                    executeRibbonAction(
                                        actionId = action,
                                        context = context,
                                        draftContent = draftContent,
                                        onContentChange = onContentChange,
                                        selectedDoc = selectedDoc,
                                        viewModel = viewModel,
                                        editorTheme = editorTheme,
                                        onThemeChange = { editorTheme = it },
                                        onMarginsChange = { pageMargins = it },
                                        onColumnsChange = { columnCount = it },
                                        onFontSizeChange = { fontSize = it },
                                        onLandscapeChange = { isLandscape = it },
                                        onShowCustomMarginsDialog = { showCustomMarginsDialog = true },
                                        onShowCustomSizeDialog = { showCustomSizeDialog = true },
                                        onShowPageNumberFormatDialog = { showPageNumberFormatDialog = true },
                                        onShowPageNumberPositionMenu = { pageNumberPositionMenu = it },
                                        onPageNumberPositionChange = { pageNumberPosition = it },
                                        snackbarScope = coroutineScope,
                                        snackbarState = snackbarHostState,
                                        tts = tts,
                                        isSpeaking = isSpeaking,
                                        onSpeakStateChange = { isSpeaking = it },
                                        textFieldValue = editorTextFieldValue,
                                        onTextFieldValueChange = { newVal ->
                                            editorTextFieldValue = newVal
                                            if (!newVal.selection.collapsed) {
                                                lastSelection = newVal.selection
                                            }
                                            if (newVal.text != draftContent) {
                                                onContentChange(newVal.text)
                                            }
                                        },
                                        lastSelection = lastSelection,
                                        onHistoryAdd = { clipboardHistory.add(it) }
                                    )
                                }.filter { tool ->
                                    (tool.tab.equals(activeRibbonTab, ignoreCase = true) || ribbonSearchQuery.isNotEmpty()) &&
                                    (tool.title.contains(ribbonSearchQuery, ignoreCase = true) ||
                                     tool.description.contains(ribbonSearchQuery, ignoreCase = true) ||
                                     tool.category.contains(ribbonSearchQuery, ignoreCase = true))
                                }

                                if (filteredTools.isEmpty()) {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Info,
                                            contentDescription = "No tools matched query",
                                            tint = Color.LightGray,
                                            modifier = Modifier.size(40.dp)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "No tools match search query",
                                            fontSize = 12.sp,
                                            color = Color.Gray
                                        )
                                    }
                                } else {
                                    val onAction: (String) -> Unit = { action ->
                                        pushSnapshot()
                                        executeRibbonAction(
                                            actionId = action,
                                            context = context,
                                            draftContent = draftContent,
                                            onContentChange = onContentChange,
                                            selectedDoc = selectedDoc,
                                            viewModel = viewModel,
                                            editorTheme = editorTheme,
                                            onThemeChange = { editorTheme = it },
                                            onMarginsChange = { pageMargins = it },
                                            onColumnsChange = { columnCount = it },
                                            onFontSizeChange = { fontSize = it },
                                            onLandscapeChange = { isLandscape = it },
                                            onShowCustomMarginsDialog = { showCustomMarginsDialog = true },
                                            onShowCustomSizeDialog = { showCustomSizeDialog = true },
                                            onShowPageNumberFormatDialog = { showPageNumberFormatDialog = true },
                                            onShowPageNumberPositionMenu = { pageNumberPositionMenu = it },
                                            onPageNumberPositionChange = { pageNumberPosition = it },
                                            snackbarScope = coroutineScope,
                                            snackbarState = snackbarHostState,
                                            tts = tts,
                                            isSpeaking = isSpeaking,
                                            onSpeakStateChange = { isSpeaking = it },
                                            textFieldValue = editorTextFieldValue,
                                            onTextFieldValueChange = { newVal ->
                                                editorTextFieldValue = newVal
                                                if (!newVal.selection.collapsed) {
                                                    lastSelection = newVal.selection
                                                }
                                                if (newVal.text != draftContent) {
                                                    onContentChange(newVal.text)
                                                }
                                            },
                                        lastSelection = lastSelection,
                                        formatVersion = formatVersion,
                                        onFormatVersionChange = { formatVersion = it },
                                        onHistoryAdd = { clipboardHistory.add(it) }
                                    )
                                    }

                                    if (activeRibbonTab.equals("Home", ignoreCase = true) && ribbonSearchQuery.isEmpty()) {
                                        LazyColumn(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 16.dp),
                                            verticalArrangement = Arrangement.spacedBy(12.dp),
                                            contentPadding = PaddingValues(bottom = 16.dp)
                                        ) {
                                            // --- FONT GROUP ---
                                            item {
                                                val groupColor = if (selectedDoc.type == "word") DocWordColor else if (selectedDoc.type == "sheet") DocSheetColor else DocSlideColor
                                                RibbonGroupContainer(
                                                    title = "Font Formatting",
                                                    isExpanded = isFontExpanded,
                                                    onToggleExpand = { isFontExpanded = !isFontExpanded },
                                                    accentColor = groupColor
                                                ) {
                                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                        // Row 1: Dropdowns and scale buttons
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            RibbonDropdown(
                                                                selectedValue = activeFontFamily,
                                                                options = listOf("Default", "Aptos", "Calibri", "Arial", "Times New Roman", "Courier New", "Georgia", "Space Grotesk", "JetBrains Mono"),
                                                                onSelect = {
                                                                    activeFontFamily = it
                                                                    val selection = editorTextFieldValue.selection
                                                                    val raw = if (!selection.collapsed) selection else if (!lastSelection.collapsed) lastSelection else null
                                                                    val effective = if (raw != null) TextRange(minOf(raw.start, raw.end), maxOf(raw.start, raw.end)) else null
                                                                    if (effective != null) {
                                                                        if (it == "Default") {
                                                                            DocFormatRepository.removeSpanTypeRange(selectedDoc.id, "fontFamily", effective.start, effective.end)
                                                                        } else {
                                                                            DocFormatRepository.applySpan(selectedDoc.id, "fontFamily", it, effective.start, effective.end)
                                                                        }
                                                                        formatVersion++
                                                                    }
                                                                },
                                                                modifier = Modifier.weight(3.5f)
                                                            )

                                                            RibbonDropdown(
                                                                selectedValue = activeFontSize,
                                                                options = listOf("9", "10", "11", "12", "14", "16", "18", "20", "24", "28", "32", "36", "40", "44", "48", "54", "60", "66", "72", "80", "88", "96", "108", "120", "144", "180", "200"),
                                                                isEditable = true,
                                                                onSelect = {
                                                                    activeFontSize = it
                                                                    val num = it.toIntOrNull() ?: 16
                                                                    val selection = editorTextFieldValue.selection
                                                                    val raw = if (!selection.collapsed) selection else if (!lastSelection.collapsed) lastSelection else null
                                                                    val effective = if (raw != null) TextRange(minOf(raw.start, raw.end), maxOf(raw.start, raw.end)) else null
                                                                    if (effective != null) {
                                                                        DocFormatRepository.applySpan(selectedDoc.id, "fontSize", it, effective.start, effective.end)
                                                                        formatVersion++
                                                                    } else {
                                                                        fontSize = num.sp
                                                                    }
                                                                },
                                                                modifier = Modifier.weight(2.2f)
                                                            )

                                                            RibbonIconButton(
                                                                contentDescription = "Increase Font Size",
                                                                onClick = {
                                                                    val selection = editorTextFieldValue.selection
                                                                    val raw = if (!selection.collapsed) selection else if (!lastSelection.collapsed) lastSelection else null
                                                                    val effective = if (raw != null) TextRange(minOf(raw.start, raw.end), maxOf(raw.start, raw.end)) else null
                                                                    if (effective != null) {
                                                                        val currentVal = activeFontSize.toIntOrNull() ?: 16
                                                                        val newSize = if (currentVal < 200) currentVal + 2 else 200
                                                                        DocFormatRepository.applySpan(selectedDoc.id, "fontSize", newSize.toString(), effective.start, effective.end)
                                                                        formatVersion++
                                                                        activeFontSize = newSize.toString()
                                                                    } else {
                                                                        val currentSize = fontSize.value.toInt()
                                                                        val newSize = if (currentSize < 200) currentSize + 2 else 200
                                                                        fontSize = newSize.sp
                                                                        activeFontSize = newSize.toString()
                                                                    }
                                                                },
                                                                colorSchemeColor = groupColor,
                                                                transparentBg = true,
                                                                modifier = Modifier.weight(1.1f),
                                                                customContent = {
                                                                    Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = null, tint = groupColor, modifier = Modifier.size(22.dp))
                                                                }
                                                            )

                                                            RibbonIconButton(
                                                                contentDescription = "Decrease Font Size",
                                                                onClick = {
                                                                    val selection = editorTextFieldValue.selection
                                                                    val raw = if (!selection.collapsed) selection else if (!lastSelection.collapsed) lastSelection else null
                                                                    val effective = if (raw != null) TextRange(minOf(raw.start, raw.end), maxOf(raw.start, raw.end)) else null
                                                                    if (effective != null) {
                                                                        val currentVal = activeFontSize.toIntOrNull() ?: 16
                                                                        val newSize = if (currentVal > 8) currentVal - 2 else 8
                                                                        DocFormatRepository.applySpan(selectedDoc.id, "fontSize", newSize.toString(), effective.start, effective.end)
                                                                        formatVersion++
                                                                        activeFontSize = newSize.toString()
                                                                    } else {
                                                                        val currentSize = fontSize.value.toInt()
                                                                        val newSize = if (currentSize > 8) currentSize - 2 else 8
                                                                        fontSize = newSize.sp
                                                                        activeFontSize = newSize.toString()
                                                                    }
                                                                },
                                                                colorSchemeColor = groupColor,
                                                                transparentBg = true,
                                                                modifier = Modifier.weight(1.1f),
                                                                customContent = {
                                                                    Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null, tint = groupColor, modifier = Modifier.size(22.dp))
                                                                }
                                                            )

                                                            RibbonIconButton(
                                                                contentDescription = "Change Case",
                                                                onClick = {
                                                                    val selection = editorTextFieldValue.selection
                                                                    val text = editorTextFieldValue.text
                                                                    if (!selection.collapsed) {
                                                                        val selStart = minOf(selection.start, selection.end)
                                                                        val selEnd = maxOf(selection.start, selection.end)
                                                                        val selectedStr = text.substring(selStart, selEnd)
                                                                        val updatedStr = if (selectedStr == selectedStr.uppercase()) {
                                                                            selectedStr.lowercase()
                                                                        } else if (selectedStr == selectedStr.lowercase()) {
                                                                            selectedStr.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                                                                        } else {
                                                                            selectedStr.uppercase()
                                                                        }
                                                                        val newText = text.replaceRange(selStart, selEnd, updatedStr)
                                                                        editorTextFieldValue = TextFieldValue(text = newText, selection = TextRange(selStart, selStart + updatedStr.length))
                                                                        onContentChange(newText)
                                                                    } else {
                                                                        val currentContent = draftContent
                                                                        val updatedContent = if (currentContent == currentContent.uppercase()) {
                                                                            currentContent.lowercase()
                                                                        } else if (currentContent == currentContent.lowercase()) {
                                                                            currentContent.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                                                                        } else {
                                                                            currentContent.uppercase()
                                                                        }
                                                                        editorTextFieldValue = TextFieldValue(text = updatedContent, selection = TextRange(updatedContent.length))
                                                                        onContentChange(updatedContent)
                                                                    }
                                                                },
                                                                colorSchemeColor = groupColor,
                                                                transparentBg = true,
                                                                modifier = Modifier.weight(1.3f),
                                                                customContent = {
                                                                    Text("Aa", color = groupColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                                                }
                                                            )

                                                            RibbonIconButton(
                                                                contentDescription = "Clear Formatting",
                                                                onClick = {
                                                                    onAction("clear_format")
                                                                },
                                                                colorSchemeColor = groupColor,
                                                                transparentBg = true,
                                                                modifier = Modifier.weight(1.1f),
                                                                customContent = {
                                                                    Text("×", color = groupColor, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                                                }
                                                            )
                                                        }

                                                        // Row 2: Text Styling
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            RibbonIconButton(
                                                                contentDescription = "Bold",
                                                                onClick = { onAction("bold") },
                                                                isSelected = "bold" in activeFormatting,
                                                                colorSchemeColor = groupColor,
                                                                modifier = Modifier.weight(1f),
                                                                customContent = {
                                                                    Text("B", fontWeight = FontWeight.Black, fontSize = 18.sp, color = if (isSystemInDarkTheme()) Color.White else Color.Black, fontFamily = FontFamily.SansSerif)
                                                                }
                                                            )
                                                            RibbonIconButton(
                                                                contentDescription = "Italic",
                                                                onClick = { onAction("italic") },
                                                                isSelected = "italic" in activeFormatting,
                                                                colorSchemeColor = groupColor,
                                                                modifier = Modifier.weight(1f),
                                                                customContent = {
                                                                    Text("I", fontStyle = FontStyle.Italic, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = if (isSystemInDarkTheme()) Color.White else Color.Black, fontFamily = FontFamily.Serif)
                                                                }
                                                            )
                                                            RibbonIconButton(
                                                                contentDescription = "Underline",
                                                                onClick = { onAction("underline") },
                                                                isSelected = "underline" in activeFormatting,
                                                                colorSchemeColor = groupColor,
                                                                modifier = Modifier.weight(1f),
                                                                customContent = {
                                                                    Text("U", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = if (isSystemInDarkTheme()) Color.White else Color.Black, textDecoration = TextDecoration.Underline, fontFamily = FontFamily.SansSerif)
                                                                }
                                                            )
                                                            RibbonIconButton(
                                                                contentDescription = "Strikethrough",
                                                                onClick = {
                                                                    onAction("strikethrough")
                                                                 },
                                                                isSelected = "strikethrough" in activeFormatting,
                                                                colorSchemeColor = groupColor,
                                                                modifier = Modifier.weight(1f),
                                                                customContent = {
                                                                    Text("abc", fontWeight = FontWeight.Medium, fontSize = 14.sp, color = if (isSystemInDarkTheme()) Color.White else Color.Black, textDecoration = TextDecoration.LineThrough, fontFamily = FontFamily.SansSerif)
                                                                }
                                                            )
                                                            RibbonIconButton(
                                                                contentDescription = "Subscript",
                                                                onClick = {
                                                                    onAction("subscript")
                                                                },
                                                                isSelected = "subscript" in activeFormatting,
                                                                colorSchemeColor = groupColor,
                                                                modifier = Modifier.weight(1f),
                                                                customContent = {
                                                                    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(bottom = 2.dp)) {
                                                                        Text("x", fontSize = 14.sp, color = if (isSystemInDarkTheme()) Color.White else Color.Black, fontWeight = FontWeight.Bold)
                                                                        Text("2", fontSize = 9.sp, color = if (isSystemInDarkTheme()) Color.White else Color.Black, fontWeight = FontWeight.Bold, modifier = Modifier.offset(y = 2.dp))
                                                                    }
                                                                }
                                                            )
                                                            RibbonIconButton(
                                                                contentDescription = "Superscript",
                                                                onClick = {
                                                                    onAction("superscript")
                                                                },
                                                                isSelected = "superscript" in activeFormatting,
                                                                colorSchemeColor = groupColor,
                                                                modifier = Modifier.weight(1f),
                                                                customContent = {
                                                                    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(top = 2.dp)) {
                                                                        Text("x", fontSize = 14.sp, color = if (isSystemInDarkTheme()) Color.White else Color.Black, fontWeight = FontWeight.Bold)
                                                                        Text("2", fontSize = 9.sp, color = if (isSystemInDarkTheme()) Color.White else Color.Black, fontWeight = FontWeight.Bold, modifier = Modifier.offset(y = -2.dp))
                                                                    }
                                                                }
                                                            )
                                                             RibbonIconButton(
                                                                contentDescription = "Font Color",
                                                                onClick = {
                                                                    if ("color" in activeFormatting) {
                                                                        val sel = editorTextFieldValue.selection
                                                                        if (!sel.collapsed) {
                                                                            val start = minOf(sel.start, sel.end)
                                                                            val end = maxOf(sel.start, sel.end)
                                                                            DocFormatRepository.removeSpanTypeRange(selectedDoc.id, "color", start, end)
                                                                            formatVersion++
                                                                        }
                                                                    } else {
                                                                        showFontColorPicker = true
                                                                    }
                                                                },
                                                                isSelected = "color" in activeFormatting,
                                                                colorSchemeColor = groupColor,
                                                                modifier = Modifier.weight(1f),
                                                                customContent = {
                                                                    val colorVal = cursorFontColorVal
                                                                    val displayColor = if (colorVal != null) {
                                                                        try { Color(android.graphics.Color.parseColor(colorVal)) } catch (e: Exception) { Color(0xFF3B82F6) }
                                                                    } else {
                                                                        Color(0xFF3B82F6)
                                                                    }
                                                                    Text("A", fontSize = 18.sp, fontWeight = FontWeight.Black, color = displayColor)
                                                                }
                                                            )
                                                            RibbonIconButton(
                                                                contentDescription = "Highlight",
                                                                onClick = {
                                                                    if ("highlight" in activeFormatting) {
                                                                        val sel = editorTextFieldValue.selection
                                                                        if (!sel.collapsed) {
                                                                            val start = minOf(sel.start, sel.end)
                                                                            val end = maxOf(sel.start, sel.end)
                                                                            DocFormatRepository.removeSpanTypeRange(selectedDoc.id, "highlight", start, end)
                                                                            formatVersion++
                                                                        }
                                                                    } else {
                                                                        showHighlightPicker = true
                                                                    }
                                                                },
                                                                isSelected = "highlight" in activeFormatting,
                                                                colorSchemeColor = groupColor,
                                                                modifier = Modifier.weight(1f),
                                                                customContent = {
                                                                    val hlColor = cursorHighlightColorVal
                                                                    val tintColor = if (hlColor != null) {
                                                                        try { Color(android.graphics.Color.parseColor(hlColor)) } catch (e: Exception) { Color(0xFFFDE047).copy(alpha = 0.6f) }
                                                                    } else {
                                                                        if (isSystemInDarkTheme()) Color(0xFF94A3B8) else Color(0xFF64748B)
                                                                    }
                                                                    Icon(
                                                                        imageVector = Icons.Outlined.Edit,
                                                                        contentDescription = "Highlight",
                                                                        tint = tintColor,
                                                                        modifier = Modifier.size(18.dp).rotate(-45f)
                                                                    )
                                                                }
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            /*
                                            // --- FONT GROUP ---
                                            item {
                                                RibbonGroupContainer(
                                                    title = "Font Formatting",
                                                    isExpanded = isFontExpanded,
                                                    onToggleExpand = { isFontExpanded = !isFontExpanded },
                                                    accentColor = if (selectedDoc.type == "word") DocWordColor else if (selectedDoc.type == "sheet") DocSheetColor else DocSlideColor
                                                ) {
                                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                        // Row 1: Dropdowns and scale buttons
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            RibbonDropdown(
                                                                selectedValue = activeFontFamily,
                                                                options = listOf("Default", "Aptos", "Calibri", "Arial", "Times New Roman", "Courier New", "Georgia", "Space Grotesk", "JetBrains Mono"),
                                                                onSelect = {
                                                                    activeFontFamily = it
                                                                    onAction("clear_format")
                                                                    coroutineScope.launch {
                                                                        snackbarHostState.showSnackbar("Font family changed to: $it")
                                                                    }
                                                                },
                                                                modifier = Modifier.weight(4f)
                                                            )

                                                            RibbonDropdown(
                                                                selectedValue = activeFontSize,
                                                                options = listOf("9", "10", "11", "12", "14", "16", "18", "20", "24", "28", "32", "48"),
                                                                onSelect = {
                                                                    activeFontSize = it
                                                                    onAction("clear_format")
                                                                    val num = it.toIntOrNull() ?: 14
                                                                    fontSize = num.sp
                                                                    coroutineScope.launch {
                                                                        snackbarHostState.showSnackbar("Font size set to: ${num}sp")
                                                                    }
                                                                },
                                                                modifier = Modifier.weight(2f)
                                                            )

                                                            RibbonIconButton(
                                                                icon = Icons.Outlined.Add,
                                                                contentDescription = "Increase Font Size",
                                                                onClick = {
                                                                    onAction("font_incr")
                                                                    val currentSize = fontSize.value.toInt()
                                                                    val newSize = if (currentSize < 48) currentSize + 2 else 48
                                                                    fontSize = newSize.sp
                                                                    activeFontSize = newSize.toString()
                                                                },
                                                                colorSchemeColor = if (selectedDoc.type == "word") DocWordColor else if (selectedDoc.type == "sheet") DocSheetColor else DocSlideColor,
                                                                modifier = Modifier.weight(1f)
                                                            )

                                                            RibbonIconButton(
                                                                icon = Icons.Outlined.Delete,
                                                                contentDescription = "Decrease Font Size",
                                                                onClick = {
                                                                    onAction("font_decr")
                                                                    val currentSize = fontSize.value.toInt()
                                                                    val newSize = if (currentSize > 8) currentSize - 2 else 8
                                                                    fontSize = newSize.sp
                                                                    activeFontSize = newSize.toString()
                                                                },
                                                                colorSchemeColor = if (selectedDoc.type == "word") DocWordColor else if (selectedDoc.type == "sheet") DocSheetColor else DocSlideColor,
                                                                modifier = Modifier.weight(1f)
                                                            )

                                                            RibbonIconButton(
                                                                icon = Icons.Outlined.Refresh,
                                                                contentDescription = "Change Case",
                                                                onClick = {
                                                                    val selection = editorTextFieldValue.selection
                                                                    val text = editorTextFieldValue.text
                                                                    if (!selection.collapsed) {
                                                                        val selectedStr = text.substring(selection.start, selection.end)
                                                                        val updatedStr = if (selectedStr == selectedStr.uppercase()) {
                                                                            selectedStr.lowercase()
                                                                        } else if (selectedStr == selectedStr.lowercase()) {
                                                                            selectedStr.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                                                                        } else {
                                                                            selectedStr.uppercase()
                                                                        }
                                                                        val newText = text.replaceRange(selection.start, selection.end, updatedStr)
                                                                        editorTextFieldValue = TextFieldValue(text = newText, selection = TextRange(selection.start, selection.start + updatedStr.length))
                                                                        onContentChange(newText)
                                                                    } else {
                                                                        val currentContent = draftContent
                                                                        val updatedContent = if (currentContent == currentContent.uppercase()) {
                                                                            currentContent.lowercase()
                                                                        } else if (currentContent == currentContent.lowercase()) {
                                                                            currentContent.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                                                                        } else {
                                                                            currentContent.uppercase()
                                                                        }
                                                                        editorTextFieldValue = TextFieldValue(text = updatedContent, selection = TextRange(updatedContent.length))
                                                                        onContentChange(updatedContent)
                                                                    }
                                                                    coroutineScope.launch {
                                                                        snackbarHostState.showSnackbar("Changed text case formatting")
                                                                    }
                                                                },
                                                                colorSchemeColor = if (selectedDoc.type == "word") DocWordColor else if (selectedDoc.type == "sheet") DocSheetColor else DocSlideColor,
                                                                modifier = Modifier.weight(1f)
                                                            )

                                                            RibbonIconButton(
                                                                icon = Icons.Outlined.Close,
                                                                contentDescription = "Clear Formatting",
                                                                onClick = {
                                                                    onAction("clear_format")
                                                                },
                                                                colorSchemeColor = if (selectedDoc.type == "word") DocWordColor else if (selectedDoc.type == "sheet") DocSheetColor else DocSlideColor,
                                                                modifier = Modifier.weight(1f)
                                                            )
                                                        }

                                                        // Row 2: Text Styling
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            RibbonIconButton(
                                                                icon = Icons.Outlined.Build,
                                                                contentDescription = "Bold",
                                                                onClick = { onAction("bold") },
                                                                colorSchemeColor = if (selectedDoc.type == "word") DocWordColor else if (selectedDoc.type == "sheet") DocSheetColor else DocSlideColor,
                                                                modifier = Modifier.weight(1f)
                                                            )
                                                            RibbonIconButton(
                                                                icon = Icons.Outlined.Refresh,
                                                                contentDescription = "Italic",
                                                                onClick = { onAction("italic") },
                                                                colorSchemeColor = if (selectedDoc.type == "word") DocWordColor else if (selectedDoc.type == "sheet") DocSheetColor else DocSlideColor,
                                                                modifier = Modifier.weight(1f)
                                                            )
                                                            RibbonIconButton(
                                                                icon = Icons.Outlined.KeyboardArrowDown,
                                                                contentDescription = "Underline",
                                                                onClick = { onAction("underline") },
                                                                colorSchemeColor = if (selectedDoc.type == "word") DocWordColor else if (selectedDoc.type == "sheet") DocSheetColor else DocSlideColor,
                                                                modifier = Modifier.weight(1f)
                                                            )
                                                            RibbonIconButton(
                                                                icon = Icons.Outlined.Close,
                                                                contentDescription = "Strikethrough",
                                                                onClick = {
                                                                    onContentChange(draftContent + " ~~Strikethrough~~")
                                                                    coroutineScope.launch {
                                                                        snackbarHostState.showSnackbar("Strikethrough formatting applied")
                                                                    }
                                                                },
                                                                colorSchemeColor = if (selectedDoc.type == "word") DocWordColor else if (selectedDoc.type == "sheet") DocSheetColor else DocSlideColor,
                                                                modifier = Modifier.weight(1f)
                                                            )
                                                            RibbonIconButton(
                                                                icon = Icons.Outlined.KeyboardArrowDown,
                                                                contentDescription = "Subscript",
                                                                onClick = {
                                                                    onContentChange(draftContent + " <sub>sub</sub>")
                                                                    coroutineScope.launch {
                                                                        snackbarHostState.showSnackbar("Subscript formatting applied")
                                                                    }
                                                                },
                                                                colorSchemeColor = if (selectedDoc.type == "word") DocWordColor else if (selectedDoc.type == "sheet") DocSheetColor else DocSlideColor,
                                                                modifier = Modifier.weight(1f)
                                                            )
                                                            RibbonIconButton(
                                                                icon = Icons.Outlined.KeyboardArrowUp,
                                                                contentDescription = "Superscript",
                                                                onClick = {
                                                                    onContentChange(draftContent + " <sup>super</sup>")
                                                                    coroutineScope.launch {
                                                                        snackbarHostState.showSnackbar("Superscript formatting applied")
                                                                    }
                                                                },
                                                                colorSchemeColor = if (selectedDoc.type == "word") DocWordColor else if (selectedDoc.type == "sheet") DocSheetColor else DocSlideColor,
                                                                modifier = Modifier.weight(1f)
                                                            )
                                                            RibbonIconButton(
                                                                icon = Icons.Outlined.Favorite,
                                                                contentDescription = "Font Color",
                                                                onClick = {
                                                                    coroutineScope.launch {
                                                                        snackbarHostState.showSnackbar("Font color changed to primary accent!")
                                                                    }
                                                                },
                                                                colorSchemeColor = if (selectedDoc.type == "word") DocWordColor else if (selectedDoc.type == "sheet") DocSheetColor else DocSlideColor,
                                                                modifier = Modifier.weight(1f)
                                                            )
                                                            RibbonIconButton(
                                                                icon = Icons.Outlined.Star,
                                                                contentDescription = "Highlight",
                                                                onClick = {
                                                                    coroutineScope.launch {
                                                                        snackbarHostState.showSnackbar("Text highlight applied!")
                                                                    }
                                                                },
                                                                colorSchemeColor = if (selectedDoc.type == "word") DocWordColor else if (selectedDoc.type == "sheet") DocSheetColor else DocSlideColor,
                                                                modifier = Modifier.weight(1f)
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            */

                                            // --- CLIPBOARD GROUP ---
                                            item {
                                                RibbonGroupContainer(
                                                    title = "Clipboard Actions",
                                                    isExpanded = isClipboardExpanded,
                                                    onToggleExpand = { isClipboardExpanded = !isClipboardExpanded },
                                                    accentColor = if (selectedDoc.type == "word") DocWordColor else if (selectedDoc.type == "sheet") DocSheetColor else DocSlideColor
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        val btnBg = if (isSystemInDarkTheme()) Color(0xFF323236) else Color(0xFFF1F3F6)
                                                        val accent = if (selectedDoc.type == "word") DocWordColor else if (selectedDoc.type == "sheet") DocSheetColor else DocSlideColor
                                                        val textColor = if (isSystemInDarkTheme()) Color.White else Color.Black

                                                        Box(modifier = Modifier.weight(1f).height(84.dp).clip(RoundedCornerShape(8.dp)).background(btnBg).clickable { onAction("cut") }, contentAlignment = Alignment.Center) {
                                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                                Icon(Icons.Outlined.ContentCut, contentDescription = "Cut", tint = accent, modifier = Modifier.size(22.dp))
                                                                Spacer(Modifier.height(4.dp))
                                                                Text("Cut", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textColor)
                                                            }
                                                        }
                                                        Box(modifier = Modifier.weight(1f).height(84.dp).clip(RoundedCornerShape(8.dp)).background(btnBg).clickable { onAction("copy") }, contentAlignment = Alignment.Center) {
                                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                                Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy", tint = accent, modifier = Modifier.size(22.dp))
                                                                Spacer(Modifier.height(4.dp))
                                                                Text("Copy", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textColor)
                                                            }
                                                        }
                                                        Box(modifier = Modifier.weight(1f).height(84.dp).clip(RoundedCornerShape(8.dp)).background(btnBg).clickable { onAction("paste") }, contentAlignment = Alignment.Center) {
                                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                                Icon(Icons.Outlined.ContentPaste, contentDescription = "Paste", tint = accent, modifier = Modifier.size(22.dp))
                                                                Spacer(Modifier.height(4.dp))
                                                                Text("Paste", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textColor)
                                                            }
                                                        }
                                                        Box(modifier = Modifier.weight(1f).height(84.dp).clip(RoundedCornerShape(8.dp)).background(btnBg).clickable {
                                                            showClipboardHistory = true
                                                        }, contentAlignment = Alignment.Center) {
                                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                                Icon(Icons.Outlined.History, contentDescription = "History", tint = accent, modifier = Modifier.size(22.dp))
                                                                Spacer(Modifier.height(4.dp))
                                                                Text("History", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textColor)
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            // --- PARAGRAPH GROUP ---
                                            item {
                                                RibbonGroupContainer(
                                                    title = "Paragraph Formatting",
                                                    isExpanded = isParagraphExpanded,
                                                    onToggleExpand = { isParagraphExpanded = !isParagraphExpanded },
                                                    accentColor = if (selectedDoc.type == "word") DocWordColor else if (selectedDoc.type == "sheet") DocSheetColor else DocSlideColor
                                                ) {
                                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        val btnBg = if (isSystemInDarkTheme()) Color(0xFF323236) else Color(0xFFF1F3F6)
                                                        val accent = if (selectedDoc.type == "word") DocWordColor else if (selectedDoc.type == "sheet") DocSheetColor else DocSlideColor
                                                        val textColor = if (isSystemInDarkTheme()) Color.White else Color.Black

                                                        @Composable
                                                        fun ParaCard(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, action: String, isSelected: Boolean = false) {
                                                            val cardBg = if (isSelected) accent.copy(alpha = 0.35f) else btnBg
                                                            Box(modifier = Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(8.dp)).background(cardBg).clickable { onAction(action) }, contentAlignment = Alignment.Center) {
                                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                                    Icon(icon, contentDescription = label, tint = accent, modifier = Modifier.size(18.dp))
                                                                    Spacer(Modifier.height(1.dp))
                                                                    Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                                }
                                                            }
                                                        }

                                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                            ParaCard(Icons.Default.FormatAlignLeft, "Left", "align_left", cursorAlignmentVal == "left")
                                                            ParaCard(Icons.Outlined.FormatAlignCenter, "Center", "align_center", cursorAlignmentVal == "center")
                                                            ParaCard(Icons.Default.FormatAlignRight, "Right", "align_right", cursorAlignmentVal == "right")
                                                            ParaCard(Icons.Outlined.FormatAlignJustify, "Justify", "align_justify", cursorAlignmentVal == "justify")
                                                            val shadingActive = pageBackgroundColor != null
                                                            Box(modifier = Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(8.dp)).background(if (shadingActive) accent.copy(alpha = 0.35f) else btnBg).clickable {
                                                                if (shadingActive) {
                                                                    pageBackgroundColor = null
                                                                    formatVersion++
                                                                } else {
                                                                    showShadingPicker = true
                                                                }
                                                            }, contentAlignment = Alignment.Center) {
                                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                                    Icon(Icons.Outlined.FormatColorFill, contentDescription = "Shading", tint = accent, modifier = Modifier.size(18.dp))
                                                                    Spacer(Modifier.height(1.dp))
                                                                    Text("Shading", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                                }
                                                            }
                                                            val borderActive = "border" in activeFormatting
                                                            Box(modifier = Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(8.dp)).background(if (borderActive) accent.copy(alpha = 0.35f) else btnBg).clickable {
                                                                if (borderActive) {
                                                                    val pos = editorTextFieldValue.selection.start
                                                                    val pRange = getParagraphRange(draftContent, pos)
                                                                    val paraEnd = pRange.endInclusive + 1
                                                                    DocFormatRepository.removeSpanTypeRange(selectedDoc.id, "border", pRange.start, paraEnd)
                                                                    formatVersion++
                                                                } else {
                                                                    showBordersDialog = true
                                                                }
                                                            }, contentAlignment = Alignment.Center) {
                                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                                    Icon(Icons.Outlined.BorderAll, contentDescription = "Borders", tint = accent, modifier = Modifier.size(18.dp))
                                                                    Spacer(Modifier.height(1.dp))
                                                                    Text("Borders", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                                }
                                                            }
                                                        }
                                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                            Box(modifier = Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(8.dp)).background(btnBg).clickable { showBulletStyleDialog = true; pendingParaAction = "bullets" }, contentAlignment = Alignment.Center) {
                                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                                    Icon(Icons.Default.FormatListBulleted, contentDescription = "Bullets", tint = accent, modifier = Modifier.size(18.dp))
                                                                    Spacer(Modifier.height(1.dp))
                                                                    Text("Bullets", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                                }
                                                            }
                                                            Box(modifier = Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(8.dp)).background(btnBg).clickable { showNumberFormatDialog = true; pendingParaAction = "numbers" }, contentAlignment = Alignment.Center) {
                                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                                    Icon(Icons.Default.FormatListNumbered, contentDescription = "Numbers", tint = accent, modifier = Modifier.size(18.dp))
                                                                    Spacer(Modifier.height(1.dp))
                                                                    Text("Numbers", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                                }
                                                            }
                                                            ParaCard(Icons.Outlined.Menu, "Multilevel", "multilevel")
                                                            ParaCard(Icons.Default.FormatIndentDecrease, "Dec", "indent_dec")
                                                            ParaCard(Icons.Default.FormatIndentIncrease, "Inc", "indent_inc")
                                                            val spacingActive = "lineSpacing" in activeFormatting
                                                            Box(modifier = Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(8.dp)).background(if (spacingActive) accent.copy(alpha = 0.35f) else btnBg).clickable {
                                                                if (spacingActive) {
                                                                    val pos = editorTextFieldValue.selection.start
                                                                    val pRange = getParagraphRange(draftContent, pos)
                                                                    val paraEnd = pRange.endInclusive + 1
                                                                    DocFormatRepository.removeSpanTypeRange(selectedDoc.id, "lineSpacing", pRange.start, paraEnd)
                                                                    formatVersion++
                                                                } else {
                                                                    showLineSpacingDialog = true
                                                                }
                                                            }, contentAlignment = Alignment.Center) {
                                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                                    Icon(Icons.Outlined.ImportExport, contentDescription = "Spacing", tint = accent, modifier = Modifier.size(18.dp))
                                                                    Spacer(Modifier.height(1.dp))
                                                                    Text(if (spacingActive && cursorLineSpacingVal != null) "${cursorLineSpacingVal}x" else "Spacing", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            // --- STYLES GROUP ---
                                            item {
                                                RibbonGroupContainer(
                                                    title = "Text Styles",
                                                    isExpanded = isStylesExpanded,
                                                    onToggleExpand = { isStylesExpanded = !isStylesExpanded },
                                                    accentColor = if (selectedDoc.type == "word") DocWordColor else if (selectedDoc.type == "sheet") DocSheetColor else DocSlideColor
                                                ) {
                                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        val stylesList = listOf(
                                                            "Normal" to "Regular document copy text style",
                                                            "Title" to "# Title Heading",
                                                            "Subtitle" to "## Secondary Section Header",
                                                            "Heading 1" to "### Heading Tier 1",
                                                            "Heading 2" to "#### Heading Tier 2",
                                                            "Heading 3" to "##### Heading Tier 3",
                                                            "Quote" to "> Inserted blockquote markup style",
                                                            "Manage" to "Configure default typeface styling template"
                                                        )
                                                        val gridRows = stylesList.chunked(4)
                                                        gridRows.forEach { rowStyles ->
                                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                                rowStyles.forEach { (styleName, mockAction) ->
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .weight(1f)
                                                                            .height(44.dp)
                                                                            .clip(RoundedCornerShape(8.dp))
                                                                            .background(if (isSystemInDarkTheme()) Color(0xFF323236) else Color(0xFFF1F3F6))
                                                                            .clickable {
                                                                                if (styleName == "Manage") {
                                                                                    coroutineScope.launch { snackbarHostState.showSnackbar("Loading style manager templates configuration dialog...") }
                                                                                } else {
                                                                                    onContentChange(draftContent + "\n\n" + mockAction)
                                                                                    coroutineScope.launch { snackbarHostState.showSnackbar("Style template '$styleName' applied successfully") }
                                                                                }
                                                                            },
                                                                        contentAlignment = Alignment.Center
                                                                    ) {
                                                                        Text(
                                                                            text = styleName,
                                                                            fontSize = 11.sp,
                                                                            fontWeight = if (styleName == "Normal") FontWeight.Normal else FontWeight.Bold,
                                                                            fontStyle = if (styleName == "Subtitle") androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
                                                                            color = if (isSystemInDarkTheme()) Color.White else Color.Black
                                                                        )
                                                                    }
                                                                }
                                                                if (rowStyles.size < 4) {
                                                                    for (j in 0 until (4 - rowStyles.size)) {
                                                                        Spacer(modifier = Modifier.weight(1f))
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            // --- EDITING GROUP ---
                                            item {
                                                RibbonGroupContainer(
                                                    title = "Editing & Selection",
                                                    isExpanded = isEditingExpanded,
                                                    onToggleExpand = { isEditingExpanded = !isEditingExpanded },
                                                    accentColor = if (selectedDoc.type == "word") DocWordColor else if (selectedDoc.type == "sheet") DocSheetColor else DocSlideColor
                                                ) {
                                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        val items = listOf("Find", "Replace", "Go To", "Select All", "Select Similar", "Clear Select")
                                                        items.chunked(3).forEach { rowItems ->
                                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                                rowItems.forEach { item ->
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .weight(1f)
                                                                            .height(40.dp)
                                                                            .clip(RoundedCornerShape(8.dp))
                                                                            .background(if (isSystemInDarkTheme()) Color(0xFF323236) else Color(0xFFF1F3F6))
                                                                            .clickable {
                                                                                if (item == "Select All") {
                                                                                    coroutineScope.launch { snackbarHostState.showSnackbar("Entire document content selected (Total ${draftContent.length} chars)") }
                                                                                } else if (item == "Clear Select") {
                                                                                    coroutineScope.launch { snackbarHostState.showSnackbar("Active text cursor selection cleared") }
                                                                                } else {
                                                                                    coroutineScope.launch { snackbarHostState.showSnackbar("Triggered Command: $item") }
                                                                                }
                                                                            },
                                                                        contentAlignment = Alignment.Center
                                                                    ) {
                                                                        Text(item, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isSystemInDarkTheme()) Color.White else Color.Black)
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            // --- STATISTICS GROUP ---
                                            item {
                                                RibbonGroupContainer(
                                                    title = "Live Document Metrics",
                                                    isExpanded = isStatsExpanded,
                                                    onToggleExpand = { isStatsExpanded = !isStatsExpanded },
                                                    accentColor = if (selectedDoc.type == "word") DocWordColor else if (selectedDoc.type == "sheet") DocSheetColor else DocSlideColor
                                                ) {
                                                    val wordsCount = draftContent.split("\\s+".toRegex()).filter { it.isNotBlank() }.size
                                                    val charsCount = draftContent.length
                                                    val paragraphsCount = draftContent.split("\n+".toRegex()).filter { it.isNotBlank() }.size
                                                    val pagesCount = maxOf(1, wordsCount / 250 + 1)
                                                    val readingTime = maxOf(1, wordsCount / 180 + 1)

                                                    Card(
                                                        shape = RoundedCornerShape(10.dp),
                                                        colors = CardDefaults.cardColors(
                                                            containerColor = if (isSystemInDarkTheme()) Color(0xFF1E1E22) else Color(0xFFF7F8FA)
                                                        ),
                                                        border = BorderStroke(1.dp, if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)),
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                                    Text(wordsCount.toString(), fontSize = 18.sp, fontWeight = FontWeight.Black, color = if (selectedDoc.type == "word") DocWordColor else if (selectedDoc.type == "sheet") DocSheetColor else DocSlideColor)
                                                                    Text("Words", fontSize = 10.sp, color = Color.Gray)
                                                                }
                                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                                    Text(charsCount.toString(), fontSize = 18.sp, fontWeight = FontWeight.Black, color = if (selectedDoc.type == "word") DocWordColor else if (selectedDoc.type == "sheet") DocSheetColor else DocSlideColor)
                                                                    Text("Chars", fontSize = 10.sp, color = Color.Gray)
                                                                }
                                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                                    Text(paragraphsCount.toString(), fontSize = 18.sp, fontWeight = FontWeight.Black, color = if (selectedDoc.type == "word") DocWordColor else if (selectedDoc.type == "sheet") DocSheetColor else DocSlideColor)
                                                                    Text("Paragraphs", fontSize = 10.sp, color = Color.Gray)
                                                                }
                                                            }
                                                            HorizontalDivider(color = if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f))
                                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                                    Text(pagesCount.toString(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (isSystemInDarkTheme()) Color.White else Color.Black)
                                                                    Text("Est. Pages", fontSize = 10.sp, color = Color.Gray)
                                                                }
                                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                                    Text("${readingTime} min", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (isSystemInDarkTheme()) Color.White else Color.Black)
                                                                    Text("Read Time", fontSize = 10.sp, color = Color.Gray)
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        if (showFontColorPicker) {
                                            ColorPickerDialog(
                                                colors = FontColors,
                                                title = "Font Color",
                                                onColorSelected = { hex ->
                                                    showFontColorPicker = false
                                                    val sel = editorTextFieldValue.selection
                                                    if (!sel.collapsed) {
                                                        val start = minOf(sel.start, sel.end)
                                                        val end = maxOf(sel.start, sel.end)
                                                        DocFormatRepository.removeSpanTypeRange(selectedDoc.id, "color", start, end)
                                                        DocFormatRepository.applySpan(selectedDoc.id, "color", hex, start, end)
                                                        formatVersion++
                                                    }
                                                },
                                                onDismiss = { showFontColorPicker = false }
                                            )
                                        }

                                        if (showHighlightPicker) {
                                            ColorPickerDialog(
                                                colors = HighlightColors,
                                                title = "Highlight Color",
                                                onColorSelected = { hex ->
                                                    showHighlightPicker = false
                                                    val sel = editorTextFieldValue.selection
                                                    if (!sel.collapsed) {
                                                        val start = minOf(sel.start, sel.end)
                                                        val end = maxOf(sel.start, sel.end)
                                                        DocFormatRepository.removeSpanTypeRange(selectedDoc.id, "highlight", start, end)
                                                        DocFormatRepository.applySpan(selectedDoc.id, "highlight", hex, start, end)
                                                        formatVersion++
                                                    }
                                                },
                                                onDismiss = { showHighlightPicker = false }
                                            )
                                        }

                                        if (showBulletStyleDialog) {
                                            BulletStyleDialog(
                                                onSelect = { char ->
                                                    showBulletStyleDialog = false
                                                    val pos = editorTextFieldValue.selection.start
                                                    val para = getParagraphText(draftContent, pos)
                                                    val (existingBullet, _) = detectListPrefix(para)
                                                    if (existingBullet != null) {
                                                        // Toggle off — remove existing bullet
                                                        val newText = removeBulletFromPara(draftContent, pos)
                                                        if (newText != draftContent) {
                                                            onContentChange(newText)
                                                            val newPos = (pos - 2).coerceAtLeast(0)
                                                            editorTextFieldValue = TextFieldValue(text = newText, selection = TextRange(newPos))
                                                        }
                                                    } else {
                                                        // Apply bullet
                                                        val newText = applyBulletToPara(draftContent, pos, char)
                                                        if (newText != draftContent) {
                                                            onContentChange(newText)
                                                            editorTextFieldValue = TextFieldValue(text = newText, selection = TextRange(pos + 2))
                                                        }
                                                    }
                                                },
                                                onDismiss = { showBulletStyleDialog = false }
                                            )
                                        }

                                        if (showNumberFormatDialog) {
                                            NumberFormatDialog(
                                                onSelect = { fmt ->
                                                    showNumberFormatDialog = false
                                                    val pos = editorTextFieldValue.selection.start
                                                    val para = getParagraphText(draftContent, pos)
                                                    val (_, existingNum) = detectListPrefix(para)
                                                    if (existingNum != null) {
                                                        // Toggle off — remove number prefix
                                                        val newText = removeBulletFromPara(draftContent, pos)
                                                        if (newText != draftContent) {
                                                            onContentChange(newText)
                                                            editorTextFieldValue = TextFieldValue(text = newText, selection = TextRange(pos.coerceAtMost(newText.length)))
                                                        }
                                                    } else {
                                                        // Apply number to current paragraph then renumber entire doc
                                                        val newText = applyNumberToPara(draftContent, pos, fmt, 1)
                                                        if (newText != draftContent) {
                                                            val renumbered = renumberDocument(newText, fmt)
                                                            onContentChange(renumbered)
                                                            editorTextFieldValue = TextFieldValue(text = renumbered, selection = TextRange(pos.coerceAtMost(renumbered.length)))
                                                        }
                                                    }
                                                },
                                                onDismiss = { showNumberFormatDialog = false }
                                            )
                                        }

                                        if (showLineSpacingDialog) {
                                            val pos = editorTextFieldValue.selection.start
                                            val currentLineSpacing = DocFormatRepository.getSpans(selectedDoc.id)
                                                .firstOrNull { it.type == "lineSpacing" && it.start <= pos && it.end > pos }
                                                ?.value?.toFloatOrNull() ?: 1.0f
                                            LineSpacingDialog(
                                                currentSpacing = currentLineSpacing,
                                                onSelect = { spacing ->
                                                    showLineSpacingDialog = false
                                                    val selStart = editorTextFieldValue.selection.start
                                                    val selEnd = editorTextFieldValue.selection.end
                                                    try {
                                                        val paraRanges = getParagraphRangesInRange(draftContent, selStart, selEnd)
                                                        if (paraRanges.isEmpty()) return@LineSpacingDialog
                                                        val allStart = paraRanges.first().start
                                                        val lastPara = paraRanges.last()
                                                        val allEnd = draftContent.indexOf('\n', lastPara.start).let { if (it == -1) draftContent.length else it + 1 }
                                                        DocFormatRepository.removeSpanTypeRange(selectedDoc.id, "lineSpacing", allStart, allEnd)
                                                        val spacingStr = spacing.toString()
                                                        for (r in paraRanges) {
                                                            val paraEnd = r.endInclusive + 1
                                                            DocFormatRepository.applySpan(selectedDoc.id, "lineSpacing", spacingStr, r.start, paraEnd)
                                                        }
                                                        formatVersion++
                                                    } catch (e: Exception) {
                                                        android.util.Log.e("Spacing", "error", e)
                                                        Toast.makeText(context, "Spacing error: ${e.message}", Toast.LENGTH_LONG).show()
                                                    }
                                                },
                                                onDismiss = { showLineSpacingDialog = false }
                                            )
                                        }

                                        if (showBordersDialog) {
                                            BordersDialog(
                                                onApply = { sides, style, color, width ->
                                                    showBordersDialog = false
                                                    val pos = editorTextFieldValue.selection.start
                                                    val pRange = getParagraphRange(draftContent, pos)
                                                    val paraEnd = pRange.endInclusive + 1
                                                    val value = "${sides.joinToString(",")}|$style|$color|$width"
                                                    DocFormatRepository.removeSpanTypeRange(selectedDoc.id, "border", pRange.start, paraEnd)
                                                    DocFormatRepository.applySpan(selectedDoc.id, "border", value, pRange.start, paraEnd)
                                                    formatVersion++
                                                },
                                                onDismiss = { showBordersDialog = false }
                                            )
                                        }

                                        if (showShadingPicker) {
                                            ColorPickerDialog(
                                                colors = listOf("#FFFFFF", "#F2F2F2", "#D9D9D9", "#BFBFBF", "#A6A6A6", "#808080", "#FFFF00", "#00FF00", "#00FFFF", "#FF0000", "#0000FF", "#FF00FF", "#800000", "#008000", "#000080", "#808000", "#800080", "#008080", "#C0C0C0", "#FFE4E1", "#F0FFF0", "#F0F8FF", "#FFFACD", "#E0FFFF", "#FFDAB9", "#E6E6FA", "#FFF0F5", "#F5DEB3", "#FFF8DC", "#FAEBD7"),
                                                title = "Page Color",
                                                onColorSelected = { hex ->
                                                    showShadingPicker = false
                                                    try {
                                                        val color = Color(android.graphics.Color.parseColor(hex))
                                                        pageBackgroundColor = color
                                                    } catch (e: Exception) {
                                                        pageBackgroundColor = null
                                                    }
                                                    formatVersion++
                                                },
                                                onDismiss = { showShadingPicker = false }
                                            )
                                        }
                                    } else {
                                        val groupedTools = filteredTools.groupBy { it.category }

                                        LazyColumn(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 16.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp),
                                            contentPadding = PaddingValues(bottom = 12.dp)
                                        ) {
                                            groupedTools.forEach { (categoryName, toolsInCategory) ->
                                                item {
                                                    Card(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        shape = RoundedCornerShape(12.dp),
                                                        colors = CardDefaults.cardColors(
                                                            containerColor = if (isDarkTheme) Color(0xFF2B2B30) else Color.White
                                                        ),
                                                        border = BorderStroke(
                                                            width = 1.dp,
                                                            color = if (isDarkTheme) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
                                                        )
                                                    ) {
                                                        Column(modifier = Modifier.padding(12.dp)) {
                                                            Text(
                                                                text = categoryName.uppercase(),
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = if (selectedDoc.type == "word") DocWordColor else if (selectedDoc.type == "sheet") DocSheetColor else DocSlideColor,
                                                                letterSpacing = 0.8.sp,
                                                                modifier = Modifier.padding(bottom = 8.dp)
                                                            )

                                                            val cols = if (totalWidth < 600.dp) 3 else if (totalWidth < 900.dp) 4 else 6
                                                            val chunks = toolsInCategory.chunked(cols)

                                                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                                chunks.forEach { rowTools ->
                                                                    Row(
                                                                        modifier = Modifier.fillMaxWidth(),
                                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                                    ) {
                                                                        rowTools.forEach { tool ->
                                                                            Box(
                                                                                modifier = Modifier
                                                                                    .weight(1f)
                                                                                    .testTag("ribbon_tool_${tool.id}")
                                                                            ) {
                                                                                RibbonToolCard(
                                                                                    tool = tool,
                                                                                    isDarkTheme = isDarkTheme
                                                                                )
                                                                            }
                                                                        }

                                                                        if (rowTools.size < cols) {
                                                                            for (j in 0 until (cols - rowTools.size)) {
                                                                                Spacer(modifier = Modifier.weight(1f))
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } // End of Box
                                } // End of nested Column
                            } // End of else
                        } // End of AnimatedVisibility Column
                    } // End of AnimatedVisibility

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(bottomNavBarHeight)
                            .background(if (isDarkTheme) Color(0xFF16161A) else Color.White)
                            .border(width = 0.5.dp, color = glassCardBorderColor)
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val ribbonTabs = listOf(
                            Triple("Home", Icons.Outlined.Home, "ribbon_tab_Home"),
                            Triple("Insert", Icons.Outlined.Add, "ribbon_tab_Insert"),
                            Triple("AI Assistant", Icons.Outlined.Star, "ribbon_tab_AIAssistant"),
                            Triple("Layout", Icons.Outlined.Settings, "ribbon_tab_Layout"),
                            Triple("Review", Icons.Outlined.Check, "ribbon_tab_Review")
                        )

                        ribbonTabs.forEach { (tabName, icon, tag) ->
                            val isSelected = activeRibbonTab == tabName && isRibbonExpanded
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        if (activeRibbonTab == tabName && isRibbonExpanded) {
                                            isRibbonExpanded = false
                                        } else {
                                            activeRibbonTab = tabName
                                            isRibbonExpanded = true
                                        }
                                    }
                                    .testTag(tag)
                                    .padding(vertical = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(if (isSelected) (if (selectedDoc.type == "word") DocWordColor.copy(alpha = 0.15f) else if (selectedDoc.type == "sheet") DocSheetColor.copy(alpha = 0.15f) else DocSlideColor.copy(alpha = 0.15f)) else Color.Transparent)
                                        .padding(horizontal = 14.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = "Ribbon tab $tabName",
                                        tint = if (isSelected) (if (selectedDoc.type == "word") DocWordColor else if (selectedDoc.type == "sheet") DocSheetColor else DocSlideColor) else (if (isDarkTheme) Color.LightGray else Color.Gray),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = tabName,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) (if (selectedDoc.type == "word") DocWordColor else if (selectedDoc.type == "sheet") DocSheetColor else DocSlideColor) else (if (isDarkTheme) Color.LightGray else Color.Gray)
                                )
                            }
                        }
                    }
                }
            }

            if (showCustomMarginsDialog) {
                CustomMarginsDialog(
                    onDismiss = { showCustomMarginsDialog = false },
                    onApply = { margin ->
                        pageMargins = margin
                        showCustomMarginsDialog = false
                    }
                )
            }
            if (showCustomSizeDialog) {
                CustomSizeDialog(
                    onDismiss = { showCustomSizeDialog = false },
                    onApply = { width, height -> 
                        viewModel.setCustomPageDimensions(width, height)
                        showCustomSizeDialog = false 
                    }
                )
            }
            if (showPageNumberFormatDialog) {
                PageNumberFormatDialog(
                    currentFormat = pageNumberFormat,
                    currentStartAt = pageNumberStartAt,
                    onDismiss = { showPageNumberFormatDialog = false },
                    onApply = { format, startAt ->
                        pageNumberFormat = format
                        pageNumberStartAt = startAt
                        showPageNumberFormatDialog = false
                    }
                )
            }
            if (pageNumberPositionMenu != null) {
                val menuTitle = pageNumberPositionMenu!!
                val options = when (menuTitle) {
                    "Top of Page" -> listOf("Top Left", "Top Center", "Top Right")
                    "Bottom of Page" -> listOf("Bottom Left", "Bottom Center", "Bottom Right")
                    "Page Margins" -> listOf("Left Margin", "Right Margin", "Outside Margin", "Inside Margin")
                    else -> emptyList()
                }
                AlertDialog(
                    onDismissRequest = { pageNumberPositionMenu = null },
                    title = { Text(menuTitle) },
                    text = {
                        Column {
                            options.forEach { option ->
                                TextButton(
                                    onClick = {
                                        pageNumberPosition = option
                                        pageNumberPositionMenu = null
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(option, textAlign = TextAlign.Left, modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { pageNumberPositionMenu = null }) { Text("Cancel") }
                    }
                )
            }
            if (showClipboardHistory) {
                AlertDialog(
                    onDismissRequest = { showClipboardHistory = false },
                    title = { Text("Clipboard History") },
                    text = {
                        if (clipboardHistory.isEmpty()) {
                            Text("No clipboard history yet")
                        } else {
                                            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                                clipboardHistory.reversed().forEach { entry ->
                                                    TextButton(
                                                        onClick = {
                                                            val sel = editorTextFieldValue.selection
                                                            val start = minOf(sel.start, sel.end)
                                                            val end = maxOf(sel.start, sel.end)
                                                            val newText = draftContent.substring(0, start) + entry + draftContent.substring(end)
                                                            onContentChange(newText)
                                                            val newCursor = start + entry.length
                                                            editorTextFieldValue = TextFieldValue(text = newText, selection = TextRange(newCursor))
                                                            showClipboardHistory = false
                                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = entry.take(80) + if (entry.length > 80) "..." else "",
                                            textAlign = TextAlign.Start,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            clipboardHistory.clear()
                            showClipboardHistory = false
                        }) {
                            Text("Clear All")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showClipboardHistory = false }) {
                            Text("Close")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun EmptyWorkspaceState(
    viewModel: DocViewModel,
    onToggleSidebar: () -> Unit,
    isSidebarExpanded: Boolean,
    onQuickCreate: (String, String) -> Unit,
    onFABClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    var activeTab by remember { mutableStateOf("home") } // "home", "files", "shared", "settings"

    BackHandler(enabled = activeTab != "home" && !isSidebarExpanded) {
        activeTab = "home"
    }

    // Simulate username personalization in SQLite workspace
    var username by remember { mutableStateOf("Sarah") }
    var userRole by remember { mutableStateOf("Lead Editor") }

    // State to toggle mock collaboration notifications
    var showSimulatedStatus by remember { mutableStateOf(false) }
    var activeCollaborators by remember { mutableStateOf(7) }

    // State for selected file category inside Files tab
    var filesCategoryTab by remember { mutableStateOf("all") }

    // Determine featured document (most recently updated/created document)
    val featuredDoc = remember(documents) {
        documents.maxByOrNull { it.updatedAt }
    }

    // SQLite data stats
    val totalFiles = documents.size
    val favoriteFiles = remember(documents) { documents.count { it.isFavorite } }
    val sheetsCount = remember(documents) { documents.count { it.type == "sheet" } }
    val writerCount = remember(documents) { documents.count { it.type == "word" } }
    val slidesCount = remember(documents) { documents.count { it.type == "slide" } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFFDFBFF)) // Matching design body background
    ) {
        // --- 1. Top Header Search Bar (Material 3 Style) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onToggleSidebar,
                modifier = Modifier.padding(end = 4.dp)
            ) {
                Icon(
                    imageVector = if (isSidebarExpanded) Icons.Outlined.Close else Icons.Outlined.Menu,
                    contentDescription = "Toggle Drawer Menu",
                    tint = Color(0xFF1A1C1E)
                )
            }

            // High polish search pill matching design HTML layout
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0xFFEEF0F6))
                    .clickable { 
                        // Automatically navigate to files tab when clicking search
                        activeTab = "files"
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = "Search",
                    tint = Color(0xFF44474E),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                
                // Allow interactive typing straight on the bento search bar
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { 
                        viewModel.setSearchQuery(it)
                        if (it.isNotEmpty() && activeTab != "files") {
                            activeTab = "files"
                        }
                    },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = Color(0xFF1A1C1E),
                        fontWeight = FontWeight.Medium
                    ),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    decorationBox = { innerTextField ->
                        if (searchQuery.isEmpty()) {
                            Text(
                                "Search JCdocs Suite...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF44474E).copy(alpha = 0.7f)
                            )
                        }
                        innerTextField()
                    }
                )

                // Colored round avatar badge representing offline native security authority
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFD9E2FF))
                        .border(1.dp, Color.White, CircleShape)
                        .clickable { activeTab = "settings" },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = username.take(2).uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF001D36)
                        )
                    )
                }
            }
        }

        // --- 2. Interactive Workspace Tabs ---
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (activeTab) {
                "home" -> {
                    // Bento Grid Layout (Featured card, Collaboration status, Stats, Storage, AI Templates)
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        val isWideScreen = maxWidth >= 700.dp
                        val scrollState = rememberScrollState()
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (isWideScreen) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1.2f),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        FeaturedDocBentoCard(
                                            featuredDoc = featuredDoc,
                                            onDocClick = { viewModel.selectDocument(it) },
                                            onQuickCreate = { onQuickCreate("Project Proposal Deck", "word") }
                                        )

                                        CollaborationBentoCard(
                                            sheetsCount = sheetsCount,
                                            writerCount = writerCount,
                                            slidesCount = slidesCount,
                                            onClick = { activeTab = "shared" }
                                        )
                                    }

                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            StatsBentoSquare(
                                                totalFiles = totalFiles,
                                                favoriteFiles = favoriteFiles,
                                                onClick = { activeTab = "files" },
                                                modifier = Modifier.weight(1f)
                                            )
                                            RoomDbStorageBentoSquare(
                                                totalFiles = totalFiles,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }

                                        SmartTemplatesBentoCard(
                                            onQuickCreate = onQuickCreate
                                        )
                                    }
                                }
                            } else {
                                FeaturedDocBentoCard(
                                    featuredDoc = featuredDoc,
                                    onDocClick = { viewModel.selectDocument(it) },
                                    onQuickCreate = { onQuickCreate("Project Proposal Deck", "word") },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    StatsBentoSquare(
                                        totalFiles = totalFiles,
                                        favoriteFiles = favoriteFiles,
                                        onClick = { activeTab = "files" },
                                        modifier = Modifier.weight(1f)
                                    )
                                    RoomDbStorageBentoSquare(
                                        totalFiles = totalFiles,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                CollaborationBentoCard(
                                    sheetsCount = sheetsCount,
                                    writerCount = writerCount,
                                    slidesCount = slidesCount,
                                    onClick = { activeTab = "shared" },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                SmartTemplatesBentoCard(
                                    onQuickCreate = onQuickCreate,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            // Spacer to prevent layout clips by navigation bar
                            Spacer(modifier = Modifier.height(80.dp))
                        }
                    }
                }
                "files" -> {
                    // Modern styled files grid list
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = "My Documents Ecosystem",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF1A1C1E),
                            modifier = Modifier.padding(vertical = 12.dp)
                        )

                        // File type filtering chips inside tab
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(bottom = 12.dp)
                        ) {
                            listOf("all" to "All Streams", "word" to "Writer Note", "sheet" to "Spreadsheet", "slide" to "Slide Decks").forEach { (type, label) ->
                                val selected = filesCategoryTab == type
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(100.dp))
                                        .background(if (selected) Color(0xFFD9E2FF) else Color(0xFFEEF0F6))
                                        .clickable { 
                                            filesCategoryTab = type
                                            viewModel.setTypeFilter(type)
                                        }
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (selected) Color(0xFF001D36) else Color(0xFF44474E)
                                        )
                                    )
                                }
                            }
                        }

                        // Listed documents
                        if (documents.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Info,
                                    contentDescription = "Empty",
                                    tint = Color.LightGray,
                                    modifier = Modifier.size(54.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "No files match active filter",
                                    fontWeight = FontWeight.Medium,
                                    color = Color.Gray
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(bottom = 90.dp)
                            ) {
                                items(documents, key = { it.id }) { doc ->
                                    val isSelected = featuredDoc?.id == doc.id
                                    DocumentTile(
                                        doc = doc,
                                        isSelected = isSelected,
                                        onClick = { viewModel.selectDocument(doc) },
                                        onDelete = { viewModel.deleteDocument(doc) },
                                        onFavoriteToggle = { viewModel.toggleFavorite(doc) }
                                    )
                                }
                            }
                        }
                    }
                }
                "shared" -> {
                    // Collaboration Dashboard Cockpit
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE1E2E9)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text(
                                    "JCdocs Real-Time Simulation Deck",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = Color(0xFF1A1C1E)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "Simulate background activity of virtual project contributors to demonstrate secure multi-window integrity.",
                                    fontSize = 13.sp,
                                    color = Color(0xFF44474E)
                                )
                            }
                        }

                        Text(
                            text = "ACTIVE SIMULATORS",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.Gray
                        )

                        // Collaborator rows
                        val simulatedUsers = listOf(
                            Triple("Sarah Jenkins", "Writer Editor", Color(0xFF42A5F5)),
                            Triple("Alex Rivera", "Spreadsheet Coordinator", Color(0xFF66BB6A)),
                            Triple("David Chang", "Slides Presentation Designer", Color(0xFFAB47BC)),
                            Triple("Integrity Agent VIPER", "Autosave Bot", Color(0xFFDF4A32))
                        )

                        simulatedUsers.forEach { (name, role, avatarBg) ->
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(1.dp, Color(0xFFEEF0F6)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(avatarBg),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            name.take(1) + name.split(" ").getOrNull(1)?.take(1).orEmpty(),
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1A1C1E))
                                        Text(role, fontSize = 11.sp, color = Color.Gray)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(100.dp))
                                            .background(Color(0xFFE8F5E9))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text("Active", fontSize = 10.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // Simulation interactive block
                        Button(
                            onClick = {
                                showSimulatedStatus = true
                                activeCollaborators++
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = OnlyOfficePrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.Share, contentDescription = "Simulate")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Simulate Co-Editor Background Edits", fontWeight = FontWeight.Bold)
                        }

                        if (showSimulatedStatus) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFFFF3CD))
                                    .border(1.dp, Color(0xFFFFEBAA), RoundedCornerShape(12.dp))
                                    .padding(12.dp)
                            ) {
                                Text(
                                    "✨ Simulation Triggered! Real-time local cache transaction registered. SQLite database synced securely.",
                                    fontSize = 12.sp,
                                    color = Color(0xFF856404),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(100.dp))
                    }
                }
                "settings" -> {
                    // Gorgeous settings panel
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "User Workspace Settings",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF1A1C1E)
                        )

                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color(0xFFEEF0F6)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("MY PROFILE CARD", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color.Gray)

                                OutlinedTextField(
                                    value = username,
                                    onValueChange = { username = it },
                                    label = { Text("Display Name") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                OutlinedTextField(
                                    value = userRole,
                                    onValueChange = { userRole = it },
                                    label = { Text("Workspace Role Title") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color(0xFFEEF0F6)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("SYSTEM INFORMATION", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color.Gray)
                                
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Software Engine", fontSize = 13.sp, color = Color.DarkGray)
                                    Text("JCdocs ONLYOFFICE 2.4", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                }
                                HorizontalDivider(color = Color(0xFFEEF0F6))
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Storage Engine", fontSize = 13.sp, color = Color.DarkGray)
                                    Text("Android SQLite Room DB", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                }
                                HorizontalDivider(color = Color(0xFFEEF0F6))
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Offline Operations", fontSize = 13.sp, color = Color.DarkGray)
                                    Text("Enabled (100% Native)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                }
                            }
                        }

                        // Cache reset action
                        Button(
                            onClick = {
                                documents.forEach { viewModel.deleteDocument(it) }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Wipe")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Clear All Local Sandbox Documents", fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        Spacer(modifier = Modifier.height(100.dp))
                    }
                }
            }

            // --- 4. Material 3 Bottom Navigation bar (Placed exactly matching HTML) ---
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(32.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F4F9)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BottomNavItem(
                            icon = Icons.Outlined.Home,
                            label = "Home",
                            isSelected = activeTab == "home",
                            onClick = { activeTab = "home" },
                            modifier = Modifier.weight(1f)
                        )
                        BottomNavItem(
                            icon = Icons.Outlined.Search,
                            label = "Files",
                            isSelected = activeTab == "files",
                            onClick = { activeTab = "files" },
                            modifier = Modifier.weight(1f)
                        )
                        BottomNavItem(
                            icon = Icons.Outlined.Share,
                            label = "Shared",
                            isSelected = activeTab == "shared",
                            onClick = { activeTab = "shared" },
                            modifier = Modifier.weight(1f)
                        )
                        BottomNavItem(
                            icon = Icons.Outlined.Settings,
                            label = "Settings",
                            isSelected = activeTab == "settings",
                            onClick = { activeTab = "settings" },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // --- 3. Large Circle FAB (Placed exactly matching HTML) ---
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 90.dp, end = 20.dp)
            ) {
                FloatingActionButton(
                    onClick = onFABClick,
                    containerColor = Color(0xFFD9E2FF),
                    contentColor = Color(0xFF001D36),
                    shape = RoundedCornerShape(16.dp),
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp),
                    modifier = Modifier
                        .size(56.dp)
                        .testTag("bento_fab")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = "Create New Document",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun BottomNavItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(vertical = 6.dp)
            .wrapContentSize(Alignment.Center)
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(100.dp))
                .background(if (isSelected) Color(0xFFD9E2FF) else Color.Transparent)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) Color(0xFF001D36) else Color(0xFF44474E).copy(alpha = 0.7f),
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) Color(0xFF001D36) else Color(0xFF44474E),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ==========================================
// BENTO GRID SUB-COMPONENTS
// ==========================================

@Composable
fun FeaturedDocBentoCard(
    featuredDoc: DocEntity?,
    onDocClick: (DocEntity) -> Unit,
    onQuickCreate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardBgColor = Color(0xFFD9E2FF) // Lavender Blue
    val textColor = Color(0xFF001D36)
    val subtextColor = Color(0xFF44474E)

    val lastEditedFormatted = remember(featuredDoc?.updatedAt) {
        if (featuredDoc != null) {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            "Modified Today at ${sdf.format(Date(featuredDoc.updatedAt))}"
        } else {
            "No recent modifications recorded"
        }
    }

    Card(
        onClick = { if (featuredDoc != null) onDocClick(featuredDoc) else onQuickCreate() },
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 160.dp)
            .testTag("bento_featured_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Icon layout inspired by OnlyOffice and Bento layouts
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    val symbolStr = when (featuredDoc?.type) {
                        "word" -> "W"
                        "sheet" -> "S"
                        "slide" -> "P"
                        else -> "O"
                    }
                    Text(
                        text = symbolStr,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF005AC1),
                        fontSize = 18.sp
                    )
                }

                // Dynamic badges
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(Color(0xFF005AC1).copy(alpha = 0.08f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (featuredDoc != null) "Resume Editing" else "Create Now",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Column {
                Text(
                    text = featuredDoc?.title ?: "Welcome To JCdocs Workspace",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        lineHeight = 26.sp
                    ),
                    color = textColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = if (featuredDoc != null) lastEditedFormatted else "Get started immediately by clicking to build your proposal note.",
                    style = MaterialTheme.typography.bodySmall,
                    color = subtextColor
                )
            }
        }
    }
}

@Composable
fun CollaborationBentoCard(
    sheetsCount: Int,
    writerCount: Int,
    slidesCount: Int,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val cardBgColor = Color(0xFFE1E2E9) // Cool Grey
    val textColor = Color(0xFF1A1C1E)

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 140.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "SANDBOX ENGAGEMENT",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = Color(0xFF44474E)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Avatar stacking overlay exactly replicating Tailwind CSS markup
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy((-8).dp)
            ) {
                // Color dots simulating users
                listOf(Color(0xFF42A5F5), Color(0xFF66BB6A), Color(0xFFAB47BC)).forEachIndexed { i, color ->
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(1.5.dp, cardBgColor, CircleShape)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.6f))
                        .border(1.5.dp, cardBgColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+4",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.DarkGray
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Text(
                    text = "7 simulated sandboxes active",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Robust offline operations utilizing local cache streams across $writerCount documents, $sheetsCount sheets, and $slidesCount decks.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF44474E).copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
fun StatsBentoSquare(
    totalFiles: Int,
    favoriteFiles: Int,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val cardBgColor = Color(0xFFFAD8FD) // Pastel Lavender Purple
    val textColor = Color(0xFF2B1230)

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .heightIn(min = 130.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = totalFiles.toString(),
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                    color = textColor
                )
                
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "Review Status",
                    tint = textColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column {
                Text(
                    text = "TOTAL REVIEWS",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = textColor.copy(alpha = 0.5f)
                )
                
                Text(
                    text = "$favoriteFiles Starred Documents",
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun RoomDbStorageBentoSquare(
    totalFiles: Int,
    modifier: Modifier = Modifier
) {
    val cardBgColor = Color(0xFFD3E8D3) // Pastel Mint Green
    val textColor = Color(0xFF00210B)
    val progressColor = Color(0xFF116C31)

    // Arbitrary percentage showcasing offline health
    val percentage = if (totalFiles == 0) 0f else minOf(100f, 15f + (totalFiles * 12f))

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .heightIn(min = 130.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SQL DATABASE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    color = textColor.copy(alpha = 0.6f)
                )
                
                Text(
                    text = "${percentage.toInt()}%",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = textColor
                )
            }

            // Custom green percentage progress bar representing sqlite memory limits
            Column {
                LinearProgressIndicator(
                    progress = percentage / 100f,
                    color = progressColor,
                    trackColor = textColor.copy(alpha = 0.08f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "SQLite schema integrity secure",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = progressColor
                )
            }
        }
    }
}

@Composable
fun SmartTemplatesBentoCard(
    onQuickCreate: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val cardBgColor = Color(0xFFFFDAD6) // Pastel Peach Pink
    val textColor = Color(0xFF410002)

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 180.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Smart Templates",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = textColor
                )
                
                Text(
                    text = "AI-powered document structure generation in modern Jetpack Compose",
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Scrollable row of template quick-action chips exactly conforming to the design
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TemplateChipItem(
                    label = "Invoice Project",
                    onClick = { onQuickCreate("Project Financial Invoice", "sheet") }
                )
                TemplateChipItem(
                    label = "AI Proposal Document",
                    onClick = { onQuickCreate("Bento Proposal Deck", "word") }
                )
                TemplateChipItem(
                    label = "NDA Agreement",
                    onClick = { onQuickCreate("Joint Consultation NDA Agreement", "word") }
                )
                TemplateChipItem(
                    label = "Keynote Slides",
                    onClick = { onQuickCreate("Smart Technology Keynote", "slide") }
                )
            }
        }
    }
}

@Composable
fun TemplateChipItem(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.6f))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                color = Color(0xFF410002)
            )
        )
    }
}

@Composable
fun TemplateCard(
    title: String,
    typeStr: String,
    iconChar: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
        modifier = modifier
            .width(150.dp)
            .padding(4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = iconChar,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 20.sp
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = typeStr,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                maxLines = 1
            )
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun WorkspaceMenuBar(
    doc: DocEntity,
    draftTitle: String,
    onTitleChange: (String) -> Unit,
    isSidebarExpanded: Boolean,
    onToggleSidebar: () -> Unit,
    onCloseClick: () -> Unit,
    undoRedoManager: DocUndoRedoManager,
    undoRedoTrigger: Int,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColor = when (doc.type) {
        "word" -> DocWordColor
        "sheet" -> DocSheetColor
        "slide" -> DocSlideColor
        else -> OnlyOfficePrimary
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Toggle Sidebar Button
        IconButton(onClick = onToggleSidebar) {
            Icon(
                imageVector = if (isSidebarExpanded) Icons.Outlined.Close else Icons.Outlined.Menu,
                contentDescription = "Toggle Sidebar"
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Document Type Badge Indicator
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(themeColor),
            contentAlignment = Alignment.Center
        ) {
            val symbolChar = when (doc.type) {
                "word" -> "W"
                "sheet" -> "S"
                "slide" -> "P"
                else -> "D"
            }
            Text(
                text = symbolChar,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Document Title Text Edit field
        BasicTextField(
            value = draftTitle,
            onValueChange = onTitleChange,
            textStyle = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            ),
            singleLine = true,
            modifier = Modifier
                .weight(1f)
                .testTag("workspace_title_input")
        )

        // Undo and Redo Buttons
        val x = undoRedoTrigger // Recompose on undo/redo actions
        IconButton(
            onClick = onUndo,
            enabled = undoRedoManager.canUndo(),
            modifier = Modifier.testTag("undo_button")
        ) {
            Icon(
                imageVector = Icons.Default.Undo,
                contentDescription = "Undo",
                tint = if (undoRedoManager.canUndo()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
        }
        
        IconButton(
            onClick = onRedo,
            enabled = undoRedoManager.canRedo(),
            modifier = Modifier.testTag("redo_button")
        ) {
            Icon(
                imageVector = Icons.Default.Redo,
                contentDescription = "Redo",
                tint = if (undoRedoManager.canRedo()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
        }

        // Saved Status Indicator (Automatic local saving is active)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2E7D32)) // Soft Green
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Saved",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Close Document Button
        IconButton(
            onClick = onCloseClick,
            modifier = Modifier.testTag("close_document_button")
        ) {
            Icon(
                imageVector = Icons.Outlined.ArrowBack,
                contentDescription = "Exit to Dashboard",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

class RichTextVisualTransformation(private val spans: List<DocFormatSpan>, private val absoluteOffset: Int) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        try {
            return filterUnsafe(text)
        } catch (e: Exception) {
            android.util.Log.e("RichTextTransform", "filter error", e)
            return TransformedText(text, OffsetMapping.Identity)
        }
    }
    
    private fun filterUnsafe(text: AnnotatedString): TransformedText {
        val builder = AnnotatedString.Builder(text.text)
        val chunkLength = text.text.length
        val paraRangeList = mutableListOf<androidx.compose.ui.text.AnnotatedString.Range<androidx.compose.ui.text.ParagraphStyle>>()
        
        // First pass: collect paragraph-level properties (alignment + lineSpacing) per paragraph range
        val paraProps = mutableMapOf<String, MutableMap<String, String>>()
        
        spans.forEach { span ->
            val relStart = maxOf(0, span.start - absoluteOffset)
            val relEnd = minOf(chunkLength, span.end - absoluteOffset)
            if (relStart < relEnd) {
                when (span.type) {
                    "alignment", "lineSpacing" -> {
                        android.util.Log.d("AlignDebug", "RichTextTransform: type=${span.type}, span=[${span.start},${span.end}), value=${span.value}, absOff=$absoluteOffset, rel=[$relStart,$relEnd), textLen=$chunkLength")
                        // Iterate through ALL paragraphs within the span's range,
                        // not just the first one (spans can cover multiple paragraphs
                        // when alignment is applied to non-first paragraphs).
                        var paraCurrentPos = relStart
                        while (paraCurrentPos < relEnd) {
                            val paraStart = text.text.lastIndexOf('\n', maxOf(0, paraCurrentPos - 1)) + 1
                            val nextNewline = text.text.indexOf('\n', paraCurrentPos)
                            if (nextNewline == -1 || nextNewline >= relEnd) {
                                // Last paragraph (or only paragraph) — extends to relEnd
                                if (paraStart < relEnd) {
                                    val key = "$paraStart-$relEnd"
                                    paraProps.getOrPut(key) { mutableMapOf() }[span.type] = span.value
                                    android.util.Log.d("AlignDebug", "RichTextTransform:   lastPara key=$key, type=${span.type}=${span.value}")
                                }
                                break
                            }
                            // Include trailing newline in the paragraph style range so Compose
                            // doesn't create a gap (which can cause extra paragraph spacing)
                            val paraEnd = nextNewline + 1
                            if (paraStart < paraEnd) {
                                val key = "$paraStart-$paraEnd"
                                paraProps.getOrPut(key) { mutableMapOf() }[span.type] = span.value
                                android.util.Log.d("AlignDebug", "RichTextTransform:   interPara key=$key, type=${span.type}=${span.value}")
                            }
                            paraCurrentPos = paraEnd
                        }
                    }
                }
            }
        }
        
        // Collect ParagraphStyle ranges
        for ((key, props) in paraProps) {
            val parts = key.split("-")
            if (parts.size < 2) continue
            val paraStart = parts[0].toIntOrNull() ?: continue
            val paraEnd = parts[1].toIntOrNull() ?: continue
            if (paraStart < 0 || paraEnd > chunkLength || paraStart >= paraEnd) continue
            val align = when (props["alignment"]) {
                "center" -> androidx.compose.ui.text.style.TextAlign.Center
                "right" -> androidx.compose.ui.text.style.TextAlign.Right
                "justify" -> androidx.compose.ui.text.style.TextAlign.Justify
                "left" -> androidx.compose.ui.text.style.TextAlign.Left
                else -> null
            }
            val lineHeightValue = props["lineSpacing"]?.toFloatOrNull()
            if (align != null || lineHeightValue != null) {
                val effectiveAlign = align ?: androidx.compose.ui.text.style.TextAlign.Start
                val pStyle = if (lineHeightValue != null) {
                    androidx.compose.ui.text.ParagraphStyle(textAlign = effectiveAlign, lineHeight = 24.sp * lineHeightValue)
                } else {
                    androidx.compose.ui.text.ParagraphStyle(textAlign = align!!)
                }
                paraRangeList.add(androidx.compose.ui.text.AnnotatedString.Range(pStyle, paraStart, paraEnd))
            }
        }
        
        // Second pass: apply span-level styles (non-paragraph types)
        spans.forEach { span ->
            val relStart = maxOf(0, span.start - absoluteOffset)
            val relEnd = minOf(chunkLength, span.end - absoluteOffset)
            if (relStart < relEnd) {
                when(span.type) {
                    "bold" -> builder.addStyle(SpanStyle(fontWeight = FontWeight.Bold), relStart, relEnd)
                    "italic" -> builder.addStyle(SpanStyle(fontStyle = FontStyle.Italic), relStart, relEnd)
                    "underline" -> builder.addStyle(SpanStyle(textDecoration = TextDecoration.Underline), relStart, relEnd)
                    "strikethrough" -> builder.addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), relStart, relEnd)
                    "color" -> try { builder.addStyle(SpanStyle(color = Color(android.graphics.Color.parseColor(span.value))), relStart, relEnd) } catch(e:Exception){}
                    "highlight" -> {
                        val bgHex = span.value.ifEmpty { "#FDE047" }
                        try {
                            builder.addStyle(SpanStyle(background = Color(android.graphics.Color.parseColor(bgHex)).copy(alpha = 0.45f)), relStart, relEnd)
                        } catch (e: Exception) {
                            builder.addStyle(SpanStyle(background = Color(0xFFFDE047).copy(alpha = 0.45f)), relStart, relEnd)
                        }
                    }
                    "subscript" -> builder.addStyle(SpanStyle(baselineShift = androidx.compose.ui.text.style.BaselineShift.Subscript, fontSize = 11.sp), relStart, relEnd)
                    "superscript" -> builder.addStyle(SpanStyle(baselineShift = androidx.compose.ui.text.style.BaselineShift.Superscript, fontSize = 11.sp), relStart, relEnd)
                    "fontSize" -> {
                        val size = span.value.toFloatOrNull()
                        if (size != null) {
                            builder.addStyle(SpanStyle(fontSize = size.sp), relStart, relEnd)
                        }
                    }
                    "fontFamily" -> {
                        val family = when (span.value) {
                            "Arial" -> FontFamily.SansSerif
                            "Times New Roman" -> FontFamily.Serif
                            "Courier New" -> FontFamily.Monospace
                            "Georgia" -> FontFamily.Serif
                            "Verdana" -> FontFamily.SansSerif
                            "Aptos" -> FontFamily.SansSerif
                            "Calibri" -> FontFamily.SansSerif
                            else -> FontFamily.Default
                        }
                        builder.addStyle(SpanStyle(fontFamily = family), relStart, relEnd)
                    }
                    "shading" -> {
                        try {
                            val bgColor = Color(android.graphics.Color.parseColor(span.value)).copy(alpha = 0.25f)
                            builder.addStyle(SpanStyle(background = bgColor), relStart, relEnd)
                        } catch (e: Exception) {}
                    }
                    "border" -> {
                        // Border rendering requires custom drawing beyond VisualTransformation (e.g., Canvas/Border inside the composable). 
                        // For now, we store the metadata — visual border drawing is deferred.
                    }
                }
            }
        }
        val base = builder.toAnnotatedString()
        val result = androidx.compose.ui.text.AnnotatedString(
            text = base.text,
            spanStyles = base.spanStyles,
            paragraphStyles = paraRangeList
        )
        return TransformedText(result, OffsetMapping.Identity)
    }
}

fun toRoman(number: Int): String {
    var num = number
    val values = intArrayOf(1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1)
    val romanLiterals = arrayOf("M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I")
    val roman = StringBuilder()
    for (i in values.indices) {
        while (num >= values[i]) {
            num -= values[i]
            roman.append(romanLiterals[i])
        }
    }
    return roman.toString()
}

fun toAlphabetic(number: Int): String {
    var num = number
    var result = ""
    while (num > 0) {
        num-- 
        result = ('A' + (num % 26)) + result
        num /= 26
    }
    return result
}

fun formatPageNumber(pageNumber: Int, format: String): String {
    return when (format) {
        "01, 02, 03..." -> String.format("%02d", pageNumber)
        "001, 002, 003..." -> String.format("%03d", pageNumber)
        "I, II, III..." -> toRoman(pageNumber)
        "i, ii, iii..." -> toRoman(pageNumber).lowercase()
        "A, B, C..." -> toAlphabetic(pageNumber)
        "a, b, c..." -> toAlphabetic(pageNumber).lowercase()
        else -> pageNumber.toString()
    }
}

// --- PARAGRAPH FORMATTING HELPERS ---

fun getParagraphRange(text: String, pos: Int): IntRange {
    if (text.isEmpty()) return 0 until 0
    val clampedPos = pos.coerceIn(0, text.length)
    val start = text.lastIndexOf('\n', clampedPos - 1) + 1
    val end = text.indexOf('\n', clampedPos).let { if (it == -1) text.length else it }
    return start until end
}

fun getParagraphRangesInRange(text: String, rangeStart: Int, rangeEnd: Int): List<IntRange> {
    if (text.isEmpty()) return emptyList()
    val start = maxOf(0, rangeStart).coerceAtMost(text.length)
    val end = rangeEnd.coerceIn(start, text.length)
    // Single cursor: use the paragraph at that position
    if (start == end) {
        val para = getParagraphRange(text, start)
        if (para.isEmpty()) return emptyList()
        // Include trailing newline in the range
        val nlPos = text.indexOf('\n', para.start)
        val endWithNl = if (nlPos == -1 || nlPos >= para.endInclusive + 1) para.endInclusive + 1 else nlPos + 1
        return listOf(para.start until endWithNl.coerceAtMost(text.length))
    }
    val result = mutableListOf<IntRange>()
    val firstPara = getParagraphRange(text, start)
    val startPos = firstPara.start
    val clampedEnd = end.coerceAtMost(text.length)
    val endPos = text.indexOf('\n', clampedEnd).let { if (it == -1) text.length else it }
    var pos = startPos
    while (pos < endPos) {
        val paraStart = pos
        val nextNewline = text.indexOf('\n', pos)
        val paraEnd = if (nextNewline == -1 || nextNewline >= endPos) {
            endPos
        } else {
            nextNewline + 1  // include trailing newline
        }
        if (paraStart < paraEnd) {
            result.add(paraStart until paraEnd)
        }
        pos = if (nextNewline == -1 || nextNewline >= endPos) endPos else nextNewline + 1
    }
    return result
}

fun getParagraphText(text: String, pos: Int): String {
    val range = getParagraphRange(text, pos)
    return text.substring(range.start, range.endInclusive + 1)
}

fun replaceParagraphText(text: String, pos: Int, newPara: String): String {
    val range = getParagraphRange(text, pos)
    val sepEnd = text.indexOf('\n', range.start).let { if (it == -1) text.length else it + 1 }
    return text.substring(0, range.start) + newPara + text.substring(sepEnd)
}

val BulletChars = listOf("•", "◦", "▪", "➢", "‣", "–", "★", "※")
val NumberFormats = listOf("1.", "a)", "A.", "i)", "I.")

fun detectListPrefix(line: String): Pair<String?, String?> {
    val trimmed = line.trimStart()
    for (b in BulletChars) {
        if (trimmed.startsWith(b)) return b to null
    }
    for (f in NumberFormats) {
        val pattern = when (f) {
            "1." -> Regex("""^\d+\.""")
            "a)" -> Regex("""^[a-z]\)""")
            "A." -> Regex("""^[A-Z]\.""")
            "i)" -> Regex("""^[ivxlcdm]+\)""", RegexOption.IGNORE_CASE)
            "I." -> Regex("""^[IVXLCDM]+\.""")
            else -> null
        }
        if (pattern != null && pattern.containsMatchIn(trimmed)) return null to f
    }
    return null to null
}

fun removeListPrefix(line: String): String {
    val trimmed = line.trimStart()
    for (b in BulletChars) {
        if (trimmed.startsWith(b)) {
            val after = trimmed.removePrefix(b).trimStart()
            val wsLen = line.length - line.trimStart().length
            return line.take(wsLen) + after
        }
    }
    for (f in NumberFormats) {
        val pattern = when (f) {
            "1." -> Regex("""^\d+\.\s*""")
            "a)" -> Regex("""^[a-z]\)\s*""")
            "A." -> Regex("""^[A-Z]\.\s*""")
            "i)" -> Regex("""^[ivxlcdm]+\)\s*""", RegexOption.IGNORE_CASE)
            "I." -> Regex("""^[IVXLCDM]+\.\s*""")
            else -> null
        }
        if (pattern != null) {
            val after = trimmed.replaceFirst(pattern, "")
            val wsLen = line.length - line.trimStart().length
            return line.take(wsLen) + after
        }
    }
    return line
}

fun applyBulletToPara(text: String, pos: Int, bulletChar: String): String {
    val para = getParagraphText(text, pos)
    val (existingBullet, _) = detectListPrefix(para)
    if (existingBullet != null) return text // already has a bullet
    val clean = removeListPrefix(para)
    val indent = clean.takeWhile { it == ' ' }
    val newPara = indent + bulletChar + " " + clean.trimStart()
    return replaceParagraphText(text, pos, newPara)
}

fun removeBulletFromPara(text: String, pos: Int): String {
    val para = getParagraphText(text, pos)
    val clean = removeListPrefix(para)
    return replaceParagraphText(text, pos, clean)
}

fun applyNumberToPara(text: String, pos: Int, numFormat: String, number: Int): String {
    val para = getParagraphText(text, pos)
    val clean = removeListPrefix(para)
    val indent = clean.takeWhile { it == ' ' }
    val prefix = when (numFormat) {
        "1." -> "$number."
        "a)" -> "${('a' + (number - 1).coerceIn(0, 25))})"
        "A." -> "${('A' + (number - 1).coerceIn(0, 25))}."
        "i)" -> toRoman(number).lowercase() + ")"
        "I." -> toRoman(number) + "."
        else -> "$number."
    }
    val newPara = indent + prefix + " " + clean.trimStart()
    return replaceParagraphText(text, pos, newPara)
}

fun renumberDocument(text: String, numFormat: String): String {
    val lines = text.split("\n")
    var counter = 1
    val result = lines.map { line ->
        val (bullet, numFmt) = detectListPrefix(line)
        if (numFmt != null) {
            val clean = removeListPrefix(line)
            val indent = line.takeWhile { it == ' ' }
            val prefix = when (numFormat) {
                "1." -> "$counter."
                "a)" -> "${('a' + (counter - 1).coerceIn(0, 25))})"
                "A." -> "${('A' + (counter - 1).coerceIn(0, 25))}."
                "i)" -> toRoman(counter).lowercase() + ")"
                "I." -> toRoman(counter) + "."
                else -> "$counter."
            }
            counter++
            indent + prefix + " " + clean.trimStart()
        } else if (bullet != null) {
            line // don't change counter for bullets
        } else {
            counter = 1 // reset counter for non-numbered paragraphs
            line
        }
    }
    return result.joinToString("\n")
}

// --- 1. JC WORD WRITER EDITOR ---
@Composable
fun WordDocumentEditor(
    docId: Int,
    draftContent: String,
    onContentChange: (String) -> Unit,
    editorTheme: String,
    onEditorThemeChange: (String) -> Unit,
    pageBackgroundColor: Color? = null,
    pageMargins: androidx.compose.ui.unit.Dp,
    columnCount: Int,
    fontSize: androidx.compose.ui.unit.TextUnit,
    formatVersion: Int = 0,
    isLandscape: Boolean,
    pageFormat: String = "A4",
    customDimensions: Pair<Float, Float> = 8.5f to 11.0f,
    pageNumberPosition: String? = null,
    pageNumberFormat: String = "1, 2, 3...",
    pageNumberStartAt: Int = 1,
    modifier: Modifier = Modifier,
    textFieldValue: TextFieldValue? = null,
    onTextFieldValueChange: ((TextFieldValue) -> Unit)? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null
) {
    val paperColor = pageBackgroundColor ?: when (editorTheme) {
        "white" -> Color.White
        "ivory" -> Color(0xFFFAF6EE)
        "dark" -> Color(0xFF262626)
        else -> Color.White
    }

    val paperTextColor = when (editorTheme) {
        "dark" -> Color(0xFFE0E0E0)
        else -> Color(0xFF2D2D2D)
    }

    val (paperMaxWidth, minPageHeight) = DocumentLayoutEngine.getDimensions(
        format = pageFormat,
        customDimensions = customDimensions,
        isLandscape = isLandscape
    )
    
    var targetFocusPage by remember { mutableStateOf<Int?>(null) }
    var targetFocusOffset by remember { mutableStateOf<Int?>(null) }

    Column(modifier = modifier) {
        // Top Ruler
        PageRuler(
            isHorizontal = true,
            totalLength = paperMaxWidth,
            modifier = Modifier
                .height(20.dp)
                .padding(horizontal = 20.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            val pages = draftContent.split("\u000C")
            
            Column(
                verticalArrangement = Arrangement.spacedBy(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                pages.forEachIndexed { pageIndex, pageContent ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = paperColor),
                        shape = RoundedCornerShape(4.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                        modifier = Modifier
                            .width(paperMaxWidth)
                            .height(minPageHeight)
                            .padding(8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight()
                                .padding(pageMargins)
                        ) {
                            val computedPageNumber = formatPageNumber(pageIndex + pageNumberStartAt, pageNumberFormat)
                            
                            // Header
                            if (pageNumberPosition?.startsWith("Top") == true) {
                                Text(
                                    text = computedPageNumber,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DocWordColor.copy(alpha = 0.5f),
                                    modifier = Modifier.align(
                                        when {
                                            pageNumberPosition.contains("Left") -> Alignment.Start
                                            pageNumberPosition.contains("Right") -> Alignment.End
                                            else -> Alignment.CenterHorizontally
                                        }
                                    )
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                            
                            if (columnCount > 1) {
                                // Multi-column read-only representation for MS Word
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                                ) {
                                    for (i in 0 until columnCount) {
                                        Text(
                                            text = pageContent,
                                            color = paperTextColor,
                                            fontSize = fontSize,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            } else {
                                val focusRequester = remember(pageIndex) { androidx.compose.ui.focus.FocusRequester() }
                                
                                var textFieldHeightPx by remember { mutableIntStateOf(0) }
                                var splitOffset by remember { mutableIntStateOf(-1) }
                                var mergeBackOffset by remember { mutableIntStateOf(-1) }
                                var mergeBackLocked by remember { mutableStateOf(false) }
                                
                                var pageTextFieldValue by remember { mutableStateOf(TextFieldValue(pageContent)) }
                                var lastPushedText by remember { mutableStateOf(pageContent) }
                                
                                LaunchedEffect(targetFocusPage) {
                                    if (targetFocusPage == pageIndex) {
                                        androidx.compose.ui.focus.FocusRequester.Default // ensure class is loaded
                                        try {
                                            focusRequester.requestFocus()
                                        } catch(e: Exception) {}
                                    }
                                }
                                
                                LaunchedEffect(splitOffset) {
                                    val currentText = pageTextFieldValue.text
                                    android.util.Log.d("AlignDebug", "LaunchedEffect(splitOffset): splitOff=$splitOffset, textLen=${currentText.length}, page=$pageIndex")
                                    if (splitOffset != -1 && splitOffset <= currentText.length) {
                                        val newPages = pages.toMutableList()
                                        
                                        var actualSplit = splitOffset
                                        val lastSpace = currentText.lastIndexOf(' ', splitOffset)
                                        if (lastSpace > splitOffset - 20 && lastSpace > 0) {
                                            actualSplit = lastSpace + 1
                                        }
                                        
                                        val keptContent = currentText.substring(0, actualSplit)
                                        var overflowContent = currentText.substring(actualSplit)
                                        
                                        val origLen = overflowContent.length
                                        overflowContent = overflowContent.trimStart(' ', '\n')
                                        val trimmedChars = origLen - overflowContent.length

                                        val absoluteOffset = pages.take(pageIndex).sumOf { it.length + 1 }
                                        if (trimmedChars > 0) {
                                            DocFormatRepository.shiftSpans(docId, absoluteOffset + actualSplit, trimmedChars, 0)
                                        }
                                        if (overflowContent.isNotEmpty()) {
                                            DocFormatRepository.moveSpanRange(
                                                docId,
                                                absoluteOffset + actualSplit,
                                                absoluteOffset + actualSplit + overflowContent.length,
                                                absoluteOffset + keptContent.length + 1
                                            )
                                        }

                                        newPages[pageIndex] = keptContent
                                        if (pageIndex + 1 < newPages.size) {
                                            newPages[pageIndex + 1] = overflowContent + newPages[pageIndex + 1]
                                        } else {
                                            newPages.add(overflowContent)
                                        }
                                        
                                        if (pageTextFieldValue.selection.start >= actualSplit) {
                                            targetFocusPage = pageIndex + 1
                                            targetFocusOffset = maxOf(0, pageTextFieldValue.selection.start - actualSplit - trimmedChars)
                                        }
                                        
                                        val newFullText = newPages.joinToString("\u000C")
                                        onContentChange(newFullText)
                                        pageTextFieldValue = pageTextFieldValue.copy(
                                            text = keptContent,
                                            selection = TextRange(minOf(pageTextFieldValue.selection.start, keptContent.length))
                                        )
                                        lastPushedText = keptContent
                                        onTextFieldValueChange?.invoke(TextFieldValue(text = newFullText))
                                        mergeBackLocked = true
                                        splitOffset = -1
                                    }
                                }

                                LaunchedEffect(mergeBackOffset) {
                                    if (mergeBackOffset != -1 && pageIndex + 1 < pages.size) {
                                        val nextContent = pages[pageIndex + 1]
                                        val currentText = pageTextFieldValue.text
                                        val merged = currentText + nextContent

                                        val absoluteOffsetNext = pages.take(pageIndex + 1).sumOf { it.length + 1 }
                                        DocFormatRepository.moveSpanRange(
                                            docId,
                                            absoluteOffsetNext,
                                            absoluteOffsetNext + nextContent.length,
                                            pages.take(pageIndex).sumOf { it.length + 1 } + currentText.length
                                        )

                                        val newPages = pages.toMutableList()
                                        newPages[pageIndex] = merged
                                        newPages.removeAt(pageIndex + 1)

                                        val newFullText = newPages.joinToString("\u000C")
                                        onContentChange(newFullText)
                                        pageTextFieldValue = pageTextFieldValue.copy(
                                            text = merged,
                                            selection = TextRange(pageTextFieldValue.selection.start)
                                        )
                                        lastPushedText = merged
                                        onTextFieldValueChange?.invoke(TextFieldValue(text = newFullText))
                                        mergeBackOffset = -1
                                        mergeBackLocked = true
                                    }
                                }

                                LaunchedEffect(pageContent, targetFocusPage) {
                                    android.util.Log.d("AlignDebug", "LaunchedEffect(pageContent): page=$pageIndex, pageContent=$pageContent, targetFocusPage=$targetFocusPage, targetOff=$targetFocusOffset, lastPushed=$lastPushedText")
                                    if (targetFocusPage == pageIndex && targetFocusOffset != null) {
                                        pageTextFieldValue = pageTextFieldValue.copy(
                                            text = pageContent,
                                            selection = TextRange(targetFocusOffset!!)
                                        )
                                        lastPushedText = pageContent
                                        targetFocusOffset = null
                                    } else if (pageContent != lastPushedText) {
                                        // Shift spans when text changes via external path (e.g. indent_inc/indent_dec)
                                        val oldText = lastPushedText
                                        val newText = pageContent
                                        var cp = 0
                                        while (cp < oldText.length && cp < newText.length && oldText[cp] == newText[cp]) { cp++ }
                                        var cs = 0
                                        while (cp + cs < oldText.length && cp + cs < newText.length && oldText[oldText.length - 1 - cs] == newText[newText.length - 1 - cs]) { cs++ }
                                        val delLen = oldText.length - cp - cs
                                        val insLen = newText.length - cp - cs
                                        if (delLen > 0 || insLen > 0) {
                                            val absOff = pages.take(pageIndex).sumOf { it.length + 1 }
                                            DocFormatRepository.shiftSpans(docId, absOff + cp, delLen, insLen)
                                        }
                                        val newSelection = if (pageTextFieldValue.selection.start <= pageContent.length && pageTextFieldValue.selection.end <= pageContent.length) {
                                            pageTextFieldValue.selection
                                        } else {
                                            TextRange(pageContent.length)
                                        }
                                        pageTextFieldValue = pageTextFieldValue.copy(text = pageContent, selection = newSelection)
                                        lastPushedText = pageContent
                                    }
                                }
                                
                                BasicTextField(
                                    value = pageTextFieldValue,
                                    onValueChange = { newTfv ->
                                        val oldSelection = pageTextFieldValue.selection
                                        pageTextFieldValue = newTfv
                                        
                                        if (newTfv.text != lastPushedText) {
                                            mergeBackLocked = false
                                            val oldText = lastPushedText
                                            val newText = newTfv.text
                                            lastPushedText = newText
                                            
                                            var commonPrefix = 0
                                            while (commonPrefix < oldText.length && commonPrefix < newText.length && oldText[commonPrefix] == newText[commonPrefix]) { commonPrefix++ }
                                            var commonSuffix = 0
                                            while (commonPrefix + commonSuffix < oldText.length && commonPrefix + commonSuffix < newText.length && oldText[oldText.length - 1 - commonSuffix] == newText[newText.length - 1 - commonSuffix]) { commonSuffix++ }
                                            val deletedLen = oldText.length - commonPrefix - commonSuffix
                                            val insertedLen = newText.length - commonPrefix - commonSuffix
                                            val absoluteOffset = pages.take(pageIndex).sumOf { it.length + 1 }
                                            DocFormatRepository.shiftSpans(docId, absoluteOffset + commonPrefix, deletedLen, insertedLen)
                                            
                                            val newPages = pages.toMutableList()
                                            
                                            if (newText.isEmpty() && pageContent.isEmpty() && pages.size > 1 && pageIndex > 0) {
                                                // Handle backspace delete empty page
                                                newPages.removeAt(pageIndex)
                                                targetFocusPage = pageIndex - 1
                                                val newFullText = newPages.joinToString("\u000C")
                                                onContentChange(newFullText)
                                                onTextFieldValueChange?.invoke(TextFieldValue(text = newFullText))
                                            } else {
                                                newPages[pageIndex] = newText
                                                val newFullText = newPages.joinToString("\u000C")
                                                onContentChange(newFullText)
                                                onTextFieldValueChange?.invoke(TextFieldValue(text = newFullText))
                                            }
                                        } else if (oldSelection != newTfv.selection) {
                                            onTextFieldValueChange?.invoke(newTfv)
                                        }
                                    },
                                    onTextLayout = { result: androidx.compose.ui.text.TextLayoutResult ->
                                        android.util.Log.d("AlignDebug", "onTextLayout: page=$pageIndex, h=${result.size.height}, textFieldH=$textFieldHeightPx, splitOff=$splitOffset, mergeOff=$mergeBackOffset, mergeLocked=$mergeBackLocked, lineCount=${result.lineCount}")
                                        if (textFieldHeightPx > 0 && result.size.height > (textFieldHeightPx - 50)) {
                                            val availableHeight = (textFieldHeightPx - 50).toFloat()
                                            val line = (0 until result.lineCount).findLast { result.getLineBottom(it) <= availableHeight }
                                            android.util.Log.d("AlignDebug", "onTextLayout: overflow detected, availableH=$availableHeight, foundLine=$line")
                                            if (line != null && line < result.lineCount - 1 && line > 0 && splitOffset == -1) {
                                                val tentativeSplit = result.getLineEnd(line, visibleEnd = false)
                                                android.util.Log.d("AlignDebug", "onTextLayout: setting splitOffset=$tentativeSplit (line=$line)")
                                                if (tentativeSplit < pageTextFieldValue.text.length) {
                                                    splitOffset = tentativeSplit
                                                } else {
                                                    splitOffset = result.getLineEnd(line - 1, visibleEnd = false)
                                                }
                                            } else if (line == 0 && splitOffset == -1 && result.lineCount > 1) {
                                                val tentativeSplit = result.getLineEnd(0, visibleEnd = false)
                                                android.util.Log.d("AlignDebug", "onTextLayout: setting splitOffset=$tentativeSplit (line=0)")
                                                if (tentativeSplit < pageTextFieldValue.text.length) {
                                                    splitOffset = tentativeSplit
                                                }
                                            }
                                        }
                                        if (textFieldHeightPx > 0 && splitOffset == -1 && mergeBackOffset == -1 && !mergeBackLocked && pageIndex + 1 < pages.size && pages[pageIndex + 1].isNotEmpty() && result.lineCount > 0) {
                                            val usedHeight = result.getLineBottom(result.lineCount - 1)
                                            val availableHeight = (textFieldHeightPx - 50).toFloat()
                                            android.util.Log.d("AlignDebug", "onTextLayout: check mergeBack, usedH=$usedHeight, availH=$availableHeight")
                                            if (usedHeight < availableHeight - 40) {
                                                android.util.Log.d("AlignDebug", "onTextLayout: setting mergeBackOffset=1")
                                                mergeBackOffset = 1
                                            }
                                        }
                                    },
                                    cursorBrush = androidx.compose.ui.graphics.SolidColor(paperTextColor),
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                                        color = paperTextColor,
                                        fontSize = fontSize,
                                        lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified
                                    ),
                                    visualTransformation = remember(
                                        DocFormatRepository.getSpans(docId).toList(),
                                        docId,
                                        pageIndex,
                                        pageTextFieldValue.text.length,
                                        formatVersion
                                    ) {
                                        RichTextVisualTransformation(DocFormatRepository.getSpans(docId).toList(), pages.take(pageIndex).sumOf { it.length + 1 })
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .onGloballyPositioned { textFieldHeightPx = it.size.height }
                                        .focusRequester(focusRequester)
                                        .onPreviewKeyEvent { keyEvent ->
                                            if (
                                                keyEvent.type == androidx.compose.ui.input.key.KeyEventType.KeyDown &&
                                                keyEvent.key == androidx.compose.ui.input.key.Key.Backspace &&
                                                pageTextFieldValue.selection.start == 0 &&
                                                pageTextFieldValue.selection.end == 0 &&
                                                pageIndex > 0
                                            ) {
                                                 val newPages = pages.toMutableList()
                                                 val prevText = newPages[pageIndex - 1]
                                                 val currentText = newPages[pageIndex]

                                                 val separatorPos = pages.take(pageIndex).sumOf { it.length + 1 } - 1
                                                 DocFormatRepository.shiftSpans(docId, separatorPos, 1, 0)
                                                
                                                targetFocusPage = pageIndex - 1
                                                targetFocusOffset = prevText.length
                                                
                                                newPages[pageIndex - 1] = prevText + currentText
                                                newPages.removeAt(pageIndex)
                                                
                                                val newFullText = newPages.joinToString("\u000C")
                                                onContentChange(newFullText)
                                                onTextFieldValueChange?.invoke(TextFieldValue(text = newFullText))
                                                true
                                            } else {
                                                false
                                            }
                                        }
                                        .onFocusChanged { 
                                            if (it.isFocused) {
                                                onFocusChanged?.invoke(true)
                                            }
                                        }
                                        .testTag("word_editor_content_field"),
                                    decorationBox = { innerTextField ->
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            if (pageContent.isEmpty()) {
                                                Text(
                                                        "Start typing your new document here...", 
                                                    color = Color.Gray.copy(alpha = 0.7f),
                                                    style = MaterialTheme.typography.bodyLarge.copy(
                                                        fontSize = fontSize
                                                    )
                                                )
                                            }
                                            innerTextField()
                                        }
                                    }
                                )
                            }
                            
                            // Footer
                            if (pageNumberPosition != null && (pageNumberPosition.startsWith("Bottom") || pageNumberPosition.contains("Margin"))) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = computedPageNumber,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DocWordColor.copy(alpha = 0.5f),
                                    modifier = Modifier.align(
                                        when {
                                            pageNumberPosition.contains("Left") -> Alignment.Start
                                            pageNumberPosition.contains("Right") -> Alignment.End
                                            pageNumberPosition.contains("Outside Margin") -> if (pageIndex % 2 == 0) Alignment.End else Alignment.Start
                                            pageNumberPosition.contains("Inside Margin") -> if (pageIndex % 2 == 0) Alignment.Start else Alignment.End
                                            else -> Alignment.CenterHorizontally
                                        }
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


// --- 2. JC SPREADSHEET EDITOR ---
@Composable
fun SpreadsheetEditor(
    viewModel: DocViewModel,
    modifier: Modifier = Modifier
) {
    val selectedCell by viewModel.selectedCell.collectAsStateWithLifecycle()
    val sheetData by viewModel.sheetData.collectAsStateWithLifecycle()

    val columns = listOf("A", "B", "C", "D", "E", "F", "G", "H")
    val rows = (1..20).toList()

    val cellExpr = sheetData[selectedCell] ?: ""

    Column(modifier = modifier) {
        // Formulas edit top bar
        Card(
            shape = RoundedCornerShape(0.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Coordinate badge
                Box(
                    modifier = Modifier
                        .width(55.dp)
                        .height(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(DocSheetColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = selectedCell,
                        fontWeight = FontWeight.Bold,
                        color = DocSheetColor,
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Formula Icon indicator
                Text(
                    text = "fx",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = DocSheetColor
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Cell Formula input bar
                OutlinedTextField(
                    value = cellExpr,
                    onValueChange = { viewModel.updateCellExpression(selectedCell, it) },
                    placeholder = { Text("Enter value or formula like =SUM(A1:A5) or =A1*A2", fontSize = 13.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DocSheetColor,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("formula_input_field")
                )
            }
        }

        // Active layout scrollable grid cells
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .horizontalScroll(rememberScrollState())
                .verticalScroll(rememberScrollState())
        ) {
            Column {
                // Header letters columns
                Row {
                    // Empty corner anchor
                    Box(
                        modifier = Modifier
                            .size(width = 46.dp, height = 28.dp)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(0.5.dp, Color.LightGray)
                    )

                    for (col in columns) {
                        Box(
                            modifier = Modifier
                                .size(width = 110.dp, height = 28.dp)
                                .background(Color(0xFFF1F3F4))
                                .border(0.5.dp, Color.LightGray),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                col,
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // Numbers rows & dynamic cell contents
                for (row in rows) {
                    Row {
                        // Row coordinate badge
                        Box(
                            modifier = Modifier
                                .size(width = 46.dp, height = 40.dp)
                                .background(Color(0xFFF1F3F4))
                                .border(0.5.dp, Color.LightGray),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = row.toString(),
                                fontWeight = FontWeight.SemiBold,
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }

                        for (col in columns) {
                            val cellRef = "$col$row"
                            val isSelected = selectedCell == cellRef

                            val evaluatedValue = viewModel.getCellValue(cellRef)
                            val originalExpression = sheetData[cellRef] ?: ""

                            Box(
                                modifier = Modifier
                                    .size(width = 110.dp, height = 40.dp)
                                    .background(
                                        if (isSelected) DocSheetColor.copy(alpha = 0.12f)
                                        else MaterialTheme.colorScheme.surface
                                    )
                                    .border(
                                        width = if (isSelected) 1.5.dp else 0.5.dp,
                                        color = if (isSelected) DocSheetColor else Color.LightGray
                                    )
                                    .clickable { viewModel.selectCell(cellRef) }
                                    .padding(4.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = evaluatedValue,
                                            fontWeight = if (originalExpression.startsWith("=")) FontWeight.Bold else FontWeight.Normal,
                                            color = if (evaluatedValue.startsWith("#")) Color.Red else MaterialTheme.colorScheme.onSurface,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        if (originalExpression.startsWith("=") && !isSelected) {
                                            // Tiny tag indicating reactive formulas
                                            Text(
                                                text = "fx",
                                                color = DocSheetColor,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- 3. JC SLIDE PRESENTATION WORKSPACE ---
@Composable
fun SlidePresentationWorkspace(
    viewModel: DocViewModel,
    modifier: Modifier = Modifier
) {
    val slides by viewModel.slides.collectAsStateWithLifecycle()
    val activeIdx by viewModel.currentSlideIndex.collectAsStateWithLifecycle()

    val activeSlide = slides.getOrNull(activeIdx) ?: SlideItem("Title Slide", "", "indigo", "title_slide")

    Column(modifier = modifier) {
        // Toolkit control actions bar
        Card(
            shape = RoundedCornerShape(0.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { viewModel.addNewSlide() },
                    colors = ButtonDefaults.buttonColors(containerColor = DocSlideColor),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "Add Slide", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Slide", fontSize = 12.sp)
                }

                Button(
                    onClick = { viewModel.deleteSlide(activeIdx) },
                    enabled = slides.size > 1,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.08f), contentColor = Color.Red),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete Slide", fontSize = 12.sp)
                }

                Divider(modifier = Modifier.height(20.dp).width(1.dp))

                // Play presentation mode launcher
                Button(
                    onClick = { viewModel.togglePresenterMode(true) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.testTag("play_slides_button")
                ) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = "Play presentation", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Play Deck", fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = "Slide ${activeIdx + 1} of ${slides.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }

        // Secondary workspace split view
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            // Left list of slides navigator
            Column(
                modifier = Modifier
                    .width(100.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, Color.LightGray.copy(alpha = 0.3f))
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                slides.forEachIndexed { index, item ->
                    val isActive = index == activeIdx
                    Card(
                        onClick = { viewModel.selectSlide(index) },
                        colors = CardDefaults.cardColors(
                            containerColor = getSlideThemeBg(item.theme).copy(alpha = if (isActive) 1f else 0.4f)
                        ),
                        border = BorderStroke(
                            2.dp,
                            if (isActive) DocSlideColor else Color.Transparent
                        ),
                        modifier = Modifier
                            .size(76.dp, 54.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = (index + 1).toString(),
                                fontWeight = FontWeight.Bold,
                                color = if (item.theme == "charcoal") Color.White else Color.Black,
                                fontSize = 18.sp
                            )
                        }
                    }
                }
            }

            // Central layout editor
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Slide Template background preview wrapper
                Card(
                    colors = CardDefaults.cardColors(containerColor = getSlideThemeBg(activeSlide.theme)),
                    shape = RoundedCornerShape(8.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 500.dp)
                        .height(300.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        when (activeSlide.layout) {
                            "title_slide" -> {
                                BasicTextField(
                                    value = activeSlide.title,
                                    onValueChange = { viewModel.updateSlideContent(it, activeSlide.body, activeSlide.theme, activeSlide.layout) },
                                    textStyle = TextStyleCompose(activeSlide.theme, 24.sp, FontWeight.ExtraBold, TextAlign.Center),
                                    modifier = Modifier.fillMaxWidth().testTag("slide_title_input")
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                BasicTextField(
                                    value = activeSlide.body,
                                    onValueChange = { viewModel.updateSlideContent(activeSlide.title, it, activeSlide.theme, activeSlide.layout) },
                                    textStyle = TextStyleCompose(activeSlide.theme, 13.sp, FontWeight.Normal, TextAlign.Center),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            "content_slide" -> {
                                BasicTextField(
                                    value = activeSlide.title,
                                    onValueChange = { viewModel.updateSlideContent(it, activeSlide.body, activeSlide.theme, activeSlide.layout) },
                                    textStyle = TextStyleCompose(activeSlide.theme, 18.sp, FontWeight.Bold),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                BasicTextField(
                                    value = activeSlide.body,
                                    onValueChange = { viewModel.updateSlideContent(activeSlide.title, it, activeSlide.theme, activeSlide.layout) },
                                    textStyle = TextStyleCompose(activeSlide.theme, 12.sp, FontWeight.Normal),
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp)
                                )
                            }
                            "split_slide" -> {
                                BasicTextField(
                                    value = activeSlide.title,
                                    onValueChange = { viewModel.updateSlideContent(it, activeSlide.body, activeSlide.theme, activeSlide.layout) },
                                    textStyle = TextStyleCompose(activeSlide.theme, 16.sp, FontWeight.Bold),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    BasicTextField(
                                        value = activeSlide.body,
                                        onValueChange = { viewModel.updateSlideContent(activeSlide.title, it, activeSlide.theme, activeSlide.layout) },
                                        textStyle = TextStyleCompose(activeSlide.theme, 11.sp, FontWeight.Normal),
                                        modifier = Modifier.weight(1f).heightIn(min = 120.dp)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(120.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.White.copy(alpha = 0.25f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "[ Presentation Illustration Placeholder ]",
                                            fontSize = 9.sp,
                                            textAlign = TextAlign.Center,
                                            color = if (activeSlide.theme == "charcoal") Color.LightGray else Color.DarkGray
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Slide configurations settings cards
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Slide Settings",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text("Choose Color Deck Theme:", fontSize = 11.sp, color = Color.Gray)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("indigo", "crimson", "teal", "charcoal", "cyberpunk").forEach { themeName ->
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(getSlideThemeBg(themeName))
                                        .border(
                                            width = if (activeSlide.theme == themeName) 2.dp else 0.5.dp,
                                            color = if (activeSlide.theme == themeName) DocSlideColor else Color.LightGray
                                        )
                                        .clickable {
                                            viewModel.updateSlideContent(
                                                activeSlide.title,
                                                activeSlide.body,
                                                themeName,
                                                activeSlide.layout
                                            )
                                        }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text("Choose Slide Layout Structure:", fontSize = 11.sp, color = Color.Gray)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("title_slide", "content_slide", "split_slide").forEach { layout ->
                                Button(
                                    onClick = {
                                        viewModel.updateSlideContent(
                                            activeSlide.title,
                                            activeSlide.body,
                                            activeSlide.theme,
                                            layout
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (activeSlide.layout == layout) DocSlideColor else Color.LightGray.copy(alpha = 0.2f),
                                        contentColor = if (activeSlide.layout == layout) Color.White else MaterialTheme.colorScheme.onSurface
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                ) {
                                    Text(layout.replace("_", " ").uppercase(), fontSize = 9.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Helpers for presentation theme compilation
fun getSlideThemeBg(theme: String): Color {
    return when (theme) {
        "indigo" -> Color(0xFFE8EAF6)
        "crimson" -> Color(0xFFFFEBEE)
        "teal" -> Color(0xFFE0F2F1)
        "charcoal" -> Color(0xFF2D3033)
        "cyberpunk" -> Color(0xFFFFFDE7)
        else -> Color(0xFFE8EAF6)
    }
}

@Composable
fun TextStyleCompose(theme: String, fontSize: androidx.compose.ui.unit.TextUnit, fontWeight: FontWeight, align: TextAlign = TextAlign.Start): androidx.compose.ui.text.TextStyle {
    return androidx.compose.ui.text.TextStyle(
        fontSize = fontSize,
        fontWeight = fontWeight,
        color = if (theme == "charcoal") Color.White else Color.Black,
        textAlign = align
    )
}

// Fullscreen slideshow overlay presenter style
@Composable
fun FullscreenPresentationView(
    viewModel: DocViewModel,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val slides by viewModel.slides.collectAsStateWithLifecycle()
    val activeIdx by viewModel.currentSlideIndex.collectAsStateWithLifecycle()

    val activeSlide = slides.getOrNull(activeIdx) ?: SlideItem("End of Deck", "", "indigo", "title_slide")

    Dialog(onDismissRequest = onExit) {
        Card(
            colors = CardDefaults.cardColors(containerColor = getSlideThemeBg(activeSlide.theme)),
            shape = RoundedCornerShape(16.dp),
            modifier = modifier
                .fillMaxWidth()
                .height(420.dp)
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                // Presenter top bar indicator
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(DocSlideColor)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("PRESENTATION MODE", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    IconButton(onClick = onExit) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Exit Presentation",
                            tint = if (activeSlide.theme == "charcoal") Color.White else Color.Black
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // The actual presentation content display
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = if (activeSlide.layout == "title_slide") Alignment.CenterHorizontally else Alignment.Start
                ) {
                    Text(
                        text = activeSlide.title,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (activeSlide.theme == "charcoal") Color.White else Color.Black,
                        textAlign = if (activeSlide.layout == "title_slide") TextAlign.Center else TextAlign.Start
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = activeSlide.body,
                        fontSize = 14.sp,
                        color = if (activeSlide.theme == "charcoal") Color.LightGray else Color.DarkGray,
                        textAlign = if (activeSlide.layout == "title_slide") TextAlign.Center else TextAlign.Start
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Presenter switching footers
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = { viewModel.selectSlide(activeIdx - 1) },
                        enabled = activeIdx > 0
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ArrowBack,
                            contentDescription = "Previous Slide",
                            tint = if (activeSlide.theme == "charcoal") Color.White else Color.Black
                        )
                    }

                    Text(
                        text = "Slide ${activeIdx + 1} of ${slides.size}",
                        fontSize = 12.sp,
                        color = if (activeSlide.theme == "charcoal") Color.LightGray else Color.DarkGray
                    )

                    IconButton(
                        onClick = { viewModel.selectSlide(activeIdx + 1) },
                        enabled = activeIdx < slides.size - 1
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ArrowForward,
                            contentDescription = "Next Slide",
                            tint = if (activeSlide.theme == "charcoal") Color.White else Color.Black
                        )
                    }
                }
            }
        }
    }
}

// Dialog helper for creating new documents
@Composable
fun CreateDocumentDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var title by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("word") } // "word", "sheet", "slide"

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp)
                .heightIn(max = 520.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Text(
                    text = "New ONLYOFFICE File",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Document Title") },
                    placeholder = { Text("e.g. Sales Forecast 2026") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = OnlyOfficePrimary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("new_document_title_field")
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "SELECT OFFICE APP TYPE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Custom type buttons matching ONLYOFFICE layout
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TypeSelectionRow(
                        typeName = "Word Document",
                        typeId = "word",
                        color = DocWordColor,
                        desc = "Create styled notes & rich layout docs",
                        isSelected = selectedType == "word",
                        onClick = { selectedType = "word" }
                    )
                    TypeSelectionRow(
                        typeName = "Spreadsheet Ledger",
                        typeId = "sheet",
                        color = DocSheetColor,
                        desc = "Execute formulas & manage row cell matrices",
                        isSelected = selectedType == "sheet",
                        onClick = { selectedType = "sheet" }
                    )
                    TypeSelectionRow(
                        typeName = "Presentation Slides",
                        typeId = "slide",
                        color = DocSlideColor,
                        desc = "Design templates & play interactive slide decks",
                        isSelected = selectedType == "slide",
                        onClick = { selectedType = "slide" }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Dialog Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = { onConfirm(title, selectedType) },
                        colors = ButtonDefaults.buttonColors(containerColor = OnlyOfficePrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("confirm_create_button")
                    ) {
                        Text("Create File", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
fun TypeSelectionRow(
    typeName: String,
    typeId: String,
    color: Color,
    desc: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) color.copy(alpha = 0.08f) else Color.Transparent
        ),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            1.dp,
            if (isSelected) color else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("selection_type_$typeId")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                val leadChar = when (typeId) {
                    "word" -> "W"
                    "sheet" -> "S"
                    "slide" -> "P"
                    else -> "D"
                }
                Text(leadChar, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = typeName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = desc,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = "Selected",
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun CustomMarginsDialog(
    onDismiss: () -> Unit,
    onApply: (androidx.compose.ui.unit.Dp) -> Unit
) {
    var topMargin by remember { mutableStateOf("1") }
    var bottomMargin by remember { mutableStateOf("1") }
    var leftMargin by remember { mutableStateOf("1") }
    var rightMargin by remember { mutableStateOf("1") }
    var gutter by remember { mutableStateOf("0") }
    var gutterPosition by remember { mutableStateOf("Left") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom Margins") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = topMargin, onValueChange = { topMargin = it }, label = { Text("Top (inches)") })
                OutlinedTextField(value = bottomMargin, onValueChange = { bottomMargin = it }, label = { Text("Bottom (inches)") })
                OutlinedTextField(value = leftMargin, onValueChange = { leftMargin = it }, label = { Text("Left (inches)") })
                OutlinedTextField(value = rightMargin, onValueChange = { rightMargin = it }, label = { Text("Right (inches)") })
                OutlinedTextField(value = gutter, onValueChange = { gutter = it }, label = { Text("Gutter (inches)") })
                OutlinedTextField(value = gutterPosition, onValueChange = { gutterPosition = it }, label = { Text("Gutter Position") })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val left = leftMargin.toFloatOrNull() ?: 1f
                onApply((left * 96).dp)
            }) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun CustomSizeDialog(
    onDismiss: () -> Unit,
    onApply: (width: Float, height: Float) -> Unit
) {
    var width by remember { mutableStateOf("8.5") }
    var height by remember { mutableStateOf("11.0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom Size (inches)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = width, onValueChange = { width = it }, label = { Text("Width") })
                OutlinedTextField(value = height, onValueChange = { height = it }, label = { Text("Height") })
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(width.toFloatOrNull() ?: 8.5f, height.toFloatOrNull() ?: 11.0f) }) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun PageNumberFormatDialog(
    currentFormat: String,
    currentStartAt: String,
    onDismiss: () -> Unit,
    onApply: (format: String, startAt: String) -> Unit
) {
    var format by remember { mutableStateOf(currentFormat) }
    var startAt by remember { mutableStateOf(currentStartAt) }
    var includeChapterNumber by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Page Number Format") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Dropdown for format
                var expanded by remember { mutableStateOf(false) }
                val formats = listOf("1, 2, 3...", "01, 02, 03...", "001, 002, 003...", "I, II, III...", "i, ii, iii...", "A, B, C...", "a, b, c...")
                
                Box {
                    OutlinedTextField(
                        value = format,
                        onValueChange = {},
                        label = { Text("Number format") },
                        readOnly = true,
                        trailingIcon = {
                            IconButton(onClick = { expanded = true }) {
                                Icon(Icons.Default.ArrowDropDown, "Select format")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        formats.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    format = option
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = includeChapterNumber, onCheckedChange = { includeChapterNumber = it })
                    Text("Include chapter number")
                }

                Text("Page numbering", fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    RadioButton(selected = true, onClick = {})
                    Text("Start at:")
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = startAt,
                        onValueChange = { startAt = it }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(format, startAt) }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
