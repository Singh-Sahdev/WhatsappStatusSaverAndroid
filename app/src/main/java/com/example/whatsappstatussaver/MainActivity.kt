package com.example.whatsappstatussaver

import MediaAdapter
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.whatsappstatussaver.ui.theme.WhatsappStatusSaverTheme

import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.io.FileOutputStream

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile

data class MediaItem(
    val uri: Uri,
    val displayName: String,
    val type: String  // "image" or "video"
)

class MainActivity : AppCompatActivity() {

    companion object {
        const val REQUEST_CODE_OPEN_DIRECTORY = 1001
        const val REQUEST_CODE_WRITE_STORAGE = 1002
        const val TARGET_FOLDER = "Android/media"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Check if the folder URI is already stored
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val folderUriString = prefs.getString("folder_uri", null)

        if (folderUriString != null) {
            println("URI: $folderUriString")
            val folderUri = Uri.parse(folderUriString)
            handleMediaAccess(folderUri)  // Access the previously selected folder
        } else {
            // No folder selected, prompt the user to select one
            selectFolder()
        }
    }

    private fun requestFolderAccess() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivityForResult(intent, REQUEST_CODE_OPEN_DIRECTORY)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_OPEN_DIRECTORY && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                // Persist permission
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                // List folder contents
                handleMediaAccess(uri)
            }
        }
    }

    private val folderPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                getSharedPreferences("app_prefs", MODE_PRIVATE).edit().putString("folder_uri", uri.toString()).apply()
                handleMediaAccess(uri)
            }
        } else {
            Toast.makeText(this, "Permission required to access media files.", Toast.LENGTH_SHORT).show()
        }
    }



    // Call this method to launch the folder picker
    private fun selectFolder() {
        val initialUri = Uri.parse("content://com.android.externalstorage.documents/document/primary:$TARGET_FOLDER")

        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        folderPickerLauncher.launch(intent)
    }

    private fun listFolderContents(folderUri: Uri) {
        val folder = DocumentFile.fromTreeUri(this, folderUri)
        folder?.listFiles()?.forEach { documentFile ->
            if (documentFile.isFile) {
                Log.d("FolderContents", "File: ${documentFile.name}")
            }
        }
    }

    private fun handleMediaAccess(folderUri: Uri) {
        val mediaList = mutableListOf<MediaItem>()

        // Access the predefined folder
        val folder = DocumentFile.fromTreeUri(this, folderUri)

        if (folder != null && folder.isDirectory) {
            folder.listFiles().forEach { documentFile ->
                if (documentFile.isFile) {
                    val fileName = documentFile.name ?: "Unknown"
                    val fileUri = documentFile.uri

                    when {
                        fileName.endsWith(".jpg", true) || fileName.endsWith(".png", true) -> {
                            // Add image files to mediaList
                            mediaList.add(MediaItem(fileUri, fileName, "image"))
                        }

                        fileName.endsWith(".mp4", true) || fileName.endsWith(".avi", true) -> {
                            // Add video files to mediaList
                            mediaList.add(MediaItem(fileUri, fileName, "video"))
                        }
                        // You can add more file type conditions if needed
                    }
                }
            }

            // Pass the mediaList to RecyclerView to display
            displayMedia(mediaList)
        } else {
            Toast.makeText(this, "Invalid folder or no access to folder", Toast.LENGTH_SHORT).show()
        }
    }

    private fun displayMedia(mediaList: List<MediaItem>) {
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = MediaAdapter(mediaList) { mediaItem ->
            checkPermissionsAndDownloadMedia(mediaItem)
        }
    }

    private fun checkPermissionsAndDownloadMedia(mediaItem: MediaItem) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
            // For Android 9 and below, we need WRITE_EXTERNAL_STORAGE permission
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE), REQUEST_CODE_WRITE_STORAGE)
            } else {
                // Permission granted, download the media
                downloadMedia(mediaItem)
            }
        } else {
            // For Android 10 and above (Scoped Storage), directly download
            downloadMedia(mediaItem)
        }
    }

    private fun downloadMedia(mediaItem: MediaItem) {
        try {
            // Open InputStream from the content resolver using the media item's URI
            val inputStream = contentResolver.openInputStream(mediaItem.uri)

            // Define the path to the public "Downloads" folder
            val downloadDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), mediaItem.displayName)

            // Create the file if it doesn't exist
            if (!downloadDir.exists()) {
                downloadDir.createNewFile()
            }

            inputStream?.use { input ->
                // Write the input stream to the file in the downloads folder
                FileOutputStream(downloadDir).use { output ->
                    input.copyTo(output)
                    Toast.makeText(this, "${mediaItem.displayName} downloaded to Downloads folder", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            // Handle any errors during the download process
            Toast.makeText(this, "Failed to download ${mediaItem.displayName}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    // Handle the result of permission request
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_WRITE_STORAGE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // Permission granted, proceed with download
        } else {
            // Permission denied
            Toast.makeText(this, "Permission denied. Cannot download media.", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    WhatsappStatusSaverTheme {
        Greeting("Android")
    }
}
