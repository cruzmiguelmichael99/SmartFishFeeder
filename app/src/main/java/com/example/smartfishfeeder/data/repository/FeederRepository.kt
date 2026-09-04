package com.example.smartfishfeeder.data.repository

import com.example.smartfishfeeder.data.datasource.MockDataSource
import com.example.smartfishfeeder.data.model.DeviceStatus
import com.example.smartfishfeeder.data.model.TemperatureReading

class FeederRepository {

    fun getDeviceStatus(): DeviceStatus {
        return MockDataSource.deviceStatus
    }

    fun getTemperatureReading(): TemperatureReading {
        return MockDataSource.temperatureReading
    }
}