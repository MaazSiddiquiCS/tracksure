package com.tracksure.android.identity

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AuthSessionManager(
    private val tokenStore: AuthTokenStore
) {
    sealed class Status {
        data object Loading : Status()
        data object Unauthenticated : Status()
        data class Authenticated(val session: AuthTokenStore.Session) : Status()
    }

    private val _status = MutableStateFlow<Status>(Status.Loading)
    val status: StateFlow<Status> = _status.asStateFlow()

    init {
        restore()
    }

    fun restore() {
        val session = tokenStore.load()
        _status.value = if (session == null) Status.Unauthenticated else Status.Authenticated(session)
    }

    fun setAuthenticated(session: AuthTokenStore.Session) {
        tokenStore.save(session)
        _status.value = Status.Authenticated(session)
    }

    fun clear() {
        tokenStore.clear()
        _status.value = Status.Unauthenticated
    }
}
