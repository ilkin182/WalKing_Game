package com.example.domain.achievement

/** The sections the achievements are grouped into on screen. */
enum class AchievementCategory(val title: String) {
    DISTANCE("Məsafə"),
    CELLS("Xanalar"),
    ZONES("Zonalar"),
    STREAKS("Seriyalar"),
    TIME("Vaxt"),
    GEOGRAPHY("Coğrafiya"),
    WEATHER("Hava və fəsillər"),
    ROUTES("Marşrutlar"),
    TRAVEL("Səyahət"),
    SPECIAL("Xüsusi")
}

/** How a progress figure should be written out. */
enum class ProgressUnit { CELLS, KILOMETRES, PERCENT, DAYS, ZONES, EVENTS }

/**
 * One achievement's definition.
 *
 * [measure] is null when the app does not yet record what the achievement asks about - the shape a
 * route traced, the parks and bridges it passed. Those are listed here anyway so the catalogue on
 * screen is the whole catalogue, and shown as locked and marked "tezliklə" rather than quietly
 * omitted or, worse, wired to a proxy that would unlock them wrongly.
 *
 * @property isSecret hides the description until it is unlocked.
 * @property isDerived set for the one achievement measured from the other achievements rather than
 * from [PlayerStats]; it has no [measure] but is not untracked either.
 */
data class AchievementDefinition(
    val id: String,
    val title: String,
    val description: String,
    val category: AchievementCategory,
    val unit: ProgressUnit,
    val target: Double,
    val isSecret: Boolean = false,
    val isDerived: Boolean = false,
    val measure: ((PlayerStats) -> Double)?
) {
    val isTracked: Boolean get() = measure != null || isDerived

    fun evaluate(stats: PlayerStats): AchievementProgress {
        val current = measure?.invoke(stats) ?: 0.0
        return AchievementProgress(
            definition = this,
            current = current,
            isUnlocked = measure != null && current >= target
        )
    }
}

data class AchievementProgress(
    val definition: AchievementDefinition,
    val current: Double,
    val isUnlocked: Boolean
) {
    val fraction: Float
        get() = if (definition.target <= 0.0) 0f
        else (current / definition.target).coerceIn(0.0, 1.0).toFloat()
}

/**
 * Every achievement in the game.
 *
 * The list is the product's, not the code's: it is written out in full, in the order it is meant to
 * be read, rather than generated from tiers - a generator would save thirty lines and make every
 * future "change only this one's wording" a change to the generator.
 */
object AchievementCatalog {

    private const val KM = 1000.0

    /** Every achievement, in display order. */
    val all: List<AchievementDefinition> = buildList {
        addAll(distance())
        addAll(cells())
        addAll(zones())
        addAll(streaks())
        addAll(time())
        addAll(geography())
        addAll(weather())
        addAll(routes())
        addAll(travel())
        addAll(special())
    }

    val byCategory: Map<AchievementCategory, List<AchievementDefinition>> =
        all.groupBy { it.category }

    /** Achievements whose rule the app cannot answer yet. Surfaced so the gap is visible, not hidden. */
    val notYetTracked: List<AchievementDefinition> = all.filterNot { it.isTracked }

    /**
     * Every achievement's progress.
     *
     * "Əfsanə" is resolved in a second pass because it is the one rule measured from the others -
     * the share of the rest of the catalogue that is unlocked - so it cannot be answered until they
     * have been.
     */
    fun evaluateAll(stats: PlayerStats): List<AchievementProgress> {
        val evaluated = all.map { it.evaluate(stats) }
        val others = evaluated.filterNot { it.definition.id == LEGEND_ID }
        val percentUnlocked = if (others.isEmpty()) {
            0.0
        } else {
            others.count { it.isUnlocked } * 100.0 / others.size
        }

        return evaluated.map { progress ->
            if (progress.definition.id != LEGEND_ID) {
                progress
            } else {
                progress.copy(
                    current = percentUnlocked,
                    isUnlocked = percentUnlocked >= progress.definition.target
                )
            }
        }
    }

