package ru.yubaba;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LiquibaseMigrationTest {
    private static final String MASTER = "db/changelog/db.changelog-master.yaml";
    private static final String INITIAL = "db/changelog/changes/001-initial-schema.yaml";

    @FunctionalInterface
    interface Scenario {
        void run(Connection connection, String schema) throws Exception;
    }

    private void inSchema(Scenario scenario) throws Exception {
        String url = System.getenv().getOrDefault("TEST_DB_URL", "jdbc:postgresql://localhost:5432/yubaba_test");
        String user = System.getenv().getOrDefault("TEST_DB_USER", "yubaba");
        String password = System.getenv().getOrDefault("TEST_DB_PASSWORD", "yubaba-local");
        String schema = "migration_test_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            try (var statement = connection.createStatement(); var result = statement.executeQuery("select current_database()")) {
                assertTrue(result.next());
                assertTrue(result.getString(1).endsWith("_test"), "Use a dedicated _test database");
            }
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE SCHEMA " + schema);
            }
            try {
                connection.setSchema(schema);
                scenario.run(connection, schema);
            } finally {
                try (var statement = connection.createStatement()) {
                    statement.execute("DROP SCHEMA " + schema + " CASCADE");
                }
            }
        }
    }

    private Liquibase liquibase(Connection connection, String schema, String path) throws Exception {
        var database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
        database.setDefaultSchemaName(schema);
        database.setLiquibaseSchemaName(schema);
        return new Liquibase(path, new ClassLoaderResourceAccessor(), database);
    }

    private int count(Connection connection, String table) throws Exception {
        try (var statement = connection.createStatement(); var result = statement.executeQuery("SELECT count(*) FROM " + table)) {
            result.next();
            return result.getInt(1);
        }
    }

    @Test
    void createsFreshSchemaAndDoesNotRepeatMigration() throws Exception {
        inSchema((connection, schema) -> {
            var migration = liquibase(connection, schema, MASTER);
            migration.update(new Contexts(), new LabelExpression());
            assertEquals(2, count(connection, "databasechangelog"));
            try (var statement = connection.createStatement(); var result = statement.executeQuery(
                    "SELECT count(*) FROM information_schema.tables WHERE table_schema=current_schema() AND table_name='allocation_lock'")) {
                result.next();
                assertEquals(0, result.getInt(1));
            }
            try (var statement = connection.createStatement()) {
                statement.execute("INSERT INTO clients(name,contact,notes) VALUES ('Сохранённый гость','fresh-test','')");
            }
            migration.update(new Contexts(), new LabelExpression());
            assertEquals(2, count(connection, "databasechangelog"));
            assertEquals(1, count(connection, "clients"));
            try (var statement = connection.createStatement(); var result = statement.executeQuery(
                    "SELECT count(*) FROM pg_indexes WHERE schemaname=current_schema() AND indexname='one_active_order_per_room'")) {
                result.next();
                assertEquals(1, result.getInt(1));
            }
        });
    }

    @Test
    void baselinePreservesExistingSchemaAndData() throws Exception {
        inSchema((connection, schema) -> {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/changelog/changes/001-initial-schema.sql"));
            try (var statement = connection.createStatement()) {
                statement.execute("INSERT INTO clients(name,contact,notes) VALUES ('Существующий гость','legacy-test','Не изменять')");
            }
            liquibase(connection, schema, INITIAL).changeLogSync(new Contexts(), new LabelExpression());
            liquibase(connection, schema, MASTER).update(new Contexts(), new LabelExpression());
            assertEquals(2, count(connection, "databasechangelog"));
            try (var statement = connection.createStatement(); var result = statement.executeQuery(
                    "SELECT count(*) FROM information_schema.tables WHERE table_schema=current_schema() AND table_name='allocation_lock'")) {
                result.next();
                assertEquals(0, result.getInt(1));
            }
            assertEquals(1, count(connection, "clients"));
            try (var statement = connection.createStatement(); var result = statement.executeQuery("SELECT notes FROM clients")) {
                result.next();
                assertEquals("Не изменять", result.getString(1));
            }
        });
    }

    @Test
    void transfersActiveAssignmentsWhenRemovingGlobalLock() throws Exception {
        inSchema((connection, schema) -> {
            liquibase(connection, schema, INITIAL).update(new Contexts(), new LabelExpression());
            try (var statement = connection.createStatement()) {
                statement.execute("INSERT INTO accounts(id,login,password,name,role) VALUES (1,'worker','hash','Worker','ATTENDANT'),(2,'free','hash','Free','ATTENDANT')");
                statement.execute("INSERT INTO clients(id,name,contact,notes) VALUES (1,'Guest','contact','')");
                statement.execute("""
                        INSERT INTO bath_orders(id,client_id,service_name,bath_type,attendants,visitors,
                          duration_minutes,preparation_minutes,break_minutes,temperature,priority,
                          steps,extra_services,base_price,total,status,created_at)
                        VALUES (1,1,'Bath','Herbal',1,1,30,10,0,40,0,'Steps','',100,100,'IN_SERVICE',now()),
                               (2,1,'Bath','Herbal',1,1,30,10,0,40,0,'Steps','',100,100,'CLOSED',now())
                        """);
                statement.execute("INSERT INTO order_attendants(order_id,account_id) VALUES (1,1),(2,2)");
            }
            liquibase(connection, schema, MASTER).update(new Contexts(), new LabelExpression());
            try (var statement = connection.createStatement(); var result = statement.executeQuery(
                    "SELECT active_order_id FROM accounts ORDER BY id")) {
                assertTrue(result.next());
                assertEquals(1L, result.getLong(1));
                assertTrue(result.next());
                assertNull(result.getObject(1));
            }
            assertEquals(2, count(connection, "bath_orders"));
            assertEquals(2, count(connection, "order_attendants"));
        });
    }

    @Test
    void refusesUnknownExistingSchemaInsteadOfSilentlyMarkingMigration() throws Exception {
        inSchema((connection, schema) -> {
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE accounts(id bigint)");
            }
            var migration = liquibase(connection, schema, MASTER);
            assertThrows(liquibase.exception.LiquibaseException.class, () -> migration.update(new Contexts(), new LabelExpression()));
            assertEquals(0, count(connection, "databasechangelog"));
        });
    }
}
