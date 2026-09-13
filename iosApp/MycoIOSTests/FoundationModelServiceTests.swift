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

    func testSafeNarrativePreservesFactsAndReceivesSafetyNotice() {
        let deterministic = "Probabilità stimata 62%. Habitat favorevole."
        let generated = "Il quadro ambientale indica una probabilità stimata 62%, con habitat favorevole."

        let result = FoundationModelService.validatedNarrative(generated, deterministicNote: deterministic)

        XCTAssertTrue(result.contains(generated))
        XCTAssertTrue(result.contains(FoundationModelService.safetyNotice))
    }

    func testNarrativeChangingProbabilityFallsBackToDeterministicNote() {
        let deterministic = "Probabilità stimata 62%."

        let result = FoundationModelService.validatedNarrative(
            "Probabilità stimata 80%.",
            deterministicNote: deterministic
        )

        XCTAssertEqual(result, deterministic)
    }

    func testUnsafeIdentificationCertaintyFallsBackToDeterministicNote() {
        let deterministic = "Probabilità stimata 62%."

        let result = FoundationModelService.validatedNarrative(
            "Probabilità stimata 62%: il fungo è commestibile.",
            deterministicNote: deterministic
        )

        XCTAssertEqual(result, deterministic)
    }
}
