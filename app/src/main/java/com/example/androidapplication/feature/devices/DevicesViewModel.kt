package com.example.androidapplication.feature.devices

import androidx.compose.material.icons.filled.Refresh
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidapplication.core.common.AppError
import com.example.androidapplication.core.common.Mac
import com.example.androidapplication.core.common.UiMessageBus
import com.example.androidapplication.core.common.Validators
import com.example.androidapplication.core.data.DeviceRepository
import com.example.androidapplication.core.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DevicesViewModel @Inject constructor(
    private val repository: DeviceRepository,
    private val bus: UiMessageBus,
    settings: SettingsRepository
) : ViewModel() {
    val cameras = repository.cameras
    val general = settings.general
    val canUpdate get() = repository.canUpdate

    private val _registering = MutableStateFlow(false)
    val registering = _registering.asStateFlow()
    private val _registerError = MutableStateFlow<String?>(null)
    val registerError = _registerError.asStateFlow()

    suspend fun poll(first: Boolean) = try {
        repository.refresh(silent = !first)
    } catch (e: AppError) {
        bus.showError(e, "카메라 목록 조회에 실패했습니다.")
    }

    fun refresh() {
        viewModelScope.launch { poll(first = true) }
    }

    fun clearRegisterError() {
        _registerError.value = null
    }

    /** 실패하면 시트를 닫지 않고 안에 오류를 표시한다. 성공 시 목록은 저장소가 바로 다시 받는다. */
    fun register(mac: String, name: String, location: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _registering.value = true
            _registerError.value = null
            try {
                repository.register(mac, name.trim(), location.trim().ifEmpty { Validators.LOCATION_DEFAULT })
                bus.show("카메라가 등록되었습니다.")
                onSuccess()
            } catch (e: AppError) {
                _registerError.value = e.message
            } finally {
                _registering.value = false
            }
        }
    }

    fun rename(mac: String, name: String) {
        viewModelScope.launch {
            try {
                repository.update(mac, name = name.trim())
                bus.show("이름이 변경되었습니다.")
            } catch (e: AppError) {
                bus.showError(e, "카메라 정보 수정에 실패했습니다.")
            }
        }
    }
}
