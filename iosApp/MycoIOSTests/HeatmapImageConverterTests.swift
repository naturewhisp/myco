import MycoCore
import XCTest
@testable import MycoIOS

final class HeatmapImageConverterTests: XCTestCase {
    func testARGBPixelsBecomeUnpremultipliedRGBAWithoutReorderingRows() throws {
        let pixels = KotlinIntArray(size: 2)
        pixels.set(index: 0, value: Int32(bitPattern: 0xAA11_2233))
        pixels.set(index: 1, value: Int32(bitPattern: 0xFF44_5566))
        let raster = HeatmapRaster(
            argbPixels: pixels,
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
}
