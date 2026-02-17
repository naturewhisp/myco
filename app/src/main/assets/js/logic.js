
const WEATHER_THRESHOLDS = {
    RAIN: [
        { min: 40, score: 40, label: "Ottimale" },
        { min: 25, score: 35, label: "Molto buona" },
        { min: 15, score: 25, label: "Buona" },
        { min: 5,  score: 10, label: "Sufficiente" },
        { min: -Infinity, score: 0, label: "Scarsa" }
    ],
    TEMP: {
        IDEAL: { min: 14, max: 22, score: 30, label: "Ideale" },
        FAVORABLE: { min: 10, max: 25, score: 15, label: "Favorevole" },
    },
    HUMIDITY: [
        { min: 85, score: 15 },
        { min: 75, score: 10 }
    ],
    SHOCK: {
        RAIN_THRESHOLD: 15,
        TEMP_DROP: 6,
        SCORE: 10
    }
};

function getRainStatus(totalRain) {
    for (const threshold of WEATHER_THRESHOLDS.RAIN) {
        if (totalRain >= threshold.min) {
            return threshold;
        }
    }
    return WEATHER_THRESHOLDS.RAIN[WEATHER_THRESHOLDS.RAIN.length - 1];
}

function getTempStatus(avgTemp) {
    if (avgTemp >= WEATHER_THRESHOLDS.TEMP.IDEAL.min && avgTemp <= WEATHER_THRESHOLDS.TEMP.IDEAL.max) {
        return WEATHER_THRESHOLDS.TEMP.IDEAL;
    }
    if (avgTemp >= WEATHER_THRESHOLDS.TEMP.FAVORABLE.min && avgTemp < WEATHER_THRESHOLDS.TEMP.FAVORABLE.max) {
        return WEATHER_THRESHOLDS.TEMP.FAVORABLE;
    }
    if (avgTemp < WEATHER_THRESHOLDS.TEMP.FAVORABLE.min) {
        return { score: 0, label: "Troppo freddo" };
    }
    return { score: 0, label: "Troppo caldo" };
}

function getHumidityScore(avgHumidity) {
    for (const threshold of WEATHER_THRESHOLDS.HUMIDITY) {
        if (avgHumidity >= threshold.min) {
            return threshold.score;
        }
    }
    return 0;
}

function calculateAltitudeScore(elevation) {
    let score, text;
    if (elevation < 200) {
        score = 0.7;
        text = `🏔️ Altitudine: ${Math.round(elevation)}m (Bassa, impatto moderato).`;
    } else if (elevation < 400) {
        score = 0.9;
        text = `🏔️ Altitudine: ${Math.round(elevation)}m (Collinare, favorevole).`;
    } else if (elevation <= 1400) {
        score = 1.0;
        text = `🏔️ Altitudine: ${Math.round(elevation)}m (Ideale).`;
    } else if (elevation <= 1800) {
        score = 0.9;
        text = `🏔️ Altitudine: ${Math.round(elevation)}m (Montana, buona ma con stagione breve).`;
    } else {
        score = 0.6;
        text = `🏔️ Altitudine: ${Math.round(elevation)}m (Elevata, meno favorevole).`;
    }
    return { score, text };
}

async function fetchOverpassData(endpoints, query) {
    for (const endpoint of endpoints) {
        try {
            const controller = new AbortController();
            const timeoutId = setTimeout(() => controller.abort(), 8000);

            const overpassUrl = `${endpoint}?data=${encodeURIComponent(query)}`;
            const response = await fetch(overpassUrl, { signal: controller.signal });
            clearTimeout(timeoutId);

            if (response.ok) {
                return await response.json();
            }
        } catch (error) {
            console.warn(`Endpoint ${endpoint} non ha risposto:`, error.name);
        }
    }
    return null;
}

