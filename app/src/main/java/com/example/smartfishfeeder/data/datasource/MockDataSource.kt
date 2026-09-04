package com.example.smartfishfeeder.data.datasource

import com.example.smartfishfeeder.data.model.AIRecommendation
import com.example.smartfishfeeder.data.model.DeviceStatus
import com.example.smartfishfeeder.data.model.FeedingEvent
import com.example.smartfishfeeder.data.model.FeedingSchedule
import com.example.smartfishfeeder.data.model.TemperatureReading
import com.example.smartfishfeeder.data.model.WeatherData

object MockDataSource {

    val deviceStatus = DeviceStatus(
        deviceId = "FEEDER001",
        deviceName = "Smart Fish Feeder",
        isOnline = true,
        wifiConnected = true,
        temperatureSensorNormal = true,
        feederReady = true,
        lastCommunicationTime = "Just now"
    )

    val temperatureReading = TemperatureReading(
        temperature = 28.5,
        timestamp = "06:00 PM"
    )

    val weatherData = WeatherData(
        temperature = 30.0,
        humidity = 78,
        weatherCondition = "Partly Cloudy",
        precipitation = "Low rain probability",
        timestamp = "06:00 PM"
    )

    val feedingSchedules = listOf(
        FeedingSchedule(
            id = "SCHEDULE001",
            feedingTime = "06:00 AM",
            enabled = true,
            feedingType = "Automatic"
        ),
        FeedingSchedule(
            id = "SCHEDULE002",
            feedingTime = "12:00 PM",
            enabled = true,
            feedingType = "Automatic"
        ),
        FeedingSchedule(
            id = "SCHEDULE003",
            feedingTime = "06:00 PM",
            enabled = true,
            feedingType = "Automatic"
        )
    )

    val feedingHistory = listOf(
        FeedingEvent(
            id = "EVENT001",
            dateTime = "06:00 AM",
            automatic = true,
            status = "Completed"
        ),
        FeedingEvent(
            id = "EVENT002",
            dateTime = "12:00 PM",
            automatic = false,
            status = "Completed"
        )
    )

    val aiRecommendation = AIRecommendation(
        recommendation = "Feed normally.",
        reason = "Water temperature is within the suitable range and current weather conditions are favorable.",
        waterTemperature = 28.5,
        confidence = "High",
        weatherCondition = "Partly Cloudy",
        timestamp = "06:00 PM"
    )
}