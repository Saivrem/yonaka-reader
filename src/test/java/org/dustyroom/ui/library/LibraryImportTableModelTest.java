package org.dustyroom.ui.library;

import org.dustyroom.be.models.library.LibraryImport;
import org.dustyroom.be.models.library.LibrarySource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LibraryImportTableModelTest {
    @Test
    void collectsCheckedRowsInOrderAndSharesTheEditedTitleAcrossChapters() {
        LibrarySource folder = new LibrarySource("/mixed/pages", "Pages", true);
        LibrarySource archive = new LibrarySource("/mixed/extra.zip", "Extra", false);
        LibrarySource last = new LibrarySource("/last.zip", "Last", false);
        LibraryImport mixed = new LibraryImport("/mixed", "Mixed", List.of(folder, archive));
        LibraryImport empty = new LibraryImport("/empty", "Empty", List.of());
        LibraryImport single = new LibraryImport("/", "Single", List.of(last));
        LibraryImportTableModel table = new LibraryImportTableModel(List.of(mixed, empty, single));

        assertEquals(List.of(single), table.selection());
        table.setValueAt(true, 1, 0);
        table.setValueAt(" Revised ", 0, 1);
        assertEquals("Revised", table.getValueAt(1, 1));
        assertEquals(List.of(new LibraryImport("/mixed", "Revised", List.of(archive)), single), table.selection());

        table.setValueAt(true, 0, 0);
        table.setValueAt(false, 2, 0);
        assertEquals(List.of(new LibraryImport("/mixed", "Revised", List.of(folder, archive))), table.selection());
    }
}
