package com.example.ui

import android.app.Application
import android.net.Uri
import com.example.data.FloorPlanStore
import com.example.model.FloorPlanDocument
import kotlinx.coroutines.withContext
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class WifiViewModel(application: Application) : AndroidViewModel(application) {

    private val planStore = FloorPlanStore(application)
    private val savedCustomPlan = planStore.load()
    private val _customPlan = MutableStateFlow<FloorPlanDocument?>(savedCustomPlan)
    val customPlan: StateFlow<FloorPlanDocument?> = _customPlan.asStateFlow()

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

    private val _currentLayout = MutableStateFlow(savedCustomPlan?.toLayout() ?: DefaultApartments.twoBedroomApartment)
    val currentLayout: StateFlow<ApartmentLayout> = _currentLayout.asStateFlow()

    private val _routerPosition = MutableStateFlow(savedCustomPlan?.router ?: DefaultApartments.twoBedroomApartment.defaultRouterPosition)
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
            _currentLayout.collectLatest { layout ->
                _dbSurveyPoints.value = emptyList()
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

        // No silent fake survey data: demonstrations require explicit user action.
    }

    fun selectBand(band: WifiBand) {
        _selectedBand.value = band
    }

    fun selectLayout(layout: ApartmentLayout) {
        _currentLayout.value = layout
        _routerPosition.value = layout.defaultRouterPosition
    }

    fun setRouterPosition(point: NormalizedPoint) {
        val safePoint = com.example.model.PlanGeometry.clamp(point)
        _routerPosition.value = safePoint
        if (_currentLayout.value.id == FloorPlanDocument.LAYOUT_ID) {
            _customPlan.value?.let { old ->
                val next = old.copy(router = safePoint, updatedAt = System.currentTimeMillis())
                if (planStore.save(next)) _customPlan.value = next
            }
        }
    }

    fun openCustomPlan() {
        _customPlan.value?.let { selectLayout(it.toLayout()) }
    }

    fun saveCustomPlan(plan: FloorPlanDocument): Boolean {
        val validated = plan.copy(
            name = plan.name.trim().take(70).ifBlank { "My apartment" },
            updatedAt = System.currentTimeMillis()
        )
        if (!planStore.save(validated)) return false
        _customPlan.value = validated
        selectLayout(validated.toLayout())
        return true
    }

    fun importFloorPlanPhoto(uri: Uri, callback: (Pair<String, Float>?, String?) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { planStore.importPhoto(uri) } }
            callback(result.getOrNull(), result.exceptionOrNull()?.message)
        }
    }

    fun floorPlanPhotoFile(name: String?) = planStore.photoFile(name)

    /** Measurement cannot silently fall back to a demo signal or fabricated RSSI. */
    fun canRecordRealSurveyPoint(): Boolean {
        val network = connectedNetwork.value
        return !isSimulatedMode.value && hasLocationPermission.value &&
            network?.isConnected == true && network.ssid.isNotBlank() &&
            network.rssi in -100..-1 && network.frequency > 0
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
        if (!canRecordRealSurveyPoint()) return
        val realNetwork = connectedNetwork.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val ssid = realNetwork.ssid
            val rssi = realNetwork.rssi
            val freq = realNetwork.frequency
            val channel = realNetwork.channel

            val entity = SurveyEntity(
                layoutId = _currentLayout.value.id,
                posX = position.x.coerceIn(0f, 1f),
                posY = position.y.coerceIn(0f, 1f),
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
        if (_currentLayout.value.id == FloorPlanDocument.LAYOUT_ID) return
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
