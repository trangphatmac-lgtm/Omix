package cn.omix.module.impl.world;

import cn.omix.module.Module;

/**
 * Serializes ScaffoldX activation and guarantees that only one implementation
 * remains enabled, including during config loading before a world is joined.
 */
final class ScaffoldMutex {
    private ScaffoldMutex() {}

    static synchronized void activate(Module other) {
        if (other != null && other.isEnabled()) {
            other.setEnabled(false);
        }
    }
}
