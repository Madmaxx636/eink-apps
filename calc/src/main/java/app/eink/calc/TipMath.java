package app.eink.calc;

/** The tip calculator's sums. */
final class TipMath {
    static final int ROUND_NONE = 0, ROUND_TOTAL = 1, ROUND_EACH = 2;

    final double bill, tip, total, perPerson, tipPerPerson;

    private TipMath(double bill, double tip, double total, double perPerson, double tipPerPerson) {
        this.bill = bill; this.tip = tip; this.total = total; this.perPerson = perPerson; this.tipPerPerson = tipPerPerson;
    }

    /**
     * @param pct tip as a percentage of the bill
     * @param people how many ways to split it (1 or more)
     * @param rounding ROUND_NONE, ROUND_TOTAL (total up to a whole unit) or ROUND_EACH (each share up to a whole unit)
     */
    static TipMath compute(double bill, double pct, int people, int rounding) {
        people = Math.max(1, people);
        double tip = bill * pct / 100.0, total = bill + tip;
        if (rounding == ROUND_TOTAL) total = Math.ceil(total - 1e-9);
        double each = total / people;
        if (rounding == ROUND_EACH) { each = Math.ceil(each - 1e-9); total = each * people; }
        tip = total - bill;
        return new TipMath(bill, tip, total, each, tip / people);
    }
}
