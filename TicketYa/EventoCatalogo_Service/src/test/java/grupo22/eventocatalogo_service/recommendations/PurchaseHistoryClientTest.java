package grupo22.eventocatalogo_service.recommendations;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import org.springframework.web.client.*;
import org.springframework.mock.env.MockEnvironment;
import grupo22.eventocatalogo_service.security.ServiceTokens;
class PurchaseHistoryClientTest {
 HttpServer server;String body="{\"eventIds\":[]}";int status=200;
 @BeforeEach void start()throws Exception{server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/",x->{byte[] bytes=body.getBytes(java.nio.charset.StandardCharsets.UTF_8);x.getResponseHeaders().set("Content-Type","application/json");x.sendResponseHeaders(status,bytes.length);x.getResponseBody().write(bytes);x.close();});server.start();}
 @AfterEach void stop(){server.stop(0);}
 PurchaseHistoryClient client(){String url="http://127.0.0.1:"+server.getAddress().getPort();var tokens=mock(ServiceTokens.class);when(tokens.client("compras",url)).thenReturn(RestClient.create(url));return new PurchaseHistoryClient(tokens,new MockEnvironment(),url);}
 @Test void validEmptyIsAvailable(){assertTrue(client().events(1L).isEmpty());}
 @Test void malformedCannotBecomeEmptyHistory(){for(String value:new String[]{"{}","null","{\"eventIds\":[-1]}","[]"}){body=value;assertThrows(RestClientException.class,()->client().events(1L));}}
 @Test void technicalErrorsCannotBecomeEmptyHistory(){for(int code:new int[]{401,403,404,429,503}){status=code;assertThrows(RestClientException.class,()->client().events(1L));}}
}
