import Foundation
#if canImport(FoundationModels)
import FoundationModels
#endif

protocol FieldNoteGenerating: Sendable {
    func enrich(deterministicNote: String) async -> String
}

struct FoundationModelService: FieldNoteGenerating {
    func enrich(deterministicNote: String) async -> String {
        guard UserDefaults.standard.bool(forKey: PreferenceKey.aiEnabled) else { return deterministicNote }
#if canImport(FoundationModels)
        if #available(iOS 26.0, *), SystemLanguageModel.default.isAvailable {
            do {
                let session = LanguageModelSession(
                    instructions: "Riscrivi esclusivamente lo stile di una breve nota ambientale micologica. Non cambiare numeri, probabilità, specie, tossicità o raccomandazioni di sicurezza. Non identificare funghi."
                )
                let response = try await session.respond(to: "Nota deterministica da preservare nei fatti: \(deterministicNote)")
                return response.content
            } catch {
                return deterministicNote
            }
        }
#endif
        return deterministicNote
    }
}
