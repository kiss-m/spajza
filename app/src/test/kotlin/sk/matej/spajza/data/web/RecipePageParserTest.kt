package sk.matej.spajza.data.web

import org.junit.jupiter.api.Test
import sk.matej.spajza.domain.model.MealType
import sk.matej.spajza.domain.own.RecipeSource
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RecipePageParserTest {
    private val url = "https://example.com/gulas"

    private fun page(vararg scripts: String) = buildString {
        append("<!doctype html><html><head><title>Guláš | Blog</title>")
        scripts.forEach { append("<script type=\"application/ld+json\" class=\"yoast-schema-graph\">").append(it).append("</script>") }
        append("</head><body><h1>Guláš</h1></body></html>")
    }

    @Test
    fun `a recipe inside a graph with sections of steps`() {
        val html = page(
            """
            {"@context":"https://schema.org","@graph":[
              {"@type":"WebPage","name":"Guláš | Blog"},
              {"@type":"Recipe","name":"Hovädzí guláš &amp; knedľa","description":"<p>Poctivý guláš.</p>",
               "recipeYield":["4","4 porcie"],"prepTime":"PT20M","cookTime":"PT2H",
               "recipeIngredient":["1 kg hovädzieho pliecka","3 cibule","2 PL sladkej papriky&nbsp;"],
               "recipeInstructions":[
                 {"@type":"HowToSection","name":"Mäso","itemListElement":[
                   {"@type":"HowToStep","text":"Mäso nakrájajte na kocky."},
                   {"@type":"HowToStep","name":"Opečte","text":"Opečte ho na masti."}]},
                 {"@type":"HowToStep","name":"Dusenie","text":"Duste 2 hodiny."}],
               "recipeCategory":"Hlavné jedlo","nutrition":{"calories":"999 kcal"}}]}
            """,
        )
        val draft = RecipePageParser.parse(html, url)!!

        assertEquals("Hovädzí guláš & knedľa", draft.title)
        assertEquals("Poctivý guláš.", draft.description)
        assertEquals(4, draft.servings)
        assertEquals(140, draft.totalTimeMin)
        assertEquals(listOf("1 kg hovädzieho pliecka", "3 cibule", "2 PL sladkej papriky"), draft.ingredientLines)
        assertEquals(listOf("Mäso nakrájajte na kocky.", "Opečte ho na masti.", "Duste 2 hodiny."), draft.steps)
        assertEquals(setOf(MealType.LUNCH, MealType.DINNER), draft.mealTypes)
        assertEquals(RecipeSource.URL, draft.source)
        assertEquals(url, draft.sourceUrl)
    }

    @Test
    fun `instructions as one text with line breaks and a type list`() {
        val html = page(
            """{"@context":"http://schema.org","@type":["Recipe"],"name":"Palacinky","recipeYield":2,"totalTime":"PT45M",
               "recipeIngredient":"2 vajcia<br>500 ml mlieka","recipeInstructions":"Vymiešajte cesto.<br/>Pečte na panvici.\nPodávajte s džemom.",
               "recipeCategory":["Dezert"]}""",
        )
        val draft = RecipePageParser.parse(html, url)!!

        assertEquals(2, draft.servings)
        assertEquals(45, draft.totalTimeMin)
        assertEquals(listOf("2 vajcia", "500 ml mlieka"), draft.ingredientLines)
        assertEquals(listOf("Vymiešajte cesto.", "Pečte na panvici.", "Podávajte s džemom."), draft.steps)
        assertEquals(setOf(MealType.DESSERT), draft.mealTypes)
    }

    @Test
    fun `broken blocks are skipped and pages without a recipe give nothing`() {
        val html = page("""{"@type": "Recipe", "name": """, """[{"@type":"Article","name":"Novinky"},{"@type":"Recipe","name":"Šalát","recipeIngredient":["1 uhorka"]}]""")
        assertEquals("Šalát", RecipePageParser.parse(html, url)?.title)

        assertNull(RecipePageParser.parse(page("""{"@type":"Article","name":"Novinky"}"""), url))
        assertNull(RecipePageParser.parse("<html><body>Bez receptu</body></html>", url))
        assertNull(RecipePageParser.parse(page("""{"@type":"Recipe","name":"  "}"""), url), "a recipe needs a name")
    }

    @Test
    fun `times written as text and numeric entities`() {
        val html = page("""{"@type":"Recipe","name":"Rizoto &#8211; rýchle","totalTime":"40 minút","recipeIngredient":["&#x00BD; cibule"]}""")
        val draft = RecipePageParser.parse(html, url)!!

        assertEquals("Rizoto – rýchle", draft.title)
        assertEquals(40, draft.totalTimeMin)
        assertEquals(listOf("½ cibule"), draft.ingredientLines)
    }
}
