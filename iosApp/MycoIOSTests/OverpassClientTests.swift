import Foundation
import MycoCore
import XCTest
@testable import MycoIOS

final class OverpassClientTests: XCTestCase {
    func testSaprotrophicQueryTargetsOpenHabitatsAtDefaultRadius() {
        XCTAssertEqual(OverpassClient.defaultHabitatRadiusMeters, 1_500)
        let query = OverpassClient.specificHabitatQuery(
            around: GeoCoordinates(latitude: 41.9, longitude: 12.5),
            radiusMeters: OverpassClient.defaultHabitatRadiusMeters,
            preferredCanopyTypes: [],
            ecologicalCategory: .saprotrophic
        )

        XCTAssertTrue(query.contains("[\"landuse\"~\"meadow|grass|pasture\"](around:1500,41.9,12.5)"))
        XCTAssertTrue(query.contains("[\"natural\"~\"grassland|heath\"](around:1500,41.9,12.5)"))
        XCTAssertFalse(query.contains("[\"natural\"=\"wood\"]"))
        XCTAssertTrue(query.hasSuffix("out tags geom;"))
    }

    func testTreeAssociatedQueryTargetsForestAndPreferredCanopyGenera() {
        let query = OverpassClient.specificHabitatQuery(
            around: GeoCoordinates(latitude: 45.4642, longitude: 9.19),
            radiusMeters: 2_000,
            preferredCanopyTypes: ["quercus", "FAGUS", "unknown"],
            ecologicalCategory: .treeAssociated
        )

        XCTAssertTrue(query.contains("[\"natural\"=\"wood\"](around:2000,45.4642,9.19)"))
        XCTAssertTrue(query.contains("[\"landuse\"=\"forest\"](around:2000,45.4642,9.19)"))
        XCTAssertTrue(query.contains("[\"genus\"~\"Quercus|Fagus\"](around:2000,45.4642,9.19)"))
        XCTAssertTrue(query.hasSuffix("out tags geom;"))
    }

    func testSharedSurfaceFixtureUsesGeometryAndRetainsRawElements() throws {
        let payload = try fixture("habitat/surfaces.json")
        let response = try JSONDecoder().decode(OverpassResponse.self, from: payload)
        let client = OverpassClient(apiClient: APIClient(loader: TestHTTPDataLoader { request in
            (payload, httpResponse(for: request))
        }))
        let evidence = client.extractHabitatEvidence(
            from: response.elements,
            around: GeoCoordinates(latitude: 44.2149, longitude: 7.9755),
            radiusMeters: 1_500
        )

        XCTAssertEqual(response.elements.count, 4)
        XCTAssertFalse(evidence.geometryComplete)
        XCTAssertTrue((0.96...0.99).contains(evidence.forestCoverFraction))
        XCTAssertTrue((0.9...1.0).contains(evidence.forestProximityIndex))
        XCTAssertTrue(evidence.confirmedHostGenera.contains("fagus"))
    }

    func testSaprotrophicHabitatSignalsSpecificMatchToSharedCore() async throws {
        let payload = Data("""
        {"version":0.6,"elements":[{"type":"way","id":9,"tags":{"landuse":"meadow"},"geometry":[{"lat":41.89,"lon":12.49},{"lat":41.91,"lon":12.49},{"lat":41.91,"lon":12.51},{"lat":41.89,"lon":12.51},{"lat":41.89,"lon":12.49}]}]}
        """.utf8)
        let endpoint = URL(string: "https://overpass.test/api")!
        let loader = TestHTTPDataLoader { request in
            (payload, httpResponse(for: request))
        }
        let client = OverpassClient(apiClient: APIClient(loader: loader), endpoints: [endpoint])

        let species = try XCTUnwrap(SpeciesCatalog.shared.byId(id: "macrolepiota_procera"))
        let snapshot = try await client.habitat(
            around: GeoCoordinates(latitude: 41.9, longitude: 12.5),
            preferredCanopyTypes: [],
            ecologicalCategory: .saprotrophic,
            selectedSpecies: species
        )

        XCTAssertEqual(snapshot.score, 0.95)
        XCTAssertEqual(snapshot.canopyCover, 0.0)
        XCTAssertEqual(snapshot.forestProximityIndex, 0.0)
        XCTAssertEqual(snapshot.canopyTypes, ["saprotrophic_habitat"])
        XCTAssertEqual(loader.requests.count, 2)
    }

    func testQueryFailsOverToNextEndpoint() async throws {
        let payload = Data("""
        {"version":0.6,"elements":[{"type":"node","id":7,"lat":41.9,"lon":12.5,"tags":{"natural":"wood"}}]}
        """.utf8)
        let first = URL(string: "https://first.test/api")!
        let second = URL(string: "https://second.test/api")!
        let loader = TestHTTPDataLoader { request in
            if request.url?.host == first.host {
                throw URLError(.cannotConnectToHost)
            }
            return (payload, httpResponse(for: request))
        }
        let client = OverpassClient(apiClient: APIClient(loader: loader), endpoints: [first, second])

        let response = try await client.query("[out:json];out;")

        XCTAssertEqual(response.elements.count, 1)
        XCTAssertEqual(loader.requests.map { $0.url?.host }, ["first.test", "second.test"])
    }

    func testAllEndpointFailuresIncludeHosts() async {
        let endpoints = [URL(string: "https://first.test/api")!, URL(string: "https://second.test/api")!]
        let loader = TestHTTPDataLoader { _ in
            throw URLError(.cannotConnectToHost)
        }
        let client = OverpassClient(apiClient: APIClient(loader: loader), endpoints: endpoints)

        do {
            _ = try await client.query("query")
            XCTFail("Expected all-endpoints-failed error")
        } catch let OverpassClientError.allEndpointsFailed(failed) {
            XCTAssertEqual(failed, ["first.test", "second.test"])
        } catch {
            XCTFail("Unexpected error: \(error)")
        }
    }