    const val LEGEND_ID = "legend"

    // ---------------------------------------------------------------- Məsafə

    private fun distance(): List<AchievementDefinition> {
        fun km(id: String, title: String, kilometres: Double) = AchievementDefinition(
            id = id,
            title = title,
            description = "${kilometres.toInt()} km məsafə qət et",
            category = AchievementCategory.DISTANCE,
            unit = ProgressUnit.KILOMETRES,
            target = kilometres * KM,
            measure = { it.totalDistanceMeters }
        )

        return listOf(
            km("first_km", "İlk Kilometr", 1.0),
            km("five_km", "Beşlik", 5.0),
            km("ten_km", "Onluq", 10.0),
            km("half_marathon", "Yarımmaraton", 21.0),
            km("marathon", "Marafonçu", 42.0),
            km("hundred_km", "Yüzlük", 100.0),
            km("five_hundred_km", "Yolçu", 500.0),
            km("thousand_km", "Min Kilometr", 1000.0),
            // Needs per-day and per-session distance; the odometer is a single running total.
            AchievementDefinition(
                id = "long_day",
                title = "Uzunçu",
                description = "Bir gündə 15 km yeri",
                category = AchievementCategory.DISTANCE,
                unit = ProgressUnit.KILOMETRES,
                target = 15 * KM,
                measure = { it.maxDayDistanceMeters }
            ),
            AchievementDefinition(
                id = "nonstop",
                title = "Dayanmayan",
                description = "Bir gəzintidə 25 km yeri",
                category = AchievementCategory.DISTANCE,
                unit = ProgressUnit.KILOMETRES,
                target = 25 * KM,
                measure = { it.maxSessionDistanceMeters }
            )
        )
    }

    // ---------------------------------------------------------------- Xanalar

    private fun cells(): List<AchievementDefinition> {
        fun count(id: String, title: String, cells: Int) = AchievementDefinition(
            id = id,
            title = title,
            description = "$cells xana fəth et",
            category = AchievementCategory.CELLS,
            unit = ProgressUnit.CELLS,
            target = cells.toDouble(),
            measure = { it.totalCells.toDouble() }
        )

        return listOf(
            count("first_step", "İlk Addım", 1),
            count("scout", "Kəşfiyyatçı", 10),
            count("conqueror", "Böyük Fatih", 50),
            count("landowner", "Ərazi Sahibi", 250),
            count("cartographer", "Xəritəçi", 1000),
            count("emperor", "İmperator", 5000),
            AchievementDefinition(
                id = "fast_conquest",
                title = "Sürətli Fəth",
                description = "Bir saatda 25 xana fəth et",
                category = AchievementCategory.CELLS,
                unit = ProgressUnit.CELLS,
                target = 25.0,
                measure = { it.maxCellsInOneHour.toDouble() }
            ),
            AchievementDefinition(
                id = "daily_record",
                title = "Gündəlik Rekord",
                description = "Bir gündə 100 xana fəth et",
                category = AchievementCategory.CELLS,
                unit = ProgressUnit.CELLS,
                target = 100.0,
                measure = { it.maxCellsInOneDay.toDouble() }
            ),
            // Both read the *shape* of what was claimed, not just how much of it there is.
            AchievementDefinition(
                id = "wall",
                title = "Divar",
                description = "20 xanalıq düz xətt qur",
                category = AchievementCategory.CELLS,
                unit = ProgressUnit.CELLS,
                target = 20.0,
                measure = { it.longestCellLine.toDouble() }
            ),
            AchievementDefinition(
                id = "ring",
                title = "Halqa",
                description = "Qapalı dövrə bağla",
                category = AchievementCategory.CELLS,
                unit = ProgressUnit.EVENTS,
                target = 1.0,
                measure = { it.closedLoops.toDouble() }
            )
        )
    }

    // ---------------------------------------------------------------- Zonalar

