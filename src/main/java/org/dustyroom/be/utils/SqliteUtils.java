package org.dustyroom.be.utils;

import lombok.experimental.UtilityClass;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

@UtilityClass
public class SqliteUtils {
    public static Connection connect(Path database) throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + database);
    }

    @FunctionalInterface
    public interface Transaction {
        void run() throws SQLException;
    }

    public static void inTransaction(Connection connection, Transaction operation) throws SQLException {
        if (!connection.getAutoCommit()) {
            throw new SQLException("Connection already has an active transaction");
        }
        connection.setAutoCommit(false);
        try {
            operation.run();
            connection.commit();
        } catch (SQLException | RuntimeException | Error failure) {
            try {
                connection.rollback();
                connection.setAutoCommit(true);
            } catch (SQLException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
                try {
                    connection.close();
                } catch (SQLException closeFailure) {
                    failure.addSuppressed(closeFailure);
                }
            }
            throw failure;
        }
        connection.setAutoCommit(true);
    }

    public static PreparedStatement prepare(Connection connection, String query, Object... values) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(query);
        try {
            bind(statement, values);
            return statement;
        } catch (SQLException | RuntimeException failure) {
            try {
                statement.close();
            } catch (SQLException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }

    public static void update(Connection connection, String query, Object... values) throws SQLException {
        try (PreparedStatement statement = prepare(connection, query, values)) {
            statement.executeUpdate();
        }
    }

    public static void addBatch(PreparedStatement statement, Object... values) throws SQLException {
        bind(statement, values);
        statement.addBatch();
    }

    private static void bind(PreparedStatement statement, Object[] values) throws SQLException {
        int expected = statement.getParameterMetaData().getParameterCount();
        if (values.length != expected) {
            throw new SQLException("Expected " + expected + " SQL parameters, received " + values.length);
        }

        for (int index = 0; index < values.length; index++) {
            statement.setObject(index + 1, values[index]);
        }
    }
}
