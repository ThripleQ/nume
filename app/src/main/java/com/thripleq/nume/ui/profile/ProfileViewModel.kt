package com.thripleq.nume.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thripleq.nume.core.net.NetEaseGateway
import com.thripleq.nume.core.repo.Account
import com.thripleq.nume.core.repo.Album
import com.thripleq.nume.core.repo.PlaylistSummary
import com.thripleq.nume.core.repo.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Everything the Profile tab shows once logged in. */
data class ProfileData(
    val account: Account,
    val likedCount: Int,
    val purchasedSongCount: Int,
    val purchasedAlbums: List<Album>,
    val subscribedPlaylists: List<PlaylistSummary>,
    val createdPlaylists: List<PlaylistSummary>,
)

sealed interface ProfileUiState {
    data object Loading : ProfileUiState
    data object LoggedOut : ProfileUiState
    data class LoggedIn(val data: ProfileData) : ProfileUiState
    data class Error(val message: String) : ProfileUiState
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: ProfileRepository,
    private val gateway: NetEaseGateway,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    init {
        refresh()
    }

    /** Re-checks login state and (when logged in) reloads every block. */
    fun refresh() {
        viewModelScope.launch {
            _busy.value = true
            _uiState.value = ProfileUiState.Loading
            val account = repository.account()
            _busy.value = false
            if (account == null) {
                _uiState.value = ProfileUiState.LoggedOut
                return@launch
            }
            loadProfile(account)
        }
    }

    private suspend fun loadProfile(account: Account) = coroutineScope {
        _busy.value = true
        val liked = async { repository.likedTracks(account.uid) }
        val purchasedSongs = async { repository.purchasedSongs() }
        val purchasedAlbums = async { repository.purchasedAlbums() }
        val playlists = async { repository.playlists(account.uid) }
        val (subscribedPlaylists, createdPlaylists) = playlists.await()
        val data = ProfileData(
            account = account,
            likedCount = liked.await().size,
            purchasedSongCount = purchasedSongs.await().size,
            purchasedAlbums = purchasedAlbums.await(),
            subscribedPlaylists = subscribedPlaylists,
            createdPlaylists = createdPlaylists,
        )
        _busy.value = false
        _uiState.value = ProfileUiState.LoggedIn(data)
    }

    /** Completes login from an in-app WebView session (official login page). */
    fun webLoginCookies(cookieStr: String, onDone: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            gateway.importCookies(cookieStr)
            val account = repository.account()
            _busy.value = false
            if (account == null) {
                onDone(false, "登录态无效，请重试")
                _uiState.value = ProfileUiState.LoggedOut
            } else {
                onDone(true, "")
                loadProfile(account)
            }
        }
    }
}