    private fun zones(): List<AchievementDefinition> = listOf(
        AchievementDefinition(
            id = "zone_master",
            title = "Zonanın Ağası",
            description = "Bir zonada 25% fəth dərəcəsinə çat",
            category = AchievementCategory.ZONES,
            unit = ProgressUnit.PERCENT,
            target = 25.0,
            measure = { it.bestZonePercentage }
        ),
        AchievementDefinition(
            id = "zone_half",
            title = "Yarı Hakim",
            description = "Bir zonada 50% fəth dərəcəsinə çat",
            category = AchievementCategory.ZONES,
            unit = ProgressUnit.PERCENT,
            target = 50.0,
            measure = { it.bestZonePercentage }
        ),
        AchievementDefinition(
            id = "zone_full",
            title = "Tam Nəzarət",
            description = "Bir zonanı tam fəth et",
            category = AchievementCategory.ZONES,
            unit = ProgressUnit.PERCENT,
            target = 100.0,
            measure = { it.bestZonePercentage }
        ),
        AchievementDefinition(
            id = "two_fronts",
            title = "İki Cəbhə",
            description = "İki fərqli zonada 25%-ə çat",
            category = AchievementCategory.ZONES,
            unit = ProgressUnit.ZONES,
            target = 2.0,
            measure = { it.zonesAtQuarter.toDouble() }
        ),
        AchievementDefinition(
            id = "district_king",
            title = "Rayon Kralı",
            description = "Bütün bir rayonu (500+ xana) tam fəth et",
            category = AchievementCategory.ZONES,
            unit = ProgressUnit.CELLS,
            target = 500.0,
            measure = { it.largestCompleteZoneCells.toDouble() }
        ),
        AchievementDefinition(
            id = "borderer",
            title = "Sərhədçi",
            description = "5 fərqli zonaya toxun",
            category = AchievementCategory.ZONES,
            unit = ProgressUnit.ZONES,
            target = 5.0,
            measure = { it.distinctZones.toDouble() }
        ),
        // Both need a definition of where a city's centre and edge are.
        AchievementDefinition(
            id = "city_centre",
            title = "Mərkəz",
            description = "Şəhər mərkəzində 50 xana fəth et",
            category = AchievementCategory.ZONES,
            unit = ProgressUnit.CELLS,
            target = 50.0,
            measure = null
        ),
        AchievementDefinition(
            id = "city_edge",
            title = "Kənar",
            description = "Şəhərin sərhədinə çat",
            category = AchievementCategory.ZONES,
            unit = ProgressUnit.EVENTS,
            target = 1.0,
            measure = null
        )
    )

    // ---------------------------------------------------------------- Seriyalar

    private fun streaks(): List<AchievementDefinition> {
        fun streak(id: String, title: String, days: Int, description: String) = AchievementDefinition(
            id = id,
            title = title,
            description = description,
            category = AchievementCategory.STREAKS,
            unit = ProgressUnit.DAYS,
            target = days.toDouble(),
            measure = { it.longestDayStreak.toDouble() }
        )

        return listOf(
            streak("streak_2", "İki Gün", 2, "2 gün ardıcıl gəz"),
            streak("streak_7", "Həftəlik Vərdiş", 7, "7 gün ardıcıl gəz"),
            streak("streak_30", "Aylıq İntizam", 30, "30 gün ardıcıl gəz"),
            streak("streak_100", "Yüz Gün", 100, "100 gün ardıcıl gəz"),
            streak("streak_365", "İl Boyu", 365, "365 gün ardıcıl gəz"),
            AchievementDefinition(
                id = "morning_person",
                title = "Səhər Adamı",
                description = "7 gün ardıcıl səhər gəzintisi et",
                category = AchievementCategory.STREAKS,
                unit = ProgressUnit.DAYS,
                target = 7.0,
                measure = { it.longestMorningStreak.toDouble() }
            ),
            AchievementDefinition(
                id = "weekend_loyalist",
                title = "Həftəsonu Sadiqi",
                description = "8 həftəsonu üst-üstə gəz",
                category = AchievementCategory.STREAKS,
                unit = ProgressUnit.EVENTS,
                target = 8.0,
                measure = { it.longestWeekendStreak.toDouble() }
            ),
            AchievementDefinition(
                id = "comeback",
                title = "Geri Dönüş",
                description = "Seriya qırıldıqdan sonra yenidən 7 gün gəz",
                category = AchievementCategory.STREAKS,
                unit = ProgressUnit.EVENTS,
                target = 1.0,
                measure = { if (it.hasComebackStreak) 1.0 else 0.0 }
            ),
            AchievementDefinition(
                id = "unbreakable",
                title = "Qırılmaz",
                description = "50 gün heç bir gün ötürmə",
                category = AchievementCategory.STREAKS,
                unit = ProgressUnit.DAYS,
                target = 50.0,
                measure = { it.longestDayStreak.toDouble() }
            ),
            AchievementDefinition(
                id = "restorer",
                title = "Bərpaçı",
                description = "30 günlük fasilədən sonra qayıt",
                category = AchievementCategory.STREAKS,
                unit = ProgressUnit.EVENTS,
                target = 1.0,
                measure = { if (it.returnedAfterLongBreak) 1.0 else 0.0 }
            )
        )
    }

