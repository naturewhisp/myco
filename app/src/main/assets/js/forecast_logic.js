
const GROWTH_PARAMETERS = {
    TODAY_INDEX: 14,
    RAIN_TRIGGER_THRESHOLD: 10,
    CUMULATIVE_RAIN_THRESHOLD: 15,
    GROWTH_PHASE_DAYS: 4,
    MATURATION_PHASE_DAYS: 7,
    HARVEST_PHASE_DAYS: 12
};

function processWeatherData(data) {
    const dailyData = {};
    const hourlyTimes = data.hourly.time;

    for (let i = 0; i < hourlyTimes.length; i++) {
        const date = hourlyTimes[i].split('T')[0];
        if (!dailyData[date]) {
            dailyData[date] = { temps: [], precips: [], humidities: [], weathercode: null };
        }
        dailyData[date].temps.push(data.hourly.temperature_2m[i]);
        dailyData[date].precips.push(data.hourly.precipitation[i]);
        dailyData[date].humidities.push(data.hourly.relativehumidity_2m[i]);
    }

    const dailyTimes = data.daily.time;
    for (let i=0; i < dailyTimes.length; i++) {
        const date = dailyTimes[i];
        if(dailyData[date]) {
            dailyData[date].weathercode = data.daily.weathercode[i];
        }
    }

    return Object.keys(dailyData).map(date => {
        const day = dailyData[date];
        const avgTemp = day.temps.reduce((a, b) => a + b, 0) / day.temps.length;
        const totalPrecip = day.precips.reduce((a, b) => a + b, 0);
        const avgHumidity = day.humidities.reduce((a, b) => a + b, 0) / day.humidities.length;
        return { date, avgTemp, totalPrecip, avgHumidity, weathercode: day.weathercode };
    }).sort((a,b) => new Date(a.date) - new Date(b.date));
}

function calculateGrowthPhase(processedData) {
    const todayIndex = GROWTH_PARAMETERS.TODAY_INDEX;
    let triggerDayIndex = -1;

    for (let i = todayIndex; i >= 0; i--) {
        if (processedData[i] && processedData[i].totalPrecip > GROWTH_PARAMETERS.RAIN_TRIGGER_THRESHOLD) {
            triggerDayIndex = i;
            break;
        }
        if (i >= 2) {
            const threeDayRain = (processedData[i]?.totalPrecip || 0) +
                                 (processedData[i-1]?.totalPrecip || 0) +
                                 (processedData[i-2]?.totalPrecip || 0);
            if (threeDayRain > GROWTH_PARAMETERS.CUMULATIVE_RAIN_THRESHOLD) {
                triggerDayIndex = i - 2;
                break;
            }
        }
    }

    if (triggerDayIndex === -1) {
        return { text: "⏳ Fase: Crescita assente (in attesa di piogge)." };
    }

    const daysSinceTrigger = todayIndex - triggerDayIndex;

    if (daysSinceTrigger <= GROWTH_PARAMETERS.GROWTH_PHASE_DAYS) {
        return { text: `⏳ Fase: In crescita (piogge recenti ${daysSinceTrigger} giorni fa).` };
    } else if (daysSinceTrigger <= GROWTH_PARAMETERS.MATURATION_PHASE_DAYS) {
        const daysToHarvest = (GROWTH_PARAMETERS.MATURATION_PHASE_DAYS + 1) - daysSinceTrigger;
        return { text: `⏳ Fase: Maturazione finale (raccolta stimata in ${daysToHarvest}-${daysToHarvest+2} giorni).` };
    } else if (daysSinceTrigger <= GROWTH_PARAMETERS.HARVEST_PHASE_DAYS) {
        return { text: "⏳ Fase: Periodo ideale per la raccolta!" };
    } else {
        return { text: "⏳ Fase: Ciclo di crescita in esaurimento." };
    }
}

