package com.frame.naina.models;

import java.util.List;

import com.frame.naina.Data.Language;

public class Column {

    String name;

    String type;

    Boolean isNullable;

    String columnDef;

    String comment;

    String langage;

    Boolean isFK;

    Boolean isPK;

    String FK_ref_table;

    Language configClass;

    public Column(String name, String type, Boolean isNullable, String columnDef, String langage) {
        this.name = name;
        this.type = type;
        this.isNullable = isNullable;
        this.columnDef = columnDef;
        this.langage = langage;
    }

    public String getters() {
        // return "\tpublic " + getTypeTemplate() + " get" + toCamelCase(this.name) +
        // "() {\n" +
        // "\t\treturn " + "this." + this.name + ";\n" +
        // "\t}\n";
        return this.replaceAll(this.configClass.getModule().getGetters());

    }

    public String setters() {
        // return "\tpublic void set" + toCamelCase(this.name) +
        // "(" + getTypeTemplate() + " " + this.name + ") " +
        // "{\n" +
        // "\t\tthis." + this.name + " = " + this.name + ";\n" +
        // "\t}\n";
        return this.replaceAll(this.configClass.getModule().getSetters());
    }

    public String getPkAnnotation() {
        String annotations = "";
        for (String annotation : this.configClass.getModule().getPkAnnotationModule()) {
            annotations += "\t" + annotation + "\n";
        }
        return annotations;
    }

    public String getFkAnnotation() {
        String annotations = "";
        for (String annotation : this.configClass.getModule().getFkAnnotationModule()) {
            annotations += "\t" + annotation + "\n";
        }
        annotations = replaceAll(annotations);
        return annotations;
    }

    public String getFieldAnnotation() {
        String annotations = "";
        for (String annotation : this.configClass.getModule().getFieldAnnotationModule()) {
            annotations += "\t" + annotation + "\n";
        }
        annotations = replaceAll(annotations);
        return annotations;
    }

    public String replaceAll(String line) {
        if (isFK) {
            line = line.replace("(fieldName)", this.getName());
            line = line.replace("(fieldNameMaj)", toCamelCase(this.getName()));
        }
        line = line.replace("(fieldName_only)", this.name);
        line = line.replace("(TypeTemplate)", getTypeTemplate() + "");
        line = line.replace("(fieldNameMaj)", toCamelCase(this.name) + "");
        line = line.replace("(fieldName)", this.name + "");

        return line;
    }

    public String getTypeTemplate() {
        this.type = this.type.toUpperCase();
        if (isPK)
            return configClass.getModule().getPkType();
        return isFK ? getFKTypeTemplate() : this.configClass.getTypes().get(this.type);
    }

    public String getFKTypeTemplate() {
        String typeTemplate = toCamelCase(this.FK_ref_table);
        return typeTemplate;
    }

    public static String arrayToLines(String[] array) {
        String annotations = "";
        for (String annotation : array) {
            annotations += "\t" + annotation + "\n";
        }
        return annotations;
    }

    public static String toCamelCase(String text) {
        String textCamelCase = "";
        if (text.length() > 0 && text != null) {
            Boolean upperCase = true;
            for (char letter : text.toCharArray()) {
                if (isNotCorrectCharacter(letter)) {
                    upperCase = true;
                    continue;
                }
                textCamelCase += upperCase ? Character.toUpperCase(letter) : letter;
                upperCase = false;
            }
        }
        return textCamelCase;
    }

    public static Boolean isNotCorrectCharacter(char character) {
        if (character == '_')
            return true;
        else
            return false;
    }

    public void isFK(String fk_ref_table) {
        if (fk_ref_table != null) {
            this.isFK = true;
            this.FK_ref_table = fk_ref_table;
        } else
            this.isFK = false;
    }

    public String transformFirstLetterToLowerCase(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return Character.toLowerCase(text.charAt(0)) + text.substring(1);
    }

