package grupo22.eventocatalogo_service.recommendations;
import grupo22.eventocatalogo_service.domain.*;
import grupo22.eventocatalogo_service.repository.*;
import grupo22.eventocatalogo_service.SecurityTestKeys;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.junit.jupiter.api.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest
class RecommendationsTest {
 @Autowired RecommendationService service;
 @Autowired org.springframework.web.context.WebApplicationContext context;
 MockMvc mvc;
 @Autowired EventRepository events;
 @Autowired ParticipantRepository participants;
 @Autowired VenueRepository venues;
 @MockitoBean PreferenceStore store;
 @MockitoBean PurchaseHistoryClient history;
 Participant artist;Venue venue;
 @BeforeEach void fixture(){mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context).apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();events.deleteAll();artist=participants.save(new Participant("Artist "+UUID.randomUUID(),"Banda"));venue=new Venue("Venue", "Address", BigDecimal.ONE,BigDecimal.ONE);venue.setCity("Tandil");venue=venues.save(venue);when(store.get(anyString())).thenReturn(PreferenceStore.empty());when(history.events(anyLong())).thenReturn(Set.of());}
 Event event(String type,EventStatus status,int days,boolean withArtist){Event e=new Event("Event "+UUID.randomUUID(),null,type,1L,venue.getVenueId(),OffsetDateTime.now(ZoneOffset.UTC).plusDays(days));e.setStatus(status);if(withArtist)e.addParticipant(artist);return events.save(e);}
 String token(long id,String role)throws Exception{return "Bearer "+SecurityTestKeys.token("eventos","user",String.valueOf(id),role,null,300);}
 @Test void preferencesNormalizeDeduplicateAndBindToJwt()throws Exception{
  when(store.put(eq("77"),any())).thenAnswer(i->i.getArgument(1));
  mvc.perform(put("/api/v1/me/preferences").header("Authorization",token(77,"CLIENTE")).contentType("application/json").content("{\"favoriteTypes\":[\" RECITAL \",\"recital\"],\"favoriteParticipantIds\":["+artist.getParticipantId()+","+artist.getParticipantId()+"],\"preferredCity\":\" Tandil \"}"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.favoriteTypes.length()").value(1)).andExpect(jsonPath("$.favoriteTypes[0]").value("recital")).andExpect(jsonPath("$.preferredCity").value("tandil"));
  verify(store).put(eq("77"),any());verify(store,never()).put(eq("88"),any());
  mvc.perform(get("/api/v1/me/preferences?userId=77").header("Authorization",token(88,"CLIENTE"))).andExpect(status().isOk());verify(store).get("88");
 }
 @Test void unauthorizedRolesCannotUsePreferencesOrRecommendations()throws Exception{
  mvc.perform(get("/api/v1/me/preferences")).andExpect(status().isUnauthorized());
  for(String role:List.of("ADMINISTRADOR","ORGANIZADOR","PERSONAL_ACCESO"))mvc.perform(get("/api/v1/me/recommendations").header("Authorization",token(77,role))).andExpect(status().isForbidden());
 }
 @Test void invalidPreferencesDoNotWriteMongo()throws Exception{
  for(String body:List.of("{\"favoriteTypes\":[\" \"],\"favoriteParticipantIds\":[]}","{\"favoriteTypes\":[],\"favoriteParticipantIds\":[-1]}","{\"favoriteTypes\":[],\"favoriteParticipantIds\":[99999999]}"))mvc.perform(put("/api/v1/me/preferences").header("Authorization",token(77,"CLIENTE")).contentType("application/json").content(body)).andExpect(status().isBadRequest());
  verify(store,never()).put(anyString(),any());
 }
 @Test void onlyFuturePublishedNotAcquiredEventsAreRecommended()throws Exception{
  Event bought=event("Recital",EventStatus.PUBLICADO,4,true),allowed=event("Recital",EventStatus.PUBLICADO,5,true);
  for(EventStatus state:EventStatus.values())if(state!=EventStatus.PUBLICADO)event("Recital",state,1,true);event("Recital",EventStatus.PUBLICADO,-1,true);
  when(history.events(77L)).thenReturn(Set.of(bought.getEventId()));
  mvc.perform(get("/api/v1/me/recommendations").header("Authorization",token(77,"CLIENTE"))).andExpect(status().isOk()).andExpect(jsonPath("$.historyAvailable").value(true)).andExpect(jsonPath("$.events.length()").value(1)).andExpect(jsonPath("$.events[0].id").value(allowed.getEventId()));
 }
 @Test void noPreferencesAndNoHistoryReturnsGeneralUpcomingEvents()throws Exception{
  Event later=event("Teatro",EventStatus.PUBLICADO,5,false),earlier=event("Recital",EventStatus.PUBLICADO,1,false);
  mvc.perform(get("/api/v1/me/recommendations").header("Authorization",token(77,"CLIENTE"))).andExpect(status().isOk()).andExpect(jsonPath("$.mode").value("GENERAL")).andExpect(jsonPath("$.warning").doesNotExist()).andExpect(jsonPath("$.events[0].id").value(earlier.getEventId()));
 }
 @Test void purchaseOutageIsExplicitAndPreferencesStillApply()throws Exception{
  Event city=event("Teatro",EventStatus.PUBLICADO,1,false),favorite=event("Recital",EventStatus.PUBLICADO,5,false);
  when(store.get("77")).thenReturn(new PreferenceStore.Preferences(List.of("recital"),List.of(),null,Instant.now()));when(history.events(77L)).thenThrow(new org.springframework.web.client.RestClientException("untrusted remote details"));
  mvc.perform(get("/api/v1/me/recommendations").header("Authorization",token(77,"CLIENTE"))).andExpect(status().isOk()).andExpect(jsonPath("$.historyAvailable").value(false)).andExpect(jsonPath("$.warning").value("HISTORY_UNAVAILABLE")).andExpect(jsonPath("$.events[0].id").value(favorite.getEventId()));
 }
 @Test void mongoOutageIs503ButPublicCatalogueStillResponds()throws Exception{
  when(store.get("77")).thenThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"Preferences unavailable"));
  mvc.perform(get("/api/v1/me/preferences").header("Authorization",token(77,"CLIENTE"))).andExpect(status().isServiceUnavailable());
  mvc.perform(get("/api/v1/events/search")).andExpect(status().isOk());
 }
 @Test void rankingUsesStrictPriorityAndStableDateThenId(){
  var date=OffsetDateTime.parse("2030-01-01T00:00:00Z");var p=new PreferenceStore.Preferences(List.of("recital"),List.of(10L),"tandil",null);
  var artist=new RecommendationService.Candidate(5L,"artist","other",date.plusDays(5),Set.of(10L),"other");
  var type=new RecommendationService.Candidate(4L,"type","recital",date.plusDays(4),Set.of(),"other");
  var city=new RecommendationService.Candidate(3L,"city","other",date.plusDays(3),Set.of(),"tandil");
  var inferred=new RecommendationService.Candidate(2L,"history","old",date.plusDays(2),Set.of(),"other");
  var none=new RecommendationService.Candidate(1L,"none","other",date,Set.of(),"other");
  assertEquals(List.of(5L,4L,3L,2L,1L),RecommendationService.rank(List.of(none,type,artist,inferred,city),p,Set.of("old"),Set.of(),Set.of()).stream().map(RecommendationService.Candidate::id).toList());
  var values=new ArrayList<RecommendationService.Candidate>();for(long id=20;id>=1;id--)values.add(new RecommendationService.Candidate(id,"x","x",date,Set.of(),null));
  assertEquals(java.util.stream.LongStream.rangeClosed(2,11).boxed().toList(),RecommendationService.rank(values,PreferenceStore.empty(),Set.of(),Set.of(),Set.of(1L)).stream().map(RecommendationService.Candidate::id).toList());
 }
}
