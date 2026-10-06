"""Regression tests for the local KMP/Swift argument-label gate."""
import tempfile
import unittest
from pathlib import Path

from check_swift_contracts import check_exported_labels


class ExportedLabelsTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.core = Path(self.directory.name)
        self.core.joinpath('MycoAnalysisEngine.kt').write_text(
            'class MycoAnalysisEngine {\n fun analyze(input: AnalysisInputs): AnalysisResult = TODO()\n}',
            encoding='utf-8',
        )
        self.core.joinpath('MycoAlgorithms.kt').write_text(
            'object MycoAlgorithms {\n fun evaluateHabitat(evidence: Evidence, species: Species, spunEcmRichness: Double?): Result = TODO()\n'
            ' fun extractHabitatEvidence(elements: List<Element>, latitude: Double, longitude: Double, radiusMeters: Double): Evidence = TODO()\n}',
            encoding='utf-8',
        )
        self.core.joinpath('WeatherAggregation.kt').write_text(
            'object WeatherAggregation {\n fun aggregate(hours: List<Hour>, expectedDays: List<Day>, dailyCodes: List<Code>): List<Day> = TODO()\n}\n'
            'data class WeatherHour(val timestamp: String, val temperature: Double?)\n'
            'data class ExpectedDayHours(val dateIso: String, val hours: Int)\n'
            'data class DailyWeatherCode(val dateIso: String, val code: Int?)',
            encoding='utf-8',
        )
        self.core.joinpath('Domain.kt').write_text(
            'data class AnalysisInputs(val days: List<Day>, val todayIndex: Int)\n'
            'data class OsmHabitatElement(val tags: Map<String, String>, val surfaces: List<Surface>)\n'
            'data class OsmSurface(val vertices: List<Point>, val inner: Boolean)',
            encoding='utf-8',
        )

    def check(self, swift):
        return check_exported_labels(self.core, {Path('Fixture.swift'): swift})

    def test_accepts_multiline_nested_calls_and_generic_constructor(self):
        self.assertEqual([], self.check('''
analysisEngine.analyze (input: AnalysisInputs(days: makeDays(a: 1, b: 2), todayIndex: 0))
OsmHabitatElement(tags: ["key": "value"], surfaces: makeSurfaces { point in point })
'''))

    def test_rejects_original_parameter_rename(self):
        path = self.core / 'MycoAnalysisEngine.kt'
        path.write_text(path.read_text().replace('input:', 'rawInput:'), encoding='utf-8')
        self.assertIn('Fixture.swift:1', self.check('analysisEngine.analyze(input: value)')[0])

    def test_private_or_commented_signature_does_not_satisfy_public_contract(self):
        path = self.core / 'MycoAnalysisEngine.kt'
        path.write_text('// fun analyze(input: AnalysisInputs)\nprivate fun analyze(input: AnalysisInputs) = TODO()', encoding='utf-8')
        self.assertTrue(self.check('analysisEngine.analyze(input: value)'))

    def test_rejects_missing_extra_and_reordered_labels(self):
        for call in ('ExpectedDayHours(dateIso: date)',
                     'ExpectedDayHours(dateIso: date, hours: 24, extra: 1)',
                     'ExpectedDayHours(hours: 24, dateIso: date)'):
            with self.subTest(call=call):
                self.assertTrue(self.check(call))

    def test_ignores_comments_and_string_literal_calls(self):
        self.assertEqual([], self.check('''
// analysisEngine.analyze(wrong: x)
/* ExpectedDayHours(wrong: x) */
let example = "analysisEngine.analyze(wrong: x)"
analysisEngine.analyze(input: value)
'''))

    def test_rejects_unclosed_call(self):
        self.assertIn('Unclosed argument list', self.check('analysisEngine.analyze(input: value')[0])


if __name__ == '__main__':
    unittest.main()
