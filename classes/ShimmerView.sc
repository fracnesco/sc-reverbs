// =====================================================================
// ShimmerView - Shimmer's display: the climb
//
// Pitch against time, in semitones from what goes in (a c5, Tidal's note
// 0, for the filters' sake). The hit is a blue mark at 0; the tank rings it
// there, and each round of the loop - every rise, plus the shifter's and
// the tank's own time, about 0.22 s, measured - comes back an interval
// higher (or lower), as bright as what survives the feedback, the decay
// and the filters: a staircase climbing and fading. The damping is a
// ceiling (hatched above) and the low cut a floor; past about 90 % of
// feedback with a long decay the climb no longer fades and runs on until
// the ceiling stops it, and the readout says so. Haze thickens the steps
// into a wash. Under it, the output itself after each hit.
//
//   drag the first step        along: the rise; up and down: the interval
//   drag the ceiling           the damping (up and down)
//   drag the floor             the low cut
//   drag the tank's end        the decay (along)
//   drag anywhere else         decay along, interval up and down
//   the wheel                  the feedback; shift: fine; double click: the default
//
// Each step's level is worked out round by round (feedback in dB, the
// tank's fall over a round, the loop's filters at the pitch it had, then
// how long the tank rings at the new one): within about 5 dB of renders of
// the octaves of a 440 Hz burst (2026-11-09).
// =====================================================================

