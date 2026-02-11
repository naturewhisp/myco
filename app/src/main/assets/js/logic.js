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

if (typeof module !== 'undefined' && module.exports) {
    module.exports = { calculateAltitudeScore };
}
