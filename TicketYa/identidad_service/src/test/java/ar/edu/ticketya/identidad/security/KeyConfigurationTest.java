package ar.edu.ticketya.identidad.security;

import ar.edu.ticketya.identidad.config.JwtProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.security.*;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class KeyConfigurationTest {
    @TempDir Path directory;
    JwtProperties properties(Path privateKey,Path publicKey){return new JwtProperties("http://identity.invalid",List.of("ticketya-identidad"),
        privateKey.toString(),publicKey.toString(),"test-key",Duration.ofMinutes(15),Duration.ofMinutes(5),Duration.ofMinutes(2));}
    String pem(String type,byte[] bytes){return "-----BEGIN "+type+"-----\n"+Base64.getMimeEncoder(64,new byte[]{10}).encodeToString(bytes)+"\n-----END "+type+"-----\n";}
    @Test void missingInvalidAndRelativeFilesFailClosedWithoutLeakingContents()throws Exception{
        Path privateKey=directory.resolve("private.pem"),publicKey=directory.resolve("public.pem");
        var config=new KeyConfiguration();
        assertThrows(IllegalStateException.class,()->config.signingKey(properties(privateKey,publicKey)));
        String sentinel="secret-looking-invalid-key-"+UUID.randomUUID();Files.writeString(privateKey,sentinel);Files.writeString(publicKey,sentinel);
        var exception=assertThrows(IllegalStateException.class,()->config.signingKey(properties(privateKey,publicKey)));
        assertFalse(exception.toString().contains(sentinel));assertNull(exception.getCause());
        assertThrows(IllegalStateException.class,()->config.signingKey(properties(Path.of("private.pem"),Path.of("public.pem"))));
    }
    @Test void mismatchedWeakAndMatchingKeyPairs()throws Exception{
        var generator=KeyPairGenerator.getInstance("RSA");generator.initialize(3072);
        var first=generator.generateKeyPair();var second=generator.generateKeyPair();
        Path privateKey=directory.resolve("private.pem"),publicKey=directory.resolve("public.pem");
        Files.writeString(privateKey,pem("PRIVATE KEY",first.getPrivate().getEncoded()));
        Files.writeString(publicKey,pem("PUBLIC KEY",second.getPublic().getEncoded()));
        var config=new KeyConfiguration();assertThrows(IllegalStateException.class,()->config.signingKey(properties(privateKey,publicKey)));
        Files.writeString(publicKey,pem("PUBLIC KEY",first.getPublic().getEncoded()));
        assertEquals("test-key",config.signingKey(properties(privateKey,publicKey)).getKeyID());
        generator.initialize(2048);var weak=generator.generateKeyPair();
        Files.writeString(privateKey,pem("PRIVATE KEY",weak.getPrivate().getEncoded()));Files.writeString(publicKey,pem("PUBLIC KEY",weak.getPublic().getEncoded()));
        assertThrows(IllegalStateException.class,()->config.signingKey(properties(privateKey,publicKey)));
    }
    @Test void malformedIssuerIsRejected(){
        for(String issuer:List.of("identity.invalid","http://","https://user:password@host","https://host?token=value","https://host#fragment"))
            assertThrows(IllegalArgumentException.class,()->new JwtProperties(issuer,List.of("ticketya-identidad"),"private","public","id",Duration.ofMinutes(15),Duration.ofMinutes(5),Duration.ofMinutes(2)));
    }
}
