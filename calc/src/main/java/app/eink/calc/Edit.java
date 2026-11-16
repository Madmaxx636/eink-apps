package app.eink.calc;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small text edits the keypad makes to the expression. No Android classes, so they are tested on a plain JVM. */
final class Edit {
    private static final Pattern WRAPPED = Pattern.compile("\\(−([0-9.]+)\\)$");
    private static final Pattern NUMBER = Pattern.compile("([0-9.]+)$");

    private Edit() {}

    /** The ± key: flips the sign of the last number typed. "5+3" becomes "5+(−3)", and again back to "5+3". */
    static String negateLast(String s) {
        Matcher w = WRAPPED.matcher(s);
        if (w.find()) return s.substring(0, w.start()) + w.group(1);
        Matcher n = NUMBER.matcher(s);
        if (n.find()) return s.substring(0, n.start()) + "(−" + n.group(1) + ")";
        return s;
    }

    /** How many characters Backspace removes: a whole function name like "sin(" at once. */
    static int backLen(String s) {
        for (String f : new String[]{"sin(", "cos(", "tan(", "log(", "ln(", "√(", "Ans", "MR"}) if (s.endsWith(f)) return f.length();
        return 1;
    }

    /** The 1/x key: wraps what is typed as 1÷(...). */
    static String reciprocal(String s) { return "1÷(" + s + ")"; }
}
