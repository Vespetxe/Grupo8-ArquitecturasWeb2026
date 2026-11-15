package grupo22.eventocatalogo_service.recommendations;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
@RestController @RequestMapping("/api/v1/me")
public class PreferenceController {
 private final RecommendationService service;
 public PreferenceController(RecommendationService service){this.service=service;}
 @GetMapping("/preferences") public ResponseEntity<PreferenceStore.Preferences> get(){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.get());}
 @PutMapping("/preferences") public ResponseEntity<PreferenceStore.Preferences> put(@RequestBody RecommendationService.Input body){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.put(body));}
 @GetMapping("/recommendations") public ResponseEntity<RecommendationService.Response> recommendations(){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.recommend());}
}
