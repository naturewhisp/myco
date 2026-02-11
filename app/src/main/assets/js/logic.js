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

if (typeof module !== 'undefined' && module.exports) {
    module.exports = { calculateAltitudeScore, fetchOverpassData };
}
