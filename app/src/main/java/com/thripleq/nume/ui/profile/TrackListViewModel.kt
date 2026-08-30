package com.thripleq.nume.ui.profile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thripleq.nume.core.playback.PlaybackLauncher
import com.thripleq.nume.core.repo.ProfileRepository
import com.thripleq.nume.core.repo.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Which data source a [TrackListScreen] shows. */
enum class TrackListSource(val wire: String) {
    LIKED("liked"),
    PURCHASED("purchased"),
    PLAYLIST("playlist"),
    ALBUM("album");

    companion object {
        fun from(wire: String): TrackListSource =
            entries.firstOrNull { it.wire == wire } ?: PLAYLIST
    }
}

sealed interface TrackListUiState {
    data object Loading : TrackListUiState
    data object Error : TrackListUiState
    data class Ready(val tracks: List<Track>) : TrackListUiState
}

@HiltViewModel
class TrackListViewModel @Inject constructor(
    private val repository: ProfileRepository,
    private val playback: PlaybackLauncher,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow<TrackListUiState>(TrackListUiState.Loading)
    val uiState: StateFlow<TrackListUiState> = _uiState.asStateFlow()

    private val _openPlayer = MutableSharedFlow<Unit>(replay = 0)
    val openPlayer: SharedFlow<Unit> = _openPlayer.asSharedFlow()

    private var loadedKey: String? = null

    fun load(source: TrackListSource, id: String) {
        val key = "${source.wire}:$id"
        if (loadedKey == key) return
        loadedKey = key
        viewModelScope.launch {
            _uiState.value = TrackListUiState.Loading
            val tracks = when (source) {
                TrackListSource.LIKED -> repository.likedTracks(id.toLongOrNull() ?: 0L)
                TrackListSource.PURCHASED -> repository.purchasedSongs()
                TrackListSource.PLAYLIST -> repository.playlistTracks(id)
                TrackListSource.ALBUM -> repository.albumTracks(id)
            }
            _uiState.value = if (tracks.isEmpty()) {
                TrackListUiState.Error
            } else {
                TrackListUiState.Ready(tracks)
            }
        }
    }

    fun onTrackClick(tracks: List<Track>, index: Int) {
        viewModelScope.launch {
            playback.play(context, tracks, index)
            _openPlayer.tryEmit(Unit)
        }
    }
}
