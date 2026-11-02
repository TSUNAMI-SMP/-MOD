package test;

import dev.gallon.motorassistance.fabric.combat.ComboSequencer;
import net.minecraft.class_310;

public final class ComboSequencerTest {
    private static void check(boolean test, String message) {
        if (!test) throw new AssertionError(message);
    }
    private static void poll(class_310 c, int times) throws Exception {
        for (int i=0; i<times; i++) {
            ComboSequencer.onRender(c);
            Thread.sleep(10);
        }
    }
    private static void untilUses(class_310 c, int count) throws Exception {
        for (int i=0; i<105 && c.clickSlots.size()<count; i++) poll(c,1);
        check(c.clickSlots.size()==count,"Expected "+count+" uses; got "+c.clickSlots);
    }
    private static void reset(class_310 c) {
        c.clickSlots.clear();
        c.syntheticKeys.clear();
        c.eventTrace.clear();
        c.field_1752=0;
        c.autoGSlot=true;
        ComboSequencer.onRawKey(1L,84,0);
    }
    private static void finished(class_310 c) {
        check(c.clickSlots.size()==2, "Exactly two right clicks required: "+c.clickSlots);
        check(c.clickSlots.get(0)==6, "First right click must use slot 7");
        check(c.clickSlots.get(1)==1, "Second right click must use G-bound slot");
        check(c.eventTrace.size()==4, "Expected right/G press/G release/right: "+c.eventTrace);
        check(String.join(",",c.eventTrace).equals("use:6,71:1,71:0,use:1"), "Wrong order: "+c.eventTrace);
        check(c.syntheticKeys.stream().noneMatch(x->x.startsWith("70:")), "F is forbidden");
    }
    public static void main(String[] args) throws Exception {
        class_310 c=class_310.method_1551();
        c.field_1724.method_31548().method_61496(0);
        long start=System.nanoTime();
        check(!ComboSequencer.onRawKey(1L,84,1), "T must reach vanilla slot binding");
        poll(c,10);
        check(c.clickSlots.isEmpty(), "Premature placement while slot 1 is selected");
        c.field_1724.method_31548().method_61496(6);
        untilUses(c,2);
        finished(c);

        // Already in slot 7: the old fast combo timing should be maintained.
        reset(c);
        c.field_1724.method_31548().method_61496(6);
        start=System.nanoTime();
        ComboSequencer.onRawKey(1L,84,1);
        untilUses(c,2);
        long elapsed=(System.nanoTime()-start)/1_000_000L;
        check(elapsed<420, "Already-on-slot combo was too slow: "+elapsed+"ms");
        finished(c);

        // Simulate G not switching slots immediately. Never place on old item.
        reset(c);
        c.field_1724.method_31548().method_61496(6);
        c.autoGSlot=false;
        ComboSequencer.onRawKey(1L,84,1);
        poll(c,26);
        check(c.clickSlots.size()==1, "Second right-click happened before G switched: "+c.clickSlots);
        c.field_1724.method_31548().method_61496(1);
        untilUses(c,2);
        finished(c);

        // If a vanilla item-use cooldown exists, never waste right-clicks into it.
        reset(c);
        c.field_1724.method_31548().method_61496(6);
        c.field_1752=4;
        ComboSequencer.onRawKey(1L,84,1);
        poll(c,13);
        check(c.clickSlots.isEmpty(),"Item-use click should wait for actual cooldown");
        c.field_1752=0;
        untilUses(c,2);
        finished(c);

        reset(c);
        System.out.println("PASS: correct hotbar selection, fast timing, delayed-G handling, cooldown, no F.");
    }
}
