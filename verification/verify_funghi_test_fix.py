import os
import json
from playwright.sync_api import sync_playwright

def run(playwright):
    browser = playwright.chromium.launch(headless=True)
    page = browser.new_page()

    # --- Mocks ---

    # 1. Nominatim
    page.route("**/nominatim.openstreetmap.org/**", lambda route: route.fulfill(
        status=200,
        content_type="application/json",
        body='[{"lat": "45.0", "lon": "10.0", "display_name": "Test Location"}]'
    ))

    # 2. Overpass
    page.route("**/overpass-api.de/**", lambda route: route.fulfill(
        status=200,
        content_type="application/json",
        body='{"elements": [{"type": "way", "id": 1, "tags": {"natural": "wood"}}]}'
    ))
    # Route other potential endpoints too
    page.route("**/overpass.kumi.systems/**", lambda route: route.fulfill(status=200, body='{"elements": []}'))
    page.route("**/overpass.openstreetmap.fr/**", lambda route: route.fulfill(status=200, body='{"elements": []}'))

    # 3. Open-Meteo
    # Generate 25 days of data
    hourly_time = []
    daily_time = []

    # Simple data generation
    for d in range(25):
        day_str = f"2023-10-{d+1:02d}"
        daily_time.append(day_str)
        for h in range(24):
            hourly_time.append(f"{day_str}T{h:02d}:00")

    mock_weather_data = {
        "elevation": 500,
        "hourly": {
            "time": hourly_time,
            "temperature_2m": [20] * len(hourly_time),
            "precipitation": [5] * len(hourly_time), # Some rain every hour -> lots of rain
            "relativehumidity_2m": [60] * len(hourly_time)
        },
        "daily": {
            "time": daily_time,
            "weathercode": [1] * len(daily_time)
        }
    }

    page.route("**/api.open-meteo.com/**", lambda route: route.fulfill(
        status=200,
        content_type="application/json",
        body=json.dumps(mock_weather_data)
    ))

    # --- Load Page ---

    # We need absolute path for file://
    # Assuming we run from repo root
    file_path = os.path.abspath("app/src/main/assets/funghi.html")
    page.goto(f"file://{file_path}")

    # --- Inject Leaflet Mock ---
    # We do this before interacting, to ensure initializeMap finds L.
    page.evaluate("""
        window.L = {
            map: function(id) {
                console.log('Mock L.map called');
                return {
                    setView: function() { return this; },
                    on: function(event, callback) {
                        if (event === 'click') {
                            window.mockMapClick = callback;
                        }
                        return this;
                    },
                    addLayer: function() { return this; }
                };
            },
            tileLayer: function() {
                return { addTo: function() { return this; } };
            },
            marker: function() {
                return {
                    addTo: function() { return this; },
                    setLatLng: function() { return this; }
                };
            }
        };
    """)

    # --- Interact ---

    # Fill search
    page.fill("#location-input", "Test Location")

    # Click search button
    with page.expect_response("**/nominatim.openstreetmap.org/**"):
        page.click("button[type='submit']")

    # Expect map container to show
    page.wait_for_selector("#map-container:not(.hidden)")

    # Trigger map click via our mock
    # initializeMap calls map.on('click', onMapClick).
    # Our mock captures this callback in window.mockMapClick
    page.evaluate("""
        if (window.mockMapClick) {
            window.mockMapClick({ latlng: { lat: 45.0, lng: 10.0 } });
        } else {
            console.error("Mock map click callback not registered!");
        }
    """)

    # Wait for results
    # This involves fetching overpass and weather data
    page.wait_for_selector("#results-container:not(.hidden)")

    # Take screenshot of results
    page.screenshot(path="verification/verification_test_fix.png", full_page=True)

    browser.close()

with sync_playwright() as p:
    run(p)