    // ---------------------------------------------------------------- Vaxt

    private fun time(): List<AchievementDefinition> = listOf(
        AchievementDefinition(
            id = "night_owl",
            title = "Gecə Bayquşu",
            description = "00:00–04:00 arasında fəth et",
            category = AchievementCategory.TIME,
            unit = ProgressUnit.EVENTS,
            target = 1.0,
            measure = { if (it.nightCells > 0) 1.0 else 0.0 }
        ),
        AchievementDefinition(
            id = "lunch_break",
            title = "Günorta Fasiləsi",
            description = "12:00–14:00 arasında 10 xana fəth et",
            category = AchievementCategory.TIME,
            unit = ProgressUnit.CELLS,
            target = 10.0,
            measure = { it.maxMiddayCellsInOneDay.toDouble() }
        ),
        AchievementDefinition(
            id = "exact_midnight",
            title = "Yarım Gecə",
            description = "Düz saat 00:00-da fəth et",
            category = AchievementCategory.TIME,
            unit = ProgressUnit.EVENTS,
            target = 1.0,
            measure = { if (it.hasExactMidnightCell) 1.0 else 0.0 }
        ),
        AchievementDefinition(
            id = "office_hours",
            title = "İş Saatı",
            description = "5 iş günü ardıcıl iş saatlarında gəz",
            category = AchievementCategory.TIME,
            unit = ProgressUnit.DAYS,
            target = 5.0,
            measure = { it.longestWorkdayStreak.toDouble() }
        ),
        AchievementDefinition(
            id = "nowruz",
            title = "Bayram Gəzintisi",
            description = "Novruz günü (21 mart) fəth et",
            category = AchievementCategory.TIME,
            unit = ProgressUnit.EVENTS,
            target = 1.0,
            measure = { if (it.hasNowruzCell) 1.0 else 0.0 }
        ),
        AchievementDefinition(
            id = "new_year",
            title = "Yeni İl",
            description = "1 yanvarda fəth et",
            category = AchievementCategory.TIME,
            unit = ProgressUnit.EVENTS,
            target = 1.0,
            measure = { if (it.hasNewYearCell) 1.0 else 0.0 }
        ),
        AchievementDefinition(
            id = "round_the_clock",
            title = "24 Saat",
            description = "Sutkanın hər 6 saatlıq bölməsində fəth et",
            category = AchievementCategory.TIME,
            unit = ProgressUnit.EVENTS,
            target = 4.0,
            measure = { it.sixHourBlocksCovered.toDouble() }
        ),
        // Both need the sun times for the day and place a cell was claimed in, which are not stored.
        AchievementDefinition(
            id = "before_dawn",
            title = "Sübh Çağı",
            description = "Gün doğuşundan əvvəl fəth et",
            category = AchievementCategory.TIME,
            unit = ProgressUnit.EVENTS,
            target = 1.0,
            measure = { if (it.hasBeforeSunriseCell) 1.0 else 0.0 }
        ),
        AchievementDefinition(
            id = "at_sunset",
            title = "Qürub",
            description = "Gün batımı anında fəth et",
            category = AchievementCategory.TIME,
            unit = ProgressUnit.EVENTS,
            target = 1.0,
            measure = { if (it.hasAtSunsetCell) 1.0 else 0.0 }
        )
    )

