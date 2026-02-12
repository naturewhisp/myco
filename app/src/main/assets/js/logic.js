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

if (typeof module !== 'undefined' && module.exports) {
    module.exports = { calculateAltitudeScore, fetchOverpassData, getMoonPhase };
}
