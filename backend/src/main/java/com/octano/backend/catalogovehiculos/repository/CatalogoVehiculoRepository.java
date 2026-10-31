package com.octano.backend.catalogovehiculos.repository;

import com.octano.backend.catalogovehiculos.dto.CatalogoDatos.Opcion;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CatalogoVehiculoRepository {
    private final JdbcTemplate jdbc;
    public CatalogoVehiculoRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public List<Opcion> marcas() {
        return jdbc.query("SELECT id, nombre FROM octano.marca ORDER BY nombre, id", (rs, n) -> new Opcion(rs.getLong("id"), rs.getString("nombre"), null));
    }
    public List<Opcion> modelos(long marcaId) {
        return jdbc.query("SELECT id, nombre, marca_id FROM octano.modelo WHERE marca_id = ? ORDER BY nombre, id",
                (rs, n) -> new Opcion(rs.getLong("id"), rs.getString("nombre"), rs.getLong("marca_id")), marcaId);
    }
    public List<Opcion> versiones(long modeloId) {
        return jdbc.query("SELECT id, nombre, modelo_id FROM octano.version_vehiculo WHERE modelo_id = ? ORDER BY nombre, id",
                (rs, n) -> new Opcion(rs.getLong("id"), rs.getString("nombre"), rs.getLong("modelo_id")), modeloId);
    }
    public boolean existeMarca(long id) {
        return jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM octano.marca WHERE id = ?)", Boolean.class, id);
    }
    public boolean existeModelo(long id) {
        return jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM octano.modelo WHERE id = ?)", Boolean.class, id);
    }
    public Optional<Opcion> version(long id) {
        return jdbc.query("SELECT id, nombre, modelo_id FROM octano.version_vehiculo WHERE id = ?",
                (rs, n) -> new Opcion(rs.getLong("id"), rs.getString("nombre"), rs.getLong("modelo_id")), id).stream().findFirst();
    }
    public Opcion crearMarca(String nombre) {
        Long id = jdbc.queryForObject("INSERT INTO octano.marca(nombre) VALUES (?) RETURNING id", Long.class, nombre);
        return new Opcion(id, nombre, null);
    }
    public Opcion crearModelo(long marcaId, String nombre) {
        Long id = jdbc.queryForObject("INSERT INTO octano.modelo(marca_id, nombre) VALUES (?, ?) RETURNING id", Long.class, marcaId, nombre);
        return new Opcion(id, nombre, marcaId);
    }
    public Opcion crearVersion(long modeloId, String nombre) {
        Long id = jdbc.queryForObject("INSERT INTO octano.version_vehiculo(modelo_id, nombre) VALUES (?, ?) RETURNING id", Long.class, modeloId, nombre);
        return new Opcion(id, nombre, modeloId);
    }
}
