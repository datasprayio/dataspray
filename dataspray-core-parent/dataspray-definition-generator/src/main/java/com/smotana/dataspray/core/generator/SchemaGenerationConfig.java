package com.smotana.dataspray.core.generator;

import org.jsonschema2pojo.AnnotationStyle;
import org.jsonschema2pojo.DefaultGenerationConfig;
import org.jsonschema2pojo.InclusionLevel;
import org.jsonschema2pojo.SourceType;

import java.io.File;
import java.io.FileFilter;
import java.net.URL;
import java.util.Iterator;
import java.util.Set;

public class SchemaGenerationConfig extends DefaultGenerationConfig {
    private final URL schemaInputFileYamlUrl;
    private final File codeGenOutputDir;

    public SchemaGenerationConfig(URL schemaInputFileYamlUrl, File codeGenOutputDir) {
        this.schemaInputFileYamlUrl = schemaInputFileYamlUrl;
        this.codeGenOutputDir = codeGenOutputDir;
    }


    @Override
    public Iterator<URL> getSource() {
        return Set.of(schemaInputFileYamlUrl).iterator();
    }

    @Override
    public FileFilter getFileFilter() {
        return file -> file.isFile() && file.getName().endsWith(".yaml");
    }

    @Override
    public String[] getFileExtensions() {
        return new String[]{"yaml", "json", "yml"};
    }

    @Override
    public boolean isGenerateBuilders() {
        return true;
    }

    @Override
    public boolean isUseInnerClassBuilders() {
        return true;
    }

    @Override
    public boolean isIncludeDynamicBuilders() {
        return true;
    }

    @Override
    public AnnotationStyle getAnnotationStyle() {
        return AnnotationStyle.JACKSON2;
    }

    @Override
    public SourceType getSourceType() {
        return SourceType.YAMLSCHEMA;
    }

    @Override
    public boolean isIncludeTypeInfo() {
        return false;
    }

    @Override
    public File getTargetDirectory() {
        return codeGenOutputDir;
    }

    @Override
    public String getTargetPackage() {
        return "com.smotana.dataspray.core.definition.model";
    }

    @Override
    public boolean isUseLongIntegers() {
        return true;
    }

    @Override
    public boolean isUseDoubleNumbers() {
        return true;
    }

    @Override
    public boolean isUseTitleAsClassname() {
        return true;
    }

    @Override
    public InclusionLevel getInclusionLevel() {
        return InclusionLevel.NON_ABSENT;
    }

    @Override
    public boolean isIncludeJsr305Annotations() {
        return true;
    }

    @Override
    public boolean isUseOptionalForGetters() {
        return true;
    }

    @Override
    public boolean isRemoveOldOutput() {
        return false;
    }

    @Override
    public boolean isIncludeConstructors() {
        return false;
    }

    @Override
    public boolean isIncludeCopyConstructor() {
        return true;
    }

    @Override
    public boolean isIncludeAdditionalProperties() {
        return false;
    }

    @Override
    public boolean isIncludeSetters() {
        return false;
    }

    @Override
    public boolean isIncludeDynamicAccessors() {
        return false;
    }

    @Override
    public boolean isIncludeDynamicGetters() {
        return false;
    }

    @Override
    public String getDateTimeType() {
        return "java.time.LocalDateTime";
    }

    @Override
    public String getDateType() {
        return "java.time.LocalDate";
    }

    @Override
    public String getTimeType() {
        return "java.time.LocalTime";
    }

    @Override
    public boolean isIncludeGeneratedAnnotation() {
        return false;
    }
}
