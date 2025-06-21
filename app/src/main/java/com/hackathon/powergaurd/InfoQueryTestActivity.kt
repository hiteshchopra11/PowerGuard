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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.hackathon.powergaurd.llm.InformationQueryTest
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Activity for testing Information Queries with real LLM
 */
@AndroidEntryPoint
class InfoQueryTestActivity : AppCompatActivity() {
    
    private val viewModel: InformationQueryTest by viewModels()
    
    private val logMessages = MutableStateFlow<List<String>>(emptyList())
    private val maxLogLines = 100 // Increased for more output
    
    private lateinit var queryInput: EditText
    private lateinit var resultText: TextView
    private lateinit var mainLayout: LinearLayout
    private lateinit var resultsScrollView: ScrollView
    
    companion object {
        private const val TAG = "PowerGuard-InfoQueryTestAct"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Setup edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // Enable the back button in the action bar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Information Query Test"
        
        // Create layout programmatically
        createLayout()
        
        // Setup insets handling for edge-to-edge display
        setupEdgeToEdgeInsets()
        
        // Register log listener
        viewModel.setLogListener { message ->
            addLogMessage(message)
        }
        
        // Log that the activity is ready
        Log.d(TAG, "Info Query Test Activity ready")
        addLogMessage("Info Query Test Activity ready")
        addLogMessage("This activity tests information queries with the REAL LLM")
    }
    
    private fun setupEdgeToEdgeInsets() {
        // Apply window insets padding to the main layout
        ViewCompat.setOnApplyWindowInsetsListener(mainLayout) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            // Apply padding based on insets
            mainLayout.updatePadding(
                left = mainLayout.paddingLeft,
                right = mainLayout.paddingRight,
                top = insets.top + 32, // Add to existing top padding
                bottom = insets.bottom + 32 // Add to existing bottom padding
            )
            
            WindowInsetsCompat.CONSUMED
        }
    }
    
    private fun createLayout() {
        // Create root scroll view
        val scrollView = ScrollView(this)
        scrollView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        )
        
        // Create main container
        mainLayout = LinearLayout(this)
        mainLayout.orientation = LinearLayout.VERTICAL
        mainLayout.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        mainLayout.setPadding(32, 32, 32, 32)
        
        // Create title
        val titleText = TextView(this)
        titleText.text = "Information Query Test (Real LLM)"
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
        queryInput.hint = "Enter your information query"
        queryInput.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        mainLayout.addView(queryInput)
        
        // Create run query button
        val runQueryButton = Button(this)
        runQueryButton.text = "Run Single Query"
        runQueryButton.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = 16
            bottomMargin = 16
        }
        runQueryButton.setOnClickListener {
            val query = queryInput.text.toString().trim()
            if (query.isNotEmpty()) {
                addLogMessage("Running query: $query")
                viewModel.runSingleTest(query)
            }
        }
        mainLayout.addView(runQueryButton)
        
        // Create run all tests button
        val runAllButton = Button(this)
        runAllButton.text = "Run All Test Cases"
        runAllButton.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = 32
        }
        runAllButton.setOnClickListener {
            addLogMessage("Running all test cases")
            viewModel.runAllTests()
        }
        mainLayout.addView(runAllButton)
        
        // Create results section title
        val resultsTitle = TextView(this)
        resultsTitle.text = "Results"
        resultsTitle.textSize = 18f
        resultsTitle.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = 16
        }
        mainLayout.addView(resultsTitle)
        
        // Create a nested scroll view for results
        resultsScrollView = ScrollView(this)
        resultsScrollView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            height = 600 // Set a fixed height for scrolling
        }
        resultsScrollView.setBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray))
        resultsScrollView.isVerticalScrollBarEnabled = true // Enable vertical scrollbar
        resultsScrollView.isSmoothScrollingEnabled = true // Enable smooth scrolling
        
        // Create results text view inside the nested scroll view
        resultText = TextView(this)
        resultText.text = "Results will appear here..."
        resultText.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        resultText.setPadding(16, 16, 16, 16)
        resultText.isVerticalScrollBarEnabled = true
        
        // Create a linear layout to hold the text within the scroll view
        val textContainer = LinearLayout(this)
        textContainer.orientation = LinearLayout.VERTICAL
        textContainer.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        textContainer.addView(resultText)
        
        // Add the text container to the ScrollView
        resultsScrollView.addView(textContainer)
        
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
                
                // After updating text, scroll to the bottom
                resultsScrollView.post {
                    resultsScrollView.fullScroll(ScrollView.FOCUS_DOWN)
                }
            }
        }
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
    
    private fun addLogMessage(message: String) {
        logMessages.value = (logMessages.value + message).takeLast(maxLogLines)
    }
} 