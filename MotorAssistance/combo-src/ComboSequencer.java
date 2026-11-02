package dev.gallon.motorassistance.fabric.combat;

import dev.gallon.motorassistance.common.mixins.ComboClientInvoker;
import dev.gallon.motorassistance.common.mixins.ComboKeyboardInvoker;
import net.minecraft.class_304;
import net.minecraft.class_310;
import java.util.concurrent.ThreadLocalRandom;

/** Client-thread only, nanosecond-based, one-shot T combination while auto-attack key is held. */
public final class ComboSequencer {
    private static final int KEY_T = 84;
    private static final int KEY_G = 71;
    private static final int KEY_F = 70;
    private static final int RELEASE = 0;
    private static final int PRESS = 1;
    private static final int REPEAT = 2;
    private static final long NS_PER_MS = 1_000_000L;
    private static final String HOLD_BINDING = "key.motorassistancemod.auto_attack";

    private static boolean tHeld;
    private static boolean active;
    private static int nextStep;
    private static long originNs;
    private static long rightOneNs, gNs, rightTwoNs, fNs;
    private static long gReleaseNs, fReleaseNs;
    private static boolean gDown, fDown;
    private static long window;

    private ComboSequencer() {}

    /** Called in the vanilla raw keyboard callback, prior to the game's chat key handling. */
    public static boolean onRawKey(long handle, int key, int action) {
        if (key != KEY_T) return false;
        if (action == RELEASE) {
            tHeld = false;
            return false;
        }
        class_310 client = class_310.method_1551();
        class_304 hold = class_304.method_65807(HOLD_BINDING);
        if (!ready(client) || hold == null || !hold.method_1434()) return false;
        if (action == PRESS && !tHeld && !active) {
            tHeld = true;
            start(handle);
        }
        // Suppress chat while the held modifier is down, including key repeats.
        return action == PRESS || action == REPEAT;
    }

    private static int between(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    private static void start(long handle) {
        window = handle;
        originNs = System.nanoTime();
        int one = between(50,80);
        int g = between(100,150);
        int two = between(Math.max(150,g + 12),220);
        int f = two + between(30,70);
        rightOneNs = originNs + one * NS_PER_MS;
        gNs = originNs + g * NS_PER_MS;
        rightTwoNs = originNs + two * NS_PER_MS;
        fNs = originNs + f * NS_PER_MS;
        nextStep = 0;
        gDown = false;
        fDown = false;
        active = true;
    }

    public static void onRender(class_310 client) {
        if (!active) return;
        if (!ready(client) || client.method_22683().method_4490() != window) {
            stop(client);
            return;
        }
        long now = System.nanoTime();
        // Separate releases from the next operation, and never sleep on the render thread.
        if (gDown && now >= gReleaseNs) {
            sendKey(client, KEY_G, RELEASE);
            gDown = false;
        }
        if (fDown && now >= fReleaseNs) {
            sendKey(client, KEY_F, RELEASE);
            fDown = false;
            active = false;
            return;
        }
        // At low FPS, due actions catch up in sequence on the same frame.
        while (active) {
            if (nextStep == 0 && now >= rightOneNs) {
                use(client); nextStep = 1;
            } else if (nextStep == 1 && now >= gNs) {
                sendKey(client, KEY_G, PRESS);
                gDown = true;
                gReleaseNs = now + 12 * NS_PER_MS;
                nextStep = 2;
            } else if (nextStep == 2 && now >= rightTwoNs) {
                if (gDown) { sendKey(client, KEY_G, RELEASE); gDown = false; }
                use(client); nextStep = 3;
            } else if (nextStep == 3 && now >= fNs) {
                sendKey(client, KEY_F, PRESS);
                fDown = true;
                fReleaseNs = now + 12 * NS_PER_MS;
                nextStep = 4;
            } else break;
        }
    }

    private static boolean ready(class_310 client) {
        return client != null && client.field_1724 != null && client.field_1687 != null
                && client.field_1755 == null && client.field_1774 != null;
    }

    private static void sendKey(class_310 client, int key, int action) {
        ((ComboKeyboardInvoker)client.field_1774).motorassistancemod$syntheticKey(window,key,0,action,0);
    }

    /** Direct vanilla use-item operation, equivalent to a single in-world right click. */
    private static void use(class_310 client) {
        ((ComboClientInvoker)client).motorassistancemod$useItem();
    }

    private static void stop(class_310 client) {
        if (client != null && client.field_1774 != null) {
            if (gDown) sendKey(client,KEY_G,RELEASE);
            if (fDown) sendKey(client,KEY_F,RELEASE);
        }
        active = false;
        gDown = false;
        fDown = false;
        nextStep = 0;
    }
}
