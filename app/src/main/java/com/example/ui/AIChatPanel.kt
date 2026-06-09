package com.example.ui

import kotlinx.coroutines.launch
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.db.ChatConversation
import com.example.db.ChatMessageEntity
import com.example.db.AiProviderConfig
import com.example.viewmodel.DocViewModel

data class ChatMessage(val isUser: Boolean, val text: String)

@Composable
fun AIChatPanel(
    onClose: () -> Unit,
    viewModel: DocViewModel,
    modifier: Modifier = Modifier
) {
    val isDarkTheme = isSystemInDarkTheme()
    val activeConv by viewModel.activeConversation.collectAsStateWithLifecycle()
    val messages by viewModel.aiMessages.collectAsStateWithLifecycle()
    val isAiLoading by viewModel.isAiLoading.collectAsStateWithLifecycle()
    val aiError by viewModel.aiError.collectAsStateWithLifecycle()
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val providers by viewModel.aiProviders.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    var showHistory by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(if (isDarkTheme) Color(0xFF121214) else Color(0xFFF3F4F6))
    ) {
        // Header Section
        HeaderSection(
            activeConv = activeConv,
            onClose = onClose,
            onShowHistory = { showHistory = true },
            onShowSettings = { showSettings = true },
            onNewChat = { viewModel.startNewConversation() },
            isDarkTheme = isDarkTheme
        )

        Divider(color = if (isDarkTheme) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.05f))

        Box(modifier = Modifier.weight(1f)) {
            if (activeConv == null && conversations.isEmpty()) {
                WelcomeView(isDarkTheme = isDarkTheme)
            } else {
                MessageList(
                    messages = messages,
                    isAiLoading = isAiLoading,
                    aiError = aiError,
                    isDarkTheme = isDarkTheme
                )
            }

            // History Overlay
            if (showHistory) {
                HistoryPanel(
                    conversations = conversations,
                    onSelect = { 
                        viewModel.selectConversation(it)
                        showHistory = false
                    },
                    onDelete = { viewModel.deleteConversation(it) },
                    onClose = { showHistory = false },
                    isDarkTheme = isDarkTheme
                )
            }

            // Settings Overlay
            if (showSettings) {
                SettingsPanel(
                    providers = providers,
                    onSelectProvider = { viewModel.selectProvider(it) },
                    onDeleteProvider = { viewModel.deleteProviderConfig(it) },
                    onUpdateProvider = { viewModel.updateProviderConfig(it) },
                    onConnectCustom = { name, key, url, model ->
                        viewModel.addCustomProvider(name, key, url, model)
                    },
                    onTestProvider = { viewModel.testProviderConnection(it) },
                    onClose = { showSettings = false },
                    isDarkTheme = isDarkTheme
                )
            }
        }

        // Input Area
        ChatInputArea(
            inputText = inputText,
            onTextChange = { inputText = it },
            onSend = {
                if (inputText.isNotBlank()) {
                    viewModel.sendMessage(inputText)
                    inputText = ""
                }
            },
            isAiLoading = isAiLoading,
            isDarkTheme = isDarkTheme
        )
    }
}

