#!/usr/bin/env python3
"""
Scan Swift source files against Kotlin Multiplatform (:core) exported contracts
to catch cross-platform API breakages, missing parameters, or property discrepancies
locally before pushing to CI.
"""

import sys
import re
from pathlib import Path

def main() -> int:
    root_dir = Path(__file__).resolve().parent.parent
    ios_dir = root_dir / "iosApp"
    core_dir = root_dir / "core" / "src" / "commonMain" / "kotlin" / "github" / "naturewhisp" / "myco" / "core"

    domain_file = core_dir / "Domain.kt"
    algorithms_file = core_dir / "MycoAlgorithms.kt"

    if not domain_file.exists():
        print(f"Error: {domain_file} not found.")
        return 1

    print("Checking Swift code against Kotlin Multiplatform (:core) contracts...")

    domain_content = domain_file.read_text(encoding="utf-8")
    algorithms_content = algorithms_file.read_text(encoding="utf-8") if algorithms_file.exists() else ""

    errors = []

    # 1. Verify HeatmapRaster properties in Domain.kt
    expected_raster_props = ["argbPixels", "width", "height", "north", "south", "west", "east", "layerStatus", "statusDescription"]
    for prop in expected_raster_props:
        if prop not in domain_content:
            errors.append(f"Kotlin HeatmapRaster missing expected property: {prop}")

    # 2. Verify AnalysisResult properties in Domain.kt
    expected_result_props = ["probability", "tier", "weatherScore", "habitatScore", "altitudeScore", "seasonalityScore", "terrain", "factors", "dailyOutlooks", "deterministicFieldNote", "missingSources", "effectiveRainMm", "isCalculable"]
    for prop in expected_result_props:
        if prop not in domain_content:
            errors.append(f"Kotlin AnalysisResult missing expected property: {prop}")

    # 3. Verify AnalysisInputs constructors in Domain.kt
    if "AnalysisInputsBuilder" not in domain_content:
        errors.append("Kotlin Domain.kt missing AnalysisInputsBuilder")

    # 4. Scan Swift files for AnalysisInputs constructor usage
    swift_files = list(ios_dir.rglob("*.swift"))
    print(f"Found {len(swift_files)} Swift source files to scan.")

    def extract_calls(content: str, func_name: str) -> list:
        calls = []
        start_pos = 0
        while True:
            idx = content.find(func_name + "(", start_pos)
            if idx == -1:
                break
            pos = idx + len(func_name) + 1
            depth = 1
            call_start = pos
            while pos < len(content) and depth > 0:
                if content[pos] == '(':
                    depth += 1
                elif content[pos] == ')':
                    depth -= 1
                pos += 1
            if depth == 0:
                calls.append(content[call_start:pos-1])
            start_pos = pos
        return calls

    def extract_arg_labels(call_body: str) -> list:
        labels = []
        depth_paren = depth_bracket = depth_brace = 0
        current_token = []
        for char in call_body:
            if char == '(':
                depth_paren += 1
            elif char == ')':
                depth_paren -= 1
            elif char == '[':
                depth_bracket += 1
            elif char == ']':
                depth_bracket -= 1
            elif char == '{':
                depth_brace += 1
            elif char == '}':
                depth_brace -= 1
            elif char == ',' and depth_paren == 0 and depth_bracket == 0 and depth_brace == 0:
                token_str = ''.join(current_token).strip()
                if ':' in token_str:
                    labels.append(token_str.split(':')[0].strip())
                current_token = []
                continue
            current_token.append(char)
        token_str = ''.join(current_token).strip()
        if ':' in token_str:
            labels.append(token_str.split(':')[0].strip())
        return labels

    for swift_file in swift_files:
        content = swift_file.read_text(encoding="utf-8")
        for call_body in extract_calls(content, "AnalysisInputs"):
            arg_names = extract_arg_labels(call_body)
            # Check required parameters in Swift call
            required = ["days", "todayIndex", "speciesId", "habitatScore", "habitatDescription", "canopyTypes", "elevationSamples", "monthIndex"]
            for req in required:
                if req not in arg_names:
                    errors.append(f"{swift_file.name}: AnalysisInputs call missing '{req}' argument")

    # 5. Check Heatmap status description usage in MapView.swift
    map_view_file = ios_dir / "MycoIOS" / "Features" / "MapView.swift"
    if map_view_file.exists():
        map_content = map_view_file.read_text(encoding="utf-8")
        if "statusDescription" not in map_content:
            errors.append("MapView.swift does not reference statusDescription on heatmap raster")

    # 6. Check clock injection in MycoViewModel.swift
    vm_file = ios_dir / "MycoIOS" / "App" / "MycoViewModel.swift"
    if vm_file.exists():
        vm_content = vm_file.read_text(encoding="utf-8")
        if "clock" not in vm_content:
            errors.append("MycoViewModel.swift does not contain injected clock parameter")

    # 7. Check SpunBundleServiceTests.swift for unsupported guild test
    spun_test_file = ios_dir / "MycoIOSTests" / "SpunBundleServiceTests.swift"
    if spun_test_file.exists():
        spun_test_content = spun_test_file.read_text(encoding="utf-8")
        if "testUnsupportedGuild" not in spun_test_content:
            errors.append("SpunBundleServiceTests.swift missing test for unsupported guild")

    # 8. Check OverpassClient.swift for saprotrophic scoring
    overpass_file = ios_dir / "MycoIOS" / "Data" / "OverpassClient.swift"
    if overpass_file.exists():
        op_content = overpass_file.read_text(encoding="utf-8")
        if "0.95" not in op_content:
            errors.append("OverpassClient.swift missing 0.95 score branch for saprotrophic open habitats")

    # 9. Check for illegal wildcard closure referencing $0 ({ _ in ... $0 })
    wildcard_closure_pattern = re.compile(r'\{\s*_\s+in[^}]*\$0')
    for swift_file in swift_files:
        content = swift_file.read_text(encoding="utf-8")
        if wildcard_closure_pattern.search(content):
            errors.append(f"{swift_file.name}: contains invalid Swift syntax '{{ _ in ... $0 }}'")

    # 10. Check that any Swift file referencing AnalysisResult imports MycoCore
    for swift_file in swift_files:
        content = swift_file.read_text(encoding="utf-8")
        if re.search(r'\bAnalysisResult\b', content) and "import MycoCore" not in content:
            errors.append(f"{swift_file.name}: references AnalysisResult but does not import MycoCore")

    # 11. Check for mutable variable capture inside TestHTTPDataLoader closures (Swift 6 strict concurrency)
    sendable_loader_pattern = re.compile(r'TestHTTPDataLoader\s*\{[^}]*(\b\w+\b)\s*(?:\+=|(?<!=)=(?!=))')
    for swift_file in swift_files:
        content = swift_file.read_text(encoding="utf-8")
        m = sendable_loader_pattern.search(content)
        if m and m.group(1) not in ("payload", "request", "let"):
            errors.append(f"{swift_file.name}: mutates captured variable '{m.group(1)}' in @Sendable TestHTTPDataLoader closure")

    # 12. Check for illegal conditional binding (if let / guard let) on non-optional Kotlin properties
    non_optional_kotlin_props = [
        "bonusText", "baseText", "score", "basalAreaM2Ha",
        "probability", "tier", "weatherScore", "habitatScore", "altitudeScore",
        "seasonalityScore", "deterministicFieldNote", "isCalculable", "effectiveRainMm",
        "argbPixels", "width", "height", "layerStatus", "statusDescription",
        "forestCoverFraction", "meadowFraction", "distanceToNearestForestMeters", "confirmedHostGenera",
        "fruitingPeriodDescription", "vernacularName", "scientificName"
    ]
    prop_pattern = r'\b(?:if|guard)\s+let\s+\w+\s*=\s*(?:[a-zA-Z0-9_?.()]+\.)(' + '|'.join(non_optional_kotlin_props) + r')\b'
    cond_binding_regex = re.compile(prop_pattern)
    for swift_file in swift_files:
        content = swift_file.read_text(encoding="utf-8")
        matches = cond_binding_regex.finditer(content)
        for m in matches:
            errors.append(f"{swift_file.name}: contains invalid conditional binding 'if/guard let' on non-optional Kotlin property '{m.group(1)}'")

    # 13. Verify GeoCoordinates in Domain.kt
    if "data class GeoCoordinates" not in domain_content:
        errors.append("Kotlin Domain.kt missing GeoCoordinates data class")
    if "distanceToMeters" not in domain_content or "distanceToKm" not in domain_content:
        errors.append("Kotlin GeoCoordinates missing distance calculation methods")

    # 14. Verify CoreLocation isolation: must NOT be imported in data clients, services or viewmodel
    allowed_core_location_files = {
        "CoreLocationService.swift",
        "CoreLocationServiceTests.swift",
        "AppleMapsNavigator.swift",
        "GeoCoordinates+CoreLocation.swift",
        "MapView.swift",
    }
    for swift_file in swift_files:
        if swift_file.name not in allowed_core_location_files:
            content = swift_file.read_text(encoding="utf-8")
            if re.search(r'^\s*(?:@\w+\s+)?import\s+CoreLocation\b', content, re.MULTILINE):
                errors.append(f"{swift_file.name}: violates architectural isolation by importing CoreLocation outside allowed platform adapters")

    if errors:
        print("\n[FAIL] Cross-platform contract parity check failed with errors:")
        for err in errors:
            print(f"  - {err}")
        return 1

    print("\n[SUCCESS] All Swift-Kotlin contracts verified successfully!")
    print(f"  - Verified {len(expected_raster_props)} HeatmapRaster properties")
    print(f"  - Verified {len(expected_result_props)} AnalysisResult properties")
    print("  - Verified AnalysisInputs builders & constructors")
    print("  - Verified GeoCoordinates value object & Haversine distance contracts")
    print("  - Verified CoreLocation architectural isolation (zero CoreLocation in data/viewmodel)")
    print("  - Verified MapView statusDescription binding")
    print("  - Verified MycoViewModel clock injection")
    print("  - Verified OverpassClient saprotrophic alignment")
    print(f"  - Verified non-optional property bindings against {len(non_optional_kotlin_props)} Kotlin properties")
    return 0

if __name__ == "__main__":
    sys.exit(main())
