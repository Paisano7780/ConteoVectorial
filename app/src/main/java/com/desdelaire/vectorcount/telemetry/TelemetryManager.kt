package com.desdelaire.vectorcount.telemetry

import dji.sdk.keyvalue.key.AirLinkKey
import dji.sdk.keyvalue.key.BatteryKey
import dji.sdk.keyvalue.key.FlightControllerKey
import dji.sdk.keyvalue.key.KeyTools
import dji.sdk.keyvalue.value.common.Attitude
import dji.sdk.keyvalue.value.common.LocationCoordinate3D
import dji.sdk.keyvalue.value.flightcontroller.FlightMode
import dji.v5.manager.KeyManager

/**
 * Suscribe la UI a telemetría de solo lectura del MSDK V5 vía KeyManager.
 *
 * No envía comandos al hardware: únicamente escucha (listen) los valores y los
 * reenvía a la capa de UI. Todas las suscripciones se cancelan en [stop].
 */
class TelemetryManager {

    private val listenHolder = Any()

    fun start(callbacks: Callbacks) {
        val keyManager = KeyManager.getInstance()

        keyManager.listen(
            KeyTools.createKey(BatteryKey.KeyChargeRemainingInPercent),
            listenHolder
        ) { _, newValue -> newValue?.let { callbacks.onBatteryPercent(it) } }

        keyManager.listen(
            KeyTools.createKey(FlightControllerKey.KeyFlightMode),
            listenHolder
        ) { _, newValue -> callbacks.onFlightMode(newValue) }

        keyManager.listen(
            KeyTools.createKey(FlightControllerKey.KeyGPSSatelliteCount),
            listenHolder
        ) { _, newValue -> newValue?.let { callbacks.onSatelliteCount(it) } }

        keyManager.listen(
            KeyTools.createKey(AirLinkKey.KeySignalQuality),
            listenHolder
        ) { _, newValue -> newValue?.let { callbacks.onRcSignalQuality(it) } }

        keyManager.listen(
            KeyTools.createKey(FlightControllerKey.KeyAircraftLocation3D),
            listenHolder
        ) { _, newValue -> callbacks.onAircraftLocation(newValue) }

        keyManager.listen(
            KeyTools.createKey(FlightControllerKey.KeyAircraftAttitude),
            listenHolder
        ) { _, newValue -> callbacks.onAircraftAttitude(newValue) }
    }

    fun stop() {
        KeyManager.getInstance().cancelListen(listenHolder)
    }

    interface Callbacks {
        fun onBatteryPercent(percent: Int)
        fun onFlightMode(flightMode: FlightMode?)
        fun onSatelliteCount(count: Int)
        fun onRcSignalQuality(quality: Int)
        fun onAircraftLocation(location: LocationCoordinate3D?)
        fun onAircraftAttitude(attitude: Attitude?)
    }
}
