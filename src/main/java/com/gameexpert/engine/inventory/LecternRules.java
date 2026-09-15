package com.gameexpert.engine.inventory;

/** Pure lectern placement and page navigation rules. */
public final class LecternRules {
    private LecternRules() {}

    public static boolean canPlace(PlayerInventory.StackSnapshot stack, ItemComponentData data) {
        if (stack == null || data == null || data.book() == null || stack.count() != 1) return false;
        return stack.itemType() == PlayerInventory.WRITABLE_BOOK
                && !data.book().signed()
                || stack.itemType() == PlayerInventory.WRITTEN_BOOK && data.book().signed();
    }

    public static int page(int requested, ItemComponentData.BookData book) {
        if (book == null) throw new IllegalArgumentException("book is required");
        return Math.max(0, Math.min(requested, book.pages().size() - 1));
    }
}
