package sk.matej.spajza.ui.signin

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import sk.matej.spajza.R

sealed interface GoogleSignInResult {
    data class Token(val idToken: String) : GoogleSignInResult

    data object Cancelled : GoogleSignInResult

    data object NoAccount : GoogleSignInResult

    data class Failed(val error: Throwable) : GoogleSignInResult
}

/** Asks Credential Manager for a Google ID token; needs an activity context for the account picker. */
suspend fun requestGoogleIdToken(activityContext: Context): GoogleSignInResult {
    val option = GetSignInWithGoogleOption.Builder(activityContext.getString(R.string.default_web_client_id)).build()
    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
    return try {
        val credential = CredentialManager.create(activityContext).getCredential(activityContext, request).credential
        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            GoogleSignInResult.Token(GoogleIdTokenCredential.createFrom(credential.data).idToken)
        } else {
            GoogleSignInResult.Failed(IllegalStateException("Unexpected credential type ${credential.type}"))
        }
    } catch (e: GetCredentialCancellationException) {
        GoogleSignInResult.Cancelled
    } catch (e: NoCredentialException) {
        GoogleSignInResult.NoAccount
    } catch (e: Exception) {
        GoogleSignInResult.Failed(e)
    }
}
