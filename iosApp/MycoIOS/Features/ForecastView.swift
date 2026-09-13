import Charts
import SwiftUI

struct ForecastView: View {
    @Environment(\.herbariumColors) private var colors
    @ObservedObject var viewModel: MycoViewModel

    var body: some View {
        NavigationStack {
            Group {
                if viewModel.isLoadingEnvironment {
                    ProgressView("Caricamento condizioni ambientali")
                } else if let selectedLocation = viewModel.selectedLocation,
                          !viewModel.environmentalDays.isEmpty {
                    ScrollView {
                        VStack(alignment: .leading, spacing: 24) {
                            VStack(alignment: .leading, spacing: 6) {
                                Label(selectedLocation.name, systemImage: "mappin.and.ellipse")
                                    .font(.headline)
                                Text("Temperature e pioggia previste per i prossimi 7 giorni.")
                                    .font(.subheadline)
                                    .foregroundStyle(colors.inkSoft)
                            }

                            Chart(viewModel.environmentalDays.prefix(7)) { day in
                                if let minimum = day.minimumTemperature, let maximum = day.maximumTemperature {
                                    BarMark(
                                        x: .value("Giorno", day.date, unit: .day),
                                        yStart: .value("Minima", minimum),
                                        yEnd: .value("Massima", maximum)
                                    )
                                    .foregroundStyle(colors.forest.gradient)
                                    .accessibilityLabel("\(day.date.formatted(date: .abbreviated, time: .omitted)): da \(minimum.formatted(.number.precision(.fractionLength(1)))) a \(maximum.formatted(.number.precision(.fractionLength(1)))) gradi Celsius")
                                }
                            }
                            .chartYAxisLabel("Temperatura °C")
                            .chartXAxis {
                                AxisMarks(values: .stride(by: .day)) { _ in
                                    AxisGridLine()
                                    AxisValueLabel(format: .dateTime.weekday(.narrow))
                                }
                            }
                            .frame(height: 220)
                            .accessibilityLabel("Grafico delle temperature minime e massime previste")

                            Chart(viewModel.environmentalDays.prefix(7)) { day in
                                if let rainfall = day.rainfall {
                                    BarMark(
                                        x: .value("Giorno", day.date, unit: .day),
                                        y: .value("Pioggia", rainfall)
                                    )
                                    .foregroundStyle(colors.lichen.gradient)
                                }
                            }
                            .chartYAxisLabel("Pioggia mm")
                            .chartXAxis {
                                AxisMarks(values: .stride(by: .day)) { _ in
                                    AxisGridLine()
                                    AxisValueLabel(format: .dateTime.weekday(.narrow))
                                }
                            }
                            .frame(height: 180)
                            .accessibilityLabel("Grafico della pioggia prevista")

                            Label("Questi sono dati meteorologici, non probabilità di crescita. Le probabilità saranno mostrate solo dopo il collegamento del core KMP.", systemImage: "info.circle")
                                .font(.footnote)
                                .foregroundStyle(colors.inkSoft)
                        }
                        .padding()
                    }
                } else {
                    ContentUnavailableView {
                        Label("Scegli una località", systemImage: "cloud.sun")
                    } description: {
                        Text("Cerca una località nel Registro o tocca la Mappa per vedere le previsioni ambientali.")
                    }
                }
            }
            .foregroundStyle(colors.ink)
            .background(colors.background)
            .navigationTitle("Previsioni")
        }
    }

}
