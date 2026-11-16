package app.eink.calc;

import java.util.Map;

/** Currency conversion through the euro, which is how the European Central Bank publishes its reference rates. */
final class RateMath {
    private RateMath() {}

    /** rates maps a currency code to how many of it make one euro; EUR itself is 1. Returns NaN if a rate is missing. */
    static double convert(double amount, String from, String to, Map<String, Double> rates) {
        Double f = from.equals("EUR") ? Double.valueOf(1) : rates.get(from);
        Double t = to.equals("EUR") ? Double.valueOf(1) : rates.get(to);
        if (f == null || t == null || f == 0) return Double.NaN;
        return amount / f * t;
    }
}
