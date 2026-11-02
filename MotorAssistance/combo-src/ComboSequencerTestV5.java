package test;

import dev.gallon.motorassistance.fabric.combat.ComboSequencer;
import net.minecraft.class_310;

public final class ComboSequencerTest {
    private static void check(boolean value,String message) {
        if(!value) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        class_310 c=class_310.method_1551();
        // Starting on slot 1, the first item use must never run before slot 7.
        boolean cancelled=ComboSequencer.onRawKey(1L,84,1);
        check(!cancelled,"T must reach the vanilla hotbar-key binding");
        for(int i=0;i<12;i++){ComboSequencer.onRender(c);Thread.sleep(15);}
        check(c.clickSlots.isEmpty(),"Right click fired before selecting hotbar 7");
        // Simulate Minecraft processing T after the raw callback.
        c.field_1724.method_31548().method_61496(6);
        for(int i=0;i<130;i++){
            ComboSequencer.onRender(c);
            Thread.sleep(12);
            if(c.syntheticKeys.contains("70:0")) break;
        }
        check(c.clickSlots.size()==2,"Expected exactly two item uses; got "+c.clickSlots);
        check(c.clickSlots.get(0)==6,"First use not on hotbar 7: "+c.clickSlots);
        check(c.clickSlots.get(1)==1,"Second use not on G-selected hotbar: "+c.clickSlots);
        String joined=String.join(",",c.syntheticKeys);
        check(joined.contains("71:1,71:0,70:1,70:0"),"Incorrect G/F input order: "+joined);
        check(!ComboSequencer.onRawKey(1L,84,0),"T release must be passed through");
        System.out.println("PASS: T forwarded; wrong-slot use prevented; G slot selected; use/use/F order.");
    }
}
