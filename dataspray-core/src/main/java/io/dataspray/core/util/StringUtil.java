package io.dataspray.core.util;

public class StringUtil {

    public static String camelCase(String text, boolean firstIsUpper) {
        boolean containsLowerCase = text.chars().anyMatch(c -> Character.isLetter(c) && Character.isLowerCase(c));
        StringBuilder builder = new StringBuilder();
        boolean isFirstLetter = true;
        boolean shouldConvertNextCharToUpper = firstIsUpper;
        for (int i = 0; i < text.length(); i++) {
            char currentChar = text.charAt(i);
            if (!Character.isLetterOrDigit(currentChar)) {
                if (!isFirstLetter) {
                    shouldConvertNextCharToUpper = true;
                }
            } else if (shouldConvertNextCharToUpper) {
                builder.append(Character.toUpperCase(currentChar));
                isFirstLetter = false;
                shouldConvertNextCharToUpper = false;
            } else if (containsLowerCase) {
                if (isFirstLetter) {
                    if (firstIsUpper) {
                        builder.append(Character.toUpperCase(currentChar));
                    } else {
                        builder.append(Character.toLowerCase(currentChar));
                    }
                } else {
                    // If text contains lowercase, we assume it's not all uppercase letters
                    // so we preserve the original casing and only upper case after a delimiter
                    builder.append(currentChar);
                }
                isFirstLetter = false;
            } else {
                builder.append(Character.toLowerCase(currentChar));
                isFirstLetter = false;
            }
        }
        return builder.toString();
    }

    public static String dirName(String text) {
        return lowerDelimited(text, '-', true);
    }

    public static String javaPackageName(String text) {
        return lowerDelimited(text, '.', false);
    }

    public static String lowerDelimited(String text, char delimiter, boolean allowBeginWithDigit) {
        StringBuilder builder = new StringBuilder();
        boolean nextCharIsIdentifierStart = true;
        for (int i = 0; i < text.length(); i++) {
            char currentChar = text.charAt(i);
            if (!allowBeginWithDigit && nextCharIsIdentifierStart && Character.isDigit(currentChar)) {
                continue;
            } else if (Character.isLetterOrDigit(currentChar)) {
                builder.append(Character.toLowerCase(currentChar));
                nextCharIsIdentifierStart = false;
            } else if (!nextCharIsIdentifierStart) {
                builder.append(delimiter);
                nextCharIsIdentifierStart = true;
            } else {
                continue;
            }
        }
        return builder.toString();
    }
}
