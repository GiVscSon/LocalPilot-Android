package org.localpilot.shared;

/** Process-local, advisory GGUF bridge for the embedded console laboratory.
 *  A model has no OS input authority. Only the emulator's verified controller
 *  may convert a STRICT suggestion into a bounded homebrew button press.
 */
public final class ConsoleAgentBridge {
 private ConsoleAgentBridge(){}
 public interface Reply { void complete(String raw); }
 public interface Brain {
  String label();
  boolean vision();
  void decide(String observation,byte[] checkedPng,Reply reply);
  void cancel();
 }
 private static volatile Brain selected;
 public static synchronized void set(Brain brain) {
  if(brain==null||brain.label()==null||brain.label().isBlank())
   throw new IllegalArgumentException("Selected model required");
  selected=brain;
 }
 public static Brain get(){return selected;}
 public static synchronized void clear(){selected=null;}
}
