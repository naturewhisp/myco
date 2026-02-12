const { test } = require('node:test');
const assert = require('node:assert');
const { calculateWeatherScore, getRainStatus, getTempStatus, WEATHER_THRESHOLDS } = require('../../main/assets/js/logic.js');

test('getRainStatus', () => {
    assert.strictEqual(getRainStatus(41).label, "Ottimale");
    assert.strictEqual(getRainStatus(40.1).label, "Ottimale");
    assert.strictEqual(getRainStatus(40).label, "Molto buona"); // > 40 check
    assert.strictEqual(getRainStatus(26).label, "Molto buona");
    assert.strictEqual(getRainStatus(25).label, "Buona");
    assert.strictEqual(getRainStatus(16).label, "Buona");
    assert.strictEqual(getRainStatus(15).label, "Sufficiente");
    assert.strictEqual(getRainStatus(6).label, "Sufficiente");
    assert.strictEqual(getRainStatus(5).label, "Scarsa");
    assert.strictEqual(getRainStatus(0).label, "Scarsa");
});

test('getTempStatus', () => {
    assert.strictEqual(getTempStatus(18).label, "Ideale");
    assert.strictEqual(getTempStatus(14).label, "Ideale");
    assert.strictEqual(getTempStatus(22).label, "Ideale");

    assert.strictEqual(getTempStatus(13).label, "Favorevole");
    assert.strictEqual(getTempStatus(10).label, "Favorevole");
    assert.strictEqual(getTempStatus(23).label, "Favorevole");
    assert.strictEqual(getTempStatus(24.9).label, "Favorevole");

    assert.strictEqual(getTempStatus(9.9).label, "Troppo freddo");
    assert.strictEqual(getTempStatus(25).label, "Troppo caldo");
});

test('calculateWeatherScore - Optimal Conditions', () => {
    // dayIndex = 10.
    // Rain window: 0 to 8. (indexes 0-7)
    // Temp window: 5 to 10. (indexes 5-9)
    // Humidity window: 7 to 11. (indexes 7-10)

    // Construct data
    const allData = Array(20).fill({ totalPrecip: 0, avgTemp: 0, avgHumidity: 0 });

    // Rain: 8 days * 6mm = 48mm > 40 -> 40 pts
    for(let i=0; i<8; i++) allData[i] = { ...allData[i], totalPrecip: 6 };

    // Temp: 5 days * 18C = 18C -> Ideale -> 30 pts
    for(let i=5; i<10; i++) allData[i] = { ...allData[i], avgTemp: 18 };

    // Humidity: 4 days * 90% -> > 85 -> 15 pts
    for(let i=7; i<11; i++) allData[i] = { ...allData[i], avgHumidity: 90 };

    // Total should be 40 + 30 + 15 = 85.

    const score = calculateWeatherScore(10, allData);
    assert.strictEqual(score, 85);
});

test('calculateWeatherScore - Shock Bonus', () => {
    // Rain > 15 in last 10 days
    // Temp drop > 6 between day-4 and day-1

    const allData = Array(20).fill({ totalPrecip: 0, avgTemp: 20, avgHumidity: 50 });

    // Add Rain
    for(let i=0; i<8; i++) allData[i] = { ...allData[i], totalPrecip: 5 }; // 40mm > 15

    // Temp Drop
    // dayIndex = 10.
    // tempBefore = day-4 = 6.
    // tempAfter = day-1 = 9.

    allData[6] = { ...allData[6], avgTemp: 20 };
    allData[9] = { ...allData[9], avgTemp: 13 }; // 20 - 13 = 7 > 6

    // Base score:
    // Rain: 40mm -> > 25 -> 35 pts (wait, > 40 is 40pts, > 25 is 35pts. 40 is not > 40. So 35 pts)
    // Temp: avg around 18-20 -> Ideale -> 30 pts
    // Humidity: 50 -> 0 pts
    // Shock: 10 pts

    // Total: 35 + 30 + 0 + 10 = 75.

    const score = calculateWeatherScore(10, allData);

    // Check Rain calculation details
    // Rain window for day 10: slice(0, 8).
    // indices 0,1,2,3,4,5,6,7.
    // all contain totalPrecip 5.
    // Sum = 40.
    // getRainStatus(40) -> 35 pts ("Molto buona").

    // Temp window for day 10: slice(5, 10). Indices 5,6,7,8,9.
    // 5: 20
    // 6: 20
    // 7: 20
    // 8: 20
    // 9: 13
    // Sum = 93. Avg = 18.6.
    // getTempStatus(18.6) -> Ideale -> 30 pts.

    assert.strictEqual(score, 75);
});
