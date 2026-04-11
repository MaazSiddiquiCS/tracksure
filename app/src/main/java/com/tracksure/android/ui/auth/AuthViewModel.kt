package com.tracksure.android.ui.auth

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tracksure.android.identity.AuthSessionManager
import com.tracksure.android.identity.AuthTokenStore
import com.tracksure.android.identity.DeviceLinkManager
import com.tracksure.android.net.AuthApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(
    private val apiClient: AuthApiClient,
    private val authSessionManager: AuthSessionManager,
    private val deviceLinkManager: DeviceLinkManager
) : ViewModel() {
    companion object {
        private const val TAG = "AuthViewModel"
    }


    data class UiState(
        val isLoading: Boolean = false,
        val error: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val authStatus: StateFlow<AuthSessionManager.Status> = authSessionManager.status

    fun login(username: String, password: String) {
        viewModelScope.launch {
            Log.i(TAG, "Login requested username=$username")
            _uiState.value = UiState(isLoading = true)
            when (val result = apiClient.login(AuthApiClient.LoginRequest(username, password))) {
                is AuthApiClient.Result.Success -> {
                    val session = toSession(result.value)
                    authSessionManager.setAuthenticated(session)
                    Log.i(TAG, "Login success userId=${session.userId}; auth session stored")
                    val linkFailure = ensureLinkedWithResult(session.accessToken)
                    _uiState.value = UiState(isLoading = false)
                    if (linkFailure != null) {
                        _uiState.value = UiState(
                            isLoading = false,
                            error = "Signed in. Device link pending: ${linkFailure.message}"
                        )
                    }
                }
                is AuthApiClient.Result.Error -> {
                    Log.w(TAG, "Login failed code=${result.code} retryable=${result.retryable} msg=${result.message}")
                    _uiState.value = UiState(isLoading = false, error = result.message)
                }
            }
        }
    }

    fun signup(username: String, email: String, password: String, confirmPassword: String) {
        viewModelScope.launch {
            Log.i(TAG, "Signup requested username=$username email=$email")
            _uiState.value = UiState(isLoading = true)
            when (val result = apiClient.signup(AuthApiClient.SignupRequest(username, email, password, confirmPassword))) {
                is AuthApiClient.Result.Success -> {
                    val session = toSession(result.value)
                    authSessionManager.setAuthenticated(session)
                    Log.i(TAG, "Signup success userId=${session.userId}; auth session stored")
                    val linkFailure = ensureLinkedWithResult(session.accessToken)
                    _uiState.value = UiState(isLoading = false)
                    if (linkFailure != null) {
                        _uiState.value = UiState(
                            isLoading = false,
                            error = "Signed in. Device link pending: ${linkFailure.message}"
                        )
                    }
                }
                is AuthApiClient.Result.Error -> {
                    Log.w(TAG, "Signup failed code=${result.code} retryable=${result.retryable} msg=${result.message}")
                    _uiState.value = UiState(isLoading = false, error = result.message)
                }
            }
        }
    }

    fun ensureLinkedForCurrentSession() {
        val current = authStatus.value
        if (current !is AuthSessionManager.Status.Authenticated) return

        viewModelScope.launch {
            val linkFailure = ensureLinkedWithResult(current.session.accessToken)
            if (linkFailure != null) {
                _uiState.value = UiState(
                    isLoading = false,
                    error = "Device link pending: ${linkFailure.message}"
                )
            }
        }
    }

    fun refreshSessionIfNeeded() {
        val current = authStatus.value
        if (current !is AuthSessionManager.Status.Authenticated) return

        viewModelScope.launch {
            when (val result = apiClient.refresh(current.session.refreshToken)) {
                is AuthApiClient.Result.Success -> {
                    Log.i(TAG, "Session refresh success userId=${result.value.userId}")
                    authSessionManager.setAuthenticated(
                        toSession(result.value)
                    )
                }
                is AuthApiClient.Result.Error -> {
                    Log.w(TAG, "Session refresh failed code=${result.code} msg=${result.message}; clearing auth")
                    authSessionManager.clear()
                }
            }
        }
    }

    fun logout() {
        val current = authStatus.value
        if (current is AuthSessionManager.Status.Authenticated) {
            viewModelScope.launch {
                Log.i(TAG, "Logout requested userId=${current.session.userId}")
                apiClient.logout(current.session.refreshToken)
                authSessionManager.clear()
                _uiState.value = UiState()
            }
        } else {
            Log.i(TAG, "Logout requested while already unauthenticated")
            authSessionManager.clear()
            _uiState.value = UiState()
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private suspend fun ensureLinkedWithResult(accessToken: String): DeviceLinkManager.LinkStatus.Failed? {
        return when (val link = deviceLinkManager.ensureLinked(accessToken)) {
            is DeviceLinkManager.LinkStatus.Linked -> {
                Log.i(
                    TAG,
                    "Device link success backendDeviceId=${link.identity.backendDeviceId} meshPeerId=${link.identity.meshPeerId}"
                )
                null
            }

            is DeviceLinkManager.LinkStatus.Failed -> {
                Log.w(TAG, "Device link failed retryable=${link.retryable} msg=${link.message}")
                link
            }
        }
    }

    private fun toSession(response: AuthApiClient.LoginResponse): AuthTokenStore.Session {
        return AuthTokenStore.Session(
            accessToken = response.accessToken,
            refreshToken = response.refreshToken,
            userId = response.userId,
            username = response.username,
            email = response.email
        )
    }
}
