package test;

import dev.gallon.motorassistance.fabric.combat.ComboSequencer;
import net.minecraft.class_304;
import net.minecraft.class_310;

/** Isolated simulated-client regression tests for the one-shot slot-7 combo. */
public final class ComboSequencerTest {
    private static void check(boolean test, String message) {
        if (!test) throw new AssertionError(message);
    }
    private static void poll(class_310 client, int count) throws Exception {
        for (int i=0; i<count; i++) {
            ComboSequencer.onRender(client);
            Thread.sleep(10);
        }
    }
    private static void hold(class_310 c, boolean pressed) throws Exception {
        class_304.setTestPressed(pressed);
        poll(c, 2);
    }
    private static void reset(class_310 c) throws Exception {
        hold(c, false);
        c.clickSlots.clear();
        c.syntheticKeys.clear();
        c.eventTrace.clear();
        c.field_1752=0;
        c.autoGSlot=true;
    }
    private static void untilUses(class_310 c, int count) throws Exception {
        for (int i=0; i<100 && c.clickSlots.size()<count; i++) poll(c,1);
        check(c.clickSlots.size()==count, "Expected "+count+" uses: "+c.clickSlots);
    }
    private static void finished(class_310 c) {
        check(c.clickSlots.size()==2,"Exactly two uses required: "+c.clickSlots);
        check(c.clickSlots.get(0)==6,"First right click must be on slot 7");
        check(c.clickSlots.get(1)==1,"Second right click must be on G-bound slot");
        check(String.join(",",c.eventTrace).equals("use:6,71:1,71:0,use:1"),
            "Wrong input sequence: "+c.eventTrace);
        check(c.syntheticKeys.stream().noneMatch(x->x.startsWith("84:") || x.startsWith("70:")),
            "Unexpected T or F input: "+c.syntheticKeys);
    }
    public static void main(String[] args) throws Exception {
        class_310 c=class_310.method_1551();

        // A press outside slot 7 must NOT trigger, even if slot 7 is selected later.
        reset(c);
        c.field_1724.method_31548().method_61496(0);
        hold(c,true);
        poll(c,18);
        check(c.clickSlots.isEmpty(),"Pressing while on another slot triggered combo");
        c.field_1724.method_31548().method_61496(6);
        poll(c,20);
        check(c.clickSlots.isEmpty(),"Switching to slot 7 while held triggered combo");

        // Releasing and newly pressing in slot 7 triggers exactly once.
        hold(c,false);
        long started=System.nanoTime();
        hold(c,true);
        untilUses(c,2);
        long duration=(System.nanoTime()-started)/1_000_000L;
        check(duration<410,"Normal slot 7 combo too slow: "+duration+"ms");
        finished(c);

        // Still holding the key must not trigger more combos.
        c.field_1724.method_31548().method_61496(6);
        poll(c,35);
        check(c.clickSlots.size()==2,"Repeated combo without new hold-key press");

        // New press after release can start another combo.
        reset(c);
        c.field_1724.method_31548().method_61496(6);
        hold(c,true);
        untilUses(c,2);
        finished(c);

        // G is allowed to take longer switching, but no premature click.
        reset(c);
        c.field_1724.method_31548().method_61496(6);
        c.autoGSlot=false;
        hold(c,true);
        poll(c,26);
        check(c.clickSlots.size()==1,"Second click before G selected a slot");
        c.field_1724.method_31548().method_61496(1);
        untilUses(c,2);
        finished(c);

        // Respect Minecraft's item use cooldown if it is not ready.
        reset(c);
        c.field_1724.method_31548().method_61496(6);
        c.field_1752=4;
        hold(c,true);
        poll(c,10);
        check(c.clickSlots.isEmpty(),"Right click fired during use cooldown");
        c.field_1752=0;
        untilUses(c,2);
        finished(c);

        hold(c,false);
        System.out.println("PASS: slot-7 rising edge only; fast use/G/use; no T/F; no repeat; delayed G; cooldown.");
    }
}
