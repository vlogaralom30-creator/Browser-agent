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

    private var executionJob: Job? = null
    private var isInterrupted = false

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
                addActivityLog("Opening URL: $url", ActivityType.ACT)
                actionExecutor.openUrl(url)
            }
            "click_element" -> {
                val selector = args.optString("selector").takeIf { it.isNotBlank() }
                val textMatch = args.optString("textMatch").takeIf { it.isNotBlank() }
                val xpath = args.optString("xpath").takeIf { it.isNotBlank() }
                addActivityLog("Clicking element [${selector ?: textMatch ?: xpath}]", ActivityType.ACT)
                actionExecutor.clickElement(selector, textMatch, xpath)
            }
            "type_text" -> {
                val selector = args.getString("selector")
                val text = args.getString("text")
                val clear = args.optBoolean("clearFirst", true)
                val enter = args.optBoolean("pressEnter", false)
                addActivityLog("Typing text into '$selector'", ActivityType.ACT)
                actionExecutor.typeText(selector, text, clear, enter)
            }
            "scroll_page" -> {
                val dir = args.getString("direction")
                val amount = args.optInt("amount", 500)
                addActivityLog("Scrolling page $dir ($amount px)", ActivityType.ACT)
                actionExecutor.scrollPage(dir, amount)
            }
            "extract_page_content" -> {
                val mode = args.optString("mode", "summary")
                addActivityLog("Reading and parsing webpage structure...", ActivityType.OBSERVE)
                actionExecutor.extractPageContent(mode)
            }
            "extract_ai_prompts" -> {
                val filter = args.optString("categoryFilter")
                val max = args.optInt("maxCount", 30)
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
                addActivityLog("Selecting '$value' in dropdown", ActivityType.ACT)
                actionExecutor.selectDropdown(selector, value)
            }
            "go_back" -> {
                viewModel.goBack()
                ActionExecutionResult(isSuccess = true, summary = "Navigated Back")
            }
            "go_forward" -> {
                viewModel.goForward()
                ActionExecutionResult(isSuccess = true, summary = "Navigated Forward")
            }
            "reload_page" -> {
                viewModel.reload()
                ActionExecutionResult(isSuccess = true, summary = "Page Reloaded")
            }
            "save_memory" -> {
                val key = args.getString("key")
                val value = args.getString("value")
                val cat = args.optString("category", "TASK_STATE")
                agentRepository.saveMemory(key, value, cat)
                addActivityLog("Saved to persistent memory: $key", ActivityType.INFO)
                ActionExecutionResult(isSuccess = true, summary = "Memory saved: $key = $value")
            }
            "finish_task" -> {
                val summary = args.getString("summary")
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
            You are an autonomous AI Browser Agent operating inside an Android Web Browser.
            You have direct browser-control capabilities via tools to navigate, read DOM structure, click buttons/links, type text into inputs, scroll pages, extract AI image prompts, save prompts to local database, and capture screenshots.

            AUTONOMOUS AGENT LOOP:
            OBSERVE -> UNDERSTAND -> PLAN -> ACT -> VERIFY -> CONTINUE

            RULES & CAPABILITIES:
            1. First inspect the page structure using 'extract_page_content' to find selectors, headings, and interactive elements.
            2. To click an element, prefer CSS selector or exact text match.
            3. To enter text, use 'type_text' with the CSS selector of the input/textarea.
            4. If a page loads dynamic content or you scroll down, use 'scroll_page' and re-inspect.
            5. If extracting prompts, use 'extract_ai_prompts' or 'save_prompt'.
            6. If you encounter an error (e.g. element not found), observe the page again and try an alternative selector or text match.
            7. For irreversible, destructive, delete, or publish actions, call 'request_sensitive_confirmation' so the user is prompted with an explicit Allow/Cancel dialog.
            8. Once you have accomplished the user's objective, call 'finish_task' with a clear summary.

            $memoryContext
        """.trimIndent()
    }
}
