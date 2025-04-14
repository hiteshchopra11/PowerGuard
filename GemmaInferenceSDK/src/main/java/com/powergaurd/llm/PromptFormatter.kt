package com.powergaurd.llm

/**
 * Utility class for formatting data into prompts for LLM inference.
 * Provides methods to transform various data structures into text suitable for the model.
 */
class PromptFormatter {
    
    /**
     * Creates a system prompt for the LLM to provide context for generation.
     * 
     * @return The system prompt text
     */
    fun createSystemPrompt(): String {
        return """
            You are an AI assistant specialized in Android device optimization.
            Analyze the provided device data and suggest actionable optimizations.
            Focus on battery usage, network data consumption, and performance.
            Provide specific recommendations based on the data patterns you observe.
        """.trimIndent()
    }
    
    /**
     * Formats a generic map of data into a human-readable prompt.
     * 
     * @param data Key-value pairs representing the data to format
     * @param userGoal Optional user goal or request to include in the prompt
     * @return Formatted prompt text
     */
    fun formatMapData(data: Map<String, Any>, userGoal: String? = null): String {
        val prompt = StringBuilder()
        
        // Add system prompt as context
        prompt.append(createSystemPrompt())
        prompt.append("\n\n")
        
        // Add device data section
        prompt.append("Device Data:\n")
        data.forEach { (key, value) ->
            when (value) {
                is Map<*, *> -> {
                    prompt.append("$key:\n")
                    @Suppress("UNCHECKED_CAST")
                    (value as Map<String, Any>).forEach { (subKey, subValue) ->
                        prompt.append("  $subKey: $subValue\n")
                    }
                }
                is List<*> -> {
                    prompt.append("$key: [")
                    prompt.append(value.joinToString(", "))
                    prompt.append("]\n")
                }
                else -> {
                    prompt.append("$key: $value\n")
                }
            }
        }
        
        // Add user goal if specified
        if (!userGoal.isNullOrBlank()) {
            prompt.append("\nUser Goal: \"$userGoal\"\n")
        }
        
        // Add output format instructions
        prompt.append("""
            
            Based on this data, generate a comprehensive analysis with:
            
            1. actionable: A list of specific actions to take, each with:
               - id: unique identifier for the action
               - type: the type of action (one of: SET_STANDBY_BUCKET, RESTRICT_BACKGROUND_DATA, KILL_APP, MANAGE_WAKE_LOCKS, THROTTLE_CPU_USAGE)
               - packageName: affected app's package name
               - description: what the action will do
               - reason: why this action is recommended
               - newMode: target state
               - parameters: additional context as key-value pairs
            
            2. insights: List of insights about the device's behavior, each with:
               - type: insight category
               - title: summary title
               - description: detailed explanation
               - severity: low, medium, or high
            
            3. scores and estimates:
               - batteryScore: from 0-100 evaluating battery health
               - dataScore: from 0-100 evaluating data usage efficiency
               - performanceScore: from 0-100 evaluating overall performance
               - estimatedSavings: with batteryMinutes and dataMB values
            
            Return your analysis as valid JSON.
        """.trimIndent())
        
        return prompt.toString()
    }
    
    /**
     * Create a targeted prompt focused on a specific optimization area.
     *
     * @param area The optimization area ("battery", "data", or "performance")
     * @param data Key-value pairs representing the data to format
     * @return Formatted prompt text
     */
    fun createTargetedPrompt(area: String, data: Map<String, Any>): String {
        val prompt = StringBuilder()
        
        // Add system prompt
        prompt.append(createSystemPrompt())
        prompt.append("\n\n")
        
        // Add targeted instructions based on area
        when (area.lowercase()) {
            "battery" -> {
                prompt.append("OPTIMIZATION GOAL: Maximize battery life\n\n")
                prompt.append("Focus specifically on identifying battery-draining apps and processes.\n")
                prompt.append("Prioritize actions that will extend battery life significantly.\n\n")
            }
            "data" -> {
                prompt.append("OPTIMIZATION GOAL: Minimize data usage\n\n")
                prompt.append("Focus specifically on identifying data-consuming apps and processes.\n")
                prompt.append("Prioritize actions that will reduce mobile data consumption.\n\n")
            }
            "performance" -> {
                prompt.append("OPTIMIZATION GOAL: Improve system performance\n\n")
                prompt.append("Focus specifically on identifying performance bottlenecks.\n")
                prompt.append("Prioritize actions that will make the device run more smoothly.\n\n")
            }
            else -> {
                prompt.append("OPTIMIZATION GOAL: General device optimization\n\n")
                prompt.append("Focus on balanced optimization across battery, data, and performance.\n\n")
            }
        }
        
        // Add relevant data
        prompt.append("Device Data:\n")
        data.forEach { (key, value) ->
            prompt.append("$key: $value\n")
        }
        
        // Add output format instructions (shortened for targeted analysis)
        prompt.append("""
            
            Based on this data, generate a targeted analysis focusing on ${area.uppercase()} optimization.
            Include specific actions, insights, and improvement estimates in JSON format.
        """.trimIndent())
        
        return prompt.toString()
    }
} 