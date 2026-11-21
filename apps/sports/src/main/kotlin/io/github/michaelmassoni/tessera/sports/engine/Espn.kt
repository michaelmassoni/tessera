package io.github.michaelmassoni.tessera.sports.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * ESPN's public JSON (site.api.espn.com), undocumented. Read loosely: any field may be missing,
 * and the same data has different shapes in different endpoints (a score is a string on the
 * scoreboard and an object on a schedule).
 */
object Espn {
    private const val SITE = "https://site.api.espn.com/apis/site/v2/sports"
    private const val STANDINGS = "https://site.api.espn.com/apis/v2/sports"
    private val json = Json { ignoreUnknownKeys = true }
    private val day = DateTimeFormatter.BASIC_ISO_DATE

    /** One day's games (ESPN's day, which is US time for US leagues; it no longer takes ranges). */
    fun scoreboardUrl(
        league: League,
        date: LocalDate,
    ) = "$SITE/${league.path}/scoreboard?dates=${day.format(date)}"

    fun teamsUrl(league: League) = "$SITE/${league.path}/teams?limit=100"

    fun scheduleUrl(team: Team) = "$SITE/${League.byId(team.league)!!.path}/teams/${team.id}/schedule"

    fun standingsUrl(league: League) = "$STANDINGS/${league.path}/standings"

    fun summaryUrl(game: Game) = "$SITE/${game.league.path}/summary?event=${game.id}"

    // ---- JSON helpers ----

    private val JsonElement?.obj get() = this as? JsonObject
    private val JsonElement?.arr get() = (this as? JsonArray).orEmpty()

    private fun JsonElement?.str(): String? =
        when (this) {
            null, is JsonNull -> null
            is JsonPrimitive -> contentOrNull
            is JsonObject -> (this["displayValue"] ?: this["value"]).str()
            else -> null
        }

    private operator fun JsonElement?.get(key: String): JsonElement? = obj?.get(key)

    private fun time(text: String?): Long =
        text?.let {
            try {
                Instant.parse(if (it.length == 17 && it.endsWith("Z")) it.dropLast(1) + ":00Z" else it).toEpochMilli()
            } catch (_: DateTimeParseException) {
                null
            }
        } ?: 0

    // ---- Teams ----

    private fun team(
        league: League,
        t: JsonElement?,
    ): Team {
        val logos = t["logos"].arr
        val logo = t["logo"].str() ?: logos.firstOrNull()["href"].str()
        val rels = { l: JsonElement? -> l["rel"].arr.mapNotNull { it.str() } }
        val logoDark =
            t["logoDark"].str()
                ?: logos.firstOrNull { "dark" in rels(it) && "scoreboard" !in rels(it) }["href"].str()
        return Team(
            league = league.id,
            id = t["id"].str().orEmpty(),
            name = t["displayName"].str() ?: t["name"].str().orEmpty(),
            shortName = t["shortDisplayName"].str() ?: t["name"].str() ?: t["abbreviation"].str().orEmpty(),
            abbreviation = t["abbreviation"].str().orEmpty(),
            logo = logo,
            logoDark = logoDark,
            color = t["color"].str(),
        )
    }

    fun parseTeams(
        league: League,
        body: String,
    ): List<Team> =
        json
            .parseToJsonElement(body)["sports"]
            .arr
            .flatMap { it["leagues"].arr }
            .flatMap { it["teams"].arr }
            .map { team(league, it["team"]) }
            .filter { it.id.isNotEmpty() }
            .sortedBy { it.name }

    // ---- Games ----

