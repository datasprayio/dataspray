package com.smotana.dataspray.core.util;

public class StringUtil {

    public static String camelCase(String text, boolean firstIsUpper) {
        boolean containsLowerCase = text.chars().anyMatch(c -> Character.isLetter(c) && Character.isLowerCase(c));
        StringBuilder builder = new StringBuilder();
        boolean shouldConvertNextCharToUpper = firstIsUpper;
        for (int i = 0; i < text.length(); i++) {
            char currentChar = text.charAt(i);
            if (!Character.isLetterOrDigit(currentChar)) {
                shouldConvertNextCharToUpper = true;
            } else if (shouldConvertNextCharToUpper) {
                builder.append(Character.toUpperCase(currentChar));
                shouldConvertNextCharToUpper = false;
            } else if (containsLowerCase) {
                // If text contains lowercase, we assume it's not all uppercase letters
                // so we preserve the original casing and only upper case after a delimiter
                builder.append(currentChar);
            } else {
                builder.append(Character.toLowerCase(currentChar));
            }
        }
        return builder.toString();
    }
}
