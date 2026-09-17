import Foundation
@testable import MycoIOS

final class TestHTTPDataLoader: HTTPDataLoading, @unchecked Sendable {
    private let lock = NSLock()
    private let handler: @Sendable (URLRequest) throws -> (Data, URLResponse)
    private var recordedRequests: [URLRequest] = []

    init(handler: @escaping @Sendable (URLRequest) throws -> (Data, URLResponse)) {
        self.handler = handler
    }

    func data(for request: URLRequest) async throws -> (Data, URLResponse) {
        lock.withLock {
            recordedRequests.append(request)
        }
        try Task.checkCancellation()
        return try handler(request)
    }

    var requests: [URLRequest] {
        lock.withLock { recordedRequests }
    }
}

func httpResponse(for request: URLRequest, statusCode: Int = 200) -> HTTPURLResponse {
    HTTPURLResponse(
        url: request.url!,
        statusCode: statusCode,
        httpVersion: nil,
        headerFields: nil
    )!
}
