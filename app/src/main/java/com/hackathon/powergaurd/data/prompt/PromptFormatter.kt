/**
 * Creates a system prompt for device optimization
 */
fun createSystemPrompt(): String {
    return """
        Android device optimizer. Analyze device data and provide:
        
        1. Scores (1-10): Battery, Data, Performance
        2. Concise optimization tips
        3. One brief insight per category
        
        Keep under 50 words. Be specific.
    """.trimIndent()
} 