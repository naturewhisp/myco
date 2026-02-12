const { test } = require('node:test');
const assert = require('node:assert');
const { getWeatherIconSvg, WEATHER_ICONS } = require('../../main/assets/js/logic.js');

test('getWeatherIconSvg - null code', () => {
  assert.strictEqual(getWeatherIconSvg(null), '');
});

test('getWeatherIconSvg - SUN (0)', () => {
  assert.strictEqual(getWeatherIconSvg(0), WEATHER_ICONS.SUN);
});

test('getWeatherIconSvg - CLOUD (1-3)', () => {
  assert.strictEqual(getWeatherIconSvg(1), WEATHER_ICONS.CLOUD);
  assert.strictEqual(getWeatherIconSvg(2), WEATHER_ICONS.CLOUD);
  assert.strictEqual(getWeatherIconSvg(3), WEATHER_ICONS.CLOUD);
});

test('getWeatherIconSvg - RAIN (51-67, 80-82)', () => {
  assert.strictEqual(getWeatherIconSvg(51), WEATHER_ICONS.RAIN);
  assert.strictEqual(getWeatherIconSvg(60), WEATHER_ICONS.RAIN);
  assert.strictEqual(getWeatherIconSvg(67), WEATHER_ICONS.RAIN);
  assert.strictEqual(getWeatherIconSvg(80), WEATHER_ICONS.RAIN);
  assert.strictEqual(getWeatherIconSvg(81), WEATHER_ICONS.RAIN);
  assert.strictEqual(getWeatherIconSvg(82), WEATHER_ICONS.RAIN);
});

test('getWeatherIconSvg - STORM (95-99)', () => {
  assert.strictEqual(getWeatherIconSvg(95), WEATHER_ICONS.STORM);
  assert.strictEqual(getWeatherIconSvg(97), WEATHER_ICONS.STORM);
  assert.strictEqual(getWeatherIconSvg(99), WEATHER_ICONS.STORM);
});

test('getWeatherIconSvg - Default (unknown code)', () => {
  assert.strictEqual(getWeatherIconSvg(4), WEATHER_ICONS.CLOUD);
  assert.strictEqual(getWeatherIconSvg(100), WEATHER_ICONS.CLOUD);
  assert.strictEqual(getWeatherIconSvg(-1), WEATHER_ICONS.CLOUD);
});
