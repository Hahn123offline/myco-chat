package com.dsh.models;

/** 发给模型的工具声明（parametersJson 是 JSON Schema 对象字符串）。 */
public final class ToolSpec {

    public final String name;
    public final String description;
    public final String parametersJson;

    public ToolSpec(String name, String description, String parametersJson) {
        this.name = name == null ? "" : name;
        this.description = description == null ? "" : description;
        this.parametersJson = parametersJson == null || parametersJson.isEmpty()
                ? "{\"type\":\"object\",\"properties\":{}}" : parametersJson;
    }
}
