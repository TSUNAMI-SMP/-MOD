package dev.gallon.motorassistance.fabric.combat;

import dev.gallon.motorassistance.common.mixins.ComboClientInvoker;
import dev.gallon.motorassistance.common.mixins.ComboKeyboardInvoker;
import net.minecraft.class_304;
import net.minecraft.class_310;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Slot 7 + NEW press of the hold key -> right click -> G -> right click.
 * No T trigger and no F. Never auto-repeat while the key is held.
 * Timing stays close to the fast v6 combo, with a short optional wait
 * when G's hotbar change or an item-use cooldown is not yet completed.
 */
public final class ComboSequencer {
    private static final int KEY_G = 71;
    private static final int SLOT_SEVEN = 6; // zero-based index
    private static final int RELEASE = 0;
    private static final int PRESS = 1;
    private static final long NS_PER_MS = 1_000_000L;
    private static final String HOLD_BINDING = "key.motorassistancemod.auto_attack";
    private static final String G_TRANSLATION_KEY = "key.keyboard.g";

    private static final int WAIT_FIRST_RIGHT = 0;
    private static final int WAIT_G_PRESS = 1;
    private static final int WAIT_SECOND_RIGHT = 2;

    private static boolean lastHoldPressed;
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

    private static int rand(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    private static void start(long handle) {
        window = handle;
        originNs = System.nanoTime();
        // Fast initial click: 50-80ms after the press. No slot-switch delay.
        nextDueNs = originNs + rand(50,80) * NS_PER_MS;
        slotReadyNs = 0L;
        firstRightNs = 0L;
        gPressedNs = 0L;
        gExpectedSlot = -1;
        gDown = false;
        step = WAIT_FIRST_RIGHT;
        active = true;
    }

    private static int slot(class_310 client) {
        return client.field_1724.method_31548().method_67532();
    }

    private static int slotBoundToG(class_310 client) {
        if (client.field_1690 == null || client.field_1690.field_1852 == null) return -1;
        class_304[] keys = client.field_1690.field_1852;
        for (int i = 0; i < keys.length; i++) {
            if (keys[i] != null && G_TRANSLATION_KEY.equals(keys[i].method_1428())) return i;
        }
        return -1;
    }

    public static void onRender(class_310 client) {
        // Detect the rising edge every frame, including when the combo is busy.
        // A held key cannot start a second combo.
        class_304 hold = class_304.method_65807(HOLD_BINDING);
        boolean pressed = hold != null && hold.method_1434();
        boolean newlyPressed = pressed && !lastHoldPressed;
        lastHoldPressed = pressed;

        if (!ready(client)) {
            if (active) stop(client);
            return;
        }

        if (newlyPressed && !active && slot(client) == SLOT_SEVEN) {
            start(client.method_22683().method_4490());
        }
        if (!active) return;
        if (client.method_22683().method_4490() != window) {
            stop(client);
            return;
        }

        long now = System.nanoTime();
        if (gDown && now >= gReleaseNs) {
            sendKey(client, KEY_G, RELEASE);
            gDown = false;
        }

        switch (step) {
            case WAIT_FIRST_RIGHT:
                // Do not use if the player moved away from slot 7.
                if (slot(client) != SLOT_SEVEN) {
                    stop(client);
                    break;
                }
                if (now >= nextDueNs && client.field_1752 <= 0) {
                    use(client);
                    firstRightNs = now;
                    nextDueNs = now + rand(43,63) * NS_PER_MS;
                    step = WAIT_G_PRESS;
                } else if (now - originNs > 650 * NS_PER_MS) stop(client);
                break;
            case WAIT_G_PRESS:
                if (now < nextDueNs) break;
                gExpectedSlot = slotBoundToG(client);
                sendKey(client, KEY_G, PRESS);
                gDown = true;
                gPressedNs = now;
                gReleaseNs = now + rand(16,27) * NS_PER_MS;
                slotReadyNs = 0L;
                // Fast original interval, slightly adjusted for the G slot change.
                nextDueNs = Math.max(now + rand(58,86) * NS_PER_MS,
                        firstRightNs + rand(105,130) * NS_PER_MS);
                step = WAIT_SECOND_RIGHT;
                break;
            case WAIT_SECOND_RIGHT:
                if (gDown) break;
                if (gExpectedSlot >= 0 && slot(client) != gExpectedSlot) {
                    slotReadyNs = 0L;
                    if (now - gPressedNs > 650 * NS_PER_MS) stop(client);
                    break;
                }
                if (slotReadyNs == 0L) {
                    slotReadyNs = now;
                    nextDueNs = Math.max(nextDueNs, now + rand(9,17) * NS_PER_MS);
                }
                if (now >= nextDueNs && client.field_1752 <= 0) {
                    if (gExpectedSlot >= 0 && slot(client) != gExpectedSlot) {
                        stop(client);
                        break;
                    }
                    use(client);
                    // End here: no F, T, or additional inputs.
                    active = false;
                } else if (now - gPressedNs > 750 * NS_PER_MS) stop(client);
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
