import re

with open('app/src/main/java/com/example/ui/BrowserViewModel.kt', 'r') as f:
    content = f.read()

content = content.replace(
    'fun startYoutubeBot(\n        query: String,\n        criteria: String,\n        like: Boolean,\n        comment: Boolean,\n        commentText: String\n    ) {',
    'fun startVideoBot(query: String, criteria: String, like: Boolean, comment: Boolean, copyLink: Boolean, commentText: String) {'
)

content = content.replace('siteCrawlerEngine.startYtBot(', 'siteCrawlerEngine.startVideoBot(')
content = content.replace('commentText = commentText,', 'commentText = commentText,\n            copyLink = copyLink,')

content = content.replace('fun clearYoutubeHistory(context: android.content.Context) {', 'fun clearVideoHistory() {')
content = content.replace('siteCrawlerEngine.clearYtHistory(context)', 'siteCrawlerEngine.clearVideoHistory(getApplication())')

new_methods = """
    fun confirmVideoComment() {
        siteCrawlerEngine.confirmCommentAction()
    }

    fun denyVideoComment() {
        siteCrawlerEngine.denyCommentAction()
    }
"""

if 'confirmVideoComment' not in content:
    content = content.replace('fun clearVideoHistory() {', new_methods.strip() + '\n\n    fun clearVideoHistory() {')

with open('app/src/main/java/com/example/ui/BrowserViewModel.kt', 'w') as f:
    f.write(content)
