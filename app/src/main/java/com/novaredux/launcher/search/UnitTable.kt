package com.novaredux.launcher.search

// More units for the calculator's conversions (#8): volume, speed, area,
// energy and pressure, and nautical miles. Each factor takes one of the unit
// to its kind's base: litres, metres, metres a second, square metres, joules
// and pascals. Cups, pints, quarts, gallons and fluid ounces are US measures.
internal object UnitTable {

    // Calculator's own kind for length; the kinds below are new.
    private const val LENGTH = 0
    const val VOLUME = 5
    const val SPEED = 6
    const val AREA = 7
    const val ENERGY = 8
    const val PRESSURE = 9

    private const val MILLIMETRE = 0.001
    private const val CENTIMETRE = 0.01
    private const val KILOMETRE = 1000.0
    private const val NAUTICAL_MILE = 1852.0

    private const val MILLILITRE = 0.001
    private const val CENTILITRE = 0.01
    private const val DECILITRE = 0.1
    private const val LITRE = 1.0
    private const val CUBIC_METRE = 1000.0
    private const val TEASPOON = 0.00492892159375
    private const val TABLESPOON = 0.01478676478125
    private const val FLUID_OUNCE = 0.0295735295625
    private const val CUP = 0.2365882365
    private const val PINT = 0.473176473
    private const val QUART = 0.946352946
    private const val GALLON = 3.785411784

    private const val METRE_PER_SECOND = 1.0
    private const val KM_PER_HOUR = 1000.0 / 3600.0
    private const val MILE_PER_HOUR = 0.44704
    private const val KNOT = 1852.0 / 3600.0
    private const val FOOT_PER_SECOND = 0.3048

    private const val SQUARE_MILLIMETRE = 1e-6
    private const val SQUARE_CENTIMETRE = 1e-4
    private const val SQUARE_METRE = 1.0
    private const val SQUARE_KILOMETRE = 1e6
    private const val HECTARE = 1e4
    private const val ACRE = 4046.8564224
    private const val SQUARE_INCH = 0.00064516
    private const val SQUARE_FOOT = 0.09290304
    private const val SQUARE_YARD = 0.83612736
    private const val SQUARE_MILE = 2589988.110336

    private const val JOULE = 1.0
    private const val KILOJOULE = 1000.0
    private const val CALORIE = 4.184
    private const val KILOCALORIE = 4184.0
    private const val WATT_HOUR = 3600.0
    private const val KILOWATT_HOUR = 3.6e6

    private const val PASCAL = 1.0
    private const val HECTOPASCAL = 100.0
    private const val KILOPASCAL = 1000.0
    private const val BAR = 1e5
    private const val PSI = 6894.757293168
    private const val ATMOSPHERE = 101325.0
    private const val MM_OF_MERCURY = 133.322387415

