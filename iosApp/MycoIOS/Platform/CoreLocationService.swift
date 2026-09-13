import Combine
@preconcurrency import CoreLocation
import Foundation
import UIKit

@MainActor
final class CoreLocationService: NSObject, ObservableObject {
    struct LocationSnapshot: Sendable {
        let latitude: Double
        let longitude: Double
        let altitude: Double
        let horizontalAccuracy: Double
        let timestamp: Date
    }

    struct HeadingSnapshot: Sendable {
        let magneticHeading: Double
        let trueHeading: Double
        let headingAccuracy: Double
        let timestamp: Date

        var effectiveHeading: Double? {
            guard headingAccuracy >= 0 else { return nil }
            if trueHeading >= 0 { return trueHeading }
            return magneticHeading >= 0 ? magneticHeading : nil
        }
    }

    @Published private(set) var authorizationStatus: CLAuthorizationStatus
    @Published private(set) var accuracyAuthorization: CLAccuracyAuthorization
    @Published private(set) var locationServicesAvailable: Bool?
    @Published private(set) var location: LocationSnapshot?
    @Published private(set) var heading: HeadingSnapshot?
    @Published private(set) var errorDescription: String?

    private let manager: CLLocationManager
    private enum Mode {
        case stopped
        case oneShot
        case tracking
    }

    private var mode = Mode.stopped
    private var isApplicationActive = true
    override convenience init() {
        self.init(manager: CLLocationManager())
    }

    init(manager: CLLocationManager) {
        self.manager = manager
        authorizationStatus = manager.authorizationStatus
        accuracyAuthorization = manager.accuracyAuthorization
        locationServicesAvailable = nil
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyNearestTenMeters
        installLifecycleObservers()
        refreshServiceAvailability()
    }

    /// Requests one location fix without leaving GPS or heading updates active.
    func requestCurrentLocation() {
        mode = .oneShot
        authorizeOrStart()
    }

    /// Starts continuous location and heading updates while the map is visible and the app is active.
    func startTracking() {
        mode = .tracking
        authorizeOrStart()
    }

    private func authorizeOrStart() {
        refreshServiceAvailability(continueAuthorization: true)
    }

    private func refreshServiceAvailability(continueAuthorization: Bool = false) {
        Task { [weak self] in
            let isAvailable = await Task.detached(priority: .userInitiated) {
                CLLocationManager.locationServicesEnabled()
            }.value
            guard let self else { return }
            locationServicesAvailable = isAvailable
            guard continueAuthorization, isAvailable else {
                if !isAvailable { stopUpdates() }
                return
            }
            authorizeOrStartAfterAvailabilityCheck()
        }
    }

    private func authorizeOrStartAfterAvailabilityCheck() {
        switch manager.authorizationStatus {
        case .notDetermined:
            manager.requestWhenInUseAuthorization()
        case .authorizedAlways, .authorizedWhenInUse:
            startRequestedModeIfActive()
        case .denied, .restricted:
            stopUpdates()
        @unknown default:
            stopUpdates()
        }
    }

    /// Stops all hardware-backed updates immediately. Call when the owning screen is no longer active.
    func stopTracking() {
        mode = .stopped
        stopUpdates()
    }

    private func installLifecycleObservers() {
        let center = NotificationCenter.default
        center.addObserver(
            self,
            selector: #selector(applicationDidBecomeActive),
            name: UIApplication.didBecomeActiveNotification,
            object: nil
        )
        center.addObserver(
            self,
            selector: #selector(applicationWillResignActive),
            name: UIApplication.willResignActiveNotification,
            object: nil
        )
    }

    @objc nonisolated private func applicationDidBecomeActive() {
        Task { @MainActor [weak self] in
            self?.isApplicationActive = true
            self?.authorizeOrStart()
        }
    }

    @objc nonisolated private func applicationWillResignActive() {
        Task { @MainActor [weak self] in
            self?.isApplicationActive = false
            self?.stopUpdates()
        }
    }

    private func startRequestedModeIfActive() {
        guard isApplicationActive else { return }
        switch mode {
        case .stopped:
            stopUpdates()
        case .oneShot:
            manager.requestLocation()
        case .tracking:
            manager.startUpdatingLocation()
            if CLLocationManager.headingAvailable() {
                manager.startUpdatingHeading()
            }
        }
    }

    private func stopUpdates() {
        manager.stopUpdatingLocation()
        manager.stopUpdatingHeading()
    }
}

extension CoreLocationService: CLLocationManagerDelegate {
    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        let status = manager.authorizationStatus
        Task { @MainActor [weak self] in
            guard let self else { return }
            authorizationStatus = status
            accuracyAuthorization = manager.accuracyAuthorization
            switch status {
            case .authorizedAlways, .authorizedWhenInUse:
                refreshServiceAvailability(continueAuthorization: true)
            case .notDetermined, .denied, .restricted:
                stopUpdates()
                refreshServiceAvailability()
            @unknown default:
                stopUpdates()
                refreshServiceAvailability()
            }
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        let snapshot = locations.last.map {
            LocationSnapshot(
                latitude: $0.coordinate.latitude,
                longitude: $0.coordinate.longitude,
                altitude: $0.altitude,
                horizontalAccuracy: $0.horizontalAccuracy,
                timestamp: $0.timestamp
            )
        }
        Task { @MainActor [weak self] in
            guard let self else { return }
            location = snapshot
            if case .oneShot = mode {
                mode = .stopped
                stopUpdates()
            }
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateHeading newHeading: CLHeading) {
        let snapshot = HeadingSnapshot(
            magneticHeading: newHeading.magneticHeading,
            trueHeading: newHeading.trueHeading,
            headingAccuracy: newHeading.headingAccuracy,
            timestamp: newHeading.timestamp
        )
        Task { @MainActor [weak self] in
            self?.heading = snapshot
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        let description = error.localizedDescription
        Task { @MainActor [weak self] in
            guard let self else { return }
            errorDescription = description
            if case .oneShot = mode {
                mode = .stopped
            }
        }
    }
}