    private fun game(
        league: League,
        e: JsonElement?,
    ): Game? {
        val comp = e["competitions"].arr.firstOrNull() ?: return null
        val status = e["status"] ?: comp["status"]
        val type = status["type"]
        val state =
            when (type["state"].str()) {
                "in" -> GameState.Live
                "post" -> if (type["completed"].str() == "false") GameState.Postponed else GameState.Final
                else -> if (type["name"].str()?.contains("POSTPONED") == true) GameState.Postponed else GameState.Scheduled
            }
        val sides =
            comp["competitors"].arr.map { c ->
                val record =
                    c["records"]
                        .arr
                        .firstOrNull { it["name"].str() == "overall" || it["type"].str() == "total" }
                        ?.get("summary")
                        .str()
                        ?: c["records"].arr.firstOrNull()["summary"].str()
                        ?: c["record"].arr.firstOrNull()["displayValue"].str()
                (c["homeAway"].str() ?: "home") to
                    Side(
                        team = team(league, c["team"]),
                        score = c["score"].str(),
                        record = record,
                        winner = c["winner"].str() == "true",
                    )
            }
        val home = sides.firstOrNull { it.first == "home" }?.second ?: sides.firstOrNull()?.second ?: return null
        val away = sides.firstOrNull { it.first == "away" }?.second ?: sides.getOrNull(1)?.second ?: return null
        val broadcast =
            comp["broadcasts"]
                .arr
                .flatMap { it["names"].arr }
                .mapNotNull { it.str() }
                .firstOrNull()
                ?: comp["broadcasts"].arr.firstOrNull()["media"]["shortName"].str()
        return Game(
            id = e["id"].str() ?: return null,
            league = league,
            start = time(e["date"].str() ?: comp["date"].str()),
            state = state,
            status = type["shortDetail"].str() ?: type["detail"].str() ?: type["description"].str().orEmpty(),
            period = status["period"].str()?.toIntOrNull() ?: 0,
            clock = status["displayClock"].str(),
            home = home,
            away = away,
            venue = comp["venue"]["fullName"].str(),
            broadcast = broadcast,
        )
    }

    fun parseScoreboard(
        league: League,
        body: String,
    ): List<Game> = json.parseToJsonElement(body)["events"].arr.mapNotNull { game(league, it) }

    fun parseSchedule(
        league: League,
        body: String,
    ): List<Game> = json.parseToJsonElement(body)["events"].arr.mapNotNull { game(league, it) }

    // ---- Standings ----

    /** Standings come as nested groups (conference → division); the innermost with entries are the tables. */
    fun parseStandings(
        league: League,
        body: String,
    ): List<StandingsGroup> {
        val groups = mutableListOf<StandingsGroup>()

        fun walk(node: JsonElement?) {
            val entries = node["standings"]["entries"].arr
            if (entries.isNotEmpty()) {
                val rows =
                    entries.map { entry ->
                        val stats = entry["stats"].arr.associateBy({ it["name"].str().orEmpty() }, { it })
                        val values = league.columns.map { col -> stats[col.stat]["displayValue"].str() ?: "–" }
                        val rank = league.rankStat?.let { stats[it]["value"].str()?.toDoubleOrNull() }
                        val pct = stats["winPercent"]["value"].str()?.toDoubleOrNull() ?: 0.0
                        Triple(team(league, entry["team"]), values, rank ?: (1000 - pct * 1000))
                    }
                groups +=
                    StandingsGroup(
                        name = node["name"].str().orEmpty(),
                        rows = rows.sortedBy { it.third }.mapIndexed { i, (team, values, _) -> StandingsRow(i + 1, team, values) },
                    )
            }
            node["children"].arr.forEach(::walk)
        }
        walk(json.parseToJsonElement(body))
        return groups
    }

    // ---- Game detail ----

    fun parseSummary(
        league: League,
        body: String,
    ): GameDetail? {
        val root = json.parseToJsonElement(body)
        val header = root["header"]
        val comp = header["competitions"].arr.firstOrNull() ?: return null
        val event =
            JsonObject(
                mapOf(
                    "id" to (header["id"] ?: JsonPrimitive("")),
                    "date" to (comp["date"] ?: JsonNull),
                    "competitions" to JsonArray(listOf(comp)),
                ),
            )
        val game = game(league, event) ?: return null
        val lines =
            comp["competitors"].arr.map { c ->
                LineScore(
                    team = team(league, c["team"]),
                    periods = c["linescores"].arr.mapNotNull { it["displayValue"].str() },
                    total = c["score"].str(),
                )
            }
        val leaders =
            root["leaders"].arr.associate { t ->
                val teamId = t["team"]["id"].str().orEmpty()
                teamId to
                    t["leaders"].arr.mapNotNull { cat ->
                        val top = cat["leaders"].arr.firstOrNull() ?: return@mapNotNull null
                        Leader(
                            category = cat["displayName"].str().orEmpty(),
                            player = top["athlete"]["shortName"].str() ?: top["athlete"]["displayName"].str().orEmpty(),
                            value = top["displayValue"].str().orEmpty(),
                        )
                    }
            }
        return GameDetail(game, lines, leaders)
    }
}
