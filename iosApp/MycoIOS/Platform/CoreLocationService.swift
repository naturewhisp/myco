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
    }

    @Published private(set) var authorizationStatus: CLAuthorizationStatus
    @Published private(set) var location: LocationSnapshot?
    @Published private(set) var heading: HeadingSnapshot?
    @Published private(set) var errorDescription: String?

    private let manager: CLLocationManager
    private var shouldRun = false
    private var isApplicationActive = true
    override convenience init() {
        self.init(manager: CLLocationManager())
    }

    init(manager: CLLocationManager) {
        self.manager = manager
        authorizationStatus = manager.authorizationStatus
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyNearestTenMeters
        installLifecycleObservers()
    }

    /// Starts requesting location and heading updates while the application is active.
    func start() {
        shouldRun = true
        guard CLLocationManager.locationServicesEnabled() else { return }
        switch manager.authorizationStatus {
        case .notDetermined:
            manager.requestWhenInUseAuthorization()
        case .authorizedAlways, .authorizedWhenInUse:
            startUpdatesIfActive()
        case .denied, .restricted:
            stopUpdates()
        @unknown default:
            stopUpdates()
        }
    }

    /// Stops all hardware-backed updates immediately. Call when the owning screen is no longer active.
    func stop() {
        shouldRun = false
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
            self?.startUpdatesIfActive()
        }
    }

    @objc nonisolated private func applicationWillResignActive() {
        Task { @MainActor [weak self] in
            self?.isApplicationActive = false
            self?.stopUpdates()
        }
    }

    private func startUpdatesIfActive() {
        guard shouldRun, isApplicationActive else { return }
        manager.startUpdatingLocation()
        if CLLocationManager.headingAvailable() {
            manager.startUpdatingHeading()
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
            if status == .authorizedAlways || status == .authorizedWhenInUse {
                startUpdatesIfActive()
            } else {
                stopUpdates()
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
            self?.location = snapshot
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
            self?.errorDescription = description
        }
    }
}
