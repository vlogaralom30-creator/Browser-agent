import re

with open('app/src/main/java/com/example/ui/components/CrawlBotSheet.kt', 'r') as f:
    content = f.read()

# Update onStartVideoBot parameter signature
content = content.replace(
    'onStartVideoBot: (query: String, criteria: String, like: Boolean, comment: Boolean, commentText: String) -> Unit = { _, _, _, _, _ -> },',
    'onStartVideoBot: (query: String, criteria: String, like: Boolean, comment: Boolean, copyLink: Boolean, commentText: String) -> Unit = { _, _, _, _, _, _ -> },\n    onConfirmComment: () -> Unit = {},\n    onDenyComment: () -> Unit = {},'
)

# Initialize `performCopyLink` state
if 'var performCopyLink by' not in content:
    content = content.replace('var performComment by', 'var performCopyLink by remember(botState.videoPerformCopyLink) { mutableStateOf(botState.videoPerformCopyLink) }\n    var performComment by')

# Add Switch for Copy Link
copy_link_ui = """
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Auto-Copy Video Link", fontSize = 12.sp)
                                }
                                Switch(
                                    checked = performCopyLink,
                                    onCheckedChange = { performCopyLink = it },
                                    colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.error, checkedTrackColor = MaterialTheme.colorScheme.errorContainer)
                                )
                            }
"""
if 'Auto-Copy Video Link' not in content:
    content = content.replace('Auto-Comment on Video', copy_link_ui.strip() + '\n\n                            Row(\n                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),\n                                horizontalArrangement = Arrangement.SpaceBetween,\n                                verticalAlignment = Alignment.CenterVertically\n                            ) {\n                                Row(verticalAlignment = Alignment.CenterVertically) {\n                                    Icon(Icons.Default.Comment, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)\n                                    Spacer(modifier = Modifier.width(8.dp))\n                                    Text("Auto-Comment on Video"')

# Update button onClick parameters
content = content.replace(
    'performLike,\n                                            performComment,\n                                            commentTextInput.trim()',
    'performLike,\n                                            performComment,\n                                            performCopyLink,\n                                            commentTextInput.trim()'
)

# Add Confirmation UI
confirm_ui = """
                            // Comment Confirmation UI
                            if (botState.status == CrawlBotStatus.WAITING_CONFIRMATION) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text("Action Confirmation Required", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("Bot is ready to post your comment: \\\"${botState.videoCommentText}\\\"", fontSize = 13.sp)
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            OutlinedButton(onClick = onDenyComment, modifier = Modifier.weight(1f)) { Text("Skip") }
                                            Button(onClick = onConfirmComment, modifier = Modifier.weight(1f)) { Text("Post Comment") }
                                        }
                                    }
                                }
                            }
"""
if 'WAITING_CONFIRMATION' not in content:
    content = content.replace('// Stop Button', confirm_ui.strip() + '\n\n                            // Stop Button')

with open('app/src/main/java/com/example/ui/components/CrawlBotSheet.kt', 'w') as f:
    f.write(content)
