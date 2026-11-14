package grupo22.eventocatalogo_service.security;
import jakarta.servlet.*;import jakarta.servlet.http.*;import java.io.IOException;import java.util.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
public class TrustedActorHeaders extends OncePerRequestFilter {
 protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{
  var values=new HashMap<String,String>();var auth=SecurityContextHolder.getContext().getAuthentication();
  if(auth instanceof JwtAuthenticationToken t&&"user".equals(t.getToken().getClaimAsString("token_use"))&&t.getToken().getClaimAsStringList("roles").contains("ADMINISTRADOR")){
   values.put("x-user-role","ADMIN");values.put("x-admin-id",t.getName());
  }
  var blocked=Set.of("x-user-role","x-admin-id","x-user-id","x-delegated-authorization");
  chain.doFilter(new HttpServletRequestWrapper(req){
   public String getHeader(String n){return blocked.contains(n.toLowerCase(Locale.ROOT))?values.get(n.toLowerCase(Locale.ROOT)):super.getHeader(n);}
   public Enumeration<String> getHeaders(String n){String v=getHeader(n);return blocked.contains(n.toLowerCase(Locale.ROOT))?Collections.enumeration(v==null?List.of():List.of(v)):super.getHeaders(n);}
   public Enumeration<String> getHeaderNames(){var names=Collections.list(super.getHeaderNames());names.removeIf(n->blocked.contains(n.toLowerCase(Locale.ROOT)));names.addAll(values.keySet());return Collections.enumeration(names);}
  },res);
 }
}
