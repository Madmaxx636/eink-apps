package app.eink.calc;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * Evaluates what you typed: + - × ÷ ^ % ! brackets, sin cos tan asin acos atan ln log sqrt abs, pi and e.
 * Multiplication can be left out ("2(3+4)", "2pi"). "200+10%" means 200 plus 10 percent of 200.
 */
final class Expr {
    static final class Error extends RuntimeException {
        Error(String m) { super(m); }
    }

    private final String s;
    private final boolean degrees;
    private int i;
    private boolean pct;

    private Expr(String s, boolean degrees) {
        this.s = s.replace('×', '*').replace('÷', '/').replace('−', '-').replace('π', 'p').replaceAll("\\s+", "").replace("√", "sqrt");
        this.degrees = degrees;
    }

    /** Returns the answer as text; throws Error with a short message if the input makes no sense. */
    static String eval(String input, boolean degrees) {
        Expr e = new Expr(input, degrees);
        if (e.s.isEmpty()) throw new Error("");
        double v = e.expr();
        if (e.i != e.s.length()) throw new Error("Check the brackets");
        return format(v);
    }

    static double evalValue(String input, boolean degrees) {
        Expr e = new Expr(input, degrees);
        double v = e.expr();
        if (e.i != e.s.length()) throw new Error("Check the brackets");
        check(v);
        return v;
    }

    private static void check(double v) {
        if (Double.isNaN(v)) throw new Error("Not a number");
        if (Double.isInfinite(v)) throw new Error("Can't divide by zero");
    }

    static String format(double v) {
        check(v);
        if (v == 0) return "0";
        double a = Math.abs(v);
        if (a >= 1e15 || a < 1e-9) {
            String t = String.format(java.util.Locale.ROOT, "%.9E", v);
            // 1.234500000E+20 -> 1.2345E20
            int e = t.indexOf('E');
            String mant = new BigDecimal(t.substring(0, e)).stripTrailingZeros().toPlainString();
            return mant + "E" + Integer.parseInt(t.substring(e + 1));
        }
        return new BigDecimal(v).round(new MathContext(12)).stripTrailingZeros().toPlainString();
    }

    private boolean peek(char c) { return i < s.length() && s.charAt(i) == c; }

    private boolean eat(char c) {
        if (peek(c)) { i++; return true; }
        return false;
    }

    private double expr() {
        double v = term();
        while (true) {
            if (eat('+')) { pct = false; double r = term(); v += pct ? v * r : r; pct = false; }
            else if (eat('-')) { pct = false; double r = term(); v -= pct ? v * r : r; pct = false; }
            else return v;
        }
    }

    private double term() {
        double v = unary();
        while (true) {
            if (eat('*')) { v *= unary(); pct = false; }
            else if (eat('/')) { v /= unary(); pct = false; }
            else if (i < s.length() && startsOperand()) { v *= unary(); pct = false; } // 2(3), 2pi, (1)(2)
            else return v;
        }
    }

    private boolean startsOperand() {
        char c = s.charAt(i);
        return c == '(' || c == 'p' || Character.isLetter(c) || Character.isDigit(c) || c == '.';
    }

    private double unary() {
        if (eat('-')) return -unary();
        if (eat('+')) return unary();
        return power();
    }

    private double power() {
        double base = postfix();
        if (eat('^')) {
            // Right to left, and -2^2 style exponents work: 2^-1
            boolean neg = eat('-');
            double e = power();
            return Math.pow(base, neg ? -e : e);
        }
        return base;
    }

    private double postfix() {
        double v = primary();
        while (true) {
            if (eat('!')) v = factorial(v);
            else if (eat('%')) { v /= 100; pct = true; }
            else return v;
        }
    }

    private double factorial(double v) {
        if (v < 0 || v != Math.floor(v) || v > 170) throw new Error("Factorial needs a whole number 0 to 170");
        double r = 1;
        for (int k = 2; k <= (int) v; k++) r *= k;
        return r;
    }

    private double primary() {
        pct = false;
        if (eat('(')) {
            double v = expr();
            eat(')'); // a missing closing bracket at the end is forgiven
            return v;
        }
        if (i >= s.length()) throw new Error("Incomplete");
        char c = s.charAt(i);
        if (Character.isDigit(c) || c == '.') {
            int start = i;
            while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) i++;
            // 1.5E10 style exponent, only when digits follow the E
            if (i + 1 < s.length() && s.charAt(i) == 'E' && (Character.isDigit(s.charAt(i + 1))
                    || (i + 2 < s.length() && (s.charAt(i + 1) == '-' || s.charAt(i + 1) == '+') && Character.isDigit(s.charAt(i + 2))))) {
                i += 2;
                while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
            }
            try {
                return Double.parseDouble(s.substring(start, i));
            } catch (NumberFormatException e) {
                throw new Error("Bad number");
            }
        }
        if (c == 'p') { i++; return Math.PI; }
        if (Character.isLetter(c)) {
            int start = i;
            while (i < s.length() && Character.isLetter(s.charAt(i))) i++;
            String name = s.substring(start, i);
            if (name.equals("e")) return Math.E;
            if (!peek('(')) throw new Error("Unknown: " + name);
            i++;
            double a = expr();
            eat(')');
            pct = false;
            return fn(name, a);
        }
        throw new Error("Unexpected " + c);
    }

    private double fn(String n, double a) {
        double r = degrees ? Math.toRadians(a) : a;
        switch (n) {
            case "sin": return clean(Math.sin(r));
            case "cos": return clean(Math.cos(r));
            case "tan": return clean(Math.tan(r));
            case "asin": return degrees ? Math.toDegrees(Math.asin(a)) : Math.asin(a);
            case "acos": return degrees ? Math.toDegrees(Math.acos(a)) : Math.acos(a);
            case "atan": return degrees ? Math.toDegrees(Math.atan(a)) : Math.atan(a);
            case "ln": return Math.log(a);
            case "log": return Math.log10(a);
            case "sqrt": return Math.sqrt(a);
            case "abs": return Math.abs(a);
            default: throw new Error("Unknown: " + n);
        }
    }

    /** sin(180°) should be 0, not 1.2E-16. */
    private static double clean(double v) { return Math.abs(v) < 1e-12 ? 0 : v; }
}
