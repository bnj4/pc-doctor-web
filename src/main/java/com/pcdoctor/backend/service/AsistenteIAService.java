package com.pcdoctor.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pcdoctor.backend.model.Diagnostico;
import com.pcdoctor.backend.model.HistorialIA;
import com.pcdoctor.backend.repository.DiagnosticoRepository;
import com.pcdoctor.backend.repository.HistorialIARepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
@SuppressWarnings("null")
public class AsistenteIAService {

    private final DiagnosticoRepository diagnosticoRepository;
    private final HistorialIARepository historialIARepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${ai.groq.api-key:}")
    private String apiKey;

    @Value("${ai.groq.model:llama-3.3-70b-versatile}")
    private String model;

    @Value("${ai.groq.system-prompt:Eres PC Doctor IA, asistente experto en diagnóstico, asesoría y reparación de computadores en Chile.}")
    private String baseSystemPrompt;

    public AsistenteIAService(DiagnosticoRepository diagnosticoRepository,
                              HistorialIARepository historialIARepository) {
        this.diagnosticoRepository = diagnosticoRepository;
        this.historialIARepository = historialIARepository;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public Map<String, Object> procesarChatIA(String textoUsuario, String sessionId) {
        String sesionSegura = (sessionId != null && !sessionId.trim().isEmpty())
                ? sessionId.trim()
                : "SESSION_" + System.currentTimeMillis();

        if (textoUsuario == null || textoUsuario.trim().isEmpty()) {
            return crearRespuestaError("Por favor escribe tu consulta técnica para poder ayudarte.", false);
        }

        if (apiKey == null || apiKey.trim().isEmpty() || apiKey.startsWith("PEGA_AQUI")) {
            return crearRespuestaError(
                "⚠️ Falta configurar `ai.groq.api-key` en `application.properties` con tu clave gratuita de console.groq.com.",
                true
            );
        }

        try {
            List<HistorialIA> historialPrevio = historialIARepository.findBySessionId(sesionSegura);
            String systemInstruction = construirSystemInstruction();
            ArrayNode messages = construirMensajesOpenAI(systemInstruction, historialPrevio, textoUsuario.trim());

            LinkedHashSet<String> candidatos = new LinkedHashSet<>();
            candidatos.add(model.trim());
            candidatos.add("llama-3.3-70b-versatile");
            candidatos.add("llama-3.1-8b-instant");
            candidatos.add("openai/gpt-oss-120b");

            HttpResponse<String> response = null;

            for (String candidato : candidatos) {
                if (candidato == null || candidato.isBlank()) continue;

                ObjectNode requestBody = objectMapper.createObjectNode();
                requestBody.put("model", candidato);
                requestBody.set("messages", messages);
                requestBody.put("temperature", 0.4);
                requestBody.putObject("response_format").put("type", "json_object");

                String jsonPayload = objectMapper.writeValueAsString(requestBody);

                System.out.println("[PC Doctor IA - Groq] Consultando modelo: " + candidato);
                response = enviarPeticionGroq(jsonPayload);
                int status = response.statusCode();

                if (status == 200) {
                    System.out.println("[PC Doctor IA - Groq] Respuesta exitosa con: " + candidato);
                    break;
                }

                if (status == 429 || status == 503 || status == 404) {
                    System.out.println("[PC Doctor IA - Groq] Modelo '" + candidato + "' devolvió HTTP " + status + ". Probando siguiente...");
                    Thread.sleep(300);
                    continue;
                }

                break;
            }

            if (response == null || response.statusCode() != 200) {
                int code = (response != null) ? response.statusCode() : 500;
                String detalle = (response != null) ? extraerMensajeError(response.body()) : "Sin respuesta del servidor.";
                System.err.println("[PC Doctor IA - Groq] Error HTTP " + code + ": " + (response != null ? response.body() : ""));
                return crearRespuestaError(
                    "Error al conectar con la API de IA (HTTP " + code + "): " + detalle,
                    true
                );
            }

            JsonNode root = objectMapper.readTree(response.body());
            String rawText = root.path("candidates").isMissingNode()
                    ? root.path("choices").path(0).path("message").path("content").asText("")
                    : "";

            String cleanJson = limpiarBloquesMarkdown(rawText);
            JsonNode parsedJson = objectMapper.readTree(cleanJson);

            @SuppressWarnings("unchecked")
            Map<String, Object> resultadoIa = objectMapper.convertValue(parsedJson, Map.class);

            resultadoIa.put("exito", true);
            String textoRespuesta = String.valueOf(resultadoIa.getOrDefault("respuestaIa", ""));
            boolean diagListo = Boolean.TRUE.equals(resultadoIa.get("diagnosticoListo"));
            boolean fueraDominio = Boolean.TRUE.equals(resultadoIa.get("fueraDeDominio"));

            String estadoHistorial = fueraDominio ? "FUERA_DOMINIO"
                    : (diagListo ? "DIAGNOSTICO_COMPLETADO" : "EN_CONVERSACION");

            guardarInteraccion(sesionSegura, textoUsuario.trim(), textoRespuesta, estadoHistorial, historialPrevio.size() + 1);

            return resultadoIa;

        } catch (Exception e) {
            e.printStackTrace();
            return crearRespuestaError(
                "Ocurrió un error al comunicarse con el motor de IA: " + e.getMessage(),
                true
            );
        }
    }

    private HttpResponse<String> enviarPeticionGroq(String jsonPayload) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.groq.com/openai/v1/chat/completions"))
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("Authorization", "Bearer " + apiKey.trim())
                .timeout(Duration.ofSeconds(25))
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private ArrayNode construirMensajesOpenAI(String systemInstruction, List<HistorialIA> historialPrevio, String mensajeActual) {
        ArrayNode messages = objectMapper.createArrayNode();

        ObjectNode sysMsg = objectMapper.createObjectNode();
        sysMsg.put("role", "system");
        sysMsg.put("content", systemInstruction);
        messages.add(sysMsg);

        for (HistorialIA h : historialPrevio) {
            if (h.getPromptUsuario() != null && !h.getPromptUsuario().trim().isEmpty()) {
                ObjectNode userMsg = objectMapper.createObjectNode();
                userMsg.put("role", "user");
                userMsg.put("content", h.getPromptUsuario());
                messages.add(userMsg);
            }
            String respPrev = extraerRespuestaHistorial(h);
            if (respPrev != null && !respPrev.trim().isEmpty()) {
                ObjectNode assistantMsg = objectMapper.createObjectNode();
                assistantMsg.put("role", "assistant");
                assistantMsg.put("content", respPrev);
                messages.add(assistantMsg);
            }
        }

        ObjectNode currentMsg = objectMapper.createObjectNode();
        currentMsg.put("role", "user");
        currentMsg.put("content", mensajeActual);
        messages.add(currentMsg);

        return messages;
    }

    private String construirSystemInstruction() {
        StringBuilder catalogoBuilder = new StringBuilder();
        try {
            List<Diagnostico> diagnosticos = diagnosticoRepository.findAll();
            for (Diagnostico d : diagnosticos) {
                Long id = extraerIdDiagnostico(d);
                catalogoBuilder.append(String.format(
                    "- ID %d | Título: %s | Causa: %s | Solución: %s | Presupuesto Base: $%s CLP%n",
                    id,
                    d.getTitulo(),
                    d.getCausaProbable(),
                    d.getSolucionRecomendada(),
                    d.getPresupuestoEstimadoClp()
                ));
            }
        } catch (Exception e) {
            catalogoBuilder.append("- Catálogo no disponible temporalmente, usa precios de mercado técnico en Chile (CLP).\n");
        }

        return baseSystemPrompt + "\n\n"
            + "CATÁLOGO DE SERVICIOS Y PRECIOS BASE EN MYSQL DE PC DOCTOR (úsalo como referencia real para cotizaciones):\n"
            + catalogoBuilder + "\n"
            + "REGLAS DE CONVERSACIÓN Y RAZONAMIENTO:\n"
            + "1. Lee TODO el historial de mensajes antes de responder. Mantén siempre un tono cercano, transparente y didáctico: explica al usuario qué ocurre técnicamente y cómo se soluciona.\n"
            + "2. MODO ASESORÍA, COMPARACIÓN Y CONSULTAS TÉCNICAS: Si el usuario pide comparaciones de componentes (ej. Ryzen vs Intel, SSD NVMe vs SATA, DDR4 vs DDR5), consejos de compra/upgrade, dudas de compatibilidad o quiere entender cómo funciona algo sin reportar una falla para reparar, respóndele de forma completa, objetiva y educativa en 'respuestaIa', dejando 'diagnosticoListo': false y 'diagnosticosPosibles': []. No le fuerces un presupuesto de reparación si solo está pidiendo orientación.\n"
            + "3. MODO DIAGNÓSTICO Y REPARACIÓN: Si reporta una falla o problema en su equipo:\n"
            + "   - Si descarta una causa (ej. 'la fuente es nueva'), descártala y recalcula otras hipótesis.\n"
            + "   - Si falta contexto clave, haz 1 o 2 preguntas técnicas naturales ('diagnosticoListo': false, 'diagnosticosPosibles': []).\n"
            + "   - Cuando tengas contexto suficiente o pida presupuesto/revisión, explica primero en 'respuestaIa' la causa raíz y el procedimiento real de reparación de forma transparente, activa 'diagnosticoListo': true y entrega hasta 3 hipótesis con presupuestos realistas en CLP.\n"
            + "4. CONTROL DE DOMINIO: Solo establece 'fueraDeDominio': true si la consulta es completamente ajena a computación, hardware, software o tecnología (ej. recetas de cocina, deportes, política).\n"
            + "5. Responde SIEMPRE exclusivamente con un objeto JSON válido con esta estructura exacta:\n"
            + "{\n"
            + "  \"respuestaIa\": \"Tu respuesta conversacional clara, cercana y educativa en español (puedes usar **negritas** y saltos de línea)\",\n"
            + "  \"fueraDeDominio\": false,\n"
            + "  \"diagnosticoListo\": false,\n"
            + "  \"categoria\": \"Categoría técnica detectada\",\n"
            + "  \"idDiagnosticoPrincipal\": 1,\n"
            + "  \"sugerenciasRapidas\": [\"Opción corta 1\", \"Opción corta 2\", \"Opción corta 3\"],\n"
            + "  \"diagnosticosPosibles\": [\n"
            + "    {\n"
            + "      \"posicion\": 1,\n"
            + "      \"titulo\": \"Nombre del diagnóstico\",\n"
            + "      \"causaProbable\": \"Explicación técnica breve\",\n"
            + "      \"solucionPrincipal\": \"Solución recomendada\",\n"
            + "      \"solucionAlternativa\": \"Alternativa técnica\",\n"
            + "      \"confianza\": \"85%\",\n"
            + "      \"nivelConfianza\": \"ALTA PROBABILIDAD\",\n"
            + "      \"manoDeObraClp\": 20000,\n"
            + "      \"repuestoEstimadoClp\": 15000,\n"
            + "      \"presupuestoTotalClp\": 35000\n"
            + "    }\n"
            + "  ]\n"
            + "}";
    }

    private String extraerMensajeError(String responseBody) {
        try {
            JsonNode errNode = objectMapper.readTree(responseBody).path("error").path("message");
            if (!errNode.isMissingNode() && !errNode.asText().isBlank()) {
                return errNode.asText();
            }
        } catch (Exception ignored) {
        }
        return "Verifica tu API Key de Groq y conexión a internet.";
    }

    private Long extraerIdDiagnostico(Diagnostico d) {
        for (String getter : List.of("getIdDiagnostico", "getId")) {
            try {
                Method m = d.getClass().getMethod(getter);
                Object val = m.invoke(d);
                if (val instanceof Number) {
                    return ((Number) val).longValue();
                }
            } catch (Exception ignored) {
            }
        }
        return 1L;
    }

    private String extraerRespuestaHistorial(HistorialIA h) {
        for (String getter : List.of("getRespuestaIa", "getRespuesta", "getRespuestaModelo")) {
            try {
                Method m = h.getClass().getMethod(getter);
                Object val = m.invoke(h);
                if (val != null) {
                    return val.toString();
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private String limpiarBloquesMarkdown(String text) {
        if (text == null) return "{}";
        String limpio = text.trim();
        if (limpio.startsWith("```json")) {
            limpio = limpio.substring(7);
        } else if (limpio.startsWith("```")) {
            limpio = limpio.substring(3);
        }
        if (limpio.endsWith("```")) {
            limpio = limpio.substring(0, limpio.length() - 3);
        }
        return limpio.trim();
    }

    private void guardarInteraccion(String sessionId, String prompt, String respuestaIa, String estado, int iteracion) {
        try {
            HistorialIA registro = new HistorialIA(sessionId, prompt, respuestaIa, estado, iteracion);
            historialIARepository.save(registro);
        } catch (Exception e) {
            System.err.println("Error guardando historial de chat: " + e.getMessage());
        }
    }

    private Map<String, Object> crearRespuestaError(String mensaje, boolean esAdvertencia) {
        Map<String, Object> map = new HashMap<>();
        map.put("exito", false);
        map.put("fueraDeDominio", esAdvertencia);
        map.put("diagnosticoListo", false);
        map.put("respuestaIa", mensaje);
        map.put("sugerenciasRapidas", List.of());
        return map;
    }
}