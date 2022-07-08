package com.smotana.dataspray.core;

import com.google.inject.Singleton;
import com.samskivert.mustache.Mustache.CustomContext;
import com.samskivert.mustache.Mustache.Lambda;

import java.nio.file.Path;

@Singleton
public class ContextUtil implements CustomContext {

    @Override
    public Object get(String name) throws Exception {
        return switch (name) {
            case "lowerCamelCase" -> (Lambda) (frag, out) -> out.write(camelCase(frag.execute(), false));
            case "upperCamelCase" -> (Lambda) (frag, out) -> out.write(camelCase(frag.execute(), true));
            case "dataFormatFolderRelative" -> Path.of("..", CodegenImpl.DATA_FORMATS_FOLDER);
            default -> null;
        };
    }

    private String camelCase(String text, boolean firstIsUpper) {
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
