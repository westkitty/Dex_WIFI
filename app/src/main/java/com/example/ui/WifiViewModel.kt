package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.DefaultApartments
import com.example.data.SurveyEntity
import com.example.model.ApartmentLayout
import com.example.model.ChannelAnalysis
import com.example.model.ChannelSpectrumCurve
import com.example.model.HeatmapCell
import com.example.model.NormalizedPoint
import com.example.model.SurveyPoint
import com.example.model.WifiBand
import com.example.model.WifiNetwork
import com.example.model.WifiOptimizationSuggestion
import com.example.service.HeatmapInterpolator
import com.example.service.WifiAnalyzerEngine
import com.example.service.WifiScannerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class WifiViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val surveyDao = database.surveyDao()

    val scannerService = WifiScannerService(application, viewModelScope)

    val networks: StateFlow<List<WifiNetwork>> = scannerService.networks
    val connectedNetwork: StateFlow<WifiNetwork?> = scannerService.connectedNetwork
    val isScanning: StateFlow<Boolean> = scannerService.isScanning
    val isSimulatedMode: StateFlow<Boolean> = scannerService.isSimulatedMode
    val hasLocationPermission: StateFlow<Boolean> = scannerService.hasLocationPermission

    private val _selectedBand = MutableStateFlow(WifiBand.BAND_2_4GHZ)
    val selectedBand: StateFlow<WifiBand> = _selectedBand.asStateFlow()

    private val _currentLayout = MutableStateFlow(DefaultApartments.twoBedroomApartment)
    val currentLayout: StateFlow<ApartmentLayout> = _currentLayout.asStateFlow()

    private val _routerPosition = MutableStateFlow(DefaultApartments.twoBedroomApartment.defaultRouterPosition)
    val routerPosition: StateFlow<NormalizedPoint> = _routerPosition.asStateFlow()

    // Real-time signal fluctuation history for the live RSSI waveform
    private val _signalHistory = MutableStateFlow<List<Int>>(List(30) { -50 })
    val signalHistory: StateFlow<List<Int>> = _signalHistory.asStateFlow()

    // DB survey points for current layout
    private val _dbSurveyPoints = MutableStateFlow<List<SurveyPoint>>(emptyList())
    val surveyPoints: StateFlow<List<SurveyPoint>> = _dbSurveyPoints.asStateFlow()

    // Channel Analysis
    val channelAnalyses: StateFlow<List<ChannelAnalysis>> = combine(
        networks,
        _selectedBand
    ) { nets, band ->
        WifiAnalyzerEngine.analyzeChannels(nets, band)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Spectrum Curves
    val spectrumCurves: StateFlow<List<ChannelSpectrumCurve>> = combine(
        networks,
        _selectedBand
    ) { nets, band ->
        WifiAnalyzerEngine.generateSpectrumCurves(nets, band)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Suggestions
    val optimizationSuggestions: StateFlow<List<WifiOptimizationSuggestion>> = combine(
        networks,
        connectedNetwork,
        surveyPoints,
        currentLayout
    ) { nets, conn, surveys, layout ->
        WifiAnalyzerEngine.generateOptimizationSuggestions(nets, conn, surveys, layout)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Heatmap Grid
    val heatmapGrid: StateFlow<List<HeatmapCell>> = combine(
        currentLayout,
        surveyPoints,
        routerPosition
    ) { layout, surveys, router ->
        HeatmapInterpolator.computeHeatmapGrid(layout, surveys, router)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Observe survey points for selected layout
        viewModelScope.launch {
            _currentLayout.collect { layout ->
                surveyDao.getPointsForLayout(layout.id).collect { entities ->
                    _dbSurveyPoints.value = entities.map { it.toDomain() }
                }
            }
        }

        // Signal history ticker
        viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                val currentRssi = connectedNetwork.value?.rssi ?: networks.value.firstOrNull()?.rssi ?: -70
                _signalHistory.value = (_signalHistory.value.drop(1) + currentRssi)
                delay(1000)
            }
        }

        // Preload sample survey points if database is empty on first launch
        viewModelScope.launch {
            delay(500)
            if (_dbSurveyPoints.value.isEmpty()) {
                loadSampleSurveyPoints()
            }
        }
    }

    fun selectBand(band: WifiBand) {
        _selectedBand.value = band
    }

    fun selectLayout(layout: ApartmentLayout) {
        _currentLayout.value = layout
        _routerPosition.value = layout.defaultRouterPosition
    }

    fun setRouterPosition(point: NormalizedPoint) {
        _routerPosition.value = point
    }

    fun triggerScan() {
        scannerService.triggerScan()
    }

    fun toggleSimulatedMode(enabled: Boolean) {
        scannerService.setSimulatedMode(enabled)
    }

    fun checkPermissions() {
        scannerService.checkPermissions()
    }

    fun addSurveyPoint(position: NormalizedPoint, roomName: String, notes: String = "") {
        viewModelScope.launch(Dispatchers.IO) {
            val primaryNet = connectedNetwork.value ?: networks.value.firstOrNull()
            val ssid = primaryNet?.ssid ?: "My Network"
            val rssi = primaryNet?.rssi ?: -65
            val freq = primaryNet?.frequency ?: 5240
            val channel = primaryNet?.channel ?: 48

            val entity = SurveyEntity(
                layoutId = _currentLayout.value.id,
                posX = position.x,
                posY = position.y,
                roomName = roomName,
                primarySsid = ssid,
                rssi = rssi,
                frequency = freq,
                channel = channel,
                timestamp = System.currentTimeMillis(),
                notes = notes
            )
            surveyDao.insertPoint(entity)
        }
    }

    fun deleteSurveyPoint(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            surveyDao.deletePoint(id)
        }
    }

    fun clearAllSurveyPoints() {
        viewModelScope.launch(Dispatchers.IO) {
            surveyDao.clearLayoutPoints(_currentLayout.value.id)
        }
    }

    fun loadSampleSurveyPoints() {
        viewModelScope.launch(Dispatchers.IO) {
            val samples = DefaultApartments.createSampleSurveyPoints(
                _currentLayout.value.id,
                connectedNetwork.value?.ssid ?: "Apt304_Home_5G"
            )
            samples.forEach { pt ->
                surveyDao.insertPoint(SurveyEntity.fromDomain(pt))
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        scannerService.cleanup()
    }
}
