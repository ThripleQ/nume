package com.thripleq.nume.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thripleq.nume.core.playback.PlaybackLauncher
import com.thripleq.nume.core.repo.Chart
import com.thripleq.nume.core.repo.ChartRepository
import com.thripleq.nume.core.repo.HomeRepository
import com.thripleq.nume.core.repo.PlaylistCard
import com.thripleq.nume.core.repo.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data object Error : HomeUiState
    data class Ready(
        val loggedIn: Boolean,
        val playlists: List<PlaylistCard>,
        val charts: List<Chart>,
        val dailySongs: List<Track>,
        val recentSongs: List<Track>,
    ) : HomeUiState
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val homeRepo: HomeRepository,
    private val chartRepo: ChartRepository,
    private val playback: PlaybackLauncher,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _openPlayer = MutableSharedFlow<Unit>(replay = 0)
    val openPlayer: SharedFlow<Unit> = _openPlayer.asSharedFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            val loggedIn = homeRepo.loggedIn()
            val playlists = async { homeRepo.recommendPlaylists() }
            val charts = async { chartRepo.charts() }
            val daily = async { if (loggedIn) homeRepo.dailySongs() else emptyList() }
            val recent = async { if (loggedIn) homeRepo.recentSongs() else emptyList() }
            val pl = playlists.await()
            val ch = charts.await()
            val da = daily.await()
            val re = recent.await()
            _uiState.value = if (pl.isEmpty() && ch.isEmpty() && da.isEmpty() && re.isEmpty()) {
                HomeUiState.Error
            } else {
                HomeUiState.Ready(loggedIn, pl, ch, da, re)
            }
        }
    }

    /** 小封面单曲行：点了直接播 + 弹出播放页（无展开动效）。 */
    fun onPlayTrack(tracks: List<Track>, index: Int) {
        viewModelScope.launch {
            playback.play(context, tracks, index)
            _openPlayer.tryEmit(Unit)
        }
    }
}
