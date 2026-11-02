package dev.gallon.motorassistance.fabric.combat;

import dev.gallon.motorassistance.common.mixins.ComboClientInvoker;
import dev.gallon.motorassistance.common.mixins.ComboKeyboardInvoker;
import net.minecraft.class_304;
import net.minecraft.class_310;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Client-thread, non-blocking combo:
 * T (vanilla keybind selects hotbar slot 7) -> wait for slot 7 -> use
 * -> G (vanilla keybind) -> wait for its hotbar selection -> use -> F.
 *
 * Crucial: the T event MUST reach vanilla. Cancelling it was the v4 bug.
 * Server-side selected-slot updates and item-use cooldowns need time, too.
 */
public final class ComboSequencer {
    private static final int KEY_T = 84;
    private static final int KEY_G = 71;
    private static final int KEY_F = 70;
    private static final int SLOT_T = 6; // Minecraft hotbar slot 7 (zero-based)
    private static final int RELEASE = 0;
    private static final int PRESS = 1;
    private static final long NS_PER_MS = 1_000_000L;
    private static final String HOLD_BINDING = "key.motorassistancemod.auto_attack";
    private static final String G_TRANSLATION_KEY = "key.keyboard.g";

    private static final int WAIT_T_SLOT = 0;
    private static final int WAIT_G_PRESS = 1;
    private static final int WAIT_G_SLOT = 2;
    private static final int WAIT_F_PRESS = 3;
    private static final int WAIT_F_RELEASE = 4;

    private static boolean tHeld;
    private static boolean active;
    private static int step;
    private static long window;
    private static long originNs;
    private static long firstRightNs;
    private static long nextDueNs;
    private static long slotReadyNs;
    private static long gPressedNs;
    private static long gReleaseNs, fReleaseNs;
    private static int gExpectedSlot;
    private static boolean gDown, fDown;

    private ComboSequencer() {}

    public static boolean onRawKey(long handle, int key, int action) {
        if (key != KEY_T) return false;
        if (action == RELEASE) {
            tHeld = false;
            return false;
        }
        if (action != PRESS) return false;
        class_310 client = class_310.method_1551();
        class_304 hold = class_304.method_65807(HOLD_BINDING);
        if (!ready(client) || hold == null || !hold.method_1434()) return false;
        if (!tHeld && !active) {
            tHeld = true;
            start(handle);
        }
        // Never cancel vanilla T. The bound hotbar key MUST select slot 7.
        return false;
    }

    private static int rand(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    private static void start(long handle) {
        window = handle;
        originNs = System.nanoTime();
        nextDueNs = 0L;
        slotReadyNs = 0L;
        firstRightNs = 0L;
        gPressedNs = 0L;
        gExpectedSlot = -1;
        gDown = false;
        fDown = false;
        step = WAIT_T_SLOT;
        active = true;
    }

    private static int slot(class_310 client) {
        return client.field_1724.method_31548().method_67532();
    }

    /** If G is assigned to a hotbar slot, wait for that exact slot. */
    private static int slotBoundToG(class_310 client) {
        if (client.field_1690 == null || client.field_1690.field_1852 == null) return -1;
        class_304[] keys = client.field_1690.field_1852;
        for (int i=0; i<keys.length; i++) {
            if (keys[i] != null && G_TRANSLATION_KEY.equals(keys[i].method_1428())) return i;
        }
        return -1; // G may be another kind of keybind.
    }

    public static void onRender(class_310 client) {
        if (!active) return;
        if (!ready(client) || client.method_22683().method_4490() != window) {
            stop(client);
            return;
        }

        final long now = System.nanoTime();
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

        switch (step) {
            case WAIT_T_SLOT:
                if (slot(client) == SLOT_T) {
                    if (slotReadyNs == 0L) {
                        slotReadyNs = now;
                        // 140-210ms from T, never before slot 7 is selected.
                        nextDueNs = Math.max(originNs + rand(140, 210)*NS_PER_MS,
                                slotReadyNs + rand(100, 160)*NS_PER_MS);
                    }
                    if (now >= nextDueNs) {
                        if (slot(client) != SLOT_T) {
                            stop(client);
                            return;
                        }
                        use(client);
                        firstRightNs = now;
                        nextDueNs = now + rand(90, 150)*NS_PER_MS;
                        step = WAIT_G_PRESS;
                    }
                } else {
                    slotReadyNs = 0L;
                    nextDueNs = 0L;
                    if (now - originNs > 900*NS_PER_MS) stop(client);
                }
                break;
            case WAIT_G_PRESS:
                if (now >= nextDueNs) {
                    gExpectedSlot = slotBoundToG(client);
                    sendKey(client, KEY_G, PRESS);
                    gPressedNs = now;
                    gDown = true;
                    gReleaseNs = now + rand(60, 90)*NS_PER_MS;
                    slotReadyNs = 0L;
                    nextDueNs = 0L;
                    step = WAIT_G_SLOT;
                }
                break;
            case WAIT_G_SLOT:
                if (gDown) break; // give the vanilla keyboard logic a full tick
                if (gExpectedSlot < 0 || slot(client) == gExpectedSlot) {
                    if (slotReadyNs == 0L) {
                        slotReadyNs = now;
                        // Give the new held item and server update time to settle.
                        // 320-430ms between uses also avoids rapid-use failures.
                        nextDueNs = Math.max(
                                now + rand(120, 195)*NS_PER_MS,
                                Math.max(firstRightNs + rand(320, 430)*NS_PER_MS,
                                         gPressedNs + rand(155, 235)*NS_PER_MS));
                    }
                    if (now >= nextDueNs) {
                        if (gExpectedSlot >= 0 && slot(client) != gExpectedSlot) {
                            stop(client);
                            return;
                        }
                        use(client);
                        nextDueNs = now + rand(60, 115)*NS_PER_MS;
                        step = WAIT_F_PRESS;
                    }
                } else {
                    slotReadyNs = 0L;
                    nextDueNs = 0L;
                    // Never place with the wrong hotbar item.
                    if (now - gPressedNs > 1100*NS_PER_MS) stop(client);
                }
                break;
            case WAIT_F_PRESS:
                if (now >= nextDueNs) {
                    sendKey(client, KEY_F, PRESS);
                    fDown = true;
                    fReleaseNs = now + rand(60, 90)*NS_PER_MS;
                    step = WAIT_F_RELEASE;
                }
                break;
            default:
                break;
        }
    }

    private static boolean ready(class_310 client) {
        return client != null && client.field_1724 != null && client.field_1687 != null
                && client.field_1755 == null && client.field_1774 != null;
    }

    private static void sendKey(class_310 client, int key, int action) {
        ((ComboKeyboardInvoker)client.field_1774).motorassistancemod$syntheticKey(window,key,0,action,0);
    }

    private static void use(class_310 client) {
        ((ComboClientInvoker)client).motorassistancemod$useItem();
    }

    private static void stop(class_310 client) {
        if (client != null && client.field_1774 != null) {
            if (gDown) sendKey(client, KEY_G, RELEASE);
            if (fDown) sendKey(client, KEY_F, RELEASE);
        }
        active = false;
        gDown = false;
        fDown = false;
    }
}
