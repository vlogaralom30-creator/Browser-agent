package com.example.ai

import org.json.JSONArray
import org.json.JSONObject

object BrowserTools {

    fun getGeminiToolsDeclaration(): JSONArray {
        val functionDeclarations = JSONArray()

        // 1. open_url
        functionDeclarations.put(JSONObject().apply {
            put("name", "open_url")
            put("description", "Navigates the browser to the specified URL or search term.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("url", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "The full URL (e.g., https://example.com) or search query to open in the active browser tab.")
                    })
                })
                put("required", JSONArray().apply { put("url") })
            })
        })

        // 2. click_element
        functionDeclarations.put(JSONObject().apply {
            put("name", "click_element")
            put("description", "Clicks an interactive element on the page using a CSS selector, element text, or XPath.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("selector", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "CSS selector for the element to click (e.g. 'button.submit', '#play-btn', 'a[href*=\"studio\"]').")
                    })
                    put("textMatch", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Optional text content of the button/link to click (e.g. 'Sign In', 'Edit', 'Copy Prompt').")
                    })
                    put("xpath", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Optional XPath expression if CSS selector is not specific enough.")
                    })
                })
            })
        })

        // 3. type_text
        functionDeclarations.put(JSONObject().apply {
            put("name", "type_text")
            put("description", "Types text into an input field, search box, textarea, or editable content element on the current webpage.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("selector", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "CSS selector for the input element (e.g. 'input[name=\"search\"]', '#title-input', 'textarea').")
                    })
                    put("text", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "The exact text string to type into the field.")
                    })
                    put("clearFirst", JSONObject().apply {
                        put("type", "BOOLEAN")
                        put("description", "Whether to clear existing text in the input field before typing (default: true).")
                    })
                    put("pressEnter", JSONObject().apply {
                        put("type", "BOOLEAN")
                        put("description", "Whether to simulate pressing the Enter key after typing (default: false).")
                    })
                })
                put("required", JSONArray().apply { put("selector"); put("text") })
            })
        })

        // 4. scroll_page
        functionDeclarations.put(JSONObject().apply {
            put("name", "scroll_page")
            put("description", "Scrolls the current webpage up or down to reveal more content or reach specific elements.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("direction", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Scroll direction: 'down', 'up', 'top', or 'bottom'.")
                    })
                    put("amount", JSONObject().apply {
                        put("type", "INTEGER")
                        put("description", "Amount in pixels to scroll (e.g. 500 for normal scroll, 1200 for full page).")
                    })
                })
                put("required", JSONArray().apply { put("direction") })
            })
        })

        // 5. extract_page_content
        functionDeclarations.put(JSONObject().apply {
            put("name", "extract_page_content")
            put("description", "Inspects and reads the current page structure, visible text, interactive buttons, forms, links, and headings.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("mode", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "'summary' for concise page outline, 'interactive' for list of buttons/inputs/links, or 'full' for complete text.")
                    })
                })
            })
        })

        // 6. extract_ai_prompts
        functionDeclarations.put(JSONObject().apply {
            put("name", "extract_ai_prompts")
            put("description", "Scrapes and parses image generation prompts (Midjourney, Stable Diffusion, DALL-E, cinematic prompts, etc.) visible on the current webpage.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("categoryFilter", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Optional filter: 'all', 'cinematic', 'photorealistic', 'anime', '3d', 'character'.")
                    })
                    put("maxCount", JSONObject().apply {
                        put("type", "INTEGER")
                        put("description", "Maximum number of prompts to extract from the visible page.")
                    })
                })
            })
        })

        // 7. save_prompt
        functionDeclarations.put(JSONObject().apply {
            put("name", "save_prompt")
            put("description", "Saves an extracted prompt into the local Android database with category, tags, and source URL.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("prompt", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "The prompt text.")
                    })
                    put("category", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Category: 'Cinematic', 'Photorealistic', 'Digital Art', 'Anime', 'Portrait', 'Landscape', etc.")
                    })
                    put("tags", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Comma-separated tags (e.g. '8k, unreal engine 5, lighting, cyberpunk').")
                    })
                    put("notes", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Optional notes or model parameters (e.g. '--ar 16:9 --v 6.0').")
                    })
                })
                put("required", JSONArray().apply { put("prompt") })
            })
        })

        // 8. take_screenshot
        functionDeclarations.put(JSONObject().apply {
            put("name", "take_screenshot")
            put("description", "Captures a visual screenshot of the current webpage for visual inspection and multimodal understanding.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("reason", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Why visual inspection is needed (e.g. 'verify video upload state', 'read visual canvas').")
                    })
                })
            })
        })

        // 9. select_dropdown
        functionDeclarations.put(JSONObject().apply {
            put("name", "select_dropdown")
            put("description", "Selects an option inside a dropdown (<select>) element.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("selector", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "CSS selector for the <select> tag.")
                    })
                    put("value", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Option value or visible text to select.")
                    })
                })
                put("required", JSONArray().apply { put("selector"); put("value") })
            })
        })

        // 10. go_back
        functionDeclarations.put(JSONObject().apply {
            put("name", "go_back")
            put("description", "Navigates back to the previous page in history.")
            put("parameters", JSONObject().apply { put("type", "OBJECT") })
        })

        // 11. go_forward
        functionDeclarations.put(JSONObject().apply {
            put("name", "go_forward")
            put("description", "Navigates forward in history.")
            put("parameters", JSONObject().apply { put("type", "OBJECT") })
        })

        // 12. reload_page
        functionDeclarations.put(JSONObject().apply {
            put("name", "reload_page")
            put("description", "Reloads the current page.")
            put("parameters", JSONObject().apply { put("type", "OBJECT") })
        })

        // 13. save_memory
        functionDeclarations.put(JSONObject().apply {
            put("name", "save_memory")
            put("description", "Saves a persistent fact, user preference, or task checkpoint into Agent Memory to remember across sessions.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("key", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Identifier key (e.g. 'last_prompt_page_index', 'youtube_channel_name').")
                    })
                    put("value", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Value to remember.")
                    })
                    put("category", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Category: 'TASK_STATE', 'PREFERENCE', 'WEBSITE_STATE', 'LEARNED_FACT'.")
                    })
                })
                put("required", JSONArray().apply { put("key"); put("value") })
            })
        })

        // 14. request_sensitive_confirmation
        functionDeclarations.put(JSONObject().apply {
            put("name", "request_sensitive_confirmation")
            put("description", "Pauses execution and prompts the user for explicit confirmation before executing an irreversible or sensitive action (e.g. Delete, Publish, Post, Send, Payment).")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("actionName", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Short action title (e.g. 'Delete Selected Video', 'Publish Video to Public').")
                    })
                    put("actionDetails", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Full details of what will be executed upon user approval.")
                    })
                    put("warningMessage", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Explanation of why this action requires confirmation and any permanent effects.")
                    })
                })
                put("required", JSONArray().apply { put("actionName"); put("actionDetails") })
            })
        })

        // 15. finish_task
        functionDeclarations.put(JSONObject().apply {
            put("name", "finish_task")
            put("description", "Concludes the current autonomous task and presents the final summary to the user.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("summary", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Detailed, user-facing summary of the actions executed and results achieved.")
                    })
                    put("itemsCount", JSONObject().apply {
                        put("type", "INTEGER")
                        put("description", "Optional number of items processed (e.g. prompts collected, videos updated).")
                    })
                })
                put("required", JSONArray().apply { put("summary") })
            })
        })

        val toolsArray = JSONArray()
        toolsArray.put(JSONObject().apply {
            put("functionDeclarations", functionDeclarations)
        })
        return toolsArray
    }

    fun getOpenAiToolsDeclaration(): JSONArray {
        val geminiTools = getGeminiToolsDeclaration()
        if (geminiTools.length() == 0) return JSONArray()

        val firstObj = geminiTools.getJSONObject(0)
        val funcDecls = firstObj.optJSONArray("functionDeclarations") ?: return JSONArray()

        val openAiTools = JSONArray()
        for (i in 0 until funcDecls.length()) {
            val decl = funcDecls.getJSONObject(i)
            val name = decl.getString("name")
            val desc = decl.optString("description", "")
            val params = decl.optJSONObject("parameters") ?: JSONObject().apply { put("type", "OBJECT") }

            // Convert Gemini type names (OBJECT -> object, STRING -> string, etc)
            val convertedParams = convertSchemaTypesToOpenAi(params)

            openAiTools.put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", name)
                    put("description", desc)
                    put("parameters", convertedParams)
                })
            })
        }
        return openAiTools
    }

    private fun convertSchemaTypesToOpenAi(json: JSONObject): JSONObject {
        val copy = JSONObject(json.toString())
        if (copy.has("type")) {
            val typeStr = copy.getString("type").lowercase()
            copy.put("type", typeStr)
        }
        if (copy.has("properties")) {
            val props = copy.getJSONObject("properties")
            val keys = props.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                props.put(k, convertSchemaTypesToOpenAi(props.getJSONObject(k)))
            }
        }
        return copy
    }
}
