package org.dustyroom.ui.i18n;

import java.lang.ref.WeakReference;
import java.util.function.Consumer;

final class TranslationBinding<T> {
    private final WeakReference<T> target;
    private final String property;
    private final Consumer<T> update;

    TranslationBinding(T target, String property, Consumer<T> update) {
        this.target = new WeakReference<>(target);
        this.property = property;
        this.update = update;
    }

    boolean isAlive() {
        return target.get() != null;
    }

    boolean replaces(Object target, String property) {
        return this.target.get() == target && this.property.equals(property);
    }

    boolean refresh() {
        T owner = target.get();
        if (owner == null) {
            return false;
        }
        update.accept(owner);
        return true;
    }
}
