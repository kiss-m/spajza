package sk.matej.spajza.domain.unit

/**
 * Units a recipe amount can be written in. Fixed sizes: tablespoon 15 ml, teaspoon 5 ml,
 * cup 250 ml, pinch ~0.5 g; "to taste" counts as 0 g.
 */
enum class MeasureUnit {
    G,
    KG,
    ML,
    L,
    PCS,
    TBSP,
    TSP,
    CUP,
    PINCH,
    TO_TASTE,
}
