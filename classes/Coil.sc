// =====================================================================
// Coil - a spring reverb: the chirp, the drip, the twang
//
// A spring is dispersive: the frequencies travel along it at different
// speeds, so a click comes out as a chirp, and the echoes of that chirp,
// round and round, are the "boing". Here that is a chain of twelve
// allpasses at one short time, in a loop as long as the spring, through a
// low pass (a spring carries nothing above a few kHz) and a little
// saturation. Up to three springs of different lengths share the loop.
//
//   Ndef(\gtr, { Coil.ar(Pluck.ar(WhiteNoise.ar(0.3), Impulse.kr(1), 0.02, 1 / 220, 1.5, 0.3) ! 2, \coil) }).play;
//   c = Coil(Ndef(\gtr), \coil);
//   c.gui;
//   c.drip = 0.9; c.tension = 0.3; c.springs = \three;
//   c.preset = 'surf tank';
//
//   c = Coil.onBus(0, 2).enabled_(true);
//
// decay is the time to 60 dB in seconds. tension sets how short each
// stage is (a tight spring chirps higher and faster); drip how strongly
// it disperses.
// =====================================================================

Coil : FxParamUnit {
	classvar <stages = 12;

	*controlName { ^\coil }
	*hasTail { ^true }
	*displayClass { ^CoilView }
	*windowSize { ^Point(900, 560) }

	*viewKeys { ^(decay: \decay, pre: nil, damp: \tone, dampLo: 800, dampHi: 8000) }

	// what a Tidal pattern may move through SuperDirtGUI, event by event
	*tidalParams { ^#[\decay, \tension, \drip, \tone, \lowCut, \drive, \width, \mix] }

	*params {
		// labels, sections and sizes are the window's (sc-fx-ui, 2026-11-09);
		// the order is the nodeMaps' - append, never reorder
		^[
			this.knob(\length, 0.02, 0.12, \exp, 0.045, "s", "length",
				"how long the spring is: the time round it, before the chirp comes back - or drag the first round trip",
				box: \ms, section: \spring),
			this.knob(\decay, 0.2, 8, \exp, 2, "s", "decay", "how long the tail takes to fall 60 dB - or drag where the springs end",
				section: \spring, dec: 2, size: \big),
			this.knob(\tension, 0, 1, \lin, 0.5, "", "tension", "how tight the spring is: tighter chirps higher and faster",
				box: \pct, section: \spring),
			this.knob(\drip, 0, 1, \lin, 0.6, "", "drip", "how strongly it disperses: the drip of a click, the boing of a hit",
				box: \pct, section: \spring, size: \big),
			this.menu(\springs, #[\one, \two, \three], \two, "springs", "how many springs, of different lengths, in the tank", \tank),

			this.knob(\tone, 800, 8000, \exp, 3500, "Hz", "tone", "the low pass in the loop: a spring carries nothing bright - or drag the ceiling",
				section: \tone, dec: 0),
			this.knob(\lowCut, 40, 400, \exp, 120, "Hz", "low cut", "the high pass in the loop", section: \tone, dec: 0),
			this.knob(\drive, 0, 1, \lin, 0.1, "", "drive", "saturation in the loop: the tank's rattle on a hit", box: \pct, section: \tank),
			this.knob(\width, 0, 2, \lin, 1, "", "width", "how far apart the two springs' sides are: 0 is mono", box: \pct, section: \output),
			this.mixParams(\output, 0.35)
		]
	}

	// ------------------------------------------------------------- the window

	*guiClass { ^FxDeviceGUI }
	*panelClass { ^FxDevicePanel }
	*identityColour { ^FxPalette.identity(\coil) }

	// the tank (how many springs, how hard they are driven), the spring, its tone
	*devicePages {
		^[
			\main -> [
				\tank,
				(title: "spring", keys: [\length, \tension, \drip, \decay], colour: \identity),
				\tone
			]
		]
	}

	*presetList {
		^[
			\default, (),
			'surf tank', (length: 0.055, decay: 3, tension: 0.35, drip: 0.9, springs: \three, tone: 3000, drive: 0.3, mix: 0.5),
			'guitar amp', (length: 0.04, decay: 1.8, tension: 0.5, drip: 0.6, springs: \two, tone: 4000, drive: 0.15),
			'short spring', (length: 0.025, decay: 0.8, tension: 0.7, drip: 0.5, springs: \one, tone: 5000, mix: 0.3),
			'long spring', (length: 0.1, decay: 4, tension: 0.2, drip: 0.8, springs: \three, tone: 2500, mix: 0.45),
			'dub tank', (length: 0.08, decay: 5, tension: 0.4, drip: 0.95, springs: \two, tone: 2000, lowCut: 200, drive: 0.5, mix: 0.6),
			'bright spring', (length: 0.035, decay: 1.5, tension: 0.8, drip: 0.4, tone: 8000, lowCut: 300),
			'loose', (length: 0.07, decay: 2.5, tension: 0.05, drip: 1, springs: \three, tone: 3000, mix: 0.4),
			'kicked', (length: 0.06, decay: 6, tension: 0.3, drip: 0.9, springs: \three, tone: 2800, drive: 0.8, mix: 0.7),
			'dry twang', (length: 0.03, decay: 0.5, tension: 0.6, drip: 0.7, springs: \one, tone: 4500, mix: 0.2)
		]
	}

	// ------------------------------------------------------------- the graph

	*process { |sig, p, ctx|
		var n = sig.size, in2 = ReverbKit.pair(sig);
		var stage = p[\tension].linexp(0, 1, 0.0025, 0.0006);
		var k = p[\drip].linlin(0, 1, 0.2, 0.88);
		var count = p[\springs];		// the menu's index: 0, 1, 2
		var ratios = #[1, 1.37, 1.81];
		var chain = stage * stages;
		var gainIn = 1 + (p[\drive] * 8);
		var springs, out;

		// each spring a loop of its own: the input and what came round, filtered and
		// saturated (the small signal gain taken back out), through the chain of
		// allpasses - the dispersion - and back in
		springs = ratios.collect { |r, j|
			var loop = FxLoop(2, 0.25), length = p[\length] * r;
			var fb = loop.read(length) * ReverbKit.lapGain(length + chain, p[\decay]);
			var x;
			fb = LPF.ar(HPF.ar(fb, p[\lowCut]), p[\tone]);
			fb = (fb * gainIn).tanh / gainIn;
			x = (in2 + fb).collect { |ch, i|
				var d = stage * (1 + (i * 0.013) + (j * 0.021));		// sides and springs a hair apart
				stages.do { ch = AllpassC.ar(ch, 0.003, d, ReverbKit.apTime(d, k)) };
				ch
			};
			loop.write(x);
			x
		};

		// the longer springs only when asked, the sum kept at one level
		out = springs[0]
			+ (springs[1] * (count >= 1))
			+ (springs[2] * (count >= 2));
		out = out / (1 + count).sqrt;

		out = FxKit.width(out * 0.7, p[\width]);
		^ReverbKit.spread(out, n)
	}
}
