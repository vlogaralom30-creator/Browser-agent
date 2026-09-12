import re

with open('app/src/main/java/com/example/ui/BrowserViewModel.kt', 'r') as f:
    content = f.read()

content = content.replace(
    'fun startVideoBot(query: String, criteria: String, like: Boolean, comment: Boolean, commentText: String) {',
    'fun startVideoBot(query: String, criteria: String, like: Boolean, comment: Boolean, copyLink: Boolean, commentText: String) {'
)

content = content.replace(
    'crawlerEngine.startVideoBot(query, criteria, like, comment, commentText, viewModelScope, { url ->',
    'crawlerEngine.startVideoBot(query, criteria, like, comment, copyLink, commentText, viewModelScope, { url ->'
)

new_methods = """
    fun confirmVideoComment() {
        crawlerEngine.confirmCommentAction()
    }

    fun denyVideoComment() {
        crawlerEngine.denyCommentAction()
    }
"""

if 'confirmVideoComment' not in content:
    content = content.replace('fun clearVideoHistory() {', new_methods.strip() + '\n\n    fun clearVideoHistory() {')

with open('app/src/main/java/com/example/ui/BrowserViewModel.kt', 'w') as f:
    f.write(content)
