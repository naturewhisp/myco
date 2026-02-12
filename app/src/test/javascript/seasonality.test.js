const { test } = require('node:test');
const assert = require('node:assert');
const { calculateSeasonalityScore } = require('../../main/assets/js/logic.js');

test('calculateSeasonalityScore - PEAK season (September, October)', () => {
    // Sept = 8
    const sept = calculateSeasonalityScore(8);
    assert.strictEqual(sept.score, 1.0);
    assert.match(sept.text, /Settembre/);
    assert.match(sept.text, /Picco della stagione/);

    // Oct = 9
    const oct = calculateSeasonalityScore(9);
    assert.strictEqual(oct.score, 1.0);
    assert.match(oct.text, /Ottobre/);
    assert.match(oct.text, /Picco della stagione/);
});

test('calculateSeasonalityScore - SPRING season (May, June)', () => {
    // May = 4
    const may = calculateSeasonalityScore(4);
    assert.strictEqual(may.score, 0.9);
    assert.match(may.text, /Maggio/);
    assert.match(may.text, /Buona stagione primaverile/);

    // June = 5
    const june = calculateSeasonalityScore(5);
    assert.strictEqual(june.score, 0.9);
    assert.match(june.text, /Giugno/);
    assert.match(june.text, /Buona stagione primaverile/);
});

test('calculateSeasonalityScore - LATE season (November)', () => {
    // Nov = 10
    const nov = calculateSeasonalityScore(10);
    assert.strictEqual(nov.score, 0.7);
    assert.match(nov.text, /Novembre/);
    assert.match(nov.text, /Fine stagione/);
});

test('calculateSeasonalityScore - SUMMER season (July, August)', () => {
    // July = 6
    const july = calculateSeasonalityScore(6);
    assert.strictEqual(july.score, 0.5);
    assert.match(july.text, /Luglio/);
    assert.match(july.text, /Estivo/);

    // Aug = 7
    const aug = calculateSeasonalityScore(7);
    assert.strictEqual(aug.score, 0.5);
    assert.match(aug.text, /Agosto/);
    assert.match(aug.text, /Estivo/);
});

test('calculateSeasonalityScore - EARLY season (April)', () => {
    // April = 3
    const april = calculateSeasonalityScore(3);
    assert.strictEqual(april.score, 0.4);
    assert.match(april.text, /Aprile/);
    assert.match(april.text, /Inizio stagione/);
});

test('calculateSeasonalityScore - OFF_SEASON', () => {
    // Jan = 0
    const jan = calculateSeasonalityScore(0);
    assert.strictEqual(jan.score, 0.1);
    assert.match(jan.text, /Gennaio/);
    assert.match(jan.text, /Fuori stagione/);

    // Feb = 1
    const feb = calculateSeasonalityScore(1);
    assert.strictEqual(feb.score, 0.1);
    assert.match(feb.text, /Febbraio/);
    assert.match(feb.text, /Fuori stagione/);

    // Mar = 2
    const mar = calculateSeasonalityScore(2);
    assert.strictEqual(mar.score, 0.1);
    assert.match(mar.text, /Marzo/);
    assert.match(mar.text, /Fuori stagione/);

    // Dec = 11
    const dec = calculateSeasonalityScore(11);
    assert.strictEqual(dec.score, 0.1);
    assert.match(dec.text, /Dicembre/);
    assert.match(dec.text, /Fuori stagione/);
});

test('calculateSeasonalityScore - Language fallback', () => {
    // Test with 'en' which is not defined, should fall back to 'it'
    const res = calculateSeasonalityScore(8, 'en');
    assert.strictEqual(res.score, 1.0);
    assert.match(res.text, /Settembre/);
    assert.match(res.text, /Picco della stagione/);
});
