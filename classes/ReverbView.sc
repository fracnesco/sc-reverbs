// =====================================================================
// ReverbView - the reverbs' display: the tail, as it will fall
//
// A gap for the pre-delay, then the tail from full to 60 dB down over the
// decay time, drawn brighter the less damping there is. The same view for
// Hall, Plate, Coil and Shimmer: each says which of its parameters are the
// decay, the pre-delay and the damping in *viewKeys.
//
//   drag   along: the decay; up and down: the damping
// =====================================================================

ReverbView : FxParamView {
	var dragging = false;

	*minHeight { ^150 }

	prKeys { ^unit.class.viewKeys }
	prRect { ^Rect(4, 4, bw - 8, bh - 8) }

	// the view's span in seconds: a little past the tail
	prSpan {
		var k = this.prKeys, decay = unit.get(k[\decay]) ? 1;
		var pre = k[\pre] !? { unit.get(k[\pre]) } ? 0;
		^((decay * 1.15) + pre).clip(0.3, 70)
	}

	prDecaySpec { ^unit.class.param(this.prKeys[\decay])[\spec] }

	prDraw {
		var r = this.prRect, k = this.prKeys;
		var decay = unit.get(k[\decay]) ? 1;
		var pre = k[\pre] !? { unit.get(k[\pre]) } ? 0;
		var damp = unit.get(k[\damp]), bright = 0.5;
		var span = this.prSpan, points = r.width.asInteger.max(2), x0;
		var levels = unit.extras ? [];
		if (damp.notNil) { bright = damp.explin(k[\dampLo], k[\dampHi], 0.15, 1, \minmax) };
		Pen.fillColor = FxPalette.bg;
		Pen.fillRect(r);
		// the tail: amplitude, as it falls to -60 dB at the decay time
		x0 = r.left + (pre / span * r.width);
		Pen.moveTo(x0 @ r.bottom);
		points.do { |i|
			var t = i / (points - 1) * span;
			var a;
			if (t >= pre) {
				a = (10 ** ((t - pre) / decay * -3)).max(0.001);
				Pen.lineTo((r.left + (i / (points - 1) * r.width)) @ (r.bottom - (a.ampdb.linlin(-60, 0, 0, r.height, \minmax))));
			};
		};
		Pen.lineTo(r.right @ r.bottom);
		Pen.fillColor = FxPalette.wave.copy.alpha_(0.2 + (0.5 * bright));
		Pen.fill;
		// the pre-delay
		if (pre > 0.0005) {
			Pen.strokeColor = FxPalette.accent.copy.alpha_(0.6);
			Pen.width = 1;
			Pen.line(x0 @ r.top, x0 @ r.bottom);
			Pen.stroke;
		};
		// the time at which it is 60 dB down
		Pen.strokeColor = FxPalette.waveDim;
		Pen.line((x0 + (decay / span * r.width)) @ r.top, (x0 + (decay / span * r.width)) @ r.bottom);
		Pen.stroke;
		this.prCaption("decay % s%%".format(
			decay.round(0.01),
			if (k[\pre].notNil) { "   pre % ms".format((pre * 1000).round(1)) } { "" },
			if (damp.notNil) { "   damp % Hz".format(damp.round(1)) } { "" }
		), r.left + 4, r.top + 2);
	}

	prMouseDown { |x, y, mod, count|
		dragging = true;
		this.prMouseMove(x, y, mod);
	}

	prMouseMove { |x, y, mod|
		var r = this.prRect, k = this.prKeys, spec = this.prDecaySpec;
		if (dragging.not) { ^this };
		unit.set(k[\decay], spec.map(x.linlin(r.left, r.right, 0, 1, \minmax)));
		if (k[\damp].notNil) {
			unit.set(k[\damp], y.linexp(r.bottom, r.top, k[\dampLo], k[\dampHi], \minmax));
		};
	}

	prMouseUp { dragging = false }

	hint { ^"the tail: drag along for the decay, up and down for the damping" }
}
