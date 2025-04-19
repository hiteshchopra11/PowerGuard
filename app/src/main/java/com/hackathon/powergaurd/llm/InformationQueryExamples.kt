package com.hackathon.powergaurd.llm

/**
 * Example responses for information queries.
 * These examples show the expected format for different types of information queries.
 * They can be used for testing or documentation purposes.
 */
object InformationQueryExamples {

    /**
     * Example response for "Top 5 data-consuming apps today"
     */
    val TOP_N_DATA_APPS_EXAMPLE = """
    {
      "insights": [
        {
          "type": "Information",
          "title": "Top 5 Data-Consuming Apps Today",
          "description": "1. YouTube (450 MB)\n2. Chrome (120 MB)\n3. Instagram (87 MB)\n4. Spotify (45 MB)\n5. Gmail (23 MB)",
          "severity": "info"
        }
      ]
    }
    """.trimIndent()

    /**
     * Example response for "Which apps are draining my battery the most?"
     */
    val BATTERY_DRAINING_APPS_EXAMPLE = """
    {
      "insights": [
        {
          "type": "Information",
          "title": "Top Battery-Consuming Apps",
          "description": "1. TikTok (26%)\n2. Google Maps (18%)\n3. WhatsApp (12%)",
          "severity": "info"
        }
      ]
    }
    """.trimIndent()

    /**
     * Example response for "How much data has YouTube used this week?"
     */
    val SPECIFIC_APP_DATA_USAGE_EXAMPLE = """
    {
      "insights": [
        {
          "type": "Information",
          "title": "YouTube Data Usage This Week",
          "description": "YouTube has used 1.2 GB of data in the past week.",
          "severity": "info"
        }
      ]
    }
    """.trimIndent()

    /**
     * Example response for "How much data has Snapchat used this week?" when no data is available
     */
    val NO_DATA_AVAILABLE_EXAMPLE = """
    {
      "insights": [
        {
          "type": "Information",
          "title": "Snapchat Data Usage",
          "description": "No data usage reported by Snapchat for this week.",
          "severity": "info"
        }
      ]
    }
    """.trimIndent()

    /**
     * Example response for "What's using my battery in the background?"
     */
    val BACKGROUND_BATTERY_USAGE_EXAMPLE = """
    {
      "insights": [
        {
          "type": "Information",
          "title": "Background Battery Usage",
          "description": "1. Google Services (8%)\n2. Gmail (5%)\n3. Weather (3%)",
          "severity": "info"
        }
      ]
    }
    """.trimIndent()
} 