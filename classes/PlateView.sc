// =====================================================================
// PlateView - Plate's display: a sheet that rings
//
// A plate has no build: the echoes are dense from the first moment, so the
// tail is drawn as a sheet of fine lines, one per echo, at full level as
// soon as the first line of the network comes back (42 ms per unit of size
// after the pre-delay), falling to -60 dB at the decay, the highs (5 kHz)
// sooner under the damping, the lows (100 Hz) under the low cut. Held, the
// sheet runs flat to the edge - it rings on at unity - and the hit fades:
// nothing more goes in. Behind it, the output itself after each hit.
//
//   drag the gap's edge        the pre-delay
//   drag the end along         the decay
//   drag the 5k line           the damping (up and down)
//   drag anywhere else         decay along, damping up and down
//   the wheel                  the size; shift: fine; double click: the default
// =====================================================================

PlateView : ReverbView {

	// ReverbKit.fdn's lines at size 1: the first comes back after 42 ms, and
	// a lap is 58 ms on average
	*firstLine { |room| ^0.0421 * room }
	*lap { |room| ^0.0578 * room }

	// the same for every plate: a fixed scatter of echo levels, never a flicker
	*jitter { |i| ^(sin(i * 12.9898) * 43758.5453).frac }

	prCompute {
		var r = this.prPlotRect, c, lap, n, x0, step = 2;
		var room = unit.get(\room), decay = unit.get(\decay), hold = unit.get(\hold) == true;
		c = (rect: r, pre: unit.get(\preDelay), room: room, decay: decay, hold: hold,
			damp: unit.get(\damp), low: unit.get(\lowCut));
		lap = this.class.lap(room);
		c[\t1] = c[\pre] + this.class.firstLine(room);
		c[\span] = this.class.niceSpan(if (hold) { (c[\t1] + 2.5).max(3) } { (c[\t1] + decay) * 1.03 });
		c[\traceSpan] = c[\span];
		c[\rtHi] = ReverbKit.rtAt(this.class.highHz, decay, lap, c[\damp], c[\low]);
		c[\rtLo] = ReverbKit.rtAt(this.class.lowHz, decay, lap, c[\damp], c[\low]);
		// the sheet: a line every 2 px from the first echo, a little under the envelope
		x0 = this.prX(c[\t1], r, c[\span]);
		n = ((r.right - x0) / step).floor.asInteger.max(0);
		c[\strokes] = n.collect { |i|
			var x = x0 + (i * step), t = this.prT(x, r, c[\span]) - c[\t1];
			var db = if (hold) { 0 } { -60 * t / decay };
			db = db - (this.class.jitter(i) * 7) - (if (i < 2) { 3 } { 0 });
			[x, this.prY(db, r)]
		}.select { |p| p[1] < (r.bottom - 0.5) };
		c[\edge] = if (hold) {
			[x0 @ this.prY(0, r), r.right @ this.prY(0, r)]
		} {
			this.prFall(r, c[\span], c[\t1], 0, decay)
		};
		c[\highs] = if (hold.not) { this.prFall(r, c[\span], c[\t1], 0, c[\rtHi]) };
		c[\lows] = if (hold.not and: { c[\rtLo] < (decay * 0.92) }) { this.prFall(r, c[\span], c[\t1], 0, c[\rtLo]) };
		c[\handles] = [
			(x: \preDelay, at: this.prX(c[\pre], r, c[\span]).max(r.left + 5) @ (r.top + 14),
				hint: "the pre-delay's end: drag along for the gap before the plate speaks")
		];
		if (hold.not) {
			c[\handles] = c[\handles] ++ [
				(x: \decay, at: this.prX(c[\t1] + decay, r, c[\span]).min(r.right) @ r.bottom,
					hint: "the tail's end, 60 dB down: drag along for the decay (shift: fine)"),
				(y: \damp, at: (c[\highs][0] + c[\highs][1]) / 2, colour: this.class.tint(this.identity),
					hint: "the highs (5 kHz) dying sooner: drag up for a brighter plate, down for a darker one")
			];
		};
		^c
	}

	prDrawFace {
		var c = cache, r = c[\rect], span = c[\span], col = this.identity, light = this.class.tint(col);
		this.prAxes(r, span);
		this.prDrawGap(r, span, c[\pre], c[\hold]);
		// the sheet
		Pen.strokeColor = FxPalette.fade(col, if (c[\hold]) { 0.62 } { 0.5 });
		Pen.width = 1;
		c[\strokes].do { |p| Pen.line(p[0] @ r.bottom, p[0] @ p[1]) };
		Pen.stroke;
		this.prLine(c[\edge], col, if (c[\hold]) { 2 } { 1.5 });
		if (c[\hold].not) {
			c[\lows] !? { |l| this.prLine(l, FxPalette.fade(col, 0.65), 1, [4, 3]) };
			this.prLine(c[\highs], light, 1.5);
			this.prTag("5k", c[\highs][1].x.min(r.right) + 4, c[\highs][1].y - 15, light);
			c[\lows] !? { |l| this.prTag("100", l[1].x.min(r.right) + 4, l[1].y - 15, FxPalette.fade(col, 0.8)) };
			this.prDrawEnd(r, span, c[\t1], c[\t1] + c[\decay], "decay " ++ this.prFmt(\decay));
		};
		this.prDrawTrace(r, span);
	}

	caption {
		^if (unit.get(\hold) == true) {
			"it rings on at unity and nothing more goes in  ·  size %  ·  mod %".format(this.prFmt(\room), this.prFmt(\modDepth))
		} {
			"decay %  ·  pre %  ·  size %  ·  damp %  ·  low cut %".format(this.prFmt(\decay), this.prFmt(\preDelay),
				this.prFmt(\room), this.prFmt(\damp), this.prFmt(\lowCut))
		}
	}

	captionRight { ^if (unit.get(\hold) == true) { "held" } }

	prWheelKey { |x, y| ^\room }

	prFreeHint { |x, y|
		^if (unit.get(\hold) == true) {
			"the plate is held: it rings on at unity until hold lets go - the decay and the damping wait"
		} {
			"the plate: drag along for the decay, up and down for the damping (shift: fine); the wheel: size; double click: the defaults"
		}
	}
}
