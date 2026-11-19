package ro.anmmeteo.app.data.local

import ro.anmmeteo.app.domain.model.StationCoordinates
import ro.anmmeteo.app.domain.util.WeatherMath

object VerifiedStations {

    val list: List<StationCoordinates> = listOf(
        StationCoordinates("BRASOV GHIMBAV", "Brașov", 45.696, 25.526),
        StationCoordinates("PREDEAL", "Brașov", 45.505, 25.579),
        StationCoordinates("FUNDATA", "Brașov", 45.443, 25.303),
        StationCoordinates("FAGARAS", "Brașov", 45.842, 24.973),
        StationCoordinates("BUCURESTI BANEASA", "București", 44.510, 26.078),
        StationCoordinates("BUCURESTI FILARET", "București", 44.414, 26.094),
        StationCoordinates("CLUJ-NAPOCA", "Cluj", 46.777, 23.571),
        StationCoordinates("DEJ", "Cluj", 47.143, 23.882),
        StationCoordinates("TURDA", "Cluj", 46.571, 23.785),
        StationCoordinates("SIBIU", "Sibiu", 45.789, 24.091),
        StationCoordinates("TIMISOARA", "Timiș", 45.772, 21.259),
        StationCoordinates("IASI", "Iași", 47.178, 27.620),
        StationCoordinates("CONSTANTA", "Constanța", 44.180, 28.650),
        StationCoordinates("MANGALIA", "Constanța", 43.816, 28.583),
        StationCoordinates("CRAIOVA", "Dolj", 44.318, 23.889),
        StationCoordinates("ORADEA", "Bihor", 47.035, 21.897),
        StationCoordinates("ARAD", "Arad", 46.176, 21.319),
        StationCoordinates("BACAU", "Bacău", 46.522, 26.910),
        StationCoordinates("SUCEAVA", "Suceava", 47.687, 26.354),
        StationCoordinates("GALATI", "Galați", 45.437, 28.024),
        StationCoordinates("PLOIESTI", "Prahova", 44.946, 26.013),
        StationCoordinates("SINAIA 1500", "Prahova", 45.352, 25.524),
        StationCoordinates("VF. OMU", "Prahova", 45.445, 25.456),
        StationCoordinates("TARGU MURES", "Mureș", 46.533, 24.533),
        StationCoordinates("SFANTU GHEORGHE", "Covasna", 45.866, 25.787),
        StationCoordinates("MIERCUREA CIUC", "Harghita", 46.361, 25.772),
        StationCoordinates("ALBA IULIA", "Alba", 46.076, 23.580),
        StationCoordinates("DEVA", "Hunedoara", 45.878, 22.905),
        StationCoordinates("BAIA MARE", "Maramureș", 47.657, 23.568),
        StationCoordinates("SATU MARE", "Satu Mare", 47.792, 22.878),
        StationCoordinates("BISTRITA", "Bistrița-Năsăud", 47.133, 24.500),
        StationCoordinates("PIATRA NEAMT", "Neamț", 46.928, 26.370),
        StationCoordinates("BOTOSANI", "Botoșani", 47.748, 26.669),
        StationCoordinates("VASLUI", "Vaslui", 46.638, 27.729),
        StationCoordinates("FOCSANI", "Vrancea", 45.696, 27.183),
        StationCoordinates("BUZAU", "Buzău", 45.150, 26.817),
        StationCoordinates("BRAILA", "Brăila", 45.269, 27.957),
        StationCoordinates("TULCEA", "Tulcea", 45.178, 28.805),
        StationCoordinates("CALARASI", "Călărași", 44.205, 27.313),
        StationCoordinates("PITESTI", "Argeș", 44.856, 24.869),
        StationCoordinates("RAMNICU VALCEA", "Vâlcea", 45.104, 24.375),
        StationCoordinates("TARGU JIU", "Gorj", 45.037, 23.274)
    )

    fun findNearest(lat: Double, lon: Double): Pair<StationCoordinates, Double>? {
        val nearest = list.minByOrNull { WeatherMath.haversineKm(lat, lon, it.lat, it.lon) } ?: return null
        val distanceKm = WeatherMath.haversineKm(lat, lon, nearest.lat, nearest.lon)
        return nearest to distanceKm
    }

    fun findByName(stationName: String): StationCoordinates? {
        val upper = stationName.uppercase().trim()
        return list.firstOrNull { it.name == upper }
            ?: list.firstOrNull { upper.contains(it.name.substringBefore(" ")) }
    }
}
