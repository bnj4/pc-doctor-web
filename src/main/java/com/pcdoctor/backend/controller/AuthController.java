package com.pcdoctor.backend.controller;

import com.pcdoctor.backend.model.UsuarioSistema;
import com.pcdoctor.backend.repository.UsuarioSistemaRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final UsuarioSistemaRepository usuarioRepository;

    private static final Map<String, SesionUsuario> SESIONES_ACTIVAS = new ConcurrentHashMap<>();

    public record SesionUsuario(String rut, String nombre, String rol) {}

    public AuthController(UsuarioSistemaRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Bean
    public CommandLineRunner inicializarAdminPorDefecto() {
        return args -> {
            try {
                String rutAdminDefault = "11.111.111-1";
                if (!usuarioRepository.existsByRut(rutAdminDefault)) {
                    UsuarioSistema admin = new UsuarioSistema();
                    admin.setRut(rutAdminDefault);
                    admin.setNombre("Administrador Principal");
                    admin.setCorreo("admin@pcdoctor.cl");
                    admin.setPasswordHash(hashSHA256("admin123"));
                    admin.setRol("ADMINISTRADOR");
                    admin.setActivo(true);
                    usuarioRepository.save(admin);
                    System.out.println("✅ Administrador inicial creado en MySQL (RUT: 11.111.111-1)");
                }
            } catch (Exception e) {
                System.err.println("⚠️ Aviso al verificar admin inicial: " + e.getMessage());
            }
        };
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credenciales) {
        String rut = normalizarRut(credenciales.getOrDefault("rut", ""));
        String clave = credenciales.getOrDefault("password", "").trim();

        if (!validarRutModulo11(rut)) {
            return ResponseEntity.badRequest().body(Map.of(
                "exito", false,
                "mensaje", "El RUT ingresado no cumple con el dígito verificador (Módulo 11)."
            ));
        }

        Optional<UsuarioSistema> userOpt = usuarioRepository.findByRut(rut);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "exito", false,
                "mensaje", "RUT no registrado en el personal de PC Doctor."
            ));
        }

        UsuarioSistema usuario = userOpt.get();
        if (!Boolean.TRUE.equals(usuario.getActivo())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "exito", false,
                "mensaje", "Cuenta desactivada. Contacte al Administrador."
            ));
        }

        if (!usuario.getPasswordHash().equals(hashSHA256(clave))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "exito", false,
                "mensaje", "Contraseña incorrecta."
            ));
        }

        String token = usuario.getRol() + "-" + UUID.randomUUID();
        SESIONES_ACTIVAS.put(token, new SesionUsuario(usuario.getRut(), usuario.getNombre(), usuario.getRol()));

        return ResponseEntity.ok(Map.of(
            "exito", true,
            "token", token,
            "rut", usuario.getRut(),
            "nombre", usuario.getNombre(),
            "rol", usuario.getRol()
        ));
    }

    @PostMapping("/tecnicos")
    public ResponseEntity<?> registrarTecnico(@RequestHeader(value = "X-Auth-Token", required = false) String token,
                                              @RequestBody Map<String, String> payload) {
        String rolActual = obtenerRolPorToken(token);
        if (!"ADMINISTRADOR".equals(rolActual)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "error", "Acceso denegado (Rol actual: " + rolActual + "). Solo ADMINISTRADOR puede registrar técnicos."
            ));
        }

        String rut = normalizarRut(payload.getOrDefault("rut", ""));
        String nombre = payload.getOrDefault("nombre", "").trim();
        String correo = payload.getOrDefault("correo", "").trim();
        String password = payload.getOrDefault("password", "").trim();

        if (!validarRutModulo11(rut)) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "El RUT ingresado (" + rut + ") no es válido según el algoritmo Módulo 11."
            ));
        }

        if (nombre.isEmpty() || correo.isEmpty() || password.length() < 4) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Todos los campos son obligatorios y la clave debe tener al menos 4 caracteres."
            ));
        }

        if (usuarioRepository.existsByRut(rut)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "Ya existe un usuario registrado con el RUT " + rut + "."
            ));
        }

        UsuarioSistema tecnico = new UsuarioSistema();
        tecnico.setRut(rut);
        tecnico.setNombre(nombre);
        tecnico.setCorreo(correo);
        tecnico.setPasswordHash(hashSHA256(password));
        tecnico.setRol("TECNICO");
        tecnico.setActivo(true);

        UsuarioSistema guardado = usuarioRepository.save(tecnico);
        return ResponseEntity.ok(Map.of(
            "exito", true,
            "idUsuario", guardado.getIdUsuario(),
            "rut", guardado.getRut(),
            "nombre", guardado.getNombre(),
            "rol", guardado.getRol()
        ));
    }

    @GetMapping("/tecnicos")
    public ResponseEntity<?> listarTecnicos(@RequestHeader(value = "X-Auth-Token", required = false) String token) {
        String rolActual = obtenerRolPorToken(token);
        if (!"ADMINISTRADOR".equals(rolActual)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "error", "Acceso denegado (Rol actual: " + rolActual + "). Solo ADMINISTRADOR puede ver el listado de personal."
            ));
        }

        var lista = new ArrayList<>();
        for (UsuarioSistema u : usuarioRepository.findAll()) {
            var item = new LinkedHashMap<>();
            item.put("idUsuario", u.getIdUsuario());
            item.put("rut", u.getRut());
            item.put("nombre", u.getNombre());
            item.put("correo", u.getCorreo());
            item.put("rol", u.getRol());
            item.put("activo", u.getActivo());
            lista.add(item);
        }

        return ResponseEntity.ok(lista);
    }

    @DeleteMapping("/tecnicos/{id}")
    public ResponseEntity<?> eliminarTecnico(@RequestHeader(value = "X-Auth-Token", required = false) String token,
                                             @PathVariable Long id) {
        String rolActual = obtenerRolPorToken(token);
        if (!"ADMINISTRADOR".equals(rolActual)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "error", "Acceso denegado. Solo ADMINISTRADOR puede eliminar técnicos."
            ));
        }

        Optional<UsuarioSistema> userOpt = usuarioRepository.findById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "El usuario seleccionado no existe en la base de datos."
            ));
        }

        UsuarioSistema usuario = userOpt.get();
        if ("ADMINISTRADOR".equals(usuario.getRol())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "error", "No está permitido eliminar una cuenta con rol ADMINISTRADOR."
            ));
        }

        // Cierra cualquier sesión activa en memoria que pertenezca al técnico eliminado
        SESIONES_ACTIVAS.entrySet().removeIf(entry -> entry.getValue().rut().equals(usuario.getRut()));

        usuarioRepository.delete(usuario);
        return ResponseEntity.ok(Map.of(
            "exito", true,
            "mensaje", "Técnico " + usuario.getNombre() + " eliminado correctamente."
        ));
    }

    public static String obtenerRolPorToken(String token) {
        if (token == null || token.isBlank()) return "USUARIO";
        SesionUsuario sesion = SESIONES_ACTIVAS.get(token.trim());
        return sesion != null ? sesion.rol() : "USUARIO";
    }

    public static SesionUsuario obtenerSesion(String token) {
        if (token == null || token.isBlank()) return null;
        return SESIONES_ACTIVAS.get(token.trim());
    }

    public static boolean validarRutModulo11(String rut) {
        if (rut == null) return false;
        String limpio = rut.replace(".", "").replace("-", "").trim().toUpperCase();
        if (limpio.length() < 8 || limpio.length() > 9) return false;

        String cuerpo = limpio.substring(0, limpio.length() - 1);
        char dvIngresado = limpio.charAt(limpio.length() - 1);
        if (!cuerpo.matches("\\d+")) return false;

        int suma = 0;
        int multiplo = 2;
        for (int i = cuerpo.length() - 1; i >= 0; i--) {
            suma += Character.getNumericValue(cuerpo.charAt(i)) * multiplo;
            multiplo = (multiplo < 7) ? multiplo + 1 : 2;
        }

        int resto = 11 - (suma % 11);
        char dvEsperado = (resto == 11) ? '0' : (resto == 10) ? 'K' : Character.forDigit(resto, 10);
        return dvIngresado == dvEsperado;
    }

    public static String normalizarRut(String rut) {
        if (rut == null) return "";
        String limpio = rut.replace(".", "").trim().toUpperCase();
        if (!limpio.contains("-") && limpio.length() >= 8) {
            limpio = limpio.substring(0, limpio.length() - 1) + "-" + limpio.substring(limpio.length() - 1);
        }
        String[] partes = limpio.split("-");
        if (partes.length == 2 && partes[0].matches("\\d+")) {
            String cuerpo = partes[0];
            StringBuilder sb = new StringBuilder();
            int cont = 0;
            for (int i = cuerpo.length() - 1; i >= 0; i--) {
                sb.insert(0, cuerpo.charAt(i));
                cont++;
                if (cont == 3 && i > 0) {
                    sb.insert(0, '.');
                    cont = 0;
                }
            }
            return sb + "-" + partes[1];
        }
        return limpio;
    }

    private String hashSHA256(String texto) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encoded = digest.digest(texto.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : encoded) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("Error encriptando contraseña", e);
        }
    }
}