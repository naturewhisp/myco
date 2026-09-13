import CoreLocation
import MapKit

@MainActor
struct AppleMapsNavigator {
    func openDirections(to coordinate: CLLocationCoordinate2D, name: String? = nil) {
        let placemark = MKPlacemark(coordinate: coordinate)
        let destination = MKMapItem(placemark: placemark)
        destination.name = name
        MKMapItem.openMaps(with: [destination], launchOptions: [
            MKLaunchOptionsDirectionsModeKey: MKLaunchOptionsDirectionsModeDriving,
        ])
    }
}
