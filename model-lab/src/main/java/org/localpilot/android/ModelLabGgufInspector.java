package org.localpilot.android;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Pure Java, bounded, READ-ONLY GGUF v2/v3 header inspector.
 * Never executes arbitrary model data, loads tensors, or trusts offsets.
 */
public final class ModelLabGgufInspector {
 private ModelLabGgufInspector(){}
 public static final class Info {
  public final int version;
  public final long tensors,metadata;
  public final String name,architecture,quantization,tokenizer;
  public final long context;
  Info(int version,long tensors,long metadata,String name,String architecture,
       String quantization,String tokenizer,long context){
   this.version=version;this.tensors=tensors;this.metadata=metadata;this.name=name;
   this.architecture=architecture;this.quantization=quantization;
   this.tokenizer=tokenizer;this.context=context;
  }
  public String summary(){
   return "GGUF v"+version+
     "\nИмя: "+(name.isEmpty()?"не указано":name)+
     "\nАрхитектура: "+(architecture.isEmpty()?"UNKNOWN":architecture)+
     "\nКвантование (general.file_type): "+(quantization.isEmpty()?"UNKNOWN":quantization)+
     "\nТензоров: "+tensors+", записей метаданных: "+metadata+
     "\nТокенизатор: "+(tokenizer.isEmpty()?"не указан":tokenizer)+
     "\nКонтекст (по метаданным): "+(context>0?context:"UNKNOWN");
  }
 }
 private static final long MAX_HEADER=128L*1024*1024;
 private static final int MAX_KEYS=16384;
 private static final int MAX_KEY_LEN=1024;
 private static final int MAX_DISPLAY_VALUE=2048;
 private static long safeAdd(long a,long b)throws IOException{
  if(a<0||b<0||a>Long.MAX_VALUE-b)throw new IOException("Переполнение GGUF");
  return a+b;
 }
 private static long ensure(RandomAccessFile in,long bytes,long limit)throws IOException{
  long pos=in.getFilePointer(),next=safeAdd(pos,bytes);
  if(next>limit)throw new IOException("Повреждённые границы GGUF metadata");
  return next;
 }
 private static int u8(RandomAccessFile in,long limit)throws IOException{
  ensure(in,1,limit);return in.readUnsignedByte();
 }
 private static long u32(RandomAccessFile in,long limit)throws IOException{
  return ((long)u8(in,limit))|((long)u8(in,limit)<<8)|
    ((long)u8(in,limit)<<16)|((long)u8(in,limit)<<24);
 }
 private static long u64(RandomAccessFile in,long limit)throws IOException{
  long low=u32(in,limit),high=u32(in,limit);
  if(high>0x7fffffffL)throw new IOException("Недопустимая длина GGUF");
  return (high<<32)|low;
 }
 private static String string(RandomAccessFile in,long limit,int max)throws IOException{
  long count=u64(in,limit);
  if(count>max)throw new IOException("Слишком длинная строка метаданных");
  ensure(in,count,limit);
  byte[] bytes=new byte[(int)count];in.readFully(bytes);
  return new String(bytes,StandardCharsets.UTF_8);
 }
 private static void skip(RandomAccessFile in,long bytes,long limit)throws IOException{
  if(bytes<0||bytes>MAX_HEADER)throw new IOException("Слишком большой элемент метаданных");
  in.seek(ensure(in,bytes,limit));
 }
 // Fixed width types: 0/1=uint8/int8, 2/3=int16/uint16, 4/5/6=uint32/int32/float32,
 // 7=bool, 8=string, 9=array, 10/11/12=uint64/int64/float64.
 private static int width(int type){
  return switch(type){case 0,1,7->1;case 2,3->2;case 4,5,6->4;
   case 10,11,12->8;default->-1;};
 }
 private static String value(RandomAccessFile in,int type,long limit,
                             boolean wanted)throws IOException{
  int bytes=width(type);
  if(bytes>0){
   if(wanted){
    long v=bytes==8?u64(in,limit):bytes==4?u32(in,limit):
        bytes==2?(u8(in,limit)|(u8(in,limit)<<8)):u8(in,limit);
    return Long.toString(v);
   }
   skip(in,bytes,limit);return "";
  }
  if(type==8){
   long n=u64(in,limit);
   if(n<0||n>MAX_HEADER)throw new IOException("Слишком длинное значение GGUF");
   if(!wanted||n>MAX_DISPLAY_VALUE){skip(in,n,limit);return "";}
   ensure(in,n,limit);
   byte[] b=new byte[(int)n];in.readFully(b);
   return new String(b,StandardCharsets.UTF_8).replaceAll("[\\p{Cntrl}]"," ").trim();
  }
  if(type==9){
   int element=(int)u32(in,limit);
   long count=u64(in,limit);
   if(count<0||count>10_000_000L)
    throw new IOException("Слишком длинный массив GGUF");
   int unit=width(element);
   if(unit>0){if(count>Long.MAX_VALUE/unit)throw new IOException("Переполнение массива");
    skip(in,count*unit,limit);return "";}
   if(element==8){
    for(long i=0;i<count;i++){
     long n=u64(in,limit);skip(in,n,limit);
    }
    return "";
   }
   throw new IOException("Неподдерживаемый вложенный тип GGUF: "+element);
  }
  throw new IOException("Неверный тип значения GGUF: "+type);
 }
 public static Info inspect(File file)throws IOException{
  if(file==null||!file.isFile())throw new IOException("GGUF-файл отсутствует");
  try(RandomAccessFile in=new RandomAccessFile(file,"r")){
   long limit=Math.min(in.length(),MAX_HEADER);
   if(limit<24||in.readUnsignedByte()!='G'||in.readUnsignedByte()!='G'||
      in.readUnsignedByte()!='U'||in.readUnsignedByte()!='F')
    throw new IOException("Неверная сигнатура GGUF");
   int version=(int)u32(in,limit);
   if(version!=2&&version!=3)
    throw new IOException("Неподдерживаемая версия GGUF: "+version);
   long tensors=u64(in,limit),count=u64(in,limit);
   if(tensors>2_000_000||count>MAX_KEYS)
    throw new IOException("Подозрительное количество тензоров/метаданных");
   Map<String,String> interesting=new HashMap<>();
   for(long i=0;i<count;i++){
    String key=string(in,limit,MAX_KEY_LEN);
    int type=(int)u32(in,limit);
    boolean chosen=key.equals("general.name")||key.equals("general.architecture")||
      key.equals("general.file_type")||key.equals("tokenizer.ggml.model")||
      key.endsWith(".context_length");
    String val=value(in,type,limit,chosen);
    if(chosen&&!interesting.containsKey(key))interesting.put(key,val);
   }
   String arch=interesting.getOrDefault("general.architecture","");
   String ctx=interesting.getOrDefault(arch+".context_length","");
   if(ctx.isEmpty())for(Map.Entry<String,String> p:interesting.entrySet())
    if(p.getKey().endsWith(".context_length")){ctx=p.getValue();break;}
   long context=0;
   try{context=Long.parseLong(ctx);}catch(NumberFormatException ignored){}
   return new Info(version,tensors,count,interesting.getOrDefault("general.name",""),
     arch,interesting.getOrDefault("general.file_type",""),
     interesting.getOrDefault("tokenizer.ggml.model",""),context);
  }
 }
}
