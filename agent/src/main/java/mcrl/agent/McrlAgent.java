package mcrl.agent;

import java.lang.instrument.Instrumentation;

// Mcrl: lifts the client-side chat-restriction check.
public final class McrlAgent {

    private McrlAgent() {
    }

    public static void premain(String agentArgs, Instrumentation inst) {
        System.out.println("[mcrl] installed, scanning for the client chat-restriction enum");
        // retransformClasses() is never used, so false is fine here.
        inst.addTransformer(new ChatRestrictionTransformer(), false);
    }

    public static void agentmain(String agentArgs, Instrumentation inst) {
        premain(agentArgs, inst);
    }
}