ShimmerView : ReverbView {

	*refHz { ^261.6256 }
	*loSt { ^-36 }
	*hiSt { ^72 }

	// a round of the loop: the rise, then the shifter's window and the tank's build
	*step { |rise| ^rise + 0.22 }

	// the tank: ReverbKit.fdn at room 1.4
	*tankLap { ^0.0578 * 1.4 }

	*intervalNames {
		^(0: "unison", 1: "semitone", 2: "tone", 3: "minor third", 4: "major third", 5: "fourth", 6: "tritone",
			7: "fifth", 8: "minor sixth", 9: "major sixth", 10: "minor seventh", 11: "major seventh", 12: "octave",
			17: "octave and a fourth", 19: "octave and a fifth", 24: "two octaves")
	}

	// "+12 st octave up", "-7 st fifth down", "+13 st"
	*intervalText { |st|
		var n = st.round.asInteger, a = n.abs, name = this.intervalNames[a];
		var sign = case { n > 0 } { "+" } { n < 0 } { "-" } { true } { "" };
		var way = case { n > 0 } { " up" } { n < 0 } { " down" } { true } { "" };
		^sign ++ a.asString ++ " st" ++ (name !? { |w| " " ++ w ++ way } ? "")
	}

	prPitchRect { var r = this.prPlotRect; ^Rect(r.left, r.top, r.width, (r.height - 52).max(20)) }
	prStripRect { var r = this.prPlotRect; ^Rect(r.left, r.bottom - 44, r.width, 44) }

	prYst { |st, r| ^st.linlin(this.class.loSt, this.class.hiSt, r.bottom, r.top, \minmax) }

	prStOf { |hz| ^12 * (hz / this.class.refHz).log2 }

	// the steps: the tank's own ring at 0, then one per round while it is heard
	prRungs { |c|
		var rungs, fbDb = c[\fb].max(1e-5).ampdb, lossStep = 60 * c[\step] / c[\decay];
		var level = 0, n = 0, st = 0, rt, shown;
		rt = ReverbKit.rtAt(this.class.refHz, c[\decay], this.class.tankLap, c[\damp], c[\low]);
		rungs = [(t: c[\pre], st: 0, db: 0, rt: rt)];
		if (c[\fb] <= 0.0001) { ^rungs };
		while { n < 24 } {
			// out of the tank, through the loop's filters at the pitch it had, shifted, scaled
			level = (level + fbDb - lossStep
				- ReverbKit.filterLoss(this.class.refHz * (2 ** (st / 12)), c[\damp], c[\low])).min(0);
			n = n + 1;
			st = n * c[\shift];
			rt = ReverbKit.rtAt(this.class.refHz * (2 ** (st / 12)), c[\decay], this.class.tankLap, c[\damp], c[\low]);
			shown = (level + (10 * (rt / c[\decay]).max(1e-6).log10)).min(0);
			if ((shown < -54) or: { st < this.class.loSt } or: { st > this.class.hiSt }) { ^rungs };
			rungs = rungs.add((t: c[\pre] + (n * c[\step]), st: st, db: shown, rt: rt));
		};
		^rungs
	}

	// the sum of the steps' levels along time: the climb as a loudness
	prLevelPoints { |r, span, rungs|
		var n = (r.width / 3).asInteger.max(2), pts = [];
		(n + 1).do { |i|
			var t = i / n * span, e = 0;
			rungs.do { |g| if (t >= g[\t]) { e = e + (10 ** ((g[\db] - (60 * (t - g[\t]) / g[\rt].max(1e-3))) / 10)) } };
			if (e > 1e-6) { pts = pts.add(this.prX(t, r, span) @ this.prY(10 * e.log10.min(0), r)) };
		};
		^pts
	}

	prCompute {
		var pr = this.prPitchRect, sr = this.prStripRect, c, rungs, tEnd, first;
		c = (pitchRect: pr, stripRect: sr, pre: unit.get(\preDelay), decay: unit.get(\decay),
			fb: unit.get(\feedback), damp: unit.get(\damp), low: unit.get(\lowCut), haze: unit.get(\haze),
			shift: unit.get(\interval) + (unit.get(\detune) * 0.01), step: this.class.step(unit.get(\rise)));
		rungs = this.prRungs(c);
		c[\rungs] = rungs;
		tEnd = rungs.collect { |g| g[\t] + (g[\rt] * ((g[\db] + 60) / 60).max(0)) }.maxItem;
		c[\span] = this.class.niceSpan((tEnd.max(c[\pre] + rungs[0][\rt]) * 1.03).min(60));
		c[\traceSpan] = c[\span];
		// it hardly fades from one round to the next: it reaches the ceiling still loud
		c[\runaway] = (c[\fb] > 0.0001) and: { (c[\fb].ampdb - (60 * c[\step] / c[\decay])) > -3 };
		// what it all adds up to, in dB, for the strip under the live output
		c[\level] = this.prLevelPoints(sr, c[\span], rungs);
		c[\ceiling] = this.prStOf(c[\damp]);
		c[\floor] = this.prStOf(c[\low]);
		c[\handles] = [
			(x: \decay, at: this.prX(c[\pre] + rungs[0][\rt], pr, c[\span]).min(pr.right) @ this.prYst(0, pr),
				hint: "the tank's decay: how long each step rings - drag along (shift: fine)"),
			(y: \damp, at: (pr.right - 14) @ this.prYst(c[\ceiling], pr),
				hint: "the ceiling: the damping, where the climb stops - drag up and down"),
			(y: \lowCut, at: (pr.right - 34) @ this.prYst(c[\floor], pr),
				hint: "the floor: the low cut, where a fall stops - drag up and down")
		];
		first = rungs[1];
		first !? { |g|
			c[\handles] = c[\handles].add((x: \rise, y: \interval, at: this.prX(g[\t], pr, c[\span]) @ this.prYst(g[\st], pr),
				hint: "the first step up: drag along for the rise (how long a round takes), up and down for the interval"));
		};
		^c
	}

	prPitchGrid { |r|
		var lo = (this.class.loSt / 12).ceil.asInteger, hi = (this.class.hiSt / 12).floor.asInteger;
		Pen.width = 1;
		(lo..hi).do { |o|
			var st = o * 12, y = this.prYst(st, r);
			Pen.strokeColor = FxPalette.fade(FxPalette.waveDim, if (st == 0) { 0.9 } { 0.4 });
			Pen.line(r.left @ y, r.right @ y);
			Pen.stroke;
			Pen.stringRightJustIn(case { st == 0 } { "0 st" } { st > 0 } { "+" ++ st } { true } { st.asString },
				Rect(r.left - 34, y - 6, 31, 12), FxPalette.smallFont, FxPalette.dim);
		};
	}

	prDrawFace {
		var c = cache, pr = c[\pitchRect], sr = c[\stripRect], span = c[\span], col = this.identity;
		var yC = this.prYst(c[\ceiling], pr), yF = this.prYst(c[\floor], pr), thick = 4 + (c[\haze] * 7);
		var both = Rect(pr.left, pr.top, pr.width, sr.bottom - pr.top);
		this.prTimeAxis(both, span, sr.bottom);
		this.prPitchGrid(pr);
		// the ceiling and the floor: what the filters take away
		this.prHatch(Rect(pr.left, pr.top, pr.width, yC - pr.top), FxPalette.fade(col, if (c[\runaway]) { 0.45 } { 0.22 }));
		this.prHatch(Rect(pr.left, yF, pr.width, pr.bottom - yF), FxPalette.fade(FxPalette.waveDim, 0.45));
		Pen.strokeColor = FxPalette.fade(col, 0.8);
		Pen.width = 1;
		Pen.line(pr.left @ yC, pr.right @ yC);
		Pen.stroke;
		Pen.strokeColor = FxPalette.fade(FxPalette.dim, 0.7);
		Pen.line(pr.left @ yF, pr.right @ yF);
		Pen.stroke;
		// the hit
		Pen.fillColor = FxPalette.fade(FxPalette.wave, 0.8);
		Pen.fillRect(Rect(this.prX(0, pr, span), this.prYst(0, pr) - 3, (this.prX(c[\pre], pr, span) - this.prX(0, pr, span)).max(3), 6));
		// the steps: up from one to the next, then each ringing away
		c[\rungs].do { |g, i|
			var x0 = this.prX(g[\t], pr, span), y = this.prYst(g[\st], pr);
			var len = g[\rt] * ((g[\db] + 60) / 60).max(0), a0 = ((g[\db] + 60) / 60).clip(0, 1) * 0.95, k = 14;
			if (i > 0) {
				Pen.strokeColor = FxPalette.fade(col, 0.4 * a0 + 0.1);
				Pen.width = 1;
				Pen.line(x0 @ this.prYst(c[\rungs][i - 1][\st], pr), x0 @ y);
				Pen.stroke;
			};
			k.do { |j|
				var xa = this.prX(g[\t] + (len * j / k), pr, span), xb = this.prX(g[\t] + (len * (j + 1) / k), pr, span);
				if (xa < pr.right) {
					Pen.fillColor = FxPalette.fade(col, a0 * (1 - (j / k)));
					Pen.fillRect(Rect(xa, y - (thick / 2), xb.min(pr.right) - xa, thick));
				};
			};
		};
		this.prTag("damp " ++ this.prFmt(\damp), pr.right - 22, (yC + 3).min(pr.bottom - 14), col, \right);
		if (yF > (pr.top + 16)) { this.prTag("low cut " ++ this.prFmt(\lowCut), pr.right - 42, yF - 16, FxPalette.dim, \right) };
		// the output, live, on its own dB
		Pen.strokeColor = FxPalette.fade(FxPalette.waveDim, 0.5);
		Pen.line(sr.left @ this.prY(0, sr), sr.right @ this.prY(0, sr));
		Pen.stroke;
		Pen.strokeColor = FxPalette.fade(FxPalette.waveDim, 0.8);
		Pen.line(sr.left @ sr.bottom, sr.right @ sr.bottom);
		Pen.stroke;
		Pen.stringRightJustIn("out", Rect(sr.left - 34, sr.top, 31, 12), FxPalette.smallFont, FxPalette.dim);
		Pen.stringRightJustIn("-60", Rect(sr.left - 34, sr.bottom - 10, 31, 12), FxPalette.smallFont, FxPalette.dim);
		if (c[\level].size > 1) { this.prFilled(c[\level] ++ [c[\level].last.x @ sr.bottom], sr, col, 0.3, 1) };
		this.prDrawTrace(sr, span);
	}

	caption {
		^if ((unit.get(\feedback) ? 0) <= 0.0001) {
			"no feedback: a plain tank  ·  decay %  ·  damp %".format(this.prFmt(\decay), this.prFmt(\damp))
		} {
			"%  ·  feedback %  ·  a round every %  ·  decay %".format(
				this.class.intervalText(unit.get(\interval)), this.prFmt(\feedback),
				FxPalette.format(this.class.step(unit.get(\rise)), "s"), this.prFmt(\decay))
		}
	}

	captionRight { ^if (cache.notNil and: { cache[\runaway] == true }) { "climbs until the damping stops it" } }

	prFreeKeys { |x, y| ^[\decay, \interval] }

	prWheelKey { |x, y| ^\feedback }

	prFreeHint { |x, y|
		^"the climb: drag along for the decay, up and down for the interval (shift: fine); the wheel: feedback; double click: the defaults"
	}
}
