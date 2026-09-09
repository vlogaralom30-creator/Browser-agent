package com.example.ai

import android.content.Context
import android.util.Log
import com.example.data.agent.AgentMessageEntity
import com.example.data.agent.AgentRepository
import com.example.data.agent.AgentTaskEntity
import com.example.data.agent.AgentTaskStepEntity
import com.example.ui.BrowserViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class AgentActivityItem(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val message: String,
    val type: ActivityType = ActivityType.INFO,
    val detail: String? = null
)

enum class ActivityType {
    OBSERVE, PLAN, ACT, VERIFY, SUCCESS, ERROR, CONFIRMATION, INFO
}

data class SensitiveConfirmationRequest(
    val taskId: String,
    val actionName: String,
    val actionDetails: String,
    val warningMessage: String,
    val pendingActionPayload: JSONObject
)

enum class AgentStatus {
    IDLE, RUNNING, PAUSED, WAITING_CONFIRMATION, COMPLETED, ERROR
}

data class AgentPointerState(
    val isVisible: Boolean = true,
    val xPercent: Float = 0.5f,
    val yPercent: Float = 0.5f,
    val actionText: String = "Observing page...",
    val isClicking: Boolean = false,
    val actionType: String = "OBSERVE"
)

data class YouTubeVideoItem(
    val title: String,
    val channel: String,
    val viewsRaw: String = "",
    val viewsNormalized: Long = 0L,
    val viewsFormatted: String = "",
    val uploadedRaw: String = "",
    val recencyDays: Double = 9999.0,
    val url: String = "",
    val videoId: String = "",
    val duration: String = "",
    val thumbnail: String = ""
)

