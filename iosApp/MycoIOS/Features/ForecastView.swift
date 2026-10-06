import Charts
import SwiftUI

struct ForecastView: View {
    @Environment(\.herbariumColors) private var colors
    @ObservedObject var viewModel: MycoViewModel

    var body: some View {
        NavigationStack {
            Group {
                if viewModel.isLoadingEnvironment {
                    ProgressView("Calcolo indice previsionale")
                } else if let selectedLocation = viewModel.selectedLocation, !viewModel.environmentalDays.isEmpty {
                    ScrollView {
                        VStack(alignment: .leading, spacing: 24) {
                            Label(selectedLocation.name, systemImage: "mappin.and.ellipse").font(.headline)
                            Text("Indice di idoneità prodotto dal core condiviso per \(viewModel.selectedSpecies.vernacularName).")
                                .font(.subheadline).foregroundStyle(colors.inkSoft)

                            if viewModel.environmentalDays.prefix(7).allSatisfy({ $0.isCalculable }) {
                            Chart(viewModel.environmentalDays.prefix(7)) { day in
                                LineMark(x: .value("Giorno", day.date, unit: .day), y: .value("Indice", day.probability))
                                    .foregroundStyle(colors.warning)
                                    .symbol(Circle())
                                PointMark(x: .value("Giorno", day.date, unit: .day), y: .value("Indice", day.probability))
                                    .annotation(position: .top) { Text("\(day.probability)/100").font(.caption2) }
                            }
                            .chartYScale(domain: 0...100)
                            .chartYAxisLabel("Indice (0–100)")
                            .chartXAxis { AxisMarks(values: .stride(by: .day)) { _ in AxisGridLine(); AxisValueLabel(format: .dateTime.weekday(.narrow)) } }
                            .frame(height: 240)
                            .accessibilityLabel("Indice di idoneità ambientale nei prossimi sette giorni")
                            } else { Text("Trend incompleto: alcuni giorni non sono calcolabili.").foregroundStyle(colors.inkSoft) }

                            Chart(viewModel.environmentalDays.prefix(7).filter { $0.isCalculable }) { day in
                                LineMark(x: .value("Giorno", day.date, unit: .day), y: .value("Temperatura", day.averageTemperature))
                                    .foregroundStyle(colors.forest)
                                BarMark(x: .value("Giorno", day.date, unit: .day), y: .value("Pioggia", day.rainfall))
                                    .foregroundStyle(colors.lichen.opacity(0.55))
                            }
                            .chartYAxisLabel("°C / mm")
                            .chartXAxis { AxisMarks(values: .stride(by: .day)) { _ in AxisGridLine(); AxisValueLabel(format: .dateTime.weekday(.narrow)) } }
                            .frame(height: 200)
                            .accessibilityLabel("Temperature e precipitazioni previste")

                            ForEach(viewModel.environmentalDays.prefix(7)) { day in
                                HStack {
                                    Text(day.date.formatted(.dateTime.weekday(.abbreviated).day().month())).frame(maxWidth: .infinity, alignment: .leading)
                                    Text(day.isCalculable ? "\(day.probability)/100 · \(day.tierLabel)" : "Indice non calcolabile").fontWeight(.semibold)
                                }
                                .frame(minHeight: 44)
                                .accessibilityElement(children: .combine)
                            }
                        }
                        .padding()
                    }
                } else {
                    ContentUnavailableView {
                        Label("Scegli una località", systemImage: "cloud.sun")
                    } description: {
                        Text("Avvia un'analisi dal Registro o dalla Mappa.")
                    }
                }
            }
            .foregroundStyle(colors.ink)
            .background(colors.background)
            .navigationTitle("Previsioni")
        }
    }
}