    // ---------------------------------------------------------------- Coğrafiya

    /**
     * None of these are measurable yet: they need elevation and a source of points of interest
     * (parks, monuments, metro entrances, bridges, squares, the coastline) that the app does not
     * query today.
     */
    private fun geography(): List<AchievementDefinition> = listOf(
        AchievementDefinition(
            id = "climber",
            title = "Dağçı",
            description = "Ən alçaq nöqtəndən 200 m yuxarı qalx",
            category = AchievementCategory.GEOGRAPHY,
            unit = ProgressUnit.EVENTS,
            target = 200.0,
            measure = { it.elevationGainMeters }
        ),
        AchievementDefinition(
            id = "summit",
            title = "Zirvə",
            description = "Dəniz səviyyəsindən 1000 m yüksəklikdə fəth et",
            category = AchievementCategory.GEOGRAPHY,
            unit = ProgressUnit.EVENTS,
            target = 1000.0,
            measure = { it.highestElevationMeters }
        ),
        untracked("seaside", "Sahil Adamı", "Dəniz kənarında 20 xana fəth et", AchievementCategory.GEOGRAPHY),
        untracked("park_dweller", "Park Sakini", "5 fərqli parkda fəth et", AchievementCategory.GEOGRAPHY),
        untracked("green_zone", "Yaşıl Zona", "Parkların 50%-ini fəth et", AchievementCategory.GEOGRAPHY),
        untracked("historic", "Tarixi İz", "10 tarixi abidənin yaxınlığında ol", AchievementCategory.GEOGRAPHY),
        untracked("metro_map", "Metro Xəritəsi", "3 metro stansiyasını kəşf et", AchievementCategory.GEOGRAPHY),
        untracked("bridges", "Körpüçü", "5 körpüdən keç", AchievementCategory.GEOGRAPHY),
        untracked("squares", "Meydan", "10 fərqli meydanda ol", AchievementCategory.GEOGRAPHY),
        untracked("coastline", "Su Kənarı", "Bütün sahil xəttini gəz", AchievementCategory.GEOGRAPHY)
    )

    // ---------------------------------------------------------------- Hava və fəsillər

    private fun weather(): List<AchievementDefinition> = listOf(
        // These five need the weather at the moment each cell was claimed to be recorded with it.
        AchievementDefinition(
            id = "in_the_rain",
            title = "Yağış Altında",
            description = "Yağışlı havada 10 xana gəz",
            category = AchievementCategory.WEATHER,
            unit = ProgressUnit.CELLS,
            target = 10.0,
            measure = { it.rainyCells.toDouble() }
        ),
        yesNo(
            "snow_conquest", "Qar Fəthi", "Qarlı gündə fəth et",
            AchievementCategory.WEATHER
        ) { it.hasSnowCell },
        yesNo(
            "hot_heart", "İsti Ürək", "35°C-dən yuxarıda gəz",
            AchievementCategory.WEATHER
        ) { it.hasHotCell },
        yesNo(
            "cold_blooded", "Soyuqqanlı", "0°C-dən aşağıda gəz",
            AchievementCategory.WEATHER
        ) { it.hasColdCell },
        yesNo(
            "windy_city", "Küləkli Şəhər", "40 km/saatdan güclü küləkdə gəz",
            AchievementCategory.WEATHER
        ) { it.hasWindyCell },
        // These three are calendar questions, so they work today.
        AchievementDefinition(
            id = "four_seasons",
            title = "Dörd Fəsil",
            description = "Hər fəsildə fəth et",
            category = AchievementCategory.WEATHER,
            unit = ProgressUnit.EVENTS,
            target = 4.0,
            measure = { it.seasonsCovered.toDouble() }
        ),
        AchievementDefinition(
            id = "spring_awakening",
            title = "Yaz Oyanışı",
            description = "Martda 100 xana fəth et",
            category = AchievementCategory.WEATHER,
            unit = ProgressUnit.CELLS,
            target = 100.0,
            measure = { it.marchCells.toDouble() }
        ),
        AchievementDefinition(
            id = "winter_insomniac",
            title = "Qış Yuxusuzu",
            description = "Dekabr–fevral arası 30 gün aktiv ol",
            category = AchievementCategory.WEATHER,
            unit = ProgressUnit.DAYS,
            target = 30.0,
            measure = { it.winterActiveDays.toDouble() }
        )
    )

