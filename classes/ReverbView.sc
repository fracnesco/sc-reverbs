// =====================================================================
// ReverbView - the reverbs' display: the frame every tail is drawn on
//
// Time along, the level in dB up (0 is the tail at its loudest, -60 the
// floor), a strip at the top for the readout, and behind the model the
// output itself: after each hit the meter's readings are drawn from the
// moment of the hit, so a sound rings out along the slope the settings say.
// Each reverb draws its own kind of space on it (sc-fx-ui, "Family faces"):
//
//   HallView      the onset - the gap, the walls, the slow swell - and the long fall
//   PlateView     a sheet: dense from the first moment, held flat by hold
//   CoilView      a lane per spring, each round trip a chirp, smearing as it goes
//   ShimmerView   pitch against time: each round an interval higher, fading
//
// This class alone draws the plain tail, for any FxParamUnit that names its
// decay, pre-delay and damping in *viewKeys.
//
//   drag a handle         what the hint bar says it moves (shift: fine)
//   drag anywhere else    along: the decay; up and down: the damping
//   double click          a handle's parameters back to their defaults
//                         (anywhere else: the decay and the damping)
//   the wheel             the face's second hero (size, drip, feedback)
//
// A face writes prCompute (an Event of what it draws, worked out again only
// when a setting or the size changes, its \handles among it), prDrawFace and
// caption; prFreeKeys, prWheelKey and prFreeHint say what the rest does.
// =====================================================================

