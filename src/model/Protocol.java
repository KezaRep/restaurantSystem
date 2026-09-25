package model;
import java.nio.charset.StandardCharsets;import java.util.Base64;
public final class Protocol {
 public static String encode(String s){return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));}
 public static String decode(String s){return new String(Base64.getUrlDecoder().decode(s),StandardCharsets.UTF_8);}
 // Order records are tab-delimited; names/notes are Base64-url encoded.
 public static String order(long id,int table,String dish,int qty,String status,long created,String note){return id+"\t"+table+"\t"+dish+"\t"+qty+"\t"+status+"\t"+created+"\t"+encode(note);}
 public record Order(long id,int table,String dish,int qty,String status,long created,String note){public String wire(){return order(id,table,dish,qty,status,created,note);}}
 public static Order parse(String line){String[] p=line.split("\t",-1);if(p.length!=7)throw new IllegalArgumentException("Bad order");return new Order(Long.parseLong(p[0]),Integer.parseInt(p[1]),p[2],Integer.parseInt(p[3]),p[4],Long.parseLong(p[5]),decode(p[6]));}
 private Protocol(){}
}
