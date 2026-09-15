package com.gameexpert.engine.inventory;

/** Pure authority plan for applying an anvil-named name tag to a mob. */
public final class NameTagRules {
    public static final int MAX_NAME_UTF16_UNITS = 64;
    private NameTagRules() {}

    public static final class Plan {
        private final String name;
        private Plan(String name) { this.name = name; }
        public String name() { return name; }
    }

    public static Plan plan(PlayerInventory.StackSnapshot held, ItemComponentData components) {
        String customName = components == null ? null : components.customName();
        if (held == null || held.itemType() != PlayerInventory.NAME_TAG || held.count() <= 0
                || customName == null || customName.isBlank()
                || customName.length() > MAX_NAME_UTF16_UNITS || !wellFormedUtf16(customName)) {
            return null;
        }
        return new Plan(customName);
    }

    private static boolean wellFormedUtf16(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (++i >= value.length() || !Character.isLowSurrogate(value.charAt(i))) return false;
            } else if (Character.isLowSurrogate(c)) return false;
        }
        return true;
    }
}
