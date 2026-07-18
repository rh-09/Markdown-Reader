package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 1. Enable modern edge-to-edge rendering
        enableEdgeToEdge()

        // 2. Initialize Room Database and Repository
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = MarkdownRepository(applicationContext, database.markdownDocumentDao())

        // 3. Initialize ViewModel via simple constructor factory
        val viewModelFactory = MarkdownViewModel.Factory(application, repository)
        val viewModel = ViewModelProvider(this, viewModelFactory)[MarkdownViewModel::class.java]

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigationContainer(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun AppNavigationContainer(viewModel: MarkdownViewModel) {
    val context = LocalContext.current
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()

    // 4. Set up the System SAF (Storage Access Framework) file picker
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                // Take persistable read permissions so we can open the file again on app restarts
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                
                // Open the picked document in Reader screen
                viewModel.navigateTo(AppScreen.Reader(uri.toString()))
            } catch (e: Exception) {
                e.printStackTrace()
                // If taking persistable permission fails, we still navigate but warn the user
                viewModel.navigateTo(AppScreen.Reader(uri.toString()))
            }
        }
    }

    val openPicker = {
        // We only want to read markdown and text files
        filePickerLauncher.launch(
            arrayOf(
                "text/plain",
                "text/markdown",
                "application/octet-stream" // some markdown files are categorized as octet-stream
            )
        )
    }

    // 5. High-fidelity smooth crossfade navigation between Library and Reader
    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "ScreenNavigation"
    ) { screen ->
        when (screen) {
            is AppScreen.Library -> {
                LibraryScreen(
                    viewModel = viewModel,
                    onOpenFilePicker = openPicker,
                    modifier = Modifier.fillMaxSize()
                )
            }
            is AppScreen.Reader -> {
                ReaderScreen(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