    val units: Map<String, Calculator.Unit> = buildMap {
        fun put(symbol: String, factor: Double, kind: Int, vararg names: String) =
            names.forEach { this[it] = Calculator.Unit(symbol, factor, kind) }
        // The metric lengths' British spellings, beside the calculator's own.
        put("mm", MILLIMETRE, LENGTH, "millimetre", "millimetres")
        put("cm", CENTIMETRE, LENGTH, "centimetre", "centimetres")
        put("km", KILOMETRE, LENGTH, "kilometre", "kilometres")
        put("nmi", NAUTICAL_MILE, LENGTH, "nmi")

        put("ml", MILLILITRE, VOLUME, "ml", "milliliter", "milliliters", "millilitre", "millilitres")
        put("cl", CENTILITRE, VOLUME, "cl")
        put("dl", DECILITRE, VOLUME, "dl")
        put("l", LITRE, VOLUME, "l", "liter", "liters", "litre", "litres")
        put("m³", CUBIC_METRE, VOLUME, "m3")
        put("tsp", TEASPOON, VOLUME, "tsp", "teaspoon", "teaspoons")
        put("tbsp", TABLESPOON, VOLUME, "tbsp", "tablespoon", "tablespoons")
        put("fl oz", FLUID_OUNCE, VOLUME, "floz")
        put("cup", CUP, VOLUME, "cup", "cups")
        put("pt", PINT, VOLUME, "pt", "pint", "pints")
        put("qt", QUART, VOLUME, "qt", "quart", "quarts")
        put("gal", GALLON, VOLUME, "gal", "gallon", "gallons")

        put("m/s", METRE_PER_SECOND, SPEED, "m/s", "meters/second", "metres/second")
        put("km/h", KM_PER_HOUR, SPEED, "km/h", "kmh", "kph", "km/hr", "kilometers/hour", "kilometres/hour")
        put("mph", MILE_PER_HOUR, SPEED, "mph", "mi/h", "miles/hour")
        put("kn", KNOT, SPEED, "kn", "kt", "knot", "knots")
        put("ft/s", FOOT_PER_SECOND, SPEED, "ft/s", "fps", "feet/second")

        put("mm²", SQUARE_MILLIMETRE, AREA, "mm2", "millimeter2", "millimeters2", "millimetre2", "millimetres2")
        put("cm²", SQUARE_CENTIMETRE, AREA, "cm2", "centimeter2", "centimeters2", "centimetre2", "centimetres2")
        put("m²", SQUARE_METRE, AREA, "m2", "meter2", "meters2", "metre2", "metres2")
        put("km²", SQUARE_KILOMETRE, AREA, "km2", "kilometer2", "kilometers2", "kilometre2", "kilometres2")
        put("ha", HECTARE, AREA, "ha", "hectare", "hectares")
        put("ac", ACRE, AREA, "ac", "acre", "acres")
        put("in²", SQUARE_INCH, AREA, "in2", "inch2", "inches2")
        put("ft²", SQUARE_FOOT, AREA, "ft2", "foot2", "feet2")
        put("yd²", SQUARE_YARD, AREA, "yd2", "yard2", "yards2")
        put("mi²", SQUARE_MILE, AREA, "mi2", "mile2", "miles2")

        put("J", JOULE, ENERGY, "j", "joule", "joules")
        put("kJ", KILOJOULE, ENERGY, "kj", "kilojoule", "kilojoules")
        put("cal", CALORIE, ENERGY, "cal", "calorie", "calories")
        put("kcal", KILOCALORIE, ENERGY, "kcal", "kilocalorie", "kilocalories")
        put("Wh", WATT_HOUR, ENERGY, "wh")
        put("kWh", KILOWATT_HOUR, ENERGY, "kwh")

        put("Pa", PASCAL, PRESSURE, "pa", "pascal", "pascals")
        put("hPa", HECTOPASCAL, PRESSURE, "hpa", "mbar", "millibar", "millibars")
        put("kPa", KILOPASCAL, PRESSURE, "kpa")
        put("bar", BAR, PRESSURE, "bar", "bars")
        put("psi", PSI, PRESSURE, "psi")
        put("atm", ATMOSPHERE, PRESSURE, "atm")
        put("mmHg", MM_OF_MERCURY, PRESSURE, "mmhg")
    }

    // Spaced unit names folded into the one-word forms above: "fl oz",
    // "fluid ounces", "nautical miles", "square feet", "sq m", "miles per hour".
    fun foldPhrases(text: String): String = text
        .replace(Regex("""(?<![a-z])(?:fl\.?\s*oz|fluid\s+ounces?)(?![a-z])"""), "floz")
        .replace(Regex("""(?<![a-z])nautical\s+miles?(?![a-z])"""), "nmi")
        .replace(Regex("""(?<![a-z])(?:sq\.?|square)\s+([a-z]+)""")) { it.groupValues[1] + "2" }
        .replace(Regex("""\s+per\s+"""), "/")
        .replace('²', '2')
        .replace('³', '3')
}