    func testCancellationIsNotConvertedToEndpointFailure() async {
        let loader = TestHTTPDataLoader { _ in
            throw CancellationError()
        }
        let client = OverpassClient(
            apiClient: APIClient(loader: loader),
            endpoints: [URL(string: "https://first.test/api")!, URL(string: "https://second.test/api")!]
        )

        do {
            _ = try await client.query("query")
            XCTFail("Expected cancellation")
        } catch is CancellationError {
            XCTAssertEqual(loader.requests.count, 1)
        } catch {
            XCTFail("Unexpected error: \(error)")
        }
    }

    func testSaprotrophicHabitatWithClosedMeadowSurfaceYieldsMeadowScore() async throws {
        let meadowPayload = Data(#"{"version":0.6,"elements":[{"type":"way","id":9,"tags":{"landuse":"meadow"},"geometry":[{"lat":41.89,"lon":12.49},{"lat":41.91,"lon":12.49},{"lat":41.91,"lon":12.51},{"lat":41.89,"lon":12.51},{"lat":41.89,"lon":12.49}]}]}"#.utf8)
        let endpoint = URL(string: "https://overpass.test/api")!
        let loader = TestHTTPDataLoader { request in
            return (meadowPayload, httpResponse(for: request))
        }
        let client = OverpassClient(apiClient: APIClient(loader: loader), endpoints: [endpoint])

        let species = try XCTUnwrap(SpeciesCatalog.shared.byId(id: "macrolepiota_procera"))
        let snapshot = try await client.habitat(
            around: GeoCoordinates(latitude: 41.9, longitude: 12.5),
            preferredCanopyTypes: [],
            ecologicalCategory: .saprotrophic,
            selectedSpecies: species
        )

        XCTAssertEqual(snapshot.score, 0.95)
        XCTAssertEqual(snapshot.canopyCover, 0.0)
        XCTAssertEqual(snapshot.forestProximityIndex, 0.0)
        XCTAssertEqual(snapshot.canopyTypes, ["saprotrophic_habitat"])
        XCTAssertTrue(snapshot.description.lowercased().contains("praticolo"))
        XCTAssertNotNil(snapshot.rawElements)
    }

    func testCachedHabitatSnapshotWithoutRawElementsDecodesToUnknownGeometry() throws {
        let legacy = Data(#"{"score":0.9,"description":"Habitat cached","canopyTypes":["fagus"],"canopyCover":0.7,"forestProximityIndex":0.7}"#.utf8)
        let snapshot = try JSONDecoder().decode(HabitatSnapshot.self, from: legacy)
        let client = OverpassClient(apiClient: APIClient(loader: TestHTTPDataLoader { request in
            (Data(#"{"version":0.6,"elements":[]}"#.utf8), httpResponse(for: request))
        }))
        let evidence = client.extractHabitatEvidence(
            from: snapshot.rawElements ?? [],
            around: GeoCoordinates(latitude: 42, longitude: 12)
        )

        XCTAssertNil(snapshot.rawElements)
        XCTAssertFalse(evidence.geometryComplete)
        XCTAssertEqual(evidence.forestCoverFraction, 0)
        XCTAssertEqual(evidence.forestProximityIndex, 0)
    }

    func testFindNearestForestFindsClosestCandidateWithinRange() async throws {
        let payload = Data("""
        {
            "version": 0.6,
            "elements": [
                {"type": "node", "id": 101, "lat": 44.510, "lon": 8.010, "tags": {"natural": "wood"}},
                {"type": "node", "id": 102, "lat": 44.502, "lon": 8.002, "tags": {"landuse": "forest"}},
                {"type": "way", "id": 103, "center": {"lat": 44.520, "lon": 8.020}, "tags": {"natural": "wood"}}
            ]
        }
        """.utf8)
        let endpoint = URL(string: "https://overpass.test/api")!
        let loader = TestHTTPDataLoader { request in
            (payload, httpResponse(for: request))
        }
        let client = OverpassClient(apiClient: APIClient(loader: loader), endpoints: [endpoint])
        let nearest = try await client.findNearestForest(around: GeoCoordinates(latitude: 44.500, longitude: 8.000))

        let target = try XCTUnwrap(nearest)
        XCTAssertEqual(target.latitude, 44.502, accuracy: 0.0001)
        XCTAssertEqual(target.longitude, 8.002, accuracy: 0.0001)
    }

    func testFindNearestForestReturnsNilWhenNoElementsFound() async throws {
        let payload = Data(#"{"version": 0.6, "elements": []}"#.utf8)
        let endpoint = URL(string: "https://overpass.test/api")!
        let loader = TestHTTPDataLoader { request in
            (payload, httpResponse(for: request))
        }
        let client = OverpassClient(apiClient: APIClient(loader: loader), endpoints: [endpoint])
        let nearest = try await client.findNearestForest(around: GeoCoordinates(latitude: 44.500, longitude: 8.000))

        XCTAssertNil(nearest)
    }

    private func fixture(_ path: String) throws -> Data {
        let components = path.split(separator: "/").map(String.init)
        let fileURL = URL(fileURLWithPath: try XCTUnwrap(components.last))
        let url = try XCTUnwrap(Bundle(for: Self.self).url(
            forResource: fileURL.deletingPathExtension().lastPathComponent,
            withExtension: fileURL.pathExtension,
            subdirectory: "testFixtures/\(components.dropLast().joined(separator: "/"))"
        ))
        return try Data(contentsOf: url)
    }
}
