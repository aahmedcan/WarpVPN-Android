package com.example.warp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.warp.data.Setting
import com.example.warp.data.SettingDao
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WarpViewModel(private val settingDao: SettingDao) : ViewModel() {
    val autoConnect: StateFlow<Boolean> = settingDao.getSetting("auto_connect")
        .map { it ?: false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setAutoConnect(enabled: Boolean) {
        viewModelScope.launch {
            settingDao.insertSetting(Setting("auto_connect", enabled))
        }
    }
}

class WarpViewModelFactory(private val settingDao: SettingDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WarpViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return WarpViewModel(settingDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
