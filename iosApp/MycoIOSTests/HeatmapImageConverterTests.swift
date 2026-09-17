import MapKit
import XCTest
@testable import MycoIOS

final class HeatmapImageConverterTests: XCTestCase {
    func testARGBPixelsBecomeUnpremultipliedRGBAWithoutReorderingRows() throws {
        let raster = SpunHeatmapRaster(
            argbPixels: [Int32(bitPattern: 0xAA11_2233), Int32(bitPattern: 0xFF44_5566)],
            width: 2,
            height: 1,
            north: 46,
            south: 45,
            west: 10,
            east: 11
        )

        let image = try XCTUnwrap(HeatmapImageConverter.makeImage(raster: raster))
        let data = try XCTUnwrap(image.dataProvider?.data) as Data

        XCTAssertEqual(image.width, 2)
        XCTAssertEqual(image.height, 1)
        XCTAssertEqual(Array(data.prefix(8)), [0x11, 0x22, 0x33, 0xAA, 0x44, 0x55, 0x66, 0xFF])
        XCTAssertEqual(image.alphaInfo, .last)
    }

    func testAsymmetricRasterPreservesNorthSouthEastWestOrientationAndAlpha() throws {
        let raster = SpunHeatmapRaster(
            argbPixels: [
                Int32(bitPattern: 0x80FF_0000), // north-west: red, 50% alpha
                Int32(bitPattern: 0xFF00_FF00), // north-east: green
                Int32(bitPattern: 0x4000_00FF), // south-west: blue, 25% alpha
                Int32(bitPattern: 0xFFFF_FF00), // south-east: yellow
            ],
            width: 2,
            height: 2,
            north: 46,
            south: 44,
            west: 9,
            east: 12
        )

        let overlay = try XCTUnwrap(HeatmapImageOverlay(raster: raster))
        let data = try XCTUnwrap(overlay.image.dataProvider?.data) as Data

        XCTAssertEqual(
            Array(data),
            [
                0xFF, 0x00, 0x00, 0x80,
                0x00, 0xFF, 0x00, 0xFF,
                0x00, 0x00, 0xFF, 0x40,
                0xFF, 0xFF, 0x00, 0xFF,
            ]
        )
        let northWest = MKMapPoint(CLLocationCoordinate2D(latitude: raster.north, longitude: raster.west))
        let southEast = MKMapPoint(CLLocationCoordinate2D(latitude: raster.south, longitude: raster.east))
        XCTAssertEqual(overlay.boundingMapRect.minX, northWest.x, accuracy: 0.001)
        XCTAssertEqual(overlay.boundingMapRect.minY, northWest.y, accuracy: 0.001)
        XCTAssertEqual(overlay.boundingMapRect.maxX, southEast.x, accuracy: 0.001)
        XCTAssertEqual(overlay.boundingMapRect.maxY, southEast.y, accuracy: 0.001)
        XCTAssertEqual(overlay.coordinate.latitude, 45, accuracy: 0.000_001)
        XCTAssertEqual(overlay.coordinate.longitude, 10.5, accuracy: 0.000_001)
    }

    func testPaletteChangeKeepsGeographicBoundsAndPixelOrder() throws {
        let bounds = (north: 46.0, south: 44.0, west: 9.0, east: 12.0)
        let light = SpunHeatmapRaster(
            argbPixels: [1, 2, 3, 4],
            width: 2,
            height: 2,
            north: bounds.north,
            south: bounds.south,
            west: bounds.west,
            east: bounds.east
        )
        let dark = SpunHeatmapRaster(
            argbPixels: [5, 6, 7, 8],
            width: 2,
            height: 2,
            north: bounds.north,
            south: bounds.south,
            west: bounds.west,
            east: bounds.east
        )

        let lightOverlay = try XCTUnwrap(HeatmapImageOverlay(raster: light))
        let darkOverlay = try XCTUnwrap(HeatmapImageOverlay(raster: dark))

        XCTAssertEqual(lightOverlay.boundingMapRect.origin.x, darkOverlay.boundingMapRect.origin.x, accuracy: 0.001)
        XCTAssertEqual(lightOverlay.boundingMapRect.origin.y, darkOverlay.boundingMapRect.origin.y, accuracy: 0.001)
        XCTAssertEqual(lightOverlay.boundingMapRect.size.width, darkOverlay.boundingMapRect.size.width, accuracy: 0.001)
        XCTAssertEqual(lightOverlay.boundingMapRect.size.height, darkOverlay.boundingMapRect.size.height, accuracy: 0.001)
        XCTAssertEqual(lightOverlay.coordinate.latitude, darkOverlay.coordinate.latitude)
        XCTAssertEqual(lightOverlay.coordinate.longitude, darkOverlay.coordinate.longitude)
    }
}
