import MapKit
import MycoCore
import SwiftUI

struct MapView: View {
    @Environment(\.herbariumColors) private var colors
    @Environment(\.colorScheme) private var colorScheme
    @ObservedObject var viewModel: MycoViewModel
    @ObservedObject var locationService: CoreLocationService
    let isActive: Bool
    @State private var mapType = MKMapType.standard
    @State private var isPitched = false
    @State private var cameraCommand: MapCameraCommand?

    var body: some View {
        NavigationStack {
            MycoMapRepresentable(
                selectedLocation: viewModel.selectedLocation,
                heatmap: viewModel.heatmap,
                mapType: mapType,
                cameraCommand: cameraCommand,
                markerColor: UIColor(colors.forest)
            ) { coordinate in
                viewModel.select(coordinate: coordinate)
            }
            .ignoresSafeArea(edges: .bottom)
            .accessibilityLabel("Mappa delle località analizzate")
            .accessibilityHint("Tocca per selezionare una località")
            .overlay(alignment: .topTrailing) { controls }
            .safeAreaInset(edge: .bottom) { analysisCard }
            .navigationTitle("Mappa")
        }
        .onAppear {
            updateActivity()
        }
        .onDisappear { locationService.stopTracking() }
        .onChange(of: isActive) { _, _ in updateActivity() }
        .onChange(of: colorScheme) { _, newValue in
            viewModel.refreshHeatmapPalette(isDark: newValue == .dark)
        }
    }

    private func updateActivity() {
        guard isActive else {
            locationService.stopTracking()
            return
        }
        locationService.startTracking()
        viewModel.refreshHeatmapPalette(isDark: colorScheme == .dark)
    }

    private var controls: some View {
        VStack(spacing: 8) {
            mapButton("Posizione", icon: "location.fill") {
                guard let location = locationService.location else {
                    locationService.startTracking()
                    return
                }
                cameraCommand = MapCameraCommand(coordinate: CLLocationCoordinate2D(latitude: location.latitude, longitude: location.longitude))
            }
            mapButton("Selezione", icon: "mappin") {
                guard let selected = viewModel.selectedLocation else { return }
                cameraCommand = MapCameraCommand(coordinate: selected.coordinate)
            }
            mapButton("Nord", icon: "safari") {
                let coordinate = viewModel.selectedLocation?.coordinate ?? CLLocationCoordinate2D(latitude: 42.5, longitude: 12.5)
                cameraCommand = MapCameraCommand(coordinate: coordinate, heading: 0, pitch: isPitched ? 55 : 0)
            }
            mapButton(isPitched ? "Vista piana" : "Vista 3D", icon: "view.3d") {
                isPitched.toggle()
                let coordinate = viewModel.selectedLocation?.coordinate ?? CLLocationCoordinate2D(latitude: 42.5, longitude: 12.5)
                cameraCommand = MapCameraCommand(coordinate: coordinate, pitch: isPitched ? 55 : 0)
            }
            mapButton(mapType == .standard ? "Satellite" : "Standard", icon: "square.3.layers.3d") {
                mapType = mapType == .standard ? .hybrid : .standard
            }
        }
        .padding(12)
        .safeAreaPadding(.top, 8)
    }

    @ViewBuilder private var analysisCard: some View {
        if let selected = viewModel.selectedLocation {
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Label {
                        Text(selected.name).lineLimit(3)
                    } icon: {
                        Image(systemName: "mappin.and.ellipse")
                    }
                    Spacer()
                    if viewModel.isLoadingEnvironment { ProgressView() }
                }
                if let analysis = viewModel.analysis {
                    Text("\(analysis.probability)% · \(analysis.tier.shortLabel)").font(.title3.bold())
                    if viewModel.heatmap == nil {
                        Label("Heatmap non disponibile per quest'area", systemImage: "square.slash")
                            .font(.caption).foregroundStyle(colors.inkSoft)
                    }
                    Button("Apri indicazioni", systemImage: "arrow.triangle.turn.up.right.diamond") {
                        AppleMapsNavigator().openDirections(to: selected.coordinate, name: selected.name)
                    }
                    .frame(minHeight: 44)
                }
            }
            .padding()
            .background(.regularMaterial)
            .accessibilityElement(children: .contain)
            .accessibilityLabel("Analisi per \(selected.name)")
        } else {
            Text("Tocca la mappa per scegliere una località")
                .font(.footnote.weight(.medium))
                .padding(.horizontal, 12).padding(.vertical, 8)
                .background(.regularMaterial, in: Capsule())
                .padding()
        }
    }

    private func mapButton(_ label: String, icon: String, action: @escaping () -> Void) -> some View {
        Button(action: action) { Image(systemName: icon).frame(width: 44, height: 44) }
            .buttonStyle(.borderedProminent)
            .tint(colors.forest)
            .accessibilityLabel(label)
    }
}

private struct MapCameraCommand {
    let id = UUID()
    let coordinate: CLLocationCoordinate2D
    var heading: CLLocationDirection = 0
    var pitch: CGFloat = 0
}

private struct MycoMapRepresentable: UIViewRepresentable {
    let selectedLocation: SelectedLocation?
    let heatmap: SpunHeatmapRaster?
    let mapType: MKMapType
    let cameraCommand: MapCameraCommand?
    let markerColor: UIColor
    let onSelect: (CLLocationCoordinate2D) -> Void

    func makeCoordinator() -> Coordinator { Coordinator(parent: self) }

