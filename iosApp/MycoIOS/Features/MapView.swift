import MapKit
import SwiftUI

struct MapView: View {
    @Environment(\.herbariumColors) private var colors
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @ObservedObject var viewModel: MycoViewModel
    @ObservedObject var locationService: CoreLocationService
    @Namespace private var mapScope
    @State private var cameraPosition = MapCameraPosition.region(Self.italyRegion)

    var body: some View {
        NavigationStack {
            MapReader { proxy in
                Map(position: $cameraPosition, scope: mapScope) {
                    UserAnnotation()
                    if let selected = viewModel.selectedLocation {
                        Marker(selected.name, coordinate: selected.coordinate)
                            .tint(colors.forest)
                    }
                }
                .onTapGesture(coordinateSpace: .local) { point in
                    guard let coordinate = proxy.convert(point, from: .local) else { return }
                    viewModel.select(coordinate: coordinate)
                }
                .overlay(alignment: .topTrailing) {
                    VStack(spacing: 8) {
                        MapCompass(scope: mapScope)
                        MapPitchToggle(scope: mapScope)
                        MapUserLocationButton(scope: mapScope)
                    }
                    .padding(12)
                }
                .overlay(alignment: .bottom) {
                    Text("Tocca la mappa per scegliere una località")
                        .font(.footnote.weight(.medium))
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .background(.regularMaterial, in: Capsule())
                        .padding()
                        .accessibilityLabel("Tocca la mappa per scegliere una località e caricare i dati ambientali")
                }
            }
            .navigationTitle("Mappa")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Italia", systemImage: "globe.europe.africa") {
                        if reduceMotion {
                            cameraPosition = .region(Self.italyRegion)
                        } else {
                            withAnimation(.easeInOut) {
                                cameraPosition = .region(Self.italyRegion)
                            }
                        }
                    }
                    .frame(minWidth: 44, minHeight: 44)
                    .accessibilityHint("Centra la mappa sull'Italia")
                }
            }
        }
        .onAppear { locationService.start() }
        .onDisappear { locationService.stop() }
    }

    private static let italyRegion = MKCoordinateRegion(
        center: CLLocationCoordinate2D(latitude: 42.5, longitude: 12.5),
        span: MKCoordinateSpan(latitudeDelta: 10, longitudeDelta: 10)
    )
}
