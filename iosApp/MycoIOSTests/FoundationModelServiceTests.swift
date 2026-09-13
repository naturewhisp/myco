import XCTest
@testable import MycoIOS

final class FoundationModelServiceTests: XCTestCase {
    func testDisabledLocalAIKeepsDeterministicNoteUnchanged() async {
        let defaults = UserDefaults.standard
        let previous = defaults.object(forKey: PreferenceKey.aiEnabled)
        defaults.set(false, forKey: PreferenceKey.aiEnabled)
        defer {
            if let previous {
                defaults.set(previous, forKey: PreferenceKey.aiEnabled)
            } else {
                defaults.removeObject(forKey: PreferenceKey.aiEnabled)
            }
        }

        let note = "Probabilità stimata 62%. Non identifica funghi."
        let result = await FoundationModelService().enrich(deterministicNote: note)

        XCTAssertEqual(result, note)
    }
}