    // ---------------------------------------------------------------- Marşrutlar

    /**
     * What the player drew with their walking.
     *
     * The four measured here are the ones a route's geometry answers on its own - did it come back,
     * how straight, how many turns, was it a square. The four left untracked ask whether a route
     * *resembles* something (a heart, a spiral, a letter), which is a different problem: it needs the
     * path matched against a template rather than measured, and until that exists they stay locked
     * rather than being approximated into unlocking wrongly.
     */
    private fun routes(): List<AchievementDefinition> = listOf(
        untracked("artist", "Rəssam", "Marşrutla ürək çək", AchievementCategory.ROUTES),
        yesNo("geometer", "Həndəsəçi", "Mükəmməl kvadrat çək", AchievementCategory.ROUTES) {
            it.hasSquareRoute
        },
        untracked("spiral", "Spiral", "Spiral marşrut çək", AchievementCategory.ROUTES),
        yesNo("boomerang", "Bumeranq", "Çıxdığın nöqtəyə qayıt", AchievementCategory.ROUTES) {
            it.hasBoomerangRoute
        },
        AchievementDefinition(
            id = "straight_line",
            title = "Düz Xətt",
            description = "3 km düz istiqamətdə get",
            category = AchievementCategory.ROUTES,
            unit = ProgressUnit.KILOMETRES,
            target = 3 * KM,
            measure = { it.longestStraightRouteMeters }
        ),
        AchievementDefinition(
            id = "zigzag",
            title = "Zigzaq",
            description = "20 dönüşlü marşrut çək",
            category = AchievementCategory.ROUTES,
            unit = ProgressUnit.EVENTS,
            target = 20.0,
            measure = { it.maxRouteTurns.toDouble() }
        ),
        untracked("writer", "Yazıçı", "Marşrutla hərf çək", AchievementCategory.ROUTES),
        untracked("calligrapher", "Xəttat", "Marşrutla 3 hərfli söz çək", AchievementCategory.ROUTES)
    )

    // ---------------------------------------------------------------- Səyahət

