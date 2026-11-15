package grupo22.eventocatalogo_service.notifications;
import grupo22.eventocatalogo_service.security.ServiceTokens;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.web.client.RestClient;
import org.junit.jupiter.api.*;
import com.sun.net.httpserver.HttpServer;
import com.mongodb.client.*;
import org.bson.Document;
import org.bson.conversions.Bson;
import java.net.*;
import java.time.Instant;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class EventNoticeDispatcherTest {
 EventNoticeStore store;MongoTemplate mongo;HttpServer server;int status;List<String> bodies;
 @BeforeEach void setup()throws Exception{store=mock(EventNoticeStore.class);mongo=mock(MongoTemplate.class);bodies=new ArrayList<>();status=503;server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/internal/notifications/events",x->{bodies.add(new String(x.getRequestBody().readAllBytes()));byte[] data="{\"accepted\":true}".getBytes();x.getResponseHeaders().set("Content-Type","application/json");x.sendResponseHeaders(status,data.length);x.getResponseBody().write(data);x.close();});server.start();}
 @AfterEach void stop(){server.stop(0);}
 EventNoticeDispatcher dispatcher(){String url="http://127.0.0.1:"+server.getAddress().getPort();ServiceTokens tokens=mock(ServiceTokens.class);when(tokens.client("compras",url)).thenReturn(RestClient.builder().baseUrl(url).requestFactory(new grupo22.eventocatalogo_service.timing.TimedTransport(new org.springframework.mock.env.MockEnvironment()).factory(false)).build());return new EventNoticeDispatcher(store,mongo,tokens,url);}
 @Test void failedDeliveryRemainsPendingAndRetriesSameIdentity(){when(store.pending()).thenReturn(List.of(new EventNoticeStore.Pending(91L,1L,"EVENTO_CANCELADO",Instant.parse("2030-01-01T00:00:00Z"),null,List.of())));var dispatcher=dispatcher();dispatcher.dispatch();verify(store,never()).delivered(anyLong());status=200;dispatcher.dispatch();verify(store).delivered(91L);assertEquals(2,bodies.size());assertEquals(bodies.get(0),bodies.get(1));verifyNoInteractions(mongo);}
 @Test void mongoOutageRetainsFavoritePublication(){when(store.pending()).thenReturn(List.of(new EventNoticeStore.Pending(92L,1L,"ARTISTA_FAVORITO_PUBLICADO",Instant.now(),"tandil",List.of(3L))));when(mongo.getCollection("preferences")).thenThrow(new com.mongodb.MongoException("simulated"));dispatcher().dispatch();verify(store,never()).delivered(anyLong());assertTrue(bodies.isEmpty());}
 @Test @SuppressWarnings("unchecked") void favoriteArtistAndCityAreMatchedBeforeDelivery(){
  when(store.pending()).thenReturn(List.of(new EventNoticeStore.Pending(93L,1L,"ARTISTA_FAVORITO_PUBLICADO",Instant.now(),"tandil",List.of(3L))));MongoCollection<Document> collection=mock(MongoCollection.class);FindIterable<Document> found=mock(FindIterable.class);MongoCursor<Document> cursor=mock(MongoCursor.class);when(mongo.getCollection("preferences")).thenReturn(collection);when(collection.find(any(Bson.class))).thenReturn(found);when(found.iterator()).thenReturn(cursor);when(cursor.hasNext()).thenReturn(true,false);when(cursor.next()).thenReturn(new Document("_id","77"));status=200;dispatcher().dispatch();verify(store).delivered(93L);assertTrue(bodies.get(0).contains("77"));var query=org.mockito.ArgumentCaptor.forClass(Bson.class);verify(collection).find(query.capture());String json=query.getValue().toBsonDocument().toJson();assertTrue(json.contains("favoriteParticipantIds"));assertTrue(json.contains("preferredCity"));assertTrue(json.contains("tandil"));
 }
}
