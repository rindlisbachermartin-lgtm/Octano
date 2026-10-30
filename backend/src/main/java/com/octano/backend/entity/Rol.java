package com.octano.backend.entity;
public enum Rol {
    ADMIN((short)1), RECEPCION((short)2), MECANICO((short)3);
    private final short id;
    Rol(short id) { this.id = id; }
    public short id() { return id; }
    public static Rol desde(short id) {
        for (Rol rol : values()) if (rol.id == id) return rol;
        throw new IllegalArgumentException("Rol desconocido.");
    }
}
