package app.platform.pricing;

import java.util.Optional;

/**
 * GS1 product codes (EAN-13, UPC-A, GTIN-8/12/13/14) normalized to 14 digits, so that an EAN-13 in a store feed and
 * the UPC-A of the same product in the catalog compare equal. Codes with a wrong check digit are rejected.
 */
public final class Gtin {

    private Gtin() {
    }

    public static Optional<String> normalize(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String digits = raw.strip();
        if (digits.isEmpty() || !digits.chars().allMatch(Character::isDigit)) {
            return Optional.empty();
        }
        int length = digits.length();
        if (length != 8 && length != 12 && length != 13 && length != 14) {
            return Optional.empty();
        }
        String padded = "0".repeat(14 - length) + digits;
        if (padded.chars().allMatch(c -> c == '0') || !validCheckDigit(padded)) {
            return Optional.empty();
        }
        return Optional.of(padded);
    }

    /** GS1 mod-10: weights 3 and 1 alternate from the rightmost data digit. */
    static boolean validCheckDigit(String fourteenDigits) {
        int sum = 0;
        for (int i = 0; i < 13; i++) {
            int digit = fourteenDigits.charAt(i) - '0';
            sum += (i % 2 == 0) ? digit * 3 : digit;
        }
        int check = (10 - sum % 10) % 10;
        return check == fourteenDigits.charAt(13) - '0';
    }
}
