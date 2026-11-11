package org.localpilot.android;

/** Optional, explicit opt-in for local GGUF-controlled 2048 proposals.
 * An older legacy overlay may omit this interface; the UI must then show
 * "unavailable" and never attempt to call absent game-control methods.
 */
public interface Neural2048Access {
 boolean mnn2048Enabled();
 void setMnn2048Enabled(boolean enabled);
}
