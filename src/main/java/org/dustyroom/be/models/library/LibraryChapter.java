package org.dustyroom.be.models.library;

import org.dustyroom.be.i18n.I18n;
import lombok.*;
import lombok.experimental.Accessors;

@Getter
@EqualsAndHashCode
@ToString
@Setter
@Accessors(chain = true)
public final class LibraryChapter {
    private String id;
    private LibrarySource source;
    private boolean finished;
    private int page;
    private int pageCount;
    private long lastRead;

    @Builder
    public LibraryChapter(String id, LibrarySource source, boolean finished, int page, int pageCount, long lastRead) {
        this.id = id;
        this.source = source;
        this.finished = finished;
        this.page = page;
        this.pageCount = pageCount;
        this.lastRead = lastRead;
    }

    public String getProgress() {
        if (finished) {
            return I18n.text("ui.finished");
        }

        return pageCount > 0 ? I18n.text("reading.progress", page, pageCount) : I18n.text("ui.unread");
    }
}
