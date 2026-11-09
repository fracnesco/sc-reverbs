// =====================================================================
// HallView - Hall's display: the onset, then the long fall
//
// A hall is slow to build and long, so it is drawn at two time scales on
// the same dB: at the left the onset - the hit, the gap of the pre-delay,
// the first reflections off the walls (one line each, as loud as early
// makes them), the tank swelling (smooth when diffused, in steps when not)
// to its crest - and at the right the whole tail, falling to -60 dB at the
// decay, rippling as the modulation moves it, with the highs (5 kHz) dying
// sooner under the damping and the lows (100 Hz) under the low cut. Behind
// the tail, the output itself after each hit.
//
//   onset:  drag the gap's edge         the pre-delay
//           drag the crest along        the size
//           drag the tallest wall       the early reflections (up and down)
//   tail:   drag the end along          the decay
//           drag the 5k line            the damping (up and down)
//           drag anywhere else          decay along, damping up and down
//   the wheel                           the size; shift: fine; double click: the default
//
// The model's numbers are Hall's own: the taps and gains of *process, and
// where the tank is first heard and peaks, measured on an impulse
// (2026-11-09: 0.12 and 0.235 s per unit of size after the pre-delay).
// =====================================================================

HallView : ReverbView {

	// the first reflections of Hall's *process, left then right, and their gains
	*taps { ^#[0.0112, 0.0231, 0.0298, 0.0414, 0.0527, 0.0147, 0.0193, 0.0346, 0.0389, 0.0561] }
	*tapGains { ^#[0.9, 0.7, 0.55, 0.4, 0.3, 0.9, 0.7, 0.55, 0.4, 0.3] }

	// after the pre-delay, per unit of size: when the tank is first heard and
	// when it peaks (measured), and a lap of its figure of eight
	*firstSound { |room| ^0.12 * room }
	*crest { |room| ^0.235 * room }
	*lap { |room| ^0.376 * room }

	// a reflection's level against the tail's crest: early at 1 puts the first
	// one 2 dB over it (energy in 5 ms, measured)
	*tapDb { |early, gain| ^((early * gain / 0.9).max(1e-6).ampdb + 2).max(-80) }

	prOnsetRect {
		var r = this.prPlotRect, w = (r.width * 0.3).round;
		^Rect(r.left, r.top, w, r.height)
	}

	prTailRect {
		var r = this.prPlotRect, w = (r.width * 0.3).round + 18;
		^Rect(r.left + w, r.top, r.width - w, r.height)
	}

	// the tank's level at t (nil before it is heard): the swell, then the fall
	prEnv { |t, c|
		var u, db;
		if (t < c[\t1]) { ^nil };
		if (t < c[\tp]) {
			u = (t - c[\t1]) / (c[\tp] - c[\t1]).max(1e-4);
			db = -16 * (1 - u).squared;
			// undiffused, the first echoes stand out of the swell
			^db - ((1 - c[\diffusion]) * 10 * (1 - u) * (0.5 + (0.5 * cos(u * 8pi))))
		};
		u = t - c[\tp];
		db = -60 * u / c[\decay];
		^db + (c[\mod] * 2.5 * sin(2pi * c[\rate] * u) * (u / 0.3).min(1))
	}

	prEnvPoints { |r, span, c|
		var pts = [], n = (r.width / 2).asInteger.max(2);
		(n + 1).do { |i|
			var t = i / n * span, db = this.prEnv(t, c);
			if (db.notNil and: { db > this.class.floorDb }) { pts = pts.add(this.prX(t, r, span) @ this.prY(db, r)) };
		};
		// down to the floor where it gets there
		if (pts.notEmpty and: { pts.last.x < (r.right - 1) }) { pts = pts.add(pts.last.x + 2 @ r.bottom) };
		^pts
	}

	prCompute {
		var on = this.prOnsetRect, tl = this.prTailRect, c, top, iTop;
		var room = unit.get(\room), decay = unit.get(\decay), lap = this.class.lap(room);
		c = (onRect: on, tailRect: tl, pre: unit.get(\preDelay), room: room, decay: decay,
			damp: unit.get(\damp), low: unit.get(\lowCut), diffusion: unit.get(\diffusion),
			early: unit.get(\early), mod: unit.get(\modDepth), rate: unit.get(\modRate));
		c[\t1] = c[\pre] + this.class.firstSound(room);
		c[\tp] = c[\pre] + this.class.crest(room);
		c[\onSpan] = this.class.niceSpan((c[\tp] * 1.5) + 0.03);
		c[\span] = this.class.niceSpan((c[\tp] + decay) * 1.03);
		c[\traceSpan] = c[\span];
		c[\rtHi] = ReverbKit.rtAt(this.class.highHz, decay, lap, c[\damp], c[\low]);
		c[\rtLo] = ReverbKit.rtAt(this.class.lowHz, decay, lap, c[\damp], c[\low]);
		c[\onPts] = this.prEnvPoints(on, c[\onSpan], c);
		c[\tailPts] = this.prEnvPoints(tl, c[\span], c);
		c[\highs] = this.prFall(tl, c[\span], c[\tp], 0, c[\rtHi]);
		c[\lows] = if (c[\rtLo] < (decay * 0.92)) { this.prFall(tl, c[\span], c[\tp], 0, c[\rtLo]) };
		c[\taps] = this.class.taps.collect { |t, i|
			[this.prX(c[\pre] + (t * room), on, c[\onSpan]), this.prY(this.class.tapDb(c[\early], this.class.tapGains[i]), on)]
		};
		iTop = c[\taps].minIndex { |p| p[1] };
		top = c[\taps][iTop];
		c[\handles] = [
			(x: \preDelay, at: this.prX(c[\pre], on, c[\onSpan]) @ (on.top + 14),
				hint: "the pre-delay's end: drag along for the gap before the hall answers"),
			(y: \early, at: top[0] @ top[1].max(on.top + 4),
				hint: "the first reflections, off the walls: drag up and down for how loud they are"),
			(x: \room, at: this.prX(c[\tp], on, c[\onSpan]).min(on.right) @ this.prY(0, on),
				hint: "the crest: drag along for the size - a bigger hall swells later and echoes slower"),
			(x: \decay, at: this.prX(c[\tp] + decay, tl, c[\span]).min(tl.right) @ tl.bottom,
				hint: "the tail's end, 60 dB down: drag along for the decay (shift: fine)"),
			(y: \damp, at: (c[\highs][0] + c[\highs][1]) / 2, colour: this.class.tint(this.identity),
				hint: "the highs (5 kHz) dying sooner: drag up for a brighter hall, down for a darker one")
		];
		^c
	}

	prDrawFace {
		var c = cache, on = c[\onRect], tl = c[\tailRect], col = this.identity, light = this.class.tint(col);
		// ------------------------------------------------ the onset
		this.prAxes(on, c[\onSpan], true);
		this.prDrawGap(on, c[\onSpan], c[\pre]);
		if (c[\early] > 0.001) {
			Pen.strokeColor = FxPalette.fade(col, 0.85);
			Pen.width = 1.5;
			c[\taps].do { |p| Pen.line(p[0] @ on.bottom, p[0] @ p[1]) };
			Pen.stroke;
		};
		this.prFilled(c[\onPts], on, col, 0.32);
		this.prDrawTrace(on, c[\onSpan]);
		// the two parts apart
		Pen.fillColor = FxPalette.card;
		Pen.fillRect(Rect(on.right + 8, on.top, 1, on.height));
		// ------------------------------------------------ the tail
		this.prAxes(tl, c[\span], false);
		this.prDrawGap(tl, c[\span], c[\pre]);
		this.prFilled(c[\tailPts], tl, col, 0.36);
		c[\lows] !? { |l| this.prLine(l, FxPalette.fade(col, 0.6), 1, [4, 3]) };
		this.prLine(c[\highs], light, 1.5);
		this.prTag("5k", c[\highs][1].x.min(tl.right) + 4, c[\highs][1].y - 15, light);
		c[\lows] !? { |l| this.prTag("100", l[1].x.min(tl.right) + 4, l[1].y - 15, FxPalette.fade(col, 0.8)) };
		this.prDrawEnd(tl, c[\span], c[\tp], c[\tp] + c[\decay], "decay " ++ this.prFmt(\decay));
		this.prDrawTrace(tl, c[\span]);
	}

	prDrawCaption {
		var c = cache;
		Pen.stringLeftJustIn("onset  ·  pre %  ·  early %  ·  size %".format(this.prFmt(\preDelay), this.prFmt(\early), this.prFmt(\room)),
			Rect(c[\onRect].left - 26, 3, c[\onRect].width + 26, 14), FxPalette.smallFont, FxPalette.text);
		Pen.stringLeftJustIn("tail  ·  decay %  ·  damp %  ·  low cut %  ·  mod %".format(
			this.prFmt(\decay), this.prFmt(\damp), this.prFmt(\lowCut), this.prFmt(\modDepth)),
			Rect(c[\tailRect].left, 3, c[\tailRect].width, 14), FxPalette.smallFont, FxPalette.text);
	}

	caption {
		^"decay %  ·  pre %  ·  size %  ·  damp %".format(this.prFmt(\decay), this.prFmt(\preDelay), this.prFmt(\room), this.prFmt(\damp))
	}

	prInOnset { |x| ^cache.notNil and: { x < (cache[\onRect].right + 9) } }

	prFreeKeys { |x, y| ^if (this.prInOnset(x)) { [\room, \early] } { [\decay, \damp] } }

	prWheelKey { |x, y| ^\room }

	prFreeHint { |x, y|
		^if (this.prInOnset(x)) {
			"the onset: the hit, the gap, the walls, the swell - drag along for the size, up and down for the walls; the wheel: size"
		} {
			"the tail: drag along for the decay, up and down for the damping (shift: fine); the wheel: size; double click: the defaults"
		}
	}
}
