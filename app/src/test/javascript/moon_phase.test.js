const { test } = require('node:test');
const assert = require('node:assert');
const { getMoonPhase } = require('../../main/assets/js/logic.js');

const KNOWN_NEW_MOON = new Date('2000-01-06T18:14:00Z');
const MS_PER_DAY = 1000 * 60 * 60 * 24;

function getDateAfterDays(days) {
    return new Date(KNOWN_NEW_MOON.getTime() + days * MS_PER_DAY);
}

test('getMoonPhase - Luna Nuova', () => {
    // 0 <= pos < 1.845
    const date = getDateAfterDays(1.0);
    const phase = getMoonPhase(date);
    assert.strictEqual(phase.text, 'Luna Nuova');
    assert.strictEqual(phase.emoji, '🌑');
    assert.strictEqual(phase.favorable, true);
});

test('getMoonPhase - Crescente', () => {
    // 1.845 <= pos < 5.535
    const date = getDateAfterDays(3.0);
    const phase = getMoonPhase(date);
    assert.strictEqual(phase.text, 'Crescente');
    assert.strictEqual(phase.emoji, '🌒');
    assert.strictEqual(phase.favorable, true);
});

test('getMoonPhase - Primo Quarto', () => {
    // 5.535 <= pos < 9.225
    const date = getDateAfterDays(7.0);
    const phase = getMoonPhase(date);
    assert.strictEqual(phase.text, 'Primo Quarto');
    assert.strictEqual(phase.emoji, '🌓');
    assert.strictEqual(phase.favorable, false);
});

test('getMoonPhase - Gibbosa Crescente', () => {
    // 9.225 <= pos < 12.915
    const date = getDateAfterDays(11.0);
    const phase = getMoonPhase(date);
    assert.strictEqual(phase.text, 'Gibbosa Crescente');
    assert.strictEqual(phase.emoji, '🌔');
    assert.strictEqual(phase.favorable, false);
});

test('getMoonPhase - Luna Piena', () => {
    // 12.915 <= pos < 16.605
    const date = getDateAfterDays(14.7);
    const phase = getMoonPhase(date);
    assert.strictEqual(phase.text, 'Luna Piena');
    assert.strictEqual(phase.emoji, '🌕');
    assert.strictEqual(phase.favorable, false);
});

test('getMoonPhase - Gibbosa Calante', () => {
    // 16.605 <= pos < 20.295
    const date = getDateAfterDays(18.0);
    const phase = getMoonPhase(date);
    assert.strictEqual(phase.text, 'Gibbosa Calante');
    assert.strictEqual(phase.emoji, '🌖');
    assert.strictEqual(phase.favorable, false);
});

test('getMoonPhase - Ultimo Quarto', () => {
    // 20.295 <= pos < 23.985
    const date = getDateAfterDays(22.0);
    const phase = getMoonPhase(date);
    assert.strictEqual(phase.text, 'Ultimo Quarto');
    assert.strictEqual(phase.emoji, '🌗');
    assert.strictEqual(phase.favorable, false);
});

test('getMoonPhase - Calante', () => {
    // >= 23.985
    const date = getDateAfterDays(26.0);
    const phase = getMoonPhase(date);
    assert.strictEqual(phase.text, 'Calante');
    assert.strictEqual(phase.emoji, '🌘');
    assert.strictEqual(phase.favorable, false);
});

test('getMoonPhase - Default Date', () => {
    const phase = getMoonPhase();
    assert.ok(phase.text);
    assert.ok(phase.emoji);
    assert.strictEqual(typeof phase.favorable, 'boolean');
});

test('getMoonPhase - Next Cycle', () => {
    // Test a date in the next lunar cycle to ensure modulo arithmetic works
    const LUNAR_CYCLE_DAYS = 29.53058867;
    // 1.0 day into the second cycle
    const date = getDateAfterDays(LUNAR_CYCLE_DAYS + 1.0);
    const phase = getMoonPhase(date);
    assert.strictEqual(phase.text, 'Luna Nuova');
    assert.strictEqual(phase.emoji, '🌑');
    assert.strictEqual(phase.favorable, true);
});
