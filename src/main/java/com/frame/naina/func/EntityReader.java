package com.frame.naina.func;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import com.frame.naina.Data.Form;
import com.frame.naina.Data.LandMark;
import com.frame.naina.Data.Language;
import com.frame.naina.Data.Setup;
import com.frame.naina.Data.Template;
import com.frame.naina.connection.Connect;
import com.frame.naina.models.ClassBuilder;
import com.frame.naina.models.Column;
import com.frame.naina.utils.Transform;

public class EntityReader {

    private String database;

    private String username;

    private String password;

    private String packageName;

    private String pathFile;

    private String langage;

    private List<String> imports = new ArrayList<String>();

    private Template template;

    private Language language;

    private Setup setup;

    public EntityReader(String database, String username, String password, Input input) {
        this.database = database;
        this.username = username;
        this.password = password;
        addInputData(input);
    }

    public void readTables() throws SQLException, FileNotFoundException, IOException {
        Connection connect = Connect.getConnection(this.setup.getDatabase());

        List<ClassBuilder> classes = getAllClassesSchemaBuilder(connect);
        Transform.unzip(LandMark.STRUCT_DATA, this.pathFile + this.setup.getProjectName());

        for (ClassBuilder classBuilder : classes) {
            classBuilder.setPackageName(this.packageName);
            classBuilder.setImports(imports);
            buildModel(classBuilder);
            buildRespository(classBuilder);
            buildController(classBuilder);
        }
        buildView(classes);
        if (this.language.getResponseHandler() != null)
            this.language.getResponseHandler().build(this);

        connect.close();
        moveFolderInStruct();
        createApplicationProperty();
        mainApp();
        testApp();
    }

    public void moveFolderInStruct() throws IOException {
        String separator = "--oo--";
        String pack = this.packageName.replaceAll("\\.", separator);
        String[] packagePath = pack.split(separator);
        Transform.deleteFolder(this.pathFile + this.setup.getProjectName() + "/src/main/java/");
        Transform.createFolder(this.pathFile + this.setup.getProjectName() + "/src/main/java/");
        Transform.createFolder(this.pathFile + this.setup.getProjectName() + "/src/main/java/" + packagePath[0] + "/");
        Transform.moveFolder(this.pathFile + packagePath[0],
                this.pathFile + this.setup.getProjectName() + "/src/main/java/" + packagePath[0] + "/");
        //
        // move auth folder into the project source
        StringBuffer pathSrc = new StringBuffer();
        for (int i = 0; i < packagePath.length; i++) {
            pathSrc.append(packagePath[i] + "/");
        }
        String srcPath = this.pathFile + this.setup.getProjectName() + "/src/main/java/" + pathSrc;
        Transform.unzip(LandMark.AUTH_DATA, srcPath);
        Transform.rewriteAll(srcPath + LandMark.AUTH_FOLDER_NAME, "(package_name)", packageName);
    }

    public void buildModel(ClassBuilder classBuilder) {
        classBuilder.setTemplateFile(this.template.model);
        classBuilder.setColumnsModels(classBuilder.getColumns());
        classBuilder.getImports().clear();
        classBuilder.setImports(arrayToListString(classBuilder.getLanguage().getModel().getImports()));
        classBuilder.getLanguage().setModule(classBuilder.getLanguage().getModel());
        classBuilder.build(this.pathFile, classBuilder.getLanguage().getModel().getContext());
    }

    public void buildRespository(ClassBuilder classBuilder) {
        classBuilder.setTemplateFile(this.template.repository);
        classBuilder.getImports().clear();
        classBuilder.setImports(arrayToListString(classBuilder.getLanguage().getRepository().getImports()));
        classBuilder.getLanguage().setModule(classBuilder.getLanguage().getRepository());
        classBuilder.build(this.pathFile, classBuilder.getLanguage().getRepository().getContext());
    }

    public void buildController(ClassBuilder classBuilder) {
        classBuilder.setTemplateFile(this.template.controller);
        classBuilder.getImports().clear();
        classBuilder.setImports(arrayToListString(classBuilder.getLanguage().getController().getImports()));
        classBuilder.getLanguage().setModule(classBuilder.getLanguage().getController());
        classBuilder.build(this.pathFile, classBuilder.getLanguage().getController().getContext());
    }

