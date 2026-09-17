import Foundation

protocol HTTPDataLoading: Sendable {
    func data(for request: URLRequest) async throws -> (Data, URLResponse)
}

extension URLSession: HTTPDataLoading {
    func data(for request: URLRequest) async throws -> (Data, URLResponse) {
        try await data(for: request, delegate: nil)
    }
}

enum APIClientError: LocalizedError, Sendable {
    case invalidResponse
    case unacceptableStatus(code: Int, body: Data)
    case decoding(String)

    var errorDescription: String? {
        switch self {
        case .invalidResponse:
            "The server returned an invalid response."
        case let .unacceptableStatus(code, _):
            "The server returned HTTP status \(code)."
        case let .decoding(message):
            "Could not decode the server response: \(message)"
        }
    }
}

struct APIClient: Sendable {
    private let loader: any HTTPDataLoading
    private let decoder: JSONDecoder

    init(loader: any HTTPDataLoading = URLSession.shared, decoder: JSONDecoder = JSONDecoder()) {
        self.loader = loader
        self.decoder = decoder
    }

    func send(_ request: URLRequest) async throws -> Data {
        let (data, response) = try await loader.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else {
            throw APIClientError.invalidResponse
        }
        guard (200 ..< 300).contains(httpResponse.statusCode) else {
            throw APIClientError.unacceptableStatus(code: httpResponse.statusCode, body: data)
        }
        return data
    }

    func decode<Response: Decodable>(_ type: Response.Type, from request: URLRequest) async throws -> Response {
        let data = try await send(request)
        do {
            return try decoder.decode(type, from: data)
        } catch {
            throw APIClientError.decoding(error.localizedDescription)
        }
    }
}
