import MycoCore
import SwiftUI

struct SpeciesPickerView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.herbariumColors) private var colors
    @ObservedObject var viewModel: MycoViewModel

    var body: some View {
        NavigationStack {
            List(viewModel.speciesCatalog, id: \.id) { species in
                Button {
                    viewModel.chooseSpecies(species)
                    dismiss()
                } label: {
                    VStack(alignment: .leading, spacing: 6) {
                        HStack {
                            Text(species.vernacularName).font(.headline).foregroundStyle(colors.ink)
                            Spacer()
                            if species.id == viewModel.selectedSpecies.id {
                                Image(systemName: "checkmark.circle.fill").foregroundStyle(colors.forest)
                            }
                        }
                        Text(species.binomialName).italic().foregroundStyle(colors.inkSoft)
                        Text("\(species.category.label) · \(species.fruitingPeriodDescription)")
                            .font(.caption).foregroundStyle(colors.inkSoft)
                        if !species.toxicLookAlikes.isEmpty {
                            Label("Sosia tossici documentati", systemImage: "exclamationmark.triangle.fill")
                                .font(.caption.weight(.semibold)).foregroundStyle(colors.warning)
                        }
                    }
                    .padding(.vertical, 6)
                }
                .accessibilityHint("Seleziona la specie e ricalcola l'analisi")
            }
            .navigationTitle("Specie")
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Chiudi") { dismiss() } } }
        }
    }
}
