/*
 * Copyright 2023 Matus Faro
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.dataspray.common;

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
