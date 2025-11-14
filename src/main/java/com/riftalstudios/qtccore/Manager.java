package com.riftalstudios.qtccore;

/**
 * Lifecycle contract QTCCore modules (ResourceNodeManager, FusionManager, etc).
 * QTCCore calls enable() on startup and disable() on shutdown for each registered manager.
 * Enables/disables in order; failures are isolated so one broken manager doesn't crash the plugin.
 */
public interface Manager {

    void enable();

    void disable();

    String getName();
}
