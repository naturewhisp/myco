import CoreLocation
import MycoCore

extension GeoCoordinates {
    var clCoordinate: CLLocationCoordinate2D {
        CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
    }

    convenience init(_ coordinate: CLLocationCoordinate2D) {
        self.init(latitude: coordinate.latitude, longitude: coordinate.longitude)
    }
}

extension SelectedLocation {
    var clCoordinate: CLLocationCoordinate2D {
        coordinate.clCoordinate
    }
}

extension MycoViewModel {
    func select(coordinate: CLLocationCoordinate2D, name: String = "Punto selezionato") {
        select(coordinate: GeoCoordinates(coordinate), name: name)
    }
}
