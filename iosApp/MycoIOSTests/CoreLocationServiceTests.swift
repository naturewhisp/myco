import CoreLocation
import UIKit
import XCTest
@testable import MycoIOS

@MainActor
final class CoreLocationServiceTests: XCTestCase {
    func testFreshStoppedStateDoesNotRequestAuthorization() async throws {
        let manager = LocationManagerSpy(authorizationStatus: .notDetermined)
        _ = makeService(manager: manager)

        try await settleAsyncAvailabilityCheck()

        XCTAssertEqual(manager.authorizationRequestCount, 0)
        XCTAssertEqual(manager.locationRequestCount, 0)
        XCTAssertEqual(manager.startLocationCount, 0)
        XCTAssertEqual(manager.startHeadingCount, 0)
    }

    func testRequestCurrentLocationAuthorizesThenRequestsOneShot() async throws {
        let manager = LocationManagerSpy(authorizationStatus: .notDetermined)
        let service = makeService(manager: manager)

        service.requestCurrentLocation()
        try await waitUntil { manager.authorizationRequestCount == 1 }

        manager.stubAuthorizationStatus = .authorizedWhenInUse
        service.locationManagerDidChangeAuthorization(manager)
        try await waitUntil { manager.locationRequestCount == 1 }

        XCTAssertEqual(manager.startLocationCount, 0)
        XCTAssertEqual(manager.startHeadingCount, 0)
    }

    func testStartTrackingAuthorizesThenStartsLocationAndHeading() async throws {
        let manager = LocationManagerSpy(authorizationStatus: .notDetermined)
        let service = makeService(manager: manager)

        service.startTracking()
        try await waitUntil { manager.authorizationRequestCount == 1 }

        manager.stubAuthorizationStatus = .authorizedWhenInUse
        service.locationManagerDidChangeAuthorization(manager)
        try await waitUntil {
            manager.startLocationCount == 1 && manager.startHeadingCount == 1
        }

        XCTAssertEqual(manager.locationRequestCount, 0)
    }

    func testForegroundWhileStoppedDoesNotRequestAuthorizationOrStartUpdates() async throws {
        let manager = LocationManagerSpy(authorizationStatus: .notDetermined)
        let service = makeService(manager: manager)
        service.stopTracking()

        NotificationCenter.default.post(name: UIApplication.didBecomeActiveNotification, object: nil)
        try await settleAsyncAvailabilityCheck()

        XCTAssertEqual(manager.authorizationRequestCount, 0)
        XCTAssertEqual(manager.locationRequestCount, 0)
        XCTAssertEqual(manager.startLocationCount, 0)
        XCTAssertEqual(manager.startHeadingCount, 0)
    }

    private func makeService(manager: LocationManagerSpy) -> CoreLocationService {
        CoreLocationService(
            manager: manager,
            locationServicesEnabledProvider: { true },
            headingAvailableProvider: { true }
        )
    }

    private func settleAsyncAvailabilityCheck() async throws {
        try await Task.sleep(for: .milliseconds(50))
    }

    private func waitUntil(
        timeout: Duration = .seconds(5),
        condition: @escaping @MainActor () -> Bool
    ) async throws {
        let deadline = ContinuousClock.now + timeout
        while !condition() {
            guard ContinuousClock.now < deadline else {
                XCTFail("Timed out waiting for Core Location interaction")
                return
            }
            try await Task.sleep(for: .milliseconds(10))
        }
    }
}

private final class LocationManagerSpy: CLLocationManager, @unchecked Sendable {
    var stubAuthorizationStatus: CLAuthorizationStatus
    private(set) var authorizationRequestCount = 0
    private(set) var locationRequestCount = 0
    private(set) var startLocationCount = 0
    private(set) var startHeadingCount = 0

    init(authorizationStatus: CLAuthorizationStatus) {
        stubAuthorizationStatus = authorizationStatus
        super.init()
    }

    override var authorizationStatus: CLAuthorizationStatus {
        stubAuthorizationStatus
    }

    override func requestWhenInUseAuthorization() {
        authorizationRequestCount += 1
    }

    override func requestLocation() {
        locationRequestCount += 1
    }

    override func startUpdatingLocation() {
        startLocationCount += 1
    }

    override func startUpdatingHeading() {
        startHeadingCount += 1
    }
}
