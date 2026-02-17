const { test } = require('node:test');
const assert = require('node:assert');
const { calculateAltitudeScore, fetchOverpassData } = require('../../main/assets/js/logic.js');

test('calculateAltitudeScore - elevation < 200', () => {
  const result = calculateAltitudeScore(100);
  assert.strictEqual(result.score, 0.7);
  assert.match(result.text, /Bassa, impatto moderato/);
  assert.match(result.text, /100m/);
});

test('calculateAltitudeScore - elevation 200', () => {
  // elevation < 200 is false, next is elevation < 400 which is true
  const result = calculateAltitudeScore(200);
  assert.strictEqual(result.score, 0.9);
  assert.match(result.text, /Collinare, favorevole/);
  assert.match(result.text, /200m/);
});

test('calculateAltitudeScore - elevation 399', () => {
  const result = calculateAltitudeScore(399);
  assert.strictEqual(result.score, 0.9);
  assert.match(result.text, /Collinare, favorevole/);
});

test('calculateAltitudeScore - elevation 400', () => {
  // elevation < 400 is false, next is elevation <= 1400 which is true
  const result = calculateAltitudeScore(400);
  assert.strictEqual(result.score, 1.0);
  assert.match(result.text, /Ideale/);
});

test('calculateAltitudeScore - elevation 1400', () => {
  const result = calculateAltitudeScore(1400);
  assert.strictEqual(result.score, 1.0);
  assert.match(result.text, /Ideale/);
});

test('calculateAltitudeScore - elevation 1401', () => {
  // elevation <= 1400 is false, next is elevation <= 1800 which is true
  const result = calculateAltitudeScore(1401);
  assert.strictEqual(result.score, 0.9);
  assert.match(result.text, /Montana, buona/);
});

test('calculateAltitudeScore - elevation 1800', () => {
  const result = calculateAltitudeScore(1800);
  assert.strictEqual(result.score, 0.9);
  assert.match(result.text, /Montana, buona/);
});

test('calculateAltitudeScore - elevation 1801', () => {
  const result = calculateAltitudeScore(1801);
  assert.strictEqual(result.score, 0.6);
  assert.match(result.text, /Elevata, meno favorevole/);
});

test('calculateAltitudeScore - rounding elevation', () => {
  const result = calculateAltitudeScore(1234.56);
  assert.match(result.text, /1235m/);
});

test('calculateAltitudeScore - language fallback', () => {
    const result = calculateAltitudeScore(1000, 'non-existent');
    assert.strictEqual(result.score, 1.0);
    assert.match(result.text, /Altitudine: 1000m \(Ideale\)/);
});

test('fetchOverpassData - success on first endpoint', async () => {
    const originalFetch = global.fetch;
    global.fetch = async (url) => {
        return {
            ok: true,
            json: async () => ({ elements: [{ id: 1 }] })
        };
    };

    try {
        const endpoints = ['http://endpoint1', 'http://endpoint2'];
        const result = await fetchOverpassData(endpoints, 'query');
        assert.deepStrictEqual(result, { elements: [{ id: 1 }] });
    } finally {
        global.fetch = originalFetch;
    }
});

test('fetchOverpassData - success on second endpoint', async () => {
    const originalFetch = global.fetch;
    let callCount = 0;
    global.fetch = async (url) => {
        callCount++;
        if (callCount === 1) {
            throw new Error('Network error');
        }
        return {
            ok: true,
            json: async () => ({ elements: [{ id: 2 }] })
        };
    };

    try {
        const endpoints = ['http://endpoint1', 'http://endpoint2'];
        const result = await fetchOverpassData(endpoints, 'query');
        assert.deepStrictEqual(result, { elements: [{ id: 2 }] });
    } finally {
        global.fetch = originalFetch;
    }
});

test('fetchOverpassData - failure on all endpoints', async () => {
    const originalFetch = global.fetch;
    global.fetch = async (url) => {
        throw new Error('Network error');
    };

    try {
        const endpoints = ['http://endpoint1', 'http://endpoint2'];
        const result = await fetchOverpassData(endpoints, 'query');
        assert.strictEqual(result, null);
    } finally {
        global.fetch = originalFetch;
    }
});

test('fetchOverpassData - response not ok', async () => {
    const originalFetch = global.fetch;
    global.fetch = async (url) => {
        return {
            ok: false,
            statusText: 'Not Found'
        };
    };

    try {
        const endpoints = ['http://endpoint1'];
        const result = await fetchOverpassData(endpoints, 'query');
        assert.strictEqual(result, null);
    } finally {
        global.fetch = originalFetch;
    }
});
