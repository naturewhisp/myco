const { test, describe, before, after } = require('node:test');
const assert = require('node:assert');
const {
    processWeatherData,
    calculateGrowthPhase,
    getSlopeRecommendation,
    analyzeFutureTrend,
    generateSummaryText
} = require('../../main/assets/js/forecast_logic.js');

describe('forecast_logic tests', () => {

    test('processWeatherData - correctly aggregates hourly data', () => {
        const inputData = {
            hourly: {
                time: ['2023-10-01T00:00', '2023-10-01T01:00', '2023-10-02T00:00'],
                temperature_2m: [10, 20, 15],
                precipitation: [0, 5, 2],
                relativehumidity_2m: [80, 90, 70]
            },
            daily: {
                time: ['2023-10-01', '2023-10-02'],
                weathercode: [1, 2]
            }
        };

        const result = processWeatherData(inputData);

        assert.strictEqual(result.length, 2);

        // Check Day 1
        assert.strictEqual(result[0].date, '2023-10-01');
        assert.strictEqual(result[0].avgTemp, 15); // (10+20)/2
        assert.strictEqual(result[0].totalPrecip, 5); // 0+5
        assert.strictEqual(result[0].avgHumidity, 85); // (80+90)/2
        assert.strictEqual(result[0].weathercode, 1);

        // Check Day 2
        assert.strictEqual(result[1].date, '2023-10-02');
        assert.strictEqual(result[1].avgTemp, 15);
        assert.strictEqual(result[1].totalPrecip, 2);
        assert.strictEqual(result[1].avgHumidity, 70);
        assert.strictEqual(result[1].weathercode, 2);
    });

    test('calculateGrowthPhase - trigger day recent (<= 4 days)', () => {
        // Mock data where index 14 is today. Trigger at 14-2 = 12.
        // Days since trigger = 2.
        const processedData = new Array(20).fill({ totalPrecip: 0 });
        processedData[12] = { totalPrecip: 15 }; // Trigger day

        const result = calculateGrowthPhase(processedData);
        assert.match(result.text, /In crescita/);
    });

    test('calculateGrowthPhase - trigger day intermediate (5-7 days)', () => {
        // Trigger at 14-6 = 8.
        const processedData = new Array(20).fill({ totalPrecip: 0 });
        processedData[8] = { totalPrecip: 15 };

        const result = calculateGrowthPhase(processedData);
        assert.match(result.text, /Maturazione finale/);
    });

    test('calculateGrowthPhase - trigger day ideal (8-12 days)', () => {
        // Trigger at 14-10 = 4.
        const processedData = new Array(20).fill({ totalPrecip: 0 });
        processedData[4] = { totalPrecip: 15 };

        const result = calculateGrowthPhase(processedData);
        assert.match(result.text, /Periodo ideale/);
    });

    test('calculateGrowthPhase - trigger day old (> 12 days)', () => {
        // Trigger at 14-13 = 1.
        const processedData = new Array(20).fill({ totalPrecip: 0 });
        processedData[1] = { totalPrecip: 15 };

        const result = calculateGrowthPhase(processedData);
        assert.match(result.text, /Ciclo di crescita in esaurimento/);
    });

    test('calculateGrowthPhase - no trigger', () => {
        const processedData = new Array(20).fill({ totalPrecip: 0 });
        const result = calculateGrowthPhase(processedData);
        assert.match(result.text, /Crescita assente/);
    });

    test('calculateGrowthPhase - cumulative rain trigger', () => {
        // Trigger with 3-day cumulative rain > 15
        const processedData = new Array(20).fill({ totalPrecip: 0 });
        processedData[12] = { totalPrecip: 6 };
        processedData[11] = { totalPrecip: 6 };
        processedData[10] = { totalPrecip: 6 }; // Sum 18 > 15
        // Trigger index logic in function: `triggerDayIndex = i - 2` when checking `i`.
        // If loop is at 12, checks 12, 11, 10. trigger is 12-2 = 10.
        // Days since: 14 - 10 = 4.

        const result = calculateGrowthPhase(processedData);
        // Should be "In crescita" (<=4 days)
        assert.match(result.text, /In crescita/);
    });

    test('getSlopeRecommendation - Summer (June)', () => {
        const originalDate = global.Date;
        // Mock Date to return June (Month 5 in 0-indexed)
        global.Date = class extends Date {
            getMonth() { return 5; }
        };

        try {
            const seasonality = { score: 1.0 };
            const avgTemp = 20;
            const result = getSlopeRecommendation(seasonality, avgTemp);
            assert.match(result.text, /versanti a NORD/);
        } finally {
            global.Date = originalDate;
        }
    });

    test('getSlopeRecommendation - Winter (January)', () => {
        const originalDate = global.Date;
        // Mock Date to return January (Month 0)
        global.Date = class extends Date {
            getMonth() { return 0; }
        };

        try {
            const seasonality = { score: 1.0 };
            const avgTemp = 10;
            const result = getSlopeRecommendation(seasonality, avgTemp);
            assert.match(result.text, /tutte le esposizioni/);
        } finally {
            global.Date = originalDate;
        }
    });

    test('getSlopeRecommendation - Off Season', () => {
         const seasonality = { score: 0.1 };
         const avgTemp = 10;
         const result = getSlopeRecommendation(seasonality, avgTemp);
         assert.match(result.text, /Indifferente/);
    });

    test('analyzeFutureTrend - Heavy Rain', () => {
        const todayIndex = 14;
        const processedData = new Array(25).fill({ totalPrecip: 0 });
        // Future window is 15 to 19 (5 days)
        processedData[15] = { totalPrecip: 10 };
        processedData[16] = { totalPrecip: 10 }; // Total 20 > 15

        const result = analyzeFutureTrend(processedData);
        assert.match(result, /nuova e promettente 'buttata'/);
    });

    test('analyzeFutureTrend - Dry', () => {
        const processedData = new Array(25).fill({ totalPrecip: 0 }); // Total 0 < 2
        const result = analyzeFutureTrend(processedData);
        assert.match(result, /tempo si manterrà stabile e asciutto/);
    });

    test('analyzeFutureTrend - Variable', () => {
        const processedData = new Array(25).fill({ totalPrecip: 0 });
        processedData[15] = { totalPrecip: 5 }; // Total 5 (between 2 and 15)
        const result = analyzeFutureTrend(processedData);
        assert.match(result, /tempo si manterrà variabile/);
    });

    test('generateSummaryText - Excellent Potential', () => {
        const weatherScore = 100;
        const vegetation = { score: 1.0, text: 'Ideale' };
        const altitude = { score: 1.0, text: 'Ideale' };
        const seasonality = { score: 1.0, text: 'Picco' };
        const totalRain = 50;
        const futureTrend = "Trend positivo.";

        const result = generateSummaryText(weatherScore, vegetation, altitude, seasonality, totalRain, futureTrend);
        assert.match(result, /Il potenziale generale è ottimo/);
        assert.match(result, /punti di forza/);
        assert.match(result, /Trend positivo/);
    });

    test('generateSummaryText - Limited Potential (Habitat)', () => {
        const weatherScore = 80;
        const vegetation = { score: 0.5, text: 'Non ideale', name: 'habitat' };
        const altitude = { score: 1.0, text: 'Ideale' };
        const seasonality = { score: 1.0, text: 'Picco' };
        const totalRain = 50;
        const futureTrend = "";

        const result = generateSummaryText(weatherScore, vegetation, altitude, seasonality, totalRain, futureTrend);
        assert.match(result, /habitat non ottimale è il principale fattore limitante/);
    });
});