    func makeUIView(context: Context) -> MKMapView {
        let map = MKMapView()
        map.delegate = context.coordinator
        map.showsUserLocation = true
        map.showsCompass = true
        map.pointOfInterestFilter = .excludingAll
        map.setRegion(
            MKCoordinateRegion(
                center: CLLocationCoordinate2D(latitude: 42.5, longitude: 12.5),
                span: MKCoordinateSpan(latitudeDelta: 10, longitudeDelta: 10)
            ),
            animated: false
        )
        let tap = UITapGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.didTap(_:)))
        map.addGestureRecognizer(tap)
        return map
    }

    func updateUIView(_ map: MKMapView, context: Context) {
        context.coordinator.parent = self
        map.mapType = mapType
        map.removeAnnotations(map.annotations.filter { !($0 is MKUserLocation) })
        if let selectedLocation {
            let annotation = MKPointAnnotation()
            annotation.coordinate = selectedLocation.coordinate
            annotation.title = selectedLocation.name
            map.addAnnotation(annotation)
        }
        map.removeOverlays(map.overlays)
        if let heatmap, let overlay = HeatmapImageOverlay(raster: heatmap) {
            map.addOverlay(overlay, level: .aboveRoads)
        }
        if let command = cameraCommand, command.id != context.coordinator.lastCameraCommandID {
            context.coordinator.lastCameraCommandID = command.id
            let camera = MKMapCamera(lookingAtCenter: command.coordinate, fromDistance: 18_000, pitch: command.pitch, heading: command.heading)
            map.setCamera(camera, animated: !UIAccessibility.isReduceMotionEnabled)
        }
    }

    final class Coordinator: NSObject, MKMapViewDelegate {
        var parent: MycoMapRepresentable
        var lastCameraCommandID: UUID?

        init(parent: MycoMapRepresentable) { self.parent = parent }

        @objc func didTap(_ recognizer: UITapGestureRecognizer) {
            guard recognizer.state == .ended, let map = recognizer.view as? MKMapView else { return }
            parent.onSelect(map.convert(recognizer.location(in: map), toCoordinateFrom: map))
        }

        func mapView(_ mapView: MKMapView, rendererFor overlay: any MKOverlay) -> MKOverlayRenderer {
            guard let overlay = overlay as? HeatmapImageOverlay else { return MKOverlayRenderer(overlay: overlay) }
            return HeatmapOverlayRenderer(overlay: overlay)
        }

        func mapView(_ mapView: MKMapView, viewFor annotation: any MKAnnotation) -> MKAnnotationView? {
            guard !(annotation is MKUserLocation) else { return nil }
            let view = mapView.dequeueReusableAnnotationView(withIdentifier: "selected") as? MKMarkerAnnotationView ?? MKMarkerAnnotationView(annotation: annotation, reuseIdentifier: "selected")
            view.annotation = annotation
            view.markerTintColor = parent.markerColor
            return view
        }
    }
}

final class HeatmapImageOverlay: NSObject, MKOverlay {
    let coordinate: CLLocationCoordinate2D
    let boundingMapRect: MKMapRect
    let image: CGImage

    init?(raster: SpunHeatmapRaster) {
        guard let image = HeatmapImageConverter.makeImage(raster: raster) else { return nil }
        self.image = image
        coordinate = CLLocationCoordinate2D(latitude: (raster.north + raster.south) / 2, longitude: (raster.west + raster.east) / 2)
        let northWest = MKMapPoint(CLLocationCoordinate2D(latitude: raster.north, longitude: raster.west))
        let southEast = MKMapPoint(CLLocationCoordinate2D(latitude: raster.south, longitude: raster.east))
        boundingMapRect = MKMapRect(
            x: min(northWest.x, southEast.x),
            y: min(northWest.y, southEast.y),
            width: abs(southEast.x - northWest.x),
            height: abs(southEast.y - northWest.y)
        )
        super.init()
    }

}

enum HeatmapImageConverter {
    static func makeImage(raster: SpunHeatmapRaster) -> CGImage? {
        let width = raster.width
        let height = raster.height
        guard width > 0, height > 0, raster.argbPixels.count == width * height else { return nil }
        var rgba = [UInt8](repeating: 0, count: width * height * 4)
        for index in 0..<(width * height) {
            let argb = UInt32(bitPattern: raster.argbPixels[index])
            rgba[index * 4] = UInt8((argb >> 16) & 0xFF)
            rgba[index * 4 + 1] = UInt8((argb >> 8) & 0xFF)
            rgba[index * 4 + 2] = UInt8(argb & 0xFF)
            rgba[index * 4 + 3] = UInt8((argb >> 24) & 0xFF)
        }
        guard let provider = CGDataProvider(data: Data(rgba) as CFData),
              let colorSpace = CGColorSpace(name: CGColorSpace.sRGB)
        else { return nil }
        return CGImage(
            width: width,
            height: height,
            bitsPerComponent: 8,
            bitsPerPixel: 32,
            bytesPerRow: width * 4,
            space: colorSpace,
            bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.last.rawValue),
            provider: provider,
            decode: nil,
            shouldInterpolate: true,
            intent: .defaultIntent
        )
    }
}

private final class HeatmapOverlayRenderer: MKOverlayRenderer {
    override func draw(_ mapRect: MKMapRect, zoomScale: MKZoomScale, in context: CGContext) {
        guard let overlay = overlay as? HeatmapImageOverlay else { return }
        let rect = rect(for: overlay.boundingMapRect)
        context.saveGState()
        context.interpolationQuality = .high
        context.draw(overlay.image, in: rect)
        context.restoreGState()
    }
}
