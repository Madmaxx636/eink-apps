package app.eink.calc;

import java.util.ArrayList;
import java.util.List;

/** Unit conversion. Every unit is a factor to a base unit of its kind; temperature is done with formulas. */
final class Units {
    static final class Unit {
        final String name, symbol;
        final double factor;     // how many base units in one of this

        Unit(String name, String symbol, double factor) { this.name = name; this.symbol = symbol; this.factor = factor; }

        @Override public String toString() { return name + "  (" + symbol + ")"; }
    }

    static final class Kind {
        final String name;
        final boolean temperature;
        final List<Unit> units = new ArrayList<>();

        Kind(String name, boolean temperature) { this.name = name; this.temperature = temperature; }

        Kind add(String name, String symbol, double factor) { units.add(new Unit(name, symbol, factor)); return this; }
    }

    static final List<Kind> KINDS = new ArrayList<>();

    static {
        KINDS.add(new Kind("Length", false).add("Metre", "m", 1).add("Kilometre", "km", 1000).add("Centimetre", "cm", 0.01).add("Millimetre", "mm", 0.001)
                .add("Mile", "mi", 1609.344).add("Yard", "yd", 0.9144).add("Foot", "ft", 0.3048).add("Inch", "in", 0.0254).add("Nautical mile", "nmi", 1852));
        KINDS.add(new Kind("Mass", false).add("Kilogram", "kg", 1).add("Gram", "g", 0.001).add("Milligram", "mg", 0.000001).add("Tonne", "t", 1000)
                .add("Pound", "lb", 0.45359237).add("Ounce", "oz", 0.028349523125).add("Stone", "st", 6.35029318));
        KINDS.add(new Kind("Volume", false).add("Litre", "L", 1).add("Millilitre", "mL", 0.001).add("Cubic metre", "m³", 1000)
                .add("US gallon", "gal", 3.785411784).add("US quart", "qt", 0.946352946).add("US pint", "pt", 0.473176473).add("US cup", "cup", 0.2365882365)
                .add("US fluid ounce", "fl oz", 0.0295735295625).add("Tablespoon (US)", "tbsp", 0.01478676478125).add("Teaspoon (US)", "tsp", 0.00492892159375)
                .add("Imperial gallon", "imp gal", 4.54609).add("Imperial pint", "imp pt", 0.56826125));
        KINDS.add(new Kind("Area", false).add("Square metre", "m²", 1).add("Square kilometre", "km²", 1e6).add("Hectare", "ha", 10000)
                .add("Acre", "ac", 4046.8564224).add("Square foot", "ft²", 0.09290304).add("Square inch", "in²", 0.00064516).add("Square mile", "mi²", 2589988.110336));
        KINDS.add(new Kind("Temperature", true).add("Celsius", "°C", 1).add("Fahrenheit", "°F", 1).add("Kelvin", "K", 1));
        KINDS.add(new Kind("Speed", false).add("Metre per second", "m/s", 1).add("Kilometre per hour", "km/h", 1 / 3.6).add("Mile per hour", "mph", 0.44704)
                .add("Knot", "kn", 1852 / 3600.0).add("Foot per second", "ft/s", 0.3048));
        KINDS.add(new Kind("Time", false).add("Second", "s", 1).add("Minute", "min", 60).add("Hour", "h", 3600).add("Day", "d", 86400).add("Week", "wk", 604800)
                .add("Year (365 days)", "yr", 31536000));
        KINDS.add(new Kind("Data", false).add("Byte", "B", 1).add("Kilobyte", "kB", 1e3).add("Megabyte", "MB", 1e6).add("Gigabyte", "GB", 1e9).add("Terabyte", "TB", 1e12)
                .add("Kibibyte", "KiB", 1024).add("Mebibyte", "MiB", 1048576).add("Gibibyte", "GiB", 1073741824.0));
        KINDS.add(new Kind("Energy", false).add("Joule", "J", 1).add("Kilojoule", "kJ", 1000).add("Calorie", "cal", 4.184).add("Kilocalorie", "kcal", 4184)
                .add("Watt hour", "Wh", 3600).add("Kilowatt hour", "kWh", 3.6e6));
    }

    private Units() {}

    private static double toCelsius(String symbol, double v) {
        switch (symbol) {
            case "°F": return (v - 32) * 5 / 9;
            case "K": return v - 273.15;
            default: return v;
        }
    }

    private static double fromCelsius(String symbol, double c) {
        switch (symbol) {
            case "°F": return c * 9 / 5 + 32;
            case "K": return c + 273.15;
            default: return c;
        }
    }

    static double convert(Kind k, Unit from, Unit to, double value) {
        if (k.temperature) return fromCelsius(to.symbol, toCelsius(from.symbol, value));
        return value * from.factor / to.factor;
    }
}
