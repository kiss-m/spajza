package sk.matej.spajza.domain.search

import sk.matej.spajza.domain.model.Ingredient
import sk.matej.spajza.domain.model.LocalizedText
import sk.matej.spajza.domain.model.PantryUnit
import sk.matej.spajza.domain.testing.ingredient

object SearchFixture {
    private fun named(id: String, sk: String, en: String, synonyms: List<String>, unit: PantryUnit = PantryUnit.G, piece: Double? = null, density: Double? = null): Ingredient =
        ingredient(id, pantryUnit = unit, gramsPerPiece = piece, densityGPerMl = density)
            .copy(name = LocalizedText(sk, en), synonyms = synonyms)

    val ingredients = listOf(
        named("chicken-breast", "Kuracie prsia", "Chicken breast", listOf("kuracie prsia", "kuracích pŕs", "kuřecí prsa", "chicken breast")),
        named("chicken-thigh", "Kuracie stehná", "Chicken thighs", listOf("kuracie stehná", "kuracích stehien", "chicken thighs")),
        named("onion", "Cibuľa", "Onion", listOf("cibuľa", "cibule", "cibúľ", "onion", "onions"), PantryUnit.PCS, piece = 110.0),
        named("red-onion", "Červená cibuľa", "Red onion", listOf("červená cibuľa", "červenej cibule", "red onion"), PantryUnit.PCS, piece = 110.0),
        named("spinach", "Špenát", "Spinach", listOf("špenát", "špenátu", "spinach")),
        named("potato", "Zemiaky", "Potatoes", listOf("zemiak", "zemiaky", "zemiakov", "brambory", "potatoes"), piece = 150.0),
        named("carrot", "Mrkva", "Carrot", listOf("mrkva", "mrkvy", "mrkiev", "mrkev", "carrot"), PantryUnit.PCS, piece = 70.0),
        named("garlic", "Cesnak", "Garlic", listOf("cesnak", "cesnaku", "strúčik cesnaku", "česnek", "garlic"), piece = 5.0),
        named("milk", "Mlieko", "Milk", listOf("mlieko", "mlieka", "mléko", "milk"), PantryUnit.ML, density = 1.03),
        named("salt", "Soľ", "Salt", listOf("soľ", "soli", "sůl", "salt"), density = 1.2),
        named("cinnamon", "Škorica", "Cinnamon", listOf("škorica", "škorice", "skořice", "cinnamon"), density = 0.52),
        named("flour", "Pšeničná múka", "Wheat flour", listOf("múka", "múky", "hladká múka", "polohrubá múka", "mouka", "flour"), density = 0.53),
        named("canned-tomatoes", "Krájané paradajky v konzerve", "Canned tomatoes", listOf("krájané paradajky", "paradajky v konzerve", "canned tomatoes"), piece = 400.0),
        named("tomato", "Paradajky", "Tomatoes", listOf("paradajka", "paradajky", "paradajok", "rajčata", "tomatoes"), PantryUnit.PCS, piece = 120.0),
        named("egg", "Vajcia", "Eggs", listOf("vajce", "vajcia", "vajec", "vejce", "eggs"), PantryUnit.PCS, piece = 50.0),
    )
}
