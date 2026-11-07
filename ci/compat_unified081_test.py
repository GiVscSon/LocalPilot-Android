from pathlib import Path
p=Path("tests/Unified081IntegrationTest.java")
s=p.read_text()
a=s.index("        List<VisionV2Core.Blob> blobs =")
b=s.index("        // 3. Verify PaddleTransfer",a)
s=s[:a]+'''        VisionV2Core.Region region = new VisionV2Core.Region(0, 0, 100, 100);
        VisionV2Core.Pixels blank = (x,y) -> 0x101010;
        VisionV2Core.Pixels changed = (x,y) ->
            (x>=20 && x<45 && y>=20 && y<45) ? 0xf0f0f0 : 0x101010;
        VisionV2Core.Frame first=v2.observe(blank,100,100,region,null,1000,1,1);
        VisionV2Core.Frame second=v2.observe(changed,100,100,region,null,1050,1,1);
        check(!first.ready() && second.ready() && second.hasLocalMotion()
            && !second.movingObjects().isEmpty(), "V2 requires two fresh frames");
        check(!v2.observe(changed,100,100,region,null,1050,1,1).ready(),
            "Replayed frame cannot grant motion evidence");

        VisionNoveltyMemory mem=engine.noveltyMemory();
        check(mem!=null && mem.identify(0x112233,40)==0,
            "Untrained appearance remains UNKNOWN");
        mem.observe(0x112233,40,1000);
        mem.observe(0x112233,40,1001);
        mem.observe(0x112233,40,1002);
        check(mem.stableCount()>=1 && mem.identify(0x112233,40)!=0,
            "Novel appearance requires distinct frames");

        RealGameNeuralCritic critic=engine.neuralCritic(ArcadeProfile.PADDLE);
        check(critic!=null && !critic.ready() && critic.updates()==0,
            "New critic is unqualified for veto");
        String exported=critic.encode();
        RealGameNeuralCritic restored=RealGameNeuralCritic.decode(exported,ArcadeProfile.PADDLE);
        check(!restored.ready() && restored.updates()==0,
            "Critic checkpoint does not invent verified rewards");
        boolean schemaRejected=false;
        try { RealGameNeuralCritic.decode(exported,ArcadeProfile.TRAFFIC); }
        catch(IllegalArgumentException expected){schemaRejected=true;}
        check(schemaRejected,"Critic weights cannot cross incompatible action schemas");

''' +s[b:]
a=s.index("        JumperWeightShadow shadow =")
b=s.index("        TextRepetitionGuard rep =",a)
s=s[:a]+'''        JumperWeightShadow.Review shadow=JumperWeightShadow.review(null,0);
        check(!shadow.usable() && !shadow.inputAuthorized(),
            "Ari shadow cannot authorize input without visual evidence");
'''+s[b:]
p.write_text(s)
