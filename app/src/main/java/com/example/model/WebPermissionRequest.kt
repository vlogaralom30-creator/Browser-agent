package com.example.model

sealed interface WebPermissionRequest {
    data class DeviceResource(
        val origin: String,
        val resources: List<String>,
        val onGrant: () -> Unit,
        val onDeny: () -> Unit
    ) : WebPermissionRequest

    data class Geolocation(
        val origin: String,
        val onGrant: () -> Unit,
        val onDeny: () -> Unit
    ) : WebPermissionRequest
}
