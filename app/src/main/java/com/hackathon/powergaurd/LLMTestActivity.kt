package com.hackathon.powergaurd

import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.hackathon.powergaurd.llm.LLMTest
import com.hackathon.powergaurd.llm.QueryProcessor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Test Activity for LLM implementation
 * This activity allows testing different categories of queries
 */
@AndroidEntryPoint
class LLMTestActivity : AppCompatActivity() {
    
    private val viewModel: LLMTest by viewModels()
    
    @Inject
    lateinit var queryProcessor: QueryProcessor
    
    private val logMessages = MutableStateFlow<List<String>>(emptyList())
    private val maxLogLines = 20
    
    private lateinit var queryInput: EditText
    private lateinit var resultText: TextView
    
    companion object {
        private const val TAG = "PowerGuard-LLMTestActivity"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable the back button in the action bar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "LLM Query Testing"
        
        // Create layout programmatically since XML is having issues
        createLayout()
        
        // Register log listener
        viewModel.setLogListener { message ->
            addLogMessage(message)
        }
        
        // Log that the activity is ready
        Log.d(TAG, "LLM Test Activity ready")
        addLogMessage("LLM Test Activity ready")
        addLogMessage("Select a category to test or enter a custom query")
    }
    
    private fun createLayout() {
        // Create root scroll view
        val scrollView = ScrollView(this)
        scrollView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        )
        
        // Create main container
        val mainLayout = LinearLayout(this)
        mainLayout.orientation = LinearLayout.VERTICAL
        mainLayout.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        mainLayout.setPadding(32, 32, 32, 32)
        
        // Create title
        val titleText = TextView(this)
        titleText.text = "LLM Implementation Test"
        titleText.textSize = 24f
        titleText.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = 48
        }
        mainLayout.addView(titleText)
        
        // Create query input
        queryInput = EditText(this)
        queryInput.hint = "Enter your query"
        queryInput.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        mainLayout.addView(queryInput)
        
        // Create run query button
        val runQueryButton = Button(this)
        runQueryButton.text = "Run Query"
        runQueryButton.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = 16
            bottomMargin = 32
        }
        runQueryButton.setOnClickListener {
            val query = queryInput.text.toString().trim()
            if (query.isNotEmpty()) {
                addLogMessage("Running query: $query")
                runQuery(query)
            }
        }
        mainLayout.addView(runQueryButton)
        
        // Create categories section title
        val categoriesTitle = TextView(this)
        categoriesTitle.text = "Test Categories"
        categoriesTitle.textSize = 18f
        categoriesTitle.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = 16
            bottomMargin = 16
        }
        mainLayout.addView(categoriesTitle)
        
        // Create category buttons
        val buttonLabels = listOf(
            "Test Category 1 - Information",
            "Test Category 2 - Predictive",
            "Test Category 3 - Optimization",
            "Test Category 4 - Monitoring",
            "Test All Categories"
        )
        
        for ((index, label) in buttonLabels.withIndex()) {
            val button = Button(this)
            button.text = label
            button.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 8
            }
            
            button.setOnClickListener {
                when (index) {
                    4 -> {
                        addLogMessage("Testing all categories")
                        viewModel.testAllCategories()
                    }
                    else -> {
                        val category = index + 1
                        addLogMessage("Testing Category $category")
                        viewModel.testCategory(category)
                    }
                }
            }
            
            mainLayout.addView(button)
        }
        
        // Create results section title
        val resultsTitle = TextView(this)
        resultsTitle.text = "Results"
        resultsTitle.textSize = 18f
        resultsTitle.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = 32
            bottomMargin = 16
        }
        mainLayout.addView(resultsTitle)
        
        // Create a nested scroll view for results
        val resultsScrollView = ScrollView(this)
        resultsScrollView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            height = 600 // Set a fixed height for scrolling
        }
        resultsScrollView.setBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray))
        
        // Create results text view inside the nested scroll view
        resultText = TextView(this)
        resultText.text = "Results will appear here..."
        resultText.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        resultText.setPadding(16, 16, 16, 16)
        
        // Add the TextView to the nested ScrollView
        resultsScrollView.addView(resultText)
        
        // Add the nested ScrollView to the main layout
        mainLayout.addView(resultsScrollView)
        
        // Add main layout to scroll view
        scrollView.addView(mainLayout)
        
        // Set content view
        setContentView(scrollView)
        
        // Set up logging collector
        lifecycleScope.launch {
            logMessages.collectLatest { messages ->
                val logText = messages.joinToString("\n")
                resultText.text = logText
            }
        }
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish() // Use finish() instead of deprecated onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
    
    private fun addLogMessage(message: String) {
        logMessages.value = (logMessages.value + message).takeLast(maxLogLines)
    }
    
    private fun runQuery(query: String) {
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Running query: $query")
                val result = queryProcessor.processQuery(query)
                Log.d(TAG, "Result: $result")
                addLogMessage("RESULT: $result")
            } catch (e: Exception) {
                Log.e(TAG, "Error processing query", e)
                addLogMessage("ERROR: ${e.message}")
            }
        }
    }
} 