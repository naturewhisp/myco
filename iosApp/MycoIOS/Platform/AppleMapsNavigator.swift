import CoreLocation
import MapKit
import MycoCore

@MainActor
struct AppleMapsNavigator {
    func openDirections(to coordinate: GeoCoordinates, name: String? = nil) {
        openDirections(to: coordinate.clCoordinate, name: name)
    }

    func openDirections(to coordinate: CLLocationCoordinate2D, name: String? = nil) {
        let placemark = MKPlacemark(coordinate: coordinate)
        let destination = MKMapItem(placemark: placemark)
        destination.name = name
        MKMapItem.openMaps(with: [destination], launchOptions: [
            MKLaunchOptionsDirectionsModeKey: MKLaunchOptionsDirectionsModeDriving,
        ])
    }
}