class BrowserAgentController(
    private val context: Context,
    private val agentRepository: AgentRepository,
    private val viewModel: BrowserViewModel,
    private val scope: CoroutineScope
) {
    private val apiClient = GeminiApiClient(agentRepository)
    private val actionExecutor = BrowserActionExecutor(viewModel)

    private val _agentStatus = MutableStateFlow(AgentStatus.IDLE)
    val agentStatus: StateFlow<AgentStatus> = _agentStatus.asStateFlow()

    private val _currentTask = MutableStateFlow<AgentTaskEntity?>(null)
    val currentTask: StateFlow<AgentTaskEntity?> = _currentTask.asStateFlow()

    private val _activityLogs = MutableStateFlow<List<AgentActivityItem>>(emptyList())
    val activityLogs: StateFlow<List<AgentActivityItem>> = _activityLogs.asStateFlow()

    private val _pendingConfirmation = MutableStateFlow<SensitiveConfirmationRequest?>(null)
    val pendingConfirmation: StateFlow<SensitiveConfirmationRequest?> = _pendingConfirmation.asStateFlow()

    private val _pointerState = MutableStateFlow(AgentPointerState())
    val pointerState: StateFlow<AgentPointerState> = _pointerState.asStateFlow()

    private val _collectedVideoResults = MutableStateFlow<List<YouTubeVideoItem>>(emptyList())
    val collectedVideoResults: StateFlow<List<YouTubeVideoItem>> = _collectedVideoResults.asStateFlow()

    private var executionJob: Job? = null
    private var isInterrupted = false

    fun updatePointer(
        xPercent: Float? = null,
        yPercent: Float? = null,
        actionText: String? = null,
        isClicking: Boolean = false,
        actionType: String? = null
    ) {
        val current = _pointerState.value
        _pointerState.value = current.copy(
            xPercent = xPercent ?: current.xPercent,
            yPercent = yPercent ?: current.yPercent,
            actionText = actionText ?: current.actionText,
            isClicking = isClicking,
            actionType = actionType ?: current.actionType
        )
    }

    fun setPointerVisible(visible: Boolean) {
        _pointerState.value = _pointerState.value.copy(isVisible = visible)
    }

    fun addActivityLog(message: String, type: ActivityType = ActivityType.INFO, detail: String? = null) {
        val item = AgentActivityItem(message = message, type = type, detail = detail)
        _activityLogs.value = _activityLogs.value + item
    }

    fun startNewTask(userGoal: String, conversationId: String? = null) {
        stopCurrentTask(pauseOnly = false)
        _activityLogs.value = emptyList()

        executionJob = scope.launch(Dispatchers.IO) {
            val convId = conversationId ?: agentRepository.createOrGetActiveConversation().id
            val taskId = UUID.randomUUID().toString()

            val task = AgentTaskEntity(
                id = taskId,
                conversationId = convId,
                goal = userGoal,
                status = "RUNNING",
                progress = "Starting autonomous task...",
                currentStepIndex = 0,
                totalEstimatedSteps = 10,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )

            agentRepository.taskDao.insertTask(task)
            _currentTask.value = task
            _agentStatus.value = AgentStatus.RUNNING

            // Record user message
            agentRepository.messageDao.insertMessage(
                AgentMessageEntity(
                    conversationId = convId,
                    role = "user",
                    content = userGoal
                )
            )

            runAutonomousLoop(task, isResume = false)
        }
    }

    fun resumeTask(taskId: String? = null) {
        executionJob?.cancel()
        executionJob = scope.launch(Dispatchers.IO) {
            val task = if (taskId != null) {
                agentRepository.taskDao.getTaskById(taskId)
            } else {
                _currentTask.value ?: agentRepository.taskDao.getLatestTask()
            }

            if (task == null) {
                addActivityLog("No saved task found to resume.", ActivityType.ERROR)
                return@launch
            }

            addActivityLog("Resuming task: '${task.goal}' from last checkpoint...", ActivityType.INFO)
            _currentTask.value = task.copy(status = "RUNNING", updatedAt = System.currentTimeMillis())
            agentRepository.taskDao.updateTask(_currentTask.value!!)
            _agentStatus.value = AgentStatus.RUNNING

            runAutonomousLoop(_currentTask.value!!, isResume = true)
        }
    }

    fun pauseTask() {
        isInterrupted = true
        _agentStatus.value = AgentStatus.PAUSED
        _currentTask.value?.let { task ->
            scope.launch(Dispatchers.IO) {
                val updated = task.copy(status = "PAUSED", updatedAt = System.currentTimeMillis())
                agentRepository.taskDao.updateTask(updated)
                _currentTask.value = updated
            }
        }
        addActivityLog("Task paused by user. You can resume anytime.", ActivityType.INFO)
        executionJob?.cancel()
    }

    fun stopCurrentTask(pauseOnly: Boolean = false) {
        isInterrupted = true
        executionJob?.cancel()
        executionJob = null
        if (!pauseOnly) {
            _agentStatus.value = AgentStatus.IDLE
            _currentTask.value?.let { task ->
                scope.launch(Dispatchers.IO) {
                    agentRepository.taskDao.updateTask(task.copy(status = if (task.status == "COMPLETED") "COMPLETED" else "PAUSED"))
                }
            }
        }
    }

    fun approveSensitiveAction() {
        val req = _pendingConfirmation.value ?: return
        _pendingConfirmation.value = null
        addActivityLog("User confirmed sensitive action: ${req.actionName}", ActivityType.ACT)

        executionJob = scope.launch(Dispatchers.IO) {
            _agentStatus.value = AgentStatus.RUNNING
            val task = _currentTask.value ?: return@launch
            // Continue the loop with approval recorded
            runAutonomousLoop(task, isResume = true, confirmedActionPayload = req.pendingActionPayload)
        }
    }

    fun rejectSensitiveAction() {
        val req = _pendingConfirmation.value ?: return
        _pendingConfirmation.value = null
        addActivityLog("User rejected sensitive action: ${req.actionName}", ActivityType.ERROR)

        executionJob = scope.launch(Dispatchers.IO) {
            _agentStatus.value = AgentStatus.RUNNING
            val task = _currentTask.value ?: return@launch
            // Inject rejection feedback into conversation and continue
            val rejectionResult = JSONObject().apply {
                put("error", "Action was explicitly rejected by the user.")
            }
            continueLoopWithToolResult(task, "request_sensitive_confirmation", rejectionResult)
        }
    }

    private suspend fun runAutonomousLoop(
        initialTask: AgentTaskEntity,
        isResume: Boolean,
        confirmedActionPayload: JSONObject? = null
    ) {
        var currentTaskState = initialTask
        isInterrupted = false

        val config = agentRepository.getConfig()
        val memoryContext = agentRepository.getFormattedMemoryContext()

        // Build conversation contents
        val contentsArray = JSONArray()

        // Restore messages if resuming
        if (isResume && currentTaskState.lastCheckpointJson != null) {
            try {
                val checkpoint = JSONObject(currentTaskState.lastCheckpointJson!!)
                val history = checkpoint.optJSONArray("contents")
                if (history != null) {
                    for (i in 0 until history.length()) {
                        contentsArray.put(history.getJSONObject(i))
                    }
                }
            } catch (e: Exception) {
                Log.w("BrowserAgentController", "Failed to parse checkpoint JSON: ${e.message}")
            }
        }

        if (contentsArray.length() == 0) {
            // Initial prompt to Gemini
            val initialUserMessage = JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().apply {
                    put(JSONObject().apply {
                        put("text", "Goal: ${currentTaskState.goal}\n\nPlease autonomously plan, inspect the current browser state, perform necessary actions, and complete the goal step-by-step.")
                    })
                })
            }
            contentsArray.put(initialUserMessage)
        }

        if (confirmedActionPayload != null) {
            // Append confirmation tool response
            val responsePart = JSONObject().apply {
                put("functionResponse", JSONObject().apply {
                    put("name", "request_sensitive_confirmation")
                    put("response", JSONObject().apply {
                        put("status", "approved")
                        put("message", "User approved the sensitive action. You may proceed with execution.")
                    })
                })
            }
            val modelTurn = JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().apply { put(responsePart) })
            }
            contentsArray.put(modelTurn)
        }

        val systemInstruction = buildSystemInstruction(memoryContext, config.permissionMode)

        var stepCount = currentTaskState.currentStepIndex
        val maxSteps = config.maxStepsPerTask

        while (!isInterrupted && stepCount < maxSteps) {
            stepCount++
            addActivityLog("Agent thinking & observing current browser state (Step $stepCount)...", ActivityType.OBSERVE)

            // Update Task in DB
            currentTaskState = currentTaskState.copy(
                currentStepIndex = stepCount,
                progress = "Executing step $stepCount / $maxSteps",
                updatedAt = System.currentTimeMillis()
            )
            agentRepository.taskDao.updateTask(currentTaskState)
            _currentTask.value = currentTaskState

            // Call Gemini API with automatic key failover
            val response = apiClient.generateWithFailover(
                model = config.activeModel,
                contents = contentsArray,
                systemInstruction = systemInstruction,
                includeTools = true
            )

            if (isInterrupted) break

            when (response) {
                is GeminiResponse.Error -> {
                    addActivityLog("Gemini API Error: ${response.message}", ActivityType.ERROR)
                    _agentStatus.value = AgentStatus.ERROR
                    currentTaskState = currentTaskState.copy(status = "FAILED", progress = "Error: ${response.message}")
                    agentRepository.taskDao.updateTask(currentTaskState)
                    _currentTask.value = currentTaskState
                    return
                }
                is GeminiResponse.Success -> {
                    // Record model output turn
                    val modelTurnParts = JSONArray()
                    if (response.text != null) {
                        modelTurnParts.put(JSONObject().apply { put("text", response.text) })
                        addActivityLog(response.text, ActivityType.PLAN)
                    }

                    for (fc in response.functionCalls) {
                        modelTurnParts.put(JSONObject().apply {
                            put("functionCall", JSONObject().apply {
                                put("name", fc.name)
                                put("args", fc.args)
                            })
                        })
                    }

                    contentsArray.put(JSONObject().apply {
                        put("role", "model")
                        put("parts", modelTurnParts)
                    })

                    // If no function calls, agent might be finished or asking a question
                    if (response.functionCalls.isEmpty()) {
                        addActivityLog("Task concluded.", ActivityType.SUCCESS)
                        _agentStatus.value = AgentStatus.COMPLETED
                        currentTaskState = currentTaskState.copy(status = "COMPLETED", progress = "Completed successfully")
                        agentRepository.taskDao.updateTask(currentTaskState)
                        _currentTask.value = currentTaskState
                        return
                    }

                    // Execute function calls
                    val toolResponseParts = JSONArray()
                    var shouldPauseForConfirmation = false
                    var isTaskFinished = false

                    for (fc in response.functionCalls) {
                        if (isInterrupted) break

                        // Check sensitive confirmation
                        if (isSensitiveAction(fc.name, fc.args, config.permissionMode)) {
                            shouldPauseForConfirmation = true
                            val actionName = fc.args.optString("actionName", fc.name)
                            val actionDetails = fc.args.optString("actionDetails", fc.args.toString())
                            val warning = fc.args.optString("warningMessage", "⚠️ This action is sensitive or permanent. Explicit confirmation required.")

                            _pendingConfirmation.value = SensitiveConfirmationRequest(
                                taskId = currentTaskState.id,
                                actionName = actionName,
                                actionDetails = actionDetails,
                                warningMessage = warning,
                                pendingActionPayload = fc.args
                            )

                            addActivityLog("Waiting for user approval: $actionName", ActivityType.CONFIRMATION, warning)
                            _agentStatus.value = AgentStatus.WAITING_CONFIRMATION
                            currentTaskState = currentTaskState.copy(status = "WAITING_CONFIRMATION")
                            agentRepository.taskDao.updateTask(currentTaskState)
                            _currentTask.value = currentTaskState
                            break
                        }

                        // Execute Tool
                        val execResult = executeBrowserTool(fc.name, fc.args, currentTaskState)

                        // Save step in DB
                        agentRepository.taskStepDao.insertStep(
                            AgentTaskStepEntity(
                                taskId = currentTaskState.id,
                                stepNumber = stepCount,
                                actionType = fc.name,
                                actionDescription = execResult.summary,
                                actionPayloadJson = fc.args.toString(),
                                resultSummary = execResult.summary,
                                isSuccess = execResult.isSuccess
                            )
                        )

                        toolResponseParts.put(JSONObject().apply {
                            put("functionResponse", JSONObject().apply {
                                put("name", fc.name)
                                put("response", JSONObject().apply {
                                    put("result", execResult.summary)
                                    put("data", execResult.data)
                                    put("success", execResult.isSuccess)
                                })
                            })
                        })

                        if (fc.name == "finish_task") {
                            isTaskFinished = true
                        }

                        // Short pause between actions to simulate human cadence and allow DOM rendering
                        delay(600)
                    }

                    if (shouldPauseForConfirmation) {
                        // Save checkpoint
                        saveTaskCheckpoint(currentTaskState, contentsArray)
                        return
                    }

                    // Append tool responses
                    contentsArray.put(JSONObject().apply {
                        put("role", "user")
                        put("parts", toolResponseParts)
                    })

                    // Save periodic checkpoint
                    saveTaskCheckpoint(currentTaskState, contentsArray)

                    if (isTaskFinished) {
                        addActivityLog("Autonomous Agent task finished successfully!", ActivityType.SUCCESS)
                        _agentStatus.value = AgentStatus.COMPLETED
                        currentTaskState = currentTaskState.copy(status = "COMPLETED", progress = "100% completed")
                        agentRepository.taskDao.updateTask(currentTaskState)
                        _currentTask.value = currentTaskState
                        return
                    }
                }
            }
        }

        if (stepCount >= maxSteps) {
            addActivityLog("Reached maximum step limit ($maxSteps). Task paused.", ActivityType.INFO)
            _agentStatus.value = AgentStatus.PAUSED
            currentTaskState = currentTaskState.copy(status = "PAUSED")
            agentRepository.taskDao.updateTask(currentTaskState)
            _currentTask.value = currentTaskState
        }
    }

    private suspend fun continueLoopWithToolResult(
        task: AgentTaskEntity,
        toolName: String,
        resultData: JSONObject
    ) {
        val contentsArray = JSONArray()
        if (task.lastCheckpointJson != null) {
            try {
                val checkpoint = JSONObject(task.lastCheckpointJson)
                val history = checkpoint.optJSONArray("contents")
                if (history != null) {
                    for (i in 0 until history.length()) {
                        contentsArray.put(history.getJSONObject(i))
                    }
                }
            } catch (e: Exception) {
                Log.w("BrowserAgentController", "Failed to parse checkpoint: ${e.message}")
            }
        }

        val toolResponsePart = JSONObject().apply {
            put("functionResponse", JSONObject().apply {
                put("name", toolName)
                put("response", resultData)
            })
        }

        contentsArray.put(JSONObject().apply {
            put("role", "user")
            put("parts", JSONArray().apply { put(toolResponsePart) })
        })

        runAutonomousLoop(task, isResume = true)
    }

    private suspend fun saveTaskCheckpoint(task: AgentTaskEntity, contents: JSONArray) {
        val checkpoint = JSONObject().apply {
            put("contents", contents)
            put("updatedAt", System.currentTimeMillis())
        }
        val updated = task.copy(
            lastCheckpointJson = checkpoint.toString(),
            updatedAt = System.currentTimeMillis()
        )
        agentRepository.taskDao.updateTask(updated)
        _currentTask.value = updated
    }

    private suspend fun executeBrowserTool(
        name: String,
        args: JSONObject,
        task: AgentTaskEntity
    ): ActionExecutionResult = withContext(Dispatchers.Main) {
        when (name) {
            "open_url" -> {
                val url = args.getString("url")
                updatePointer(xPercent = 0.5f, yPercent = 0.08f, actionText = "Navigating: ${url.take(25)}...", isClicking = true)
                addActivityLog("Opening URL: $url", ActivityType.ACT)
                actionExecutor.openUrl(url)
            }
            "click_element" -> {
                val selector = args.optString("selector").takeIf { it.isNotBlank() }
                val textMatch = args.optString("textMatch").takeIf { it.isNotBlank() }
                val xpath = args.optString("xpath").takeIf { it.isNotBlank() }
                val targetDesc = selector ?: textMatch ?: xpath ?: "element"
                updatePointer(actionText = "Clicking: ${targetDesc.take(20)}...", isClicking = true)
                addActivityLog("Clicking element [$targetDesc]", ActivityType.ACT)
                val res = actionExecutor.clickElement(selector, textMatch, xpath)
                val xPct = res.data.optDouble("xPct", 0.5)
                val yPct = res.data.optDouble("yPct", 0.5)
                updatePointer(xPercent = xPct.toFloat(), yPercent = yPct.toFloat(), isClicking = false)
                res
            }
            "type_text" -> {
                val selector = args.getString("selector")
                val text = args.getString("text")
                val clear = args.optBoolean("clearFirst", true)
                val enter = args.optBoolean("pressEnter", false)
                updatePointer(actionText = "Typing: '${text.take(20)}...'", isClicking = true)
                addActivityLog("Typing text into '$selector'", ActivityType.ACT)
                val res = actionExecutor.typeText(selector, text, clear, enter)
                val xPct = res.data.optDouble("xPct", 0.5)
                val yPct = res.data.optDouble("yPct", 0.3)
                updatePointer(xPercent = xPct.toFloat(), yPercent = yPct.toFloat(), isClicking = false)
                res
            }
            "scroll_page" -> {
                val dir = args.getString("direction")
                val amount = args.optInt("amount", 500)
                val targetY = if (dir == "down") 0.75f else if (dir == "up") 0.25f else 0.5f
                updatePointer(xPercent = 0.5f, yPercent = targetY, actionText = "Scrolling $dir...", isClicking = false)
                addActivityLog("Scrolling page $dir ($amount px)", ActivityType.ACT)
                actionExecutor.scrollPage(dir, amount)
            }
            "extract_page_content" -> {
                val mode = args.optString("mode", "summary")
                updatePointer(actionText = "Inspecting page structure...", isClicking = false)
                addActivityLog("Reading and parsing webpage structure...", ActivityType.OBSERVE)
                actionExecutor.extractPageContent(mode)
            }
            "extract_ai_prompts" -> {
                val filter = args.optString("categoryFilter")
                val max = args.optInt("maxCount", 30)
                updatePointer(actionText = "Extracting AI prompts...", isClicking = false)
                addActivityLog("Extracting AI prompts from page...", ActivityType.OBSERVE)
                val res = actionExecutor.extractAiPrompts(filter, max)
                
                // Automatically save extracted prompts to Room DB
                val promptsArr = res.data.optJSONArray("prompts")
                var savedCount = 0
                if (promptsArr != null) {
                    for (i in 0 until promptsArr.length()) {
                        val p = promptsArr.getJSONObject(i)
                        val promptText = p.getString("prompt")
                        val cat = p.optString("category", "General")
                        val didSave = agentRepository.savePrompt(
                            promptText = promptText,
                            category = cat,
                            sourceWebsite = viewModel.uiState.value.let { s ->
                                val tab = s.normalTabs.find { it.id == s.activeNormalTabId }
                                tab?.title ?: "Webpage"
                            },
                            sourceUrl = res.data.optString("url", "")
                        )
                        if (didSave) savedCount++
                    }
                    addActivityLog("Saved $savedCount new prompts to local database (deduplicated).", ActivityType.SUCCESS)
                }
                res
            }
            "save_prompt" -> {
                val promptText = args.getString("prompt")
                val category = args.optString("category", "General")
                val tags = args.optString("tags", "")
                val notes = args.optString("notes", "")
                val saved = agentRepository.savePrompt(
                    promptText = promptText,
                    category = category,
                    tags = tags,
                    sourceWebsite = "Agent Extracted",
                    notes = notes
                )
                if (saved) {
                    addActivityLog("Saved prompt to database: '${promptText.take(40)}...'", ActivityType.SUCCESS)
                    ActionExecutionResult(isSuccess = true, summary = "Prompt successfully saved and indexed.")
                } else {
                    ActionExecutionResult(isSuccess = true, summary = "Prompt already exists in database (duplicate skipped).")
                }
            }
            "take_screenshot" -> {
                updatePointer(actionText = "Capturing visual screenshot...", isClicking = false)
                addActivityLog("Capturing visual screenshot for verification...", ActivityType.OBSERVE)
                val bitmap = actionExecutor.captureScreenshot()
                if (bitmap != null) {
                    val base64 = GeminiApiClient.bitmapToBase64(bitmap)
                    ActionExecutionResult(
                        isSuccess = true,
                        summary = "Screenshot captured successfully.",
                        screenshotBase64 = base64
                    )
                } else {
                    ActionExecutionResult(isSuccess = false, summary = "Could not capture screenshot.")
                }
            }
            "select_dropdown" -> {
                val selector = args.getString("selector")
                val value = args.getString("value")
                updatePointer(actionText = "Selecting dropdown: $value", isClicking = true)
                addActivityLog("Selecting '$value' in dropdown", ActivityType.ACT)
                actionExecutor.selectDropdown(selector, value)
            }
            "go_back" -> {
                updatePointer(xPercent = 0.1f, yPercent = 0.95f, actionText = "Browser: Back", isClicking = true)
                viewModel.goBack()
                ActionExecutionResult(isSuccess = true, summary = "Navigated Back")
            }
            "go_forward" -> {
                updatePointer(xPercent = 0.2f, yPercent = 0.95f, actionText = "Browser: Forward", isClicking = true)
                viewModel.goForward()
                ActionExecutionResult(isSuccess = true, summary = "Navigated Forward")
            }
            "reload_page" -> {
                updatePointer(xPercent = 0.8f, yPercent = 0.95f, actionText = "Browser: Reload", isClicking = true)
                viewModel.reload()
                ActionExecutionResult(isSuccess = true, summary = "Page Reloaded")
            }
            "go_home" -> {
                updatePointer(xPercent = 0.08f, yPercent = 0.08f, actionText = "Browser: Home", isClicking = true)
                viewModel.goHome()
                ActionExecutionResult(isSuccess = true, summary = "Navigated to Home page")
            }
            "open_new_tab" -> {
                updatePointer(xPercent = 0.5f, yPercent = 0.95f, actionText = "Browser: New Tab", isClicking = true)
                val url = args.optString("url")
                viewModel.openNewTab(url = url, isIncognito = viewModel.uiState.value.isIncognitoMode)
                ActionExecutionResult(isSuccess = true, summary = "Opened new tab")
            }
            "close_tab" -> {
                val tabId = args.optString("tabId").takeIf { it.isNotBlank() }
                    ?: viewModel.uiState.value.activeTabId
                viewModel.closeTab(tabId, isIncognito = viewModel.uiState.value.isIncognitoMode)
                ActionExecutionResult(isSuccess = true, summary = "Closed tab $tabId")
            }
            "switch_tab" -> {
                val tabId = args.getString("tabId")
                viewModel.switchTab(tabId, isIncognito = viewModel.uiState.value.isIncognitoMode)
                ActionExecutionResult(isSuccess = true, summary = "Switched to tab $tabId")
            }
            "toggle_bookmark" -> {
                updatePointer(xPercent = 0.67f, yPercent = 0.95f, actionText = "Browser: Bookmark", isClicking = true)
                viewModel.toggleBookmark(context)
                ActionExecutionResult(isSuccess = true, summary = "Toggled bookmark for current page")
            }
            "share_page" -> {
                updatePointer(xPercent = 0.92f, yPercent = 0.95f, actionText = "Browser: Share", isClicking = true)
                val currentTab = viewModel.uiState.value.currentTab
                val shareUrl = currentTab?.url ?: ""
                if (shareUrl.isNotBlank()) {
                    val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_SUBJECT, currentTab?.title ?: "")
                        putExtra(android.content.Intent.EXTRA_TEXT, shareUrl)
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(android.content.Intent.createChooser(shareIntent, "Share link"))
                }
                ActionExecutionResult(isSuccess = true, summary = "Triggered share sheet for $shareUrl")
            }
            "toggle_desktop_site" -> {
                viewModel.toggleDesktopMode()
                ActionExecutionResult(isSuccess = true, summary = "Toggled Desktop Site mode")
            }
            "find_in_page" -> {
                val query = args.getString("query")
                viewModel.updateFindQuery(query)
                ActionExecutionResult(isSuccess = true, summary = "Opened Find in Page for query '$query'")
            }
            "set_pointer_visibility" -> {
                val visible = args.getBoolean("visible")
                setPointerVisible(visible)
                ActionExecutionResult(isSuccess = true, summary = "Set pointer visibility to $visible")
            }
            "save_memory" -> {
                val key = args.getString("key")
                val value = args.getString("value")
                val cat = args.optString("category", "TASK_STATE")
                agentRepository.saveMemory(key, value, cat)
                addActivityLog("Saved to persistent memory: $key", ActivityType.INFO)
                ActionExecutionResult(isSuccess = true, summary = "Memory saved: $key = $value")
            }
            "extract_youtube_results" -> {
                updatePointer(actionText = "Extracting YouTube video metadata...", isClicking = false)
                addActivityLog("Scraping YouTube video cards & view statistics...", ActivityType.OBSERVE)
                val res = actionExecutor.extractYouTubeResults()
                
                val itemsArr = res.data.optJSONArray("items")
                if (itemsArr != null && itemsArr.length() > 0) {
                    val list = mutableListOf<YouTubeVideoItem>()
                    for (i in 0 until itemsArr.length()) {
                        val obj = itemsArr.getJSONObject(i)
                        list.add(
                            YouTubeVideoItem(
                                title = obj.optString("title"),
                                channel = obj.optString("channel"),
                                viewsRaw = obj.optString("viewsRaw"),
                                viewsNormalized = obj.optLong("viewsNormalized"),
                                viewsFormatted = obj.optString("viewsFormatted"),
                                uploadedRaw = obj.optString("uploadedRaw"),
                                recencyDays = obj.optDouble("recencyDays", 9999.0),
                                url = obj.optString("url"),
                                videoId = obj.optString("videoId"),
                                duration = obj.optString("duration"),
                                thumbnail = obj.optString("thumbnail")
                            )
                        )
                    }
                    _collectedVideoResults.value = list
                    addActivityLog("Collected ${list.size} structured YouTube videos in session memory.", ActivityType.SUCCESS)
                }
                res
            }
            "finish_task" -> {
                val summary = args.getString("summary")
                updatePointer(actionText = "Task Completed ✓", isClicking = false)
                addActivityLog("Task Summary: $summary", ActivityType.SUCCESS)
                ActionExecutionResult(isSuccess = true, summary = summary)
            }
            else -> {
                ActionExecutionResult(isSuccess = false, summary = "Unknown tool: $name")
            }
        }
    }

    private fun isSensitiveAction(toolName: String, args: JSONObject, permissionMode: String): Boolean {
        if (toolName == "request_sensitive_confirmation") return true
        if (permissionMode != "FULL_ACCESS") {
            // Strict mode requires confirmation for all mutative tools
            if (toolName == "click_element" || toolName == "type_text" || toolName == "select_dropdown") {
                return true
            }
        }

        // Even in FULL_ACCESS, destructive or irreversible actions require explicit user confirmation
        val textArgs = args.toString().lowercase()
        val isDestructive = textArgs.contains("delete") ||
                textArgs.contains("permanent") ||
                textArgs.contains("publish") ||
                textArgs.contains("post") ||
                textArgs.contains("checkout") ||
                textArgs.contains("transfer") ||
                textArgs.contains("remove video") ||
                textArgs.contains("discard")

        return isDestructive
    }

    private fun buildSystemInstruction(memoryContext: String, permissionMode: String): String {
        return """
            You are an autonomous, deeply intelligent AI Browser Agent operating an Android Web Browser like a real human user.
            You possess advanced web navigation reasoning, semantic element understanding, tree-traversal memory mapping, and direct control over both browser UI controls and webpage elements.

            ACTION LOOP (MANDATORY):
            OBSERVE ➔ UNDERSTAND ➔ ACT ➔ VERIFY ➔ NEXT ACTION
            - NEVER guess blindly.
            - After every important action, inspect the current page again to verify whether the expected result occurred.

            BROWSER UI vs WEBPAGE CONTROLS:
            - BROWSER CONTROLS: Address bar, Back ('go_back'), Forward ('go_forward'), Reload ('reload_page'), Home ('go_home'), New Tab ('open_new_tab'), Close Tab ('close_tab'), Tab switching ('switch_tab'), Bookmark ('toggle_bookmark'), Share ('share_page'), Find in Page ('find_in_page'), Desktop site ('toggle_desktop_site').
            - WEBPAGE CONTROLS: Search inputs, form fields, buttons, links, dropdowns, checkboxes, videos, menus, etc. Use the correct tool for each layer.

            HUMAN-LIKE SEARCH PROTOCOL:
            Do NOT depend on manually guessing search URLs. Prefer interacting through the page:
            1. Inspect current webpage with 'extract_page_content'.
            2. Find the real search input element.
            3. Focus/click the search input.
            4. Type the query using 'type_text' ('clearFirst = true', 'pressEnter = true').
            5. Submit using Enter or the actual search button/icon.
            6. Wait for page update and inspect search results.
            7. Fallback to direct 'open_url' navigation ONLY if no search box exists or on home tab.

            YOUTUBE AUTOMATION & RESULT SELECTION PROTOCOL:
            When tasked to find, play, or list YouTube content (e.g., "Arijit Singh er latest song play koro"):
            1. Open YouTube (`https://www.youtube.com` or `https://m.youtube.com`).
            2. Find the YouTube search box, focus it, type query, press enter.
            3. Call 'extract_youtube_results' to extract structured metadata (Title, Channel, Normalized Views, Upload Date, Link, Thumbnail).
            4. If more items are required or target is not visible, call 'scroll_page("down", 600)', then 'extract_youtube_results' again.
            5. ANALYZE & COMPARE METADATA:
               - **LATEST** = Smallest recency value (newest upload date e.g. "2 days ago" vs "2 years ago").
               - **POPULAR / MOST VIEWS** = Highest normalized view count (e.g. 2,500,000 views > 50,000 views).
               - NEVER confuse LATEST with POPULAR!
            6. Select the exact best match based on user request.
            7. Open/play the video (`open_url` or `click_element`).
            8. Conclude with 'finish_task' providing Title, Channel, Views, Upload date, and direct Link.

            RESULT MEMORY & FOLLOW-UP QUESTIONS:
            - Keep track of collected items in session memory.
            - Answer follow-up queries (e.g., "ওই 10টার মধ্যে latest কোনটা?", "সবচেয়ে popular কোনটা?") directly from memory without repeating searches unnecessarily.
            - NEVER hallucinate unobserved view counts, dates, titles, or URLs.

            CLICKING & ELEMENT SELECTION:
            Before clicking, identify the element's visible text, aria-label, title, role, href, position, and surrounding context.
            Choose elements based strictly on relevance to the goal.

            ACTIVE SCROLLING:
            Use 'scroll_page' ('down', 'up', 'top', 'bottom') to reveal hidden elements or load lazy search results. Inspect after scrolling.

            FAILURE RECOVERY PROTOCOL:
            If an action fails: inspect page again -> try alternative selector -> scroll -> backtrack ('go_back') if stuck.

            SENSITIVE ACTION PROTECTION:
            Playing or opening public web content does NOT require confirmation.
            Require confirmation ('request_sensitive_confirmation') only before sensitive or permanent actions: Upload, Delete, Publish, Send, Purchase, Account changes.

            $memoryContext
        """.trimIndent()
    }
}
