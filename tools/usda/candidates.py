#!/usr/bin/env python3
"""Find USDA FoodData Central (SR Legacy) candidates for every seed ingredient.

Usage: candidates.py <sr_legacy_csv_dir> <seed_ingredients_dir> <out.json> [<all_foods.json>]

For each ingredient that is not marked "estimate", ranks SR Legacy foods by word overlap with the
ingredient's English name and synonyms and writes the top candidates with their nutrition per
100 g. A person (or Claude) then picks the right FDC id; nothing is chosen automatically.
"""
import csv
import glob
import json
import os
import re
import sys

NUTRIENTS = {
    "kcal": [("Energy", "KCAL")],
    "protein": [("Protein", None)],
    "fat": [("Total lipid (fat)", None)],
    "carbs": [("Carbohydrate, by difference", None)],
    "fiber": [("Fiber, total dietary", None)],
    "sugar": [("Sugars, total including NLEA", None), ("Sugars, Total", None), ("Total Sugars", None)],
    "sodium_mg": [("Sodium, Na", None)],
}
EXCLUDED_CATEGORIES = {"Baby Foods", "Fast Foods", "Restaurant Foods", "Meals, Entrees, and Side Dishes"}
WORD = re.compile(r"[a-z]+")


def singular(word):
    if len(word) <= 3:
        return word
    if word.endswith("ies"):
        return word[:-3] + "y"
    if word.endswith("oes"):
        return word[:-2]
    if word.endswith("es") and word[:-2].endswith(("ch", "sh", "x", "ss")):
        return word[:-2]
    if word.endswith("s") and not word.endswith("ss"):
        return word[:-1]
    return word


def words(text):
    return [singular(w) for w in WORD.findall(text.lower())]


def load(csv_dir):
    def rows(name):
        path = next(iter(glob.glob(os.path.join(csv_dir, "**", name), recursive=True)))
        with open(path, newline="", encoding="utf-8") as f:
            yield from csv.DictReader(f)

    categories = {r["id"]: r["description"] for r in rows("food_category.csv")}
    foods = {
        r["fdc_id"]: {"fdcId": int(r["fdc_id"]), "description": r["description"]}
        for r in rows("food.csv")
        if categories.get(r["food_category_id"]) not in EXCLUDED_CATEGORIES
    }
    wanted = {}
    for r in rows("nutrient.csv"):
        for key, options in NUTRIENTS.items():
            for priority, (name, unit) in enumerate(options):
                if r["name"] == name and (unit is None or r["unit_name"].upper() == unit):
                    wanted[r["id"]] = (key, priority)
    for r in rows("food_nutrient.csv"):
        food = foods.get(r["fdc_id"])
        hit = wanted.get(r["nutrient_id"])
        if food is None or hit is None or not r["amount"]:
            continue
        key, priority = hit
        if priority < food.get("_p_" + key, 99):
            food[key] = float(r["amount"])
            food["_p_" + key] = priority
    for food in foods.values():
        for key in [k for k in food if k.startswith("_p_")]:
            del food[key]
        food["_words"] = set(words(food["description"]))
    return list(foods.values())


def score(query_words, food):
    if not query_words:
        return 0.0
    hit = len(query_words & food["_words"])
    if hit == 0:
        return 0.0
    s = hit / len(query_words) - 0.02 * len(food["_words"] - query_words)
    if "raw" in food["_words"]:
        s += 0.05
    return s


def main(csv_dir, seed_dir, out, dump=None):
    foods = load(csv_dir)
    if dump:
        with open(dump, "w", encoding="utf-8") as f:
            for food in sorted(foods, key=lambda x: x["fdcId"]):
                f.write(json.dumps({k: v for k, v in food.items() if not k.startswith("_")}, ensure_ascii=False) + "\n")
    result = {}
    for path in sorted(glob.glob(os.path.join(seed_dir, "*.json"))):
        for ingredient in json.load(open(path, encoding="utf-8")):
            if ingredient.get("source") == "estimate":
                continue
            queries = [ingredient["name"]["en"]] + [s for s in ingredient.get("synonyms", []) if s.isascii()]
            best = {}
            for q in queries:
                qw = set(words(q))
                for food in foods:
                    s = score(qw, food)
                    if s > best.get(food["fdcId"], (0, None))[0]:
                        best[food["fdcId"]] = (s, food)
            ranked = sorted(best.values(), key=lambda x: -x[0])[:12]
            result[ingredient["id"]] = [
                {k: v for k, v in food.items() if not k.startswith("_")} | {"score": round(s, 3)} for s, food in ranked
            ]
    with open(out, "w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False, indent=1)
    print(f"{len(result)} ingredients, {len(foods)} foods")


if __name__ == "__main__":
    main(*sys.argv[1:])
