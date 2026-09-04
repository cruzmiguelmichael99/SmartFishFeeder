package com.example.smartfishfeeder.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartfishfeeder.data.auth.AuthRepository
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.launch

class AuthViewModel : ViewModel() {

    private val authRepository = AuthRepository()

    // Read by MainActivity to decide LoginScreen vs. MainScreen.
    var currentUser by mutableStateOf(authRepository.currentUser)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var isLoading by mutableStateOf(false)
        private set

    init {
        authRepository.addAuthStateListener { user ->
            currentUser = user
        }
    }

    fun signUp(email: String, password: String) {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            authRepository.signUpWithEmail(email, password)
                .onFailure { errorMessage = it.message ?: "Sign up failed" }
            isLoading = false
        }
    }

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            authRepository.signInWithEmail(email, password)
                .onFailure { errorMessage = it.message ?: "Sign in failed" }
            isLoading = false
        }
    }

    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            authRepository.signInWithGoogle(idToken)
                .onFailure { errorMessage = it.message ?: "Google sign-in failed" }
            isLoading = false
        }
    }

    fun signOut() {
        authRepository.signOut()
    }

    fun clearError() {
        errorMessage = null
    }
}