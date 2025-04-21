package com.hackathon.powergaurd.data.gemma

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Looper
import android.util.Log
import com.hackathon.powergaurd.data.model.Actionable
import com.hackathon.powergaurd.data.model.AnalysisRepository
import com.hackathon.powergaurd.data.model.AnalysisResponse
import com.hackathon.powergaurd.data.model.DeviceData
import com.hackathon.powergaurd.data.model.Insight
import com.powergaurd.llm.GemmaConfig
import com.powergaurd.llm.GemmaInferenceSDK
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for handling on-device AI inference using GemmaInferenceSDK.
 */
@Singleton
class GemmaRepository @Inject constructor(
    private val context: Context,
    private val config: GemmaConfig
) : AnalysisRepository {
    companion object {
        private const val TAG = "GemmaRepository"

        // Log tags
        private const val LOG_SDK = "GemmaSDK_Debug"
        private const val LOG_PROMPT = "GemmaPrompt_Debug"
        private const val LOG_QUERY = "GemmaQuery_Debug"
        private const val LOG_ERROR = "GemmaError_Debug"

        // Constants
        private const val GENERATION_TIMEOUT_MS = 10000L

        // Query categories
        private const val CATEGORY_INFORMATION = 1
        private const val CATEGORY_PREDICTIVE = 2
        private const val CATEGORY_OPTIMIZATION = 3
        private const val CATEGORY_MONITORING = 4
        private const val CATEGORY_INVALID = 0

        // Resource types
        private const val RESOURCE_BATTERY = "BATTERY"
        private const val RESOURCE_DATA = "DATA"
        private const val RESOURCE_OTHER = "OTHER"

        // Actionable types
        const val SET_STANDBY_BUCKET = "set_standby_bucket"
        const val RESTRICT_BACKGROUND_DATA = "restrict_background_data"
        const val KILL_APP = "kill_app"
        const val MANAGE_WAKE_LOCKS = "manage_wake_locks"
        const val SET_NOTIFICATION = "set_notification"
        const val SET_ALARM = "set_alarm"

        private val ALLOWED_ACTIONABLE_TYPES = listOf(
            SET_STANDBY_BUCKET,
            RESTRICT_BACKGROUND_DATA,
            KILL_APP,
            MANAGE_WAKE_LOCKS,
            SET_NOTIFICATION,
            SET_ALARM
        )
    }

    private var _sdk: GemmaInferenceSDK? = null

    private val sdk: GemmaInferenceSDK
        get() {
            if (_sdk == null) {
                synchronized(this) {
                    if (_sdk == null) {
                        if (Looper.myLooper() == Looper.getMainLooper()) {
                            _sdk = GemmaInferenceSDK(context, config)
                            Log.d(TAG, "GemmaInferenceSDK created on main thread")
                        } else {
                            val latch = CountDownLatch(1)
                            var error: Exception? = null
                            MainScope().launch(Dispatchers.Main) {
                                try {
                                    _sdk = GemmaInferenceSDK(context, config)
                                } catch (e: Exception) {
                                    error = e
                                } finally {
                                    latch.countDown()
                                }
                            }
                            latch.await(5, TimeUnit.SECONDS)
                            if (error != null) throw error!!
                            if (_sdk == null) throw IllegalStateException("SDK initialization failed")
                        }
                    }
                }
            }
            return _sdk!!
        }

    suspend fun initialize(): Boolean = withContext(Dispatchers.Main) {
        try {
            sdk.initialize()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize SDK: ${e.message}", e)
            false
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val nc = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        return nc?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * Analyzes device data following the specified flow:
     * 1. Determine resource type (battery, data, or other).
     * 2. Categorize the query.
     * 3. Create and send a modified prompt to LLM.
     * 4. Return JSON response with insights and actionables.
     */
    @SuppressLint("NewApi")
    override suspend fun analyzeDeviceData(deviceData: DeviceData): Result<AnalysisResponse> =
        withContext(Dispatchers.IO) {
            try {
                val userQuery = deviceData.prompt
                var resourceType = RESOURCE_OTHER
                var queryCategory = CATEGORY_INVALID
                var dataToProcess = deviceData

                if (!userQuery.isNullOrBlank()) {
                    resourceType = determineResourceType(userQuery)
                    Log.d(LOG_QUERY, "Resource type: $resourceType")
                    queryCategory = categorizeQuery(userQuery, resourceType)
                    Log.d(LOG_QUERY, "User query: $userQuery, Categorized as: $queryCategory")
                    dataToProcess = deviceData.copy(
                        prompt = "RESOURCE:$resourceType QUERY_CATEGORY:$queryCategory - $userQuery"
                    )
                }

                val prompt = createAnalysisPrompt(dataToProcess, resourceType, queryCategory)
                Log.d(LOG_PROMPT, "Full Prompt:\n$prompt")

                if (!isNetworkAvailable()) {
                    Log.w(LOG_SDK, "Network unavailable, using simulated response")
                    return@withContext Result.success(
                        simulateAnalysisResponse(
                            dataToProcess,
                            resourceType,
                            queryCategory
                        )
                    )
                }

                try {
                    val result = processPromptWithGemma(prompt, dataToProcess, resourceType)
                    Log.d(LOG_SDK, "LLM processing completed")
                    return@withContext result
                } catch (e: Exception) {
                    Log.e(LOG_ERROR, "LLM error: ${e.message}", e)
                    return@withContext Result.success(
                        simulateAnalysisResponse(dataToProcess, resourceType, queryCategory).copy(
                            message = "LLM error: ${e.message}"
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(LOG_ERROR, "Fatal error: ${e.message}", e)
                return@withContext Result.success(
                    simulateAnalysisResponse(
                        deviceData,
                        RESOURCE_OTHER,
                        CATEGORY_INVALID
                    )
                )
            }
        }

    private suspend fun determineResourceType(query: String): String {
        try {
            val prompt = """
                Determine if the following user query is primarily about BATTERY, DATA, or OTHER.
                Respond with ONLY the resource type: BATTERY, DATA, or OTHER.
                Query: "$query"
            """.trimIndent()

            val response = withTimeout(15000) {
                sdk.generateResponseSuspend(prompt, maxTokens = 10, temperature = 0.1f)
            }.trim().uppercase()

            return when (response) {
                RESOURCE_BATTERY, RESOURCE_DATA -> response
                else -> RESOURCE_OTHER
            }
        } catch (e: Exception) {
            Log.e(LOG_QUERY, "Error determining resource type: ${e.message}", e)
            return RESOURCE_OTHER
        }
    }

    private suspend fun categorizeQuery(query: String, resourceType: String): Int {
        try {
            val categorizePrompt = """
                Categorize this user query into one of these categories based on its intent:
                1. Information Queries (e.g., "Which apps use the most $resourceType?")
                2. Predictive Queries (e.g., "Can I use an app with current $resourceType?")
                3. Optimization Requests (e.g., "Optimize $resourceType for an activity")
                4. Monitoring Triggers (e.g., "Notify me when $resourceType reaches a threshold")
                0. Invalid Query (unrelated to $resourceType or unclear)
                Respond with ONLY the category number (0-4).
                Query: "$query"
            """.trimIndent()

            val response = withTimeout(15000) {
                sdk.generateResponseSuspend(categorizePrompt, maxTokens = 5, temperature = 0.1f)
            }.trim().toIntOrNull()

            return response?.coerceIn(0, 4) ?: CATEGORY_INVALID
        } catch (e: Exception) {
            Log.e(LOG_QUERY, "Error categorizing query: ${e.message}", e)
            return CATEGORY_INVALID
        }
    }

    private fun createAnalysisPrompt(
        deviceData: DeviceData,
        resourceType: String,
        queryCategory: Int
    ): String {
        val prompt = StringBuilder()
        val userQuery = deviceData.prompt?.substringAfter("- ")?.trim() ?: ""

        prompt.append(
            """
            You are a battery and data analysis/saver system. Analyze the device data and user query below.
            Respond with ONLY a valid JSON object matching this structure:
            {
                "batteryScore": number between 0-100,
                "dataScore": number between 0-100,
                "performanceScore": number between 0-100,
                "insights": [{"type": "BATTERY|DATA", "title": "string", "description": "string", "severity": "LOW|MEDIUM|HIGH"}],
                "actionable": [{"type": "string", "description": "string", "parameters": {}}]
            }

            IMPORTANT: The "actionable" array must only contain objects with "type" being one of the following, with their purposes:
    - $SET_STANDBY_BUCKET: Limits an app's background activity by placing it in a standby bucket (e.g., restricted), saving battery and data.
    - $RESTRICT_BACKGROUND_DATA: Prevents an app from using data in the background, reducing data consumption.
    - $KILL_APP: Force stops an app to immediately reduce resource usage, though it may restart later.
    - $MANAGE_WAKE_LOCKS: Restricts an app from keeping the device awake, reducing battery drain.
    - $SET_NOTIFICATION: Sets a notification to alert the user when a condition is met (e.g., low battery).
    - $SET_ALARM: Configures an alarm to trigger when a condition is met (e.g., data threshold reached).
    Do not create or use any other actionable types.

    When including actionables, ensure to provide the necessary parameters:
    - For $SET_STANDBY_BUCKET: "packageName" and "newMode", newMode should be one of the "active", "working_set" or "frequent" where frequent is the most aggressive mode
    - For $RESTRICT_BACKGROUND_DATA: "packageName"
    - For $KILL_APP: "packageName"
    - For $MANAGE_WAKE_LOCKS: "packageName"
    - For $SET_NOTIFICATION and $SET_ALARM: "condition" and "message"

            Device Data (last 24 hours):
            DEVICE: ${deviceData.deviceInfo.manufacturer} ${deviceData.deviceInfo.model}
            BATTERY: ${deviceData.battery.level}%${if (deviceData.battery.isCharging) " (Charging)" else ""}
            DATA: Current ${deviceData.currentDataMb}MB, Total ${deviceData.totalDataMb}MB
        """.trimIndent()
        )

        val topBatteryApps = deviceData.apps.sortedByDescending { it.batteryUsage }.take(10)
        val topDataApps =
            deviceData.apps.sortedByDescending { it.dataUsage.rxBytes + it.dataUsage.txBytes }
                .take(10)

        if (topBatteryApps.isNotEmpty()) {
            prompt.append("\nTOP BATTERY APPS:\n")
            topBatteryApps.forEach { prompt.append("- ${it.appName}: ${it.batteryUsage}%\n") }
        }

        if (topDataApps.isNotEmpty()) {
            prompt.append("\nTOP DATA APPS:\n")
            topDataApps.forEach {
                val mb = (it.dataUsage.rxBytes + it.dataUsage.txBytes) / (1024 * 1024)
                prompt.append("- ${it.appName}: ${mb}MB\n")
            }
        }

        if (userQuery.isNotEmpty()) {
            prompt.append("\nUSER QUERY: $userQuery\n")
        }

        when (queryCategory) {
            CATEGORY_INFORMATION -> {
                val numMatch = "\\b(\\d+)\\b".toRegex().find(userQuery)
                val numRequested = numMatch?.groupValues?.get(1)?.toIntOrNull() ?: 5
                prompt.append(
                    """
                    INSTRUCTIONS FOR INFORMATION QUERY:
                    - Provide exactly $numRequested items if specified, else 5.
                    - Use data from Device Data for ${resourceType.lowercase()}.
                    - Return insights only, do not include any actionables.
                    - Type: "$resourceType".
                """.trimIndent()
                )
            }

            CATEGORY_PREDICTIVE -> {
                prompt.append(
                    """
                    INSTRUCTIONS FOR PREDICTIVE QUERY:
                    - Analyze feasibility based on current ${resourceType.lowercase()} and typical app usage.
                    - Return yes/no with a brief explanation in insights.
                    - Return insights only, do not include any actionables.
                    - Type: "$resourceType".
                """.trimIndent()
                )
            }

            CATEGORY_OPTIMIZATION -> {
                prompt.append(
                    """
                    INSTRUCTIONS FOR OPTIMIZATION REQUEST:
                    - Provide recommendations to optimize $resourceType.
                    - Include actionables only from: $SET_STANDBY_BUCKET, $RESTRICT_BACKGROUND_DATA, $KILL_APP, $MANAGE_WAKE_LOCKS. choose from description whatever is appropriate
                    - For each actionable, include the required parameters as specified above.
                    - Type: "$resourceType".
                    
                    IMPORTANT EXCLUSION HANDLING:
                    1. First, identify any apps that should be kept running based on the user query.
                       For example, in "Save battery but keep WhatsApp running", "WhatsApp" should be excluded from restrictions.
                    2. For each excluded app:
                       - Include a "$SET_STANDBY_BUCKET" actionable with "newMode" set to "active"
                       - Set "packageName" to the appropriate package name for that app
                       - Include a description like "Keep [AppName] active as requested"
                    3. Do NOT apply any restrictive actions to excluded apps.
                    4. For all other apps, apply appropriate restrictions to optimize $resourceType.
                    5. Include one insight stating something like limiting functionality for other apps while keeping WhatsApp running for "Save battery but keep WhatsApp running". Just an example, you choose appropriate insight
                """.trimIndent()
                )
            }

            CATEGORY_MONITORING -> {
                prompt.append(
                    """
                    INSTRUCTIONS FOR MONITORING TRIGGER:
                    - Set up a trigger using $SET_NOTIFICATION if the query mentions "notify", otherwise use $SET_ALARM.
                    - Include "condition" and "message" in parameters.
                    - Type: "$resourceType".
                """.trimIndent()
                )
            }

            else -> {
                prompt.append("INSTRUCTIONS: General analysis, minimal response. Do not include actionables.")
            }
        }

        return prompt.toString()
    }

    private suspend fun processPromptWithGemma(
        prompt: String,
        deviceData: DeviceData,
        resourceType: String
    ): Result<AnalysisResponse> {
        try {
            ensureSdkInitialized()
            val jsonResponse = withTimeout(GENERATION_TIMEOUT_MS) {
                val response = sdk.generateResponseSuspend(
                    prompt,
                    maxTokens = config.maxTokens,
                    temperature = 0.1f
                )
                Log.d(LOG_SDK, "LLM Response: $response")
                val jsonStart = response.indexOf("{")
                val jsonEnd = response.lastIndexOf("}")
                if (jsonStart in 0..<jsonEnd) {
                    JSONObject(response.substring(jsonStart, jsonEnd + 1))
                } else null
            }
            return Result.success(jsonResponse?.let { parseGemmaResponse(it, resourceType) }
                ?: simulateAnalysisResponse(deviceData, RESOURCE_OTHER, CATEGORY_INVALID))
        } catch (e: Exception) {
            Log.e(LOG_ERROR, "Processing error: ${e.message}", e)
            return Result.success(
                simulateAnalysisResponse(
                    deviceData,
                    RESOURCE_OTHER,
                    CATEGORY_INVALID
                )
            )
        }
    }

    private fun parseGemmaResponse(json: JSONObject, resourceType: String = RESOURCE_OTHER): AnalysisResponse {
        val insights = mutableListOf<Insight>()
        val actionables = mutableListOf<Actionable>()
        val insightsArray = json.optJSONArray("insights") ?: JSONArray()
        val actionablesArray = json.optJSONArray("actionable") ?: JSONArray()

        for (i in 0 until insightsArray.length()) {
            val insight = insightsArray.getJSONObject(i)
            val insightType = insight.optString("type")

            // Only include insights matching the query's resource type
            if (resourceType == RESOURCE_OTHER || insightType == resourceType) {
                insights.add(
                    Insight(
                        type = insightType,
                        title = insight.optString("title"),
                        description = insight.optString("description"),
                        severity = insight.optString("severity")
                    )
                )
            }
        }

        for (i in 0 until actionablesArray.length()) {
            val actionable = actionablesArray.getJSONObject(i)
            val type = actionable.optString("type")
            if (type !in ALLOWED_ACTIONABLE_TYPES) {
                Log.w(LOG_ERROR, "Invalid actionable type: $type, discarding")
                continue
            }
            val description = actionable.optString("description")
            val params = actionable.optJSONObject("parameters")?.let {
                mutableMapOf<String, String>().apply {
                    it.keys().forEach { key -> put(key, it.getString(key)) }
                }
            } ?: emptyMap()

            val packageName =
                params["packageName"] ?: extractPackageNameFromDescription(description)
                ?: "com.android.settings"

            // Create the description with app name instead of "data-intensive apps" for set_standby_bucket
            val formattedDescription = if (type == SET_STANDBY_BUCKET &&
                description.contains("data-intensive apps", ignoreCase = true)) {
                // Get app name if possible
                val appName = try {
                    val packageManager = context.packageManager
                    val appInfo = packageManager.getApplicationInfo(packageName, 0)
                    packageManager.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) {
                    // If can't get app name, use package name
                    packageName
                }

                // Replace "data-intensive apps" with app name
                description.replace("data-intensive apps", appName, ignoreCase = true)
            } else {
                description
            }

            actionables.add(
                Actionable(
                    id = UUID.randomUUID().toString(),
                    type = type,
                    packageName = packageName,
                    description = formattedDescription,
                    reason = "AI recommended",
                    estimatedBatterySavings = if (type in listOf(
                            SET_STANDBY_BUCKET,
                            KILL_APP,
                            MANAGE_WAKE_LOCKS
                        )
                    ) 10.0f else 0.0f,
                    estimatedDataSavings = if (type == RESTRICT_BACKGROUND_DATA) 50.0f else 0.0f,
                    severity = 3,
                    parameters = params,
                    enabled = true,
                    newMode = params["newMode"]
                        ?: if (type == SET_STANDBY_BUCKET) "working_set" else "",
                    throttleLevel = 0
                )
            )
        }

        return AnalysisResponse(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis().toFloat(),
            success = true,
            message = "Gemma response parsed successfully",
            responseType = "analysis",
            actionable = actionables,
            insights = insights,
            batteryScore = 75f,
            dataScore = 80f,
            performanceScore = 70f,
            estimatedSavings = AnalysisResponse.EstimatedSavings(
                batteryMinutes = 15f,
                dataMB = 200f
            )
        )
    }

    private fun extractPackageNameFromDescription(description: String): String? {
        val knownApps = listOf(
            "1Weather" to "com.handmark.expressweather",
            "Teams" to "com.microsoft.teams",
            "Docs" to "com.google.android.apps.docs",
            "Chrome" to "com.android.chrome",
            "Subway Surf" to "com.kiloo.subwaysurf",
            "Outlook" to "com.microsoft.office.outlook",
            "Gmail" to "com.google.android.gm",
            "Meet" to "com.google.android.apps.meetings",
            "Netflix" to "com.netflix.mediaclient",
            "WhatsApp" to "com.whatsapp",
            "Spotify" to "com.spotify.music",
            "Facebook" to "com.facebook.katana",
            "Instagram" to "com.instagram.android",
            "Snapchat" to "com.snapchat.android",
            "YouTube" to "com.google.android.youtube",
            "TikTok" to "com.zhiliaoapp.musically",
            "Messenger" to "com.facebook.orca",
            "Telegram" to "org.telegram.messenger",
            "Twitter" to "com.twitter.android",
            "LinkedIn" to "com.linkedin.android",
            "Pinterest" to "com.pinterest",
            "Reddit" to "com.reddit.frontpage",
            "Amazon" to "com.amazon.mShop.android.shopping",
            "Flipkart" to "com.flipkart.android",
            "Snapdeal" to "com.snapdeal.main",
            "Myntra" to "com.myntra.android",
            "Swiggy" to "in.swiggy.android",
            "Zomato" to "com.application.zomato",
            "Uber" to "com.ubercab",
            "Ola" to "com.olacabs.customer",
            "Google Maps" to "com.google.android.apps.maps",
            "Google Drive" to "com.google.android.apps.docs",
            "Google Photos" to "com.google.android.apps.photos",
            "Google Pay" to "com.google.android.apps.nbu.paisa.user",
            "Paytm" to "net.one97.paytm",
            "PhonePe" to "com.phonepe.app",
            "Truecaller" to "com.truecaller",
            "MX Player" to "com.mxtech.videoplayer.ad",
            "Hotstar" to "in.startv.hotstar",
            "JioCinema" to "com.jio.media.ondemand",
            "Gaana" to "com.gaana",
            "Saavn" to "com.saavn.android",
            "Wynk Music" to "com.bsbportal.music",
            "Google News" to "com.google.android.apps.magazines",
            "Inshorts" to "com.nis.app",
            "Dailyhunt" to "com.newshunt.news",
            "Quora" to "com.quora.android",
            "Coursera" to "org.coursera.android",
            "Udemy" to "com.udemy.android",
            "Khan Academy" to "org.khanacademy.android",
            "Duolingo" to "com.duolingo",
            "BYJU'S" to "com.byjus.thelearningapp",
            "Unacademy" to "com.unacademyapp",
            "Zoom" to "us.zoom.videomeetings",
            "Skype" to "com.skype.raider",
            "Slack" to "com.Slack",
            "Dropbox" to "com.dropbox.android",
            "Evernote" to "com.evernote",
            "Notion" to "notion.id",
            "Google Keep" to "com.google.android.keep",
            "CamScanner" to "com.intsig.camscanner",
            "Adobe Acrobat" to "com.adobe.reader",
            "WPS Office" to "cn.wps.moffice_eng",
            "Microsoft Word" to "com.microsoft.office.word",
            "Microsoft Excel" to "com.microsoft.office.excel",
            "Microsoft PowerPoint" to "com.microsoft.office.powerpoint",
            "Google Calendar" to "com.google.android.calendar",
            "Google Translate" to "com.google.android.apps.translate",
            "Google Assistant" to "com.google.android.apps.googleassistant",
            "Google Lens" to "com.google.ar.lens",
            "Google Home" to "com.google.android.apps.chromecast.app",
            "Mi Fit" to "com.xiaomi.hm.health",
            "Fitbit" to "com.fitbit.FitbitMobile",
            "Samsung Health" to "com.sec.android.app.shealth",
            "Nike Training Club" to "com.nike.ntc",
            "Strava" to "com.strava",
            "MyFitnessPal" to "com.myfitnesspal.android",
            "Calm" to "com.calm.android",
            "Headspace" to "com.getsomeheadspace.android",
            "Sleep Cycle" to "com.northcube.sleepcycle",
            "Period Tracker" to "com.lovelyapps.PeriodTracker",
            "Clue" to "com.clue.android",
            "Flo" to "org.iggymedia.periodtracker",
            "BabyCenter" to "com.babycenter.pregnancytracker",
            "FirstCry" to "com.firstcry",
            "BabyChakra" to "com.babychakra",
            "Koo" to "com.koo.app",
            "ShareChat" to "in.mohalla.sharechat",
            "Josh" to "com.eterno.shortvideos",
            "Moj" to "in.mohalla.video",
            "Roposo" to "com.roposo.android",
            "Chingari" to "io.chingari.app",
            "MX TakaTak" to "com.next.innovation.takatak",
            "Mitron" to "com.mitron.tv",
            "Trell" to "app.trell",
            "Bolo Indya" to "com.boloindya.boloindya",
            "Public" to "in.public.app",
            "Dailyhunt" to "com.newshunt.news",
            "NewsPoint" to "com.newspoint.android",
            "JioNews" to "com.jio.media.jionews",
            "Way2News" to "com.way2news.way2news",
            "Flipboard" to "flipboard.app",
            "Google Podcasts" to "com.google.android.apps.podcasts",
            "Spotify" to "com.spotify.music",
            "Pocket Casts" to "au.com.shiftyjelly.pocketcasts",
            "Castbox" to "com.podcast.podcasts",
            "Anchor" to "fm.anchor.android",
            "SoundCloud" to "com.soundcloud.android",
            "TuneIn Radio" to "tunein.player",
            "JioSaavn" to "com.jio.media.jiobeats",
            "Hungama Music" to "com.hungama.myplay.activity",
            "Amazon Music" to "com.amazon.mp3",
        )
        for ((appName, packageName) in knownApps) {
            if (description.contains(appName, ignoreCase = true)) {
                return packageName
            }
        }
        return null
    }

    private fun simulateAnalysisResponse(
        deviceData: DeviceData,
        resourceType: String,
        queryCategory: Int
    ): AnalysisResponse {
        val insights = mutableListOf<Insight>()
        val actionables = mutableListOf<Actionable>()
        val userQuery = deviceData.prompt?.substringAfter("- ")?.trim() ?: ""

        when (queryCategory) {
            CATEGORY_INFORMATION -> {
                val numRequested =
                    "\\b(\\d+)\\b".toRegex().find(userQuery)?.groupValues?.get(1)?.toIntOrNull()
                        ?: 5
                val apps = if (resourceType == RESOURCE_BATTERY) {
                    deviceData.apps.sortedByDescending { it.batteryUsage }.take(numRequested)
                } else {
                    deviceData.apps.sortedByDescending { it.dataUsage.rxBytes + it.dataUsage.txBytes }
                        .take(numRequested)
                }
                val desc = if (apps.isNotEmpty()) {
                    apps.joinToString("\n") {
                        if (resourceType == RESOURCE_BATTERY) "• ${it.appName}: ${it.batteryUsage}%"
                        else "• ${it.appName}: ${(it.dataUsage.rxBytes + it.dataUsage.txBytes) / (1024 * 1024)}MB"
                    }
                } else "Sorry, no ${resourceType.lowercase()} data available."
                insights.add(
                    Insight(
                        resourceType,
                        "Top $numRequested ${resourceType.lowercase()} usage",
                        desc,
                        "LOW"
                    )
                )
            }

            CATEGORY_PREDICTIVE -> {
                val remaining =
                    if (resourceType == RESOURCE_BATTERY) deviceData.battery.level else (deviceData.totalDataMb - deviceData.currentDataMb)
                insights.add(
                    Insight(
                        resourceType,
                        "Prediction",
                        "Assuming typical usage, $remaining ${if (resourceType == RESOURCE_BATTERY) "%" else "MB"} may suffice.",
                        "MEDIUM"
                    )
                )
            }

            CATEGORY_OPTIMIZATION -> {
                insights.add(
                    Insight(
                        resourceType,
                        "Optimization Applied",
                        "Restricted high-usage apps.",
                        "MEDIUM"
                    )
                )
                actionables.add(
                    Actionable(
                        id = UUID.randomUUID().toString(),
                        type = if (resourceType == RESOURCE_BATTERY) KILL_APP else RESTRICT_BACKGROUND_DATA,
                        packageName = "com.android.settings",
                        description = "Restrict high ${resourceType.lowercase()} usage apps",
                        reason = "Optimization",
                        estimatedBatterySavings = if (resourceType == RESOURCE_BATTERY) 10.0f else 0.0f,
                        estimatedDataSavings = if (resourceType == RESOURCE_DATA) 50.0f else 0.0f,
                        severity = 3,
                        parameters = emptyMap(),
                        enabled = true,
                        newMode = "",
                        throttleLevel = 0
                    )
                )
            }

            CATEGORY_MONITORING -> {
                insights.add(Insight(resourceType, "Monitoring Set", "Trigger configured.", "LOW"))
                actionables.add(
                    Actionable(
                        id = UUID.randomUUID().toString(),
                        type = if ("notify" in userQuery.lowercase()) SET_NOTIFICATION else SET_ALARM,
                        packageName = "com.android.settings",
                        description = "Monitor $resourceType",
                        reason = "User request",
                        estimatedBatterySavings = 0.0f,
                        estimatedDataSavings = 0.0f,
                        severity = 1,
                        parameters = mapOf(
                            "condition" to userQuery,
                            "message" to "Threshold reached"
                        ),
                        enabled = true,
                        newMode = "",
                        throttleLevel = 0
                    )
                )
            }
        }

        return AnalysisResponse(
            id = deviceData.deviceId,
            timestamp = System.currentTimeMillis().toFloat(),
            success = true,
            message = "Simulated response",
            actionable = actionables,
            insights = insights,
            batteryScore = 50f,
            dataScore = 50f,
            performanceScore = 50f,
            estimatedSavings = AnalysisResponse.EstimatedSavings(15f, 100f)
        )
    }

    private suspend fun ensureSdkInitialized() {
        if (_sdk == null) sdk
        if (!sdk.initialize()) throw IllegalStateException("SDK initialization failed")
    }
}