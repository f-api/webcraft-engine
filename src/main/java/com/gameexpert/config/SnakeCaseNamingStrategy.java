package com.gameexpert.config;

import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl;

/**
 * 엔티티 필드명을 snake_case 컬럼명으로 변환합니다.
 *
 * Hibernate 기본 구현이 놓치는 {@code posX}, {@code chunkZ}, {@code slot0Type}의
 * 한 글자 축과 숫자 경계도 각각 {@code pos_x}, {@code chunk_z}, {@code slot_0_type}으로 분리합니다.
 */
public final class SnakeCaseNamingStrategy extends PhysicalNamingStrategySnakeCaseImpl {

    @Override
    protected Identifier unquotedIdentifier(Identifier identifier) {
        String source = identifier.getText().replace('.', '_');
        StringBuilder result = new StringBuilder(source.length() + 8);
        for (int index = 0; index < source.length(); index++) {
            char current = source.charAt(index);
            if (needsSeparator(source, index, current)
                    && !result.isEmpty() && result.charAt(result.length() - 1) != '_') {
                result.append('_');
            }
            result.append(Character.toLowerCase(current));
        }
        return Identifier.toIdentifier(result.toString(), false);
    }

    private static boolean needsSeparator(String value, int index, char current) {
        if (index == 0 || current == '_') return false;
        char previous = value.charAt(index - 1);
        if (previous == '_') return false;

        if (Character.isDigit(current)) {
            return Character.isLetter(previous);
        }
        if (Character.isLetter(current) && Character.isDigit(previous)) {
            return true;
        }
        if (!Character.isUpperCase(current)) {
            return false;
        }
        if (Character.isLowerCase(previous)) {
            return true;
        }
        return Character.isUpperCase(previous)
                && index + 1 < value.length()
                && Character.isLowerCase(value.charAt(index + 1));
    }
}
