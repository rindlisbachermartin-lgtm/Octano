package com.octano.backend.autenticacion;

import com.octano.backend.soporte.ApiFixture;

import java.time.Instant;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class AuthApiTests {
    @Autowired MockMvc api;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtEncoder encoder;
    ApiFixture.Cuenta cuenta;
    long condicion;
    final JsonMapper json = new JsonMapper();

    @BeforeEach void prepararCuenta() throws Exception {
        condicion=jdbc.queryForObject("SELECT id FROM octano.condicion_iva WHERE nombre='Consumidor Final'",Long.class);
        cuenta=ApiFixture.registrar(api,"admin@primero.test",condicion);
    }
    private long crearUsuario(String email,String rol,Long mecanicoId) throws Exception {
        String body="{\"email\":\""+email+"\",\"password\":\"ClaveDePrueba123!\",\"rol\":\""+rol+"\",\"mecanicoId\":"+mecanicoId+"}";
        String respuesta=api.perform(post("/api/usuarios").header("Authorization",cuenta.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id=json.readTree(respuesta).get("id").asLong();
        api.perform(get("/api/usuarios/{id}",id).header("Authorization",cuenta.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rol").value(rol));
        return id;
    }
    private String login(String email) throws Exception {
        String respuesta=api.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\""+email+"\",\"password\":\"ClaveDePrueba123!\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer "+json.readTree(respuesta).get("tokenAcceso").asString();
    }

    @Test void protegeLaSesionYGuardaSoloElHashDeLaPassword() throws Exception {
        // Sin token, con firma alterada o con fecha vencida, la API debe rechazar el acceso.
        api.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        String token=cuenta.token(); int firma=token.lastIndexOf('.')+1;
        String alterado=token.substring(0,firma)+(token.charAt(firma)=='a'?'b':'a')+token.substring(firma+1);
        api.perform(get("/api/auth/me").header("Authorization","Bearer "+alterado)).andExpect(status().isUnauthorized());
        Instant ahora=Instant.now();
        String vencido=encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().issuer("octano").subject(Long.toString(cuenta.usuarioId())).claim("version",0)
                        .issuedAt(ahora.minusSeconds(3600)).expiresAt(ahora.minusSeconds(120)).build())).getTokenValue();
        api.perform(get("/api/auth/me").header("Authorization","Bearer "+vencido)).andExpect(status().isUnauthorized());
        api.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@primero.test\",\"password\":\"incorrecta\"}")).andExpect(status().isUnauthorized());
        String hash=jdbc.queryForObject("SELECT password_hash FROM octano.usuario WHERE id=?",String.class,cuenta.usuarioId());
        assertTrue(hash.startsWith("{bcrypt}")); assertFalse(hash.contains("ClaveDePrueba123!"));
        api.perform(get("/api/auth/me").header("Authorization",login("admin@primero.test")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test void cerrarSesionRevocaLosTokensAnteriores() throws Exception {
        // El token deja de servir incluso si todavía no llegó a su fecha de vencimiento.
        api.perform(post("/api/auth/logout").header("Authorization",cuenta.bearer())).andExpect(status().isNoContent());
        api.perform(get("/api/auth/me").header("Authorization",cuenta.bearer())).andExpect(status().isUnauthorized());
    }

    @Test void cadaAdministradorSoloVeLosUsuariosYDatosDeSuTaller() throws Exception {
        // Conocer el ID ajeno no permite leerlo ni darlo de baja.
        var otra=ApiFixture.registrar(api,"admin@segundo.test",condicion);
        api.perform(get("/api/usuarios/{id}",cuenta.usuarioId()).header("Authorization",otra.bearer())).andExpect(status().isNotFound());
        api.perform(get("/api/talleres/{id}",cuenta.tallerId()).header("Authorization",otra.bearer())).andExpect(status().isNotFound());
        api.perform(get("/api/usuarios").header("Authorization",otra.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        api.perform(patch("/api/usuarios/{id}/estado",cuenta.usuarioId()).header("Authorization",otra.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}")).andExpect(status().isNotFound());
    }

    @Test void recepcionYMecanicoNoPuedenAdministrarUsuarios() throws Exception {
        api.perform(get("/api/usuarios/roles").header("Authorization",cuenta.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3));
        // Recepción puede consultar el taller, pero solo ADMIN administra cuentas.
        crearUsuario("recepcion@prueba.test","RECEPCION",null);
        String recepcion=login("recepcion@prueba.test");
        api.perform(get("/api/talleres").header("Authorization",recepcion)).andExpect(status().isOk());
        api.perform(get("/api/usuarios").header("Authorization",recepcion)).andExpect(status().isForbidden());
        // Este es solo un dato de prueba para verificar el vínculo del usuario MECANICO.
        long mecanico=jdbc.queryForObject("""
                INSERT INTO octano.mecanico(taller_id,nombre,apellido,telefono,fecha_inicio)
                VALUES (?,'Mecánico','Prueba','02392 456789','2020-01-01') RETURNING id
                """,Long.class,cuenta.tallerId());
        crearUsuario("mecanico@prueba.test","MECANICO",mecanico);
        api.perform(get("/api/usuarios").header("Authorization",login("mecanico@prueba.test"))).andExpect(status().isForbidden());
    }

    @Test void conservaUnAdministradorYLaBajaDeUsuarioRevocaSuAcceso() throws Exception {
        // El último administrador no puede darse de baja.
        api.perform(patch("/api/usuarios/{id}/estado",cuenta.usuarioId()).header("Authorization",cuenta.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}")).andExpect(status().isBadRequest());
        crearUsuario("otroadmin@prueba.test","ADMIN",null);
        String otro=login("otroadmin@prueba.test");
        api.perform(patch("/api/usuarios/{id}/estado",cuenta.usuarioId()).header("Authorization",otro)
                .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}")).andExpect(status().isOk());
        api.perform(get("/api/auth/me").header("Authorization",cuenta.bearer())).andExpect(status().isUnauthorized());
    }

    @Test void cambiarLaPasswordInvalidaLasSesionesAnteriores() throws Exception {
        // Cambiar la contraseña devuelve un token nuevo e invalida los anteriores.
        String respuesta=api.perform(put("/api/auth/password").header("Authorization",cuenta.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"actual\":\"ClaveDePrueba123!\",\"nueva\":\"OtraClaveSegura123!\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String nuevo=json.readTree(respuesta).get("tokenAcceso").asString();
        api.perform(get("/api/auth/me").header("Authorization",cuenta.bearer())).andExpect(status().isUnauthorized());
        api.perform(get("/api/auth/me").header("Authorization","Bearer "+nuevo)).andExpect(status().isOk());
    }

    @Test void conservaElRegistroAnteriorComoRutaCompatible() throws Exception {
        // Las peticiones que ya usan /auth/registro también deben crear la cuenta y el taller.
        String body="{\"email\":\"registro.compatible@prueba.test\",\"password\":\"ClaveDePrueba123!\",\"taller\":"+ApiFixture.taller(condicion)+"}";
        String respuesta=api.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.usuario.rol").value("ADMIN"))
                .andReturn().getResponse().getContentAsString();
        var sesion=json.readTree(respuesta);
        api.perform(get("/api/talleres/{id}",sesion.get("usuario").get("tallerId").asLong())
                .header("Authorization","Bearer "+sesion.get("tokenAcceso").asString())).andExpect(status().isOk());
    }

    @Test void elAltaDeTallerExigeLosDatosDeSuAdministrador() throws Exception {
        // El cuerpo anterior, que solo tenía datos del taller, ahora recibe errores claros de formulario.
        long cantidad=jdbc.queryForObject("SELECT count(*) FROM octano.taller",Long.class);
        api.perform(post("/api/talleres").contentType(MediaType.APPLICATION_JSON).content(ApiFixture.taller(condicion)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores.taller").exists())
                .andExpect(jsonPath("$.errores.email").exists()).andExpect(jsonPath("$.errores.password").exists());
        assertEquals(cantidad,jdbc.queryForObject("SELECT count(*) FROM octano.taller",Long.class));
    }
}