function getMoonPhase(date = new Date()) {
    const knownNewMoon = new Date('2000-01-06T18:14:00Z');
    const daysSinceKnownNewMoon = (date - knownNewMoon) / (1000 * 60 * 60 * 24);
    const lunarCycleDays = 29.53058867;
    const currentCyclePos = daysSinceKnownNewMoon % lunarCycleDays;

    let phaseText = ""; let emoji = ""; let favorable = false;

    if (currentCyclePos < 1.845)      { phaseText = "Luna Nuova"; emoji = "🌑"; favorable = true; }
    else if (currentCyclePos < 5.535) { phaseText = "Crescente"; emoji = "🌒"; favorable = true; }
    else if (currentCyclePos < 9.225) { phaseText = "Primo Quarto"; emoji = "🌓"; }
    else if (currentCyclePos < 12.915){ phaseText = "Gibbosa Crescente"; emoji = "🌔"; }
    else if (currentCyclePos < 16.605){ phaseText = "Luna Piena"; emoji = "🌕"; }
    else if (currentCyclePos < 20.295){ phaseText = "Gibbosa Calante"; emoji = "🌖"; }
    else if (currentCyclePos < 23.985){ phaseText = "Ultimo Quarto"; emoji = "🌗"; }
    else                              { phaseText = "Calante"; emoji = "🌘"; }

    return { text: phaseText, emoji: emoji, favorable: favorable };
}

function calculateWeatherScore(dayIndex, allData) {
    let score = 0;
    if (!allData[dayIndex]) return 0;

    // Rain Score
    const rainWindow = allData.slice(Math.max(0, dayIndex - 10), dayIndex - 2);
    const totalRainLast10Days = rainWindow.reduce((sum, day) => sum + (day.totalPrecip || 0), 0);
    score += getRainStatus(totalRainLast10Days).score;

    // Temp Score
    const tempWindow = allData.slice(Math.max(0, dayIndex - 5), dayIndex);
    const avgTempLast5Days = tempWindow.length > 0 ? tempWindow.reduce((sum, day) => sum + day.avgTemp, 0) / tempWindow.length : 0;
    score += getTempStatus(avgTempLast5Days).score;

    // Humidity Score
    const humidityWindow = allData.slice(Math.max(0, dayIndex - 3), dayIndex + 1);
    const avgHumidityRecent = humidityWindow.length > 0 ? humidityWindow.reduce((sum, day) => sum + day.avgHumidity, 0) / humidityWindow.length : 0;
    score += getHumidityScore(avgHumidityRecent);

    // Temp Shock Bonus
    if (dayIndex > 4 && totalRainLast10Days > WEATHER_THRESHOLDS.SHOCK.RAIN_THRESHOLD) {
        const tempBefore = allData[Math.max(0, dayIndex - 4)].avgTemp;
        const tempAfter = allData[Math.max(0, dayIndex - 1)].avgTemp;
        if (tempBefore - tempAfter > WEATHER_THRESHOLDS.SHOCK.TEMP_DROP) {
            score += WEATHER_THRESHOLDS.SHOCK.SCORE;
        }
    }
    return Math.min(score, 100);
}

const SEASONALITY_THRESHOLDS = {
    PEAK: { months: [8, 9], score: 1.0, labelKey: "PEAK" },
    SPRING: { months: [4, 5], score: 0.9, labelKey: "SPRING" },
    LATE: { months: [10], score: 0.7, labelKey: "LATE" },
    SUMMER: { months: [6, 7], score: 0.5, labelKey: "SUMMER" },
    EARLY: { months: [3], score: 0.4, labelKey: "EARLY" },
    OFF_SEASON: { score: 0.1, labelKey: "OFF_SEASON" }
};

const TEXT_RESOURCES = {
    it: {
        MONTHS: ["Gennaio", "Febbraio", "Marzo", "Aprile", "Maggio", "Giugno", "Luglio", "Agosto", "Settembre", "Ottobre", "Novembre", "Dicembre"],
        SEASONALITY: {
            PEAK: "Picco della stagione",
            SPRING: "Buona stagione primaverile",
            LATE: "Fine stagione, possibile con clima mite",
            SUMMER: "Estivo, crescita legata a temporali",
            EARLY: "Inizio stagione, ancora presto",
            OFF_SEASON: "Fuori stagione"
        }
    }
};

