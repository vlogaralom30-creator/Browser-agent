import re

with open('app/src/main/java/com/example/ui/screens/BrowserMainScreen.kt', 'r') as f:
    content = f.read()

content = content.replace(
    'onStartVideoBot = { query, criteria, like, comment, text ->\n                viewModel.startVideoBot(query, criteria, like, comment, text)\n            },',
    'onStartVideoBot = { query, criteria, like, comment, copyLink, text ->\n                viewModel.startVideoBot(query, criteria, like, comment, copyLink, text)\n            },\n            onConfirmComment = { viewModel.confirmVideoComment() },\n            onDenyComment = { viewModel.denyVideoComment() },'
)

with open('app/src/main/java/com/example/ui/screens/BrowserMainScreen.kt', 'w') as f:
    f.write(content)
