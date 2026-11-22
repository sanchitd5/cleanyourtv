package com.gathin.tvhome;

/** Physical left/right boundaries for a grid whose first column follows its reading direction. */
final class GridNavigation {
    private GridNavigation() { }

    static boolean atHorizontalEdge(int position, int count, int columns, boolean rtl, boolean left) {
        boolean towardStart = left != rtl;
        if (towardStart) return position % columns == 0;
        return position % columns == columns - 1 || position == count - 1;
    }

    /** Dim level of a non-carried tile: edit-dock dims only the other dock places, Move mode dims every tile. */
    static float idleAlpha(int pos, int columns, boolean dockEditing, int dockEditSlot, boolean reordering) {
        if (dockEditing) return dockEditSlot >= 0 && pos >= 0 && pos < columns && pos != dockEditSlot ? 0.45f : 1f;
        return reordering ? 0.45f : 1f;
    }

    /** input_group: both places are among the input tiles (below {@code inputCount}), or both are among the apps. */
    static boolean sameGroup(int from, int to, int inputCount) {
        return (from < inputCount) == (to < inputCount);
    }

    /** input_group ordering: -1 puts a before b (an input before an app), 1 the reverse, 0 when they are the same kind. */
    static int groupOrder(boolean aIsInput, boolean bIsInput) {
        return aIsInput == bIsInput ? 0 : aIsInput ? -1 : 1;
    }

    /** Dock edit offers app places only: {@code slot} held to {@code inputCount} .. {@code columns - 1}. */
    static int appSlot(int slot, int inputCount, int columns) {
        return Math.min(Math.max(slot, inputCount), Math.max(0, columns - 1));
    }
}
