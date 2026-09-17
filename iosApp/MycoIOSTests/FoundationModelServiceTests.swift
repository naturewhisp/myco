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

    func testAllowedStylePreservesDeterministicNoteVerbatimAndAddsSafetyNotice() {
        let deterministic = "Probabilità 62%, soglia 20 e seconda soglia 20. Habitat favorevole."

        let result = FoundationModelService.render(styleChoice: "taccuino", deterministicNote: deterministic)

        XCTAssertTrue(result.contains(deterministic))
        XCTAssertTrue(result.contains(FoundationModelService.safetyNotice))
    }

    func testUnknownStyleFallsBackToDeterministicNote() {
        let deterministic = "Probabilità stimata 62%."

        let result = FoundationModelService.render(
            styleChoice: "creativo",
            deterministicNote: deterministic
        )

        XCTAssertEqual(result, deterministic)
    }

    func testArbitraryUnsafeNarrativeCannotCrossStyleBoundary() {
        let deterministic = "Probabilità stimata 62%."

        let result = FoundationModelService.render(
            styleChoice: "È sicuro da consumare e adatto al consumo.",
            deterministicNote: deterministic
        )

        XCTAssertEqual(result, deterministic)
    }
}
