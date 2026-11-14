package grupo22.eventocatalogo_service;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import grupo22.eventocatalogo_service.repository.VenueRepository;
import grupo22.eventocatalogo_service.domain.Venue;
import java.net.*;import java.net.http.*;import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class HistoricalOwnershipTest {
 @Autowired VenueRepository venues;@LocalServerPort int port;
 @Test void matchingSubjectDoesNotClaimUnownedHistoricalVenue()throws Exception{
  var venue=venues.save(new Venue("Historical ownership fixture","Address",BigDecimal.ONE,BigDecimal.ONE));
  var token=SecurityTestKeys.token("eventos","user",venue.getVenueId().toString(),"ORGANIZADOR",null,300);
  var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/venues/"+venue.getVenueId()+"/sectors"))
   .header("Authorization","Bearer "+token).header("Content-Type","application/json")
   .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"Forbidden\",\"type\":\"NO_NUMERADO\",\"capacity\":2}")).build();
  assertEquals(404,HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.discarding()).statusCode());
  assertNull(venues.findById(venue.getVenueId()).orElseThrow().getIdentityOwnerId());
 }
}