    public void buildView(List<ClassBuilder> classes) throws IOException {
        for (ClassBuilder classBuilder : classes) {
            classBuilder.getImports().clear();
            classBuilder.getLanguage().setModule(classBuilder.getLanguage().getView());
        }
        Form.handleView(classes, this.language, this.setup.getProjectName(), this.pathFile);

    }

    public List<String> arrayToListString(String[] array) {
        List<String> list = new ArrayList<String>();
        for (String s : array) {
            list.add(s);
        }
        return list;
    }

    public Boolean isIgnored(String tableName) {
        List<String> tableToIgnore = arrayToListString(this.language.getIgnoreTable());
        return tableToIgnore.contains(tableName);
    }

    public List<ClassBuilder> getAllClassesSchemaBuilder(Connection connection)
            throws SQLException, FileNotFoundException {
        DatabaseMetaData metaData = connection.getMetaData();
        ResultSet resultSet = metaData.getTables(null, null, "%", new String[] {
                "TABLE" });
        List<ClassBuilder> classes = new Vector<ClassBuilder>();

        Language configClass = Language.getLangageConfig(this.langage.toLowerCase());
        this.language = configClass;
        while (resultSet.next()) {

            String tableName = resultSet.getString("TABLE_NAME");
            // ignore specified table
            if (isIgnored(tableName))
                continue;

            ResultSet resCol = metaData.getColumns(null, null, tableName, null);

            ClassBuilder classBuilder = new ClassBuilder(tableName);
            classBuilder.setlanguage(configClass);
            while (resCol.next()) {
                String columnName = resCol.getString("COLUMN_NAME");
                String columnType = resCol.getString("TYPE_NAME");
                String isNullable = resCol.getString("IS_NULLABLE");
                String columnDefinition = resCol.getString("COLUMN_DEF");

                try {
                    String comment = getColumnComment(connection, tableName, columnName);
                    String fk_table_ref = isFK(connection, tableName, columnName, metaData);
                    Boolean isPK = isPK(connection, tableName, columnName, metaData);
                    Column col = toColumn(columnName, columnType, isNullable, columnDefinition, fk_table_ref);
                    col.setConfigClass(configClass);
                    col.setIsPK(isPK);
                    col.setComment(comment);
                    classBuilder.getColumns().add(col);
                } catch (Exception e) {
                    System.out.println(e);
                }
            }
            classes.add(classBuilder);
            resCol.close();
        }
        resultSet.close();
        return classes;
    }

