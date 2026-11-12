package org.localpilot.gamelab;

import java.security.SecureRandom;
import java.util.Base64;
import org.localpilot.shared.ConsoleAgentContract;

/** A one-use capability bound to the active ROM, observed frame and lifetime. */
public final class ConsoleAgentGate {
 private final SecureRandom random=new SecureRandom();
 private Ticket pending;
 public static final class Ticket {
  public final String nonce,epoch;
  public final int frame,hash;
  public final long issuedAt;
  private Ticket(String nonce,String epoch,int frame,int hash,long issuedAt){
   this.nonce=nonce;this.epoch=epoch;this.frame=frame;this.hash=hash;this.issuedAt=issuedAt;
  }
 }
 public Ticket begin(String epoch,int frame,int hash,long now){
  if(pending!=null||epoch==null||epoch.isBlank()||frame<15||hash==0||now<0)
   throw new IllegalStateException("NES agent observation not ready");
  byte[] nonce=new byte[16];random.nextBytes(nonce);
  pending=new Ticket(Base64.getUrlEncoder().withoutPadding().encodeToString(nonce),
    epoch,frame,hash,now);
  return pending;
 }
 public boolean hasPending(){return pending!=null;}
 public void stop(){pending=null;}
 public boolean consume(String nonce,String epoch,int currentHash,long now){
  Ticket ticket=pending;
  pending=null; // every response, including wrong ones, consumes the ticket
  return ticket!=null&&ConsoleAgentContract.tokenValid(nonce)&&
    ticket.nonce.equals(nonce)&&ticket.epoch.equals(epoch)&&
    currentHash==ticket.hash&&now>=ticket.issuedAt&&
    now-ticket.issuedAt<=ConsoleAgentContract.TTL_MS;
 }
}
