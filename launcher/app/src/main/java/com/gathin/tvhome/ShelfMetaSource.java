package com.gathin.tvhome;

/**
 * Hook for tiles that are not apps (the input tiles): the shelf's meta line for the tile at a position, or null when the
 * tile has none and the shelf falls back to "Last opened". The text may carry spans, e.g. the state in the ink colour.
 */
interface ShelfMetaSource {
    CharSequence metaAt(int position);
}