    private String getColumnComment(Connection connection, String tableName, String columnName)
            throws SQLException {
        String query = "SELECT a.attname AS column_name,\n" +
                "pd.description AS column_description\n" +
                "FROM pg_attribute a\n" +
                "JOIN pg_class c ON a.attrelid = c.oid\n" +
                "JOIN pg_description pd ON pd.objoid = c.oid AND pd.objsubid = a.attnum\n" +
                "WHERE c.relname = ? AND a.attname = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, tableName);
            statement.setString(2, columnName);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    String colNameIn = resultSet.getString("column_name");
                    if (colNameIn.equals(columnName)) {
                        return resultSet.getString("column_description");
                    }
                }
            }
            statement.close();
        } catch (Exception e) {
            System.out.println(e);
        }
        return null;
    }

    public String isFK(Connection connection, String tableName, String columnName, DatabaseMetaData metaData)
            throws Exception {
        ResultSet foreignKeys = metaData.getImportedKeys(connection.getCatalog(), null, tableName);
        boolean isForeignKey = false;
        String fk_column = null;
        while (foreignKeys.next()) {
            String fkColumnName = foreignKeys.getString("FKCOLUMN_NAME"); ///
            if (columnName.equals(fkColumnName)) {
                isForeignKey = true;
                String referencedTableName = foreignKeys.getString("PKTABLE_NAME");
                fk_column = referencedTableName;
                break;
            }
        }
        foreignKeys.close();
        return isForeignKey ? fk_column : null;
    }

    public Boolean isPK(Connection connection, String tableName, String columnName, DatabaseMetaData metaData)
            throws Exception {

        ResultSet primaryKeys = metaData.getPrimaryKeys(null, null, tableName);
        boolean isPrimaryKey = false;
        while (primaryKeys.next()) {
            String pkColumnName = primaryKeys.getString("COLUMN_NAME"); ///
            if (columnName.equals(pkColumnName)) {
                isPrimaryKey = true;
                break;
            }
        }
        return isPrimaryKey;
    }

    public Column toColumn(String columnName, String typeName, String isNullable, String definition,
            String fk_ref_table) {
        Boolean is_nullable = Boolean.parseBoolean(isNullable);
        Column column = new Column(columnName, typeName, is_nullable, definition, this.langage);
        column.isFK(fk_ref_table);
        return column;
    }

    public void showColumnResultSet(ResultSet resultSet) throws SQLException {
        ResultSetMetaData resultSetMD = resultSet.getMetaData();
        int columnCount = resultSetMD.getColumnCount();
        for (int i = 1; i <= columnCount; i++) {
            String columnName = resultSetMD.getColumnName(i);
            System.out.println("Column " + i + ": " + columnName);
        }
    }

    public void addInputData(Input input) {
        if (input != null) {
            this.setSetup(input.getSetup());
            this.setPackageName(input.getPackageName());
            this.setPathFile(input.getPathFile());
            this.setTemplate(input.getSetup().getTemplate());
            this.setLangage(input.getLangage());
            this.setImports(input.getImports());
        }
    }

    public void createApplicationProperty() throws FileNotFoundException {
        String content = Transform.getContent(this.template.getProperties());
        content = content.replace("(name)", this.setup.getDatabase().getName());
        content = content.replace("(host)", this.setup.getDatabase().getHost());
        content = content.replace("(dbname)", this.setup.getDatabase().getDatabaseName());
        content = content.replace("(username)", this.setup.getDatabase().getUsername());
        content = content.replace("(password)", this.setup.getDatabase().getPassword());
        content = content.replace("(port)", this.setup.getDatabase().getPort().toString());
        content = content.replace("(driver)", this.setup.getDatabase().getDriver());
        Transform.writeFile("application.properties", content,
                this.pathFile + this.setup.getProjectName() + "/src/main/resources/");
    }

    public void mainApp() throws FileNotFoundException {

        String appName = Column.CamelCase(this.setup.getProjectName().trim().replace(" ", "")) + "Application";
        String content = Transform.getContent(this.language.getMainTemplate());
        content = content.replace("(appName)", appName);
        content = content.replace("(package_name)", this.packageName);

        Transform.writeFile(appName + "." + this.langage.toLowerCase(), content,
                this.pathFile +
                        this.setup.getProjectName() + "/src/main/java/"
                        + this.packageName.replace(".", "/") + '/');
    }

    public void testApp() throws FileNotFoundException {
        String appName = Column.CamelCase(this.setup.getProjectName().trim().replace(" ", "")) + "ApplicationTests";
        String content = Transform.getContent(this.language.getTestTemplate());
        content = content.replace("(testName)", appName);
        content = content.replace("(appName)", appName);
        content = content.replace("(package_name)", this.packageName);

        Transform.deleteFolder(this.pathFile + this.setup.getProjectName() + "/src/test/java/");
        Transform.createFolders(
                this.pathFile + this.setup.getProjectName() + "/src/test/java/" + this.packageName.replace(".", "/")
                        + '/');
        Transform.writeFile(appName + "." + this.langage.toLowerCase(), content,
                this.pathFile + this.setup.getProjectName() + "/src/test/java/"
                        + this.packageName.replace(".", "/")
                        + '/');
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getPathFile() {
        return pathFile;
    }

    public void setPathFile(String pathFile) {
        this.pathFile = pathFile;
    }

    public String getLangage() {
        return langage;
    }

    public void setLangage(String langage) {
        this.langage = langage;
    }

    public List<String> getImports() {
        return imports;
    }

    public void setImports(List<String> imports) {
        this.imports = imports;
    }

    public Template getTemplate() {
        return template;
    }

    public void setTemplate(Template template) {
        this.template = template;
    }

    public Language getLanguage() {
        return language;
    }

    public void setLanguage(Language language) {
        this.language = language;
    }

    public Setup getSetup() {
        return setup;
    }

    public void setSetup(Setup setup) {
        this.setup = setup;
    }

}
