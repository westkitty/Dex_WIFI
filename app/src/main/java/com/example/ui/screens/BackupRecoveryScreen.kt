package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.WifiViewModel

@Composable
fun BackupRecoveryScreen(viewModel: WifiViewModel) {
    val status by viewModel.projectExportStatus.collectAsState()
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> if (uri != null) viewModel.exportProject(uri) }
    Column(
        Modifier.fillMaxSize().padding(16.dp).testTag("data_safety_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("DATA SAFETY", style = MaterialTheme.typography.titleLarge)
        Text("Save a private project backup before changing app versions. The archive includes all saved Wi-Fi names, measurements, apartment geometry and imported photos. Anyone with the ZIP can read it. Store it securely.")
        Text("This is a manual, unencrypted export. A verified export is not proof that a separate app installation can restore it. Import and migration remain disabled until recovery validation is complete.")
        Button(
            onClick = { picker.launch("Dex_WIFI_project_backup.zip") },
            modifier = Modifier.testTag("export_project_button")
        ) { Text("Export project ZIP") }
        Text(status, modifier = Modifier.testTag("export_verdict"))
    }
}
