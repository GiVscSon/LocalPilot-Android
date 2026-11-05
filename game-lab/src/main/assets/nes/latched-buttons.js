"use strict";
/**
 * Original GameLab controller adapter for slow Android WebView devices.
 *
 * A real Android touch may deliver DOWN and UP while the JSNES CPU thread is
 * paused by a long frame. Never discard a button before the emulated CPU has
 * run at least MIN_FRAMES frames with that button latched.
 *
 * This is an input-transport reliability rule, NOT synthetic gameplay reward.
 */
(function(root){
  class FrameHeldButtons{
    constructor(machine,minFrames=4){
      if(!machine||typeof machine.buttonDown!=="function"||
         typeof machine.buttonUp!=="function")throw Error("Gamepad requires a NES core");
      this.machine=machine;
      this.minFrames=Math.max(2,Math.min(12,minFrames|0));
      this.down=new Map();
    }
    change(key,code,isDown,frame){
      if(typeof key!=="string"||!Number.isInteger(code)
        ||!Number.isSafeInteger(frame)||frame<0)return false;
      if(isDown){
        if(this.down.has(key)){
          // Repeat DOWN does not extend/lose the original hold.
          this.down.get(key).releaseRequested=false;
        }else{
          this.machine.buttonDown(1,code);
          this.down.set(key,{code,started:frame,releaseRequested:false});
        }
        return true;
      }
      const held=this.down.get(key);
      if(!held)return false;
      held.releaseRequested=true;
      this.flush(frame);
      return true;
    }
    flush(frame){
      if(!Number.isSafeInteger(frame)||frame<0)return;
      for(const [key,held] of this.down){
        if(held.releaseRequested&&frame-held.started>=this.minFrames){
          this.machine.buttonUp(1,held.code);
          this.down.delete(key);
        }
      }
    }
    clear(){
      for(const held of this.down.values())
        this.machine.buttonUp(1,held.code);
      this.down.clear();
    }
  }
  root.FrameHeldButtons=FrameHeldButtons;
  if(typeof module!=="undefined"&&module.exports)module.exports={FrameHeldButtons};
})(typeof window!=="undefined"?window:{});
