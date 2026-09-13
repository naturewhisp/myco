import Foundation
#if canImport(FoundationModels)
import FoundationModels
#endif

protocol FieldNoteGenerating: Sendable {
    func enrich(deterministicNote: String) async -> String
}

struct FoundationModelService: FieldNoteGenerating {
    static let safetyNotice = "Myco non identifica funghi e non conferma la commestibilità."

    func enrich(deterministicNote: String) async -> String {
        guard UserDefaults.standard.bool(forKey: PreferenceKey.aiEnabled) else { return deterministicNote }
#if canImport(FoundationModels)
        if #available(iOS 26.0, *), SystemLanguageModel.default.isAvailable {
            do {
                let session = LanguageModelSession(
                    instructions: "Riscrivi esclusivamente lo stile di una breve nota ambientale micologica. Non cambiare o aggiungere numeri, probabilità, specie, tossicità o raccomandazioni di sicurezza. Non identificare funghi e non dichiararli commestibili."
                )
                let response = try await session.respond(to: "Nota deterministica da preservare nei fatti: \(deterministicNote)")
                return Self.validatedNarrative(response.content, deterministicNote: deterministicNote)
            } catch {
                return deterministicNote
            }
        }
#endif
        return deterministicNote
    }

    /// Rejects generated prose that changes numeric facts or introduces identification/consumption certainty.
    static func validatedNarrative(_ generated: String, deterministicNote: String) -> String {
        let trimmed = generated.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty,
              numericTokens(in: trimmed) == numericTokens(in: deterministicNote)
        else { return deterministicNote }

        let unsafePhrases = [
            "identificato come",
            "identifica come",
            "è commestibile",
            "e commestibile",
            "sicuro da mangiare",
            "puoi mangiare",
            "può essere consumato",
            "puo essere consumato",
        ]
        let normalized = trimmed.folding(options: [.caseInsensitive, .diacriticInsensitive], locale: .current)
        guard !unsafePhrases.contains(where: normalized.contains) else { return deterministicNote }
        guard !normalized.contains(safetyNotice.folding(options: [.caseInsensitive, .diacriticInsensitive], locale: .current)) else {
            return trimmed
        }
        return "\(trimmed) \(safetyNotice)"
    }

    private static func numericTokens(in text: String) -> Set<String> {
        guard let expression = try? NSRegularExpression(pattern: #"\d+(?:[.,]\d+)?%?"#) else { return [] }
        let range = NSRange(text.startIndex..., in: text)
        return Set(expression.matches(in: text, range: range).compactMap { match in
            guard let tokenRange = Range(match.range, in: text) else { return nil }
            return String(text[tokenRange])
        })
    }
}
