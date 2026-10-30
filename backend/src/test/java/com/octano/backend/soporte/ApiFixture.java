package com.octano.backend.soporte;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
public final class ApiFixture {
    private ApiFixture() { }
    public record Cuenta(String token,long usuarioId,long tallerId) { public String bearer() { return "Bearer "+token; } }
    public static String taller(long condicion) { return """
        {"nombre":" Taller de prueba ","calle":" Calle de prueba ","altura":100,
         "telefono":"02392 456789","cuit":"20-12345678-6","ingresosBrutos":"000123",
         "inicioActividades":"2020-01-01","condicionIvaId":%d}
        """.formatted(condicion); }
    public static Cuenta registrar(MockMvc api,String email,long condicion) throws Exception {
        // Cada cuenta se crea por la API real, igual que al registrar un taller.
        String body="{\"email\":\""+email+"\",\"password\":\"ClaveDePrueba123!\",\"taller\":"+taller(condicion)+"}";
        String json=api.perform(post("/api/talleres").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andExpect(header().exists("Location"))
            .andReturn().getResponse().getContentAsString();
        var r=new JsonMapper().readTree(json);
        return new Cuenta(r.get("tokenAcceso").asString(),r.get("usuario").get("id").asLong(),r.get("usuario").get("tallerId").asLong());
    }
}