function calculateSeasonalityScore(month, lang = 'it') {
    const resources = TEXT_RESOURCES[lang] || TEXT_RESOURCES.it;
    const monthName = resources.MONTHS[month];

    let threshold = SEASONALITY_THRESHOLDS.OFF_SEASON;

    for (const key in SEASONALITY_THRESHOLDS) {
        if (SEASONALITY_THRESHOLDS[key].months && SEASONALITY_THRESHOLDS[key].months.includes(month)) {
            threshold = SEASONALITY_THRESHOLDS[key];
            break;
        }
    }

    const seasonDesc = resources.SEASONALITY[threshold.labelKey];
    return {
        score: threshold.score,
        text: `🗓️ Stagione: ${monthName} (${seasonDesc}).`
    };
}

const WEATHER_ICONS = {
    SUN: `<svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5 text-yellow-300" viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M10 2a1 1 0 011 1v1a1 1 0 11-2 0V3a1 1 0 011-1zm4 8a4 4 0 11-8 0 4 4 0 018 0zm-.464 4.95l.707.707a1 1 0 001.414-1.414l-.707-.707a1 1 0 00-1.414 1.414zm2.12-10.607a1 1 0 010 1.414l-.706.707a1 1 0 11-1.414-1.414l.707-.707a1 1 0 011.414 0zM17 11a1 1 0 100-2h-1a1 1 0 100 2h1zm-7 4a1 1 0 011 1v1a1 1 0 11-2 0v-1a1 1 0 011-1zM5.05 14.95a1 1 0 01-1.414-1.414l.707-.707a1 1 0 011.414 1.414l-.707.707zm-2.12-2.122a1 1 0 000-1.414l.707-.707a1 1 0 101.414 1.414l-.707.707a1 1 0 00-1.414 0zM12 17a1 1 0 100 2h-1a1 1 0 100-2h1z" clip-rule="evenodd" /></svg>`,
    CLOUD: `<svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5 text-gray-300" viewBox="0 0 20 20" fill="currentColor"><path d="M5.5 16a3.5 3.5 0 01-.369-6.98 4 4 0 117.753-1.977A4.5 4.5 0 1113.5 16h-8z" /></svg>`,
    RAIN: `<svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5 text-blue-300" viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M15.707 8.707a1 1 0 00-1.414-1.414l-4 4a1 1 0 001.414 1.414l4-4zM5.707 8.707a1 1 0 01-1.414-1.414l4-4a1 1 0 111.414 1.414l-4 4z" clip-rule="evenodd" /><path d="M3 10a1 1 0 011-1h12a1 1 0 110 2H4a1 1 0 01-1-1z" /></svg>`,
    STORM: `<svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5 text-yellow-400" viewBox="0 0 20 20" fill="currentColor"><path d="M13 10V3L4 14h7v7l9-11h-7z" /></svg>`
};

function getWeatherIconSvg(code) {
    if (code === null) return '';
    if (code === 0) return WEATHER_ICONS.SUN;
    if (code >= 1 && code <= 3) return WEATHER_ICONS.CLOUD;
    if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) return WEATHER_ICONS.RAIN;
    if (code >= 95 && code <= 99) return WEATHER_ICONS.STORM;
    return WEATHER_ICONS.CLOUD; // Default
}

if (typeof module !== 'undefined' && module.exports) {
    module.exports = {
        calculateAltitudeScore,
        fetchOverpassData,
        getMoonPhase,
        calculateWeatherScore,
        getRainStatus,
        getTempStatus,
        getHumidityScore,
        calculateSeasonalityScore,
        getWeatherIconSvg,
        WEATHER_THRESHOLDS,
        SEASONALITY_THRESHOLDS,
        WEATHER_ICONS
    };
}
