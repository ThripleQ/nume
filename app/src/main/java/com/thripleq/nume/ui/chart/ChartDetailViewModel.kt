package com.thripleq.nume.ui.chart

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thripleq.nume.core.playback.PlaybackLauncher
import com.thripleq.nume.core.repo.ChartRepository
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

sealed interface ChartDetailUiState {
    data object Loading : ChartDetailUiState
    data object Error : ChartDetailUiState
    data class Ready(val tracks: List<Track>) : ChartDetailUiState
}

@HiltViewModel
class ChartDetailViewModel @Inject constructor(
    private val repository: ChartRepository,
    private val playback: PlaybackLauncher,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ChartDetailUiState>(ChartDetailUiState.Loading)
    val uiState: StateFlow<ChartDetailUiState> = _uiState.asStateFlow()

    private val _openPlayer = MutableSharedFlow<Unit>(replay = 0)
    val openPlayer: SharedFlow<Unit> = _openPlayer.asSharedFlow()

    private var loadedId: String? = null

    fun load(chartId: String) {
        if (loadedId == chartId) return
        loadedId = chartId
        viewModelScope.launch {
            _uiState.value = ChartDetailUiState.Loading
            val tracks = repository.chartTracks(chartId)
            _uiState.value = if (tracks.isEmpty()) {
                ChartDetailUiState.Error
            } else {
                ChartDetailUiState.Ready(tracks)
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