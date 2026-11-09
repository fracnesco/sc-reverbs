// =====================================================================
// Plate - a plate: dense at once, bright, with a hold
//
// A feedback delay network of four short lines, Hadamard mixed, so the
// echoes are dense from the first moment, as on a steel plate; the input
// diffused on its way in. hold freezes what is in it: the lines go round
// at unity, the filters step aside and nothing more comes in.
//
//   Ndef(\keys, { Plate.ar(Blip.ar([220, 221], 8) * Decay2.kr(Impulse.kr(0.5), 0.01, 0.2) * 0.3, \plate) }).play;
//   p = Plate(Ndef(\keys), \plate);
//   p.gui;
//   p.decay = 3; p.damp = 12000;
//   p.hold = true;                       // and later false
//
//   p = Plate.onBus(0, 2).enabled_(true);
//
// decay is the time to 60 dB in seconds. A held plate never goes quiet, so
// an insert with hold on keeps its orbit awake.
// =====================================================================

Plate : FxParamUnit {
	*controlName { ^\plate }
	*hasTail { ^true }
	*displayClass { ^PlateView }
	*windowSize { ^Point(900, 560) }

	*viewKeys { ^(decay: \decay, pre: \preDelay, damp: \damp, dampLo: 1000, dampHi: 20000) }

	// parameters renamed on 2026-11-08 so each is a free Tidal word (old -> new):
	// old presets, sessions and code still reach them
	*renamed { ^(mod: \modDepth) }

	// what a Tidal pattern may move through SuperDirtGUI, event by event
	*tidalParams { ^#[\preDelay, \decay, \hold, \damp, \lowCut, \modDepth, \width, \mix] }

	*params {
		// labels, sections, sizes and units are the window's (sc-fx-ui, 2026-11-09);
		// the order is the nodeMaps' - append, never reorder
		^[
			this.knob(\preDelay, 0, 0.1, \lin, 0.005, "s", "pre", "the gap before the plate speaks - or drag its edge in the display",
				box: \ms, section: \input, size: \small),
			this.knob(\room, 0.5, 2, \exp, 1, "x", "size", "how big the plate is: the lines, scaled", section: \plate, dec: 2, size: \small),
			this.knob(\decay, 0.3, 30, \exp, 1.8, "s", "decay", "how long the tail takes to fall 60 dB - or drag the tail's end",
				section: \plate, dec: 2, size: \big)
				.put(\menuItems, [["short  0.5 s", 0.5], ["snare  1.1 s", 1.1], ["vocal  2.4 s", 2.4], ["long  8 s", 8], ["endless  30 s", 30]]),
			this.knob(\diffusion, 0, 1, \lin, 0.6, "", "diffusion", "how smeared the input is on its way in", box: \pct,
				section: \input, size: \small),
			this.toggle(\hold, false, "hold",
				"freeze what is in the plate: it rings on at unity, nothing more goes in - click to hold, press and keep pressing to hold only while you do",
				\plate).put(\size, \big),

			this.knob(\damp, 1000, 20000, \exp, 9000, "Hz", "damp", "the low pass in the loop: the high end dies first - or drag the 5k line",
				section: \tone, dec: 0, size: \big),
			this.knob(\lowCut, 20, 500, \exp, 100, "Hz", "low cut", "the high pass in the loop", section: \tone, dec: 0),
			this.knob(\modDepth, 0, 1, \lin, 0.2, "", "mod", "how much the lines move: takes the metal out of a long tail", box: \pct, section: \tone),
			this.knob(\width, 0, 2, \lin, 1, "", "width", "how far apart the two sides are: 0 is mono", box: \pct, section: \output),
			this.mixParams(\output, 0.3)
		]
	}

	// ------------------------------------------------------------- the window

	*guiClass { ^FxDeviceGUI }
	*panelClass { ^FxDevicePanel }
	*identityColour { ^FxPalette.identity(\plate) }

	// the way in, the plate with its hold beside the decay, its tone
	*devicePages {
		^[
			\main -> [
				\input,
				(title: "plate", keys: [\room, \decay, \hold], colour: \identity),
				\tone
			]
		]
	}

	// held, nothing goes in and the loop runs at unity with the filters out
	*isActive { |key, unit|
		^if (unit.get(\hold) == true) { #[\decay, \damp, \lowCut, \diffusion, \preDelay].includes(key).not } { true }
	}

	*presetList {
		^[
			\default, (),
			'vocal plate', (room: 1.2, decay: 2.4, preDelay: 0.03, damp: 7000, lowCut: 150),
			'snare plate', (room: 0.7, decay: 1.1, preDelay: 0.01, damp: 12000, lowCut: 200, mix: 0.35),
			'bright plate', (room: 1, decay: 2, damp: 18000, lowCut: 120),
			'dark plate', (room: 1.4, decay: 3, damp: 2500, lowCut: 80),
			'short plate', (room: 0.6, decay: 0.5, damp: 10000, mix: 0.25),
			'long plate', (room: 1.8, decay: 8, damp: 5000, modDepth: 0.5, mix: 0.4),
			'tiny box', (room: 0.5, decay: 0.3, preDelay: 0, damp: 6000, lowCut: 300, mix: 0.3),
			\frozen, (hold: true, mix: 0.6),
			'wide wash', (room: 1.6, decay: 5, damp: 8000, modDepth: 0.8, width: 1.8, mix: 0.5)
		]
	}

	// ------------------------------------------------------------- the graph

	*process { |sig, p, ctx|
		var n = sig.size, in2 = ReverbKit.pair(sig);
		var room = p[\room], k = p[\diffusion] * 0.7, hold = Lag.kr(p[\hold], 0.05);
		var pre, out;

		pre = DelayC.ar(in2, 0.1, p[\preDelay]);
		pre = pre.collect { |x, i|
			var times = if (i == 0) { #[0.0043, 0.0071] } { #[0.0051, 0.0067] };
			times.do { |t|
				var d = t * room;
				x = AllpassC.ar(x, 0.02, d, ReverbKit.apTime(d, k.max(0.05)));
			};
			x
		};
		out = ReverbKit.fdn(pre, room, p[\decay], p[\damp], p[\lowCut], p[\modDepth], hold, 2);
		out = FxKit.width(out, p[\width]);
		^ReverbKit.spread(out, n)
	}
}
