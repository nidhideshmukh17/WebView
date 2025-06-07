package com.example.puratechsolutions

import androidx.lifecycle.ViewModel

class WebViewViewModel : ViewModel() {
    fun handlePermissionsResult(permissions: Map<String, Boolean>) {

        permissions.forEach { (permission, granted) -> }
    }
}