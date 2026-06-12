package com.desdelaire.vectorcount.telemetry

import dji.sdk.keyvalue.key.AirLinkKey
import dji.sdk.keyvalue.key.BatteryKey
import dji.sdk.keyvalue.key.FlightControllerKey
import dji.sdk.keyvalue.key.KeyTools
import dji.sdk.keyvalue.value.common.Attitude
import dji.sdk.keyvalue.value.common.LocationCoordinate2D
import dji.sdk.keyvalue.value.common.LocationCoordinate3D
import dji.sdk.keyvalue.value.flightcontroller.RemoteControllerFlightMode
import dji.v5.manager.KeyManager

/**
 * Suscribe la UI a telemetría de solo lectura del MSDK V5 vía KeyManager.
 *
 * No envía comandos al hardware: únicamente escucha (listen) los valores y los
 * reenvía a la capa de UI. Cada listener se registra con el flag de valor
 * inicial activado para que la UI reciba el valor cacheado actual de inmediato
 * (evita la "telemetría dormida" hasta el primer cambio). Cada lectura es
 * independiente: ninguna depende de que se haya fijado el Home Point.
 * Todas las suscripciones se cancelan en [stop].
 */
class TelemetryManager {

    private val listenHolder = Any()

    fun start(callbacks: Callbacks) {
        val keyManager = KeyManager.getInstance()

        keyManager.listen(
            KeyTools.createKey(BatteryKey.KeyChargeRemainingInPercent),
            listenHolder, true
        ) { _, newValue -> newValue?.let { callbacks.onBatteryPercent(it) } }

        // Estado de vuelo (texto): p. ej. "GPS", "Atti", "AutoLanding".
        keyManager.listen(
            KeyTools.createKey(FlightControllerKey.KeyFlightModeString),
            listenHolder, true
        ) { _, newValue -> callbacks.onFlightModeString(newValue) }

        // Modo del switch del control remoto (N/S/C).
        keyManager.listen(
            KeyTools.createKey(FlightControllerKey.KeyRemoteControllerFlightMode),
            listenHolder, true
        ) { _, newValue -> callbacks.onRcFlightMode(newValue) }

        keyManager.listen(
            KeyTools.createKey(FlightControllerKey.KeyGPSSatelliteCount),
            listenHolder, true
        ) { _, newValue -> newValue?.let { callbacks.onSatelliteCount(it) } }

        keyManager.listen(
            KeyTools.createKey(AirLinkKey.KeySignalQuality),
            listenHolder, true
        ) { _, newValue -> newValue?.let { callbacks.onRcSignalQuality(it) } }

        // Altitud barométrica relativa al despegue: se actualiza de forma
        // continua e independiente del Home Point.
        keyManager.listen(
            KeyTools.createKey(FlightControllerKey.KeyAltitude),
            listenHolder, true
        ) { _, newValue -> newValue?.let { callbacks.onAltitude(it) } }

        // Ubicación de la aeronave (lat/long) para calcular distancia.
        keyManager.listen(
            KeyTools.createKey(FlightControllerKey.KeyAircraftLocation3D),
            listenHolder, true
        ) { _, newValue -> callbacks.onAircraftLocation(newValue) }

        // Home Point: solo se usa para la distancia; no bloquea el resto.
        keyManager.listen(
            KeyTools.createKey(FlightControllerKey.KeyHomeLocation),
            listenHolder, true
        ) { _, newValue -> callbacks.onHomeLocation(newValue) }

        keyManager.listen(
            KeyTools.createKey(FlightControllerKey.KeyAircraftAttitude),
            listenHolder, true
        ) { _, newValue -> callbacks.onAircraftAttitude(newValue) }
    }

    fun stop() {
        KeyManager.getInstance().cancelListen(listenHolder)
    }

    interface Callbacks {
        fun onBatteryPercent(percent: Int)
        fun onFlightModeString(flightMode: String?)
        fun onRcFlightMode(mode: RemoteControllerFlightMode?)
        fun onSatelliteCount(count: Int)
        fun onRcSignalQuality(quality: Int)
        fun onAltitude(altitude: Double)
        fun onAircraftLocation(location: LocationCoordinate3D?)
        fun onHomeLocation(home: LocationCoordinate2D?)
        fun onAircraftAttitude(attitude: Attitude?)
    }
}
