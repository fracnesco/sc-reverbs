// =====================================================================
// CoilView - Coil's display: the springs, each round trip a chirp
//
// A lane per spring in the tank (one, two or three, as `springs` says),
// stacked like the springs of a real tank, time along and pitch up (100 Hz
// to 8 kHz). A click goes through a spring as a chirp - its lows first, its
// highs closing up after them - and comes round again every round trip
// (the length, times the spring's ratio, plus the twelve allpasses), each
// time more spread out (drip, and a looser tension, spread it more),
// fainter by the decay, and with less of its top under the tone and of its
// bottom under the low cut: the bright part of each chirp is what is left
// of it. The tone is the ceiling drawn across the lanes. Below them, the
// fall in dB to -60 at the decay, the highs (5 kHz) sooner, and the output
// itself after each hit.
//
//   drag the end along             the decay
//   drag the first round trip      the length (along)
//   drag the ceiling               the tone (up and down)
//   drag anywhere else             decay along, tone up and down
//   the wheel                      the drip; shift: fine; double click: the default
//
// The numbers are Coil's own (*process): the springs' ratios, a stage of
// 2.5 to 0.6 ms by tension, the allpass coefficient 0.2 to 0.88 by drip.
// =====================================================================

CoilView : ReverbView {

	// the springs' lengths against the first, as in Coil's *process
	*ratios { ^#[1, 1.37, 1.81] }
	*stage { |tension| ^tension.linexp(0, 1, 0.0025, 0.0006) }
	*coef { |drip| ^drip.linlin(0, 1, 0.2, 0.88) }

	// one spring's round trip: its share of the loop, then the chain of allpasses
	*lap { |length, ratio, tension| ^(length * ratio) + (this.stage(tension) * Coil.stages) }

	// how long a chirp lasts after one pass through the chain: half the longest
	// delay an allpass of that coefficient gives, times the stages
	*spread { |tension, drip|
		var k = this.coef(drip);
		^this.stage(tension) * Coil.stages * (1 + k) / (1 - k) * 0.5
	}

	// the lanes' pitch
	*loHz { ^100 }
	*hiHz { ^8000 }

	// where a filter met n times has taken 6 dB: the bright part of a chirp's n-th round
	*topAfter { |n, tone| ^if (n < 1) { this.hiHz } { tone * ((10 ** (0.6 / n)) - 1).max(1e-6).pow(0.25) } }
	*bottomAfter { |n, lowCut| ^if (n < 1) { this.loHz } { lowCut / ((10 ** (0.6 / n)) - 1).max(1e-6).pow(0.25) } }

	*words { ^#["one spring", "two springs", "three springs"] }

	prCount { ^(#[\one, \two, \three].indexOf(unit.get(\springs)) ? 1) + 1 }

	prStripRect { var r = this.prPlotRect; ^Rect(r.left, r.bottom - 44, r.width, 44) }

	prLanes { |count|
		var r = this.prPlotRect, gap = 6, h = (r.height - 52 - ((count - 1) * gap)) / count;
		^count.collect { |j| Rect(r.left, r.top + (j * (h + gap)), r.width, h) }
	}

	prYf { |f, r| ^f.explin(this.class.loHz, this.class.hiHz, r.bottom, r.top, \minmax) }

	// one round's chirp, from f0 to f1 of its sweep: lows first, the highs closing up
	prSweep { |r, span, t0, d, f0, f1, m = 10|
		var lo = this.class.loHz, hi = this.class.hiHz, u0, u1;
		u0 = (f0.max(lo) / lo).log / (hi / lo).log;
		u1 = (f1.min(hi) / lo).log / (hi / lo).log;
		if (u1 <= u0) { ^nil };
		^m.collect { |i|
			var u = u0 + ((u1 - u0) * i / (m - 1));
			this.prX(t0 + (d * (u ** 0.6)), r, span) @ this.prYf(lo * ((hi / lo) ** u), r)
		}
	}

	// one spring's rounds: (alpha:, all:, bright:) each, while they are heard
	prChirps { |r, span, lap, c|
		var out = [], n = 0, t0 = 0, level, d;
		while { (t0 < span) and: { n < 80 } } {
			level = -60 * t0 / c[\decay];
			if (level <= -54) { ^out };
			d = (n + 1) * c[\spread];
			out = out.add((
				alpha: ((level + 54) / 54).clip(0, 1),
				all: this.prSweep(r, span, t0, d, this.class.loHz, this.class.hiHz),
				bright: this.prSweep(r, span, t0, d, this.class.bottomAfter(n, c[\low]), this.class.topAfter(n, c[\tone]))
			));
			n = n + 1;
			t0 = n * lap;
		};
		^out
	}

	prCompute {
		var count = this.prCount, lanes = this.prLanes(count), sr = this.prStripRect, c, l0;
		var length = unit.get(\length), tension = unit.get(\tension), drip = unit.get(\drip);
		var decay = unit.get(\decay);
		c = (lanes: lanes, stripRect: sr, count: count, decay: decay, tone: unit.get(\tone), low: unit.get(\lowCut),
			spread: this.class.spread(tension, drip));
		c[\span] = this.class.niceSpan(decay * 1.03);
		c[\traceSpan] = c[\span];
		c[\laps] = count.collect { |j| this.class.lap(length, this.class.ratios[j], tension) };
		c[\chirps] = count.collect { |j| this.prChirps(lanes[j], c[\span], c[\laps][j], c) };
		c[\fall] = this.prFall(sr, c[\span], 0, 0, decay);
		c[\highs] = this.prFall(sr, c[\span], 0, 0, ReverbKit.rtAt(this.class.highHz, decay, c[\laps][0], c[\tone], c[\low]));
		l0 = lanes[0];
		c[\handles] = [
			(x: \decay, at: this.prX(decay, sr, c[\span]).min(sr.right) @ sr.bottom,
				hint: "where the springs are 60 dB down: drag along for the decay (shift: fine)"),
			(x: \length, at: this.prX(c[\laps][0], l0, c[\span]) @ this.prYf(this.class.loHz * 1.6, l0),
				hint: "the first round trip of spring 1: drag along for the length - the time round the spring"),
			(y: \tone, at: (l0.right - 14) @ this.prYf(c[\tone], l0), colour: this.class.tint(this.identity),
				hint: "the ceiling: the tone, the most a spring carries - drag up for a brighter tank, down for a darker one")
		];
		^c
	}

	prDrawFace {
		var c = cache, span = c[\span], col = this.identity, light = this.class.tint(col), sr = c[\stripRect];
		var all = this.prPlotRect, xEnd = this.prX(c[\decay], sr, span);
		this.prTimeAxis(all, span, all.bottom);
		c[\lanes].do { |r, j|
			var yT = this.prYf(c[\tone], r), yL = this.prYf(c[\low], r);
			// the pitch: 100 Hz, 1 kHz, 8 kHz
			[100, 1000, 8000].do { |f|
				var y = this.prYf(f, r);
				Pen.strokeColor = FxPalette.fade(FxPalette.waveDim, if (f == 1000) { 0.45 } { 0.7 });
				Pen.width = 1;
				Pen.line(r.left @ y, r.right @ y);
				Pen.stroke;
				if (j == 0 or: { r.height >= 60 }) {
					Pen.stringRightJustIn(case { f >= 1000 } { (f div: 1000).asString ++ "k" } { true } { f.asString },
						Rect(r.left - 34, (y - 6).clip(r.top - 2, r.bottom - 10), 31, 12), FxPalette.smallFont, FxPalette.dim);
				};
			};
			// what the tone and the low cut take: above the ceiling, under the floor
			this.prHatch(Rect(r.left, r.top, r.width, yT - r.top), FxPalette.fade(FxPalette.waveDim, 0.3));
			this.prHatch(Rect(r.left, yL, r.width, r.bottom - yL), FxPalette.fade(FxPalette.waveDim, 0.3));
			// the click going in
			Pen.strokeColor = FxPalette.fade(FxPalette.wave, 0.75);
			Pen.width = 2;
			Pen.line(r.left + 1 @ r.top, r.left + 1 @ r.bottom);
			Pen.stroke;
			// the rounds: each a chirp, faint where the filters have taken it
			c[\chirps][j].do { |ch|
				ch[\all] !? { |pts|
					Pen.strokeColor = FxPalette.fade(col, ch[\alpha] * 0.22);
					Pen.width = 1;
					this.prPolyline(pts);
					Pen.stroke;
				};
				ch[\bright] !? { |pts|
					Pen.strokeColor = FxPalette.fade(col, (ch[\alpha] * 0.95).max(0.08));
					Pen.width = 1.5;
					this.prPolyline(pts);
					Pen.stroke;
				};
			};
			// the ceiling
			Pen.strokeColor = FxPalette.fade(light, 0.8);
			Pen.width = 1;
			Pen.line(r.left @ yT, r.right @ yT);
			Pen.stroke;
			this.prTag("spring %  ·  %".format(j + 1, FxPalette.format(c[\laps][j], "s")), r.right - 2, r.bottom - 14,
				FxPalette.fade(col, 0.95), \right);
		};
		this.prTag("tone " ++ this.prFmt(\tone), c[\lanes][0].right - 24, this.prYf(c[\tone], c[\lanes][0]) + 2, light, \right);
		// the end of the decay, through the lanes and the strip
		if (xEnd <= (sr.right + 0.5)) {
			Pen.push;
			Pen.strokeColor = FxPalette.fade(col, 0.55);
			Pen.width = 1;
			Pen.lineDash = FloatArray[3, 3];
			Pen.line(xEnd @ all.top, xEnd @ sr.bottom);
			Pen.stroke;
			Pen.pop;
		};
		this.prTag("decay " ++ this.prFmt(\decay), xEnd.min(sr.right) - 4, sr.top - 1, col, \right);
		// the strip: the fall in dB, the highs, the output
		Pen.strokeColor = FxPalette.fade(FxPalette.waveDim, 0.5);
		Pen.width = 1;
		Pen.line(sr.left @ this.prY(0, sr), sr.right @ this.prY(0, sr));
		Pen.stroke;
		Pen.strokeColor = FxPalette.fade(FxPalette.waveDim, 0.8);
		Pen.line(sr.left @ sr.bottom, sr.right @ sr.bottom);
		Pen.stroke;
		Pen.stringRightJustIn("0 dB", Rect(sr.left - 34, this.prY(0, sr) - 6, 31, 12), FxPalette.smallFont, FxPalette.dim);
		Pen.stringRightJustIn("-60", Rect(sr.left - 34, sr.bottom - 10, 31, 12), FxPalette.smallFont, FxPalette.dim);
		this.prFilled(c[\fall], sr, col, 0.3, 1);
		this.prLine(c[\highs], light, 1.5);
		this.prTag("5k", c[\highs][1].x.min(sr.right - 24) + 4, (c[\highs][1].y - 15).max(sr.top + 2), light);
		this.prDrawTrace(sr, span);
	}

	caption {
		^"%  ·  round trips %  ·  decay %  ·  tone %  ·  drip %".format(
			this.class.words[this.prCount - 1],
			cache !? { cache[\laps].collect { |l| FxPalette.format(l, "s") }.join(", ") } ? "",
			this.prFmt(\decay), this.prFmt(\tone), this.prFmt(\drip))
	}

	prFreeKeys { |x, y| ^[\decay, \tone] }

	prWheelKey { |x, y| ^\drip }

	prFreeHint { |x, y|
		^"the springs: drag along for the decay, up and down for the tone (shift: fine); the wheel: drip; double click: the defaults"
	}
}
