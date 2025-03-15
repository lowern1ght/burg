package org.dawnoftime.onceuponatown.entity;

public class Profession {
    public static final Profession BUILDER = new Profession("builder");
    public static final Profession UNEMPLOYED = new Profession("unemployed");
    private final String id;

    public static Profession of(String id) {
        return switch (id) {
            case "builder" -> BUILDER;
            case "unemployed" -> UNEMPLOYED;
            default -> new Profession(id);
        };
    }

    private Profession(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }
}
