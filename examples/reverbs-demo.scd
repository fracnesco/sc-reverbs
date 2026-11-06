// Reverbs - a walkthrough, one block at a time
//
// Evaluate each ( ... ) block in turn with the cursor inside it. This is
// an editor document, not a file to .load.

// ---- 1. a sound, and a Hall driven from its Ndef
(
s.boot;
)

(
Ndef(\keys, { Hall.ar(Blip.ar([220, 221], 8) * Decay2.kr(Impulse.kr(0.5), 0.01, 0.2) * 0.3, \hall) }).play;
~h = Hall(Ndef(\keys), \hall);
~h.gui;
)

// ---- 2. settings: each is a property, each moves the window
(
~h.preset = \cathedral;
~h.decay = 8; ~h.damp = 2500; ~h.preDelay = 0.06;
)

// ---- 3. every preset; evaluate one at a time
~h.preset = \default;
~h.preset = 'small hall';
~h.preset = 'concert hall';
~h.preset = \cathedral;
~h.preset = 'dark hall';
~h.preset = 'bright hall';
~h.preset = 'chorus wash';
~h.preset = \infinite;
~h.preset = 'drum room';
~h.preset = 'far away';

// ---- 4. the plate, with its hold
(
Ndef(\keys, { Plate.ar(Blip.ar([220, 221], 8) * Decay2.kr(Impulse.kr(0.5), 0.01, 0.2) * 0.3, \plate) });
~h.free;
~p = Plate(Ndef(\keys), \plate);
~p.gui;
~p.preset = 'vocal plate';
)

~p.hold = true;      // what is in it rings on
~p.hold = false;

// ---- 5. the spring, on a pluck
(
Ndef(\keys, { Coil.ar(Pluck.ar(WhiteNoise.ar(0.3), Impulse.kr(1), 0.02, 1 / 220, 1.5, 0.3) ! 2, \coil) });
~p.free;
~c = Coil(Ndef(\keys), \coil);
~c.gui;
~c.preset = 'surf tank';
)

~c.drip = 1; ~c.tension = 0.1;     // a loose spring, dripping
~c.springs = \one; ~c.decay = 0.5;

// ---- 6. the shimmer, on a pad
(
Ndef(\keys, { Shimmer.ar(LFTri.ar([110, 165, 220]).sum * 0.1 * LFPulse.kr(0.25, 0, 0.3).lag(0.05) ! 2, \shimmer) });
~c.free;
~m = Shimmer(Ndef(\keys), \shimmer);
~m.gui;
~m.preset = 'octave up';
)

~m.interval = 7; ~m.feedback = 0.8;
~m.preset = \cloud;

// ---- 7. settings kept, and carried
(
~m.saveAsPreset("demo");
~m.copySettings;
Shimmer.presetNames.postln;
Shimmer.removePreset("demo");
)

// ---- 8. the same effect with a node of its own, on the main out
(
~own = Shimmer.onBus(0, 2);
~own.pasteSettings;
~own.enabled = true;
~own.gui;
)

(
~own.free;
)

// ---- 9. in SuperDirtGUI, if ~sdg is running: inserts on orbit 0
(
~i = ~sdg.addInsert(0, \hall);
~i.gui;
~i.unit.preset = 'drum room';
)

(
~sdg.removeInsert(~i);
)

// ---- 10. tidy up
(
~m.free;
Ndef(\keys).clear;
)