    private fun travel(): List<AchievementDefinition> = listOf(
        AchievementDefinition(
            id = "tour_the_country",
            title = "Ölkəni Gəz",
            description = "10 fərqli rayon/şəhərdə fəth et",
            category = AchievementCategory.TRAVEL,
            unit = ProgressUnit.ZONES,
            target = 10.0,
            measure = { it.distinctZones.toDouble() }
        ),
        AchievementDefinition(
            id = "far_point",
            title = "Uzaq Nöqtə",
            description = "Başlanğıc yerindən 100 km aralıda fəth et",
            category = AchievementCategory.TRAVEL,
            unit = ProgressUnit.KILOMETRES,
            target = 100 * KM,
            measure = { it.farthestCellMeters }
        ),
        // The rest need the city and country a cell belongs to, which is not stored - only the
        // neighbourhood name is.
        AchievementDefinition(
            id = "second_city",
            title = "Qonşu Şəhər",
            description = "İkinci şəhərdə fəth et",
            category = AchievementCategory.TRAVEL,
            unit = ProgressUnit.ZONES,
            target = 2.0,
            measure = { it.distinctCities.toDouble() }
        ),
        AchievementDefinition(
            id = "five_cities",
            title = "Beş Şəhər",
            description = "5 fərqli şəhərdə fəth et",
            category = AchievementCategory.TRAVEL,
            unit = ProgressUnit.ZONES,
            target = 5.0,
            measure = { it.distinctCities.toDouble() }
        ),
        AchievementDefinition(
            id = "abroad",
            title = "Sərhəddən Kənar",
            description = "Başqa ölkədə fəth et",
            category = AchievementCategory.TRAVEL,
            unit = ProgressUnit.ZONES,
            target = 2.0,
            measure = { it.distinctCountries.toDouble() }
        ),
        AchievementDefinition(
            id = "tourist",
            title = "Turist",
            description = "3 ölkədə fəth et",
            category = AchievementCategory.TRAVEL,
            unit = ProgressUnit.ZONES,
            target = 3.0,
            measure = { it.distinctCountries.toDouble() }
        ),
        yesNo(
            "train_rider", "Qatar Yolçusu", "Bir gündə iki fərqli şəhərdə fəth et",
            AchievementCategory.TRAVEL
        ) { it.hasIntercityDay },
        yesNo(
            "homecoming", "Vətənə Dönüş", "Səyahətdən sonra doğma şəhərinə qayıt",
            AchievementCategory.TRAVEL
        ) { it.hasHomecoming }
    )

    // ---------------------------------------------------------------- Xüsusi

    private fun special(): List<AchievementDefinition> {
        fun luckyNumber(id: String, title: String, nth: Int) = AchievementDefinition(
            id = id,
            title = title,
            description = "$nth-ci xananı fəth et",
            category = AchievementCategory.SPECIAL,
            unit = ProgressUnit.CELLS,
            target = nth.toDouble(),
            measure = { it.totalCells.toDouble() }
        )

        return listOf(
            luckyNumber("lucky_111", "Şanslı Rəqəm 111", 111),
            luckyNumber("lucky_333", "Şanslı Rəqəm 333", 333),
            luckyNumber("lucky_777", "Şanslı Rəqəm 777", 777),
            luckyNumber("symmetry", "Simmetriya", 1234),
            untracked("coincidence", "Təsadüf", "Nadir bir xana tap", AchievementCategory.SPECIAL),
            AchievementDefinition(
                id = "loyal",
                title = "Sadiq",
                description = "Tətbiqin ildönümündə aktiv ol",
                category = AchievementCategory.SPECIAL,
                unit = ProgressUnit.EVENTS,
                target = 1.0,
                measure = { if (it.activeOnAppAnniversary) 1.0 else 0.0 }
            ),
            AchievementDefinition(
                id = LEGEND_ID,
                title = "Əfsanə",
                description = "Bütün digər nailiyyətlərin 90%-ini aç",
                category = AchievementCategory.SPECIAL,
                unit = ProgressUnit.PERCENT,
                target = 90.0,
                isDerived = true,
                measure = null
            ),
            AchievementDefinition(
                id = "nameless",
                title = "Adsız",
                description = "5000 xana fəth et",
                category = AchievementCategory.SPECIAL,
                unit = ProgressUnit.CELLS,
                target = 5000.0,
                isSecret = true,
                measure = { it.totalCells.toDouble() }
            )
        )
    }

    /** A yes/no achievement: measured as 1 once it has happened, 0 until then. */
    private fun yesNo(
        id: String,
        title: String,
        description: String,
        category: AchievementCategory,
        measure: (PlayerStats) -> Boolean
    ) = AchievementDefinition(
        id = id,
        title = title,
        description = description,
        category = category,
        unit = ProgressUnit.EVENTS,
        target = 1.0,
        measure = { if (measure(it)) 1.0 else 0.0 }
    )

    private fun untracked(
        id: String,
        title: String,
        description: String,
        category: AchievementCategory
    ) = AchievementDefinition(
        id = id,
        title = title,
        description = description,
        category = category,
        unit = ProgressUnit.EVENTS,
        target = 1.0,
        measure = null
    )
}
