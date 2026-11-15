package ar.edu.ticketya.gateway;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRoutingTest {
    static HttpServer upstream;
    @Value("${local.server.port}") int port;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry)throws Exception {
        upstream=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        upstream.createContext("/",exchange->{byte[] body=exchange.getRequestURI().toString().getBytes();exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();});
        upstream.start();
        for(String name:new String[]{"eventos","inventario","entradas","compras"})registry.add("ticketya."+name+".url",()->"http://127.0.0.1:"+upstream.getAddress().getPort());
    }
    @AfterAll static void close(){upstream.stop(0);}
    HttpResponse<String> get(String path)throws Exception{return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).GET().build(),HttpResponse.BodyHandlers.ofString());}
    @Test void enrutaLosCuatroServiciosSinCambiarRuta()throws Exception{
        for(String path:new String[]{"/api/v1/venues","/api/v1/participants"}){
            var result=get(path);assertEquals(200,result.statusCode());assertEquals(path,result.body());
        }
    }
    @Test void noPublicaOperacionesInternas()throws Exception{
        for(String path:new String[]{"/internal/orders/1","/internal/tickets/issue","/reservas/1/confirmar","/ocupaciones/liberar","/eventos/1/inventario"})assertEquals(401,get(path).statusCode());
    }

    @Test void queueRoutesEnforceRolesAndKeepPaths()throws Exception{
        for(String method:new String[]{"POST","GET","DELETE","PUT"}){
            String route="/colas/eventos/7"+(method.equals("GET")||method.equals("DELETE")?"/mi-turno":"");
            String role=method.equals("PUT")?"ADMINISTRADOR":"CLIENTE";
            String token=SecurityTestKeys.token("gateway","user","7",role,null,300);
            var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+route)).header("Authorization","Bearer "+token).method(method,HttpRequest.BodyPublishers.noBody()).build();
            var response=HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofString());
            assertEquals(200,response.statusCode());assertEquals(route,response.body());
        }
        assertEquals(401,get("/colas/eventos/7/mi-turno").statusCode());
        String token=SecurityTestKeys.token("gateway","user","7","CLIENTE",null,300);
        var forbidden=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/colas/eventos/7")).header("Authorization","Bearer "+token).PUT(HttpRequest.BodyPublishers.noBody()).build();
        assertEquals(403,HttpClient.newHttpClient().send(forbidden,HttpResponse.BodyHandlers.discarding()).statusCode());
    }

    @Test void qrRoutesEnforceAccessAndAdminRoles()throws Exception{
        String scan="/accesos/eventos/7/validaciones",assignment="/admin/accesos/eventos/7/personal/9";
        for(String method:new String[]{"POST","PUT","DELETE"}){
            String route=method.equals("POST")?scan:assignment,role=method.equals("POST")?"PERSONAL_ACCESO":"ADMINISTRADOR";
            var token=SecurityTestKeys.token("gateway","user","9",role,null,300);
            var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+route)).header("Authorization","Bearer "+token).method(method,HttpRequest.BodyPublishers.noBody()).build();
            var result=HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofString());assertEquals(200,result.statusCode());assertEquals(route,result.body());
        }
        for(String role:new String[]{"CLIENTE","ORGANIZADOR","ADMINISTRADOR"}){
            var token=SecurityTestKeys.token("gateway","user","9",role,null,300);
            var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+scan)).header("Authorization","Bearer "+token).POST(HttpRequest.BodyPublishers.noBody()).build();
            assertEquals(403,HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.discarding()).statusCode());
        }
    }

    @Test void personalPreferencesAndRecommendationsAreClientOnly()throws Exception{
        for(String method:new String[]{"GET","PUT"}){
            String route="/api/v1/me/preferences";var token=SecurityTestKeys.token("gateway","user","9","CLIENTE",null,300);
            var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+route)).header("Authorization","Bearer "+token).method(method,HttpRequest.BodyPublishers.noBody()).build();
            var result=HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofString());assertEquals(200,result.statusCode());assertEquals(route,result.body());
        }
        assertEquals(401,get("/api/v1/me/recommendations").statusCode());
        var admin=SecurityTestKeys.token("gateway","user","9","ADMINISTRADOR",null,300);
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/me/recommendations")).header("Authorization","Bearer "+admin).GET().build();
        assertEquals(403,HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.discarding()).statusCode());
    }
}
