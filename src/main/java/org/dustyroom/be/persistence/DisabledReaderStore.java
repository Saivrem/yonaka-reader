package org.dustyroom.be.persistence;

import org.dustyroom.be.i18n.I18n;
import org.dustyroom.be.models.state.BookState;
import org.dustyroom.be.models.state.ReaderSettings;

import java.util.Optional;

final class DisabledReaderStore implements ReaderStore {

    public ReaderSettings settings() {
        return ReaderSettings.defaults();
    }

    public void saveSettings(ReaderSettings settings) {
    }

    public Optional<BookState> book(String id) {
        return Optional.empty();
    }

    public void saveBook(BookState state) {
    }

    public void reset() {
        throw new IllegalStateException(I18n.text("reset.storageUnavailable"));
    }

    public void close() {
    }

}
