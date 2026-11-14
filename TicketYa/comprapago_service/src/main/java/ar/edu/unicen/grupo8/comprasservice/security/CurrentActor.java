package ar.edu.unicen.grupo8.comprasservice.security;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
/** Identity comes exclusively from the verified JWT, never from legacy IDs or headers. */
public final class CurrentActor {
 private CurrentActor() {}
 public static org.springframework.security.oauth2.jwt.Jwt jwt(){var a=SecurityContextHolder.getContext().getAuthentication();return a instanceof JwtAuthenticationToken t?t.getToken():null;}
 public static boolean technical(){var j=jwt();return j!=null&&"service".equals(j.getClaimAsString("token_use"));}
 public static boolean admin(){var a=SecurityContextHolder.getContext().getAuthentication();return jwt()!=null&&!technical()&&a.getAuthorities().stream().anyMatch(x->x.getAuthority().equals("ROLE_ADMINISTRADOR"));}
 public static Long humanId(){var j=jwt();if(j==null||technical())return null;try{return Long.valueOf(j.getSubject());}catch(NumberFormatException e){throw new AccessDeniedException("Invalid human identity");}}
 public static void self(Long declared){var id=humanId();if(id==null||!id.equals(declared))throw new AccessDeniedException("Identity does not match authenticated user");}
 public static boolean visible(Long owner){return admin()||(owner!=null&&owner.equals(humanId()));}
 public static void own(Long owner){if(!visible(owner))throw missing();}
 public static ResponseStatusException missing(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"Resource not found");}
}