function getSlopeRecommendation(seasonality, avgTemp) {
    const month = new Date().getMonth();
    let text = "";
    let emoji = "🧭";

    if (seasonality.score < 0.2) {
        text = "Versante: Indifferente (fuori stagione).";
    } else if (month >= 5 && month <= 7) {
        text = "Versante: Prediligi versanti a NORD (più freschi e umidi).";
    } else if (month === 4 || month >= 9) {
        text = "Versante: Prediligi versanti a SUD (più caldi e soleggiati).";
    } else {
         text = "Versante: Controlla tutte le esposizioni, con preferenza per EST.";
    }
    return { text: `${emoji} ${text}` };
}

function analyzeFutureTrend(processedData) {
    const todayIndex = GROWTH_PARAMETERS.TODAY_INDEX;
    const futureWindow = processedData.slice(todayIndex + 1, todayIndex + 6);
    if(futureWindow.length < 5) return "";

    const futureRain = futureWindow.reduce((sum, day) => sum + day.totalPrecip, 0);

    if (futureRain > 15) {
        return "Inoltre, le piogge significative previste nei prossimi giorni potrebbero innescare una **nuova e promettente 'buttata'** tra circa 7-10 giorni.";
    } else if (futureRain < 2) {
        return "Guardando al futuro, il tempo si manterrà stabile e asciutto. Questo significa che l'umidità del terreno calerà, **riducendo gradualmente il potenziale di crescita** se non arriveranno nuove piogge.";
    } else {
         return "Nei prossimi giorni il tempo si manterrà variabile ma senza piogge decisive, quindi la situazione di crescita dovrebbe rimanere simile a quella attuale.";
    }
}

function generateSummaryText(weatherScore, vegetation, altitude, seasonality, totalRain, futureTrend) {
    let summary = "";
    const scores = [
        { name: 'habitat', value: vegetation.score, text: `l'habitat ${vegetation.text.includes('Ideale') ? 'ideale' : 'promettente'}` },
        { name: 'season', value: seasonality.score, text: `la stagione, che è al suo picco` },
        { name: 'altitude', value: altitude.score, text: `l'altitudine` },
        { name: 'weather', value: weatherScore / 100, text: `le condizioni meteo` }
    ];

    const overallPotential = (weatherScore / 100) * vegetation.score * altitude.score * seasonality.score;
    if (overallPotential > 0.6) summary += "Il potenziale generale è ottimo. ";
    else if (overallPotential > 0.3) summary += "Le condizioni generali sono buone. ";
    else summary += "Il potenziale di crescita è moderato. ";

    const strongest = scores.filter(s => s.value >= 0.95).sort((a, b) => b.value - a.value);
    if (strongest.length > 1) {
        const strongPoints = strongest.map(s => s.text).join(' e ');
        summary += `I punti di forza sono ${strongPoints}, che creano una base eccellente. `;
    } else if (strongest.length === 1) {
         summary += `Il punto di forza principale è ${strongest[0].text}. `;
    }

    const limiting = scores.filter(s => s.value < 0.9).sort((a, b) => a.value - b.value);
    if (limiting.length > 0) {
        const mainLimiter = limiting[0];
        summary += "Tuttavia, "
        if (mainLimiter.name === 'weather') {
            summary += `la pioggia solo sufficiente (${Math.round(totalRain)}mm) limita il potenziale di una 'buttata' più abbondante, mantenendo le probabilità su questi livelli. `;
        } else if (mainLimiter.name === 'altitude') {
            summary += `l'altitudine non perfettamente ideale sta frenando leggermente il risultato finale. `;
        } else if (mainLimiter.name === 'habitat') {
             summary += `l'habitat non ottimale è il principale fattore limitante. `;
        } else if (mainLimiter.name === 'season') {
             summary += `la stagione non è ancora al suo picco, e questo è il principale fattore limitante. `;
        }
    } else {
         summary += "Tutti i fattori sono allineati in modo ottimale per una buona crescita. "
    }

    summary += futureTrend;

    return summary;
}

if (typeof module !== 'undefined' && module.exports) {
    module.exports = {
        processWeatherData,
        calculateGrowthPhase,
        getSlopeRecommendation,
        analyzeFutureTrend,
        generateSummaryText,
        GROWTH_PARAMETERS
    };
}