ReverbView : FxParamView {
	var drag, hoverKeys, cache, cacheSig;
	var sweep, sweepAt = -100, sweepTop = -200, sweepHit = false, inFollow = -120;

	*minHeight { ^180 }

	// the frequencies the two extra lines are drawn for: the highs, the lows
	*highHz { ^5000 }
	*lowHz { ^100 }

	// the scale, in dB: a little room above 0 for what pokes over the tail
	*topDb { ^6 }
	*floorDb { ^-60 }
	*dbTicks { ^#[0, -12, -24, -48] }

	// the spans the time axis keeps to, so it moves in steps while you drag
	*spans { ^#[0.1, 0.15, 0.2, 0.25, 0.3, 0.4, 0.5, 0.75, 1, 1.5, 2, 2.5, 3, 4, 5, 6, 8, 10, 12, 15, 20, 30, 45, 60, 90] }
	*niceSpan { |t| ^this.spans.detect { |s| s >= t } ? this.spans.last }
	*tickStep { |span, most = 6.5| ^#[0.01, 0.02, 0.025, 0.05, 0.1, 0.2, 0.25, 0.5, 1, 2, 2.5, 5, 10, 20].detect { |s| (span / s) <= most } ? 20 }

	*timeText { |t|
		var ms = (t * 1000).round.asInteger;
		^case
		{ ms == 0 } { "0" }
		{ ms < 1000 } { ms.asString ++ " ms" }
		{ (ms % 1000) == 0 } { (ms div: 1000).asString ++ " s" }
		{ (ms % 100) == 0 } { FxPalette.fixed(t, 1) ++ " s" }
		{ true } { FxPalette.fixed(t, 2) ++ " s" }
	}

	// a colour taken part of the way to white: the highs' line
	*tint { |c, f = 0.45| ^Color(c.red + ((1 - c.red) * f), c.green + ((1 - c.green) * f), c.blue + ((1 - c.blue) * f), c.alpha) }

	init { |argUnit, parent|
		super.init(argUnit, parent);
		sweep = List.new;
		view.mouseLeaveAction = { if (hoverKeys.notNil) { hoverKeys = nil; this.refresh } };
		^this
	}

	identity { ^(unit !? { unit.class.tryPerform(\identityColour) }) ?? { FxPalette.identity(\reverb) } }

	prKeys { ^unit.class.viewKeys }

	// ------------------------------------------------------------ parameters

	prGet { |key| ^key !? { unit.get(key) } }
	prParam { |key| ^key !? { unit.class.param(key) } }
	prUnmap { |key| ^this.prParam(key)[\spec].unmap(unit.get(key)) }
	prSetUnmapped { |key, v| unit.set(key, this.prParam(key)[\spec].map(v.clip(0, 1))) }
	prDefault { |key| this.prParam(key) !? { |e| unit.set(key, e[\default]) } }

	// a parameter's value as its knob says it
	prFmt { |key|
		var e = this.prParam(key);
		^if (e.isNil) { "" } {
			FxPalette.format(unit.get(key), e[\spec].units, e[\box], e[\dec], e[\bipolar] ? false, e[\floorText], e[\spec])
		}
	}

	// -------------------------------------------------------------- geometry

	// the plot: the readout's strip above, the dB labels at the left, the time below
	prPlotRect { ^Rect(34, 22, (bw - 42).max(20), (bh - 22 - 18).max(20)) }

	prX { |t, r, span| ^r.left + (t / span.max(1e-3) * r.width) }
	prT { |x, r, span| ^(x - r.left) / r.width.max(1) * span }
	prY { |db, r| ^db.linlin(this.class.floorDb, this.class.topDb, r.bottom, r.top, \minmax) }

	// a straight fall in dB: from t0 at db0, 60 dB every rt seconds, to the floor
	prFall { |r, span, t0, db0, rt|
		var t1 = t0 + ((db0 - this.class.floorDb) * rt.max(1e-4) / 60);
		^[this.prX(t0, r, span) @ this.prY(db0, r), this.prX(t1, r, span) @ this.prY(this.class.floorDb, r)]
	}

	// ----------------------------------------------------------------- drawing

	prDraw {
		var sig = [bw, bh] ++ unit.controls;
		if (sig != cacheSig) { cacheSig = sig; cache = this.prCompute };
		this.prDrawFace;
		this.prDrawHandles;
		this.prDrawCaption;
	}

	// the dB lines and the time of one part of the display
	prAxes { |r, span, labels = true|
		Pen.width = 1;
		this.class.dbTicks.do { |db|
			var y = this.prY(db, r);
			Pen.strokeColor = FxPalette.fade(FxPalette.waveDim, if (db == 0) { 0.9 } { 0.45 });
			Pen.line(r.left @ y, r.right @ y);
			Pen.stroke;
			if (labels) {
				Pen.stringRightJustIn(if (db == 0) { "0 dB" } { db.asString }, Rect(r.left - 34, y - 6, 31, 12),
					FxPalette.smallFont, FxPalette.dim);
			};
		};
		Pen.strokeColor = FxPalette.fade(FxPalette.waveDim, 0.8);
		Pen.line(r.left @ r.bottom, r.right @ r.bottom);
		Pen.stroke;
		if (labels) {
			Pen.stringRightJustIn("-60", Rect(r.left - 34, r.bottom - 6, 31, 12), FxPalette.smallFont, FxPalette.dim);
		};
		this.prTimeAxis(r, span, r.bottom);
	}

	// the time's grid through r and its labels under `under`
	prTimeAxis { |r, span, under|
		var step = this.class.tickStep(span, (r.width / 72).clip(2, 6.5)), n = (span / step + 1e-6).floor.asInteger;
		(n + 1).do { |i|
			var t = i * step, x = this.prX(t, r, span), str = this.class.timeText(t), lr;
			Pen.strokeColor = FxPalette.fade(FxPalette.waveDim, if (i == 0) { 0.6 } { 0.28 });
			Pen.line(x @ r.top, x @ r.bottom);
			Pen.stroke;
			lr = case
			{ i == 0 } { Rect(x, under + 3, 60, 12) }
			{ (x + 30) > (r.right + 6) } { Rect(x - 60, under + 3, 60, 12) }
			{ true } { Rect(x - 30, under + 3, 60, 12) };
			case
			{ i == 0 } { Pen.stringLeftJustIn(str, lr, FxPalette.smallFont, FxPalette.dim) }
			{ (x + 30) > (r.right + 6) } { Pen.stringRightJustIn(str, lr, FxPalette.smallFont, FxPalette.dim) }
			{ true } { Pen.stringCenteredIn(str, lr, FxPalette.smallFont, FxPalette.dim) };
		};
	}

	// diagonal lines over a part of the display: what is not there yet, or is cut
	prHatch { |r, colour, gap = 6|
		if (r.width < 1 or: { r.height < 1 }) { ^this };
		Pen.push;
		Pen.addRect(r);
		Pen.clip;
		Pen.strokeColor = colour;
		Pen.width = 1;
		(r.left - r.height).forBy(r.right, gap, { |x0| Pen.line(x0 @ r.bottom, (x0 + r.height) @ r.top) });
		Pen.stroke;
		Pen.pop;
	}

	// the pre-delay: nothing yet, and the hit that starts it at 0
	prDrawGap { |r, span, pre, closed = false|
		var x0 = this.prX(0, r, span), x1 = this.prX(pre, r, span);
		this.prHatch(Rect(x0, r.top, x1 - x0, r.height), FxPalette.fade(FxPalette.waveDim, 0.35));
		// the hit: what came in, in the signal's blue
		Pen.strokeColor = FxPalette.fade(FxPalette.wave, if (closed) { 0.25 } { 0.7 });
		Pen.width = 2;
		Pen.line(x0 + 1 @ this.prY(0, r), x0 + 1 @ r.bottom);
		Pen.stroke;
	}

	prPolyline { |pts|
		pts.do { |p, i| if (i == 0) { Pen.moveTo(p) } { Pen.lineTo(p) } };
	}

	// a line, and under it to the floor a fill fading downwards
	prFilled { |pts, r, colour, top = 0.38, line = 1.5|
		if (pts.size < 2) { ^this };
		Pen.moveTo(pts[0].x @ r.bottom);
		pts.do { |p| Pen.lineTo(p) };
		Pen.lineTo(pts.last.x @ r.bottom);
		Pen.fillAxialGradient(0 @ this.prY(0, r), 0 @ r.bottom, FxPalette.fade(colour, top), FxPalette.fade(colour, 0.04));
		Pen.strokeColor = colour;
		Pen.width = line;
		this.prPolyline(pts);
		Pen.stroke;
	}

	// a line of its own: the highs, the lows
	prLine { |pts, colour, width = 1.5, dash|
		if (pts.size < 2) { ^this };
		Pen.push;
		Pen.strokeColor = colour;
		Pen.width = width;
		dash !? { Pen.lineDash = FloatArray.newFrom(dash) };
		this.prPolyline(pts);
		Pen.stroke;
		Pen.pop;
	}

	// a small word on a dark ground, so it reads on top of a line
	prTag { |str, x, y, colour, align = \left|
		var w = str.bounds(FxPalette.smallFont).width + 8, r;
		r = if (align == \left) { Rect(x, y, w, 13) } { Rect(x - w, y, w, 13) };
		r = r.moveTo(r.left.clip(0, (bw - w).max(0)), r.top.clip(0, (bh - 13).max(0)));
		Pen.fillColor = FxPalette.fade(FxPalette.waveBg, 0.85);
		Pen.addRoundedRect(r, 3, 3);
		Pen.fill;
		Pen.stringCenteredIn(str, r, FxPalette.smallFont, colour);
	}

	// the decay as a length: a dashed mark where the tail reaches the floor and,
	// above the 0 dB line, a line from where the fall starts to there, its time on it
	prDrawEnd { |r, span, t0, t1, label|
		var x0 = this.prX(t0, r, span), x1 = this.prX(t1, r, span), y = this.prY(3, r), col = this.identity, w;
		if (x1 <= (r.right + 0.5)) {
			Pen.push;
			Pen.strokeColor = FxPalette.fade(col, 0.55);
			Pen.width = 1;
			Pen.lineDash = FloatArray[3, 3];
			Pen.line(x1 @ this.prY(0, r), x1 @ r.bottom);
			Pen.stroke;
			Pen.pop;
		};
		x1 = x1.min(r.right);
		Pen.strokeColor = FxPalette.fade(col, 0.7);
		Pen.width = 1;
		Pen.line(x0 @ y, x1 @ y);
		Pen.line(x0 @ (y - 3), x0 @ (y + 3));
		Pen.line(x1 @ (y - 3), x1 @ (y + 3));
		Pen.stroke;
		label !? {
			w = label.bounds(FxPalette.smallFont).width + 8;
			this.prTag(label, (((x0 + x1) / 2) - (w / 2)).max(x0), y - 7, col);
		};
	}

	// what came out since the last hit, along the time of r, its loudest at 0 dB
	prDrawTrace { |r, span, colour|
		var pts, c = colour ? FxPalette.wave;
		if (sweep.isNil or: { sweep.size < 2 } or: { sweepTop < -70 }) { ^this };
		pts = sweep.select { |p| p[0] <= span }.collect { |p|
			this.prX(p[0], r, span) @ this.prY((p[1] - sweepTop).max(this.class.floorDb), r)
		};
		if (pts.size < 2) { ^this };
		Pen.moveTo(pts[0].x @ r.bottom);
		pts.do { |p| Pen.lineTo(p) };
		Pen.lineTo(pts.last.x @ r.bottom);
		Pen.fillColor = FxPalette.fade(c, 0.1);
		Pen.fill;
		Pen.strokeColor = FxPalette.fade(c, 0.9);
		Pen.width = 1.5;
		this.prPolyline(pts);
		Pen.stroke;
		Pen.fillColor = c;
		Pen.fillOval(Rect.aboutPoint(pts.last, 2.5, 2.5));
	}

	prDrawHandles {
		var col = this.identity;
		this.prHandles.do { |h|
			var keys = [h[\x], h[\y]];
			var hot = keys == hoverKeys, held = drag.notNil and: { keys == [drag[\x], drag[\y]] };
			var c = if (held) { FxPalette.accent } { h[\colour] ? col };
			var rad = if (hot or: { held }) { 5.5 } { 4.5 };
			Pen.fillColor = c;
			Pen.fillOval(Rect.aboutPoint(h[\at], rad, rad));
			Pen.strokeColor = if (hot or: { held }) { FxPalette.text } { FxPalette.waveBg };
			Pen.width = 1.5;
			Pen.strokeOval(Rect.aboutPoint(h[\at], rad, rad));
		};
	}

	// the readout, in the strip above the plot (never over what it describes)
	prDrawCaption {
		var right = this.captionRight;
		Pen.stringLeftJustIn(this.caption, Rect(8, 3, bw - 16, 14), FxPalette.smallFont, FxPalette.text);
		right !? { Pen.stringRightJustIn(right, Rect(8, 3, bw - 16, 14), FxPalette.smallFont, this.identity) };
	}

	caption {
		var k = this.prKeys;
		^(["decay " ++ this.prFmt(k[\decay])]
			++ (k[\pre] !? { ["pre " ++ this.prFmt(k[\pre])] } ? [])
			++ (k[\damp] !? { ["damp " ++ this.prFmt(k[\damp])] } ? [])).join("  ·  ")
	}

	captionRight { ^nil }

	// --------------------------------------------- the plain tail (any reverb)

	prCompute {
		var k = this.prKeys, r = this.prPlotRect, c;
		var decay = this.prGet(k[\decay]) ? 1, pre = this.prGet(k[\pre]) ? 0, damp = this.prGet(k[\damp]);
		var span = this.class.niceSpan((pre + decay) * 1.03);
		c = (rect: r, span: span, pre: pre, decay: decay);
		c[\fall] = this.prFall(r, span, pre, 0, decay);
		c[\highs] = damp !? { this.prFall(r, span, pre, 0, ReverbKit.rtAt(this.class.highHz, decay, 0.06, damp, 20)) };
		c[\handles] = [(x: k[\decay], at: this.prX(pre + decay, r, span).min(r.right) @ r.bottom,
			hint: "the tail's end: drag along for the decay (shift: fine), double click for its default")];
		c[\highs] !? { |h|
			c[\handles] = c[\handles].add((y: k[\damp], at: (h[0] + h[1]) / 2, colour: this.class.tint(this.identity),
				hint: "the highs (5 kHz): drag up and down for the damping"));
		};
		^c
	}

	prDrawFace {
		var r = cache[\rect], span = cache[\span], col = this.identity;
		this.prAxes(r, span);
		this.prDrawGap(r, span, cache[\pre]);
		this.prFilled([cache[\fall][0], cache[\fall][1]], r, col);
		cache[\highs] !? { |h| this.prLine(h, this.class.tint(col), 1.5) };
		this.prDrawEnd(r, span, cache[\pre], cache[\pre] + cache[\decay], "decay " ++ this.prFmt(this.prKeys[\decay]));
		this.prDrawTrace(r, span);
	}

	// ------------------------------------------------------------------ live

	// on every meter reply: the readings since the last hit (the input jumps
	// 9 dB over where it was), or since the sweep last ran off the display
	levelsArrived {
		var now = Main.elapsedTime, inDb, outDb, span;
		if (unit.isNil) { ^this };
		inDb = (unit.inLevel ? 0).max(1e-6).ampdb;
		outDb = (unit.outLevel ? 0).max(1e-6).ampdb;
		span = (cache !? { cache[\traceSpan] ? cache[\span] }) ? 3;
		if ((inDb > -50) and: { inDb > (inFollow + 9) } and: { (now - sweepAt) > 0.15 }) {
			this.prNewSweep(now, true);
		} {
			if ((now - sweepAt) > span) { this.prNewSweep(now, false) };
		};
		inFollow = max(inDb, inFollow - 1.5);
		sweep.add([now - sweepAt, outDb]);
		sweepTop = max(sweepTop, outDb);
	}

	prNewSweep { |now, hit|
		sweep = List.new;
		sweepAt = now;
		sweepTop = -200;
		sweepHit = hit;
	}

	// for a check: the readings drawn now ([seconds since the hit, dB]), their
	// loudest, whether a hit started them, and what the face drew last
	sweep { ^sweep }
	sweepTop { ^sweepTop }
	sweepHit { ^sweepHit }
	model { ^cache }

	// ------------------------------------------------------------------ mouse

	prHandles { ^(cache !? { cache[\handles] }) ? [] }

	prHandleAt { |x, y|
		var best, dist = 10;
		this.prHandles.do { |h|
			var d = h[\at].dist(x @ y);
			if (d < dist) { dist = d; best = h };
		};
		^best
	}

	// what a drag that misses every handle moves: [along, up and down]
	prFreeKeys { |x, y| var k = this.prKeys; ^[k[\decay], k[\damp]] }

	// what the wheel moves
	prWheelKey { |x, y| ^nil }

	prFreeHint { |x, y|
		^"the tail: drag along for the decay, up and down for the damping (shift: fine) - double click: both back to their defaults"
	}

	prMouseDown { |x, y, mod, count|
		var h = this.prHandleAt(x, y);
		var keys = if (h.notNil) { [h[\x], h[\y]] } { this.prFreeKeys(x, y) };
		if (count == 2) {
			keys.do { |k| k !? { this.prDefault(k) } };
			drag = nil;
			this.refresh;
			^this
		};
		drag = (x: keys[0], y: keys[1], x0: x, y0: y, shift: mod.isShift);
		this.prDragFrom;
		this.refresh;
	}

	prDragFrom {
		drag[\fromX] = drag[\x] !? { |k| this.prUnmap(k) };
		drag[\fromY] = drag[\y] !? { |k| this.prUnmap(k) };
	}

	prMouseMove { |x, y, mod|
		var speed;
		if (drag.isNil) { ^this };
		// a change of speed mid drag carries on from where it is, no jump
		if (mod.isShift != drag[\shift]) {
			drag[\shift] = mod.isShift;
			drag[\x0] = x;
			drag[\y0] = y;
			this.prDragFrom;
		};
		speed = if (drag[\shift]) { 0.1 } { 1 };
		drag[\x] !? { |k| this.prSetUnmapped(k, drag[\fromX] + ((x - drag[\x0]) / 300 * speed)) };
		drag[\y] !? { |k| this.prSetUnmapped(k, drag[\fromY] + ((drag[\y0] - y) / 200 * speed)) };
	}

	prMouseUp { drag = nil; this.refresh }

	prWheel { |x, y, delta|
		var k = this.prWheelKey(x, y);
		if (k.isNil or: { delta == 0 }) { ^this };
		this.prSetUnmapped(k, this.prUnmap(k) + (delta.sign * 0.02));
	}

	// what the bar says: the handle under the pointer, or what dragging there does
	hint { |x, y|
		var h = if (x.notNil and: { y.notNil }) { this.prHandleAt(x, y) };
		var keys = h !? { [h[\x], h[\y]] };
		if (keys != hoverKeys) { hoverKeys = keys; this.refresh };
		^(h !? { h[\hint] }) ?? { this.prFreeHint(x, y) }
	}
}