    public static String minFirst(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return Character.toLowerCase(text.charAt(0)) + text.substring(1);
    }

    public static String CamelCase(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return toCamelCase(text);
    }

    public String getFkLabel(List<ClassBuilder> classes) {
        ClassBuilder classBuilder = null;
        String defaultLabel = "id";

        for (ClassBuilder cls : classes) {
            if (cls.getTableName().equals(this.FK_ref_table)) {
                classBuilder = cls;
                break;
            }
        }
        if (classBuilder == null)
            return "[\"" + this.getName() + "\",\"" + defaultLabel
                    + "\"]";
        else {
            Column columnLabel = getLabelFkColumn(classBuilder.getColumnsModels());
            if (columnLabel != null)
                return "[\"" + this.getName() + "\",\""
                        + (columnLabel.getName())
                        + "\"]";
            else
                return "[\"" + this.getName() + "\",\""
                        + (defaultLabel)
                        + "\"]";
        }
    }

    public String getFkLabelArray(List<ClassBuilder> classes) {
        ClassBuilder classBuilder = null;
        String defaultLabel = "id";

        for (ClassBuilder cls : classes) {
            if (cls.getTableName().equals(this.FK_ref_table)) {
                classBuilder = cls;
                break;
            }
        }
        if (classBuilder == null)
            return "[\"" + this.getName() + "\"][\"" + defaultLabel
                    + "\"]";
        else {
            Column columnLabel = getLabelFkColumn(classBuilder.getColumnsModels());
            if (columnLabel != null)

                return "[\"" + this.getName() + "\"][\""
                        + (columnLabel.getName())
                        + "\"]";
            else
                return "[\"" + this.getName() + "\"][\""
                        + (defaultLabel)
                        + "\"]";
        }
    }

    public String getFkLabelName(List<ClassBuilder> classes) {
        ClassBuilder classBuilder = null;
        String defaultLabel = "id";

        for (ClassBuilder cls : classes) {
            if (cls.getTableName().equals(this.FK_ref_table)) {
                classBuilder = cls;
                break;
            }
        }
        if (classBuilder == null) {
            return defaultLabel;
        } else {
            Column columnLabel = getLabelFkColumn(classBuilder.getColumnsModels());
            if (columnLabel != null)
                return columnLabel.getName();
            else
                return defaultLabel;
        }
    }

    public Column getLabelFkColumn(List<Column> columns) {
        for (Column column : columns) {
            if (column.getComment() != null)
                if (column.getComment().equals("label"))
                    return column;
        }
        return null;
    }

    public String getName() {
        if (isFK)
            return transformFirstLetterToLowerCase(toCamelCase(FK_ref_table));
        else
            return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Boolean getIsPK() {
        return isPK;
    }

    public void setIsPK(Boolean isPK) {
        this.isPK = isPK;
    }

    public Boolean getIsNullable() {
        return isNullable;
    }

    public void setIsNullable(Boolean isNullable) {
        this.isNullable = isNullable;
    }

    public String getColumnDef() {
        return columnDef;
    }

    public void setColumnDef(String columnDef) {
        this.columnDef = columnDef;
    }

    public String getLangage() {
        return langage;
    }

    public void setLangage(String langage) {
        this.langage = langage;
    }

    public Language getConfigClass() {
        return configClass;
    }

    public void setConfigClass(Language configClass) {
        this.configClass = configClass;
    }

    public Boolean getIsFK() {
        return isFK;
    }

    public void setIsFK(Boolean isFK) {
        this.isFK = isFK;
    }

    public String getFK_ref_table() {
        return FK_ref_table;
    }

    public void setFK_ref_table(String fK_ref_table) {
        FK_ref_table = fK_ref_table;
    }

    @Override
    public String toString() {
        return "Column [name=" + name + ", type=" + type + ", isNullable=" + isNullable + ", columnDef=" + columnDef
                + "]";
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

}
