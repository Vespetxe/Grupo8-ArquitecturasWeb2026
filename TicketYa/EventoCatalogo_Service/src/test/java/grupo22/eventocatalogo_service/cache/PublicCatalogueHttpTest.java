package grupo22.eventocatalogo_service.cache;

import grupo22.eventocatalogo_service.SecurityTestKeys;
import grupo22.eventocatalogo_service.domain.*;
import grupo22.eventocatalogo_service.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import java.net.*;
import java.net.http.*;
import java.time.OffsetDateTime;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class PublicCatalogueHttpTest {
 @LocalServerPort int port;
 @Autowired ParticipantRepository participants;
 @Autowired EventRepository events;
 @Autowired PublicCatalogueCache cache;
 HttpResponse<String> call(String route,String token,String method)throws Exception{
  var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+route));
  if(token!=null)b.header("Authorization","Bearer "+token);
  if(method.equals("GET"))b.GET();else b.header("Content-Type","application/json").method(method,HttpRequest.BodyPublishers.ofString("{\"name\":\"forbidden\",\"role\":\"Banda\"}"));
  return HttpClient.newHttpClient().send(b.build(),HttpResponse.BodyHandlers.ofString());
 }
 @Test void publicSnapshotsNeverGrantPrivateOrAdministrativeAccess()throws Exception{
  var p=participants.save(new Participant("Public cache artist","Banda"));cache.invalidateAfterCommit();
  String client=SecurityTestKeys.token("eventos","user","71","CLIENTE",null,300);
  String other=SecurityTestKeys.token("eventos","user","72","CLIENTE",null,300);
  var route="/api/v1/participants/"+p.getParticipantId();
  var anonymous=call(route,null,"GET");assertEquals(200,anonymous.statusCode());
  assertEquals(anonymous.body(),call(route,client,"GET").body());assertEquals(anonymous.body(),call(route,other,"GET").body());
  assertEquals(403,call(route,client,"PUT").statusCode());assertEquals("Public cache artist",participants.findById(p.getParticipantId()).orElseThrow().getName());
  var draft=new Event("Private draft","Private", "Recital",71L,999L,OffsetDateTime.now().plusDays(8));draft.setIdentityOwnerId(71L);draft=events.save(draft);
  String owner=SecurityTestKeys.token("eventos","user","71","ORGANIZADOR",null,300);
  String eventRoute="/api/v1/events/"+draft.getEventId();
  assertEquals(200,call(eventRoute,owner,"GET").statusCode());assertEquals(404,call(eventRoute,other,"GET").statusCode());assertEquals(401,call(eventRoute,null,"GET").statusCode());
 }
 @Test void missingParticipantRetains404Contract()throws Exception{
  assertEquals(404,call("/api/v1/participants/999999999",null,"GET").statusCode());
 }
}
