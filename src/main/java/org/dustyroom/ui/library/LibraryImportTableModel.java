package org.dustyroom.ui.library;

import org.dustyroom.be.i18n.I18n;
import org.dustyroom.be.models.library.LibraryImport;
import org.dustyroom.be.models.library.LibrarySource;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

final class LibraryImportTableModel extends AbstractTableModel {
    private static final class Row {
        final LibraryImport work;
        final LibrarySource source;
        boolean included;

        Row(LibraryImport work, LibrarySource source, boolean included) {
            this.work = work;
            this.source = source;
            this.included = included;
        }
    }

    private final List<List<Row>> groups = new ArrayList<>();
    private final List<Row> rows;

    LibraryImportTableModel(List<LibraryImport> works) {
        for (LibraryImport work : works) {
            List<LibrarySource> chapters = work.getSources();
            boolean mixed = chapters.size() > 1 && chapters
                    .stream()
                    .anyMatch(LibrarySource::isDirectory);

            List<Row> group = chapters.stream()
                    .map(source -> new Row(work, source, !mixed))
                    .toList();
            groups.add(group);
        }
        rows = groups.stream()
                .flatMap(Collection::stream)
                .toList();
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return 3;
    }

    @Override
    public String getColumnName(int column) {
        return switch (column) {
            case 0 -> I18n.text("ui.import");
            case 1 -> I18n.text("ui.book_title");
            default -> I18n.text("ui.chapter");
        };
    }

    @Override
    public Class<?> getColumnClass(int column) {
        return column == 0 ? Boolean.class : String.class;
    }

    @Override
    public Object getValueAt(int row, int column) {
        Row entry = rows.get(row);
        return switch (column) {
            case 0 -> entry.included;
            case 1 -> entry.work.getTitle();
            default -> entry.source.toString();
        };
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        return column < 2;
    }

    @Override
    public void setValueAt(Object value, int row, int column) {
        if (column == 0) {
            rows.get(row).included = Boolean.TRUE.equals(value);
            fireTableCellUpdated(row, column);
        } else if (column == 1) {
            rows.get(row).work.setTitle(value.toString().strip());
            fireTableRowsUpdated(0, getRowCount() - 1);
        }
    }

    List<LibraryImport> selection() {
        List<LibraryImport> selected = new ArrayList<>();
        for (List<Row> group : groups) {
            List<LibrarySource> chapters = group.stream()
                    .filter(row -> row.included)
                    .map(row -> row.source)
                    .toList();
            if (!chapters.isEmpty()) {
                LibraryImport work = group.getFirst().work;
                selected.add(new LibraryImport(work.getRoot(), work.getTitle(), chapters));
            }
        }
        return selected;
    }
}
