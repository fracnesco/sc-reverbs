// =====================================================================
// Hall - a concert hall: slow to build, long, moving
//
// A Dattorro tank: the input diffused through four allpasses, then two
// branches in a figure of eight, each a modulated allpass, a delay, the
// damping, another allpass and a second delay, feeding the other. Early
// reflections on the side, a pre-delay in front.
//
//   Ndef(\keys, { Hall.ar(Blip.ar([220, 221], 8) * Decay2.kr(Impulse.kr(0.5), 0.01, 0.2) * 0.3, \hall) }).play;
//   h = Hall(Ndef(\keys), \hall);
//   h.gui;
//   h.decay = 6; h.room = 1.6; h.preDelay = 0.04;
//   h.preset = \cathedral;
//
//   h = Hall.onBus(0, 2).enabled_(true);
//
// decay is the time to 60 dB in seconds, whatever the room's size: the loop
// gain is worked out from the lap. damp is a low pass in the loop, so the
// high end dies first, as in a real room; lowCut a high pass, so the bottom
// does not pile up.
// =====================================================================

Hall : FxParamUnit {
	*controlName { ^\hall }
	*hasTail { ^true }
	*displayClass { ^HallView }
	*windowSize { ^Point(900, 560) }

	// what ReverbView draws and drags: the keys of the decay, the pre-delay
	// and the damping, with the damping's range
	*viewKeys { ^(decay: \decay, pre: \preDelay, damp: \damp, dampLo: 500, dampHi: 20000) }

	// what a Tidal pattern may move through SuperDirtGUI, event by event
	*tidalParams { ^#[\preDelay, \decay, \early, \damp, \lowCut, \modDepth, \width, \mix] }

	*params {
		// labels, sections, sizes and units are the window's (sc-fx-ui, 2026-11-09);
		// the order is the nodeMaps' - append, never reorder
		^[
			this.knob(\preDelay, 0, 0.25, \lin, 0.02, "s", "pre", "the gap before the reverb starts - or drag its edge in the display",
				box: \ms, section: \onset),
			this.knob(\room, 0.3, 3, \exp, 1, "x", "size", "how big the hall is: the tank's delays, scaled - a bigger one swells later",
				section: \space, dec: 2),
			this.knob(\decay, 0.2, 60, \exp, 2.5, "s", "decay", "how long the tail takes to fall 60 dB - or drag the tail's end",
				section: \space, dec: 2, size: \big)
				.put(\menuItems, [["room  0.8 s", 0.8], ["chamber  1.5 s", 1.5], ["hall  2.5 s", 2.5], ["church  5 s", 5], ["cathedral  9 s", 9]]),
			this.knob(\diffusion, 0, 1, \lin, 0.7, "", "diffusion", "how smeared the input is on its way in: low is echoey, high is smooth",
				box: \pct, section: \onset),
			this.knob(\early, 0, 1, \lin, 0.4, "", "early", "how loud the first reflections are, off the walls, before the tail",
				box: \pct, section: \onset),

			this.knob(\damp, 500, 20000, \exp, 6000, "Hz", "damp", "the low pass in the loop: the high end dies first", section: \tone, dec: 0),
			this.knob(\lowCut, 20, 1000, \exp, 60, "Hz", "low cut", "the high pass in the loop: keeps the bottom from piling up", section: \tone, dec: 0),
			this.knob(\modDepth, 0, 1, \lin, 0.3, "", "mod", "how much the tank's allpasses move: thickens the tail, chorus at the top",
				box: \pct, section: \tone),
			this.knob(\modRate, 0.1, 3, \exp, 0.7, "Hz", "rate", "how fast they move", section: \tone),
			this.knob(\width, 0, 2, \lin, 1, "", "width", "how far apart the two sides are: 0 is mono", box: \pct, section: \output),
			this.mixParams(\output, 0.3)
		]
	}

	// ------------------------------------------------------------- the window

	*guiClass { ^FxDeviceGUI }
	*panelClass { ^FxDevicePanel }
	*identityColour { ^FxPalette.identity(\hall) }

	// the onset (the gap, the walls, the swell), the hall itself, its tone
	*devicePages {
		^[
			\main -> [
				(title: "onset", keys: [\preDelay, \early, \diffusion]),
				(title: "space", keys: [\room, \decay], colour: \identity),
				\tone
			]
		]
	}

	// the rate moves nothing while nothing moves
	*isActive { |key, unit| ^if (key == \modRate) { unit.get(\modDepth) > 0 } { true } }

	*presetList {
		^[
			\default, (),
			'small hall', (room: 0.6, decay: 1.2, preDelay: 0.01, early: 0.5, damp: 8000),
			'concert hall', (room: 1.4, decay: 3.2, preDelay: 0.03, early: 0.35, damp: 5000),
			\cathedral, (room: 2.4, decay: 9, preDelay: 0.06, early: 0.2, damp: 3500, lowCut: 40, modDepth: 0.4),
			'dark hall', (room: 1.2, decay: 4, damp: 1800, lowCut: 80, early: 0.3),
			'bright hall', (room: 1, decay: 2.2, damp: 16000, lowCut: 120, early: 0.5),
			'chorus wash', (room: 1.6, decay: 5, modDepth: 1, modRate: 1.5, damp: 7000, mix: 0.45),
			'infinite', (room: 2, decay: 60, damp: 4000, lowCut: 100, early: 0, mix: 0.5),
			'drum room', (room: 0.4, decay: 0.6, preDelay: 0.005, early: 0.8, damp: 9000, lowCut: 150, mix: 0.25),
			'far away', (room: 1.8, decay: 6, preDelay: 0.08, early: 0.1, damp: 2500, mix: 0.7)
		]
	}

	// ------------------------------------------------------------- the graph

	*process { |sig, p, ctx|
		var n = sig.size, in2 = ReverbKit.pair(sig);
		var room = p[\room], k = p[\diffusion] * 0.75;
		var pre, diffused, early, loop, fb, tank, lap, g, lfo, out;
		var inAp = #[0.0047, 0.0036, 0.0127, 0.0093];
		var tapsL = #[0.0112, 0.0231, 0.0298, 0.0414, 0.0527], tapsR = #[0.0147, 0.0193, 0.0346, 0.0389, 0.0561];
		var gains = #[0.9, 0.7, 0.55, 0.4, 0.3];

		pre = DelayC.ar(in2, 0.25, p[\preDelay]);

		// the first reflections, a few taps a side
		early = [
			tapsL.collect { |t, i| DelayC.ar(pre[0], 0.17, t * room) * gains[i] }.sum,
			tapsR.collect { |t, i| DelayC.ar(pre[1], 0.17, t * room) * gains[i] }.sum
		] * 0.25;

		// the input into the tank: mono, diffused
		diffused = (pre[0] + pre[1]) * 0.5;
		inAp.do { |t|
			var d = t * room;
			diffused = AllpassC.ar(diffused, 0.04, d, ReverbKit.apTime(d, k.max(0.05)));
		};

		// the tank: two branches, each feeding the other through the loop
		loop = FxLoop(2, 0.5);
		lap = (#[0.0227, 0.1496, 0.0604, 0.1250].sum + #[0.0304, 0.1250, 0.0892, 0.1496].sum) * 0.5 * room;
		g = ReverbKit.lapGain(lap, p[\decay]);
		fb = loop.read([0.1250, 0.1496] * room);
		lfo = [SinOsc.kr(p[\modRate]), SinOsc.kr(p[\modRate] * 0.83, 2)] * p[\modDepth] * 0.0012;
		tank = [
			[fb[1], 0.0227, 0.1496, 0.0604],
			[fb[0], 0.0304, 0.1250, 0.0892]
		].collect { |spec, i|
			var x = diffused + (spec[0] * g);
			var d1 = (spec[1] * room) + lfo[i], d2 = spec[3] * room;
			x = AllpassC.ar(x, 0.1, d1, ReverbKit.apTime(d1, 0.7));
			x = DelayC.ar(x, 0.5, spec[2] * room);
			x = LPF.ar(HPF.ar(x, p[\lowCut]), p[\damp]);
			x = AllpassC.ar(x, 0.3, d2, ReverbKit.apTime(d2, 0.5));
			x
		};
		loop.write(tank);

		out = [
			tank[0] + (fb[1] * 0.6),
			tank[1] + (fb[0] * 0.6)
		] * 1.5;
		out = out + (early * p[\early]);
		out = FxKit.width(out, p[\width]);
		^ReverbKit.spread(out, n)
	}
}
