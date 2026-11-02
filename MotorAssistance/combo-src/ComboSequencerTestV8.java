package test;

import dev.gallon.motorassistance.fabric.combat.ComboSequencer;
import dev.gallon.motorassistance.fabric.config.TheModConfig;
import me.shedaniel.autoconfig.AutoConfig;
import net.minecraft.class_310;

/** Headless deterministic event-order regression tests. The first click is vanilla. */
public final class ComboSequencerTest {
    private static final class_310 client=class_310.method_1551();
    private static final TheModConfig config=(TheModConfig)
            AutoConfig.getConfigHolder(TheModConfig.class).getConfig();

    private static void check(boolean b,String m){
        if(!b) throw new AssertionError(m);
    }
    private static void poll(int frames)throws Exception{
        for(int i=0;i<frames;i++){
            ComboSequencer.onRender(client);
            Thread.sleep(10);
        }
    }
    private static void physicalRightClick(){
        ComboSequencer.onMouseButton(1L,1,1);
        // Vanilla receives the original mouse event, sends a FIRST use.
        client.motorassistancemod$useItem();
    }
    private static void release(){
        ComboSequencer.onMouseButton(1L,1,0);
    }
    private static void reset()throws Exception{
        release();
        poll(2);
        client.clickSlots.clear();
        client.syntheticKeys.clear();
        client.eventTrace.clear();
        client.field_1752=0;
        client.autoGSlot=true;
        config.slotSevenRightClickComboEnabled=true;
    }
    private static void waitForUses(int count)throws Exception{
        for(int i=0;i<100 && client.clickSlots.size()<count;i++)poll(1);
        check(client.clickSlots.size()==count,"Right-click count mismatch: "+client.clickSlots);
    }
    private static void checkCombo(){
        check(client.clickSlots.size()==2, "Expected first real and second synthetic use");
        check(client.clickSlots.get(0)==6,"First use must be in hotbar 7");
        check(client.clickSlots.get(1)==1,"Second use must be on G-selected hotbar");
        check(String.join(",",client.eventTrace).equals("use:6,71:1,71:0,use:1"),
                "Wrong sequence "+client.eventTrace);
        check(client.syntheticKeys.stream().noneMatch(v->v.startsWith("84:")||v.startsWith("70:")),
                "T or F was unexpectedly pressed");
    }

    public static void main(String[] args)throws Exception{
        reset();
        client.field_1724.method_31548().method_61496(0);
        physicalRightClick();
        poll(25);
        check(client.clickSlots.size()==1,"Other slot should not trigger the macro");
        check(client.syntheticKeys.isEmpty(),"Other slot triggered synthetic G");

        reset();
        client.field_1724.method_31548().method_61496(6);
        long start=System.nanoTime();
        physicalRightClick();
        waitForUses(2);
        long elapsed=(System.nanoTime()-start)/1_000_000L;
        check(elapsed<390,"Fast right/G/right was too slow: "+elapsed+"ms");
        checkCombo();
        // No further events while physical mouse button is still held.
        poll(30);
        check(client.clickSlots.size()==2,"Macro repeated while mouse held");

        reset();
        config.slotSevenRightClickComboEnabled=false;
        client.field_1724.method_31548().method_61496(6);
        physicalRightClick();
        poll(24);
        check(client.clickSlots.size()==1,"Disabled macro still added a click");
        check(client.syntheticKeys.isEmpty(),"Disabled macro still emitted G");

        reset();
        client.field_1724.method_31548().method_61496(6);
        physicalRightClick();
        config.slotSevenRightClickComboEnabled=false;
        poll(24);
        check(client.clickSlots.size()==1,"Turning OFF did not cancel pending macro");
        check(client.syntheticKeys.isEmpty(),"Turning OFF did not cancel G");

        reset();
        client.field_1724.method_31548().method_61496(6);
        client.autoGSlot=false;
        physicalRightClick();
        poll(25);
        check(client.clickSlots.size()==1,"Clicked before G-selected slot was ready");
        client.field_1724.method_31548().method_61496(1);
        waitForUses(2);
        checkCombo();

        reset();
        client.field_1724.method_31548().method_61496(6);
        physicalRightClick();
        client.field_1752=4;
        poll(25);
        check(client.clickSlots.size()==1,"Clicked while use cooldown was active");
        client.field_1752=0;
        waitForUses(2);
        checkCombo();

        reset();
        System.out.println("PASS: physical right/G/one extra right; slot7; ModMenu toggle; "+
                "cancel; timing; delayed slot; cooldown; no T/F or repeat.");
    }
}
