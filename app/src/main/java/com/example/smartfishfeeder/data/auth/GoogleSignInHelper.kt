package com.example.smartfishfeeder.data.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import java.security.MessageDigest
import java.util.UUID

/**
 * Wraps Android's Credential Manager for "Sign in with Google". This is
 * the replacement for the old GoogleSignInClient/GoogleSignInOptions API,
 * which Google has deprecated and is removing from Play Services.
 */
class GoogleSignInHelper(private val context: Context) {

    private val credentialManager = CredentialManager.create(context)

    /**
     * Shows the Credential Manager bottom sheet and returns the signed-in
     * user's Google ID token, or null if the user cancelled or something
     * went wrong.
     */
    suspend fun requestGoogleIdToken(webClientId: String): String? {
        val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(webClientId)
            .setNonce(generateNonce())
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInWithGoogleOption)
            .build()

        return try {
            val result = credentialManager.getCredential(context, request)
            val credential = result.credential

            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                GoogleIdTokenCredential.createFrom(credential.data).idToken
            } else {
                null
            }
        } catch (e: GetCredentialException) {
            // User dismissed the bottom sheet, no Google account on the
            // device, etc. Nothing actionable to surface here.
            null
        } catch (e: GoogleIdTokenParsingException) {
            null
        }
    }

    /** A per-request random value Google echoes back in the ID token, to guard against replay. */
    private fun generateNonce(): String {
        val rawNonce = UUID.randomUUID().toString()
        val digest = MessageDigest.getInstance("SHA-256").digest(rawNonce.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}