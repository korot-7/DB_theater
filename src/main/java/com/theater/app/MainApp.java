package com.theater.app;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;

public final class MainApp extends Application {
    private static final String DB_HOST = "localhost";
    private static final String DB_PORT = "5432";
    private static final String DB_NAME = "theater_db";
    private static final String DB_USER = "postgres";
    private static final String DB_PASSWORD = "postgres";
    private static final Object NO_CHANGE = new Object();

    private static final List<String> ALL_TABLES = List.of(
            "worker_types",
            "countries",
            "city",
            "theater_schools",
            "genres",
            "age_categories",
            "theaters",
            "current_theater",
            "seat_types",
            "seasons",
            "halls",
            "workers",
            "actors",
            "musicians",
            "director_producers",
            "designer_producers",
            "conductor_producers",
            "staff",
            "actor_honors",
            "actor_awards",
            "authors",
            "spectacles",
            "spectacle_authors",
            "roles",
            "casting",
            "seats",
            "pricing",
            "shows",
            "tickets",
            "tours",
            "tour_participants"
    );

    private static final Set<Integer> STATIC_HIDDEN_QUERY_IDS = Set.of(3, 4, 5, 6, 24, 25, 43, 44);
    private static final ParamSpec RESULT_KIND_SPEC = ParamSpec.lookup("result_kind", "Тип результата", "resultKind");

    private final DatabaseService databaseService = new DatabaseService();
    private final List<QueryDefinition> allQueries = QueryCatalog.queries();
    private final Map<Integer, Integer> queryCounterpartById = buildQueryCounterparts();
    private final Set<Integer> hiddenQueryIds = buildHiddenQueryIds();
    private final Map<Integer, List<ParamSpec>> paramSpecsByQueryId = buildParamSpecs();
    private final Map<Integer, String> queryTitleById = buildUserQueryTitles();
    private final Map<String, List<OptionItem<?>>> lookupCache = new LinkedHashMap<>();
    private final Map<String, ControlHolder> currentParamControls = new LinkedHashMap<>();
    private final Map<String, String> sectionDisplayToRaw = new LinkedHashMap<>();
    private final Map<String, TableMeta> tableMetaCache = new LinkedHashMap<>();
    private final Map<String, MutationInput> mutationInputs = new LinkedHashMap<>();
    private final Map<String, List<OptionItem<?>>> foreignKeyOptionsCache = new LinkedHashMap<>();
    private final Map<String, List<String>> tableColumnsCache = new LinkedHashMap<>();
    private final Map<UserRole, RolePolicy> rolePolicies = buildRolePolicies();

    private Stage primaryStage;
    private UserRole currentRole;
    private List<QueryDefinition> activeQueries = List.of();

    private ComboBox<String> sectionComboBox;
    private ComboBox<QueryView> queryComboBox;
    private VBox paramsContainer;
    private Button runQueryButton;
    private VBox resultCardsBox;
    private Label summaryLabel;
    private Label statusLabel;

    private ComboBox<String> viewTableComboBox;
    private VBox viewResultCardsBox;
    private Label viewSummaryLabel;

