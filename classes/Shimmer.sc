// =====================================================================
// Shimmer - a reverb that climbs: a pitch shift in the feedback
//
// A dense tank, and what comes out of it goes round again an octave up (or
// whatever interval you choose), so a chord becomes a rising, glassy cloud.
// Eno and Lanois's trick, as on the U2 records. The shift is a granular
// pitch shifter; haze spreads its grains so the climb is a wash rather than
// a staircase.
//
//   Ndef(\pad, { Shimmer.ar(LFTri.ar([110, 165, 220]).sum * 0.1 ! 2, \shimmer) }).play;
//   m = Shimmer(Ndef(\pad), \shimmer);
//   m.gui;
//   m.interval = 12; m.feedback = 0.7; m.rise = 0.3;
//   m.preset = 'fifth up';
//
//   m = Shimmer.onBus(0, 2).enabled_(true);
//
// decay is the tank's time to 60 dB in seconds; feedback how much of the
// shifted tail goes round. Past about 0.9 with a long decay it keeps
// climbing until damp stops it: the low pass is what makes it come down.
// =====================================================================

Shimmer : FxParamUnit {
	*controlName { ^\shimmer }
	*hasTail { ^true }
	*displayClass { ^ShimmerView }
	*windowSize { ^Point(900, 560) }

	*viewKeys { ^(decay: \decay, pre: \preDelay, damp: \damp, dampLo: 1000, dampHi: 16000) }

	// what a Tidal pattern may move through SuperDirtGUI, event by event
	*tidalParams { ^#[\preDelay, \decay, \interval, \detune, \feedback, \haze, \damp, \lowCut, \width, \mix] }

	*params {
		// labels, sections and sizes are the window's (sc-fx-ui, 2026-11-09);
		// the order is the nodeMaps' - append, never reorder
		^[
			this.knob(\preDelay, 0, 0.2, \lin, 0.01, "s", "pre", "the gap before the tank speaks", box: \ms, section: \tank),
			this.knob(\decay, 0.5, 20, \exp, 4, "s", "decay", "the tank's time to fall 60 dB, before the feedback - or drag the tank's end",
				section: \tank, dec: 2, size: \big),
			this.knob(\interval, -24, 24, \lin, 12, "st", "pitch",
				"the shift, in semitones: 12 an octave up, 7 a fifth, -12 down - or drag the first step up and down",
				lag: 0, section: \climb, dec: 0, step: 1, size: \big)
				.put(\menuItems, [["octave up", 12], ["fifth up", 7], ["fourth up", 5], ["octave and a fifth", 19],
					["two octaves", 24], ["octave down", -12], ["unison (just the detune)", 0]]),
			this.knob(\detune, -50, 50, \lin, 0, "ct", "detune", "a few cents off the interval, so the climb beats", section: \climb, dec: 0,
				size: \small),
			this.knob(\feedback, 0, 1, \lin, 0.6, "", "feedback", "how much of the shifted tail goes round again - or the wheel on the display",
				box: \pct, section: \climb, size: \big),
			this.knob(\rise, 0.05, 1, \exp, 0.25, "s", "rise", "how long each round takes: how fast it climbs - or drag the first step along",
				box: \ms, section: \climb),

			this.knob(\damp, 1000, 16000, \exp, 5000, "Hz", "damp", "the low pass in the loop: where the climb stops - or drag the ceiling",
				section: \tone, dec: 0),
			this.knob(\lowCut, 60, 1000, \exp, 150, "Hz", "low cut", "the high pass in the loop: where a fall stops - or drag the floor",
				section: \tone, dec: 0),
			this.knob(\haze, 0, 1, \lin, 0.3, "", "haze", "how spread the shifter's grains are: a wash rather than steps", box: \pct, section: \tone),
			this.knob(\width, 0, 2, \lin, 1, "", "width", "how far apart the two sides are: 0 is mono", box: \pct, section: \output),
			this.mixParams(\output, 0.35)
		]
	}

	// ------------------------------------------------------------- the window

	*guiClass { ^FxDeviceGUI }
	*panelClass { ^FxDevicePanel }
	*identityColour { ^FxPalette.identity(\shimmer) }

	// the tank, the climb (what goes round, how far up, how fast), its tone
	*devicePages {
		^[
			\main -> [
				\tank,
				(title: "climb", keys: [\interval, \detune, \feedback, \rise], colour: \identity),
				\tone
			]
		]
	}

	// without feedback nothing goes round: the shift does nothing
	*isActive { |key, unit|
		^if (#[\interval, \detune, \rise, \haze].includes(key)) { unit.get(\feedback) > 0 } { true }
	}

	*presetList {
		^[
			\default, (),
			'octave up', (interval: 12, feedback: 0.7, decay: 5, damp: 6000),
			'fifth up', (interval: 7, feedback: 0.65, decay: 4, damp: 5000),
			'octave down', (interval: -12, feedback: 0.6, decay: 4, lowCut: 80, damp: 8000),
			'two octaves', (interval: 24, feedback: 0.6, decay: 3, rise: 0.4, damp: 9000),
			'slow climb', (interval: 12, feedback: 0.8, decay: 8, rise: 0.8, damp: 4000, mix: 0.5),
			'fast climb', (interval: 12, feedback: 0.75, decay: 3, rise: 0.08, damp: 7000),
			'detuned', (interval: 0, detune: 12, feedback: 0.8, decay: 6, haze: 0.6, mix: 0.4),
			'glass', (interval: 19, feedback: 0.7, decay: 4, damp: 12000, lowCut: 300, haze: 0.1),
			'cloud', (interval: 12, feedback: 0.85, decay: 10, rise: 0.5, haze: 1, damp: 3500, mix: 0.6)
		]
	}

	// ------------------------------------------------------------- the graph

	*process { |sig, p, ctx|
		var n = sig.size, in2 = ReverbKit.pair(sig);
		var ratio = 2 ** ((p[\interval] + (p[\detune] * 0.01)) / 12);
		var loop = FxLoop(2, 1.1);
		var pre, fb, shifted, tank, out;

		pre = DelayC.ar(in2, 0.2, p[\preDelay]);

		// what came out of the tank a round ago, shifted and filtered
		fb = loop.read(p[\rise]);
		fb = LPF.ar(HPF.ar(fb, p[\lowCut]), p[\damp]);
		shifted = PitchShift.ar(fb, 0.12, ratio, 0, p[\haze] * 0.06) * p[\feedback];
		shifted = (shifted * 2).tanh * 0.5;

		tank = ReverbKit.fdn(pre + shifted, 1.4, p[\decay], p[\damp], p[\lowCut], 0.4, 0, 1.5);
		loop.write(tank);

		out = FxKit.width(tank, p[\width]);
		^ReverbKit.spread(out, n)
	}
}
