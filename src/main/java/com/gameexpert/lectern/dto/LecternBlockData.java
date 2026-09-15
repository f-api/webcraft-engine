package com.gameexpert.lectern.dto;

import com.gameexpert.engine.inventory.LecternRules;
import com.gameexpert.engine.inventory.PlayerInventory;

/** Immutable complete state of one lectern block entity. */
public final class LecternBlockData {
    private final int x;
    private final int y;
    private final int z;
    private final PlayerInventory.StackSnapshot book;
    private final int page;

    public LecternBlockData(int x, int y, int z,
            PlayerInventory.StackSnapshot book, int page) {
        if (book == null || !LecternRules.canPlace(book, book.itemComponents())) {
            throw new IllegalArgumentException("lectern requires one writable or written book");
        }
        int pageCount = book.itemComponents().book().pages().size();
        if (page < 0 || page >= pageCount) {
            throw new IllegalArgumentException("lectern page outside book");
        }
        this.x = x;
        this.y = y;
        this.z = z;
        this.book = book;
        this.page = page;
    }

    public int x() { return x; }
    public int y() { return y; }
    public int z() { return z; }
    public PlayerInventory.StackSnapshot book() { return book; }
    public int page() { return page; }
    public int pageCount() { return book.itemComponents().book().pages().size(); }
}