@Composable
fun HeaderSection(
    activeConv: ChatConversation?,
    onClose: () -> Unit,
    onShowHistory: () -> Unit,
    onShowSettings: () -> Unit,
    onNewChat: () -> Unit,
    isDarkTheme: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = activeConv?.title ?: "JCDocs AI",
                    fontWeight = FontWeight.Bold,
                    color = if (isDarkTheme) Color.White else Color.Black,
                    fontSize = 15.sp,
                    maxLines = 1
                )
                if (activeConv != null) {
                    Text(
                        text = "V1 Assistant",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onNewChat) {
                Icon(Icons.Default.AddComment, "New Chat", modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onShowHistory) {
                Icon(Icons.Default.History, "History", modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onShowSettings) {
                Icon(Icons.Default.Settings, "Settings", modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, "Close", modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun MessageList(
    messages: List<ChatMessageEntity>,
    isAiLoading: Boolean,
    aiError: String?,
    isDarkTheme: Boolean
) {
    val listState = rememberLazyListState()
    
    LaunchedEffect(messages.size, isAiLoading) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(messages) { message ->
            ChatBubble(
                role = message.role,
                text = message.content,
                isDarkTheme = isDarkTheme
            )
        }
        
        if (isAiLoading) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Thinking...", fontSize = 12.sp, color = Color.Gray)
                }
            }
        }
        
        if (aiError != null) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = aiError,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (aiError.contains("OpenRouter", ignoreCase = true) || aiError.contains("gemini-2.5-flash", ignoreCase = true)) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "💡 Explanation: You are currently using the OpenRouter AI Provider. The default model is 'google/gemini-2.5-flash:free', which runs Google's Gemini model hosted via the OpenRouter service. A '401 Missing Authentication header' error indicates your saved OpenRouter API key is invalid, incorrectly copied, or empty.",
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
                                fontSize = 11.sp,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatBubble(role: String, text: String, isDarkTheme: Boolean) {
    val isUser = role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Surface(
                modifier = Modifier.size(28.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Surface(
            color = if (isUser) MaterialTheme.colorScheme.primary else (if (isDarkTheme) Color(0xFF232326) else Color.White),
            shape = RoundedCornerShape(
                topStart = 16.dp, 
                topEnd = 16.dp, 
                bottomStart = if (isUser) 16.dp else 2.dp, 
                bottomEnd = if (isUser) 2.dp else 16.dp
            ),
            tonalElevation = if (isUser) 0.dp else 2.dp,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = if (isUser) Color.White else (if (isDarkTheme) Color.LightGray else Color.Black)
            )
        }

        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                modifier = Modifier.size(28.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Person,
                        null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ChatInputArea(
    inputText: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    isAiLoading: Boolean,
    isDarkTheme: Boolean
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(24.dp),
        color = if (isDarkTheme) Color(0xFF232326) else Color.White,
        tonalElevation = 4.dp,
        border = BorderStroke(1.dp, if (isDarkTheme) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = inputText,
                onValueChange = onTextChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 12.dp, horizontal = 8.dp),
                textStyle = TextStyle(
                    color = if (isDarkTheme) Color.White else Color.Black,
                    fontSize = 14.sp
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    if (inputText.isEmpty()) {
                        Text("Type a message...", color = Color.Gray, fontSize = 14.sp)
                    }
                    innerTextField()
                }
            )
            
            IconButton(
                onClick = onSend,
                enabled = inputText.isNotBlank() && !isAiLoading,
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        if (inputText.isNotBlank() && !isAiLoading) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.2f),
                        CircleShape
                    )
            ) {
                Icon(
                    Icons.Default.Send,
                    null,
                    tint = if (inputText.isNotBlank() && !isAiLoading) Color.White else Color.Gray,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun WelcomeView(isDarkTheme: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.AutoAwesome,
            null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "Welcome to JCDocs AI",
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = if (isDarkTheme) Color.White else Color.Black
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Your human-like conversational assistant.",
            fontSize = 14.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

@Composable
fun HistoryPanel(
    conversations: List<ChatConversation>,
    onSelect: (ChatConversation) -> Unit,
    onDelete: (ChatConversation) -> Unit,
    onClose: () -> Unit,
    isDarkTheme: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = (if (isDarkTheme) Color.Black else Color.White).copy(alpha = 0.95f)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Chat History", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, null) }
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (conversations.isEmpty()) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text("No history yet", color = Color.Gray)
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(conversations) { conv ->
                        HistoryItem(conv, onSelect, onDelete, isDarkTheme)
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryItem(
    conv: ChatConversation,
    onSelect: (ChatConversation) -> Unit,
    onDelete: (ChatConversation) -> Unit,
    isDarkTheme: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onSelect(conv) },
        shape = RoundedCornerShape(12.dp),
        color = if (isDarkTheme) Color(0xFF1E1E22) else Color(0xFFF9FAFB),
        border = BorderStroke(1.dp, if (isDarkTheme) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.05f))
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ChatBubbleOutline, null, modifier = Modifier.size(18.dp), tint = Color.Gray)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(conv.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                Text(conv.lastMessage, fontSize = 12.sp, color = Color.Gray, maxLines = 1)
            }
            IconButton(onClick = { onDelete(conv) }) {
                Icon(Icons.Default.DeleteOutline, null, modifier = Modifier.size(18.dp), tint = Color.Gray)
            }
        }
    }
}

@Composable
fun SettingsPanel(
    providers: List<AiProviderConfig>,
    onSelectProvider: (AiProviderConfig) -> Unit,
    onDeleteProvider: (AiProviderConfig) -> Unit,
    onUpdateProvider: (AiProviderConfig) -> Unit,
    onConnectCustom: (String, String, String, String) -> Unit,
    onTestProvider: suspend (AiProviderConfig) -> String,
    onClose: () -> Unit,
    isDarkTheme: Boolean
) {
    var name by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var modelBySpec by remember { mutableStateOf("") }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = (if (isDarkTheme) Color(0xFF121212) else Color(0xFFF8F9FA)).copy(alpha = 0.98f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Shield,
                        contentDescription = "Security",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("AI Provider Settings", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, null)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Security Notice Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isDarkTheme) Color(0xFF1E1E1E) else Color(0xFFE9ECEF)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        "🔒 How is your API Key Secured?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "You can configure your keys securely in two ways:\n\n" +
                        "1. Platform Ingress (Highly Recommended): Enter your keys in the AI Studio Secrets panel as GEMINI_API_KEY and OPENROUTER_API_KEY. They are injected at build-time securely and never committed to version control or leaked.\n\n" +
                        "2. Secured Device Sandbox: Enter your custom API key directly below. It is saved in the local Room SQLite Database on this device's private storage, making it completely private to you.",
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = if (isDarkTheme) Color.LightGray else Color.DarkGray
                    )
                }
            }

            Text("Select Active Provider", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(12.dp))

            // List Providers
            if (providers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Initializing AI Providers...", color = Color.Gray, fontSize = 13.sp)
                }
            } else {
                providers.forEach { p ->
                    val isActive = p.isActive
                    var localKey by remember(p.apiKey) { mutableStateOf(p.apiKey) }
                    var localModel by remember(p.modelId) { mutableStateOf(p.modelId ?: "") }

                    Card(
                        border = BorderStroke(
                            2.dp,
                            if (isActive) MaterialTheme.colorScheme.primary else Color.Transparent
                        ),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isActive) {
                                if (isDarkTheme) Color(0xFF233142) else Color(0xFFE8F0FE)
                            } else {
                                if (isDarkTheme) Color(0xFF1A1A1A) else Color(0xFFFFFFFF)
                            }
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .clickable {
                                onSelectProvider(p.copy(apiKey = localKey.trim(), modelId = localModel.trim()))
                            }
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = isActive,
                                        onClick = { onSelectProvider(p.copy(apiKey = localKey.trim(), modelId = localModel.trim())) }
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        p.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (p.isDefault) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        SuggestionChip(
                                            onClick = {},
                                            label = { Text("Built-In", fontSize = 10.sp) }
                                        )
                                    }
                                }

                                if (!p.isDefault) {
                                    IconButton(onClick = { onDeleteProvider(p) }) {
                                        Icon(
                                            Icons.Filled.Delete,
                                            contentDescription = "Delete",
                                            tint = Color.Red,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            // Editable configurations
                            Spacer(modifier = Modifier.height(8.dp))

                            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                                Text(
                                    "Configure Keys & Models for ${p.name}:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.Gray
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                // API Key custom override
                                OutlinedTextField(
                                    value = localKey,
                                    onValueChange = { localKey = it },
                                    label = { Text("API Key Override (Optional)", fontSize = 11.sp) },
                                    placeholder = { 
                                        Text(
                                            if (p.isDefault) "Using secure platform-injected key"
                                            else "Enter your private API key",
                                            fontSize = 12.sp
                                        ) 
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    shape = RoundedCornerShape(8.dp)
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // Custom Model Selector/ID
                                OutlinedTextField(
                                    value = localModel,
                                    onValueChange = { localModel = it },
                                    label = { Text("Model ID (Optional)", fontSize = 11.sp) },
                                    placeholder = {
                                        Text(
                                            when (p.name) {
                                                "Gemini" -> "gemini-3.5-flash"
                                                "OpenRouter" -> "google/gemini-2.5-flash:free"
                                                else -> "e.g. gpt-4"
                                            },
                                            fontSize = 12.sp
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp)
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                var testStatus by remember { mutableStateOf<String?>(null) }
                                var testingInProgress by remember { mutableStateOf(false) }
                                val scope = rememberCoroutineScope()

                                if (testStatus != null) {
                                    Text(
                                        text = testStatus!!,
                                        fontSize = 11.sp,
                                        color = if (testStatus!!.startsWith("SUCCESS")) Color(0xFF4CAF50) else Color(0xFFF44336),
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp, end = 4.dp)
                                    )
                                }

                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            testingInProgress = true
                                            testStatus = "Verifying connection..."
                                            scope.launch {
                                                val works = onTestProvider(
                                                    p.copy(apiKey = localKey.trim(), modelId = localModel.trim())
                                                )
                                                testStatus = works
                                                testingInProgress = false
                                            }
                                        },
                                        enabled = !testingInProgress,
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        if (testingInProgress) {
                                            CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                                            Spacer(modifier = Modifier.width(6.dp))
                                        }
                                        Text("Test Connection", fontSize = 12.sp)
                                    }

                                    Button(
                                        onClick = {
                                            onUpdateProvider(p.copy(apiKey = localKey.trim(), modelId = localModel.trim()))
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Icon(Icons.Filled.Save, null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Save Settings", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Text("Connect New Custom Provider", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(12.dp))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isDarkTheme) Color(0xFF1E1E1E) else Color(0xFFFFFFFF)
                ),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, if (isDarkTheme) Color(0xFF2C2C2C) else Color(0xFFE0E0E0)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SettingsField("Provider Name", name, { name = it }, "e.g. Groq, Lambda, DeepSeek", isDarkTheme)
                    SettingsField("API Key", key, { key = it }, "Masked for safety", isDarkTheme, isPassword = true)
                    SettingsField("Base URL", url, { url = it }, "e.g. https://api.deepseek.com/v1/", isDarkTheme)
                    SettingsField("Model ID", modelBySpec, { modelBySpec = it }, "e.g. deepseek-chat", isDarkTheme)

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            if (name.isNotBlank() && key.isNotBlank()) {
                                onConnectCustom(name.trim(), key.trim(), url.trim(), modelBySpec.trim())
                                // Clear fields
                                name = ""
                                key = ""
                                url = ""
                                modelBySpec = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Add, null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save & Add Custom Provider")
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsField(label: String, value: String, onValueChange: (String) -> Unit, placeholder: String, isDarkTheme: Boolean, isPassword: Boolean = false) {
    Column(modifier = Modifier.padding(bottom = 12.dp)) {
        Text(label, fontSize = 12.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, fontSize = 13.sp) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None
        )
    }
}
