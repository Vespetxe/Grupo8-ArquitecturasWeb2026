package grupo22.eventocatalogo_service.recommendations;
import grupo22.eventocatalogo_service.domain.*;
import grupo22.eventocatalogo_service.repository.*;
import grupo22.eventocatalogo_service.security.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientException;
import java.time.*;
import java.util.*;
@Service
public class RecommendationService {
 private final PreferenceStore preferences;
 private final ParticipantRepository participants;
 private final EventRepository events;
 private final VenueRepository venues;
 private final PurchaseHistoryClient history;
 private final TransactionTemplate read;
 public RecommendationService(PreferenceStore preferences,ParticipantRepository participants,EventRepository events,VenueRepository venues,PurchaseHistoryClient history,PlatformTransactionManager manager){this.preferences=preferences;this.participants=participants;this.events=events;this.venues=venues;this.history=history;read=new TransactionTemplate(manager);read.setReadOnly(true);}
 public record Input(List<String> favoriteTypes,List<Long> favoriteParticipantIds,String preferredCity){}
 public record Candidate(Long id,String name,String type,OffsetDateTime date,Set<Long> participants,String city){}
 public record Response(boolean historyAvailable,String mode,String warning,List<Candidate> events){}
 private Long owner(){var id=CurrentActor.humanId();if(id==null)throw new org.springframework.security.access.AccessDeniedException("Client identity required");return id;}
 private org.springframework.web.server.ResponseStatusException invalid(String message){return new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,message);}
 static String normalize(String value){return value==null?null:value.trim().toLowerCase(Locale.ROOT);}
 public PreferenceStore.Preferences get(){return preferences.get(owner().toString());}
 public PreferenceStore.Preferences put(Input input){
  Long owner=owner();
  if(input==null||input.favoriteTypes()==null||input.favoriteParticipantIds()==null||input.favoriteTypes().size()>20||input.favoriteParticipantIds().size()>50)throw invalid("Invalid preferences");
  var types=new TreeSet<String>();for(String type:input.favoriteTypes()){if(type==null||type.isBlank()||type.trim().length()>50)throw invalid("Invalid favorite type");types.add(normalize(type));}
  var artists=new TreeSet<Long>();for(Long id:input.favoriteParticipantIds()){if(id==null||id<=0)throw invalid("Invalid participant");artists.add(id);}
  if(participants.countByParticipantIdIn(artists)!=artists.size())throw invalid("Unknown participant");
  String city=normalize(input.preferredCity());if(city!=null&&city.length()>150)throw invalid("Invalid city");if(city!=null&&city.isEmpty())city=null;
  return preferences.put(owner.toString(),new PreferenceStore.Preferences(List.copyOf(types),List.copyOf(artists),city,Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS)));
 }
 public Response recommend(){
  Long owner=owner();var p=preferences.get(owner.toString());Set<Long> acquired;boolean available=true;
  try{acquired=history.events(owner);}catch(RestClientException ex){acquired=Set.of();available=false;org.slf4j.LoggerFactory.getLogger(getClass()).atWarn().addKeyValue("errorCategory","HISTORY_UNAVAILABLE").log("Recommendations without purchase history");}
  final Set<Long> bought=acquired;
  var data=read.execute(status->{var candidates=events.recommendationCandidates(EventStatus.PUBLICADO,OffsetDateTime.now(ZoneOffset.UTC));var past=bought.isEmpty()?List.<Event>of():events.findByEventIdIn(bought);var ids=new HashSet<Long>();candidates.forEach(e->ids.add(e.getVenueId()));var cities=new HashMap<Long,String>();venues.findAllById(ids).forEach(v->cities.put(v.getVenueId(),normalize(v.getCity())));
   return new Data(candidates.stream().map(e->new Candidate(e.getEventId(),e.getName(),normalize(e.getEventType()),e.getStartDateTime(),e.getParticipants().stream().map(Participant::getParticipantId).collect(java.util.stream.Collectors.toSet()),cities.get(e.getVenueId()))).toList(),past.stream().map(e->normalize(e.getEventType())).collect(java.util.stream.Collectors.toSet()),past.stream().flatMap(e->e.getParticipants().stream()).map(Participant::getParticipantId).collect(java.util.stream.Collectors.toSet()));});
  boolean explicit=!p.favoriteTypes().isEmpty()||!p.favoriteParticipantIds().isEmpty()||p.preferredCity()!=null;
  boolean signals=explicit||!bought.isEmpty();
  return new Response(available,signals?(available?"PERSONALIZED":"PREFERENCES_ONLY"):"GENERAL",available?null:"HISTORY_UNAVAILABLE",rank(data.candidates(),p,data.types(),data.artists(),bought));
 }
 private record Data(List<Candidate> candidates,Set<String> types,Set<Long> artists){}
 static List<Candidate> rank(List<Candidate> candidates,PreferenceStore.Preferences preferences,Set<String> historyTypes,Set<Long> historyArtists,Set<Long> acquired){
  java.util.function.ToIntFunction<Candidate> favoriteArtists=c->(int)c.participants().stream().filter(preferences.favoriteParticipantIds()::contains).count();
  java.util.function.ToIntFunction<Candidate> favoriteType=c->preferences.favoriteTypes().contains(c.type())?1:0;
  java.util.function.ToIntFunction<Candidate> city=c->preferences.preferredCity()!=null&&preferences.preferredCity().equals(c.city())?1:0;
  java.util.function.ToIntFunction<Candidate> inferred=c->(historyTypes.contains(c.type())?1:0)+(int)c.participants().stream().filter(historyArtists::contains).count();
  var order=Comparator.comparingInt(favoriteArtists).reversed().thenComparing(Comparator.comparingInt(favoriteType).reversed()).thenComparing(Comparator.comparingInt(city).reversed()).thenComparing(Comparator.comparingInt(inferred).reversed()).thenComparing(c->c.date().toInstant()).thenComparing(Candidate::id);
  return candidates.stream().filter(c->!acquired.contains(c.id())).sorted(order).limit(10).toList();
 }
}
