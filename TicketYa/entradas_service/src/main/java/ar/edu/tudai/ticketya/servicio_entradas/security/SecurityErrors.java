package ar.edu.tudai.ticketya.servicio_entradas.security;
import jakarta.servlet.http.*;
import java.io.IOException;
/** Fixed error bodies: no claims, token contents or internal exception details. */
final class SecurityErrors {
 private SecurityErrors(){}
 static void unauthorized(HttpServletRequest request,HttpServletResponse response,org.springframework.security.core.AuthenticationException error)throws IOException, jakarta.servlet.ServletException {
  new org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint().commence(request,response,error);write(response,401,"Unauthorized");
 }
 static void forbidden(HttpServletRequest request,HttpServletResponse response,org.springframework.security.access.AccessDeniedException error)throws IOException,jakarta.servlet.ServletException {
  new org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler().handle(request,response,error);write(response,403,"Forbidden");
 }
 private static void write(HttpServletResponse response,int status,String message)throws IOException{response.setContentType("application/json");response.setCharacterEncoding("UTF-8");response.getWriter().write("{\"status\":"+status+",\"message\":\""+message+"\"}");}
}
