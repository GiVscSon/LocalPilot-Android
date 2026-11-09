"use strict";
(function(root){
 function nesObservation(rgb,frame,buttons){
  if(!rgb||rgb.length!==256*240||!Number.isSafeInteger(frame)||frame<0||!Number.isInteger(buttons)||buttons<0||buttons>255)
   throw Error('Invalid NES observation');
  const features=[];let hash=2166136261>>>0;
  for(let row=0;row<8;row++)for(let col=0;col<8;col++){
   let sum=0,count=0;
   for(let y=row*30+3;y<(row+1)*30;y+=6)for(let x=col*32+3;x<(col+1)*32;x+=6){
    const value=rgb[y*256+x];sum+=(((value>>>16)&255)*77+((value>>>8)&255)*150+(value&255)*29)/256;count++;
    hash=Math.imul(hash^value,16777619)>>>0;
   }
   features.push(Math.round(sum/count));
  }
  return {frame,pixelHash:hash|0,buttons,features};
 }
 class NesFrameSampler{
  constructor(){this.lastFrame=-1;this.seenFrame=-1;this.buttons=-1;}
  sample(rgb,frame,buttons){
   if(!Number.isSafeInteger(frame)||frame<0||frame<=this.seenFrame)return null;
   // Invalid input must not consume a frame or hide a later valid button transition.
   if(!rgb||rgb.length!==256*240||!Number.isInteger(buttons)||buttons<0||buttons>255)
    throw Error('Invalid NES observation');
   if(buttons===this.buttons&&frame-this.lastFrame<15){this.seenFrame=frame;return null;}
   const result=nesObservation(rgb,frame,buttons);this.seenFrame=frame;this.lastFrame=frame;this.buttons=buttons;return result;
  }
 }
 root.nesObservation=nesObservation;root.NesFrameSampler=NesFrameSampler;
 if(typeof module!=='undefined'&&module.exports)module.exports={nesObservation,NesFrameSampler};
})(typeof window!=='undefined'?window:{});
