package dev.gallon.motorassistance.fabric.combat;

import dev.gallon.motorassistance.common.mixins.ComboClientInvoker;
import dev.gallon.motorassistance.common.mixins.ComboKeyboardInvoker;
import net.minecraft.class_304;
import net.minecraft.class_310;
import java.util.concurrent.ThreadLocalRandom;

/**
 * T (vanilla slot 7) -> use item -> G (vanilla key) -> use item; no F action.
 *
 * Keep v4-like timing while waiting for actual slot selection. Only increase
 * delays when a hotbar update or vanilla item-use cooldown has not completed.
 * All work happens on the client thread without sleeping/blocking rendering.
 */
public final class ComboSequencer {
    private static final int KEY_T = 84;
    private static final int KEY_G = 71;
    private static final int SLOT_T = 6;
    private static final int RELEASE = 0;
    private static final int PRESS = 1;
    private static final long NS_PER_MS = 1_000_000L;
    private static final String HOLD_BINDING = "key.motorassistancemod.auto_attack";
    private static final String G_TRANSLATION_KEY = "key.keyboard.g";

    private static final int WAIT_T_SLOT = 0;
    private static final int WAIT_G_PRESS = 1;
    private static final int WAIT_G_SLOT = 2;

    private static boolean tHeld;
    private static boolean active;
    private static int step;
    private static long window;
    private static long originNs;
    private static long nextDueNs;
    private static long slotReadyNs;
    private static long firstRightNs;
    private static long gPressedNs;
    private static long gReleaseNs;
    private static int gExpectedSlot;
    private static boolean gDown;

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
        // Let the real T key reach Minecraft to select hotbar slot 7.
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
        step = WAIT_T_SLOT;
        active = true;
    }

    private static int slot(class_310 client) {
        return client.field_1724.method_31548().method_67532();
    }

    private static int slotBoundToG(class_310 client) {
        if (client.field_1690 == null || client.field_1690.field_1852 == null) return -1;
        class_304[] keys = client.field_1690.field_1852;
        for (int i=0; i<keys.length; i++) {
            if (keys[i] != null && G_TRANSLATION_KEY.equals(keys[i].method_1428())) return i;
        }
        return -1;
    }

    public static void onRender(class_310 client) {
        if (!active) return;
        if (!ready(client) || client.method_22683().method_4490() != window) {
            stop(client);
            return;
        }
        long now = System.nanoTime();
        if (gDown && now >= gReleaseNs) {
            sendKey(client, KEY_G, RELEASE);
            gDown = false;
        }

        switch (step) {
            case WAIT_T_SLOT:
                if (slot(client) != SLOT_T) {
                    // Never use a different item when the T hotbar switch is pending.
                    slotReadyNs = 0L;
                    nextDueNs = 0L;
                    if (now - originNs > 650*NS_PER_MS) stop(client);
                    break;
                }
                if (slotReadyNs == 0L) {
                    slotReadyNs = now;
                    // Normal: about 55-82ms from T. Extra settling is just
                    // 9-17ms after the ACTUAL slot switch, not v5's 100-160ms.
                    nextDueNs = Math.max(originNs + rand(55,82)*NS_PER_MS,
                                         slotReadyNs + rand(9,17)*NS_PER_MS);
                }
                if (now >= nextDueNs && client.field_1752 <= 0) {
                    if (slot(client) != SLOT_T) {
                        stop(client);
                        break;
                    }
                    use(client);
                    firstRightNs = now;
                    // Keep G close to the initial fast combo.
                    nextDueNs = now + rand(43,63)*NS_PER_MS;
                    step = WAIT_G_PRESS;
                } else if (now - originNs > 750*NS_PER_MS) stop(client);
                break;

            case WAIT_G_PRESS:
                if (now < nextDueNs) break;
                gExpectedSlot = slotBoundToG(client);
                sendKey(client, KEY_G, PRESS);
                gDown = true;
                gPressedNs = now;
                gReleaseNs = now + rand(16,27)*NS_PER_MS;
                slotReadyNs = 0L;
                // Normal: G + 58-86ms, and first right click + 105-130ms.
                // Slot changes/cooldown can delay this only as necessary.
                nextDueNs = Math.max(now + rand(58,86)*NS_PER_MS,
                        firstRightNs + rand(105,130)*NS_PER_MS);
                step = WAIT_G_SLOT;
                break;

            case WAIT_G_SLOT:
                if (gDown) break;
                if (gExpectedSlot >= 0 && slot(client) != gExpectedSlot) {
                    slotReadyNs = 0L;
                    if (now - gPressedNs > 650*NS_PER_MS) stop(client);
                    break;
                }
                if (slotReadyNs == 0L) {
                    slotReadyNs = now;
                    nextDueNs = Math.max(nextDueNs, now + rand(9,17)*NS_PER_MS);
                }
                if (now >= nextDueNs && client.field_1752 <= 0) {
                    if (gExpectedSlot >= 0 && slot(client) != gExpectedSlot) {
                        stop(client);
                        break;
                    }
                    use(client);
                    // The combo ends on the second right click. No F key.
                    active = false;
                    step = WAIT_T_SLOT;
                } else if (now - gPressedNs > 750*NS_PER_MS) stop(client);
                break;
            default:
                stop(client);
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
        if (client != null && client.field_1774 != null && gDown) {
            sendKey(client, KEY_G, RELEASE);
        }
        active = false;
        gDown = false;
    }
}
