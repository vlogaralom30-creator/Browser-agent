package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.webkit.PermissionRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.model.WebPermissionRequest

@Composable
fun WebPermissionDialog(
    request: WebPermissionRequest,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    when (request) {
        is WebPermissionRequest.DeviceResource -> {
            val needsCamera = request.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
            val needsAudio = request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)

            val androidPermissions = mutableListOf<String>().apply {
                if (needsCamera) add(Manifest.permission.CAMERA)
                if (needsAudio) add(Manifest.permission.RECORD_AUDIO)
            }

            val osLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) { result ->
                val allGranted = result.values.all { it }
                if (allGranted) {
                    request.onGrant()
                } else {
                    request.onDeny()
                }
            }

            AlertDialog(
                onDismissRequest = {
                    request.onDeny()
                    onDismiss()
                },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Security",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                },
                title = {
                    Text(
                        text = "Website Permission Request",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "${request.origin} would like to use:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        if (needsCamera) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = "Camera",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Your Camera", style = MaterialTheme.typography.bodyMedium)
                            }
                        }

                        if (needsAudio) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Microphone",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Your Microphone", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val missing = androidPermissions.filter {
                                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                            }
                            if (missing.isNotEmpty()) {
                                osLauncher.launch(missing.toTypedArray())
                            } else {
                                request.onGrant()
                            }
                        },
                        modifier = Modifier.testTag("perm_allow_button")
                    ) {
                        Text("Allow")
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = {
                            request.onDeny()
                            onDismiss()
                        },
                        modifier = Modifier.testTag("perm_deny_button")
                    ) {
                        Text("Block")
                    }
                }
            )
        }

        is WebPermissionRequest.Geolocation -> {
            val osLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) { result ->
                val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                        result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
                if (granted) {
                    request.onGrant()
                } else {
                    request.onDeny()
                }
            }

            AlertDialog(
                onDismissRequest = {
                    request.onDeny()
                    onDismiss()
                },
                icon = {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Location",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                },
                title = {
                    Text(
                        text = "Location Permission Request",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "${request.origin} wants to know your device location.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val fineGranted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED
                            if (fineGranted) {
                                request.onGrant()
                            } else {
                                osLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }
                        },
                        modifier = Modifier.testTag("perm_allow_location")
                    ) {
                        Text("Allow")
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = {
                            request.onDeny()
                            onDismiss()
                        },
                        modifier = Modifier.testTag("perm_deny_location")
                    ) {
                        Text("Block")
                    }
                }
            )
        }
    }
}
