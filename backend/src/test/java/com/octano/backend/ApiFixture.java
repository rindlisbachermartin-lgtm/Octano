package com.octano.backend;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
final class ApiFixture {
    private ApiFixture() { }
    record Cuenta(String token,long usuarioId,long tallerId) { String bearer() { return "Bearer "+token; } }
    static String taller(long condicion) { return """
        {"nombre":" Taller de prueba ","calle":" Calle de prueba ","altura":100,
         "telefono":"02392 456789","cuit":"20-12345678-6","ingresosBrutos":"000123",
         "inicioActividades":"2020-01-01","condicionIvaId":%d}
        """.formatted(condicion); }
    static Cuenta registrar(MockMvc api,String email,long condicion) throws Exception {
        // Cada cuenta se crea por la API real, igual que al registrar un taller.
        String body="{\"email\":\""+email+"\",\"password\":\"ClaveDePrueba123!\",\"taller\":"+taller(condicion)+"}";
        String json=api.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var r=new JsonMapper().readTree(json);
        return new Cuenta(r.get("tokenAcceso").asText(),r.get("usuario").get("id").asLong(),r.get("usuario").get("tallerId").asLong());
    }
}