    private ComboBox<String> mutationTableComboBox;
    private ComboBox<DataOperation> mutationOperationComboBox;
    private ComboBox<OptionItem<?>> mutationRecordComboBox;
    private VBox mutationFormBox;
    private Label mutationHintLabel;

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        stage.setTitle("Информационная система театра");
        stage.setScene(buildLoginScene());
        stage.show();
        Platform.runLater(this::ensurePrimaryStageVisible);
    }

    @Override
    public void stop() throws Exception {
        databaseService.disconnect();
        super.stop();
    }

    private Scene buildLoginScene() {
        Label title = new Label("Информационная система театра");
        title.setStyle("-fx-font-size: 30px; -fx-font-weight: bold; -fx-text-fill: #0F172A;");

        Label subtitle = new Label("Выберите рабочую роль");
        subtitle.setStyle("-fx-font-size: 15px; -fx-text-fill: #475569;");

        VBox rolesBox = new VBox(10);
        for (UserRole role : UserRole.values()) {
            rolesBox.getChildren().add(buildRoleButton(role));
        }

        VBox card = new VBox(16, title, subtitle, new Separator(), rolesBox);
        card.setPadding(new Insets(26));
        card.setMaxWidth(760);
        card.setStyle("-fx-background-color: white; -fx-border-color: #D8E1EE; -fx-border-radius: 14; -fx-background-radius: 14;");

        StackPane root = new StackPane(card);
        root.setPadding(new Insets(40));
        root.setStyle("-fx-background-color: linear-gradient(to bottom, #F3F7FF, #EEF2F8);");
        return new Scene(root, 1400, 900);
    }

    private Button buildRoleButton(UserRole role) {
        RolePolicy policy = rolePolicies.get(role);

        Label head = new Label(role.displayName());
        head.setStyle("-fx-font-size: 17px; -fx-font-weight: bold; -fx-text-fill: #0F172A;");

        Label desc = new Label(policy.description());
        desc.setStyle("-fx-text-fill: #475569;");

        VBox content = new VBox(4, head, desc);
        Button button = new Button();
        button.setGraphic(content);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT);
        button.setStyle(
                "-fx-background-color: #FFFFFF;" +
                        "-fx-border-color: #C9D4E6;" +
                        "-fx-border-radius: 10;" +
                        "-fx-background-radius: 10;" +
                        "-fx-padding: 12 14 12 14;"
        );
        button.setOnAction(e -> loginAs(role));
        return button;
    }

    private void loginAs(UserRole role) {
        try {
            currentRole = role;
            connectToDatabase();
            ensureCorePlpgsqlObjects();
            ensureDatabaseRolesAndPermissions();
            activateDatabaseRole(role);
            activeQueries = allQueries.stream()
                    .filter(query -> currentPolicy().canUseQuery(query.id()))
                    .filter(query -> !hiddenQueryIds.contains(query.id()))
                    .toList();
            tableMetaCache.clear();
            loadLookupCache();
            primaryStage.setScene(buildWorkspaceScene());
            Platform.runLater(this::ensurePrimaryStageVisible);
        } catch (SQLException ex) {
            showError("Не удалось выполнить вход", ex);
        }
    }

    private void connectToDatabase() throws SQLException {
        if (databaseService.isConnected()) {
            databaseService.disconnect();
        }
        databaseService.connect(DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD);
    }

    private void ensureCorePlpgsqlObjects() throws SQLException {
        List<String> coreStatements = new ArrayList<>();
        for (String statement : QueryCatalog.setupStatements()) {
            String upper = statement.trim().toUpperCase(Locale.ROOT);
            if (upper.startsWith("CREATE TRIGGER")) {
                continue;
            }
            coreStatements.add(statement);
        }
        databaseService.executeScript(coreStatements);
    }

    private void ensureDatabaseRolesAndPermissions() throws SQLException {
        String createRolesSql = """
                DO $$
                BEGIN
                    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_hr') THEN
                        CREATE ROLE app_hr NOLOGIN;
                    END IF;
                    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_producer') THEN
                        CREATE ROLE app_producer NOLOGIN;
                    END IF;
                    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_repertoire_admin') THEN
                        CREATE ROLE app_repertoire_admin NOLOGIN;
                    END IF;
                    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_cashier') THEN
                        CREATE ROLE app_cashier NOLOGIN;
                    END IF;
                    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_director') THEN
                        CREATE ROLE app_director NOLOGIN;
                    END IF;
                END;
                $$;
                """;
        databaseService.executeSql(createRolesSql);

        databaseService.executeScript(List.of(
                "GRANT USAGE ON SCHEMA public TO app_hr, app_producer, app_repertoire_admin, app_cashier, app_director",
                "GRANT SELECT ON ALL TABLES IN SCHEMA public TO app_hr, app_producer, app_repertoire_admin, app_cashier, app_director",
                "GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO app_hr, app_producer, app_repertoire_admin, app_cashier, app_director",
                "ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO app_hr, app_producer, app_repertoire_admin, app_cashier, app_director",
                "REVOKE INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public FROM app_hr, app_producer, app_repertoire_admin, app_cashier, app_director",

                "GRANT INSERT, UPDATE, DELETE ON TABLE workers, actors, musicians, director_producers, designer_producers, conductor_producers, staff, actor_honors, actor_awards TO app_hr",
                "GRANT INSERT, UPDATE, DELETE ON TABLE spectacles, spectacle_authors, roles, casting TO app_producer",
                "GRANT INSERT, UPDATE, DELETE ON TABLE seasons, halls, shows, tours, tour_participants, pricing, seats TO app_repertoire_admin",
                "GRANT INSERT, UPDATE, DELETE ON TABLE tickets TO app_cashier",
                "GRANT INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO app_director",

                "REVOKE EXECUTE ON PROCEDURE add_actor(VARCHAR, VARCHAR, DATE, VARCHAR, NUMERIC, VARCHAR, NUMERIC) FROM PUBLIC",
                "REVOKE EXECUTE ON PROCEDURE add_show(INT, INT, INT, DATE, TIME, BOOLEAN) FROM PUBLIC",
                "REVOKE EXECUTE ON PROCEDURE add_musician(VARCHAR, VARCHAR, DATE, VARCHAR, NUMERIC, VARCHAR) FROM PUBLIC",
                "REVOKE EXECUTE ON PROCEDURE add_director(VARCHAR, VARCHAR, DATE, VARCHAR, NUMERIC, VARCHAR) FROM PUBLIC",
                "REVOKE EXECUTE ON PROCEDURE add_designer(VARCHAR, VARCHAR, DATE, VARCHAR, NUMERIC, VARCHAR) FROM PUBLIC",
                "REVOKE EXECUTE ON PROCEDURE add_conductor(VARCHAR, VARCHAR, DATE, VARCHAR, NUMERIC, VARCHAR) FROM PUBLIC",
                "REVOKE EXECUTE ON PROCEDURE add_staff(VARCHAR, VARCHAR, DATE, VARCHAR, NUMERIC, VARCHAR) FROM PUBLIC",
                "GRANT EXECUTE ON PROCEDURE add_actor(VARCHAR, VARCHAR, DATE, VARCHAR, NUMERIC, VARCHAR, NUMERIC) TO app_hr, app_director",
                "GRANT EXECUTE ON PROCEDURE add_show(INT, INT, INT, DATE, TIME, BOOLEAN) TO app_repertoire_admin, app_director",
                "GRANT EXECUTE ON PROCEDURE add_musician(VARCHAR, VARCHAR, DATE, VARCHAR, NUMERIC, VARCHAR) TO app_hr, app_director",
                "GRANT EXECUTE ON PROCEDURE add_director(VARCHAR, VARCHAR, DATE, VARCHAR, NUMERIC, VARCHAR) TO app_hr, app_director",
                "GRANT EXECUTE ON PROCEDURE add_designer(VARCHAR, VARCHAR, DATE, VARCHAR, NUMERIC, VARCHAR) TO app_hr, app_director",
                "GRANT EXECUTE ON PROCEDURE add_conductor(VARCHAR, VARCHAR, DATE, VARCHAR, NUMERIC, VARCHAR) TO app_hr, app_director",
                "GRANT EXECUTE ON PROCEDURE add_staff(VARCHAR, VARCHAR, DATE, VARCHAR, NUMERIC, VARCHAR) TO app_hr, app_director"
        ));
    }

    private void activateDatabaseRole(UserRole role) throws SQLException {
        databaseService.executeSql("RESET ROLE");
        databaseService.executeSql("SET ROLE " + role.databaseRole());
    }

    private Scene buildWorkspaceScene() {
        Label title = new Label("Рабочее место");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #0F172A;");

        Label roleBadge = new Label(currentRole.displayName());
        roleBadge.setStyle("-fx-background-color: #E4EEFF; -fx-text-fill: #1D4ED8; -fx-padding: 4 10 4 10; -fx-background-radius: 14;");

        Button logoutButton = new Button("Выйти");
        logoutButton.setOnAction(e -> logout());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox topBar = new HBox(12, title, roleBadge, spacer, logoutButton);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(0, 0, 12, 0));

        TabPane tabPane = new TabPane();
        Tab queryTab = new Tab("Запросы", buildQueryPane());
        queryTab.setClosable(false);
        tabPane.getTabs().add(queryTab);

        RolePolicy policy = currentPolicy();
        Tab viewTab = new Tab("Просмотр", buildViewPane());
        viewTab.setClosable(false);
        tabPane.getTabs().add(viewTab);

        if (!policy.writableTables().isEmpty() || !policy.procedures().isEmpty()) {
            Tab dataTab = new Tab("Изменение данных", buildDataManagementPane());
            dataTab.setClosable(false);
            tabPane.getTabs().add(dataTab);
        }

        statusLabel = new Label("Подключено к БД. Активная роль: " + currentRole.displayName());
        statusLabel.setPadding(new Insets(8, 0, 0, 0));
        statusLabel.setStyle("-fx-text-fill: #334155;");

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(14));
        root.setTop(topBar);
        root.setCenter(tabPane);
        root.setBottom(statusLabel);
        root.setStyle("-fx-background-color: #F6F8FC;");
        return new Scene(root, 1680, 1020);
    }

    private Node buildQueryPane() {
        sectionComboBox = new ComboBox<>();
        queryComboBox = new ComboBox<>();
        paramsContainer = new VBox(10);
        runQueryButton = new Button("Выполнить запрос");
        runQueryButton.setOnAction(e -> runSelectedQuery());
        runQueryButton.setMaxWidth(Double.MAX_VALUE);

        Label sectionLabel = new Label("Раздел");
        sectionLabel.setStyle("-fx-font-weight: bold;");
        Label queryLabel = new Label("Запрос");
        queryLabel.setStyle("-fx-font-weight: bold;");
        Label paramsLabel = new Label("Параметры");
        paramsLabel.setStyle("-fx-font-weight: bold;");

        VBox left = new VBox(
                10,
                sectionLabel, sectionComboBox,
                queryLabel, queryComboBox,
                new Separator(),
                paramsLabel, paramsContainer,
                runQueryButton
        );
        left.setPadding(new Insets(14));
        left.setStyle("-fx-background-color: white; -fx-border-color: #D8DFEA; -fx-border-radius: 10; -fx-background-radius: 10;");
        left.setMinWidth(480);

        resultCardsBox = new VBox(10);
        resultCardsBox.getChildren().add(createHintLabel("Выберите запрос и нажмите «Выполнить запрос»."));
        ScrollPane resultScroll = new ScrollPane(resultCardsBox);
        resultScroll.setFitToWidth(true);
        resultScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        resultScroll.setStyle("-fx-background: white; -fx-background-color: white;");

        summaryLabel = new Label("Результат пока не получен.");
        summaryLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #475569;");

        VBox right = new VBox(10, new Label("Результат"), resultScroll, summaryLabel);
        right.setPadding(new Insets(14));
        right.setStyle("-fx-background-color: white; -fx-border-color: #D8DFEA; -fx-border-radius: 10; -fx-background-radius: 10;");
        VBox.setVgrow(resultScroll, Priority.ALWAYS);

        SplitPane splitPane = new SplitPane(left, right);
        splitPane.setDividerPositions(0.30);

        initQuerySelectors();
        return splitPane;
    }

    private Node buildViewPane() {
        viewTableComboBox = createTableComboBox(currentPolicy().writableTables());
        Button refreshButton = new Button("Показать данные");
        refreshButton.setOnAction(e -> loadViewTable());
        refreshButton.setMaxWidth(Double.MAX_VALUE);

        Label tableLabel = new Label("Таблица");
        tableLabel.setStyle("-fx-font-weight: bold;");

        VBox left = new VBox(10, tableLabel, viewTableComboBox, refreshButton);
        left.setPadding(new Insets(14));
        left.setStyle("-fx-background-color: white; -fx-border-color: #D8DFEA; -fx-border-radius: 10; -fx-background-radius: 10;");
        left.setMinWidth(400);

        viewResultCardsBox = new VBox(10);
        viewResultCardsBox.getChildren().add(createHintLabel("Выберите таблицу и нажмите «Показать данные»."));
        ScrollPane resultScroll = new ScrollPane(viewResultCardsBox);
        resultScroll.setFitToWidth(true);
        resultScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        resultScroll.setStyle("-fx-background: white; -fx-background-color: white;");

        viewSummaryLabel = new Label("Результат пока не получен.");
        viewSummaryLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #475569;");

        VBox right = new VBox(10, new Label("Результат"), resultScroll, viewSummaryLabel);
        right.setPadding(new Insets(14));
        right.setStyle("-fx-background-color: white; -fx-border-color: #D8DFEA; -fx-border-radius: 10; -fx-background-radius: 10;");
        VBox.setVgrow(resultScroll, Priority.ALWAYS);

        SplitPane splitPane = new SplitPane(left, right);
        splitPane.setDividerPositions(0.24);

        viewTableComboBox.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null) {
                loadViewTable();
            }
        });

        if (!viewTableComboBox.getItems().isEmpty()) {
            viewTableComboBox.setValue(viewTableComboBox.getItems().get(0));
        } else {
            refreshButton.setDisable(true);
            viewResultCardsBox.getChildren().setAll(createHintLabel("Для текущей роли нет таблиц для просмотра."));
        }

        return splitPane;
    }

    private Node buildProceduresPane() {
        VBox root = new VBox(12);
        root.setPadding(new Insets(12));

        if (hasAnyHireProcedure()) {
            root.getChildren().add(buildHireWorkerPane());
        }
        if (currentPolicy().canUseProcedure("add_show")) {
            root.getChildren().add(buildAddShowPane());
        }
        if (root.getChildren().isEmpty()) {
            root.getChildren().add(createHintLabel("Для текущей роли процедуры недоступны."));
        }
        return root;
    }

    private Node buildDataManagementPane() {
        VBox root = new VBox(12);
        root.setPadding(new Insets(12));

        if (!currentPolicy().procedures().isEmpty()) {
            root.getChildren().add(buildProceduresSection());
        }
        if (!currentPolicy().writableTables().isEmpty()) {
            root.getChildren().add(buildTableMutationSection());
        } else if (currentPolicy().procedures().isEmpty()) {
            root.getChildren().add(createHintLabel("Для текущей роли нет операций изменения данных."));
        }
        return root;
    }

    private Node buildTableMutationSection() {
        mutationTableComboBox = createTableComboBox(currentPolicy().writableTables());
        mutationOperationComboBox = new ComboBox<>(FXCollections.observableArrayList(DataOperation.values()));
        mutationOperationComboBox.setValue(DataOperation.INSERT);

        mutationFormBox = new VBox(10);

        mutationHintLabel = new Label("Можно добавлять, изменять и удалять данные");
        mutationHintLabel.setStyle("-fx-text-fill: #64748B;");

        Button executeButton = new Button("Выполнить изменение");
        executeButton.setMaxWidth(Double.MAX_VALUE);
        executeButton.setOnAction(e -> runMutation());

        GridPane selectors = new GridPane();
        selectors.setHgap(10);
        selectors.setVgap(10);
        selectors.add(new Label("Таблица"), 0, 0);
        selectors.add(mutationTableComboBox, 1, 0);
        selectors.add(new Label("Операция"), 0, 1);
        selectors.add(mutationOperationComboBox, 1, 1);
        GridPane.setHgrow(mutationTableComboBox, Priority.ALWAYS);
        GridPane.setHgrow(mutationOperationComboBox, Priority.ALWAYS);

        VBox content = new VBox(10, selectors, new Separator(), mutationFormBox, mutationHintLabel, executeButton);
        content.setPadding(new Insets(14));
        content.setStyle("-fx-background-color: white; -fx-border-color: #D8DFEA; -fx-border-radius: 10; -fx-background-radius: 10;");

        mutationTableComboBox.valueProperty().addListener((obs, oldValue, newValue) -> rebuildMutationForm());
        mutationOperationComboBox.valueProperty().addListener((obs, oldValue, newValue) -> rebuildMutationForm());

        if (!mutationTableComboBox.getItems().isEmpty()) {
            mutationTableComboBox.setValue(mutationTableComboBox.getItems().get(0));
            rebuildMutationForm();
        }

        return content;
    }

    private void rebuildMutationForm() {
        mutationInputs.clear();
        mutationRecordComboBox = null;
        mutationFormBox.getChildren().clear();

        String table = mutationTableComboBox.getValue();
        DataOperation operation = mutationOperationComboBox.getValue();
        if (table == null || operation == null) {
            return;
        }

        try {
            TableMeta meta = loadTableMeta(table);

            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(8);

            int row = 0;
            if (operation != DataOperation.INSERT) {
                if (meta.primaryKeyColumn() == null) {
                    throw new IllegalStateException("У таблицы " + table + " не найден первичный ключ.");
                }
                List<OptionItem<?>> recordOptions = loadRecordOptions(meta);
                Label keyLabel = new Label("Запись");
                mutationRecordComboBox = new ComboBox<>(FXCollections.observableArrayList(recordOptions));
                if (!recordOptions.isEmpty()) {
                    mutationRecordComboBox.setValue(recordOptions.get(0));
                }
                grid.add(keyLabel, 0, row);
                grid.add(mutationRecordComboBox, 1, row);
                GridPane.setHgrow(mutationRecordComboBox, Priority.ALWAYS);
                row++;
            }

            List<ColumnMeta> editableColumns = switch (operation) {
                case INSERT -> insertableColumns(meta.columns());
                case UPDATE -> updatableColumns(meta.columns(), meta.primaryKeyColumn());
                case DELETE -> List.of();
            };

            for (ColumnMeta column : editableColumns) {
                String fieldLabel = userFacingColumnLabel(meta, column);
                if ("Тип".equals(fieldLabel)) {
                    continue;
                }
                String suffix = (column.nullable() || column.hasDefault()) ? " (необяз.)" : "";
                Label label = new Label(fieldLabel + suffix);
                Node control = createMutationControl(meta, column, operation);
                mutationInputs.put(column.name(), new MutationInput(column, control));
                grid.add(label, 0, row);
                grid.add(control, 1, row);
                if (control instanceof Region region) {
                    GridPane.setHgrow(region, Priority.ALWAYS);
                    region.setMaxWidth(Double.MAX_VALUE);
                }
                row++;
            }

            mutationFormBox.getChildren().add(grid);
        } catch (SQLException ex) {
            showError("Ошибка загрузки структуры таблицы", ex);
        } catch (IllegalStateException ex) {
            showWarning(ex.getMessage());
        }
    }

    private void runMutation() {
        String table = mutationTableComboBox.getValue();
        DataOperation operation = mutationOperationComboBox.getValue();
        if (table == null || operation == null) {
            showWarning("Выберите таблицу и операцию.");
            return;
        }
        if (!currentPolicy().canWriteTable(table)) {
            showWarning("У текущей роли нет прав на изменение таблицы: " + humanizeTableName(table));
            return;
        }

        try {
            TableMeta meta = loadTableMeta(table);
            QueryResult result = switch (operation) {
                case INSERT -> executeInsert(table, meta);
                case UPDATE -> executeUpdate(table, meta);
                case DELETE -> executeDelete(table, meta);
            };

            foreignKeyOptionsCache.clear();
            loadLookupCache();
            refreshParamControlsIfNeeded();
            rebuildMutationForm();
            renderResult(result);
            setStatus("Операция " + operation.displayName() + " для таблицы «" + humanizeTableName(table) + "» выполнена.");
        } catch (SQLException ex) {
            showError("Ошибка изменения данных", ex);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            showWarning(ex.getMessage());
        }
    }

    private void loadViewTable() {
        String table = viewTableComboBox == null ? null : viewTableComboBox.getValue();
        if (table == null || table.isBlank()) {
            showWarning("Выберите таблицу для просмотра.");
            return;
        }
        if (!currentPolicy().canWriteTable(table)) {
            showWarning("У текущей роли нет доступа к просмотру таблицы: " + humanizeTableName(table));
            return;
        }

        try {
            TableMeta meta = loadTableMeta(table);
            String sql = buildViewSql(meta);
            QueryResult result = databaseService.executeSql(sql);
            renderResultInto(result, viewResultCardsBox, viewSummaryLabel);
            setStatus("Показаны данные таблицы «" + humanizeTableName(table) + "».");
        } catch (SQLException ex) {
            showError("Ошибка просмотра таблицы", ex);
        } catch (IllegalStateException ex) {
            showWarning(ex.getMessage());
        }
    }

    private OptionItem<?> getSelectedRecordOption() {
        if (mutationRecordComboBox == null) {
            throw new IllegalArgumentException("Выберите запись.");
        }
        OptionItem<?> selected = mutationRecordComboBox.getValue();
        if (selected == null) {
            throw new IllegalArgumentException("Выберите запись.");
        }
        return selected;
    }

    private boolean isMutationInputEmpty(MutationInput input) {
        Node control = input.control();
        if (control instanceof TextField textField) {
            return safeTrim(textField.getText()).isEmpty();
        }
        if (control instanceof DatePicker datePicker) {
            return datePicker.getValue() == null;
        }
        if (control instanceof ComboBox<?> comboBox) {
            Object selected = comboBox.getValue();
            if (selected == null) {
                return true;
            }
            if (selected instanceof OptionItem<?> optionItem) {
                return optionItem.value() == NO_CHANGE;
            }
            return false;
        }
        return true;
    }

    private Object readMutationInputValue(MutationInput input) {
        Node control = input.control();
        ColumnMeta column = input.column();

        if (control instanceof TextField textField) {
            String raw = safeTrim(textField.getText());
            return parseInputValue(raw, column);
        }
        if (control instanceof DatePicker datePicker) {
            LocalDate value = datePicker.getValue();
            if (value == null) {
                return null;
            }
            return Date.valueOf(value);
        }
        if (control instanceof ComboBox<?> comboBox) {
            Object selected = comboBox.getValue();
            if (selected == null) {
                return null;
            }
            if (selected instanceof OptionItem<?> optionItem) {
                if (optionItem.value() == NO_CHANGE) {
                    return null;
                }
                return optionItem.value();
            }
            if (selected instanceof String text) {
                return parseInputValue(text, column);
            }
        }
        throw new IllegalStateException("Не удалось прочитать значение поля: " + column.name());
    }

    private Node createMutationControl(TableMeta meta, ColumnMeta column, DataOperation operation) throws SQLException {
        ForeignKeyMeta foreignKey = meta.foreignKeys().get(column.name());
        if (foreignKey != null) {
            List<OptionItem<?>> options = new ArrayList<>();
            if (operation == DataOperation.UPDATE) {
                options.add(new OptionItem<>("— не менять —", NO_CHANGE));
            }
            if (column.nullable()) {
                options.add(new OptionItem<>("— очистить значение —", null));
            }
            options.addAll(loadForeignKeyOptions(foreignKey));

            ComboBox<OptionItem<?>> combo = new ComboBox<>(FXCollections.observableArrayList(options));
            if (!combo.getItems().isEmpty()) {
                combo.setValue(combo.getItems().get(0));
            }
            combo.setMaxWidth(Double.MAX_VALUE);
            return combo;
        }

        String normalizedType = column.dataType().toLowerCase(Locale.ROOT);
        if ("gender".equalsIgnoreCase(column.name())) {
            List<OptionItem<?>> options = new ArrayList<>();
            if (operation == DataOperation.UPDATE) {
                options.add(new OptionItem<>("— не менять —", NO_CHANGE));
            }
            options.add(new OptionItem<>("М", "М"));
            options.add(new OptionItem<>("Ж", "Ж"));
            ComboBox<OptionItem<?>> combo = new ComboBox<>(FXCollections.observableArrayList(options));
            combo.setValue(combo.getItems().get(0));
            combo.setMaxWidth(Double.MAX_VALUE);
            return combo;
        }
        if (normalizedType.contains("boolean")) {
            List<OptionItem<?>> options = new ArrayList<>();
            if (operation == DataOperation.UPDATE) {
                options.add(new OptionItem<>("— не менять —", NO_CHANGE));
            }
            options.add(new OptionItem<>("Да", true));
            options.add(new OptionItem<>("Нет", false));
            ComboBox<OptionItem<?>> combo = new ComboBox<>(FXCollections.observableArrayList(options));
            combo.setValue(combo.getItems().get(0));
            combo.setMaxWidth(Double.MAX_VALUE);
            return combo;
        }
        if (normalizedType.equals("date")) {
            return new DatePicker();
        }
        if (normalizedType.startsWith("time")) {
            ComboBox<String> combo = new ComboBox<>(FXCollections.observableArrayList(
                    "10:00", "12:00", "14:00", "16:00", "18:00", "19:00", "20:00", "21:00"
            ));
            combo.setEditable(true);
            combo.setPromptText("HH:mm:ss");
            combo.setMaxWidth(Double.MAX_VALUE);
            return combo;
        }

        TextField field = new TextField();
        field.setPromptText(exampleForColumn(column));
        return field;
    }

    private List<OptionItem<?>> loadRecordOptions(TableMeta meta) throws SQLException {
        if (meta.primaryKeyColumn() == null) {
            return List.of();
        }
        ColumnMeta primaryKey = findColumn(meta.columns(), meta.primaryKeyColumn());
        String sql = buildRecordOptionsSql(meta);
        if (sql == null) {
            List<ColumnMeta> displayColumns = new ArrayList<>();
            for (ColumnMeta column : meta.columns()) {
                if (column.name().equals(meta.primaryKeyColumn())) {
                    continue;
                }
                if (isTechnicalIdColumn(column.name())) {
                    continue;
                }
                displayColumns.add(column);
                if (displayColumns.size() == 3) {
                    break;
                }
            }

            String selectedColumns = meta.primaryKeyColumn();
            if (!displayColumns.isEmpty()) {
                selectedColumns = selectedColumns + ", " + displayColumns.stream().map(ColumnMeta::name).collect(Collectors.joining(", "));
            }
            sql = "SELECT " + selectedColumns + " FROM " + meta.tableName() + " ORDER BY " + meta.primaryKeyColumn() + " LIMIT 500";
        }
        QueryResult result = databaseService.executeSql(sql);

        List<OptionItem<?>> options = new ArrayList<>();
        int index = 1;
        for (List<String> row : result.rows()) {
            if (row.isEmpty()) {
                continue;
            }
            Object idValue = parseInputValue(row.get(0), primaryKey);
            List<String> parts = new ArrayList<>();
            for (int i = 1; i < row.size(); i++) {
                String value = row.get(i);
                if (value != null && !value.isBlank()) {
                    parts.add(value.trim());
                }
            }
            String label = parts.isEmpty() ? "Запись " + index : String.join(" | ", parts);
            options.add(new OptionItem<>(label, idValue));
            index++;
        }
        return List.copyOf(options);
    }

    private String buildRecordOptionsSql(TableMeta meta) {
        String tableName = meta.tableName();
        String table = normalizeTableName(tableName);
        String pk = meta.primaryKeyColumn();
        if (pk == null) {
            return null;
        }

        return switch (table) {
            case "worker_types" -> """
                    SELECT wt.type_id,
                           wt.type_name AS label
                    FROM worker_types wt
                    ORDER BY wt.type_name
                    LIMIT 500
                    """;
            case "countries" -> """
                    SELECT c.country_id,
                           c.country_name AS label
                    FROM countries c
                    ORDER BY c.country_name
                    LIMIT 500
                    """;
            case "city" -> """
                    SELECT c.city_id,
                           c.city_name AS label
                    FROM city c
                    ORDER BY c.city_name
                    LIMIT 500
                    """;
            case "theater_schools" -> """
                    SELECT ts.school_id,
                           ts.school_name || ' — ' || c.city_name AS label
                    FROM theater_schools ts
                             JOIN city c ON c.city_id = ts.city_id
                    ORDER BY ts.school_name
                    LIMIT 500
                    """;
            case "genres" -> """
                    SELECT g.genre_id,
                           g.genre_name AS label
                    FROM genres g
                    ORDER BY g.genre_name
                    LIMIT 500
                    """;
            case "age_categories" -> """
                    SELECT ac.category_id,
                           ac.category_name AS label
                    FROM age_categories ac
                    ORDER BY ac.category_name
                    LIMIT 500
                    """;
            case "theaters" -> """
                    SELECT t.theater_id,
                           t.theater_name || ' — ' || c.city_name AS label
                    FROM theaters t
                             JOIN city c ON c.city_id = t.city_id
                    ORDER BY t.theater_name
                    LIMIT 500
                    """;
            case "current_theater" -> """
                    SELECT ct.theater_id,
                           t.theater_name || ' — ' || c.city_name AS label
                    FROM current_theater ct
                             JOIN theaters t ON t.theater_id = ct.theater_id
                             JOIN city c ON c.city_id = t.city_id
                    ORDER BY label
                    LIMIT 500
                    """;
            case "seat_types" -> """
                    SELECT st.seat_type_id,
                           st.type_name AS label
                    FROM seat_types st
                    ORDER BY st.type_name
                    LIMIT 500
                    """;
            case "seasons" -> """
                    SELECT s.season_id,
                           s.season_name || ' (' || s.start_date || ' - ' || s.end_date || ')' AS label
                    FROM seasons s
                    ORDER BY s.start_date DESC, s.season_name
                    LIMIT 500
                    """;
            case "halls" -> """
                    SELECT h.hall_id,
                           h.hall_name AS label
                    FROM halls h
                    ORDER BY h.hall_name
                    LIMIT 500
                    """;
            case "workers" -> """
                    SELECT w.worker_id,
                           w.last_name || ' ' || w.first_name || ' (' || wt.type_name || ')' AS label
                    FROM workers w
                             JOIN worker_types wt ON wt.type_id = w.worker_type_id
                    ORDER BY label
                    LIMIT 500
                    """;
            case "actors" -> """
                    SELECT a.worker_id,
                           w.last_name || ' ' || w.first_name ||
                           COALESCE(' — ' || a.voice_type, '') ||
                           COALESCE(', ' || a.height || ' см', '') AS label
                    FROM actors a
                             JOIN workers w ON w.worker_id = a.worker_id
                    ORDER BY label
                    LIMIT 500
                    """;
            case "musicians" -> """
                    SELECT m.worker_id,
                           w.last_name || ' ' || w.first_name || ' — ' || m.instrument AS label
                    FROM musicians m
                             JOIN workers w ON w.worker_id = m.worker_id
                    ORDER BY label
                    LIMIT 500
                    """;
            case "director_producers" -> """
                    SELECT d.worker_id,
                           w.last_name || ' ' || w.first_name AS label
                    FROM director_producers d
                             JOIN workers w ON w.worker_id = d.worker_id
                    ORDER BY label
                    LIMIT 500
                    """;
            case "designer_producers" -> """
                    SELECT d.worker_id,
                           w.last_name || ' ' || w.first_name AS label
                    FROM designer_producers d
                             JOIN workers w ON w.worker_id = d.worker_id
                    ORDER BY label
                    LIMIT 500
                    """;
            case "conductor_producers" -> """
                    SELECT c.worker_id,
                           w.last_name || ' ' || w.first_name AS label
                    FROM conductor_producers c
                             JOIN workers w ON w.worker_id = c.worker_id
                    ORDER BY label
                    LIMIT 500
                    """;
            case "staff" -> """
                    SELECT s.worker_id,
                           w.last_name || ' ' || w.first_name || ' — ' || s.position AS label
                    FROM staff s
                             JOIN workers w ON w.worker_id = s.worker_id
                    ORDER BY label
                    LIMIT 500
                    """;
            case "actor_honors" -> """
                    SELECT ah.honor_id,
                           w.last_name || ' ' || w.first_name || ' — ' || ah.honor_name ||
                           ' (' || ah.award_date || ')' AS label
                    FROM actor_honors ah
                             JOIN workers w ON w.worker_id = ah.worker_id
                    ORDER BY ah.award_date DESC, label
                    LIMIT 500
                    """;
            case "actor_awards" -> """
                    SELECT aa.award_id,
                           w.last_name || ' ' || w.first_name || ' — ' || aa.competition_name ||
                           COALESCE(' / ' || aa.award_name, '') ||
                           ' (' || aa.award_date || ')' AS label
                    FROM actor_awards aa
                             JOIN workers w ON w.worker_id = aa.worker_id
                    ORDER BY aa.award_date DESC, label
                    LIMIT 500
                    """;
            case "authors" -> """
                    SELECT a.author_id,
                           a.last_name || ' ' || a.first_name || ' — ' || c.country_name AS label
                    FROM authors a
                             JOIN countries c ON c.country_id = a.country_id
                    ORDER BY label
                    LIMIT 500
                    """;
            case "spectacles" -> """
                    SELECT s.spectacle_id,
                           s.title || ' — ' || g.genre_name AS label
                    FROM spectacles s
                             JOIN genres g ON g.genre_id = s.genre_id
                    ORDER BY label
                    LIMIT 500
                    """;
            case "spectacle_authors" -> """
                    SELECT sa.spectacle_id,
                           s.title || ' — ' || a.last_name || ' ' || a.first_name AS label
                    FROM spectacle_authors sa
                             JOIN spectacles s ON s.spectacle_id = sa.spectacle_id
                             JOIN authors a ON a.author_id = sa.author_id
                    ORDER BY label
                    LIMIT 500
                    """;
            case "roles" -> """
                    SELECT r.role_id,
                           r.role_name || ' — ' || s.title AS label
                    FROM roles r
                             JOIN spectacles s ON s.spectacle_id = r.spectacle_id
                    ORDER BY label
                    LIMIT 500
                    """;
            case "casting" -> """
                    SELECT c.casting_id,
                           r.role_name || ' — ' || w.last_name || ' ' || w.first_name ||
                           CASE WHEN c.is_understudy THEN ' (дублёр)' ELSE '' END AS label
                    FROM casting c
                             JOIN roles r ON r.role_id = c.role_id
                             JOIN workers w ON w.worker_id = c.actor_id
                    ORDER BY label
                    LIMIT 500
                    """;
            case "shows" -> """
                    SELECT sh.show_id,
                           sp.title || ' — ' || sh.show_date || ' ' || sh.show_time ||
                           ' — ' || h.hall_name AS label
                    FROM shows sh
                             JOIN spectacles sp ON sp.spectacle_id = sh.spectacle_id
                             JOIN halls h ON h.hall_id = sh.hall_id
                    ORDER BY sh.show_date DESC, sh.show_time DESC, sp.title
                    LIMIT 500
                    """;
            case "tickets" -> """
                    SELECT t.ticket_id,
                           sp.title || ' — ' || sh.show_date || ' ' || sh.show_time ||
                           ' — ' || h.hall_name || ', ряд ' || se.row_number || ', место ' || se.seat_number AS label
                    FROM tickets t
                             JOIN shows sh ON sh.show_id = t.show_id
                             JOIN spectacles sp ON sp.spectacle_id = sh.spectacle_id
                             JOIN seats se ON se.seat_id = t.seat_id
                             JOIN halls h ON h.hall_id = se.hall_id
                    ORDER BY sh.show_date DESC, sh.show_time DESC, label
                    LIMIT 500
                    """;
            case "pricing" -> """
                    SELECT p.pricing_id,
                           sp.title || ' — ' || st.type_name ||
                           CASE WHEN p.is_premiere THEN ' (премьера)' ELSE '' END AS label
                    FROM pricing p
                             JOIN spectacles sp ON sp.spectacle_id = p.spectacle_id
                             JOIN seat_types st ON st.seat_type_id = p.seat_type_id
                    ORDER BY label
                    LIMIT 500
                    """;
            case "seats" -> """
                    SELECT s.seat_id,
                           h.hall_name || ', ряд ' || s.row_number || ', место ' || s.seat_number ||
                           ' — ' || st.type_name AS label
                    FROM seats s
                             JOIN halls h ON h.hall_id = s.hall_id
                             JOIN seat_types st ON st.seat_type_id = s.seat_type_id
                    ORDER BY h.hall_name, s.row_number, s.seat_number
                    LIMIT 500
                    """;
            case "tours" -> """
                    SELECT t.tour_id,
                           COALESCE(sp.title, 'Без спектакля') || ' — ' ||
                           tf.theater_name || ' → ' || tt.theater_name ||
                           ' (' || t.start_date || ' - ' || t.end_date || ')' AS label
                    FROM tours t
                             LEFT JOIN spectacles sp ON sp.spectacle_id = t.spectacle_id
                             JOIN theaters tf ON tf.theater_id = t.from_theater_id
                             JOIN theaters tt ON tt.theater_id = t.to_theater_id
                    ORDER BY t.start_date DESC, label
                    LIMIT 500
                    """;
            case "tour_participants" -> """
                    SELECT tp.tour_id,
                           COALESCE(sp.title, 'Без спектакля') || ' — ' ||
                           w.last_name || ' ' || w.first_name || ' (' || t.start_date || ')' AS label
                    FROM tour_participants tp
                             JOIN tours t ON t.tour_id = tp.tour_id
                             LEFT JOIN spectacles sp ON sp.spectacle_id = t.spectacle_id
                             JOIN workers w ON w.worker_id = tp.worker_id
                    ORDER BY t.start_date DESC, label
                    LIMIT 500
                    """;
            default -> "SELECT " + pk + ", " + pk + "::text AS label FROM " + tableName + " ORDER BY " + pk + " LIMIT 500";
        };
    }

    private List<OptionItem<?>> loadForeignKeyOptions(ForeignKeyMeta foreignKey) throws SQLException {
        String cacheKey = foreignKey.columnName() + "->" + foreignKey.referencedTable() + "." + foreignKey.referencedColumn();
        List<OptionItem<?>> cached = foreignKeyOptionsCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        TableMeta referencedMeta = loadTableMeta(foreignKey.referencedTable());
        ColumnMeta referencedPk = findColumn(referencedMeta.columns(), foreignKey.referencedColumn());
        String sql = buildForeignKeyOptionsSql(foreignKey);
        QueryResult result = databaseService.executeSql(sql);

        List<OptionItem<?>> options = new ArrayList<>();
        for (List<String> row : result.rows()) {
            if (row.size() < 2) {
                continue;
            }
            Object idValue = parseInputValue(row.get(0), referencedPk);
            String label = row.get(1) == null || row.get(1).isBlank() ? "Без названия" : row.get(1);
            options.add(new OptionItem<>(label, idValue));
        }

        List<OptionItem<?>> immutable = List.copyOf(options);
        foreignKeyOptionsCache.put(cacheKey, immutable);
        return immutable;
    }

    private String buildForeignKeyOptionsSql(ForeignKeyMeta foreignKey) {
        String sourceTable = foreignKey.referencedTable();
        String table = normalizeTableName(sourceTable);
        String key = foreignKey.referencedColumn();

        return switch (table) {
            case "workers" -> """
                    SELECT w.worker_id,
                           w.last_name || ' ' || w.first_name || ' (' || w.birth_date || ')' AS label
                    FROM workers w
                    ORDER BY label
                    """;
            case "actors" -> """
                    SELECT a.worker_id,
                           w.last_name || ' ' || w.first_name ||
                           COALESCE(' — ' || a.voice_type, '') AS label
                    FROM actors a
                             JOIN workers w ON w.worker_id = a.worker_id
                    ORDER BY label
                    """;
            case "musicians" -> """
                    SELECT m.worker_id,
                           w.last_name || ' ' || w.first_name ||
                           COALESCE(' — ' || m.instrument, '') AS label
                    FROM musicians m
                             JOIN workers w ON w.worker_id = m.worker_id
                    ORDER BY label
                    """;
            case "director_producers" -> """
                    SELECT d.worker_id,
                           w.last_name || ' ' || w.first_name ||
                           COALESCE(' — ' || d.specialization, '') AS label
                    FROM director_producers d
                             JOIN workers w ON w.worker_id = d.worker_id
                    ORDER BY label
                    """;
            case "designer_producers" -> """
                    SELECT d.worker_id,
                           w.last_name || ' ' || w.first_name ||
                           COALESCE(' — ' || d.art_style, '') AS label
                    FROM designer_producers d
                             JOIN workers w ON w.worker_id = d.worker_id
                    ORDER BY label
                    """;
            case "conductor_producers" -> """
                    SELECT c.worker_id,
                           w.last_name || ' ' || w.first_name ||
                           COALESCE(' — ' || c.orchestra_type, '') AS label
                    FROM conductor_producers c
                             JOIN workers w ON w.worker_id = c.worker_id
                    ORDER BY label
                    """;
            case "staff" -> """
                    SELECT s.worker_id,
                           w.last_name || ' ' || w.first_name ||
                           COALESCE(' — ' || s.position, '') AS label
                    FROM staff s
                             JOIN workers w ON w.worker_id = s.worker_id
                    ORDER BY label
                    """;
            case "roles" -> """
                    SELECT r.role_id,
                           r.role_name || ' — ' || sp.title AS label
                    FROM roles r
                             JOIN spectacles sp ON sp.spectacle_id = r.spectacle_id
                    ORDER BY label
                    """;
            case "shows" -> """
                    SELECT sh.show_id,
                           sp.title || ' (' || sh.show_date || ' ' || sh.show_time || ')' AS label
                    FROM shows sh
                             JOIN spectacles sp ON sp.spectacle_id = sh.spectacle_id
                    ORDER BY sh.show_date, sh.show_time, sp.title
                    """;
            case "seats" -> """
                    SELECT s.seat_id,
                           h.hall_name || ', ряд ' || s.row_number || ', место ' || s.seat_number AS label
                    FROM seats s
                             JOIN halls h ON h.hall_id = s.hall_id
                    ORDER BY h.hall_name, s.row_number, s.seat_number
                    """;
            case "tours" -> """
                    SELECT t.tour_id,
                           COALESCE(sp.title, 'Без спектакля') || ' — ' ||
                           tf.theater_name || ' → ' || tt.theater_name ||
                           ' (' || t.start_date || ' - ' || t.end_date || ')' AS label
                    FROM tours t
                             LEFT JOIN spectacles sp ON sp.spectacle_id = t.spectacle_id
                             JOIN theaters tf ON tf.theater_id = t.from_theater_id
                             JOIN theaters tt ON tt.theater_id = t.to_theater_id
                    ORDER BY t.start_date DESC, label
                    """;
            default -> "SELECT " + key + ", " + foreignKey.displayExpression() + " AS label FROM "
                    + sourceTable + " ORDER BY label";
        };
    }

    private String userFacingColumnLabel(TableMeta meta, ColumnMeta column) {
        ForeignKeyMeta foreignKey = meta.foreignKeys().get(column.name());
        if (foreignKey != null) {
            return humanizeTableName(foreignKey.referencedTable());
        }
        String label = humanizeColumnName(column.name());
        if (label.toLowerCase(Locale.ROOT).endsWith(" id")) {
            return label.substring(0, label.length() - 3).trim();
        }
        return label;
    }

    private String exampleForColumn(ColumnMeta column) {
        String name = column.name().toLowerCase(Locale.ROOT);
        if ("gender".equals(name)) {
            return "М или Ж";
        }
        if (name.contains("date")) {
            return "YYYY-MM-DD";
        }
        if (name.contains("time")) {
            return "HH:mm:ss";
        }
        if (name.contains("price") || name.contains("salary")) {
            return "Например: 1500.00";
        }
        if (name.contains("year")) {
            return "Например: 2024";
        }
        if (name.contains("count") || name.contains("number")) {
            return "Например: 1";
        }
        return "Введите значение";
    }

    private QueryResult executeInsert(String table, TableMeta meta) throws SQLException {
        List<ColumnMeta> columnsToBind = new ArrayList<>();
        List<Object> valuesToBind = new ArrayList<>();

        for (ColumnMeta column : insertableColumns(meta.columns())) {
            MutationInput input = mutationInputs.get(column.name());
            if (input == null) {
                continue;
            }
            if (isMutationInputEmpty(input)) {
                if (!column.nullable() && !column.hasDefault()) {
                    throw new IllegalArgumentException("Поле \"" + userFacingColumnLabel(meta, column) + "\" обязательно для INSERT.");
                }
                continue;
            }

            columnsToBind.add(column);
            valuesToBind.add(readMutationInputValue(input));
        }

        if (columnsToBind.isEmpty()) {
            return databaseService.executeSql("INSERT INTO " + table + " DEFAULT VALUES");
        }

        String columnList = columnsToBind.stream().map(ColumnMeta::name).collect(Collectors.joining(", "));
        String placeholders = columnsToBind.stream().map(col -> "?").collect(Collectors.joining(", "));
        String sql = "INSERT INTO " + table + " (" + columnList + ") VALUES (" + placeholders + ")";
        return databaseService.executePrepared(sql, ps -> bindPreparedValues(ps, columnsToBind, valuesToBind, 1));
    }

    private QueryResult executeUpdate(String table, TableMeta meta) throws SQLException {
        if (meta.primaryKeyColumn() == null) {
            throw new IllegalStateException("У таблицы " + table + " не найден первичный ключ.");
        }
        OptionItem<?> selectedRecord = getSelectedRecordOption();
        ColumnMeta primaryKey = findColumn(meta.columns(), meta.primaryKeyColumn());
        Object keyValue = selectedRecord.value();

        List<ColumnMeta> columnsToBind = new ArrayList<>();
        List<Object> valuesToBind = new ArrayList<>();
        for (ColumnMeta column : updatableColumns(meta.columns(), meta.primaryKeyColumn())) {
            MutationInput input = mutationInputs.get(column.name());
            if (input == null || isMutationInputEmpty(input)) {
                continue;
            }
            columnsToBind.add(column);
            valuesToBind.add(readMutationInputValue(input));
        }

        if (columnsToBind.isEmpty()) {
            throw new IllegalArgumentException("Укажите хотя бы одно поле для изменения.");
        }

        String setClause = columnsToBind.stream().map(column -> column.name() + " = ?").collect(Collectors.joining(", "));
        String sql = "UPDATE " + table + " SET " + setClause + " WHERE " + meta.primaryKeyColumn() + " = ?";
        return databaseService.executePrepared(sql, ps -> {
            int next = bindPreparedValues(ps, columnsToBind, valuesToBind, 1);
            bindPreparedValue(ps, next, primaryKey, keyValue);
        });
    }

    private QueryResult executeDelete(String table, TableMeta meta) throws SQLException {
        if (meta.primaryKeyColumn() == null) {
            throw new IllegalStateException("У таблицы " + table + " не найден первичный ключ.");
        }
        OptionItem<?> selectedRecord = getSelectedRecordOption();
        ColumnMeta primaryKey = findColumn(meta.columns(), meta.primaryKeyColumn());
        Object keyValue = selectedRecord.value();

        String sql = "DELETE FROM " + table + " WHERE " + meta.primaryKeyColumn() + " = ?";
        return databaseService.executePrepared(sql, ps -> bindPreparedValue(ps, 1, primaryKey, keyValue));
    }

    private int bindPreparedValues(PreparedStatement statement, List<ColumnMeta> columns, List<Object> values, int startIndex) throws SQLException {
        int index = startIndex;
        for (int i = 0; i < columns.size(); i++) {
            bindPreparedValue(statement, index, columns.get(i), values.get(i));
            index++;
        }
        return index;
    }

    private void bindPreparedValue(PreparedStatement statement, int index, ColumnMeta column, Object value) throws SQLException {
        if (value == null) {
            statement.setNull(index, sqlTypeFor(column.dataType()));
            return;
        }
        statement.setObject(index, value);
    }

    private int sqlTypeFor(String dataType) {
        String normalized = dataType.toLowerCase(Locale.ROOT);
        if (normalized.contains("timestamp")) {
            return Types.TIMESTAMP;
        }
        if (normalized.equals("date")) {
            return Types.DATE;
        }
        if (normalized.startsWith("time")) {
            return Types.TIME;
        }
        if (normalized.contains("bigint")) {
            return Types.BIGINT;
        }
        if (normalized.contains("smallint")) {
            return Types.SMALLINT;
        }
        if (normalized.contains("integer")) {
            return Types.INTEGER;
        }
        if (normalized.contains("numeric") || normalized.contains("decimal")) {
            return Types.NUMERIC;
        }
        if (normalized.contains("double")) {
            return Types.DOUBLE;
        }
        if (normalized.contains("real")) {
            return Types.REAL;
        }
        if (normalized.contains("boolean")) {
            return Types.BOOLEAN;
        }
        return Types.VARCHAR;
    }

    private Object parseInputValue(String raw, ColumnMeta column) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalizedType = column.dataType().toLowerCase(Locale.ROOT);
        String value = raw.trim();

        try {
            if (normalizedType.contains("bigint")) {
                return Long.parseLong(value);
            }
            if (normalizedType.contains("smallint") || normalizedType.contains("integer")) {
                return Integer.parseInt(value);
            }
            if (normalizedType.contains("numeric") || normalizedType.contains("decimal")) {
                return new BigDecimal(value);
            }
            if (normalizedType.contains("double") || normalizedType.contains("real")) {
                return Double.parseDouble(value);
            }
            if (normalizedType.contains("boolean")) {
                if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("t") || value.equals("1") || value.equalsIgnoreCase("да")) {
                    return true;
                }
                if (value.equalsIgnoreCase("false") || value.equalsIgnoreCase("f") || value.equals("0") || value.equalsIgnoreCase("нет")) {
                    return false;
                }
                throw new IllegalArgumentException("Некорректное логическое значение для поля \"" + column.name() + "\".");
            }
            if (normalizedType.equals("date")) {
                return Date.valueOf(LocalDate.parse(value));
            }
            if (normalizedType.startsWith("time")) {
                return Time.valueOf(parseTime(value));
            }
            if (normalizedType.contains("timestamp")) {
                return Timestamp.valueOf(value.replace('T', ' '));
            }
            return value;
        } catch (DateTimeParseException | NumberFormatException ex) {
            throw new IllegalArgumentException("Некорректное значение поля \"" + column.name() + "\" (" + column.dataType() + ").");
        }
    }

    private List<ColumnMeta> insertableColumns(List<ColumnMeta> columns) {
        List<ColumnMeta> result = new ArrayList<>();
        for (ColumnMeta column : columns) {
            if (!column.autoGenerated()) {
                if (column.primaryKey() && !column.foreignKey()) {
                    continue;
                }
                if (isTechnicalIdColumn(column.name()) && !column.foreignKey()) {
                    continue;
                }
                result.add(column);
            }
        }
        return result;
    }

    private List<ColumnMeta> updatableColumns(List<ColumnMeta> columns, String primaryKey) {
        List<ColumnMeta> result = new ArrayList<>();
        for (ColumnMeta column : columns) {
            if (column.autoGenerated()) {
                continue;
            }
            if (primaryKey != null && primaryKey.equals(column.name())) {
                continue;
            }
            if (isTechnicalIdColumn(column.name()) && !column.foreignKey()) {
                continue;
            }
            result.add(column);
        }
        return result;
    }

    private boolean isTechnicalIdColumn(String columnName) {
        String normalized = columnName.toLowerCase(Locale.ROOT);
        return "id".equals(normalized) || normalized.endsWith("_id");
    }

    private ColumnMeta findColumn(List<ColumnMeta> columns, String name) {
        for (ColumnMeta column : columns) {
            if (column.name().equals(name)) {
                return column;
            }
        }
        throw new IllegalStateException("Не найден столбец: " + name);
    }

    private TableMeta loadTableMeta(String tableName) throws SQLException {
        TableMeta cached = tableMetaCache.get(tableName);
        if (cached != null) {
            return cached;
        }

        String columnSql = """
                SELECT c.column_name,
                       c.data_type,
                       c.is_nullable,
                       COALESCE(c.column_default, ''),
                       c.is_identity
                FROM information_schema.columns c
                WHERE c.table_schema = 'public'
                  AND c.table_name = ?
                ORDER BY c.ordinal_position
                """;

        QueryResult columnsResult = databaseService.executePrepared(columnSql, ps -> ps.setString(1, tableName));
        if (columnsResult.rows().isEmpty()) {
            throw new IllegalStateException("Таблица не найдена: " + tableName);
        }

        List<ColumnMeta> columns = new ArrayList<>();
        for (List<String> row : columnsResult.rows()) {
            String columnName = row.get(0);
            String dataType = row.get(1);
            boolean nullable = "YES".equalsIgnoreCase(row.get(2));
            String defaultValue = row.get(3) == null ? "" : row.get(3);
            boolean isIdentity = row.size() > 4 && "YES".equalsIgnoreCase(row.get(4));
            boolean autoGenerated = isIdentity || defaultValue.toLowerCase(Locale.ROOT).contains("nextval(");
            columns.add(new ColumnMeta(columnName, dataType, nullable, !defaultValue.isBlank(), autoGenerated, false, false));
        }

        String pkSql = """
                SELECT kcu.column_name
                FROM information_schema.table_constraints tc
                         JOIN information_schema.key_column_usage kcu
                              ON tc.constraint_name = kcu.constraint_name
                                  AND tc.table_schema = kcu.table_schema
                WHERE tc.table_schema = 'public'
                  AND tc.table_name = ?
                  AND tc.constraint_type = 'PRIMARY KEY'
                ORDER BY kcu.ordinal_position
                LIMIT 1
                """;

        QueryResult pkResult = databaseService.executePrepared(pkSql, ps -> ps.setString(1, tableName));
        String primaryKey = pkResult.rows().isEmpty() ? null : pkResult.rows().get(0).get(0);

        Map<String, ForeignKeyMeta> foreignKeys = loadForeignKeys(tableName);

        List<ColumnMeta> enrichedColumns = new ArrayList<>(columns.size());
        for (ColumnMeta column : columns) {
            boolean isPrimary = primaryKey != null && primaryKey.equals(column.name());
            boolean isForeign = foreignKeys.containsKey(column.name());
            enrichedColumns.add(new ColumnMeta(
                    column.name(),
                    column.dataType(),
                    column.nullable(),
                    column.hasDefault(),
                    column.autoGenerated(),
                    isPrimary,
                    isForeign
            ));
        }

        TableMeta meta = new TableMeta(tableName, List.copyOf(enrichedColumns), primaryKey, Map.copyOf(foreignKeys));
        tableMetaCache.put(tableName, meta);
        return meta;
    }

    private String buildViewSql(TableMeta meta) throws SQLException {
        String baseAlias = "t";
        List<String> selectParts = new ArrayList<>();
        List<String> joinParts = new ArrayList<>();
        int joinIndex = 1;

        for (ColumnMeta column : meta.columns()) {
            ForeignKeyMeta foreignKey = meta.foreignKeys().get(column.name());
            if (foreignKey != null) {
                if (isWorkerSubtypeTable(foreignKey.referencedTable())) {
                    String joinAlias = "worker_ref" + joinIndex++;
                    String aliasName = aliasForWorkerSubtypeFk(column.name(), foreignKey.referencedTable());
                    selectParts.add("(" + joinAlias + ".last_name || ' ' || " + joinAlias + ".first_name) AS " + aliasName);
                    joinParts.add("LEFT JOIN workers " + joinAlias
                            + " ON " + joinAlias + ".worker_id = " + baseAlias + "." + column.name());
                    continue;
                }

                String joinAlias = sanitizeAlias(column.name()) + "_ref" + joinIndex++;
                String expression = qualifyDisplayExpression(foreignKey.displayExpression(), foreignKey.referencedTable(), joinAlias);
                String aliasName = column.name().endsWith("_id")
                        ? column.name().substring(0, column.name().length() - 3) + "_name"
                        : column.name() + "_name";
                selectParts.add("(" + expression + ") AS " + aliasName);
                joinParts.add("LEFT JOIN " + foreignKey.referencedTable() + " " + joinAlias
                        + " ON " + joinAlias + "." + foreignKey.referencedColumn() + " = " + baseAlias + "." + column.name());
                continue;
            }

            if (isTechnicalIdColumn(column.name())) {
                continue;
            }
            selectParts.add(baseAlias + "." + column.name());
        }

        if (selectParts.isEmpty()) {
            selectParts.add(baseAlias + ".*");
        }

        String orderBy = meta.primaryKeyColumn() == null
                ? ""
                : " ORDER BY " + baseAlias + "." + meta.primaryKeyColumn();

        String joins = joinParts.isEmpty() ? "" : " " + String.join(" ", joinParts);
        return "SELECT " + String.join(", ", selectParts)
                + " FROM " + meta.tableName() + " " + baseAlias
                + joins
                + orderBy;
    }

    private String qualifyDisplayExpression(String expression, String tableName, String alias) throws SQLException {
        List<String> columns = new ArrayList<>(loadTableColumns(tableName));
        columns.sort((a, b) -> Integer.compare(b.length(), a.length()));
        String result = expression;
        for (String column : columns) {
            Pattern pattern = Pattern.compile("\\b" + Pattern.quote(column) + "\\b");
            result = pattern.matcher(result).replaceAll(alias + "." + column);
        }
        return result;
    }

    private boolean isWorkerSubtypeTable(String tableName) {
        String normalized = normalizeTableName(tableName);
        return Set.of(
                "actors",
                "musicians",
                "director_producers",
                "designer_producers",
                "conductor_producers",
                "staff"
        ).contains(normalized);
    }

    private String aliasForWorkerSubtypeFk(String columnName, String referencedTable) {
        String column = columnName.toLowerCase(Locale.ROOT);
        String referenced = normalizeTableName(referencedTable);
        String subtypeAlias = switch (referenced) {
            case "actors" -> "actor";
            case "musicians" -> "musician";
            case "director_producers" -> "director";
            case "designer_producers" -> "designer";
            case "conductor_producers" -> "conductor";
            case "staff" -> "staff_member";
            default -> null;
        };
        if ("worker_id".equals(column) && subtypeAlias != null) {
            return subtypeAlias + "_name";
        }
        if (column.endsWith("_id")) {
            return column.substring(0, column.length() - 3) + "_name";
        }
        return column + "_name";
    }

    private String sanitizeAlias(String base) {
        String sanitized = base.replaceAll("[^a-zA-Z0-9_]", "");
        return sanitized.isBlank() ? "ref" : sanitized;
    }

    private String normalizeTableName(String tableName) {
        if (tableName == null) {
            return "";
        }
        String normalized = tableName.trim();
        int dotIndex = normalized.lastIndexOf('.');
        if (dotIndex >= 0) {
            normalized = normalized.substring(dotIndex + 1);
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private Map<String, ForeignKeyMeta> loadForeignKeys(String tableName) throws SQLException {
        String fkSql = """
                SELECT kcu.column_name,
                       ccu.table_name AS referenced_table,
                       ccu.column_name AS referenced_column
                FROM information_schema.table_constraints tc
                         JOIN information_schema.key_column_usage kcu
                              ON tc.constraint_name = kcu.constraint_name
                                  AND tc.table_schema = kcu.table_schema
                         JOIN information_schema.constraint_column_usage ccu
                              ON ccu.constraint_name = tc.constraint_name
                                  AND ccu.table_schema = tc.table_schema
                WHERE tc.table_schema = 'public'
                  AND tc.table_name = ?
                  AND tc.constraint_type = 'FOREIGN KEY'
                ORDER BY kcu.ordinal_position
                """;
        QueryResult result = databaseService.executePrepared(fkSql, ps -> ps.setString(1, tableName));
        Map<String, ForeignKeyMeta> map = new LinkedHashMap<>();
        for (List<String> row : result.rows()) {
            String columnName = row.get(0);
            String referencedTable = row.get(1);
            String referencedColumn = row.get(2);
            String displayExpression = selectDisplayExpression(referencedTable, referencedColumn);
            map.put(columnName, new ForeignKeyMeta(columnName, referencedTable, referencedColumn, displayExpression));
        }
        addBuiltinForeignKeys(tableName, map);
        return map;
    }

    private void addBuiltinForeignKeys(String tableName, Map<String, ForeignKeyMeta> map) throws SQLException {
        String normalized = normalizeTableName(tableName);
        switch (normalized) {
            case "theater_schools" -> putBuiltinForeignKey(map, "city_id", "city", "city_id");
            case "theaters" -> putBuiltinForeignKey(map, "city_id", "city", "city_id");
            case "current_theater" -> putBuiltinForeignKey(map, "theater_id", "theaters", "theater_id");
            case "workers" -> putBuiltinForeignKey(map, "worker_type_id", "worker_types", "type_id");
            case "actors" -> {
                putBuiltinForeignKey(map, "worker_id", "workers", "worker_id");
                putBuiltinForeignKey(map, "school_id", "theater_schools", "school_id");
            }
            case "musicians" -> putBuiltinForeignKey(map, "worker_id", "workers", "worker_id");
            case "director_producers" -> putBuiltinForeignKey(map, "worker_id", "workers", "worker_id");
            case "designer_producers" -> putBuiltinForeignKey(map, "worker_id", "workers", "worker_id");
            case "conductor_producers" -> putBuiltinForeignKey(map, "worker_id", "workers", "worker_id");
            case "staff" -> putBuiltinForeignKey(map, "worker_id", "workers", "worker_id");
            case "actor_honors" -> putBuiltinForeignKey(map, "worker_id", "actors", "worker_id");
            case "actor_awards" -> putBuiltinForeignKey(map, "worker_id", "actors", "worker_id");
            case "authors" -> putBuiltinForeignKey(map, "country_id", "countries", "country_id");
            case "spectacles" -> {
                putBuiltinForeignKey(map, "genre_id", "genres", "genre_id");
                putBuiltinForeignKey(map, "age_category_id", "age_categories", "category_id");
                putBuiltinForeignKey(map, "director_id", "director_producers", "worker_id");
                putBuiltinForeignKey(map, "artist_id", "designer_producers", "worker_id");
                putBuiltinForeignKey(map, "conductor_id", "conductor_producers", "worker_id");
                putBuiltinForeignKey(map, "theater_id", "theaters", "theater_id");
            }
            case "spectacle_authors" -> {
                putBuiltinForeignKey(map, "spectacle_id", "spectacles", "spectacle_id");
                putBuiltinForeignKey(map, "author_id", "authors", "author_id");
            }
            case "roles" -> putBuiltinForeignKey(map, "spectacle_id", "spectacles", "spectacle_id");
            case "casting" -> {
                putBuiltinForeignKey(map, "role_id", "roles", "role_id");
                putBuiltinForeignKey(map, "actor_id", "actors", "worker_id");
            }
            case "seats" -> {
                putBuiltinForeignKey(map, "hall_id", "halls", "hall_id");
                putBuiltinForeignKey(map, "seat_type_id", "seat_types", "seat_type_id");
            }
            case "pricing" -> {
                putBuiltinForeignKey(map, "spectacle_id", "spectacles", "spectacle_id");
                putBuiltinForeignKey(map, "seat_type_id", "seat_types", "seat_type_id");
            }
            case "shows" -> {
                putBuiltinForeignKey(map, "spectacle_id", "spectacles", "spectacle_id");
                putBuiltinForeignKey(map, "season_id", "seasons", "season_id");
                putBuiltinForeignKey(map, "hall_id", "halls", "hall_id");
            }
            case "tickets" -> {
                putBuiltinForeignKey(map, "show_id", "shows", "show_id");
                putBuiltinForeignKey(map, "seat_id", "seats", "seat_id");
            }
            case "tours" -> {
                putBuiltinForeignKey(map, "spectacle_id", "spectacles", "spectacle_id");
                putBuiltinForeignKey(map, "from_theater_id", "theaters", "theater_id");
                putBuiltinForeignKey(map, "to_theater_id", "theaters", "theater_id");
            }
            case "tour_participants" -> {
                putBuiltinForeignKey(map, "tour_id", "tours", "tour_id");
                putBuiltinForeignKey(map, "worker_id", "workers", "worker_id");
            }
            default -> {
            }
        }
    }

    private void putBuiltinForeignKey(
            Map<String, ForeignKeyMeta> map,
            String columnName,
            String referencedTable,
            String referencedColumn
    ) throws SQLException {
        if (map.containsKey(columnName)) {
            return;
        }
        String displayExpression = selectDisplayExpression(referencedTable, referencedColumn);
        map.put(columnName, new ForeignKeyMeta(columnName, referencedTable, referencedColumn, displayExpression));
    }

    private String selectDisplayExpression(String tableName, String keyColumn) throws SQLException {
        String normalized = normalizeTableName(tableName);
        List<String> columns = loadTableColumns(normalized);
        if ("seats".equals(normalized) && columns.contains("row_number") && columns.contains("seat_number")) {
            return "'Ряд ' || row_number::text || ', место ' || seat_number::text";
        }
        if ("tours".equals(normalized) && columns.contains("start_date") && columns.contains("end_date")) {
            return "start_date::text || ' - ' || end_date::text";
        }
        if (columns.contains("last_name") && columns.contains("first_name")) {
            return "last_name || ' ' || first_name";
        }
        if (columns.contains("title")) {
            return "title";
        }
        if (columns.contains("name")) {
            return "name";
        }
        for (String column : columns) {
            if (column.endsWith("_name")) {
                return column;
            }
        }
        if (columns.contains("show_date") && columns.contains("show_time")) {
            return "show_date::text || ' ' || show_time::text";
        }
        return keyColumn;
    }

    private List<String> loadTableColumns(String tableName) throws SQLException {
        String normalized = normalizeTableName(tableName);
        List<String> cached = tableColumnsCache.get(normalized);
        if (cached != null) {
            return cached;
        }
        QueryResult result = databaseService.executePrepared(
                """
                        SELECT column_name
                        FROM information_schema.columns
                        WHERE table_schema = 'public'
                          AND table_name = ?
                        ORDER BY ordinal_position
                        """,
                ps -> ps.setString(1, normalized)
        );
        List<String> columns = new ArrayList<>();
        for (List<String> row : result.rows()) {
            if (!row.isEmpty()) {
                columns.add(row.get(0));
            }
        }
        List<String> immutable = List.copyOf(columns);
        tableColumnsCache.put(normalized, immutable);
        return immutable;
    }

    private Node buildProceduresSection() {
        VBox root = new VBox(12);
        Label title = new Label("Процедуры");
        title.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #0F172A;");
        root.getChildren().add(title);

        if (hasAnyHireProcedure()) {
            root.getChildren().add(buildHireWorkerPane());
        }
        if (currentPolicy().canUseProcedure("add_show")) {
            root.getChildren().add(buildAddShowPane());
        }
        if (root.getChildren().size() == 1) {
            root.getChildren().add(createHintLabel("Для текущей роли процедуры недоступны."));
        }
        return root;
    }

    private ComboBox<String> createTableComboBox(List<String> tables) {
        ComboBox<String> combo = new ComboBox<>(FXCollections.observableArrayList(tables));
        combo.setConverter(tableNameConverter());
        combo.setMaxWidth(Double.MAX_VALUE);
        return combo;
    }

    private StringConverter<String> tableNameConverter() {
        return new StringConverter<>() {
            @Override
            public String toString(String table) {
                return table == null ? "" : humanizeTableName(table);
            }

            @Override
            public String fromString(String display) {
                if (display == null || display.isBlank()) {
                    return null;
                }
                for (Map.Entry<String, String> entry : TABLE_LABEL_TRANSLATIONS.entrySet()) {
                    if (entry.getValue().equalsIgnoreCase(display.trim())) {
                        return entry.getKey();
                    }
                }
                return display.trim();
            }
        };
    }

    private boolean hasAnyHireProcedure() {
        for (HireProcedureSpec spec : HIRE_PROCEDURE_SPECS) {
            if (currentPolicy().canUseProcedure(spec.procedureName())) {
                return true;
            }
        }
        return false;
    }

    private VBox buildHireWorkerPane() {
        Label title = new Label("Процедура «Найм работника»");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        List<HireProcedureSpec> available = new ArrayList<>();
        for (HireProcedureSpec spec : HIRE_PROCEDURE_SPECS) {
            if (currentPolicy().canUseProcedure(spec.procedureName())) {
                available.add(spec);
            }
        }

        ComboBox<HireProcedureSpec> typeCombo = new ComboBox<>(FXCollections.observableArrayList(available));
        typeCombo.setMaxWidth(Double.MAX_VALUE);
        typeCombo.setValue(available.get(0));

        TextField lastNameField = new TextField();
        TextField firstNameField = new TextField();
        DatePicker birthDatePicker = new DatePicker();
        ComboBox<OptionItem<?>> genderCombo = buildLookupCombo("gender");
        TextField salaryField = new TextField();
        GridPane extraFieldsGrid = new GridPane();
        extraFieldsGrid.setHgap(10);
        extraFieldsGrid.setVgap(10);

        Runnable rebuildExtraFields = () -> {
            extraFieldsGrid.getChildren().clear();
            HireProcedureSpec spec = typeCombo.getValue();
            if (spec == null) {
                return;
            }
            int row = 0;
            for (HireExtraField field : spec.extraFields()) {
                Node control;
                if (field.voiceCombo()) {
                    ComboBox<OptionItem<?>> voiceCombo = buildLookupCombo("voiceType");
                    voiceCombo.setEditable(true);
                    control = voiceCombo;
                } else {
                    TextField textField = new TextField();
                    textField.setPromptText(field.hint());
                    control = textField;
                }
                control.getProperties().put("hireFieldLabel", field.label());
                extraFieldsGrid.add(new Label(field.label()), 0, row);
                extraFieldsGrid.add(control, 1, row);
                if (control instanceof Region region) {
                    region.setMaxWidth(Double.MAX_VALUE);
                    GridPane.setHgrow(region, Priority.ALWAYS);
                }
                row++;
            }
        };
        typeCombo.valueProperty().addListener((obs, oldValue, newValue) -> rebuildExtraFields.run());
        rebuildExtraFields.run();

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.addRow(0, new Label("Тип работника"), typeCombo);
        grid.addRow(1, new Label("Фамилия"), lastNameField, new Label("Имя"), firstNameField);
        grid.addRow(2, new Label("Дата рождения"), birthDatePicker, new Label("Пол"), genderCombo);
        grid.addRow(3, new Label("Зарплата"), salaryField);
        makeGrowing(lastNameField, firstNameField, salaryField);
        GridPane.setColumnSpan(typeCombo, 3);
        GridPane.setHgrow(typeCombo, Priority.ALWAYS);

        Button runButton = new Button("Нанять работника");
        runButton.setOnAction(e -> {
            HireProcedureSpec spec = typeCombo.getValue();
            if (spec == null) {
                showWarning("Выберите тип работника.");
                return;
            }
            if (!currentPolicy().canUseProcedure(spec.procedureName())) {
                showWarning("У текущей роли нет доступа к найму: " + spec.roleLabel() + ".");
                return;
            }
            try {
                String lastName = requireText(lastNameField, "Фамилия");
                String firstName = requireText(firstNameField, "Имя");
                if (birthDatePicker.getValue() == null) {
                    throw new IllegalArgumentException("Укажите дату рождения.");
                }
                String gender = getSelectedLookupValue(genderCombo, "Пол");
                BigDecimal salary = new BigDecimal(requireText(salaryField, "Зарплата"));

                if ("add_actor".equals(spec.procedureName())) {
                    List<String> extraValues = readHireExtraFieldValues(extraFieldsGrid, spec);
                    String voiceType = extraValues.get(0);
                    BigDecimal height = new BigDecimal(extraValues.get(1));
                    databaseService.executePrepared(
                            "CALL add_actor(?, ?, ?, ?, ?, ?, ?)",
                            ps -> {
                                ps.setString(1, lastName);
                                ps.setString(2, firstName);
                                ps.setDate(3, Date.valueOf(birthDatePicker.getValue()));
                                ps.setString(4, gender);
                                ps.setBigDecimal(5, salary);
                                ps.setString(6, voiceType);
                                ps.setBigDecimal(7, height);
                            }
                    );
                } else {
                    String specificValue = readHireExtraFieldValues(extraFieldsGrid, spec).get(0);
                    databaseService.executePrepared(
                            "CALL " + spec.procedureName() + "(?, ?, ?, ?, ?, ?)",
                            ps -> {
                                ps.setString(1, lastName);
                                ps.setString(2, firstName);
                                ps.setDate(3, Date.valueOf(birthDatePicker.getValue()));
                                ps.setString(4, gender);
                                ps.setBigDecimal(5, salary);
                                ps.setString(6, specificValue);
                            }
                    );
                }
                loadLookupCache();
                refreshParamControlsIfNeeded();
                setStatus("Процедура «Найм работника» (" + spec.roleLabel() + ") выполнена успешно.");
                showInfo("Успех", spec.successMessage());
            } catch (SQLException ex) {
                showError("Ошибка выполнения процедуры «Найм работника»", ex);
            } catch (IllegalArgumentException ex) {
                showWarning(ex.getMessage());
            }
        });

        VBox pane = new VBox(10, title, grid, extraFieldsGrid, runButton);
        pane.setPadding(new Insets(14));
        pane.setStyle("-fx-background-color: white; -fx-border-color: #D8DFEA; -fx-border-radius: 10; -fx-background-radius: 10;");
        return pane;
    }

    private List<String> readHireExtraFieldValues(GridPane extraFieldsGrid, HireProcedureSpec spec) {
        List<Node> controls = extraFieldsGrid.getChildren().stream()
                .filter(node -> GridPane.getColumnIndex(node) != null && GridPane.getColumnIndex(node) == 1)
                .sorted((a, b) -> Integer.compare(
                        GridPane.getRowIndex(a) == null ? 0 : GridPane.getRowIndex(a),
                        GridPane.getRowIndex(b) == null ? 0 : GridPane.getRowIndex(b)))
                .toList();
        if (controls.size() != spec.extraFields().size()) {
            throw new IllegalStateException("Не совпадает число дополнительных полей найма.");
        }
        List<String> values = new ArrayList<>();
        for (int i = 0; i < controls.size(); i++) {
            Node control = controls.get(i);
            HireExtraField field = spec.extraFields().get(i);
            if (control instanceof ComboBox<?> combo) {
                values.add(readComboEditableValue(castCombo(combo), field.label()));
            } else if (control instanceof TextField textField) {
                values.add(requireText(textField, field.label()));
            } else {
                throw new IllegalStateException("Неподдерживаемый тип поля найма.");
            }
        }
        return values;
    }

    @SuppressWarnings("unchecked")
    private ComboBox<OptionItem<?>> castCombo(ComboBox<?> combo) {
        return (ComboBox<OptionItem<?>>) combo;
    }

    private static final List<HireProcedureSpec> HIRE_PROCEDURE_SPECS = List.of(
            new HireProcedureSpec("add_actor", "Актёр", List.of(
                    HireExtraField.voiceType(),
                    HireExtraField.text("Рост (см)", "Например: 180")
            ), "Актёр добавлен."),
            new HireProcedureSpec("add_musician", "Музыкант", List.of(
                    HireExtraField.text("Инструмент", "Например: скрипка")
            ), "Музыкант добавлен."),
            new HireProcedureSpec("add_director", "Режиссёр-постановщик", List.of(
                    HireExtraField.text("Специализация", "Например: драматический театр")
            ), "Режиссёр-постановщик добавлен."),
            new HireProcedureSpec("add_designer", "Художник-постановщик", List.of(
                    HireExtraField.text("Стиль", "Например: реализм")
            ), "Художник-постановщик добавлен."),
            new HireProcedureSpec("add_conductor", "Дирижёр-постановщик", List.of(
                    HireExtraField.text("Тип оркестра", "Например: симфонический")
            ), "Дирижёр-постановщик добавлен."),
            new HireProcedureSpec("add_staff", "Служащий", List.of(
                    HireExtraField.text("Должность", "Например: гардеробщик")
            ), "Служащий добавлен.")
    );

    private record HireProcedureSpec(
            String procedureName,
            String roleLabel,
            List<HireExtraField> extraFields,
            String successMessage
    ) {
        @Override
        public String toString() {
            return roleLabel;
        }
    }

    private record HireExtraField(String label, String hint, boolean voiceCombo) {
        static HireExtraField text(String label, String hint) {
            return new HireExtraField(label, hint, false);
        }

        static HireExtraField voiceType() {
            return new HireExtraField("Тип голоса", "Например: баритон", true);
        }
    }

    private VBox buildAddShowPane() {
        Label title = new Label("Процедура «Назначить показ»");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        ComboBox<OptionItem<?>> spectacleCombo = buildLookupCombo("spectacle");
        ComboBox<OptionItem<?>> seasonCombo = buildLookupCombo("season");
        ComboBox<OptionItem<?>> hallCombo = buildLookupCombo("hall");
        DatePicker showDatePicker = new DatePicker();
        ComboBox<String> timeCombo = new ComboBox<>(FXCollections.observableArrayList(
                "10:00", "12:00", "14:00", "16:00", "18:00", "19:00", "20:00", "21:00"
        ));
        timeCombo.setEditable(true);
        timeCombo.setValue("19:00");
        ComboBox<OptionItem<?>> premiereCombo = new ComboBox<>(FXCollections.observableArrayList(
                new OptionItem<>("Да", true),
                new OptionItem<>("Нет", false)
        ));
        premiereCombo.setValue(new OptionItem<>("Нет", false));

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.addRow(0, new Label("Спектакль"), spectacleCombo, new Label("Сезон"), seasonCombo);
        grid.addRow(1, new Label("Зал"), hallCombo, new Label("Дата"), showDatePicker);
        grid.addRow(2, new Label("Время"), timeCombo, new Label("Премьера"), premiereCombo);

        Button runButton = new Button("Назначить показ");
        runButton.setOnAction(e -> {
            if (!currentPolicy().canUseProcedure("add_show")) {
                showWarning("У текущей роли нет доступа к процедуре «Назначить показ».");
                return;
            }
            try {
                int spectacleId = getSelectedLookupInt(spectacleCombo, "Спектакль");
                int seasonId = getSelectedLookupInt(seasonCombo, "Сезон");
                int hallId = getSelectedLookupInt(hallCombo, "Зал");
                if (showDatePicker.getValue() == null) {
                    throw new IllegalArgumentException("Укажите дату показа.");
                }
                LocalTime showTime = parseTime(Objects.requireNonNullElse(timeCombo.getValue(), "").trim());
                OptionItem<?> premiereItem = premiereCombo.getValue();
                if (premiereItem == null) {
                    throw new IllegalArgumentException("Укажите признак премьеры.");
                }
                boolean isPremiere = (Boolean) premiereItem.value();

                databaseService.executePrepared(
                        "CALL add_show(?, ?, ?, ?, ?, ?)",
                        ps -> {
                            ps.setInt(1, spectacleId);
                            ps.setInt(2, seasonId);
                            ps.setInt(3, hallId);
                            ps.setDate(4, Date.valueOf(showDatePicker.getValue()));
                            ps.setTime(5, Time.valueOf(showTime));
                            ps.setBoolean(6, isPremiere);
                        }
                );
                loadLookupCache();
                refreshParamControlsIfNeeded();
                setStatus("Процедура «Назначить показ» выполнена успешно.");
                showInfo("Успех", "Показ добавлен.");
            } catch (SQLException ex) {
                showError("Ошибка выполнения процедуры «Назначить показ»", ex);
            } catch (IllegalArgumentException ex) {
                showWarning(ex.getMessage());
            }
        });

        VBox pane = new VBox(10, title, grid, runButton);
        pane.setPadding(new Insets(14));
        pane.setStyle("-fx-background-color: white; -fx-border-color: #D8DFEA; -fx-border-radius: 10; -fx-background-radius: 10;");
        return pane;
    }

    private void initQuerySelectors() {
        sectionDisplayToRaw.clear();
        List<String> sections = new ArrayList<>();
        for (QueryDefinition query : activeQueries) {
            String rawSection = query.section();
            if (sectionDisplayToRaw.containsValue(rawSection)) {
                continue;
            }
            String displaySection = makeUniqueSectionTitle(sanitizeSectionTitle(rawSection));
            sectionDisplayToRaw.put(displaySection, rawSection);
            sections.add(displaySection);
        }
        sectionComboBox.setItems(FXCollections.observableArrayList(sections));
        sectionComboBox.valueProperty().addListener((obs, oldValue, newValue) -> refreshQueryComboForSection(newValue));
        queryComboBox.valueProperty().addListener((obs, oldValue, newValue) -> onQuerySelected(newValue));

        if (!sections.isEmpty()) {
            sectionComboBox.setValue(sections.get(0));
        } else {
            runQueryButton.setDisable(true);
            paramsContainer.getChildren().setAll(createHintLabel("Для текущей роли нет доступных запросов."));
        }
    }

    private void refreshQueryComboForSection(String section) {
        if (section == null) {
            queryComboBox.getItems().clear();
            return;
        }
        String rawSection = sectionDisplayToRaw.getOrDefault(section, section);
        List<QueryView> list = new ArrayList<>();
        for (QueryDefinition query : activeQueries) {
            if (rawSection.equals(query.section())) {
                String userTitle = queryTitleById.getOrDefault(query.id(), sanitizeQueryTitle(query.title()));
                list.add(new QueryView(query, paramSpecsByQueryId.getOrDefault(query.id(), List.of()), userTitle));
            }
        }
        queryComboBox.setItems(FXCollections.observableArrayList(list));
        if (!list.isEmpty()) {
            queryComboBox.setValue(list.get(0));
        }
    }

    private void onQuerySelected(QueryView queryView) {
        currentParamControls.clear();
        paramsContainer.getChildren().clear();
        if (queryView == null) {
            runQueryButton.setDisable(true);
            return;
        }

        runQueryButton.setDisable(false);

        List<ParamSpec> specs = withResultKindParam(queryView.params());

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        int row = 0;
        for (ParamSpec spec : specs) {
            Label label = new Label(spec.label());
            Node control = createParamControl(spec);
            currentParamControls.put(spec.key(), new ControlHolder(spec, control));
            grid.add(label, 0, row);
            grid.add(control, 1, row);
            if (control instanceof Region region) {
                region.setMaxWidth(Double.MAX_VALUE);
                GridPane.setHgrow(region, Priority.ALWAYS);
            }
            row++;
        }
        paramsContainer.getChildren().add(grid);
    }

    private Node createParamControl(ParamSpec spec) {
        if (spec.kind() == ParamKind.LOOKUP) {
            ComboBox<OptionItem<?>> combo = buildLookupCombo(spec.lookupKey(), spec.optional());
            if (combo.getItems().isEmpty()) {
                throw new IllegalStateException("Нет вариантов выбора для параметра: " + spec.label());
            }
            return combo;
        }
        if (spec.kind() == ParamKind.INTEGER) {
            if (spec.optional()) {
                List<OptionItem<Integer>> options = new ArrayList<>();
                options.add(new OptionItem<>("— любое значение —", null));
                for (Integer value : spec.intOptions()) {
                    options.add(new OptionItem<>(String.valueOf(value), value));
                }
                ComboBox<OptionItem<Integer>> combo = new ComboBox<>(FXCollections.observableArrayList(options));
                combo.setValue(options.get(0));
                combo.setMaxWidth(Double.MAX_VALUE);
                return combo;
            }
            ComboBox<Integer> combo = new ComboBox<>(FXCollections.observableArrayList(spec.intOptions()));
            combo.setValue(spec.defaultIntValue());
            return combo;
        }
        if (spec.optional()) {
            return new DatePicker();
        }
        return new DatePicker(spec.defaultDate());
    }

    private ComboBox<OptionItem<?>> buildLookupCombo(String key) {
        return buildLookupCombo(key, false);
    }

    private ComboBox<OptionItem<?>> buildLookupCombo(String key, boolean allowEmpty) {
        List<OptionItem<?>> baseOptions = lookupCache.getOrDefault(key, List.of());
        List<OptionItem<?>> options = new ArrayList<>(baseOptions.size() + 1);
        if (allowEmpty) {
            options.add(new OptionItem<>("— любое значение —", null));
        }
        options.addAll(baseOptions);
        ComboBox<OptionItem<?>> combo = new ComboBox<>(FXCollections.observableArrayList(options));
        if (!options.isEmpty()) {
            combo.setValue(options.get(0));
        }
        combo.setMaxWidth(Double.MAX_VALUE);
        return combo;
    }

    private void runSelectedQuery() {
        QueryView queryView = queryComboBox.getValue();
        if (queryView == null) {
            showWarning("Выберите запрос.");
            return;
        }
        if (!currentPolicy().canUseQuery(queryView.definition().id())) {
            showWarning("У текущей роли нет доступа к этому запросу.");
            return;
        }

        try {
            List<ParamSpec> specs = withResultKindParam(queryView.params());
            Map<String, Object> params = readCurrentParamValues(specs);
            QueryResult result = executeQuery(queryView.definition(), params);
            renderResult(result);
            setStatus("Выполнен " + queryView + ".");
        } catch (SQLException ex) {
            showError("Ошибка выполнения запроса", ex);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            showWarning(ex.getMessage());
        }
    }

    private Map<String, Object> readCurrentParamValues(List<ParamSpec> specs) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (ParamSpec spec : specs) {
            ControlHolder holder = currentParamControls.get(spec.key());
            if (holder == null) {
                throw new IllegalStateException("Не найден контрол параметра: " + spec.label());
            }

            if (spec.kind() == ParamKind.LOOKUP) {
                @SuppressWarnings("unchecked")
                ComboBox<OptionItem<?>> combo = (ComboBox<OptionItem<?>>) holder.control();
                OptionItem<?> item = combo.getValue();
                if (item == null) {
                    if (spec.optional()) {
                        values.put(spec.key(), null);
                        continue;
                    }
                    throw new IllegalArgumentException("Не выбрано значение: " + spec.label());
                }
                if (spec.optional() && item.value() == null) {
                    values.put(spec.key(), null);
                    continue;
                }
                values.put(spec.key(), item.value());
                continue;
            }

            if (spec.kind() == ParamKind.INTEGER) {
                if (spec.optional()) {
                    @SuppressWarnings("unchecked")
                    ComboBox<OptionItem<Integer>> combo = (ComboBox<OptionItem<Integer>>) holder.control();
                    OptionItem<Integer> item = combo.getValue();
                    if (item == null || item.value() == null) {
                        values.put(spec.key(), null);
                        continue;
                    }
                    values.put(spec.key(), item.value());
                    continue;
                } else {
                    @SuppressWarnings("unchecked")
                    ComboBox<Integer> combo = (ComboBox<Integer>) holder.control();
                    Integer value = combo.getValue();
                    if (value == null) {
                        throw new IllegalArgumentException("Не выбрано значение: " + spec.label());
                    }
                    values.put(spec.key(), value);
                    continue;
                }
            }

            DatePicker datePicker = (DatePicker) holder.control();
            LocalDate value = datePicker.getValue();
            if (value == null) {
                if (spec.optional()) {
                    values.put(spec.key(), null);
                    continue;
                }
                throw new IllegalArgumentException("Не выбрана дата: " + spec.label());
            }
            values.put(spec.key(), value);
        }
        return values;
    }

    private QueryResult executeQuery(QueryDefinition query, Map<String, Object> params) throws SQLException {
        if ("count".equals(optionalTextParam(params, "result_kind"))) {
            return executeCountQuery(query, params);
        }
        if (isPureCountQuery(query.sql())) {
            Integer listQueryId = queryCounterpartById.get(query.id());
            if (listQueryId != null) {
                return executeQueryBody(queryById(listQueryId), params);
            }
        }
        return executeQueryBody(query, params);
    }

    private QueryResult executeCountQuery(QueryDefinition query, Map<String, Object> params) throws SQLException {
        if (query.id() == 7) {
            return executeFlexibleWorkerCount(params);
        }
        if (query.id() == 45) {
            return executeFlexibleActorRecognitionQuery(params);
        }
        if (isPureCountQuery(query.sql())) {
            return executeQueryBody(query, params);
        }
        Integer countQueryId = queryCounterpartById.get(query.id());
        if (countQueryId != null && countQueryId != query.id()) {
            return executeQueryBody(queryById(countQueryId), params);
        }
        return executeWrappedCount(query, params);
    }

    private QueryResult executeWrappedCount(QueryDefinition query, Map<String, Object> params) throws SQLException {
        String countSql = "SELECT COUNT(*) AS result_count FROM ("
                + prepareSqlForCountSubquery(query.sql())
                + ") counted_rows";
        QueryDefinition countQuery = new QueryDefinition(
                query.id(), query.section(), query.title(), countSql
        );
        return executeQueryBody(countQuery, params);
    }

    private String prepareSqlForCountSubquery(String sql) {
        String cleaned = sql.replaceAll("(?m)^\\s*--.*$", "").trim();
        if (cleaned.endsWith(";")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
        }
        return stripOrderBy(cleaned);
    }

    private QueryDefinition queryById(int id) {
        for (QueryDefinition query : allQueries) {
            if (query.id() == id) {
                return query;
            }
        }
        throw new IllegalStateException("Не найден запрос #" + id);
    }

    private String stripOrderBy(String sql) {
        return sql.replaceAll("(?is)\\s+order\\s+by\\s+.+$", "").trim();
    }

    private Set<Integer> buildHiddenQueryIds() {
        Set<Integer> hidden = new LinkedHashSet<>(STATIC_HIDDEN_QUERY_IDS);
        for (Map.Entry<Integer, Integer> entry : queryCounterpartById.entrySet()) {
            QueryDefinition query = queryById(entry.getKey());
            if (isPureCountQuery(query.sql())) {
                hidden.add(query.id());
            }
        }
        return Set.copyOf(hidden);
    }

    private Map<Integer, Integer> buildQueryCounterparts() {
        Map<String, List<QueryDefinition>> grouped = new LinkedHashMap<>();
        for (QueryDefinition query : allQueries) {
            String groupKey = query.section() + "::" + normalizeQueryGroupTitle(query.title());
            grouped.computeIfAbsent(groupKey, key -> new ArrayList<>()).add(query);
        }
        Map<Integer, Integer> counterparts = new LinkedHashMap<>();
        for (List<QueryDefinition> group : grouped.values()) {
            if (group.size() < 2) {
                continue;
            }
            QueryDefinition listQuery = null;
            QueryDefinition countQuery = null;
            for (QueryDefinition query : group) {
                if (isPureCountQuery(query.sql())) {
                    countQuery = query;
                } else {
                    listQuery = query;
                }
            }
            if (listQuery != null && countQuery != null) {
                counterparts.put(listQuery.id(), countQuery.id());
                counterparts.put(countQuery.id(), listQuery.id());
            }
        }
        return Map.copyOf(counterparts);
    }

    private List<ParamSpec> withResultKindParam(List<ParamSpec> specs) {
        if (specs.stream().anyMatch(spec -> "result_kind".equals(spec.key()))) {
            return specs;
        }
        List<ParamSpec> result = new ArrayList<>(specs.size() + 1);
        result.add(RESULT_KIND_SPEC);
        result.addAll(specs);
        return result;
    }

    private QueryResult executeQueryBody(QueryDefinition query, Map<String, Object> params) throws SQLException {
        return switch (query.id()) {
            case 7 -> {
                String workerType = optionalTextParam(params, "worker_type");
                Integer minExperience = optionalIntParam(params, "min_experience");
                String gender = optionalTextParam(params, "gender");
                Integer birthYear = optionalIntParam(params, "birth_year");
                Integer ageMin = optionalIntParam(params, "age_min");
                Integer ageMax = optionalIntParam(params, "age_max");
                Integer minChildren = optionalIntParam(params, "min_children");
                Integer childrenCount = optionalIntParam(params, "children_count");
                Integer minSalary = optionalIntParam(params, "min_salary");

                yield databaseService.executePrepared(query.sql(), ps -> {
                    int index = 1;
                    index = bindOptionalString(ps, index, workerType);
                    index = bindOptionalString(ps, index, workerType);
                    index = bindOptionalInt(ps, index, minExperience);
                    index = bindOptionalInt(ps, index, minExperience);
                    index = bindOptionalString(ps, index, gender);
                    index = bindOptionalString(ps, index, gender);
                    index = bindOptionalInt(ps, index, birthYear);
                    index = bindOptionalInt(ps, index, birthYear);
                    index = bindOptionalInt(ps, index, ageMin);
                    index = bindOptionalInt(ps, index, ageMin);
                    index = bindOptionalInt(ps, index, ageMax);
                    index = bindOptionalInt(ps, index, ageMax);
                    index = bindOptionalInt(ps, index, minChildren);
                    index = bindOptionalInt(ps, index, minChildren);
                    index = bindOptionalInt(ps, index, childrenCount);
                    index = bindOptionalInt(ps, index, childrenCount);
                    index = bindOptionalInt(ps, index, minSalary);
                    bindOptionalInt(ps, index, minSalary);
                });
            }
            case 14, 15 -> {
                String seasonName = optionalTextParam(params, "season_name");
                String genre = optionalTextParam(params, "genre_name");
                Integer theaterId = optionalIntParam(params, "theater_id");
                LocalDate dateFrom = optionalDateParam(params, "date_from");
                LocalDate dateTo = optionalDateParam(params, "date_to");

                yield databaseService.executePrepared(query.sql(), ps -> {
                    int index = 1;
                    index = bindOptionalString(ps, index, seasonName);
                    index = bindOptionalString(ps, index, seasonName);
                    index = bindOptionalString(ps, index, genre);
                    index = bindOptionalString(ps, index, genre);
                    index = bindOptionalInt(ps, index, theaterId);
                    index = bindOptionalInt(ps, index, theaterId);
                    index = bindOptionalDate(ps, index, dateFrom);
                    index = bindOptionalDate(ps, index, dateFrom);
                    index = bindOptionalDate(ps, index, dateTo);
                    bindOptionalDate(ps, index, dateTo);
                });
            }
            case 18 -> {
                String genre = optionalTextParam(params, "genre_name");
                Integer theaterId = optionalIntParam(params, "theater_id");
                String ageCategory = optionalTextParam(params, "age_category");
                Integer yearFrom = optionalIntParam(params, "year_from");
                Integer yearTo = optionalIntParam(params, "year_to");
                LocalDate premiereFrom = optionalDateParam(params, "premiere_from");
                LocalDate premiereTo = optionalDateParam(params, "premiere_to");

                yield databaseService.executePrepared(query.sql(), ps -> {
                    int index = 1;
                    index = bindOptionalString(ps, index, genre);
                    index = bindOptionalString(ps, index, genre);
                    index = bindOptionalInt(ps, index, theaterId);
                    index = bindOptionalInt(ps, index, theaterId);
                    index = bindOptionalString(ps, index, ageCategory);
                    index = bindOptionalString(ps, index, ageCategory);
                    index = bindOptionalInt(ps, index, yearFrom);
                    index = bindOptionalInt(ps, index, yearFrom);
                    index = bindOptionalInt(ps, index, yearTo);
                    index = bindOptionalInt(ps, index, yearTo);
                    index = bindOptionalDate(ps, index, premiereFrom);
                    index = bindOptionalDate(ps, index, premiereFrom);
                    index = bindOptionalDate(ps, index, premiereTo);
                    bindOptionalDate(ps, index, premiereTo);
                });
            }
            case 33 -> {
                String country = optionalTextParam(params, "country_name");
                Integer century = optionalIntParam(params, "century");
                String genre = optionalTextParam(params, "genre_name");
                Integer theaterId = optionalIntParam(params, "theater_id");
                LocalDate dateFrom = optionalDateParam(params, "date_from");
                LocalDate dateTo = optionalDateParam(params, "date_to");

                yield databaseService.executePrepared(query.sql(), ps -> {
                    int index = 1;
                    index = bindOptionalString(ps, index, country);
                    index = bindOptionalString(ps, index, country);
                    index = bindOptionalInt(ps, index, century);
                    index = bindOptionalInt(ps, index, century);
                    index = bindOptionalString(ps, index, genre);
                    index = bindOptionalString(ps, index, genre);
                    index = bindOptionalInt(ps, index, theaterId);
                    index = bindOptionalInt(ps, index, theaterId);
                    index = bindOptionalDate(ps, index, dateFrom);
                    index = bindOptionalDate(ps, index, dateFrom);
                    index = bindOptionalDate(ps, index, dateTo);
                    bindOptionalDate(ps, index, dateTo);
                });
            }
            case 34 -> {
                String genre = optionalTextParam(params, "genre_name");
                Integer authorId = optionalIntParam(params, "author_id");
                String country = optionalTextParam(params, "country_name");
                Integer century = optionalIntParam(params, "century");
                Integer theaterId = optionalIntParam(params, "theater_id");
                LocalDate dateFrom = optionalDateParam(params, "date_from");
                LocalDate dateTo = optionalDateParam(params, "date_to");

                yield databaseService.executePrepared(query.sql(), ps -> {
                    int index = 1;
                    index = bindOptionalString(ps, index, genre);
                    index = bindOptionalString(ps, index, genre);
                    index = bindOptionalInt(ps, index, authorId);
                    index = bindOptionalInt(ps, index, authorId);
                    index = bindOptionalString(ps, index, country);
                    index = bindOptionalString(ps, index, country);
                    index = bindOptionalInt(ps, index, century);
                    index = bindOptionalInt(ps, index, century);
                    index = bindOptionalInt(ps, index, theaterId);
                    index = bindOptionalInt(ps, index, theaterId);
                    index = bindOptionalDate(ps, index, dateFrom);
                    index = bindOptionalDate(ps, index, dateFrom);
                    index = bindOptionalDate(ps, index, dateTo);
                    bindOptionalDate(ps, index, dateTo);
                });
            }
            case 42 -> databaseService.executePrepared(
                    query.sql(),
                    ps -> ps.setInt(1, intParam(params, "role_id"))
            );
            case 45 -> executeFlexibleActorRecognitionQuery(params);
            case 50 -> {
                Integer toTheaterId = optionalIntParam(params, "to_theater_id");
                Integer fromTheaterId = optionalIntParam(params, "from_theater_id");
                LocalDate dateFrom = optionalDateParam(params, "date_from");
                LocalDate dateTo = optionalDateParam(params, "date_to");
                Integer spectacleId = optionalIntParam(params, "spectacle_id");

                yield databaseService.executePrepared(query.sql(), ps -> {
                    int index = 1;
                    index = bindOptionalInt(ps, index, toTheaterId);
                    index = bindOptionalInt(ps, index, toTheaterId);
                    index = bindOptionalInt(ps, index, fromTheaterId);
                    index = bindOptionalInt(ps, index, fromTheaterId);
                    index = bindOptionalDate(ps, index, dateFrom);
                    index = bindOptionalDate(ps, index, dateFrom);
                    index = bindOptionalDate(ps, index, dateTo);
                    index = bindOptionalDate(ps, index, dateTo);
                    index = bindOptionalInt(ps, index, spectacleId);
                    bindOptionalInt(ps, index, spectacleId);
                });
            }
            case 52, 53, 54 -> databaseService.executePrepared(
                    query.sql(),
                    ps -> ps.setInt(1, intParam(params, "spectacle_id"))
            );
            case 55 -> {
                int actorId = intParam(params, "actor_id");
                LocalDate dateFrom = optionalDateParam(params, "date_from");
                LocalDate dateTo = optionalDateParam(params, "date_to");
                String genre = optionalTextParam(params, "genre_name");
                String ageCategory = optionalTextParam(params, "age_category");
                Integer directorId = optionalIntParam(params, "director_id");

                yield databaseService.executePrepared(query.sql(), ps -> {
                    int index = 1;
                    ps.setInt(index++, actorId);
                    index = bindOptionalDate(ps, index, dateFrom);
                    index = bindOptionalDate(ps, index, dateFrom);
                    index = bindOptionalDate(ps, index, dateTo);
                    index = bindOptionalDate(ps, index, dateTo);
                    index = bindOptionalString(ps, index, genre);
                    index = bindOptionalString(ps, index, genre);
                    index = bindOptionalString(ps, index, ageCategory);
                    index = bindOptionalString(ps, index, ageCategory);
                    index = bindOptionalInt(ps, index, directorId);
                    bindOptionalInt(ps, index, directorId);
                });
            }
            case 67 -> {
                Integer spectacleId = optionalIntParam(params, "spectacle_id");
                LocalDate dateFrom = optionalDateParam(params, "date_from");
                LocalDate dateTo = optionalDateParam(params, "date_to");
                Boolean isPremiere = optionalBooleanParam(params, "is_premiere");
                Boolean isAdvance = optionalBooleanParam(params, "is_advance_sale");

                yield databaseService.executePrepared(query.sql(), ps -> {
                    int index = 1;
                    index = bindOptionalInt(ps, index, spectacleId);
                    index = bindOptionalInt(ps, index, spectacleId);
                    index = bindOptionalDate(ps, index, dateFrom);
                    index = bindOptionalDate(ps, index, dateFrom);
                    index = bindOptionalDate(ps, index, dateTo);
                    index = bindOptionalDate(ps, index, dateTo);
                    index = bindOptionalBoolean(ps, index, isPremiere);
                    index = bindOptionalBoolean(ps, index, isPremiere);
                    index = bindOptionalBoolean(ps, index, isAdvance);
                    bindOptionalBoolean(ps, index, isAdvance);
                });
            }
            case 75 -> {
                Integer spectacleId = optionalIntParam(params, "spectacle_id");
                LocalDate dateFrom = optionalDateParam(params, "date_from");
                LocalDate dateTo = optionalDateParam(params, "date_to");

                yield databaseService.executePrepared(query.sql(), ps -> {
                    int index = 1;
                    index = bindOptionalInt(ps, index, spectacleId);
                    index = bindOptionalInt(ps, index, spectacleId);
                    index = bindOptionalDate(ps, index, dateFrom);
                    index = bindOptionalDate(ps, index, dateFrom);
                    index = bindOptionalDate(ps, index, dateTo);
                    bindOptionalDate(ps, index, dateTo);
                });
            }
            case 79 -> {
                Integer spectacleId = optionalIntParam(params, "spectacle_id");
                Boolean isPremiere = optionalBooleanParam(params, "is_premiere");

                yield databaseService.executePrepared(query.sql(), ps -> {
                    int index = 1;
                    index = bindOptionalInt(ps, index, spectacleId);
                    index = bindOptionalInt(ps, index, spectacleId);
                    index = bindOptionalBoolean(ps, index, isPremiere);
                    bindOptionalBoolean(ps, index, isPremiere);
                });
            }
            default -> databaseService.executeSql(query.sql());
        };
    }

    private QueryResult executeFlexibleWorkerCount(Map<String, Object> params) throws SQLException {
        String sql = """
                SELECT COUNT(*) AS total_workers
                FROM workers w
                         JOIN worker_types wt ON wt.type_id = w.worker_type_id
                WHERE (? IS NULL OR wt.type_name = ?)
                  AND (? IS NULL OR get_years_from_date(w.hire_date) >= ?)
                  AND (? IS NULL OR w.gender = ?)
                  AND (? IS NULL OR EXTRACT(YEAR FROM w.birth_date) = ?)
                  AND (? IS NULL OR get_years_from_date(w.birth_date) >= ?)
                  AND (? IS NULL OR get_years_from_date(w.birth_date) <= ?)
                  AND (? IS NULL OR w.children_count >= ?)
                  AND (? IS NULL OR w.children_count = ?)
                  AND (? IS NULL OR w.salary >= ?)
                """;

        String workerType = optionalTextParam(params, "worker_type");
        Integer minExperience = optionalIntParam(params, "min_experience");
        String gender = optionalTextParam(params, "gender");
        Integer birthYear = optionalIntParam(params, "birth_year");
        Integer ageMin = optionalIntParam(params, "age_min");
        Integer ageMax = optionalIntParam(params, "age_max");
        Integer minChildren = optionalIntParam(params, "min_children");
        Integer childrenCount = optionalIntParam(params, "children_count");
        Integer minSalary = optionalIntParam(params, "min_salary");

        return databaseService.executePrepared(sql, ps -> {
            int index = 1;
            index = bindOptionalString(ps, index, workerType);
            index = bindOptionalString(ps, index, workerType);
            index = bindOptionalInt(ps, index, minExperience);
            index = bindOptionalInt(ps, index, minExperience);
            index = bindOptionalString(ps, index, gender);
            index = bindOptionalString(ps, index, gender);
            index = bindOptionalInt(ps, index, birthYear);
            index = bindOptionalInt(ps, index, birthYear);
            index = bindOptionalInt(ps, index, ageMin);
            index = bindOptionalInt(ps, index, ageMin);
            index = bindOptionalInt(ps, index, ageMax);
            index = bindOptionalInt(ps, index, ageMax);
            index = bindOptionalInt(ps, index, minChildren);
            index = bindOptionalInt(ps, index, minChildren);
            index = bindOptionalInt(ps, index, childrenCount);
            index = bindOptionalInt(ps, index, childrenCount);
            index = bindOptionalInt(ps, index, minSalary);
            bindOptionalInt(ps, index, minSalary);
        });
    }

    private QueryResult executeFlexibleActorRecognitionQuery(Map<String, Object> params) throws SQLException {
        String resultKind = optionalTextParam(params, "result_kind");
        boolean countOnly = "count".equals(resultKind);

        String recognitionKind = optionalTextParam(params, "recognition_kind");
        if ("both".equals(recognitionKind)) {
            recognitionKind = null;
        }
        Boolean hasHonor = optionalBooleanParam(params, "has_honor");
        Boolean hasAward = optionalBooleanParam(params, "has_award");
        String honorType = optionalTextParam(params, "honor_type");
        LocalDate dateFrom = optionalDateParam(params, "date_from");
        LocalDate dateTo = optionalDateParam(params, "date_to");
        String competition = optionalTextParam(params, "competition_name");
        String gender = optionalTextParam(params, "gender");
        Integer ageMin = optionalIntParam(params, "age_min");
        Integer ageMax = optionalIntParam(params, "age_max");

        boolean needsPresenceQuery = Boolean.FALSE.equals(hasHonor) || Boolean.FALSE.equals(hasAward);
        if (needsPresenceQuery
                && honorType == null
                && dateFrom == null
                && dateTo == null
                && competition == null
                && !"honor".equals(recognitionKind)
                && !"award".equals(recognitionKind)) {
            return executeActorPresenceQuery(countOnly, hasHonor, hasAward, gender, ageMin, ageMax);
        }

        StringBuilder sql = new StringBuilder();
        sql.append("""
                WITH actor_recognitions AS (
                    SELECT a.worker_id,
                           ah.honor_type AS recognition_type,
                           ah.honor_name AS recognition_name,
                           ah.award_date AS recognition_date,
                           NULL::VARCHAR AS competition_name
                    FROM actor_honors ah
                             JOIN actors a ON a.worker_id = ah.worker_id
                    UNION ALL
                    SELECT a.worker_id,
                           'награда' AS recognition_type,
                           aw.award_name AS recognition_name,
                           aw.award_date AS recognition_date,
                           aw.competition_name
                    FROM actor_awards aw
                             JOIN actors a ON a.worker_id = aw.worker_id
                )
                """);
        if (countOnly) {
            sql.append("SELECT COUNT(DISTINCT w.worker_id) AS total_with_recognition\n");
        } else {
            sql.append("""
                    SELECT DISTINCT w.last_name,
                                    w.first_name,
                                    w.gender,
                                    get_years_from_date(w.birth_date) AS age,
                                    ar.recognition_type,
                                    ar.recognition_name,
                                    ar.competition_name,
                                    ar.recognition_date
                    """);
        }
        sql.append("""
                FROM actor_recognitions ar
                         JOIN workers w ON w.worker_id = ar.worker_id
                         JOIN actors a ON a.worker_id = w.worker_id
                WHERE 1 = 1
                """);

        List<Object> stringParams = new ArrayList<>();
        List<Object> intParams = new ArrayList<>();
        List<LocalDate> dateParams = new ArrayList<>();

        if ("honor".equals(recognitionKind)) {
            sql.append(" AND ar.recognition_type <> 'награда' ");
        } else if ("award".equals(recognitionKind)) {
            sql.append(" AND ar.recognition_type = 'награда' ");
        }
        if (honorType != null) {
            sql.append(" AND ar.recognition_type = ? ");
            stringParams.add(honorType);
        }
        if (Boolean.TRUE.equals(hasHonor)) {
            sql.append("""
                     AND EXISTS (
                        SELECT 1 FROM actor_honors ah2 WHERE ah2.worker_id = a.worker_id
                    ) """);
        } else if (Boolean.FALSE.equals(hasHonor)) {
            sql.append("""
                     AND NOT EXISTS (
                        SELECT 1 FROM actor_honors ah2 WHERE ah2.worker_id = a.worker_id
                    ) """);
        }
        if (Boolean.TRUE.equals(hasAward)) {
            sql.append("""
                     AND EXISTS (
                        SELECT 1 FROM actor_awards aw2 WHERE aw2.worker_id = a.worker_id
                    ) """);
        } else if (Boolean.FALSE.equals(hasAward)) {
            sql.append("""
                     AND NOT EXISTS (
                        SELECT 1 FROM actor_awards aw2 WHERE aw2.worker_id = a.worker_id
                    ) """);
        }
        if (dateFrom != null) {
            sql.append(" AND ar.recognition_date >= ? ");
            dateParams.add(dateFrom);
        }
        if (dateTo != null) {
            sql.append(" AND ar.recognition_date <= ? ");
            dateParams.add(dateTo);
        }
        if (competition != null) {
            sql.append(" AND ar.competition_name = ? ");
            stringParams.add(competition);
        }
        if (gender != null) {
            sql.append(" AND w.gender = ? ");
            stringParams.add(gender);
        }
        if (ageMin != null) {
            sql.append(" AND get_years_from_date(w.birth_date) >= ? ");
            intParams.add(ageMin);
        }
        if (ageMax != null) {
            sql.append(" AND get_years_from_date(w.birth_date) <= ? ");
            intParams.add(ageMax);
        }
        if (!countOnly) {
            sql.append(" ORDER BY w.last_name, ar.recognition_date DESC");
        }

        return databaseService.executePrepared(sql.toString(), ps -> {
            int index = 1;
            for (Object value : stringParams) {
                ps.setString(index++, (String) value);
            }
            for (LocalDate value : dateParams) {
                ps.setDate(index++, Date.valueOf(value));
            }
            for (Object value : intParams) {
                ps.setInt(index++, (Integer) value);
            }
        });
    }

    private QueryResult executeActorPresenceQuery(
            boolean countOnly,
            Boolean hasHonor,
            Boolean hasAward,
            String gender,
            Integer ageMin,
            Integer ageMax
    ) throws SQLException {
        StringBuilder sql = new StringBuilder();
        if (countOnly) {
            sql.append("SELECT COUNT(DISTINCT w.worker_id) AS total_with_recognition\n");
        } else {
            sql.append("""
                    SELECT DISTINCT w.last_name,
                                    w.first_name,
                                    w.gender,
                                    get_years_from_date(w.birth_date) AS age
                    """);
        }
        sql.append("""
                FROM workers w
                         JOIN actors a ON a.worker_id = w.worker_id
                WHERE 1 = 1
                """);
        if (Boolean.TRUE.equals(hasHonor)) {
            sql.append(" AND EXISTS (SELECT 1 FROM actor_honors ah WHERE ah.worker_id = a.worker_id) ");
        } else if (Boolean.FALSE.equals(hasHonor)) {
            sql.append(" AND NOT EXISTS (SELECT 1 FROM actor_honors ah WHERE ah.worker_id = a.worker_id) ");
        }
        if (Boolean.TRUE.equals(hasAward)) {
            sql.append(" AND EXISTS (SELECT 1 FROM actor_awards aw WHERE aw.worker_id = a.worker_id) ");
        } else if (Boolean.FALSE.equals(hasAward)) {
            sql.append(" AND NOT EXISTS (SELECT 1 FROM actor_awards aw WHERE aw.worker_id = a.worker_id) ");
        }
        if (gender != null) {
            sql.append(" AND w.gender = ? ");
        }
        if (ageMin != null) {
            sql.append(" AND get_years_from_date(w.birth_date) >= ? ");
        }
        if (ageMax != null) {
            sql.append(" AND get_years_from_date(w.birth_date) <= ? ");
        }
        if (!countOnly) {
            sql.append(" ORDER BY w.last_name");
        }

        return databaseService.executePrepared(sql.toString(), ps -> {
            int index = 1;
            if (gender != null) {
                ps.setString(index++, gender);
            }
            if (ageMin != null) {
                ps.setInt(index++, ageMin);
            }
            if (ageMax != null) {
                ps.setInt(index++, ageMax);
            }
        });
    }

    private String replaceFirstDateRange(String sql) {
        Pattern datePattern = Pattern.compile("'\\d{4}-\\d{2}-\\d{2}'");
        Matcher matcher = datePattern.matcher(sql);
        if (!matcher.find()) {
            throw new IllegalStateException("В SQL не найдена первая дата диапазона.");
        }
        String afterFirst = matcher.replaceFirst("?");
        Matcher secondMatcher = datePattern.matcher(afterFirst);
        if (!secondMatcher.find()) {
            throw new IllegalStateException("В SQL не найдена вторая дата диапазона.");
        }
        return secondMatcher.replaceFirst("?");
    }

    private String replaceLiteral(String source, String literal) {
        int index = source.indexOf(literal);
        if (index < 0) {
            throw new IllegalStateException("Не найден фрагмент для параметризации: " + literal);
        }
        return source.substring(0, index) + "?" + source.substring(index + literal.length());
    }

    private String replaceRegexOnce(String source, String regex, String replacement) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(source);
        if (!matcher.find()) {
            throw new IllegalStateException("Не найден шаблон для параметризации: " + regex);
        }
        return matcher.replaceFirst(Matcher.quoteReplacement(replacement));
    }

    private Map<Integer, String> buildUserQueryTitles() {
        Map<Integer, String> result = new LinkedHashMap<>();
        Map<String, List<QueryDefinition>> grouped = new LinkedHashMap<>();
        Set<String> usedTitles = new LinkedHashSet<>();

        for (QueryDefinition query : allQueries) {
            if (hiddenQueryIds.contains(query.id())) {
                continue;
            }
            String groupKey = query.section() + "::" + normalizeQueryGroupTitle(query.title());
            grouped.computeIfAbsent(groupKey, key -> new ArrayList<>()).add(query);
        }

        for (List<QueryDefinition> group : grouped.values()) {
            QueryDefinition primary = group.stream()
                    .filter(query -> !isPureCountQuery(query.sql()))
                    .findFirst()
                    .orElse(group.get(0));
            String title = makeUniqueTitleWithoutNumbers(displayQueryTitle(primary), usedTitles);
            for (QueryDefinition query : group) {
                if (!hiddenQueryIds.contains(query.id())) {
                    result.put(query.id(), title);
                }
            }
        }

        result.put(1, "Все работники театра");
        result.put(7, "Гибкий фильтр работников");
        result.put(14, "Гибкий фильтр спектаклей в репертуаре");
        result.put(18, "Гибкий фильтр поставленных спектаклей");
        result.put(32, "Авторы поставленных спектаклей");
        result.put(33, "Гибкий фильтр авторов");
        result.put(34, "Гибкий поиск спектаклей (автор, страна, век)");
        result.put(42, "Актёры, подходящие на роль");
        result.put(45, "Гибкий фильтр актёров (звания и награды)");
        result.put(50, "Гибкий фильтр гастролей");
        result.put(55, "Гибкий фильтр ролей актёра");
        result.put(65, "Проданные билеты по спектаклям");
        result.put(67, "Гибкий фильтр продаж билетов");
        result.put(75, "Гибкая выручка (по спектаклю и/или периоду)");
        result.put(78, "Свободные места на предстоящие показы");
        result.put(79, "Гибкий фильтр свободных мест");
        return result;
    }

    private String normalizeQueryGroupTitle(String rawTitle) {
        String cleaned = sanitizeQueryTitle(rawTitle);
        cleaned = cleaned.replaceAll("(?iu)^(список|количество)\\s+(всех\\s+)?", "");
        cleaned = cleaned.replaceAll("(?iu)^гибкий\\s+список\\s+", "");
        cleaned = cleaned.replaceAll("(?iu)^гибкое\\s+количество\\s+", "");
        cleaned = cleaned.replaceAll("(?iu)^общее\\s+число\\s+", "");
        cleaned = cleaned.replaceAll("(?iu)^число\\s+", "");
        cleaned = cleaned.replaceAll("(?iu)^все\\s+", "");
        return cleaned.trim();
    }

    private String displayQueryTitle(QueryDefinition query) {
        String title = normalizeQueryGroupTitle(query.title());
        if (title.isBlank()) {
            title = sanitizeQueryTitle(query.title());
        }
        if (title.toLowerCase(Locale.ROOT).startsWith("гибкий фильтр")
                || title.toLowerCase(Locale.ROOT).startsWith("гибкая")
                || title.toLowerCase(Locale.ROOT).startsWith("гибкий поиск")) {
            return capitalizeFirst(title);
        }
        if (query.sql().contains("WHERE (:")) {
            return capitalizeFirst("Гибкий фильтр " + title);
        }
        return capitalizeFirst(title);
    }

    private String sanitizeSectionTitle(String rawSection) {
        if (rawSection == null || rawSection.isBlank()) {
            return "Раздел";
        }
        String cleaned = rawSection
                .replaceAll("(?iu)^\\s*запрос\\s*\\d+\\s*[:\\-]?\\s*", "")
                .replaceAll("\\s+", " ")
                .trim();
        if (cleaned.isBlank()) {
            return "Раздел";
        }
        return capitalizeFirst(cleaned);
    }

    private String makeUniqueSectionTitle(String baseTitle) {
        if (!sectionDisplayToRaw.containsKey(baseTitle)) {
            return baseTitle;
        }
        String candidate = baseTitle + " (доп.)";
        if (!sectionDisplayToRaw.containsKey(candidate)) {
            return candidate;
        }
        candidate = baseTitle + " (вариант)";
        if (!sectionDisplayToRaw.containsKey(candidate)) {
            return candidate;
        }
        return baseTitle + " (другой)";
    }

    private String makeUniqueTitleWithoutNumbers(String baseTitle, Set<String> usedTitles) {
        if (usedTitles.add(baseTitle)) {
            return baseTitle;
        }
        List<String> alternatives = List.of(
                baseTitle + " (доп.)",
                baseTitle + " (вариант)",
                baseTitle + " (альтернатива)",
                baseTitle + " (другой вариант)",
                baseTitle + " (дополнительный)"
        );
        for (String alternative : alternatives) {
            if (usedTitles.add(alternative)) {
                return alternative;
            }
        }
        String fallback = baseTitle + " (другой)";
        usedTitles.add(fallback);
        return fallback;
    }

    private String sanitizeQueryTitle(String rawTitle) {
        if (rawTitle == null || rawTitle.isBlank()) {
            return "Запрос";
        }

        String cleaned = rawTitle
                .replaceAll("(?i)query\\s*#\\d+", "")
                .replaceAll("(?iu)запрос\\s*\\d+\\s*[:\\-]?", "")
                .replaceAll("\\([^)]*\\)", "")
                .replaceAll("\\s+", " ")
                .trim();

        cleaned = cleaned.replaceAll("^[\\-—:;,\\s]+", "").replaceAll("[\\-—:;,\\s]+$", "").trim();
        if (cleaned.isBlank()) {
            return "Запрос";
        }
        return capitalizeFirst(cleaned);
    }

    private boolean isPureCountQuery(String sql) {
        String normalized = sql.replaceAll("(?m)^\\s*--.*$", "").replaceAll("\\s+", " ").trim().toUpperCase(Locale.ROOT);
        if (!normalized.startsWith("SELECT COUNT(")) {
            return false;
        }
        int fromIndex = normalized.indexOf(" FROM ");
        if (fromIndex < 0) {
            return false;
        }
        String selectPart = normalized.substring(0, fromIndex);
        return !selectPart.contains(",");
    }

    private Label createHintLabel(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setStyle("-fx-text-fill: #64748B; -fx-padding: 6 2 6 2;");
        return label;
    }

    private VBox createMetricCard(String labelText, String valueText) {
        Label label = new Label(labelText);
        label.setStyle("-fx-font-size: 13px; -fx-text-fill: #475569;");
        Label value = new Label(valueText);
        value.setStyle("-fx-font-size: 26px; -fx-font-weight: bold; -fx-text-fill: #0F172A;");

        VBox card = new VBox(6, label, value);
        card.setPadding(new Insets(14));
        card.setStyle("-fx-background-color: #EEF6FF; -fx-border-color: #C9DEFF; -fx-border-radius: 12; -fx-background-radius: 12;");
        return card;
    }

    private HBox createFieldLine(String labelText, String valueText) {
        Label label = new Label(labelText + ":");
        label.setMinWidth(230);
        label.setStyle("-fx-font-weight: bold; -fx-text-fill: #334155;");

        Label value = new Label(valueText);
        value.setWrapText(true);
        value.setStyle("-fx-text-fill: #0F172A;");

        HBox line = new HBox(8, label, value);
        line.setAlignment(Pos.TOP_LEFT);
        HBox.setHgrow(value, Priority.ALWAYS);
        return line;
    }

    private String humanizeTableName(String tableName) {
        if (tableName == null || tableName.isBlank()) {
            return "Таблица";
        }
        String normalized = tableName.toLowerCase(Locale.ROOT).trim();
        return TABLE_LABEL_TRANSLATIONS.getOrDefault(normalized, humanizeColumnName(tableName));
    }

    private String humanizeColumnName(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Поле";
        }
        String normalized = raw.toLowerCase(Locale.ROOT).trim();
        String translated = COLUMN_LABEL_TRANSLATIONS.get(normalized);
        if (translated != null) {
            return translated;
        }
        String pretty = raw.replace('_', ' ').replaceAll("\\s+", " ").trim();
        return capitalizeFirst(pretty);
    }

    private static final Map<String, String> TABLE_LABEL_TRANSLATIONS = Map.ofEntries(
            Map.entry("worker_types", "Типы работников"),
            Map.entry("countries", "Страны"),
            Map.entry("city", "Города"),
            Map.entry("theater_schools", "Театральные училища"),
            Map.entry("genres", "Жанры"),
            Map.entry("age_categories", "Возрастные категории"),
            Map.entry("theaters", "Театры"),
            Map.entry("current_theater", "Текущий театр"),
            Map.entry("seat_types", "Типы мест"),
            Map.entry("seasons", "Сезоны"),
            Map.entry("halls", "Залы"),
            Map.entry("workers", "Работники"),
            Map.entry("actors", "Актёры"),
            Map.entry("musicians", "Музыканты"),
            Map.entry("director_producers", "Режиссёры-постановщики"),
            Map.entry("designer_producers", "Художники-постановщики"),
            Map.entry("conductor_producers", "Дирижёры-постановщики"),
            Map.entry("staff", "Служащие"),
            Map.entry("actor_honors", "Звания актёров"),
            Map.entry("actor_awards", "Награды актёров"),
            Map.entry("authors", "Авторы"),
            Map.entry("spectacles", "Спектакли"),
            Map.entry("spectacle_authors", "Авторы спектаклей"),
            Map.entry("roles", "Роли"),
            Map.entry("casting", "Распределение ролей"),
            Map.entry("seats", "Места"),
            Map.entry("pricing", "Цены"),
            Map.entry("shows", "Показы"),
            Map.entry("tickets", "Билеты"),
            Map.entry("tours", "Гастроли"),
            Map.entry("tour_participants", "Участники гастролей")
    );

    private static final Map<String, String> COLUMN_LABEL_TRANSLATIONS = Map.ofEntries(
            Map.entry("last_name", "Фамилия"),
            Map.entry("first_name", "Имя"),
            Map.entry("birth_date", "Дата рождения"),
            Map.entry("gender", "Пол"),
            Map.entry("hire_date", "Дата приёма"),
            Map.entry("salary", "Зарплата"),
            Map.entry("children_count", "Количество детей"),
            Map.entry("type_name", "Тип работника"),
            Map.entry("worker_name", "ФИО"),
            Map.entry("worker_type_name", "Тип работника"),
            Map.entry("experience_years", "Стаж (лет)"),
            Map.entry("age", "Возраст"),
            Map.entry("voice_type", "Тип голоса"),
            Map.entry("height", "Рост"),
            Map.entry("weight", "Вес"),
            Map.entry("hair_color", "Цвет волос"),
            Map.entry("eye_color", "Цвет глаз"),
            Map.entry("is_student", "Студент училища"),
            Map.entry("school_name", "Театральное училище"),
            Map.entry("instrument", "Инструмент"),
            Map.entry("specialization", "Специализация"),
            Map.entry("art_style", "Стиль"),
            Map.entry("orchestra_type", "Тип оркестра"),
            Map.entry("position", "Должность"),
            Map.entry("country_name", "Страна"),
            Map.entry("city_name", "Город"),
            Map.entry("title", "Название"),
            Map.entry("spectacle_title", "Спектакль"),
            Map.entry("spectacle_name", "Спектакль"),
            Map.entry("genre_name", "Жанр"),
            Map.entry("category_name", "Возрастная категория"),
            Map.entry("age_category_name", "Возрастная категория"),
            Map.entry("theater_name", "Театр"),
            Map.entry("creation_year", "Год создания"),
            Map.entry("premiere_date", "Дата премьеры"),
            Map.entry("director_name", "Режиссёр-постановщик"),
            Map.entry("designer_name", "Художник-постановщик"),
            Map.entry("artist_name", "Художник-постановщик"),
            Map.entry("conductor_name", "Дирижёр-постановщик"),
            Map.entry("role_name", "Роль"),
            Map.entry("roles", "Роли"),
            Map.entry("roles_count", "Кол-во ролей"),
            Map.entry("is_main_role", "Главная роль"),
            Map.entry("required_gender", "Требуемый пол"),
            Map.entry("required_min_age", "Возраст от"),
            Map.entry("required_max_age", "Возраст до"),
            Map.entry("required_voice_type", "Требуемый голос"),
            Map.entry("required_min_height", "Рост от"),
            Map.entry("required_max_height", "Рост до"),
            Map.entry("actor_name", "Актёр"),
            Map.entry("musician_name", "Музыкант"),
            Map.entry("staff_member_name", "Служащий"),
            Map.entry("is_understudy", "Дублёр"),
            Map.entry("role_type", "Тип роли"),
            Map.entry("honor_type", "Тип звания"),
            Map.entry("honor_name", "Название звания"),
            Map.entry("award_name", "Награда"),
            Map.entry("competition_name", "Конкурс"),
            Map.entry("award_date", "Дата награждения"),
            Map.entry("recognition_type", "Тип"),
            Map.entry("recognition_name", "Название"),
            Map.entry("recognition_date", "Дата"),
            Map.entry("season_name", "Сезон"),
            Map.entry("start_date", "Дата начала"),
            Map.entry("end_date", "Дата окончания"),
            Map.entry("hall_name", "Зал"),
            Map.entry("seat_type_name", "Тип места"),
            Map.entry("row_number", "Ряд"),
            Map.entry("seat_number", "Номер места"),
            Map.entry("price", "Цена"),
            Map.entry("is_premiere", "Премьера"),
            Map.entry("show_date", "Дата показа"),
            Map.entry("show_time", "Время показа"),
            Map.entry("sale_date", "Дата продажи"),
            Map.entry("is_advance_sale", "Предв. продажа"),
            Map.entry("tickets_sold", "Билетов продано"),
            Map.entry("advance_sold", "Из них предв."),
            Map.entry("revenue", "Выручка"),
            Map.entry("total_revenue", "Общая выручка"),
            Map.entry("total_workers", "Всего работников"),
            Map.entry("total_actors", "Всего актёров"),
            Map.entry("total_musicians", "Всего музыкантов"),
            Map.entry("total_spectacles", "Всего спектаклей"),
            Map.entry("total_in_repertoire", "Всего в репертуаре"),
            Map.entry("total_with_honors", "Всего со званиями"),
            Map.entry("total_with_recognition", "Всего найдено"),
            Map.entry("result_count", "Количество"),
            Map.entry("author_name", "Автор"),
            Map.entry("author_country", "Страна автора"),
            Map.entry("author_century", "Век автора"),
            Map.entry("is_main", "Основной состав"),
            Map.entry("understudy_name", "Дублёр"),
            Map.entry("participant_name", "Участник"),
            Map.entry("tour_role", "Роль в гастролях"),
            Map.entry("duration_minutes", "Длительность (мин)"),
            Map.entry("capacity", "Вместимость"),
            Map.entry("seat_count", "Количество мест"),
            Map.entry("type_id", "Тип"),
            Map.entry("worker_id", "Работник"),
            Map.entry("spectacle_id", "Спектакль"),
            Map.entry("show_id", "Показ"),
            Map.entry("season_id", "Сезон"),
            Map.entry("hall_id", "Зал"),
            Map.entry("theater_id", "Театр"),
            Map.entry("genre_id", "Жанр"),
            Map.entry("author_id", "Автор"),
            Map.entry("role_id", "Роль"),
            Map.entry("country_id", "Страна"),
            Map.entry("category_id", "Категория"),
            Map.entry("school_id", "Училище"),
            Map.entry("worker_type_id", "Тип работника"),
            Map.entry("age_category_id", "Возрастная категория"),
            Map.entry("from_theater_id", "Театр отправления"),
            Map.entry("to_theater_id", "Театр прибытия"),
            Map.entry("tour_id", "Гастроль"),
            Map.entry("ticket_id", "Билет"),
            Map.entry("seat_id", "Место"),
            Map.entry("pricing_id", "Цена"),
            Map.entry("casting_id", "Запись распределения"),
            Map.entry("honor_id", "Звание"),
            Map.entry("award_id", "Награда"),
            Map.entry("is_advance", "Предварительная продажа"),
            Map.entry("total_roles", "Всего ролей"),
            Map.entry("total_tickets_sold", "Всего билетов"),
            Map.entry("from_theater", "Откуда"),
            Map.entry("to_theater", "Куда"),
            Map.entry("from_theater_name", "Откуда"),
            Map.entry("to_theater_name", "Куда"),
            Map.entry("birth_year", "Год рождения"),
            Map.entry("death_year", "Год смерти"),
            Map.entry("century", "Век"),
            Map.entry("address", "Адрес"),
            Map.entry("free_seats", "Свободных мест"),
            Map.entry("label", "Описание")
    );

    private String capitalizeFirst(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        return text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
    }

    private void renderResult(QueryResult result) {
        renderResultInto(result, resultCardsBox, summaryLabel);
    }

    private void renderResultInto(QueryResult result, VBox cardsBox, Label summary) {
        cardsBox.getChildren().clear();

        if (result.hasResultSet()) {
            List<String> sourceColumns = result.columns();
            List<List<String>> sourceRows = result.rows();
            List<Integer> visibleIndexes = new ArrayList<>();
            List<String> columns = new ArrayList<>();
            for (int i = 0; i < sourceColumns.size(); i++) {
                String column = sourceColumns.get(i);
                if (isTechnicalIdColumn(column)) {
                    continue;
                }
                visibleIndexes.add(i);
                columns.add(column);
            }

            List<List<String>> rows = new ArrayList<>();
            for (List<String> sourceRow : sourceRows) {
                List<String> filtered = new ArrayList<>(visibleIndexes.size());
                for (Integer visibleIndex : visibleIndexes) {
                    filtered.add(visibleIndex < sourceRow.size() ? sourceRow.get(visibleIndex) : "");
                }
                rows.add(filtered);
            }

            if (rows.isEmpty()) {
                cardsBox.getChildren().add(createHintLabel("По выбранным параметрам ничего не найдено."));
                summary.setText("Найдено строк: 0");
                return;
            }

            if (columns.isEmpty()) {
                cardsBox.getChildren().add(createHintLabel("Данные найдены, но технические поля ID скрыты."));
                summary.setText("Найдено строк: " + rows.size());
                return;
            }

            if (rows.size() == 1 && columns.size() == 1) {
                String metricLabel = humanizeColumnName(columns.get(0));
                String metricValue = rows.get(0).isEmpty() ? "" : rows.get(0).get(0);
                cardsBox.getChildren().add(createMetricCard(metricLabel, metricValue));
                summary.setText("Найдено строк: 1");
                return;
            }

            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                VBox card = new VBox(7);
                card.setPadding(new Insets(12));
                card.setStyle("-fx-background-color: #F8FAFC; -fx-border-color: #D5DEEA; -fx-border-radius: 10; -fx-background-radius: 10;");

                Label rowTitle = new Label("Запись " + (rowIndex + 1));
                rowTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #1E293B;");
                card.getChildren().add(rowTitle);

                List<String> row = rows.get(rowIndex);
                for (int i = 0; i < columns.size(); i++) {
                    String label = humanizeColumnName(columns.get(i));
                    String value = i < row.size() && !row.get(i).isBlank() ? row.get(i) : "—";
                    card.getChildren().add(createFieldLine(label, value));
                }
                cardsBox.getChildren().add(card);
            }

            summary.setText("Найдено строк: " + rows.size());
            return;
        }

        cardsBox.getChildren().add(createMetricCard("Операция выполнена", "Затронуто строк: " + result.updateCount()));
        summary.setText("Операция успешно завершена.");
    }

    private void loadLookupCache() throws SQLException {
        lookupCache.clear();
        lookupCache.put("season", fetchIntStringOptions("SELECT season_id, season_name FROM seasons ORDER BY season_name"));
        lookupCache.put("seasonName", fetchStringOptions("SELECT season_name FROM seasons ORDER BY season_name"));
        lookupCache.put("genre", fetchIntStringOptions("SELECT genre_id, genre_name FROM genres ORDER BY genre_name"));
        lookupCache.put("genreName", fetchStringOptions("SELECT genre_name FROM genres ORDER BY genre_name"));
        lookupCache.put("country", fetchIntStringOptions("SELECT country_id, country_name FROM countries ORDER BY country_name"));
        lookupCache.put("countryName", fetchStringOptions("SELECT country_name FROM countries ORDER BY country_name"));
        lookupCache.put("theater", fetchIntStringOptions("SELECT theater_id, theater_name FROM theaters ORDER BY theater_name"));
        lookupCache.put("theaterName", fetchStringOptions("SELECT theater_name FROM theaters ORDER BY theater_name"));
        lookupCache.put("spectacle", fetchIntStringOptions("SELECT spectacle_id, title FROM spectacles ORDER BY title"));
        lookupCache.put("author", fetchIntStringOptions("SELECT author_id, last_name || ' ' || first_name FROM authors ORDER BY last_name, first_name"));
        lookupCache.put("role", fetchIntStringOptions("""
                SELECT r.role_id, r.role_name || ' — ' || s.title
                FROM roles r
                         JOIN spectacles s ON s.spectacle_id = r.spectacle_id
                ORDER BY r.role_name
                """));
        lookupCache.put("actor", fetchIntStringOptions("""
                SELECT w.worker_id, w.last_name || ' ' || w.first_name
                FROM actors a
                         JOIN workers w ON w.worker_id = a.worker_id
                ORDER BY w.last_name, w.first_name
                """));
        lookupCache.put("director", fetchIntStringOptions("""
                SELECT w.worker_id, w.last_name || ' ' || w.first_name
                FROM director_producers d
                         JOIN workers w ON w.worker_id = d.worker_id
                ORDER BY w.last_name, w.first_name
                """));
        lookupCache.put("show", fetchIntStringOptions("""
                SELECT sh.show_id, s.title || ' (' || sh.show_date || ' ' || sh.show_time || ')'
                FROM shows sh
                         JOIN spectacles s ON s.spectacle_id = sh.spectacle_id
                ORDER BY sh.show_date, sh.show_time
                """));
        lookupCache.put("ageCategory", fetchIntStringOptions("SELECT category_id, category_name FROM age_categories ORDER BY category_name"));
        lookupCache.put("ageCategoryName", fetchStringOptions("SELECT category_name FROM age_categories ORDER BY category_name"));
        lookupCache.put("hall", fetchIntStringOptions("SELECT hall_id, hall_name FROM halls ORDER BY hall_name"));
        lookupCache.put("competition", fetchStringOptions("SELECT DISTINCT competition_name FROM actor_awards ORDER BY competition_name"));
        lookupCache.put("voiceType", fetchStringOptions("SELECT DISTINCT voice_type FROM actors WHERE voice_type IS NOT NULL ORDER BY voice_type"));
        lookupCache.put("gender", List.of(new OptionItem<>("М", "М"), new OptionItem<>("Ж", "Ж")));
        lookupCache.put("workerType", fetchStringOptions("SELECT type_name FROM worker_types ORDER BY type_name"));
        lookupCache.put("booleanFlag", List.of(new OptionItem<>("Да", true), new OptionItem<>("Нет", false)));
        lookupCache.put("resultKind", List.of(
                new OptionItem<>("Список", "list"),
                new OptionItem<>("Количество", "count")
        ));
        lookupCache.put("recognitionKind", List.of(
                new OptionItem<>("— любое —", null),
                new OptionItem<>("Звания и награды", "both"),
                new OptionItem<>("Только звания", "honor"),
                new OptionItem<>("Только награды", "award")
        ));
        lookupCache.put("booleanOptional", List.of(
                new OptionItem<>("— любое значение —", null),
                new OptionItem<>("Есть", true),
                new OptionItem<>("Нет", false)
        ));
        lookupCache.put("honorType", fetchStringOptions(
                "SELECT DISTINCT honor_type FROM actor_honors WHERE honor_type IS NOT NULL ORDER BY honor_type"));
    }

    private List<OptionItem<?>> fetchIntStringOptions(String sql) throws SQLException {
        QueryResult result = databaseService.executeSql(sql);
        List<OptionItem<?>> options = new ArrayList<>();
        for (List<String> row : result.rows()) {
            if (row.size() < 2) {
                continue;
            }
            int id = Integer.parseInt(row.get(0));
            options.add(new OptionItem<>(row.get(1), id));
        }
        return List.copyOf(options);
    }

    private List<OptionItem<?>> fetchStringOptions(String sql) throws SQLException {
        QueryResult result = databaseService.executeSql(sql);
        List<OptionItem<?>> options = new ArrayList<>();
        for (List<String> row : result.rows()) {
            if (row.isEmpty()) {
                continue;
            }
            options.add(new OptionItem<>(row.get(0), row.get(0)));
        }
        return List.copyOf(options);
    }

    private void refreshParamControlsIfNeeded() {
        QueryView current = queryComboBox == null ? null : queryComboBox.getValue();
        if (current != null) {
            onQuerySelected(current);
        }
    }

    private void logout() {
        try {
            databaseService.disconnect();
        } catch (SQLException ex) {
            showError("Ошибка отключения от БД", ex);
        }
        currentRole = null;
        activeQueries = List.of();
        primaryStage.setScene(buildLoginScene());
        Platform.runLater(this::ensurePrimaryStageVisible);
    }

    private void ensurePrimaryStageVisible() {
        if (primaryStage == null) {
            return;
        }
        Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
        double width = clamp(primaryStage.getWidth(), 1200, bounds.getWidth());
        double height = clamp(primaryStage.getHeight(), 800, bounds.getHeight());
        primaryStage.setWidth(width);
        primaryStage.setHeight(height);

        double minX = bounds.getMinX();
        double maxX = bounds.getMaxX() - width;
        double minY = bounds.getMinY();
        double maxY = bounds.getMaxY() - height;

        double x = primaryStage.getX();
        double y = primaryStage.getY();
        boolean invalidPosition = Double.isNaN(x)
                || Double.isNaN(y)
                || x < minX
                || y < minY
                || x > maxX
                || y > maxY;

        if (invalidPosition) {
            primaryStage.centerOnScreen();
        } else {
            primaryStage.setX(clamp(x, minX, maxX));
            primaryStage.setY(clamp(y, minY, maxY));
        }

        primaryStage.toFront();
        primaryStage.requestFocus();
    }

    private double clamp(double value, double min, double max) {
        if (max <= min) {
            return min;
        }
        return Math.max(min, Math.min(value, max));
    }

    private int intParam(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (!(value instanceof Integer intValue)) {
            throw new IllegalArgumentException("Параметр \"" + key + "\" должен быть числом.");
        }
        return intValue;
    }

    private String textParam(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException("Параметр \"" + key + "\" должен быть строкой.");
        }
        return text;
    }

    private LocalDate dateParam(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (!(value instanceof LocalDate date)) {
            throw new IllegalArgumentException("Параметр \"" + key + "\" должен быть датой.");
        }
        return date;
    }

    private Integer optionalIntParam(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Integer intValue) {
            return intValue;
        }
        throw new IllegalArgumentException("Параметр \"" + key + "\" должен быть числом.");
    }

    private String optionalTextParam(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            return text;
        }
        throw new IllegalArgumentException("Параметр \"" + key + "\" должен быть строкой.");
    }

    private LocalDate optionalDateParam(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate date) {
            return date;
        }
        throw new IllegalArgumentException("Параметр \"" + key + "\" должен быть датой.");
    }

    private int bindOptionalInt(PreparedStatement statement, int index, Integer value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.INTEGER);
        } else {
            statement.setInt(index, value);
        }
        return index + 1;
    }

    private int bindOptionalString(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, value);
        }
        return index + 1;
    }

    private int bindOptionalDate(PreparedStatement statement, int index, LocalDate value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.DATE);
        } else {
            statement.setDate(index, Date.valueOf(value));
        }
        return index + 1;
    }

    private int bindOptionalBoolean(PreparedStatement statement, int index, Boolean value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.BOOLEAN);
        } else {
            statement.setBoolean(index, value);
        }
        return index + 1;
    }

    private Boolean optionalBooleanParam(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        throw new IllegalArgumentException("Параметр \"" + key + "\" должен быть логическим.");
    }

    private int getSelectedLookupInt(ComboBox<OptionItem<?>> combo, String label) {
        OptionItem<?> item = combo.getValue();
        if (item == null || !(item.value() instanceof Integer value)) {
            throw new IllegalArgumentException("Не выбрано значение: " + label);
        }
        return value;
    }

    private String getSelectedLookupValue(ComboBox<OptionItem<?>> combo, String label) {
        OptionItem<?> item = combo.getValue();
        if (item == null || !(item.value() instanceof String value)) {
            throw new IllegalArgumentException("Не выбрано значение: " + label);
        }
        return value;
    }

    private String readComboEditableValue(ComboBox<?> combo, String label) {
        Object selected = combo.getValue();
        if (selected instanceof OptionItem<?> item && item.value() instanceof String value) {
            return value;
        }
        if (selected instanceof String typed && !typed.trim().isEmpty()) {
            return typed.trim();
        }
        if (combo.getEditor() != null) {
            String typed = combo.getEditor().getText();
            if (typed != null && !typed.trim().isEmpty()) {
                return typed.trim();
            }
        }
        throw new IllegalArgumentException("Укажите значение: " + label);
    }

    private String requireText(TextField field, String label) {
        String text = safeTrim(field.getText());
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Поле \"" + label + "\" не заполнено.");
        }
        return text;
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private LocalTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Укажите время.");
        }
        try {
            if (value.length() == 5) {
                return LocalTime.parse(value + ":00");
            }
            return LocalTime.parse(value);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Неверный формат времени. Используйте HH:mm или HH:mm:ss.");
        }
    }

    private void makeGrowing(TextField... fields) {
        for (TextField field : fields) {
            field.setMaxWidth(Double.MAX_VALUE);
            GridPane.setHgrow(field, Priority.ALWAYS);
        }
    }

    private List<Integer> intRange(int start, int end, int step) {
        List<Integer> values = new ArrayList<>();
        for (int i = start; i <= end; i += step) {
            values.add(i);
        }
        return List.copyOf(values);
    }

    private Map<Integer, List<ParamSpec>> buildParamSpecs() {
        Map<Integer, List<ParamSpec>> specs = new LinkedHashMap<>();

        specs.put(7, List.of(
                ParamSpec.optionalLookup("worker_type", "Тип работника", "workerType"),
                ParamSpec.optionalInt("min_experience", "Минимальный стаж (лет)", intRange(0, 40, 1), 10),
                ParamSpec.optionalLookup("gender", "Пол", "gender"),
                ParamSpec.optionalInt("birth_year", "Год рождения", intRange(1940, 2010, 1), 1985),
                ParamSpec.optionalInt("age_min", "Минимальный возраст", intRange(18, 75, 1), 30),
                ParamSpec.optionalInt("age_max", "Максимальный возраст", intRange(18, 75, 1), 40),
                ParamSpec.optionalInt("min_children", "Минимальное число детей", intRange(0, 8, 1), 0),
                ParamSpec.optionalInt("children_count", "Точное число детей", intRange(0, 8, 1), 2),
                ParamSpec.optionalInt("min_salary", "Минимальная зарплата", intRange(30000, 200000, 5000), 80000)
        ));

        List<ParamSpec> repertoireSpecs = List.of(
                ParamSpec.optionalLookup("season_name", "Сезон", "seasonName"),
                ParamSpec.optionalLookup("genre_name", "Жанр", "genreName"),
                ParamSpec.optionalLookup("theater_id", "Театр", "theater"),
                ParamSpec.optionalDate("date_from", "Дата начала периода", LocalDate.of(2024, 1, 1)),
                ParamSpec.optionalDate("date_to", "Дата конца периода", LocalDate.of(2025, 12, 31))
        );
        specs.put(14, repertoireSpecs);
        specs.put(15, repertoireSpecs);

        specs.put(18, List.of(
                ParamSpec.optionalLookup("genre_name", "Жанр", "genreName"),
                ParamSpec.optionalLookup("theater_id", "Театр", "theater"),
                ParamSpec.optionalLookup("age_category", "Возрастная категория", "ageCategoryName"),
                ParamSpec.optionalInt("year_from", "Год создания: с", intRange(1500, 2025, 1), 1801),
                ParamSpec.optionalInt("year_to", "Год создания: по", intRange(1500, 2025, 1), 1900),
                ParamSpec.optionalDate("premiere_from", "Дата премьеры: с", LocalDate.of(2010, 1, 1)),
                ParamSpec.optionalDate("premiere_to", "Дата премьеры: по", LocalDate.of(2025, 12, 31))
        ));

        specs.put(33, List.of(
                ParamSpec.optionalLookup("country_name", "Страна", "countryName"),
                ParamSpec.optionalInt("century", "Век рождения", intRange(15, 21, 1), 19),
                ParamSpec.optionalLookup("genre_name", "Жанр", "genreName"),
                ParamSpec.optionalLookup("theater_id", "Театр", "theater"),
                ParamSpec.optionalDate("date_from", "Дата премьеры: с", LocalDate.of(2010, 1, 1)),
                ParamSpec.optionalDate("date_to", "Дата премьеры: по", LocalDate.of(2025, 12, 31))
        ));

        specs.put(34, List.of(
                ParamSpec.optionalLookup("genre_name", "Жанр", "genreName"),
                ParamSpec.optionalLookup("author_id", "Автор", "author"),
                ParamSpec.optionalLookup("country_name", "Страна автора", "countryName"),
                ParamSpec.optionalInt("century", "Век написания (рождения автора)", intRange(15, 21, 1), 19),
                ParamSpec.optionalLookup("theater_id", "Театр первой постановки", "theater"),
                ParamSpec.optionalDate("date_from", "Дата премьеры: с", LocalDate.of(2010, 1, 1)),
                ParamSpec.optionalDate("date_to", "Дата премьеры: по", LocalDate.of(2025, 12, 31))
        ));

        specs.put(42, List.of(ParamSpec.lookup("role_id", "Роль", "role")));

        specs.put(45, List.of(
                ParamSpec.optionalLookup("recognition_kind", "Учитывать", "recognitionKind"),
                ParamSpec.optionalLookup("has_honor", "Наличие званий", "booleanOptional"),
                ParamSpec.optionalLookup("has_award", "Наличие наград", "booleanOptional"),
                ParamSpec.optionalLookup("honor_type", "Тип звания", "honorType"),
                ParamSpec.optionalDate("date_from", "Дата награждения: с", LocalDate.of(2010, 1, 1)),
                ParamSpec.optionalDate("date_to", "Дата награждения: по", LocalDate.of(2025, 12, 31)),
                ParamSpec.optionalLookup("competition_name", "Конкурс", "competition"),
                ParamSpec.optionalLookup("gender", "Пол", "gender"),
                ParamSpec.optionalInt("age_min", "Минимальный возраст", intRange(18, 90, 1), 30),
                ParamSpec.optionalInt("age_max", "Максимальный возраст", intRange(18, 90, 1), 70)
        ));

        specs.put(50, List.of(
                ParamSpec.optionalLookup("to_theater_id", "Театр прибытия", "theater"),
                ParamSpec.optionalLookup("from_theater_id", "Театр отправления", "theater"),
                ParamSpec.optionalDate("date_from", "Дата начала периода", LocalDate.of(2023, 1, 1)),
                ParamSpec.optionalDate("date_to", "Дата конца периода", LocalDate.of(2025, 12, 31)),
                ParamSpec.optionalLookup("spectacle_id", "Спектакль", "spectacle")
        ));

        specs.put(52, List.of(ParamSpec.lookup("spectacle_id", "Спектакль", "spectacle")));
        specs.put(53, List.of(ParamSpec.lookup("spectacle_id", "Спектакль", "spectacle")));
        specs.put(54, List.of(ParamSpec.lookup("spectacle_id", "Спектакль", "spectacle")));

        specs.put(55, List.of(
                ParamSpec.lookup("actor_id", "Актёр", "actor"),
                ParamSpec.optionalDate("date_from", "Дата премьеры: с", LocalDate.of(2010, 1, 1)),
                ParamSpec.optionalDate("date_to", "Дата премьеры: по", LocalDate.of(2025, 12, 31)),
                ParamSpec.optionalLookup("genre_name", "Жанр", "genreName"),
                ParamSpec.optionalLookup("age_category", "Возрастная категория", "ageCategoryName"),
                ParamSpec.optionalLookup("director_id", "Режиссёр-постановщик", "director")
        ));

        specs.put(67, List.of(
                ParamSpec.optionalLookup("spectacle_id", "Спектакль", "spectacle"),
                ParamSpec.optionalDate("date_from", "Дата начала периода", LocalDate.of(2024, 1, 1)),
                ParamSpec.optionalDate("date_to", "Дата конца периода", LocalDate.of(2024, 12, 31)),
                ParamSpec.optionalLookup("is_premiere", "Премьера", "booleanFlag"),
                ParamSpec.optionalLookup("is_advance_sale", "Предварительная продажа", "booleanFlag")
        ));

        specs.put(75, List.of(
                ParamSpec.optionalLookup("spectacle_id", "Спектакль", "spectacle"),
                ParamSpec.optionalDate("date_from", "Дата начала периода", LocalDate.of(2024, 1, 1)),
                ParamSpec.optionalDate("date_to", "Дата конца периода", LocalDate.of(2024, 12, 31))
        ));

        specs.put(79, List.of(
                ParamSpec.optionalLookup("spectacle_id", "Спектакль", "spectacle"),
                ParamSpec.optionalLookup("is_premiere", "Только премьеры", "booleanFlag")
        ));

        return specs;
    }

    private RolePolicy currentPolicy() {
        RolePolicy policy = rolePolicies.get(currentRole);
        if (policy == null) {
            throw new IllegalStateException("Не найдена политика доступа для роли.");
        }
        return policy;
    }

    private Map<UserRole, RolePolicy> buildRolePolicies() {
        Map<UserRole, RolePolicy> map = new LinkedHashMap<>();

        Set<Integer> hrQueries = new LinkedHashSet<>();
        hrQueries.addAll(rangeIds(1, 13));
        hrQueries.addAll(rangeIds(42, 49));
        hrQueries.addAll(rangeIds(55, 64));

        Set<Integer> producerQueries = new LinkedHashSet<>();
        producerQueries.addAll(rangeIds(14, 41));
        producerQueries.add(42);
        producerQueries.addAll(rangeIds(52, 64));

        Set<Integer> repertoireQueries = new LinkedHashSet<>();
        repertoireQueries.addAll(rangeIds(14, 41));
        repertoireQueries.addAll(rangeIds(50, 54));
        repertoireQueries.addAll(rangeIds(78, 81));

        Set<Integer> cashierQueries = new LinkedHashSet<>();
        cashierQueries.addAll(rangeIds(52, 54));
        cashierQueries.addAll(rangeIds(65, 81));

        Set<Integer> directorQueries = new LinkedHashSet<>(rangeIds(1, 81));

        map.put(UserRole.HR, new RolePolicy(
                "Кадры и персонал: сотрудники, актёры, награды.",
                hrQueries,
                List.of("workers", "actors", "musicians", "director_producers", "designer_producers", "conductor_producers", "staff", "actor_honors", "actor_awards"),
                Set.of("add_actor", "add_musician", "add_director", "add_designer", "add_conductor", "add_staff")
        ));

        map.put(UserRole.PRODUCER, new RolePolicy(
                "Постановочная часть: спектакли, роли, состав.",
                producerQueries,
                List.of("spectacles", "spectacle_authors", "roles", "casting"),
                Set.of()
        ));

        map.put(UserRole.REPERTOIRE_ADMIN, new RolePolicy(
                "Репертуар и расписание: сезоны, показы, гастроли.",
                repertoireQueries,
                List.of("seasons", "halls", "shows", "tours", "tour_participants", "pricing", "seats"),
                Set.of("add_show")
        ));

        map.put(UserRole.CASHIER, new RolePolicy(
                "Продажи: билеты, выручка, свободные места.",
                cashierQueries,
                List.of("tickets"),
                Set.of()
        ));

        map.put(UserRole.DIRECTOR, new RolePolicy(
                "Полный доступ ко всем аналитическим и операционным функциям.",
                directorQueries,
                ALL_TABLES,
                Set.of("add_actor", "add_show", "add_musician", "add_director", "add_designer", "add_conductor", "add_staff")
        ));

        return Map.copyOf(map);
    }

    private List<Integer> rangeIds(int fromInclusive, int toInclusive) {
        List<Integer> ids = new ArrayList<>();
        for (int i = fromInclusive; i <= toInclusive; i++) {
            ids.add(i);
        }
        return ids;
    }

    private void setStatus(String message) {
        if (statusLabel != null) {
            statusLabel.setText(message);
        }
    }

    private void showError(String header, SQLException ex) {
        String reason = describeReason(ex);
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Ошибка");
        alert.setHeaderText(header);
        alert.setContentText(reason);
        alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        alert.showAndWait();
        setStatus("Ошибка: " + reason);
    }

    private String describeReason(SQLException ex) {
        for (Throwable current = ex; current != null; current = current.getCause()) {
            if (current instanceof PSQLException) {
                ServerErrorMessage server = ((PSQLException) current).getServerErrorMessage();
                if (server != null && server.getMessage() != null) {
                    StringBuilder sb = new StringBuilder(server.getMessage().trim());
                    if (server.getDetail() != null && !server.getDetail().isBlank()) {
                        sb.append('\n').append(server.getDetail().trim());
                    }
                    if (server.getHint() != null && !server.getHint().isBlank()) {
                        sb.append('\n').append(server.getHint().trim());
                    }
                    return sb.toString();
                }
            }
        }
        return stripErrorContext(ex.getMessage());
    }

    private String stripErrorContext(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Неизвестная ошибка.";
        }
        String firstLine = raw.lines().findFirst().orElse(raw).trim();
        return firstLine.replaceFirst("(?i)^(ошибка|error)\\s*:\\s*", "");
    }

    private void showWarning(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Предупреждение");
        alert.setHeaderText("Проверьте параметры");
        alert.setContentText(message);
        alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        alert.showAndWait();
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        alert.showAndWait();
    }

    private enum UserRole {
        HR("Отдел кадров", "app_hr"),
        PRODUCER("Постановщик", "app_producer"),
        REPERTOIRE_ADMIN("Администратор репертуара", "app_repertoire_admin"),
        CASHIER("Кассир", "app_cashier"),
        DIRECTOR("Директор", "app_director");

        private final String title;
        private final String databaseRole;

        UserRole(String title, String databaseRole) {
            this.title = title;
            this.databaseRole = databaseRole;
        }

        public String displayName() {
            return title;
        }

        public String databaseRole() {
            return databaseRole;
        }
    }

    private enum DataOperation {
        INSERT("Добавить"),
        UPDATE("Изменить"),
        DELETE("Удалить");

        private final String title;

        DataOperation(String title) {
            this.title = title;
        }

        public String displayName() {
            return title;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    private record RolePolicy(
            String description,
            Set<Integer> queryIds,
            List<String> writableTables,
            Set<String> procedures
    ) {
        private RolePolicy {
            Objects.requireNonNull(description);
            queryIds = Set.copyOf(queryIds);
            writableTables = List.copyOf(writableTables);
            procedures = Set.copyOf(procedures);
        }

        boolean canUseQuery(int queryId) {
            return queryIds.contains(queryId);
        }

        boolean canWriteTable(String tableName) {
            return writableTables.contains(tableName);
        }

        boolean canUseProcedure(String procedureName) {
            return procedures.contains(procedureName);
        }
    }

    private enum ParamKind {
        LOOKUP,
        INTEGER,
        DATE
    }

    private record OptionItem<T>(String label, T value) {
        @Override
        public String toString() {
            return label;
        }
    }

    private record ParamSpec(
            String key,
            String label,
            ParamKind kind,
            String lookupKey,
            List<Integer> intOptions,
            Integer defaultIntValue,
            LocalDate defaultDate,
            boolean optional
    ) {
        private ParamSpec {
            Objects.requireNonNull(key);
            Objects.requireNonNull(label);
            Objects.requireNonNull(kind);
            if (kind == ParamKind.LOOKUP) {
                Objects.requireNonNull(lookupKey);
            }
            if (kind == ParamKind.INTEGER) {
                Objects.requireNonNull(intOptions);
                Objects.requireNonNull(defaultIntValue);
            }
            if (kind == ParamKind.DATE && !optional) {
                Objects.requireNonNull(defaultDate);
            }
        }

        static ParamSpec lookup(String key, String label, String lookupKey) {
            return new ParamSpec(key, label, ParamKind.LOOKUP, lookupKey, null, null, null, false);
        }

        static ParamSpec optionalLookup(String key, String label, String lookupKey) {
            return new ParamSpec(key, label, ParamKind.LOOKUP, lookupKey, null, null, null, true);
        }

        static ParamSpec intParam(String key, String label, List<Integer> options, int defaultValue) {
            return new ParamSpec(key, label, ParamKind.INTEGER, null, options, defaultValue, null, false);
        }

        static ParamSpec optionalInt(String key, String label, List<Integer> options, int defaultValue) {
            return new ParamSpec(key, label, ParamKind.INTEGER, null, options, defaultValue, null, true);
        }

        static ParamSpec date(String key, String label, LocalDate defaultDate) {
            return new ParamSpec(key, label, ParamKind.DATE, null, null, null, defaultDate, false);
        }

        static ParamSpec optionalDate(String key, String label, LocalDate defaultDate) {
            return new ParamSpec(key, label, ParamKind.DATE, null, null, null, defaultDate, true);
        }
    }

    private record QueryView(QueryDefinition definition, List<ParamSpec> params, String displayTitle) {
        @Override
        public String toString() {
            return displayTitle;
        }
    }

    private record ControlHolder(ParamSpec spec, Node control) {
    }

    private record ColumnMeta(
            String name,
            String dataType,
            boolean nullable,
            boolean hasDefault,
            boolean autoGenerated,
            boolean primaryKey,
            boolean foreignKey
    ) {
    }

    private record ForeignKeyMeta(
            String columnName,
            String referencedTable,
            String referencedColumn,
            String displayExpression
    ) {
    }

    private record MutationInput(
            ColumnMeta column,
            Node control
    ) {
    }

    private record TableMeta(
            String tableName,
            List<ColumnMeta> columns,
            String primaryKeyColumn,
            Map<String, ForeignKeyMeta> foreignKeys
    ) {
    }

    public static void main(String[] args) {
        launch(args);
    }
}
