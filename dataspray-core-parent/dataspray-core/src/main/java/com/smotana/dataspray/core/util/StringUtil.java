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

    public static String javaPackageName(String text) {
        StringBuilder builder = new StringBuilder();
        boolean nextCharIsIdentifierStart = true;
        for (int i = 0; i < text.length(); i++) {
            char currentChar = text.charAt(i);
            if (nextCharIsIdentifierStart && Character.isDigit(currentChar)) {
                continue;
            } else if (Character.isLetterOrDigit(currentChar)) {
                builder.append(Character.toLowerCase(currentChar));
                nextCharIsIdentifierStart = false;
            } else if (!nextCharIsIdentifierStart && currentChar == '.') {
                builder.append(currentChar);
                nextCharIsIdentifierStart = true;
            } else {
                continue;
            }
        }
        return builder.toString();
    }
}
