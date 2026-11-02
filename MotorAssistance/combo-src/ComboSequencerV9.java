package dev.gallon.motorassistance.fabric.combat;

import dev.gallon.motorassistance.common.mixins.ComboClientInvoker;
import dev.gallon.motorassistance.common.mixins.ComboKeyboardInvoker;
import dev.gallon.motorassistance.fabric.config.TheModConfig;
import me.shedaniel.autoconfig.AutoConfig;
import net.minecraft.class_304;
import net.minecraft.class_310;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Minecraft 1.21.11 (intermediary mappings):
 * A real right click in hotbar 7 is the FIRST use.
 * Then synthetic G, then ONE extra right click. No T/F/hold-key trigger.
 * Config is the same AutoConfig object already presented by Mod Menu.
 */
public final class ComboSequencer {
    private static final int SLOT_SEVEN=6;
    private static final int BUTTON_RIGHT=1;
    private static final int KEY_G=71;
    private static final int RELEASE=0, PRESS=1;
    private static final long NS_PER_MS=1_000_000L;
    private static final String G_TRANSLATION_KEY="key.keyboard.g";
    private static final int WAIT_G=1, WAIT_SECOND_USE=2;

    private static boolean active;
    private static boolean mouseHeld;
    private static boolean gDown;
    private static long window;
    private static long firstClickNs, nextDueNs, gPressNs, gReleaseNs, slotReadyNs;
    private static int step, expectedGSlot;

    private ComboSequencer(){}

    private static int randomMs(int min, int max){
        return ThreadLocalRandom.current().nextInt(min,max+1);
    }

    private static boolean enabled(){
        TheModConfig cfg=(TheModConfig)AutoConfig.getConfigHolder(TheModConfig.class).getConfig();
        return cfg!=null && cfg.slotSevenRightClickComboEnabled;
    }

    private static boolean ready(class_310 client){
        return client!=null && client.field_1724!=null && client.field_1687!=null
                && client.field_1755==null && client.field_1774!=null;
    }

    private static int selectedSlot(class_310 client){
        return client.field_1724.method_31548().method_67532();
    }

    /** Listen to the raw mouse callback without cancelling vanilla input. */
    public static void onMouseButton(long handle,int button,int action){
        if(button!=BUTTON_RIGHT) return;
        if(action==RELEASE){
            mouseHeld=false;
            return;
        }
        if(action!=PRESS || mouseHeld) return;
        mouseHeld=true;
        // The real mouse event is deliberately NOT consumed; its vanilla item
        // use serves as the first click, avoiding a duplicate right click.
        if(active) return;
        class_310 client=class_310.method_1551();
        if(!ready(client) || selectedSlot(client)!=SLOT_SEVEN
                || ((ComboClientInvoker)client).motorassistancemod$getItemUseCooldown()>0 || !enabled()) return;
        window=handle;
        firstClickNs=System.nanoTime();
        nextDueNs=firstClickNs+randomMs(55,80)*NS_PER_MS;
        expectedGSlot=-1;
        slotReadyNs=0L;
        gDown=false;
        step=WAIT_G;
        active=true;
    }

    private static int slotBoundToG(class_310 client){
        if(client.field_1690==null || client.field_1690.field_1852==null) return -1;
        class_304[] keys=client.field_1690.field_1852;
        for(int i=0;i<keys.length;i++){
            if(keys[i]!=null && G_TRANSLATION_KEY.equals(keys[i].method_1428()))
                return i;
        }
        return -1; // G may be assigned to a non-hotbar operation
    }

    public static void onRender(class_310 client){
        if(!active) return;
        if(!ready(client) || client.method_22683().method_4490()!=window || !enabled()){
            stop(client);
            return;
        }
        long now=System.nanoTime();
        if(gDown && now>=gReleaseNs){
            sendG(client,RELEASE);
            gDown=false;
        }
        if(step==WAIT_G){
            if(now<nextDueNs) return;
            // If the player changed slots manually, do not run a delayed G.
            if(selectedSlot(client)!=SLOT_SEVEN){
                stop(client);
                return;
            }
            expectedGSlot=slotBoundToG(client);
            sendG(client,PRESS);
            gDown=true;
            gPressNs=now;
            gReleaseNs=now+randomMs(16,28)*NS_PER_MS;
            slotReadyNs=0L;
            nextDueNs=Math.max(gPressNs+randomMs(60,88)*NS_PER_MS,
                            firstClickNs+randomMs(118,155)*NS_PER_MS);
            step=WAIT_SECOND_USE;
            return;
        }
        if(step!=WAIT_SECOND_USE){
            stop(client);
            return;
        }
        if(gDown) return;
        if(expectedGSlot>=0 && selectedSlot(client)!=expectedGSlot){
            slotReadyNs=0L;
            if(now-gPressNs>600*NS_PER_MS) stop(client);
            return;
        }
        if(slotReadyNs==0L){
            slotReadyNs=now;
            nextDueNs=Math.max(nextDueNs,now+randomMs(9,16)*NS_PER_MS);
        }
        if(now>=nextDueNs && ((ComboClientInvoker)client).motorassistancemod$getItemUseCooldown()<=0){
            // This synthetic use is the ONLY extra right click.
            ((ComboClientInvoker)client).motorassistancemod$useItem();
            active=false;
        }else if(now-gPressNs>700*NS_PER_MS){
            stop(client);
        }
    }

    private static void sendG(class_310 client,int action){
        ((ComboKeyboardInvoker)client.field_1774)
                .motorassistancemod$syntheticKey(window,KEY_G,0,action,0);
    }

    private static void stop(class_310 client){
        if(gDown && client!=null && client.field_1774!=null) sendG(client,RELEASE);
        gDown=false;
        active=false;
    }
}
