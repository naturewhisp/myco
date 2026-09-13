import Foundation
#if canImport(FoundationModels)
import FoundationModels
#endif

protocol FieldNoteGenerating: Sendable {
    func enrich(deterministicNote: String) async -> String
}

struct FoundationModelService: FieldNoteGenerating {
    static let safetyNotice = "Myco non identifica funghi e non conferma la commestibilità."

    private enum NoteStyle: String {
        case essential = "essenziale"
        case fieldJournal = "taccuino"
        case observational = "osservazione"
    }

    func enrich(deterministicNote: String) async -> String {
        guard UserDefaults.standard.bool(forKey: PreferenceKey.aiEnabled) else { return deterministicNote }
#if canImport(FoundationModels)
        if #available(iOS 26.0, *), SystemLanguageModel.default.isAvailable {
            do {
                let session = LanguageModelSession(
                    instructions: "Seleziona esclusivamente uno stile per una nota ambientale. Rispondi con una sola parola tra: essenziale, taccuino, osservazione. Non riscrivere la nota e non aggiungere altri contenuti."
                )
                let response = try await session.respond(to: "Scegli lo stile per questa nota: \(deterministicNote)")
                return Self.render(styleChoice: response.content, deterministicNote: deterministicNote)
            } catch {
                return deterministicNote
            }
        }
#endif
        return deterministicNote
    }

    /// The model selects only a closed style token. Scientific and safety prose is rendered locally.
    static func render(styleChoice: String, deterministicNote: String) -> String {
        let normalizedChoice = styleChoice
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased(with: Locale(identifier: "it_IT"))
        guard let style = NoteStyle(rawValue: normalizedChoice) else { return deterministicNote }

        let styledNote = switch style {
        case .essential:
            deterministicNote
        case .fieldJournal:
            "Nota dal taccuino: \(deterministicNote)"
        case .observational:
            "Osservazione ambientale: \(deterministicNote)"
        }
        return "\(styledNote) \(safetyNotice)"
    }
}
