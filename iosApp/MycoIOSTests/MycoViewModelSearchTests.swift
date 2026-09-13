import CoreLocation
import XCTest
@testable import MycoIOS

@MainActor
final class MycoViewModelSearchTests: XCTestCase {
    func testNewSearchCancelsStaleResult() async throws {
        let viewModel = MycoViewModel(locationSearch: DelayedSearchService())

        viewModel.submitSearch(query: "prima")
        viewModel.submitSearch(query: "seconda")
        try await Task.sleep(for: .milliseconds(100))

        XCTAssertEqual(viewModel.searchResults.map(\.name), ["seconda"])
        XCTAssertFalse(viewModel.isSearching)
    }
}

@MainActor
private struct DelayedSearchService: LocationSearching {
    func search(query: String) async throws -> [LocationSearchResult] {
        try await Task.sleep(for: query == "prima" ? .milliseconds(80) : .milliseconds(5))
        return [
            LocationSearchResult(
                id: query,
                name: query,
                detail: nil,
                coordinate: CLLocationCoordinate2D(latitude: 42, longitude: 12)
            ),
        ]
    }
}
