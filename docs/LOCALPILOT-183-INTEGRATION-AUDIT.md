# Issue 183: integration audit

Base: PR182 7ff1b4c35ce1b10cf4330db4cd7f70652dac6e63. Main observed: 32838fde8c2432dd060f3cf2923d4dd1910c9553.

| Link | Actual implementation | Status |
|---|---|---|
| UI → environment | NesEmulatorActivity owns one JSNES WebView | Working NES; generic adapter not extracted |
| Environment → observation | epoch, frame fingerprint, PNG, built-in ROM RAM | Working; ordinary ROM semantics absent |
| Observation → model | Application NesModelHost/HarnessModelRuntime; StudentPolicyNet | Working for supported built-in task |
| Proposal → gate → input | HarnessCoordinator permits, TTL, epoch, STOP; retroAiStep | Working bounded NES steps |
| Input → verifier | ACK plus fresh frame; task-specific ROM RAM | Working task scope; pixels alone not reward |
| Verifier → experience | ConsoleExperienceMemory and VerifiedPpoReplayStore | Working distinct evidence streams |
| Experience → learning | LearningCoordinator, PPO/GAE, candidate weights | Real candidate updates; pending candidate blocks further updates |
| Candidate → evaluation → promotion | Host JSNES evaluator; Android promotion absent | Incomplete, never silently activate |
| External games | PilotService, ActionGate, InputPermit | Existing separate control path; universal integration unproven |
| Video → learning | No integrated video adapter | Absent; defer until stable core |

First incremental UI change: controller status and validated replay summary appear inside the existing NES Activity. Read-only storage work runs off the UI thread; no new controller/runtime or policy activation. Import remains a separate screen and is a subsequent step. All-button acceptance, device acceptance, universal environment adapters and automatic candidate promotion are not completed by this patch.

Evidence from the base is not acceptance of a new commit. Keep BUILD/UI/INFERENCE/TRANSITION/TRAINING/DEVICE labels separate. Release/signing/main merge remain on hold.
